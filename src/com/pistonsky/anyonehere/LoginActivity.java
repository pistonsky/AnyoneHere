package com.pistonsky.anyonehere;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;

public class LoginActivity extends Activity {
	
	private static final String TAG = "LoginActivity";
	
	/**
	 * The time given before app will show "There is no connection" while logging in the user
	 */
	private static final int LOGIN_TIMEOUT = 10000; // in milliseconds
	
	public static boolean active = false; // to check if activity is running
	
	private String mLogin;
	private String mPassword;
	private EditText mLoginView;
	private EditText mPasswordView;
	private CheckBox mSavePasswordView;
	private View mLoginFormView;
	private View mLoginStatusView;
	private TextView mLoginStatusMessageView;
	
	private boolean doNotStopService = false; // we do not stop CloudService if we are logged in
	
	/**
	 * CloudService is communication with LoginActivity through a BroadcastReceiver
	 */
	private final BroadcastReceiver receiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String status = intent.getStringExtra("status");
			if (status != null) {
				if (status.equals("LOGGED_IN")) {
					SharedPreferences state = getSharedPreferences(App.SHARED_PREFERENCES,0);
					String login = intent.getStringExtra("login");
					if (login == null) {
						Log.e(TAG, "LOGGED_IN status received, but without login data.");
					} else {
						SharedPreferences.Editor ed = state.edit();
						ed.putString("logged_in_as", login);
						String prevLogin = state.getString("login", "");
						if (!prevLogin.equals("") && !prevLogin.equals(login)) {
							// we were previously logged in as a different user
							// TODO: we have to clean up all personal data in database
						}
						ed.putString("login", login);
						ed.putString("last_login", login);
						if (mSavePasswordView.isChecked())
							ed.putString("last_password", mPasswordView.toString());
						else
							ed.remove("last_password");
						ed.putBoolean("save_password", mSavePasswordView.isChecked());
						ed.commit();
						finish();
						Intent mainActivityIntent = new Intent(LoginActivity.this, PostActivity.class);
						mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						LoginActivity.this.startActivity(mainActivityIntent);
					}
				} else if (status.equals("INCORRECT_PASSWORD")) {
					if (mLoginStatusView.isShown()) {
						showProgress(false);
						mPasswordView.setError(getString(R.string.error_incorrect_password));
						mPasswordView.requestFocus();
					}
				} else if (status.equals("NO_SUCH_LOGIN_DOMAIN")) {
					if (mLoginStatusView.isShown()) {
						showProgress(false);
						mLoginView.setError(getString(R.string.error_incorrect_login));
						mLoginView.requestFocus();
					}
				} else if (status.equals("TIMEOUT")) {
					if (mLoginStatusView.isShown()) {
						showProgress(false);
						mLoginView.setError(getString(R.string.error_timeout));
						mLoginView.requestFocus();
					}
				} else if (status.equals("SALT_RECEIVED")) {
					// if we are in the process of logging in
					if (mLoginStatusView.isShown()) {
						App app = (App)getApplicationContext();
				    	String login = app.getLogin();
				    	String password = app.getPassword();
				    	// Construct the line to be encoded with MD5
				    	String credentials = login + ":" + password;
				    	// Get the MD5 of login:password
				    	String hashedCredentials = new String (Hex.encodeHex(DigestUtils.md5(credentials)));
				    	// Surround it with salt and MD5 again
				    	String salt = app.getSalt();
				    	if (salt == null) {
				    		// we have no salt, so ask for it
				    		Log.d(TAG, "We did not get salt yet, sending SALT command...");
				    		Bundle data = new Bundle();
				    		data.putString("cmd", "SALT");
				    		SendToServer loginTask = new SendToServer(app);
				    		loginTask.execute(data);
				    	} else {
				    		// everything is fine, encrypt login/password with salt and send to the server
				    		String finalHash = new String (Hex.encodeHex(DigestUtils.md5(salt+"_"+hashedCredentials+"_"+salt)));
				    		// Now send it to the server
				    		Bundle data = new Bundle();
				    		data.putString("cmd", "LOGIN");
				    		data.putString("md5", finalHash);
				    		data.putString("login", login);
				    		SendToServer loginTask = new SendToServer(app);
				    		loginTask.execute(data);
				    		Log.v(TAG, "Sent LOGIN data to the server.");
				    	}
					}
				}
			}
		}
	};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		active = true; // static variable, to indicate that activity is running
		
		this.startService(new Intent(this, CloudService.class)); // connect to server and listen
		
		registerReceiver(receiver, new IntentFilter("LOGIN"));
		
		SharedPreferences state = getSharedPreferences(App.SHARED_PREFERENCES,0);
		String logged_in_as = state.getString("logged_in_as", "");
		if (logged_in_as.equals("")) {
			// we are not logged in
			setContentView(R.layout.activity_login);

			// Set up the login form.
			mLogin = state.getString("last_login", "");
			mLoginView = (EditText) findViewById(R.id.login);
			mLoginView.setText(mLogin);

			mPasswordView = (EditText) findViewById(R.id.password);
			mPasswordView.setText(state.getString("last_password", ""));
			if (mLogin.equals(""))
				mLoginView.requestFocus();
			else
				mPasswordView.requestFocus();
			mPasswordView
					.setOnEditorActionListener(new TextView.OnEditorActionListener() {
						@Override
						public boolean onEditorAction(TextView textView, int id,
								KeyEvent keyEvent) {
							if (id == R.id.login || id == EditorInfo.IME_NULL) {
								attemptLogin();
								return true;
							}
							return false;
						}
					});
							
			mSavePasswordView = (CheckBox)findViewById(R.id.save_password); 
			mSavePasswordView.setChecked(state.getBoolean("save_password", false));

			mLoginFormView = findViewById(R.id.login_form);
			mLoginStatusView = findViewById(R.id.login_status);
			mLoginStatusMessageView = (TextView) findViewById(R.id.login_status_message);

			findViewById(R.id.sign_in_button).setOnClickListener(
					new View.OnClickListener() {
						@Override
						public void onClick(View view) {
							attemptLogin();
						}
					});
	    	
	    	// First of all, send SALT command to the cloud
	    	Bundle data = new Bundle();
	    	data.putString("cmd", "SALT");
	    	Log.i(TAG, "Sending SALT command to the server");
	    	SendToServer saltyTask = new SendToServer(getApplicationContext());
	    	saltyTask.execute(data);
		} else {
			// we are already logged in as logged_in_as
			finish(); // end this activity
			Intent mainActivityIntent = new Intent(LoginActivity.this, PostActivity.class);
        	mainActivityIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        	startActivity(mainActivityIntent);			
		}

	}

	@Override 
	protected void onResume() {
		super.onResume();
		startService(new Intent(this,CloudService.class));
		doNotStopService = false;
	}
	
	@Override
	protected void onPause() {
		// stop CloudService if we are not logged in
		if (!doNotStopService) {
			SharedPreferences state = getSharedPreferences(App.SHARED_PREFERENCES,0);
			String logged_in_as = state.getString("logged_in_as", "");
			if (logged_in_as.equals("")) {
				// we are not logged in - can stop the CloudSocket
				sendBroadcast(new Intent(CloudSocket.BROADCAST_TERMINATE));
			}
		}
		super.onPause();
	}
	
	@Override
	protected void onDestroy() {
		unregisterReceiver(receiver);
		active = false; // activity is not running anymore
		super.onDestroy();
	}

	/**
	 * Attempts to log in or register the account specified by the login form.
	 * If there are form errors (invalid email, missing fields, etc.), the
	 * errors are presented and no actual login attempt is made.
	 */
	public void attemptLogin() {
		// Reset errors.
		mLoginView.setError(null);
		mPasswordView.setError(null);

		// Store values at the time of the login attempt.
		mLogin = mLoginView.getText().toString().trim();
		mPassword = mPasswordView.getText().toString();
		
		// Store login/password in the application class so that they are global
		App app = ((App)getApplicationContext());
		app.setLogin(mLogin);
		app.setPassword(mPassword);

		boolean cancel = false;
		View focusView = null;

		// Check if password is valid - not empty and more than 4 chars
		if (TextUtils.isEmpty(mPassword)) {
			mPasswordView.setError(getString(R.string.error_field_required));
			focusView = mPasswordView;
			cancel = true;
		} else if (mPassword.length() < 4) {
			mPasswordView.setError(getString(R.string.error_short_password));
			focusView = mPasswordView;
			cancel = true;
		}

		// Check if login is valid - not empty
		if (TextUtils.isEmpty(mLogin)) {
			mLoginView.setError(getString(R.string.error_field_required));
			focusView = mLoginView;
			cancel = true;
		}

		if (cancel) {
			// There was an error; don't attempt login 
			// and focus the first form field with an error.
			focusView.requestFocus();
		} else {
			// Show a progress spinner, and kick off a background task to
			// send encrypted user credentials to the server.
			mLoginStatusMessageView.setText(R.string.login_progress_signing_in);
			showProgress(true);
	    	
	    	Log.v(TAG, "Login/Password: " + mLogin + "/" + mPassword);
	    	
	    	// Construct the line to be encoded with MD5
	    	String credentials = mLogin + ":" + mPassword;
	    	Log.v(TAG, "Credentials: " + credentials);

	    	// Get the MD5 of login:password
	    	String hashedCredentials = new String (Hex.encodeHex(DigestUtils.md5(credentials)));
	    	Log.v(TAG, "Hashed credentials: " + hashedCredentials);
	    	// Surround it with salt and MD5 again
	    	String salt = app.getSalt();
	    	if (salt == null) {
	    		Log.d(TAG, "We did not get salt yet, sending SALT command...");
	    		Bundle data = new Bundle();
	    		data.putString("cmd", "SALT");
	    		SendToServer loginTask = new SendToServer(app);
	    		loginTask.execute(data);
	    	} else {
	    		String finalHash = new String (Hex.encodeHex(DigestUtils.md5(salt+"_"+hashedCredentials+"_"+salt)));
	    		Log.v(TAG, "Surrounded by salt: " + finalHash);
	    		// Now send it to the server
	    		Bundle data = new Bundle();
	    		data.putString("cmd", "LOGIN");
	    		data.putString("md5", finalHash);
	    		data.putString("login", mLogin);
	    		SendToServer loginTask = new SendToServer(app);
	    		loginTask.execute(data);
	    		Log.v(TAG, "LOGIN command has been sent to the server.");
	    	}
	    	
	    	// we don't want to make user angry and wait for too long to log in
	    	// so if there is no reply from the server within LOGIN_TIMEOUT seconds - show error
	    	new Handler().postDelayed(new Runnable(){
	    		@Override
	    		public void run() {
	    			Intent loginIntent = new Intent("LOGIN");
	    			loginIntent.putExtra("status", "TIMEOUT");
	    			sendBroadcast(loginIntent);
	    		}
	    	}, LoginActivity.LOGIN_TIMEOUT);
		}
	}

	/**
	 * Shows the progress UI and hides the login form.
	 */
	@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
	private void showProgress(final boolean show) {
		// On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
		// for very easy animations. If available, use these APIs to fade-in
		// the progress spinner.
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
			int shortAnimTime = getResources().getInteger(
					android.R.integer.config_shortAnimTime);

			mLoginStatusView.setVisibility(View.VISIBLE);
			mLoginStatusView.animate().setDuration(shortAnimTime)
					.alpha(show ? 1 : 0)
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							mLoginStatusView.setVisibility(show ? View.VISIBLE
									: View.GONE);
						}
					});

			mLoginFormView.setVisibility(View.VISIBLE);
			mLoginFormView.animate().setDuration(shortAnimTime)
					.alpha(show ? 0 : 1)
					.setListener(new AnimatorListenerAdapter() {
						@Override
						public void onAnimationEnd(Animator animation) {
							mLoginFormView.setVisibility(show ? View.GONE
									: View.VISIBLE);
						}
					});
		} else {
			// The ViewPropertyAnimator APIs are not available, so simply show
			// and hide the relevant UI components.
			mLoginStatusView.setVisibility(show ? View.VISIBLE : View.GONE);
			mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
		}
	}
}
