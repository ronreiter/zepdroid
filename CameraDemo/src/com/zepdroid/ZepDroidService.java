package com.zepdroid;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;
import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.Timer;
import java.util.TimerTask;

public class ZepDroidService extends Service implements ADKManager.Callback {
    // Unique Identification Number for the Notification.
    // We use it on Notification start, and to cancel it.
    private static final int NOTIFICATION = 1;
    private static final String BASE_URI = "http://zepdroid.com:8099";
    private static final int KEEP_ALIVE_PERIOD = 10000;
    public static final String TAG = "ZepDroid";

    private ADKManager mADKManager;

    private SocketIO socket = null;
    private Timer timer;
    private byte mLastCommand;
    private byte mLastAction;
    Handler mHandler;
    MediaPlayer player = null;
    Zepdroid mZepDroidActivity;

    // This is the object that receives interactions from clients.  See
    // RemoteService for a more complete example.
    private final IBinder mBinder = new LocalBinder();

    /**
     * Class for clients to access.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with
     * IPC.
     */
    public class LocalBinder extends Binder {
        ZepDroidService getService() {
            return ZepDroidService.this;
        }

        public void setActivity(Zepdroid zepdroid) {
            mZepDroidActivity = zepdroid;
        }
    }


    public void runOnUiThread(Runnable action) {
        mHandler.post(action);
    }

