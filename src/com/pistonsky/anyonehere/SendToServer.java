package com.pistonsky.anyonehere;

import java.util.HashMap;
import java.util.UUID;

import com.google.gson.Gson;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

public class SendToServer extends AsyncTask<Bundle, Void, Boolean> {
	private static final String TAG = "SendToServerTask";
	private Context context;
	
	public SendToServer(Context c) {
		context = c;
	}
	
	@Override
	protected Boolean doInBackground(Bundle... data) {
		Log.i(TAG, "entered doInBackground");

			Gson gson = new Gson();
			ServerMessage message = new ServerMessage();
			message.message_id = UUID.randomUUID().toString();
			message.data = new HashMap<String,String>();
			Log.d(TAG, "data: ");
			for (String key: data[0].keySet()) {
				Log.d(TAG, key);
				message.data.put(key, data[0].getString(key));
			}
			String jsoned_data = gson.toJson(message);
			Log.d(TAG, "Sending message: " + jsoned_data);
			Intent sendIntent = new Intent(CloudSocket.BROADCAST_SEND_MESSAGE);
			sendIntent.putExtra("message", jsoned_data);
			context.sendBroadcast(sendIntent);
			
		return true;
	}
	
	public void execute(Bundle data) {
		doInBackground(data);
	}
}
