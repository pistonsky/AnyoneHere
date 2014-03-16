package com.pistonsky.anyonehere;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class CloudSocket extends AsyncTask<CloudData, Integer, Integer> {

	private final String TAG = "CloudSocket";
	public static final String BROADCAST_SEND_MESSAGE = "SEND";
	public static final String BROADCAST_TERMINATE = "LynksSocket.TERMINATE";
	/**
	 * The time given to receive data from socket, needed for AsyncTask termination purpose
	 */
	private static final int READ_TIMEOUT = 5000; // 5 seconds
	/**
	 * Time given to connect the socket
	 */
	private static final int CONNECT_TIMEOUT = 2000; // 2 seconds
	private Socket socket;
	private Context context;
	private Handler threadHandler;
	
	private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String message = intent.getStringExtra("message");
			if (message != null) {
				send(message);
			}
		}
	};
	
	private final BroadcastReceiver terminator = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			cancel(true);
		}
	};
	
	public CloudSocket(Context c, Handler h) {
		context = c;
		threadHandler = h;
		context.registerReceiver(receiver, new IntentFilter(BROADCAST_SEND_MESSAGE));
		context.registerReceiver(terminator, new IntentFilter(BROADCAST_TERMINATE));
	}
	
	@Override
	protected Integer doInBackground(CloudData... datas) {
		int interval = 1000;
		while (!isCancelled()) {
			try {
				Log.i(TAG, "Trying to create new Socket on pistonsky.jelasticloud.com/server.php:8099...");
				socket = new Socket();
				socket.connect(new InetSocketAddress("pistonsky.jelasticloud.com/server.php", 8099), CloudSocket.CONNECT_TIMEOUT);
				socket.setSoTimeout(CloudSocket.READ_TIMEOUT); // 2 seconds read timeout
				interval = 1000; // reset
				App.socket = socket;
				Intent intent = new Intent(App.BROADCAST_ACTION);
				intent.putExtra("SERVER_STATUS", true);
				Message threadMessage = new Message();
				threadMessage.what = App.CODE_SOCKET_SERVER_CONNECTED;
				threadMessage.obj = intent;
				threadHandler.sendMessage(threadMessage);

				Log.i(TAG, "OK! Sending installation id...");
				send(Installation.id(context));

				while ( !isCancelled() && (socket != null) && socket.isConnected()) {
					Log.d(TAG, "We're in the read loop");
					try {
						BufferedReader input = new BufferedReader(
								new InputStreamReader(socket.getInputStream()));
						String st = null;
						Log.d(TAG, "Trying to read a line...");
						while ((!isCancelled()) && ((st = input.readLine()) != null) && (!st.equals(""))) {
							Log.d(TAG, "Read: " + st);
							Message m = new Message();
							m.what = App.CODE_SOCKET_SERVER_ANSWER;
							m.obj = st;
							threadHandler.sendMessage(m);
						}
						if (st == null) {
							socket.close();
							socket = null;
						}
					} catch (InterruptedIOException e) {
						Log.e(TAG, "Socket timed out while receiving data.");
					} catch (IOException e) {
						e.printStackTrace();
						Log.e(TAG, "There was an IOException.");
						break;
					}
				}
			} catch (SocketTimeoutException e) {
				Log.e(TAG, "Timed out. No response.");

				Intent serverStatusIntent = new Intent("SERVER_STATUS");
				serverStatusIntent.putExtra("status", "offline");
				context.sendBroadcast(serverStatusIntent);
				App.online = false;
				/*}*/
				try {
					Thread.sleep(interval);
					if (interval < 60*1000) // maximum interval between connects is 1 minute
						interval = interval * 2;
				} catch (InterruptedException ee) {

				}
			} catch (IOException e) {
				Log.e(TAG, "Something went wrong.");
				Intent serverStatusIntent = new Intent("SERVER_STATUS");
				serverStatusIntent.putExtra("status", "offline");
				App.online = false;
				context.sendBroadcast(serverStatusIntent);
				try {
					Thread.sleep(interval);
					if (interval < 60*1000) // maximum interval between connects is 1 minute
						interval = interval * 2;
				} catch (InterruptedException ee) {

				}
			}
		} 
		if (isCancelled()) {
			Log.i(TAG,"The task was cancelled. Closing the socket and unregistering receivers.");
			try {
				socket.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
			context.unregisterReceiver(receiver);
			context.unregisterReceiver(terminator);
			context.stopService(new Intent(context,App.class));
		}
		return null;
	}

	protected void send(String msg) {
		if (socket != null && socket.isConnected()) {
			if (msg != null) {
				Log.d(TAG, "Sending message: " + msg);
				try {
					PrintWriter out = new PrintWriter(new BufferedWriter(
							new OutputStreamWriter(socket.getOutputStream())),
							true);
					out.println(msg);
				} catch (Exception e) {
					Log.e(TAG, "TCP Error", e);
					return;
				}
			}
		}
	}
	
	protected void keepSending(Socket socket) {
        while (socket != null && socket.isConnected()) {
            if (!App.SOCKET_MESSAGES.isEmpty()) {
                Log.d(TAG, "Sending message: " + App.SOCKET_MESSAGES.element());
                try {
                    PrintWriter out = new PrintWriter(new BufferedWriter(
                            new OutputStreamWriter(socket.getOutputStream())),
                            true);
                    out.println(App.SOCKET_MESSAGES.remove());
                } catch (Exception e) {
                    Log.e(TAG, "S: Error", e);
                    return;
                }
            }
        }
    }
}
