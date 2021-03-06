package nl.simbits.rambler;

import org.json.JSONException;
import org.json.JSONObject;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.facebook.android.BaseRequestListener;
import com.facebook.android.Facebook;
import com.facebook.android.LoginButton;
import com.facebook.android.SessionEvents;
import com.facebook.android.SessionEvents.AuthListener;
import com.facebook.android.SessionEvents.LogoutListener;

import nl.simbits.rambler.RamblerApplication;

public class MainTabActivity extends Activity
{
    public static final String TAG = "MainTabActivity";
    public static final int BLUETOOTH_REQUEST_ENABLE = 1;
    
    private ProgressDialog mProgressDialog;
    
    private RamblerApplication mRambler;
    private Handler mHandler;
    private SharedPreferences mPrefs;
    private BroadcastReceiver mBroadcastReceiver;
    
    private FacebookUtilities mFacebook;
    private Facebook mFbSession;
    private TextView mFbMessages;
    private LoginButton mFbLoginButton;
    private FacebookSessionListener mFbSessionListener = new FacebookSessionListener();

    private TwitterSessionListener mTwSessionListener = new TwitterSessionListener();
    private TextView mTwMessages;
    private TwitterLoginButton mTwLoginButton;

    private BluetoothAdapter mBtAdapter;
    private TextView mBtMessages;
    private ImageButton mBtConnectButton;
    private boolean mShoeConnected;

