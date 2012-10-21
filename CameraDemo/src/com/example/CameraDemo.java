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
import android.widget.Toast;
import io.socket.IOAcknowledge;
import io.socket.IOCallback;
import io.socket.SocketIO;
import io.socket.SocketIOException;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONObject;

import java.io.*;
import java.net.*;
import java.util.Timer;
import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.TimerTask;

public class CameraDemo extends Activity implements ADKManager.Callback {
	private static final String TAG = "ZepDroid";
    private static final String BASE_URI = "http://zepdroid.com:8099";
    //private static final String BASE_URI = "http://192.168.2.101:8099";

    ADKManager mADKManager;
	Preview preview;
	Button buttonClick;
    SocketIO socket = null;
    MediaPlayer player = null;
    byte mLastCommand;
    byte mLastAction;

    int period = 10 * 1000;  // repeat every sec.
    Timer timer = new Timer();

	/** Called when the activity is first created. */
	class TakePicTask extends TimerTask{
		
		public void run(){
			
			preview.camera.takePicture(shutterCallback, rawCallback, jpegCallback);
		}
	}
	
	private class SentPicTask extends AsyncTask<byte[], Integer, Long> {

		@Override
		protected Long doInBackground(byte[]... params) {
			
			String url = "http://zepdroid.com:8099";
			
			Log.d(TAG, " params[0] wrote bytes: " + params[0].length);
			
			HttpClient httpclient = new DefaultHttpClient();
			HttpPost httppost = new HttpPost(url);
			
			InputStream dataStream = new ByteArrayInputStream(params[0]);
			
			try {
				Log.d(TAG, " before sending 0  " + dataStream.available() );
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			
			InputStreamEntity reqEntity;
			try {
				reqEntity = new InputStreamEntity(dataStream, dataStream.available());
				reqEntity.setContentType("binary/octet-stream");
			    
			    Log.d(TAG, " before sending 1" + reqEntity.getContentLength() );
			    
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

        mADKManager.connect();

		((FrameLayout) findViewById(R.id.preview)).addView(preview);
		
		//Timer getPic = new Timer();
		//getPic.schedule(new TakePicTask(), 1000*3);

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
                }, 0, period);
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
                        Toast.makeText(CameraDemo.this, args[0].toString(), Toast.LENGTH_SHORT).show();
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
                    Log.e(TAG, "onError", socketIOException);
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
        mLastCommand = command;
        mLastAction = action;

    }

    @Override
    public void onADKAckReceived(boolean ack) {
        Log.d(TAG, "onADKAckReceived: " + ack);
        socket.emit("ack", mLastCommand, mLastAction);
    }

    @Override
    public void onLog(final String s) {
        /*
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(CameraDemo.this, s, Toast.LENGTH_SHORT).show();
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
    public void onDestroy() {
        super.onDestroy();
        mADKManager.disconnect();
        android.os.Process.killProcess(android.os.Process.myPid());
    }


    ShutterCallback shutterCallback = new ShutterCallback() {
        public void onShutter() {
            Log.d(TAG, "onShutter'd");
        }
    };

	/** Handles data for raw picture */
	PictureCallback rawCallback = new PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			Log.d(TAG, "onPictureTaken - raw");
		}
	};

	/** Handles data for jpeg picture */
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
