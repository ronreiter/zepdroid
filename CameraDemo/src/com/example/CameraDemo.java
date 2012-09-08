package com.example;

import android.app.Activity;
import android.content.res.AssetFileDescriptor;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.FrameLayout;
import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.TimerTask;

public class CameraDemo extends Activity implements ADKManager.Callback {
    private static final String TAG = "CameraDemo";

    ADKManager mADKManager;
    Camera camera;
    Preview preview;
    Button buttonClick;
    SocketIO socket = null;
    MediaPlayer player = null;


    /**
     * Called when the activity is first created.
     */

    class TakePicTask extends TimerTask {

        public void run() {

            preview.camera.takePicture(shutterCallback, rawCallback, jpegCallback);
        }
    }

    private class SentPicTask extends AsyncTask<byte[], Integer, Long> {

        @Override
        protected Long doInBackground(byte[]... params) {

            String url = "192.168.2.101:8099";

            Log.d(TAG, " params[0] wrote bytes: " + params[0].length);

            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost(url);

            InputStream dataStream = new ByteArrayInputStream(params[0]);

            try {
                Log.d(TAG, " before sending 0  " + dataStream.available());
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }

            InputStreamEntity reqEntity;
            try {
                reqEntity = new InputStreamEntity(dataStream, dataStream.available());
                reqEntity.setContentType("binary/octet-stream");

                Log.d(TAG, " before sending 1" + reqEntity.getContentLength());

                //reqEntity.setChunked(true); // Send in multiple parts if needed
                httppost.setEntity(reqEntity);
                try {
                    HttpResponse response = httpclient.execute(httppost);
                } catch (ClientProtocolException e) {
                    Log.d(TAG, "ClientProtocolException");
                    e.printStackTrace();
                } catch (IOException e) {
                    Log.d(TAG, "IOException");
                    e.printStackTrace();
                } catch (Exception e) {
                    Log.d(TAG, "Exception");
                    e.printStackTrace();
                }
            } catch (IOException e1) {
                // TODO Auto-generated catch block
                e1.printStackTrace();
            }


            Log.d(TAG, "Async op");
            return null;
        }


    }


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mADKManager = new ADKManager(this, this);
        preview = new Preview(this);
        ((FrameLayout) findViewById(R.id.preview)).addView(preview);

        //Timer getPic = new Timer();
        //getPic.schedule(new TakePicTask(), 1000*3);