    private boolean mServiceBound;
    private RamblerService mService;
    private ServiceConnection mServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            mService = ((RamblerService.ServiceBinder)service).getService();
            mServiceBound = true;
            mBtConnectButton.setEnabled(true);
        }

        public void onServiceDisconnected(ComponentName className) {
        	mService = null;
        	mServiceBound = false;
            mBtConnectButton.setEnabled(false);
        }
    };
   
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_tab_layout);

        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setIndeterminate(true);
        
        mRambler = (RamblerApplication) getApplication();
        mHandler = new Handler();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        mBroadcastReceiver = new BluetoothConnectionReceiver();
        
        IntentFilter filter = new IntentFilter(BluetoothSPPConnector.BROADCAST_INTENT_BLUETOOTH);
        registerReceiver(mBroadcastReceiver, filter);
        
        /**
         * Facebook session
         */
        mFacebook = FacebookUtilities.getInstance();
        mFacebook.restoreSession(mRambler);
        mFbSession = mFacebook.getSession();

        SessionEvents.addAuthListener(mFbSessionListener);
        SessionEvents.addLogoutListener(mFbSessionListener);        

        mFbMessages = (TextView)findViewById(R.id.facebookMessages);
        mFbLoginButton = (LoginButton)findViewById(R.id.facebookLoginButton);
        mFbLoginButton.init(this, 
                            FacebookUtilities.AUTHORIZE_ACTIVITY_RESULT_CODE, 
                            mFbSession, 
                            FacebookUtilities.permissions);
        
        if (mFbSession.isSessionValid()) {
            requestFacebookUserData();
        }
        
        
        /**
         * Twitter session
         */
        TwitterSessionEvents.addAuthListener(mTwSessionListener);
        TwitterSessionEvents.addLogoutListener(mTwSessionListener);
        mTwMessages = (TextView)findViewById(R.id.twitterMessages);
        mTwLoginButton = (TwitterLoginButton)findViewById(R.id.twitterLoginButton);
        mTwLoginButton.init(this, TwitterUtilities.AUTHORIZE_ACTIVITY_RESULT_CODE);
        
        requestTwitterScreenName();

        
        /**
         * Bluetooth
         */
        mBtMessages = (TextView)findViewById(R.id.bluetoothMessages);
        mBtConnectButton = (ImageButton)findViewById(R.id.bluetoothConnectButton);
        mBtAdapter = BluetoothAdapter.getDefaultAdapter();
        
        mBtConnectButton.setBackgroundColor(Color.TRANSPARENT);
        mBtConnectButton.setOnClickListener(new ButtonOnClickListener());
        mBtConnectButton.setEnabled(false);

        if (mBtAdapter == null) {
        	mBtMessages.setText("Bluetooth not available");
            Toast.makeText(this, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            mBtConnectButton.setEnabled(false);
        }
    }

    @Override
    public void onStart() {
    	super.onStart();
    	
    	Log.d(TAG, "onStart()");
    	
    	Intent intent = new Intent(this, RamblerService.class);
    	
        if (ServiceTools.isServiceRunning(this) == false){
            Log.d(TAG,"-->service will be started.");
            startService(intent);
        } else {
            Log.d(TAG,"-->service is running.");
        }
    	getApplicationContext().bindService(intent, mServiceConnection, 0);
    }
    
    public void connectToShoe() {
		mBtMessages.setText("Trying to connect");
		mProgressDialog.setTitle("Connecting");
		mProgressDialog.setMessage("Trying to connect to rambler shoe");
		mProgressDialog.show();
		mService.startBluetoothConnection();	
    }
    
    public void disconnectShoe() {
		mService.stopBluetoothConnection();
		mBtConnectButton.setImageResource(R.drawable.bt_connect_button);
		mShoeConnected = false;
		mBtMessages.setText("Disconnected");
    }
    
    private final class ButtonOnClickListener implements OnClickListener {
    	/*
		 * Source Tag: login_tag
		 */
        public void onClick(View arg0) {
        	//ImageButton b = (ImageButton)arg0;
    		if (mServiceBound && mService != null) {
    			if (!mShoeConnected) {
            		Log.d(TAG, "onClick: start bluetooth connection");
            		connectToShoe();
        		} else {
        			Log.d(TAG, "onClick: stopping bluetooth connection");
        			disconnectShoe();
        		}
        	}
        }
    }
    
    public class UserRequestListener extends BaseRequestListener {
        public void onComplete(final String response, final Object state) {
			try {
				final String name = new JSONObject(response).getString("name");
	        	mHandler.post(new Runnable() {
	                public void run() {
	                	mFbMessages.setText("Logged in as " + name);
                        Toast.makeText(getApplicationContext(), "Facebook logged in as " + name, Toast.LENGTH_SHORT).show();
	                }
	            });
	        	
			} catch (JSONException e) {
				e.printStackTrace();
			}
        }
    }
   
    public void requestFacebookUserData() {
    	Bundle params = new Bundle();
   		params.putString("fields", "name");
		mFacebook.request("me", params, new UserRequestListener());
    }

    public class FacebookSessionListener implements AuthListener, LogoutListener {
        public void onAuthSucceed() {
            mFacebook.saveSession(mRambler);
        	requestFacebookUserData();
        }

        public void onAuthFail(String error) {
            mFbMessages.setText("Login Failed: " + error);
            Toast.makeText(getApplicationContext(), "Facebook Login failed: " + error, Toast.LENGTH_SHORT).show();
        }
        
        public void onLogoutBegin() {
			Toast.makeText(getApplicationContext(), "Facebook logging out", Toast.LENGTH_SHORT).show();
        }

        public void onLogoutFinish() {
            mFbMessages.setText("Logged out");
            Toast.makeText(getApplicationContext(), "Facebook Logged out", Toast.LENGTH_SHORT).show();
        }
    }

    public void requestTwitterScreenName() {
        mHandler.post(new Runnable() {
            public void run() {
                if (TwitterUtilities.isAuthenticated(mPrefs)) {
                    try {
                        String name = TwitterUtilities.getScreenName(mPrefs);
                        mTwMessages.setText("Logged in as " + name);
                        Toast.makeText(getApplicationContext(), 
                                       "Twitter Logged in as " + name, Toast.LENGTH_SHORT).show();
                    } catch (Exception e) {
                        mTwMessages.setText("Failed to get screen name: " + e.toString());
                        Log.d(TAG, "Failed to get screen name: " + e.toString());
                    }
                }
            }
        });
    }
    
    public class TwitterSessionListener implements TwitterSessionEvents.TwitterAuthListener, 
	    											   TwitterSessionEvents.TwitterLogoutListener {
		@Override
		public void onAuthSucceed() {
			requestTwitterScreenName();
		}
	
		@Override
		public void onAuthFail(String error) { 
            mTwMessages.setText("Login Failed: " + error);
			Toast.makeText(getApplicationContext(), "Twitter login failed: " + error, Toast.LENGTH_SHORT).show();
		}
		
		@Override
		public void onLogoutBegin() {
			Toast.makeText(getApplicationContext(), "Twitter logging out", Toast.LENGTH_SHORT).show();
		}
		@Override
		public void onLogoutFinish() {
            mTwMessages.setText("Logged out");
			Toast.makeText(getApplicationContext(), "Twitter logged out", Toast.LENGTH_SHORT).show();
		}
	}
    
    public class BluetoothConnectionReceiver extends BroadcastReceiver {
    	@Override
    	public void onReceive(Context context, Intent intent) {
    		if (intent.getAction().equals(BluetoothSPPConnector.BROADCAST_INTENT_BLUETOOTH)) {
    			Bundle extras = intent.getExtras();
    			
    			int state = extras.getInt("state");
    			String stateText = extras.getString("state_text");
    			String name = extras.getString("name");
    			String address = extras.getString("address");
    			
    			Log.i(TAG, "Received broadcast: " + intent.toString());
    			Log.i(TAG, "extras: state: " + state + " (" + BluetoothSPPConnector.stateToString(state) + "), address: " + address);
    			
    			switch (state) {
    				case BluetoothSPPConnector.BT_CONNECTED: {
    					mBtMessages.setText("Connected to " + name + " (" + address + ")");
    					mBtConnectButton.setImageResource(R.drawable.bt_disconnect_button);
    					mShoeConnected = true;
    					break;
    				}
    				case BluetoothSPPConnector.BT_NOT_ENABLED: {
    					mBtMessages.setText("Bluetooth not enabled");
    					Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
    			        startActivityForResult(enableIntent, BLUETOOTH_REQUEST_ENABLE);
    					break;
    				}
    				default: {
    					mBtMessages.setText("Unable to connect with " + name + " (" + address + "): " + 
    										((stateText.equals("")) ? "" : ": " + stateText));
    					mBtConnectButton.setImageResource(R.drawable.bt_connect_button);
    					mShoeConnected = false;
    				}
    			}
    			
    			mProgressDialog.dismiss(); 
    		}
    	}
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
            case FacebookUtilities.AUTHORIZE_ACTIVITY_RESULT_CODE: {
                mFbSession.authorizeCallback(requestCode, resultCode, data);
                break;
            }
            case TwitterUtilities.AUTHORIZE_ACTIVITY_RESULT_CODE: {
                TwitterUtilities.authorizeCallback(requestCode, resultCode, data, mPrefs);
                break;
            }
            
            case BLUETOOTH_REQUEST_ENABLE: {
            	if (resultCode == RESULT_OK) {
            		connectToShoe();
            	}
            	break;
            }
        }
    }
     
    @Override
    public void onPause() {
    	super.onPause();
    	Log.d(TAG, "onPause");
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	
        Log.d(TAG, "onResume");
    	
    	if(mFbSession != null && !mFbSession.isSessionValid()) {
	    	mFbMessages.setText("You are logged out");
    	}
    	if (!TwitterUtilities.isAuthenticated(mPrefs)) {
	    	mTwMessages.setText("You are logged out");
    	}
    	
    	if (mServiceBound) {
    		mService.broadcastBluetoothState();
    	}
    }   

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        
    	unregisterReceiver(mBroadcastReceiver);
        if (mServiceBound) {
            getApplicationContext().unbindService(mServiceConnection);
            mServiceBound = false;
        }
    }
}
