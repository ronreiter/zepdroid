package com.geeckon;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import com.android.future.usb.UsbAccessory;
import com.android.future.usb.UsbManager;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * https://github.com/Epsiloni/ADKBareMinimum Based on Simon Monk code. Rewritten by Assaf Gamliel (goo.gl/E2MhJ) (assafgamliel.com). Feel free to contact
 * me with any question, I hope I can help. This code should give you a good jump start with your Android and Arduino project. -- This is the minimum you
 * need to communicate between your Android device and your Arduino device. If needed I'll upload the example I made (Sonar distance measureing device).
 */


public class MainActivity extends Activity implements Runnable, View.OnClickListener {

    private static final int ACCESSORY_TO_RETURN = 0;
    private static final String ACTION_USB_PERMISSION = "com.google.android.DemoKit.action.USB_PERMISSION";
    private static final String TAG = "MainActivity";

    private static final byte ON = 0;
    private static final byte OFF = 1;

    private static final byte DIRECTION_LEFT = 1;
    private static final byte DIRECTION_RIGHT = 0;

    private static final byte COMMAND_MOTOR_1 = 1;
    private static final byte COMMAND_MOTOR_2 = 2;
    private static final byte COMMAND_STAND_BY = 3;
    private static final byte COMMAND_ROTATE = 4;

    private static final byte ACTION_POWER_ON = 1;
    private static final byte ACTION_POWER_OFF = 2;
    private static final byte ACTION_ROTATE_BY_ANGLE = 3;
    private static final byte ACTION_RESET_ROTATION = 4;
    private static final byte ACTION_START_ROTATE = 5;
    private static final byte ACTION_END_ROTATE = 6;

    private UsbManager mUsbManager;
    private PendingIntent mPermissionIntent;
    private boolean mPermissionRequestPending;
    private UsbAccessory mAccessory;
    private ParcelFileDescriptor mFileDescriptor;
    private FileInputStream mInputStream;
    private FileOutputStream mOutputStream;

    private ProgressBar mProgress;
    private ToggleButton mBtnPower;
    private ToggleButton mBtnMotor1;
    private ToggleButton mBtnMotor2;
    private TextView mTextAck;
    private EditText mTextAngle;
    private Button mBtnSendAngle;
    private Button mBtnResetAngle;