        try {
            socket = new SocketIO("192.168.2.101:8099");


        } catch (Exception e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        socket.connect(new IOCallback() {


            @Override
            public void onDisconnect() {
                System.out.println("Connection terminated.");
            }

            @Override
            public void onConnect() {
                System.out.println("Connection established");
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
            public void on(String event, IOAcknowledge ack, Object... args) {
                Log.d(TAG, "on");
                // TODO Auto-generated method stub

                Log.d(TAG, event);

                if (args[0].equals("ngn1start") && args[1].equals("up")) {
                    Log.d(TAG, "ng1Start up");

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
                    Object[] resp = {"hello", "world"};
                    socket.emit("test", resp);
                    byte speed = (byte) 255;
                    sendCommand(
                            ADKManager.COMMAND_MOTOR_2,
                            ADKManager.ACTION_POWER_ON,
                            new byte[]{ADKManager.DIRECTION_LEFT, (byte) speed});
                }
                if (args[0].equals("forward") && args[1].equals("down")) {
                    Log.d(TAG, "down: forward");
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
                    byte speed = (byte) 255;
                    sendCommand(
                            ADKManager.COMMAND_MOTOR_2,
                            ADKManager.ACTION_POWER_ON,
                            new byte[]{ADKManager.DIRECTION_RIGHT, (byte) speed});
                }
                if (args[0].equals("back") && args[1].equals("down")) {
                    Log.d(TAG, "down: back");
                }

                if (args[0].equals("elevate_up") && args[1].equals("up")) {
                    Log.d(TAG, "up: elevate_up");
                    byte speed = (byte) 255;
                    sendCommand(
                            ADKManager.COMMAND_MOTOR_1,
                            ADKManager.ACTION_POWER_ON,
                            new byte[]{ADKManager.DIRECTION_LEFT, (byte) speed});
                }
                if (args[0].equals("elevate_up") && args[1].equals("down")) {
                    Log.d(TAG, "down: elevate_up");
                }

                if (args[0].equals("elevate_down") && args[1].equals("up")) {
                    Log.d(TAG, "up: elevate_down");
                    byte speed = (byte) 255;
                    sendCommand(
                            ADKManager.COMMAND_MOTOR_1,
                            ADKManager.ACTION_POWER_ON,
                            new byte[]{ADKManager.DIRECTION_RIGHT, (byte) speed});
                }
                if (args[0].equals("elevate_down") && args[1].equals("down")) {
                    Log.d(TAG, "down: elevate_down");
                }

                if (args[0].equals("music") && args[1].equals("up")) {
                    Log.d(TAG, "up: music");
                    // toggle playing
                    if (CameraDemo.this.player.isPlaying()) {
                        CameraDemo.this.player.stop();

                    } else {
                        CameraDemo.this.player.start();
                    }
                }
                if (args[0].equals("music") && args[1].equals("down")) {
                    Log.d(TAG, "down: music");
                }

                if (args[0].equals("picture") && args[1].equals("up")) {
                    Log.d(TAG, "up: picture");

                    preview.camera.takePicture(shutterCallback, rawCallback, null, jpegCallback);

                    //Object[] resp = {"hello"};
                    //socket.emit("get_img", resp);
                }

                if (args[0].equals("picture") && args[1].equals("down")) {
                    Log.d(TAG, "down: picture");
                }


            }

            @Override
            public void onError(SocketIOException socketIOException) {
                Log.d(TAG, "onError");
            }
        }
        );

        try {
            player = new MediaPlayer();
            AssetFileDescriptor afd = CameraDemo.this.getResources().openRawResourceFd(R.raw.wholelottalove);
            player.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
            player.setAudioStreamType(AudioManager.STREAM_MUSIC);
            player.setLooping(false);
            player.prepare();

        } catch (IOException e) {
            e.printStackTrace();
        }

        buttonClick = (Button) findViewById(R.id.buttonClick);
        buttonClick.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                //preview.camera.takePicture(shutterCallback, rawCallback, jpegCallback);
                //AudioManager audioManager = (AudioManager) getSystemService(CameraDemo.AUDIO_SERVICE);
                //audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 20, 0);

                // toggle playing
                if (CameraDemo.this.player.isPlaying()) {
                    CameraDemo.this.player.stop();

                } else {
                    CameraDemo.this.player.start();

                }

            }
        });

        Log.d(TAG, "onCreate'd");
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
    }

    @Override
    public void onADKAckReceived(boolean ack) {
        Log.d(TAG, "onADKAckReceived: " + ack);
    }

    @Override
    public void onPause() {
        super.onPause();
        mADKManager.disconnect();
    }

    @Override
    public void onResume() {
        super.onResume();
        mADKManager.connect();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        android.os.Process.killProcess(android.os.Process.myPid());
    }


    ShutterCallback shutterCallback = new ShutterCallback() {
        public void onShutter() {
            Log.d(TAG, "onShutter'd");
        }
    };

    /**
     * Handles data for raw picture
     */
    PictureCallback rawCallback = new PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            Log.d(TAG, "onPictureTaken - raw");
        }
    };

    /**
     * Handles data for jpeg picture
     */
    PictureCallback jpegCallback = new PictureCallback() {
        public void onPictureTaken(byte[] data, Camera camera) {
            FileOutputStream outStream = null;
            //try {
            // write to local sandbox file system
//				outStream = CameraDemo.this.openFileOutput(String.format("%d.jpg", System.currentTimeMillis()), 0);	
            // Or write to sdcard
            //outStream = new FileOutputStream(String.format("/sdcard/%d.jpg", System.currentTimeMillis()));
            //outStream.write(data);
            //outStream.close();
            Log.d(TAG, "onPictureTaken - wrote bytes: " + data.length);
            System.out.print("Length:" + data.length);
            new SentPicTask().execute(data);

            //Object[] resp = {data};
            //socket.emit("get_img", resp);


            /*} catch (FileNotFoundException e) {
                   e.printStackTrace();
               } catch (IOException e) {
                   e.printStackTrace();
               } finally {
               }*/
            Log.d(TAG, "onPictureTaken - jpeg");
        }
    };

}
