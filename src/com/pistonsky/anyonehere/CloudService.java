package com.pistonsky.anyonehere;

import java.lang.reflect.Type;
import java.util.Map;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

public class CloudService extends Service {
	
	private final String TAG = "CloudService";
	/**
	 * CloudSocket is responsible for communicating with the server through TCP socket
	 */
	private CloudSocket socket;
	
	public Handler myUpdateHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case App.ERR_SOCKET_SERVER_UNAVAILABLE:
                startService();
                sendBroadcast((Intent) msg.obj);
                break;

            case App.CODE_SOCKET_SERVER_ANSWER:
            	// server has answered, need to process the answer here
            	
                break;

            case App.CODE_SOCKET_SERVER_CONNECTED:
                sendBroadcast((Intent) msg.obj);
                break;
            default:
                break;
            }
            super.handleMessage(msg);
        }
    };
	
	public class LocalBinder extends Binder {
		CloudService getService() {
			return CloudService.this;
		}
	}
	
	private LocalBinder mBinder = new LocalBinder();
	
	@Override
	public void onCreate() {
		super.onCreate();
		Log.i(TAG, "We are in onCreate()");
		startService();
	}
	
	@Override
    public void onDestroy() {
        Toast.makeText(this, "Service destroyed", Toast.LENGTH_LONG).show();
        Log.v(TAG, "Service destroyed.");
    }

	@Override
	public IBinder onBind(Intent arg0) {
		Log.v(TAG, "---> Service binded.");
		return null;
	}
	
	private void startService() {
		try {
			openConnection();
		} catch (InterruptedException e) {
			Log.e(TAG, "startService failed.");
			e.printStackTrace();
		}
	}
	
	private void openConnection() throws InterruptedException {
		try {
			CloudData data = new CloudData();
			data.ctx = this;
			Log.i(TAG, "Trying to LynksSocket().execute(data)...");
			socket = new CloudSocket(this, myUpdateHandler);
			socket.execute(data);
			Log.i(TAG, "OK!");
		} catch (Exception e) {
			Log.e(TAG, "openConnection failed.");
			e.printStackTrace();
		}
	}

}
