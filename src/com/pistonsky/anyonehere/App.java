package com.pistonsky.anyonehere;

import java.net.Socket;
import java.util.AbstractQueue;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;

import android.app.Application;

public class App extends Application {

	public static final String BROADCAST_ACTION = "com.pistonsky.anyonehere.event";
	public static final int ERR_SOCKET_SERVER_UNAVAILABLE = 2;
	public static final int CODE_SOCKET_SERVER_ANSWER = 4;
	public static final int CODE_SOCKET_SERVER_CONNECTED = 8;
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
	
}