    @Override
    public void onCreate() {
        mHandler = new Handler();

        mADKManager = new ADKManager(this, this);
        mADKManager.connect();

        try {
            player = new MediaPlayer();
            AssetFileDescriptor afd = getResources().openRawResourceFd(R.raw.wholelottalove);
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setLooping(false);
            player.prepare();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "Received start id " + startId + ": " + intent);

        try {
            socket = new SocketIO(BASE_URI);
        } catch (Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        socket.connect(new IOCallback() {

            @Override
            public void onDisconnect() {
                System.out.println("Connection terminated.");
            }

            public void onConnect() {
                System.out.println("Connection established");

                timer.scheduleAtFixedRate(new TimerTask() {
                    public void run() {

                        System.out.println("HTTP GET");

                        BufferedReader in = null;
                        try {
                            HttpClient client = new DefaultHttpClient();
                            HttpGet request = new HttpGet();
                            request.setURI(new URI(BASE_URI + "/keepalive"));
                            client.execute(request);

                            /*in = new BufferedReader
                                    (new InputStreamReader(response.getEntity().getContent()));
                            StringBuffer sb = new StringBuffer("");
                            String line = "";
                            String NL = System.getProperty("line.separator");
                            while ((line = in.readLine()) != null) {
                                sb.append(line + NL);
                            }
                            in.close();
                            String page = sb.toString();
                            System.out.println(page);   */
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (URISyntaxException e) {
                            e.printStackTrace();
                        }
                    }
                }, 0, KEEP_ALIVE_PERIOD);
            }


            @Override
            public void onMessage(String data, IOAcknowledge ack) {
                // TODO Auto-generated method stub


                Log.d(TAG, "onMessage");


            }


            @Override
            public void onMessage(JSONObject json, IOAcknowledge ack) {

                // TODO Auto-generated method stub
                Log.d(TAG, "onMessagejson");

            }


            @Override
            public void on(String event, IOAcknowledge ack, final Object... args) {
                Log.d(TAG, "on " + args[0]);

                if (args[0].equals("keep_alive")) {
                    Log.d(TAG, "got back keep alive from socket");
                    return;
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(ZepDroidService.this, args[0].toString(), Toast.LENGTH_SHORT).show();
                    }
                });

                if (args[0].equals("ngn1start") && args[1].equals("up")) {
                    Log.d(TAG, "ng1Start up");
                    sendCommand(
                            ADKManager.COMMAND_MOTOR_2,
                            ADKManager.ACTION_POWER_ON,
                            null);
                }

                if (args[0].equals("ngn1stop") && args[1].equals("up")) {
                    Log.d(TAG, "ng1Stop up");
                    sendCommand(
                            ADKManager.COMMAND_MOTOR_2,
                            ADKManager.ACTION_POWER_OFF,
                            null);

                }

                if (args[0].equals("ngn2start") && args[1].equals("up")) {
                    Log.d(TAG, "ng2Start up");
                    sendCommand(
                            ADKManager.COMMAND_MOTOR_1,
                            ADKManager.ACTION_POWER_ON,
                            null);
                }

                if (args[0].equals("ngn2stop") && args[1].equals("up")) {
                    Log.d(TAG, "ng2Stop up");
                    sendCommand(
                            ADKManager.COMMAND_MOTOR_1,
                            ADKManager.ACTION_POWER_OFF,
                            null);
                }

                if (args[0].equals("center") && args[1].equals("up")) {
                    Log.d(TAG, "center up");
                    sendCommand(
                            ADKManager.COMMAND_ROTATE,
                            ADKManager.ACTION_RESET_ROTATION,
                            null);

                }

                if (args[0].equals("power_on") && args[1].equals("up")) {
                    Log.d(TAG, "power on");
                    sendCommand(ADKManager.COMMAND_STAND_BY,
                            ADKManager.ON,
                            null);
                }

                if (args[0].equals("power_off") && args[1].equals("up")) {
                    Log.d(TAG, "power off");
                    sendCommand(ADKManager.COMMAND_STAND_BY,
                            ADKManager.OFF,
                            null);
                }

                if (args[0].equals("forward") && args[1].equals("up")) {
                    Log.d(TAG, "up: forward");
                    sendCommand(
                            ADKManager.COMMAND_MOTOR_2,
                            ADKManager.ACTION_POWER_OFF,
                            null);
                }
                if (args[0].equals("forward") && args[1].equals("down")) {
                    Log.d(TAG, "down: forward");
                    byte speed = (byte) 255;
                    sendCommand(
                            ADKManager.COMMAND_MOTOR_2,
                            ADKManager.ACTION_POWER_ON,
                            new byte[]{ADKManager.DIRECTION_LEFT, speed});

                }
                if (args[0].equals("left") && args[1].equals("up")) {
                    Log.d(TAG, "up: left");
                    sendCommand(
                            ADKManager.COMMAND_ROTATE,
                            ADKManager.ACTION_END_ROTATE,
                            null);
                }
                if (args[0].equals("left") && args[1].equals("down")) {
                    Log.d(TAG, "down: left");
                    sendCommand(
                            ADKManager.COMMAND_ROTATE,
                            ADKManager.ACTION_START_ROTATE,
                            new byte[]{ADKManager.DIRECTION_LEFT});
                }
                if (args[0].equals("right") && args[1].equals("up")) {
                    Log.d(TAG, "up: right");
                    sendCommand(
                            ADKManager.COMMAND_ROTATE,
                            ADKManager.ACTION_END_ROTATE,
                            null);
                }
                if (args[0].equals("right") && args[1].equals("down")) {
                    Log.d(TAG, "down: right");
                    sendCommand(
                            ADKManager.COMMAND_ROTATE,
                            ADKManager.ACTION_START_ROTATE,
                            new byte[]{ADKManager.DIRECTION_RIGHT});
                }
                if (args[0].equals("back") && args[1].equals("up")) {
                    Log.d(TAG, "up: back");
                    sendCommand(
                            ADKManager.COMMAND_MOTOR_2,
                            ADKManager.ACTION_POWER_OFF,
                            null);
                }
                if (args[0].equals("back") && args[1].equals("down")) {
                    Log.d(TAG, "down: back");
                    byte speed = (byte) 255;
                    sendCommand(
                            ADKManager.COMMAND_MOTOR_2,
                            ADKManager.ACTION_POWER_ON,
                            new byte[]{ADKManager.DIRECTION_RIGHT, (byte) speed});
                }

                if (args[0].equals("elevate_up") && args[1].equals("up")) {
                    Log.d(TAG, "up: elevate_up");
                    byte speed = (byte) 255;
                    sendCommand(
                            ADKManager.COMMAND_MOTOR_1,
                            ADKManager.ACTION_POWER_OFF,
                            null);
                }
                if (args[0].equals("elevate_up") && args[1].equals("down")) {
                    Log.d(TAG, "down: elevate_up");
                    byte speed = (byte) 255;
                    sendCommand(
                            ADKManager.COMMAND_MOTOR_1,
                            ADKManager.ACTION_POWER_ON,
                            new byte[]{ADKManager.DIRECTION_LEFT, (byte) speed});
                }

                if (args[0].equals("elevate_down") && args[1].equals("up")) {
                    Log.d(TAG, "up: elevate_down");
                    sendCommand(
                            ADKManager.COMMAND_MOTOR_1,
                            ADKManager.ACTION_POWER_OFF,
                            null);
                }
                if (args[0].equals("elevate_down") && args[1].equals("down")) {
                    Log.d(TAG, "down: elevate_down");
                    byte speed = (byte) 255;
                    sendCommand(
                            ADKManager.COMMAND_MOTOR_1,
                            ADKManager.ACTION_POWER_ON,
                            new byte[]{ADKManager.DIRECTION_RIGHT, (byte) speed});
                }

                if (args[0].equals("music") && args[1].equals("up")) {
                    Log.d(TAG, "up: music");
                    // toggle playing
                    if (ZepDroidService.this.player.isPlaying()) {
                        ZepDroidService.this.player.stop();

                    } else {
                        ZepDroidService.this.player.start();
                    }
                }
                if (args[0].equals("music") && args[1].equals("down")) {
                    Log.d(TAG, "down: music");
                }

                if (args[0].equals("picture") && args[1].equals("up")) {
                    Log.d(TAG, "up: picture");

                    mZepDroidActivity.takePicture();


                    //Object[] resp = {"hello"};
                    //socket.emit("get_img", resp);
                }

                if (args[0].equals("picture") && args[1].equals("down")) {
                    Log.d(TAG, "down: picture");
                }

            }

            @Override
            public void onError(SocketIOException socketIOException) {
                Log.e(TAG, "onError", socketIOException);
            }
        }
        );

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mADKManager.disconnect();

        // Tell the user we stopped.
        Toast.makeText(this, "local service stopped", Toast.LENGTH_SHORT).show();
    }

    /**
     * Send command to the ADK
     *
     * @param command
     * @param action
     * @param data
     */
    private void sendCommand(final byte command, final byte action, final byte[] data) {
        // We should show the user
        // mProgress.setVisibility(View.VISIBLE);
        // enableButtons(false);
        mADKManager.sendCommand(command, action, data);
        mLastCommand = command;
        mLastAction = action;

    }

    public void onADKAckReceived(boolean ack) {
        Log.d(TAG, "onADKAckReceived: " + ack);
        socket.emit("ack", mLastCommand, mLastAction);
    }

    public void onLog(final String s) {
        /*
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(Zepdroid.this, s, Toast.LENGTH_SHORT).show();
            }
        });
        */

        //socket.emit("log", s);

        /*
        try {
            URL url = new URL(BASE_URI + "/log?s=" + s);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.getInputStream();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        */

        HttpClient client = new DefaultHttpClient();
        HttpGet request = new HttpGet();
        try {
            request.setURI(new URI(BASE_URI + "/log?s=" + URLEncoder.encode(s)));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        try {
            client.execute(request);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

}