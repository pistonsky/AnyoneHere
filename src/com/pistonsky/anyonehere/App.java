package com.pistonsky.anyonehere;

import java.net.Socket;
import java.util.AbstractQueue;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class App extends Application {
	
	private static final String TAG = "App";

	public static final String BROADCAST_ACTION = "com.pistonsky.anyonehere.event";
	public static final String SHARED_PREFERENCES = "com.pistonsky.anyonehere";
	public static final int ERR_SOCKET_SERVER_UNAVAILABLE = 2;
	public static final int CODE_SOCKET_SERVER_ANSWER = 4;
	public static final int CODE_SOCKET_SERVER_CONNECTED = 8;
	
	private String mLogin;
	private String mPassword;
	private String mSalt = null;
	
	/**
	 * socket used for connection with the server
	 */
	public static Socket socket;
	/**
	 * this boolean variable reflects the online status
	 */
	public static boolean online = false;
	/**
	 * messages waiting to be sent to the server
	 */
	public static AbstractQueue<String> SOCKET_MESSAGES = new ArrayBlockingQueue<String>(32);
	
	public String getMsgId() {
		return UUID.randomUUID().toString();
	}
	
	public String getLogin() {
		return mLogin;
	}
	
	public void setLogin(String login) {
		mLogin = login;
	}
	
	public String getPassword() {
		return mPassword;
	}
	
	public void setPassword(String password) {
		mPassword = password;
	}
	
	public String getSalt() {
		return mSalt;
	}
	
	public void setSalt(String s) {
		mSalt = s;
	}
	
    protected void onMessage(Context context, Intent intent) {
    	Log.v(TAG, "Incoming message...");
    	String event = intent.getStringExtra("event");
    	if (event != null) {
	        Log.i(TAG, "Received new event: "+event);
	        if (event.equals("SALT")) {
	        	String salt = intent.getStringExtra("salt");
	        	if (salt != null) {
	        		Log.v(TAG, "Salt received: " + salt);
	        		setSalt(salt);
	        		if (LoginActivity.active) {
	        			Intent loginIntent = new Intent("LOGIN");
	        			loginIntent.putExtra("status","SALT_RECEIVED");
	        			sendBroadcast(loginIntent);
	        		}
	        	}
	        } else if (event.equals("LOGOUT")) {
	        	if (LoginActivity.active) {
	        		Log.i(TAG, "We are already logged out, LoginActivity.active is true.");
	        	} else {
	        		Log.i(TAG, "Sending LOGOUT Broadcast");
	        		Intent logoutIntent = new Intent("LOGOUT");
	        		sendBroadcast(logoutIntent);
	        	}
	        	String salt = intent.getStringExtra("salt");
	        	if (salt != null) {
	        		Log.v(TAG, "Salt received: " + salt);
	        		setSalt(salt);
	        	}
	        	online = false;
	        } else if (event.equals("LOGIN")) {
	        	String status = intent.getStringExtra("status");
	        	if (status != null) {
	        		// We are logged in.
	        		// If the user is still on "signing in..." screen
	        		// Or if he is on login screen
	        		// Finish LoginActivity and put him on the main activity
	        		if (LoginActivity.active) {
	        			Intent loginIntent = new Intent("LOGIN");
	        			loginIntent.putExtra("status",status);
	        			String login = intent.getStringExtra("login");
	        			if (login != null)
	        				loginIntent.putExtra("login", login);
	        			sendBroadcast(loginIntent);
	        		}
	        	}
	        	String salt = intent.getStringExtra("salt");
	        	if (salt != null) {
	        		Log.v(TAG, "Salt received: " + salt);
	        		setSalt(salt);
	        	}
	        }
    	}
    }
}
