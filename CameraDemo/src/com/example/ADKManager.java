package com.example;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.hardware.usb.UsbAccessory;
import android.hardware.usb.UsbManager;
import android.widget.Toast;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Protocol: [command - 1 byte][action - 1 byte][data - X bytes]
 *
 * @author Amir Lazarovich
 */
public class ADKManager implements Runnable {
    ///////////////////////////////////////////////
    // Constants
    ///////////////////////////////////////////////
    private static final String TAG = "ADKManager";
    private static final int ACCESSORY_TO_RETURN = 0;
    private static final String ACTION_USB_PERMISSION = "com.google.android.DemoKit.action.USB_PERMISSION";

    private static final int RECONNECT_TIMER_PERIOD_MILLIS = 5000;

    public static final byte ON = 0;
    public static final byte OFF = 1;

    public static final byte DIRECTION_LEFT = 1;
    public static final byte DIRECTION_RIGHT = 0;

    // adk-commands
    public static final byte COMMAND_MOTOR_1 = 1;
    public static final byte COMMAND_MOTOR_2 = 2;
    public static final byte COMMAND_STAND_BY = 3;
    public static final byte COMMAND_ROTATE = 4;

    // adk-actions
    public static final byte ACTION_POWER_ON = 1;
    public static final byte ACTION_POWER_OFF = 2;
    public static final byte ACTION_ROTATE_BY_ANGLE = 3;
    public static final byte ACTION_RESET_ROTATION = 4;
    public static final byte ACTION_START_ROTATE = 5;
    public static final byte ACTION_END_ROTATE = 6;

    ///////////////////////////////////////////////
    // Members
    ///////////////////////////////////////////////
    private UsbManager mUsbManager;
    private boolean mPermissionRequestPending;
    private UsbAccessory mAccessory;
    private ParcelFileDescriptor mFileDescriptor;
    private FileInputStream mInputStream;
    private FileOutputStream mOutputStream;

    private ExecutorService mPool;
    private Context mContext;
    private Handler mHandler;
    private Callback mCallback;
    private Thread mCommunicationThread;

    private boolean mConnecting = false;
    private boolean mConnected = false;
    private Timer mTimer;

    ///////////////////////////////////////////////
    // Constructors
    ///////////////////////////////////////////////

    public ADKManager(Context context, Callback callback) {
        mContext = context;
        mHandler = new Handler();
        mCallback = callback;
        mPool = Executors.newSingleThreadExecutor();
    }


    ///////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////

    /**
     * Connect to the ADK
     */
    public void connect() {
        if (mConnected) {
            return;
        }

        //mUsbManager = UsbManager.getInstance(mContext);
        mUsbManager = (UsbManager) mContext.getSystemService(Context.USB_SERVICE);

        // register receiver
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        mContext.registerReceiver(mUsbReceiver, filter);


        mConnected = false;
        mConnecting = true;

        mTimer = new Timer();
        TimerTask reconnectTask = new TimerTask() {

            @Override
            public void run() {
                if (mConnected) {
                    return;
                }

                if (mConnecting) {
                    mCallback.onLog("Connecting to ADK...");
                    connectToADK();
                } else {
                    mTimer.cancel();
                }
            }
        };
        mTimer.schedule(reconnectTask, 0, RECONNECT_TIMER_PERIOD_MILLIS);

    }

    private void connectToADK() {
        // Looking for more than 1 connected accessory, if found will return the one defined in the constant
        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[ACCESSORY_TO_RETURN]);

