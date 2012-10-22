package com.example;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.AssetFileDescriptor;
import android.hardware.Camera;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
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

public class Zepdroid extends Activity {
	private static final String TAG = "ZepDroid";
    //private static final String BASE_URI = "http://192.168.2.101:8099";

	Preview preview;
    private ZepDroidService mService;
    private boolean mBound;

    /** Called when the activity is first created. */
	private class ZepdroidClientTask extends AsyncTask<byte[], Integer, Long> {

		@Override
		protected Long doInBackground(byte[]... params) {
			String url = "http://zepdroid.com:8099";

			onLog(" params[0] wrote bytes: " + params[0].length);

			HttpClient httpclient = new DefaultHttpClient();
			HttpPost httppost = new HttpPost(url);

			InputStream dataStream = new ByteArrayInputStream(params[0]);

			try {
				onLog(" before sending 0  " + dataStream.available() );
			} catch (IOException e1) {
				e1.printStackTrace();
			}

			InputStreamEntity reqEntity;
			try {
				reqEntity = new InputStreamEntity(dataStream, dataStream.available());
				reqEntity.setContentType("binary/octet-stream");

			    onLog(" before sending 1" + reqEntity.getContentLength() );

			    //reqEntity.setChunked(true); // Send in multiple parts if needed
			    httppost.setEntity(reqEntity);
			    try {
					HttpResponse response = httpclient.execute(httppost);
				} catch (ClientProtocolException e) {
					onLog("ClientProtocolException");
					e.printStackTrace();
				} catch (IOException e) {
					onLog("IOException");
					e.printStackTrace();
				} catch (Exception e) {
					onLog("Exception");
					e.printStackTrace();
				}
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			onLog("Async op");
			return null;
		}
	}

    public void takePicture() {
        preview.camera.takePicture(shutterCallback, rawCallback, null, jpegCallback);
    }



	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

        bindZepDroidService();

		preview = new Preview(this);
		((FrameLayout) findViewById(R.id.preview)).addView(preview);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        unbindZepDroidService();
        // android.os.Process.killProcess(android.os.Process.myPid());
    }


    ShutterCallback shutterCallback = new ShutterCallback() {
        public void onShutter() {
            onLog("onShutter'd");
        }
    };

	/** Handles data for raw picture */
	PictureCallback rawCallback = new PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			onLog("onPictureTaken - raw");
		}
	};

	/** Handles data for jpeg picture */
	PictureCallback jpegCallback = new PictureCallback() {
		public void onPictureTaken(byte[] data, Camera camera) {
			FileOutputStream outStream = null;
			//try {
				// write to local sandbox file system
//				outStream = Zepdroid.this.openFileOutput(String.format("%d.jpg", System.currentTimeMillis()), 0);
				// Or write to sdcard
				//outStream = new FileOutputStream(String.format("/sdcard/%d.jpg", System.currentTimeMillis()));	
				//outStream.write(data);
				//outStream.close();
				onLog("onPictureTaken - wrote bytes: " + data.length);
				System.out.print("Length:" + data.length);
				new ZepdroidClientTask().execute(data);
				
				//Object[] resp = {data};
				//socket.emit("get_img", resp);
					
				
			/*} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
			}*/
			onLog("onPictureTaken - jpeg");
		}
	};


    ///////////////////////////
    // Service shit
    ///////////////////////////
    protected ServiceConnection mServerConn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            onLog("onServiceConnected");
            ZepDroidService.LocalBinder binder = (ZepDroidService.LocalBinder) service;
            mService = binder.getService();
            binder.setActivity(Zepdroid.this);
            mBound = true;

        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            onLog("onServiceDisconnected");
            mBound = false;
        }
    };

    public void bindZepDroidService() {
        bindService(new Intent(this, ZepDroidService.class), mServerConn, Context.BIND_AUTO_CREATE);
    }

    public void unbindZepDroidService() {
        unbindService(mServerConn);
    }


    public void onLog(String s) {
        if (mService != null) {
            mService.onLog(s);
        } else {
            Log.d(TAG, s);
        }
    }

}