    private ExecutorService mPool;

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        init();
    }

    private void init() {
        mPool = Executors.newSingleThreadExecutor();
        mProgress = (ProgressBar) findViewById(R.id.progress);
        mBtnPower = (ToggleButton) findViewById(R.id.btn_power);
        mBtnMotor1 = (ToggleButton) findViewById(R.id.btn_motor_1);
        mBtnMotor2 = (ToggleButton) findViewById(R.id.btn_motor_2);
        mTextAck = (TextView) findViewById(R.id.ack);
        mTextAngle = (EditText) findViewById(R.id.txt_angle);
        mBtnSendAngle = (Button) findViewById(R.id.btn_send_angle);
        mBtnResetAngle = (Button) findViewById(R.id.btn_reset_angle);

        // listeners
        mBtnSendAngle.setOnClickListener(this);
        mBtnResetAngle.setOnClickListener(this);
        //mBtnPower.setOnClickListener(this);
        //mBtnMotor1.setOnClickListener(this);
        //mBtnMotor2.setOnClickListener(this);

        // accessory
        setupAccessory();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_send_angle:
                if (!TextUtils.isEmpty(mTextAngle.getText())) {
                    try {

                        byte angle = Byte.parseByte(mTextAngle.getText().toString());
                        sendCommand(COMMAND_ROTATE, ACTION_ROTATE_BY_ANGLE, new byte[] {angle});
                    } catch (Exception e) {
                        Toast.makeText(this, "Invalid angle, please use values in range of 0 - 180", Toast.LENGTH_SHORT).show();
                    }
                }
                break;

            case R.id.btn_reset_angle:
                sendCommand(COMMAND_ROTATE, ACTION_RESET_ROTATION, null);
                break;
        }
    }

    public void onPowerClicked(View view) {
        // Is the toggle on?
        boolean on = ((ToggleButton) view).isChecked();

        if (on) {
            sendCommand(COMMAND_STAND_BY, ON, null);
        } else {
            sendCommand(COMMAND_STAND_BY, OFF, null);
        }
    }

    public void onMotor1Clicked(View view) {
        // Is the toggle on?
        boolean on = ((ToggleButton) view).isChecked();

        if (on) {
            sendCommand(COMMAND_MOTOR_1, ACTION_POWER_ON, new byte[] {DIRECTION_LEFT, (byte) 255});
        } else {
            sendCommand(COMMAND_MOTOR_1, ACTION_POWER_OFF, null);
        }
    }

    public void onMotor2Clicked(View view) {
        // Is the toggle on?
        boolean on = ((ToggleButton) view).isChecked();

        if (on) {
            sendCommand(COMMAND_MOTOR_2, ACTION_POWER_ON, new byte[] {DIRECTION_LEFT, (byte) 255});
        } else {
            sendCommand(COMMAND_MOTOR_2, ACTION_POWER_OFF, null);
        }
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        if (mAccessory != null) {
            return mAccessory;
        } else {
            return super.onRetainNonConfigurationInstance();
        }
    }

    @Override
    public void onResume() {
        Log.d(TAG, "Resuming");
        super.onResume();

        // register receiver
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
        registerReceiver(mUsbReceiver, filter);

        if (mInputStream != null && mOutputStream != null) {
            Log.d(TAG, "Resuming: streams were not null");
            return;
        }
        Log.d(TAG, "Resuming: streams were null");

        // Looking for more than 1 connected accessory, if found will return the one defined in the constant.
        UsbAccessory[] accessories = mUsbManager.getAccessoryList();
        UsbAccessory accessory = (accessories == null ? null : accessories[ACCESSORY_TO_RETURN]);

        // No accessory is connected to the device.
        if (accessory != null) {
            if (mUsbManager.hasPermission(accessory)) {
                openAccessory(accessory);
            } else {
                synchronized (mUsbReceiver) {
                    if (!mPermissionRequestPending) {
                        mUsbManager.requestPermission(accessory, mPermissionIntent);
                        mPermissionRequestPending = true;
                    }
                }
            }
        } else {
            Log.d(TAG, "onResume: mAccessory is null");
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "Application being paused");
        super.onPause();
        unregisterReceiver(mUsbReceiver);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Application being destroyed");
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    // Setting the accessory connection with the device.
    private void setupAccessory() {
        Log.d(TAG, "In setupAccessory");

        mUsbManager = UsbManager.getInstance(this);
        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);

        if (getLastNonConfigurationInstance() != null) {
            mAccessory = (UsbAccessory) getLastNonConfigurationInstance();
            openAccessory(mAccessory);
        }
    }

    // Open read and write to and from the Arduino device.
    private void openAccessory(UsbAccessory accessory) {
        Log.d(TAG, "In openAccessory");

        mFileDescriptor = mUsbManager.openAccessory(accessory);
        if (mFileDescriptor != null) {
            mAccessory = accessory;
            FileDescriptor fd = mFileDescriptor.getFileDescriptor();
            mInputStream = new FileInputStream(fd);
            mOutputStream = new FileOutputStream(fd);
            Thread thread = new Thread(null, this, TAG);
            thread.start();
            Log.d(TAG, "Attached");
        } else {
            Log.d(TAG, "openAccessory: accessory open failed");
        }
    }

    // Closing the read and write to and from the Arduino.
    private void closeAccessory() {
        Log.d(TAG, "In closeAccessory");

        try {
            if (mFileDescriptor != null) {
                mFileDescriptor.close();
            }
        } catch (IOException e) {
        } finally {
            mFileDescriptor = null;
            mAccessory = null;
        }
    }

    // The running thread.
    // It takes care of the communication between the Android and the Arduino.
    public void run() {
        int ret = 0;
        byte[] buffer = new byte[16384];
        int index;

        // Keeps reading messages forever.
        // There are probably a lot of messages in the buffer, each message 4 bytes.
        while (true) {
            try {
                ret = mInputStream.read(buffer);
            } catch (IOException e) {
                break;
            }

            if (ret > 0) {
                final boolean ack = buffer[0] == 1;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        enableButtons(true);
                        mProgress.setVisibility(View.GONE);
                        mTextAck.setText(String.format("Ack received: %b", ack));
                    }
                });
            }
        }
    }

    public void sendCommand(final byte command, final byte action, final byte[] data) {
        mProgress.setVisibility(View.VISIBLE);
        enableButtons(false);
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
                        Log.d(TAG, "sendCommand: Sending data to Arduino device: " + buffer);
                        mOutputStream.write(buffer.array());
                    } catch (IOException e) {
                        Log.d(TAG, "sendCommand: Send failed: " + e.getMessage());
                    }
                } else {
                    Log.d(TAG, "sendCommand: Send failed: mOutStream was null");
                }
            }
        });
    }

    private void enableButtons(boolean enabled) {
        mBtnPower.setEnabled(enabled);
        mBtnMotor1.setEnabled(enabled);
        mBtnMotor2.setEnabled(enabled);
    }

    // Obtaining permission to communicate with a device.
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbAccessory accessory = UsbManager.getAccessory(intent);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        openAccessory(accessory);
                    } else {
                        Log.d(TAG, "USB permission denied");
                    }
                }
            } else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
                UsbAccessory accessory = UsbManager.getAccessory(intent);
                if (accessory != null && accessory.equals(mAccessory)) {
                    Log.d(TAG, "Detached");
                    closeAccessory();
                }
            }
        }
    };
}