        if (accessory != null) {
            if (mUsbManager.hasPermission(accessory)) {
                mCallback.onLog("Already have permission to connect. Opening accessory.");
                openAccessory(accessory);
            } else {
                mCallback.onLog("Requesting permission from user to connect.");
                mTimer.cancel();
                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        PendingIntent permissionIntent = PendingIntent.getBroadcast(mContext, 0, new Intent(ACTION_USB_PERMISSION), 0);
                        mUsbManager.requestPermission(accessory, permissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        }
    }

    /**
     * Disconnect from the ADK
     */
    public void disconnect() {
        mCallback.onLog("Disconnecting from the ADK device");
        mConnecting = false;
        mConnected = false;

        if (mTimer != null) {
            mTimer.cancel();
        }

        try {
            mContext.unregisterReceiver(mUsbReceiver);

        } catch (Exception e) {
           mCallback.onLog("Unregister called twice");
        }
        closeAccessory();
    }

    /**
     * Send command to the ADK
     *
     * @param command
     * @param action
     * @param data    May also be null if there's no data (if you read this, you rock!)
     */
    public void sendCommand(final byte command, final byte action, final byte[] data) {
        mPool.execute(new Runnable() {
            @Override
            public void run() {
                int dataLength = ((data != null) ? data.length : 0);

                ByteBuffer buffer = ByteBuffer.allocate(2 + dataLength);
                buffer.put(command);
                buffer.put(action);
                if (data != null) {
                    buffer.put(data);
                }

                if (mOutputStream != null) {
                    try {
                        mCallback.onLog("sendCommand: Sending data to Arduino device: " + buffer);
                        mOutputStream.write(buffer.array());
                    } catch (IOException e) {
                        mCallback.onLog("sendCommand: Send failed: " + e.getMessage());
                        reconnect();
                    }
                } else {
                    mCallback.onLog("sendCommand: Send failed: mOutStream was null");
                    reconnect();
                }
            }
        });
    }

    private void reconnect() {
        Log.i(TAG, "attempting to reconnect to ADK device");
        disconnect();
        connect();
    }

    ///////////////////////////////////////////////
    // Overrides & Implementations
    ///////////////////////////////////////////////

    /**
     * The running thread. It takes care of the communication between the Android and the Arduino
     */
    @Override
    public void run() {
        int ret;
        byte[] buffer = new byte[16384];

        // Keeps reading messages forever.
        // There are probably a lot of messages in the buffer, each message 4 bytes.
        while (true) {
            try {
                ret = mInputStream.read(buffer);
                if (ret > 0) {
                    final boolean ack = buffer[0] == 1;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mCallback.onADKAckReceived(ack);
                        }
                    });
                }
            }
            catch (Exception e) {
                break;
            }

        }
    }

    ///////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////

    /**
     * Open read and write to and from the Arduino device
     *
     * @param accessory
     */
    private void openAccessory(UsbAccessory accessory) {
        mCallback.onLog("Trying to attach ADK device");
        mFileDescriptor = mUsbManager.openAccessory(accessory);
        if (mFileDescriptor != null) {
            mAccessory = accessory;
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);

            if (mCommunicationThread != null) {
                // TODO need to stop the thread in a better/safer way
                mCommunicationThread.interrupt();
            }

            mCommunicationThread = new Thread(null, this, TAG);
            mCommunicationThread.start();

            mCallback.onLog("Attached");

            mConnected = true;
            mConnecting = false;
            mTimer.cancel();

        } else {
            mCallback.onLog("openAccessory: accessory open failed");
        }
    }

    /**
     * Closing the read and write to and from the Arduino
     */
    private void closeAccessory() {
        mCallback.onLog("Trying to de-attach ADK device");
        try {
            if (mFileDescriptor != null) {
                mFileDescriptor.close();
            }

            if (mInputStream != null) {
                mInputStream.close();
            }

            if (mOutputStream != null) {
                mOutputStream.close();
            }
        } catch (Exception e) {
            mCallback.onLog("Couldn't close all streams properly");
        } finally {
            mFileDescriptor = null;
            mAccessory = null;
        }
    }

    /**
     * Run on UI thread
     *
     * @param runnable
     */
    private void runOnUiThread(Runnable runnable) {
        mHandler.post(runnable);
    }

    ///////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////

    public interface Callback {
        void onADKAckReceived(boolean ack);

        void onLog(String s);
    }

    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            mCallback.onLog("Got USB intent " + action);

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    //UsbAccessory accessory = UsbManager.getAccessory(intent);
                    UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openAccessory(accessory);
                    } else {
                        mCallback.onLog("USB permission denied");
                    }
                    mPermissionRequestPending = false;
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
                UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

                //if (accessory != null && accessory.equals(mAccessory)) {
                    mCallback.onLog("Got ACTION_USB_ACCESSORY_ATTACHED message");
                    mConnecting = false;
                    mConnected = true;
                //}

            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbAccessory accessory = (UsbAccessory) intent.getParcelableExtra(UsbManager.EXTRA_ACCESSORY);

                //if (accessory != null && accessory.equals(mAccessory)) {
                    mCallback.onLog("Got ACTION_USB_ACCESSORY_DETACHED message");
                    closeAccessory();

                    // reconnect if needed
                    if (mConnected) {
                        mCallback.onLog("Reconnecting...");
                        mConnected = false;
                        connect();
                    }
                //}
            }
        }
    };
}
