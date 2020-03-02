package com.hoxfon.react.RNTwilioVoice;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.AssertionException;
import com.facebook.react.bridge.JSApplicationIllegalArgumentException;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReadableType;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.storage.AsyncLocalStorageUtil;
import com.facebook.react.modules.storage.ReactDatabaseSupplier;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.iid.InstanceIdResult;
import com.hoxfon.react.RNTwilioVoice.screens.AutomaticCallScreenActivity;
import com.hoxfon.react.RNTwilioVoice.screens.DirectCallScreenActivity;
import com.hoxfon.react.RNTwilioVoice.screens.UnlockScreenActivity;
import com.twilio.voice.Call;
import com.twilio.voice.CallException;
import com.twilio.voice.CallInvite;
import com.twilio.voice.ConnectOptions;
import com.twilio.voice.LogLevel;
import com.twilio.voice.RegistrationException;
import com.twilio.voice.RegistrationListener;
import com.twilio.voice.UnregistrationListener;
import com.twilio.voice.Voice;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.json.JSONException;

import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_CONNECTION_DID_CONNECT;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_CONNECTION_DID_DISCONNECT;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_DEVICE_DID_RECEIVE_INCOMING;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_DEVICE_NOT_READY;
import static com.hoxfon.react.RNTwilioVoice.EventManager.EVENT_DEVICE_READY;

public class TwilioVoiceModule extends ReactContextBaseJavaModule implements ActivityEventListener, LifecycleEventListener {

    public static String TAG = "RNTwilioVoice";

    private static final int MIC_PERMISSION_REQUEST_CODE = 1;

    public static final String INCOMING_CALL_INVITE          = "INCOMING_CALL_INVITE";
    public static final String INCOMING_CALL_NOTIFICATION_ID = "INCOMING_CALL_NOTIFICATION_ID";
    public static final String NOTIFICATION_TYPE             = "NOTIFICATION_TYPE";
    public static final String CANCELLED_CALL_INVITE         = "CANCELLED_CALL_INVITE";

    public static final String ACTION_INCOMING_CALL = "com.hoxfon.react.TwilioVoice.INCOMING_CALL";
    public static final String ACTION_ACCEPTED_CALL = "com.hoxfon.react.TwilioVoice.ACCEPTED_CALL";
    public static final String ACTION_REJECTED_CALL = "com.hoxfon.react.TwilioVoice.REJECTED_CALL";
    public static final String ACTION_FCM_TOKEN     = "com.hoxfon.react.TwilioVoice.ACTION_FCM_TOKEN";
    public static final String ACTION_MISSED_CALL   = "com.hoxfon.react.TwilioVoice.MISSED_CALL";
    public static final String ACTION_ANSWER_CALL   = "com.hoxfon.react.TwilioVoice.ANSWER_CALL";
    public static final String ACTION_REJECT_CALL   = "com.hoxfon.react.TwilioVoice.REJECT_CALL";
    public static final String ACTION_HANGUP_CALL   = "com.hoxfon.react.TwilioVoice.HANGUP_CALL";
    public static final String ACTION_DISCONNECTED_CALL  = "com.hoxfon.react.TwilioVoice.DISCONNECTED_CALL";
    public static final String ACTION_CLEAR_MISSED_CALLS_COUNT = "com.hoxfon.react.TwilioVoice.CLEAR_MISSED_CALLS_COUNT";
    public static final String ACTION_ALLOW_VISITOR = "com.hoxfon.react.TwilioVoice.ALLOW_VISITOR";
    public static final String ACTION_REJECT_VISITOR = "com.hoxfon.react.TwilioVoice.REJECT_VISITOR";
    public static final String ACTION_REQUEST_CALL = "com.hoxfon.react.TwilioVoice.REQUEST_CALL";
    public static final String ACTION_SPEAKER_ON = "com.hoxfon.react.TwilioVoice.SPEAKER_ON";
    public static final String ACTION_SPEAKER_OFF = "com.hoxfon.react.TwilioVoice.SPEAKER_OFF";
    public static final String ACTION_CANCEL_CALL   = "com.hoxfon.react.TwilioVoice.CANCEL_CALL";

    public static final String CALL_SID_KEY = "CALL_SID";
    public static final String INCOMING_NOTIFICATION_PREFIX = "Incoming_";
    public static final String MISSED_CALLS_GROUP = "MISSED_CALLS";
    public static final int MISSED_CALLS_NOTIFICATION_ID = 1;
    public static final int HANGUP_NOTIFICATION_ID = 11;
    public static final int CLEAR_MISSED_CALLS_NOTIFICATION_ID = 21;

    private AudioManager audioManager;
    private int originalAudioMode = AudioManager.MODE_NORMAL;

    private boolean isReceiverRegistered = false;
    private VoiceBroadcastReceiver voiceBroadcastReceiver;

    private String savedDigit = null;

    public static final String PREFERENCE_KEY = "com.hoxfon.react.TwilioVoice.PREFERENCE_FILE_KEY";

    private NotificationManager notificationManager;
    public static CallNotificationManager callNotificationManager;
    private EventManager eventManager;

    private String accessToken;

    public static String toNumber = "";
    public static String toName = "";

    static Map<String, Integer> callNotificationMap;

    private RegistrationListener registrationListener = registrationListener();
    private UnregistrationListener unregistrationListener = unregistrationListener();
    public Call.Listener callListener = callListener();

    public static CallInvite activeCallInvite;
    public static Call activeCall;

    // this variable determines when to create missed calls notifications
    public static Boolean callAccepted = false;

    private AudioFocusRequest focusRequest;

    private ReactContext reactContext;

    // Empty HashMap, contains parameters for the Outbound call
    private HashMap<String, String> twiMLParams = new HashMap<>();
    private ProximityManager proximityManager;
    
    // this variable determines when to create missed calls notifications
    private HeadsetManager headsetManager;
    private int savedAudioMode = AudioManager.MODE_INVALID;

    public TwilioVoiceModule(ReactApplicationContext reactContext,
            boolean shouldAskForMicPermission, String baseUrl, String s3Url) {

        super(reactContext);
        if (BuildConfig.DEBUG) {
            Voice.setLogLevel(LogLevel.DEBUG);
        } else {
            Voice.setLogLevel(LogLevel.ERROR);
        }

        this.reactContext = reactContext;

        reactContext.addActivityEventListener(this);
        reactContext.addLifecycleEventListener(this);

        eventManager = new EventManager(reactContext);
        callNotificationManager = new CallNotificationManager();
        proximityManager = new ProximityManager(reactContext, eventManager);
        headsetManager = new HeadsetManager(eventManager);

        SharedPreferences sharedPref = getReactApplicationContext().getSharedPreferences("com.hoxfon.react.RNTwilioVoice.config", Context.MODE_PRIVATE);
        SharedPreferences.Editor sharedPrefEditor = sharedPref.edit();
        sharedPrefEditor.putString("BASE_URL", baseUrl);
        sharedPrefEditor.putString("S3_URL", s3Url);
        sharedPrefEditor.commit();

        notificationManager = (android.app.NotificationManager) reactContext.getSystemService(Context.NOTIFICATION_SERVICE);

        /*
         * Setup the broadcast receiver to be notified of GCM Token updates
         * or incoming call messages in this Activity.
         */
        voiceBroadcastReceiver = new VoiceBroadcastReceiver();
        registerReceiver();

        TwilioVoiceModule.callNotificationMap = new HashMap<>();

        /*
         * Needed for setting/abandoning audio focus during a call
         */
        audioManager = (AudioManager) reactContext.getSystemService(Context.AUDIO_SERVICE);

        /*
         * Ensure the microphone permission is enabled
         */
        if (shouldAskForMicPermission && !checkPermissionForMicrophone()) {
            requestPermissionForMicrophone();
        }
    }

    @Override
    public void onHostResume() {
        /*
         * Enable changing the volume using the up/down keys during a conversation
         */
        getCurrentActivity().setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);
        registerReceiver();
    }

    @Override
    public void onHostPause() {
        // the library needs to listen for events even when the app is paused
        //        unregisterReceiver();
    }

    @Override
    public void onHostDestroy() {
        disconnect();
        callNotificationManager.removeHangupNotification(getReactApplicationContext());
        setAudioFocus(false);
    }

    @Override
    public String getName() {
        return TAG;
    }

    public void onNewIntent(Intent intent) {
        // This is called only when the App is in the foreground
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "onNewIntent " + intent.toString());
        }
        handleIncomingCallIntent(intent);
    }

    private RegistrationListener registrationListener() {
        return new RegistrationListener() {
            @Override
            public void onRegistered(String accessToken, String fcmToken) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Successfully registered FCM");
                }
                eventManager.sendEvent(EVENT_DEVICE_READY, null);
            }

            @Override
            public void onError(RegistrationException error, String accessToken, String fcmToken) {
                Log.e(TAG, String.format("Registration Error: %d, %s", error.getErrorCode(), error.getMessage()));
                WritableMap params = Arguments.createMap();
                params.putString("err", error.getMessage());
                eventManager.sendEvent(EVENT_DEVICE_NOT_READY, params);
            }
        };
    }

    private UnregistrationListener unregistrationListener() {
        return new UnregistrationListener() {
            @Override
            public void onUnregistered(String accessToken, String fcmToken) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Successfully unregistered FCM");
                }
            }

            @Override
            public void onError(RegistrationException error, String accessToken, String fcmToken) {
                Log.e(TAG, String.format("Unregistration Error: %d, %s", error.getErrorCode(), error.getMessage()));
                WritableMap params = Arguments.createMap();
                params.putString("err", error.getMessage());
                eventManager.sendEvent(EVENT_DEVICE_NOT_READY, params);
            }
        };
    }


    private Call.Listener callListener() {
        return new Call.Listener() {
            /*
             * This callback is emitted once before the Call.Listener.onConnected() callback when
             * the callee is being alerted of a Call. The behavior of this callback is determined by
             * the answerOnBridge flag provided in the Dial verb of your TwiML application
             * associated with this client. If the answerOnBridge flag is false, which is the
             * default, the Call.Listener.onConnected() callback will be emitted immediately after
             * Call.Listener.onRinging(). If the answerOnBridge flag is true, this will cause the
             * call to emit the onConnected callback only after the call is answered.
             * See answeronbridge for more details on how to use it with the Dial TwiML verb. If the
             * twiML response contains a Say verb, then the call will emit the
             * Call.Listener.onConnected callback immediately after Call.Listener.onRinging() is
             * raised, irrespective of the value of answerOnBridge being set to true or false
             */
            @Override
            public void onRinging(Call call) {
                Log.d(TAG, "Ringing");
            }

            @Override
            public void onRinging(Call call) {
                Log.d(TAG, "Ringing");
            }

            @Override
            public void onConnected(Call call) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "CALL CONNECTED callListener().onConnected call state = " + call.getState());
                }

                setAudioFocus(true);
                
                Log.d(TAG, "CALL STATE " + call.getState());

                proximityManager.startProximitySensor();
                headsetManager.startWiredHeadsetEvent(getReactApplicationContext());

                WritableMap params = Arguments.createMap();
                if (call != null) {
                    params.putString("call_sid", call.getSid());
                    params.putString("call_state", call.getState().name());
                    params.putString("call_from", call.getFrom());
                    params.putString("call_to", call.getTo());
                    String caller = "Show call details in the app";
                    if (!toName.equals("")) {
                        caller = toName;
                    } else if (!toNumber.equals("")) {
                        caller = toNumber;
                    }
                    activeCall = call;
                   callNotificationManager.createHangupLocalNotification(getReactApplicationContext(),
                           call.getSid(), caller);
                }

                if (savedDigit != null) {
                    sendDigits(savedDigit);
                    savedDigit = null;
                }

                eventManager.sendEvent(EVENT_CONNECTION_DID_CONNECT, params);
            }

            @Override
            public void onReconnecting(Call call, CallException callException) {
                Log.d(TAG, "onReconnecting");
            }

            @Override
            public void onReconnected(Call call) {
                Log.d(TAG, "onReconnected");
            }

            @Override
            public void onDisconnected(Call call, CallException error) {
                setAudioFocus(false);
                proximityManager.stopProximitySensor();
                headsetManager.stopWiredHeadsetEvent(getReactApplicationContext());
                callAccepted = false;

                Log.d(TAG, "Call has been disconnected");

                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "call disconnected");
                }

                Intent intent = new Intent(ACTION_DISCONNECTED_CALL);
                LocalBroadcastManager.getInstance(reactContext).sendBroadcast(intent);

                String callSid = "";
                if (call != null) {
                    callSid = call.getSid();
                }
                if (error != null) {
                    Log.e(TAG, String.format("CallListener onDisconnected error: %d, %s",
                        error.getErrorCode(), error.getMessage()));
                }
                if (callSid != null && activeCall != null && activeCall.getSid() != null && activeCall.getSid().equals(callSid)) {
                    activeCall = null;
                }

                eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, params);
                callNotificationManager.removeHangupNotification(getReactApplicationContext());
                toNumber = "";
                toName = "";
            }

            @Override
            public void onConnectFailure(Call call, CallException error) {
                setAudioFocus(false);
                proximityManager.stopProximitySensor();
                callAccepted = false;
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "connect failure");
                }

                Intent intent = new Intent(ACTION_DISCONNECTED_CALL);
                LocalBroadcastManager.getInstance(reactContext).sendBroadcast(intent);

                Log.e(TAG, String.format("CallListener onDisconnected error: %d, %s",
                    error.getErrorCode(), error.getMessage()));

                WritableMap params = Arguments.createMap();
                params.putString("err", error.getMessage());
                String callSid = "";
                if (call != null) {
                    callSid = call.getSid();
                    params.putString("call_sid", callSid);
                    params.putString("call_state", call.getState().name());
                    params.putString("call_from", call.getFrom());
                    params.putString("call_to", call.getTo());
                }
                if (callSid != null && activeCall != null && activeCall.getSid() != null && activeCall.getSid().equals(callSid)) {
                    activeCall = null;
                }
                eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, params);
                callNotificationManager.removeHangupNotification(getReactApplicationContext());
                toNumber = "";
                toName = "";
            }
        };
    }

    /**
     * Register the Voice broadcast receiver
     */
    private void registerReceiver() {
        if (!isReceiverRegistered) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(ACTION_INCOMING_CALL);
            intentFilter.addAction(ACTION_MISSED_CALL);
            intentFilter.addAction(ACTION_ACCEPTED_CALL);
            intentFilter.addAction(ACTION_REJECTED_CALL);
            intentFilter.addAction(ACTION_HANGUP_CALL);
            intentFilter.addAction(ACTION_ALLOW_VISITOR);
            intentFilter.addAction(ACTION_REJECT_VISITOR);
            intentFilter.addAction(ACTION_REQUEST_CALL);
            intentFilter.addAction(ACTION_SPEAKER_ON);
            intentFilter.addAction(ACTION_SPEAKER_OFF);
            LocalBroadcastManager.getInstance(getReactApplicationContext()).registerReceiver(
                voiceBroadcastReceiver, intentFilter);
            isReceiverRegistered = true;
        }
    }

//    private void unregisterReceiver() {
//        if (isReceiverRegistered) {
//            LocalBroadcastManager.getInstance(getReactApplicationContext()).unregisterReceiver(voiceBroadcastReceiver);
//            isReceiverRegistered = false;
//        }
//    }
//TODO Check where this should go.
    private void registerActionReceiver() {

        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(ACTION_ANSWER_CALL);
        intentFilter.addAction(ACTION_REJECT_CALL);
        intentFilter.addAction(ACTION_HANGUP_CALL);
        intentFilter.addAction(ACTION_CLEAR_MISSED_CALLS_COUNT);

        getReactApplicationContext().registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                switch (action) {
                    case ACTION_ANSWER_CALL:
                        accept();
                        break;
                    case ACTION_REJECT_CALL:
                        reject();
                        break;
                    case ACTION_HANGUP_CALL:
                        disconnect();
                        break;
                    case ACTION_CLEAR_MISSED_CALLS_COUNT:
                        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCE_KEY, Context.MODE_PRIVATE);
                        SharedPreferences.Editor sharedPrefEditor = sharedPref.edit();
                        sharedPrefEditor.putInt(MISSED_CALLS_GROUP, 0);
                        sharedPrefEditor.commit();
                }
                // Dismiss the notification when the user tap on the relative notification action
                // eventually the notification will be cleared anyway
                // but in this way there is no UI lag
                notificationManager.cancel(intent.getIntExtra(INCOMING_CALL_NOTIFICATION_ID, 0));
            }
        }, intentFilter);
    }

    // removed @Override temporarily just to get it working on different versions of RN
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        onActivityResult(requestCode, resultCode, data);
    }

    // removed @Override temporarily just to get it working on different versions of RN
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Ignored, required to implement ActivityEventListener for RN 0.33
    }

    private void spawnActivity(Activity parent, Class childActivityClass) {
        Intent intent = new Intent(parent, childActivityClass);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        parent.startActivity(intent);
    }

    private void spawnActivity(Activity parent, Class childActivityClass, Map<String, String> data) {
        Intent intent = new Intent(parent, childActivityClass);
        Log.d(TAG, String.format("SpawnActivity Intent %s", intent));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        for (Map.Entry<String, String> entry : data.entrySet()) {
            intent.putExtra(entry.getKey(), entry.getValue());
        }
        Log.d(TAG, "SpawnActivity Extra Data Added");
        parent.startActivity(intent);
    }

    private void showUnlockScreen() {
        final Activity activity = getCurrentActivity();

        if (activeCallInvite == null) {
            return;
        } else if (activity == null) {
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    Activity activity = getCurrentActivity();
                    if (activity != null) {
                        spawnActivity(activity, UnlockScreenActivity.class);
                    } else {
                        Log.e(TAG, "Activity STILL loading");
                    }
                }
            }, 5000);
        } else {
            spawnActivity(activity, UnlockScreenActivity.class);
        }
    }

    private void handleIncomingCallIntent(Intent intent) {

        if (intent == null || intent.getAction() == null) {
            Log.e(TAG, "handleIncomingCallIntent intent is null");
            return;
        }
    
        if (!checkPermissionForMicrophone()) {
            Log.e(TAG, "Permissions for microphone have been disabled");
            return;
        }
    
        if (intent.getAction().equals(ACTION_INCOMING_CALL)) {
            activeCallInvite = intent.getParcelableExtra(INCOMING_CALL_INVITE);
    
            if (activeCallInvite != null) {
                callAccepted = false;
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "handleIncomingCallIntent state = PENDING");
                }
                SoundPoolManager.getInstance(getReactApplicationContext()).playRinging();
    
                if (getReactApplicationContext().getCurrentActivity() != null) {
                    Window window = getReactApplicationContext().getCurrentActivity().getWindow();
                    window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    );
                }
    
                showUnlockScreen();
    
                // send a JS event ONLY if the app's importance is FOREGROUND or SERVICE
                // at startup the app would try to fetch the activeIncoming calls
                int appImportance = callNotificationManager.getApplicationImportance(getReactApplicationContext());
                if (appImportance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                        appImportance == RunningAppProcessInfo.IMPORTANCE_SERVICE) {
                    WritableMap params = Arguments.createMap();
                    params.putString("call_sid", activeCallInvite.getCallSid());
                    params.putString("call_from", activeCallInvite.getFrom());
                    params.putString("call_to", activeCallInvite.getTo());
                    params.putString("call_state", "PENDING");
                    eventManager.sendEvent(EVENT_DEVICE_DID_RECEIVE_INCOMING, params);
                }
    
            } else {
               Log.d(TAG, "Should this be called at all?");
            }
        }  else if (intent.getAction().equals(ACTION_CANCEL_CALL)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "====> BEGIN handleIncomingCallIntent when activeCallInvite != PENDING");
            }
            // this block is executed when the callInvite is cancelled and:
            //   - the call is answered (activeCall != null)
            //   - the call is rejected
    
            SoundPoolManager.getInstance(getReactApplicationContext()).stopRinging();
    
            if (BuildConfig.DEBUG) {
                // Log.d(TAG, "activeCallInvite state = " + activeCallInvite.getState());
            }
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "activeCallInvite was cancelled by " + activeCallInvite.getFrom());
            }
            if (!callAccepted) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "creating a missed call");
                }
                callNotificationManager.createMissedCallNotification(getReactApplicationContext(), activeCallInvite);
                int appImportance = callNotificationManager.getApplicationImportance(getReactApplicationContext());
                if (appImportance != RunningAppProcessInfo.IMPORTANCE_BACKGROUND) {
                    WritableMap params = Arguments.createMap();
                    params.putString("call_sid", activeCallInvite.getCallSid());
                    params.putString("call_from", activeCallInvite.getFrom());
                    params.putString("call_to", activeCallInvite.getTo());
                    params.putString("call_state", "DISCONNECTED");
                    eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, params);
                }
            }
    
            clearIncomingNotification(activeCallInvite);
        } else if (intent.getAction().equals(ACTION_FCM_TOKEN)) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "handleIncomingCallIntent ACTION_FCM_TOKEN");
            }
            registerForCallInvites();
        }
    }

    public class VoiceBroadcastReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "ACTION RECEIVED " + action);
            if (action.equals(ACTION_ACCEPTED_CALL)) {
                accept();
            } else if (action.equals(ACTION_REJECTED_CALL)) {
                reject();
            } else if (action.equals(ACTION_INCOMING_CALL)) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "creating a missed call");
                }
                handleIncomingCallIntent(intent);
            } else if (action.equals(ACTION_MISSED_CALL)) {
                SharedPreferences sharedPref = getReactApplicationContext().getSharedPreferences(PREFERENCE_KEY, Context.MODE_PRIVATE);
                SharedPreferences.Editor sharedPrefEditor = sharedPref.edit();
                sharedPrefEditor.remove(MISSED_CALLS_GROUP);
                sharedPrefEditor.commit();
            } else if (action.equals(ACTION_HANGUP_CALL)) {
                disconnect();
            } else if (action.equals(ACTION_ALLOW_VISITOR)) {
                sendDigits("1");
            } else if (action.equals(ACTION_REQUEST_CALL)) {
                sendDigits("2");
            } else if (action.equals(ACTION_REJECT_VISITOR)) {
                sendDigits("3");
            } else if (action.equals(ACTION_SPEAKER_ON)) {
                setSpeakerPhone(true);
            } else if (action.equals(ACTION_SPEAKER_OFF)) {
                setSpeakerPhone(false);
            } else {
                Log.e(TAG, "received broadcast unhandled action " + action);
            }
            registerForCallInvites();
        }
    }

    @ReactMethod
    public void initWithAccessToken(final String accessToken, Promise promise) {
        Log.d(TAG, "INIT ACCESS WITH TOKEN");

        if (accessToken == null || accessToken.equals("")) {
            Log.e(TAG, "Invalid access token");
            promise.reject(new JSApplicationIllegalArgumentException("Invalid access token"));
            return;
        }

        if(!checkPermissionForMicrophone()) {
            Log.e(TAG, "Can't init without microphone permission");
            promise.reject(new AssertionException("Can't init without microphone permission"));
            return;
        }

        TwilioVoiceModule.this.accessToken = accessToken;
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "initWithAccessToken");
        }
        registerForCallInvites();
        WritableMap params = Arguments.createMap();
        params.putBoolean("initialized", true);
        promise.resolve(params);
    }

    @ReactMethod
    public void unregisterWithAccessToken(final String accessToken, Promise promise) {
        Log.d(TAG, "UNREGISTER WITH TOKEN");

        if (accessToken == null || accessToken.equals("")) {
            Log.e(TAG, "Invalid access token");
            promise.reject(new JSApplicationIllegalArgumentException("Invalid access token"));
            return;
        }
        
        final String fcmToken = FirebaseInstanceId.getInstance().getToken();
        Voice.unregister(accessToken, Voice.RegistrationChannel.FCM, fcmToken, unregistrationListener);

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "unregisterWithAccessToken ACTION_FCM_TOKEN");
        }

        WritableMap params = Arguments.createMap();
        params.putBoolean("unregistered", true);
        promise.resolve(params);
    }

    private void clearIncomingNotification(CallInvite callInvite) {

        if (callInvite != null && callInvite.getCallSid() != null) {
            // remove incoming call notification
            String notificationKey = INCOMING_NOTIFICATION_PREFIX + callInvite.getCallSid();
            int notificationId = 0;
            if (TwilioVoiceModule.callNotificationMap.containsKey(notificationKey)) {
                notificationId = TwilioVoiceModule.callNotificationMap.get(notificationKey);
            }
            callNotificationManager.removeIncomingCallNotification(getReactApplicationContext(), null, notificationId);
            TwilioVoiceModule.callNotificationMap.remove(notificationKey);
        }
//        activeCallInvite = null;
    }

    /*
     * Register your FCM token with Twilio to receive incoming call invites
     *
     * If a valid google-services.json has not been provided or the FirebaseInstanceId has not been
     * initialized the fcmToken will be null.
     *
     */
    private void registerForCallInvites() {
        FirebaseInstanceId.getInstance().getInstanceId()
                .addOnCompleteListener(new OnCompleteListener<InstanceIdResult>() {
                    @Override
                    public void onComplete(@NonNull Task<InstanceIdResult> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "getInstanceId failed", task.getException());
                            return;
                        }

                        // Get new Instance ID token
                        String fcmToken = task.getResult().getToken();
                        if (fcmToken != null) {
                            if (BuildConfig.DEBUG) {
                                Log.d(TAG, "Registering with FCM");
                            }
                            Voice.register(accessToken, Voice.RegistrationChannel.FCM, fcmToken, registrationListener);
                        }
                    }
                });
    }

    public void accept() {
        callAccepted = true;
        SoundPoolManager.getInstance(getReactApplicationContext()).stopRinging();
        if (activeCallInvite != null){
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "accept() activeCallInvite.getState() PENDING");
            }
            activeCallInvite.accept(getReactApplicationContext(), callListener);

            String from = activeCallInvite.getFrom();
            if (from != null && from.toLowerCase().contains("client:")) {
                spawnActivity(getCurrentActivity(), DirectCallScreenActivity.class);
            } else if (from != null) {
                Log.d(TAG, "accept() Automatic Call");
                setSpeakerPhone(false);

                SQLiteDatabase readableDatabase = ReactDatabaseSupplier.getInstance(getReactApplicationContext()).getReadableDatabase();
                String keenvilData = AsyncLocalStorageUtil.getItemImpl(readableDatabase, "persist:Keenvil");
                HashMap<String, String> data = new HashMap<String, String>();

                try {
                    JSONObject keenvilDataObject = new JSONObject(keenvilData);
                    String sessionData = keenvilDataObject.getString("session");
                    Log.d(TAG, "Session data " + sessionData);
                    JSONObject sessionObject = new JSONObject(sessionData);
                    data.put("CALL_SID", activeCallInvite.getCallSid());
                    Log.d(TAG, String.format("Call Sid: [%s]", activeCallInvite.getCallSid()));
                    data.put("SESSION_TOKEN", sessionObject.getString("token"));
                    Log.d(TAG, String.format("Auth token: [%s]", sessionObject.getString("token")));
                    Log.d(TAG, "accept() Extra Info Added");

                } catch (JSONException ex) {
                    //Do nothing and spawn the activity with no data.
                }
                spawnActivity(getCurrentActivity(), AutomaticCallScreenActivity.class, data);
            }
            activeCallInvite.accept(getReactApplicationContext(), callListener);
            clearIncomingNotification(activeCallInvite);
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "There is no active call");
            }
            eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, null);
        }
    }

    public void reject() {
        callAccepted = false;
        SoundPoolManager.getInstance(getReactApplicationContext()).stopRinging();
        WritableMap params = Arguments.createMap();
        if (activeCallInvite != null) {
            params.putString("call_sid", activeCallInvite.getCallSid());
            params.putString("call_from", activeCallInvite.getFrom());
            params.putString("call_to", activeCallInvite.getTo());
            params.putString("call_state", "REJECTED");
            activeCallInvite.reject(getReactApplicationContext());
            clearIncomingNotification(activeCallInvite);
        }
        eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, params);
    }

    @ReactMethod
    public void ignore() {
        callAccepted = false;
        SoundPoolManager.getInstance(getReactApplicationContext()).stopRinging();
        WritableMap params = Arguments.createMap();
        if (activeCallInvite != null) {
            params.putString("call_sid", activeCallInvite.getCallSid());
            params.putString("call_from", activeCallInvite.getFrom());
            params.putString("call_to", activeCallInvite.getTo());
            params.putString("call_state", "BUSY");
            clearIncomingNotification(activeCallInvite);
        }
        eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, params);
    }

    @ReactMethod
    public void connect(ReadableMap params) {

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "connect params: " + params);
        }
        WritableMap errParams = Arguments.createMap();
        if (accessToken == null) {
            errParams.putString("err", "Invalid access token");
            eventManager.sendEvent(EVENT_DEVICE_NOT_READY, errParams);
            return;
        }
        if (params == null) {
            errParams.putString("err", "Invalid parameters");
            eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, errParams);
            return;
        } else if (!params.hasKey("To")) {
            errParams.putString("err", "Invalid To parameter");
            eventManager.sendEvent(EVENT_CONNECTION_DID_DISCONNECT, errParams);
            return;
        }
        toNumber = params.getString("To");
        if (params.hasKey("ToName")) {
            toName = params.getString("ToName");
        }


        twiMLParams.clear();

        ReadableMapKeySetIterator iterator = params.keySetIterator();
        while (iterator.hasNextKey()) {
            String key = iterator.nextKey();
            ReadableType readableType = params.getType(key);
            switch (readableType) {
                case Null:
                    twiMLParams.put(key, "");
                    break;
                case Boolean:
                    twiMLParams.put(key, String.valueOf(params.getBoolean(key)));
                    break;
                case Number:
                    // Can be int or double.
                    twiMLParams.put(key, String.valueOf(params.getDouble(key)));
                    break;
                case String:
                    twiMLParams.put(key, params.getString(key));
                    break;
                default:
                    Log.d(TAG, "Could not convert with key: " + key + ".");
                    break;
            }
        }

        ConnectOptions connectOptions = new ConnectOptions.Builder(accessToken)
                .params(twiMLParams)
                .build();

        activeCall = Voice.connect(getReactApplicationContext(), connectOptions, callListener);
    }

    @ReactMethod
    public void disconnect() {
        if (activeCall != null) {
            activeCall.disconnect();
            activeCall = null;
        }
    }

    public void setMuted(Boolean muteValue) {
        if (activeCall != null) {
            activeCall.mute(muteValue);
        }
    }

    public void sendDigits(String digits) {
        Log.d(TAG, "Digits to send " + digits);
        if (activeCall != null) {
            Log.d(TAG, "Send digits" + digits);
            activeCall.sendDigits(digits);
        } else if (activeCallInvite != null) {
            Log.d(TAG, "Save digits " + digits);
            savedDigit = digits;
        }
    }

    @ReactMethod
    public void getActiveCall(Promise promise) {
        if (activeCall != null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Active call found state = " + activeCall.getState());
            }
            WritableMap params = Arguments.createMap();
            params.putString("call_sid", activeCall.getSid());
            params.putString("call_from", activeCall.getFrom());
            params.putString("call_to", activeCall.getTo());
            params.putString("call_state", activeCall.getState().name());
            promise.resolve(params);
            return;
        }
        if (activeCallInvite != null) {
            if (BuildConfig.DEBUG) {
                // Log.d(TAG, "Active call invite found state = "+activeCallInvite.getState());
            }
            WritableMap params = Arguments.createMap();
            params.putString("call_sid", activeCallInvite.getCallSid());
            params.putString("call_from", activeCallInvite.getFrom());
            params.putString("call_to", activeCallInvite.getTo());
            if (activeCall != null)
                params.putString("call_state", activeCall.getState().name());
            promise.resolve(params);
            return;
        }
        promise.resolve(null);
    }

    @ReactMethod
    public void setSpeakerPhone(Boolean value) {
        // TODO check whether it is necessary to call setAudioFocus again
//        setAudioFocus();
        audioManager.setSpeakerphoneOn(value);
    }

    private void setAudioFocus(boolean setFocus) {
        if (audioManager != null) {
            if (setFocus) {
                savedAudioMode = audioManager.getMode();
                // Request audio focus before making any device switch.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
                    AudioFocusRequest focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                            .setAudioAttributes(playbackAttributes)
                            .setAcceptsDelayedFocusGain(true)
                            .setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() {
                                @Override
                                public void onAudioFocusChange(int i) {
                                }
                            })
                            .build();
                    audioManager.requestAudioFocus(focusRequest);
                } else {
                    int focusRequestResult = audioManager.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {

                                                                                @Override
                                                                                public void onAudioFocusChange(int focusChange) {
                                                                                }
                                                                            }, AudioManager.STREAM_VOICE_CALL,
                            AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
                }
                /*
                 * Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
                 * required to be in this mode when playout and/or recording starts for
                 * best possible VoIP performance. Some devices have difficulties with speaker mode
                 * if this is not set.
                 */
                audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            } else {
                audioManager.setMode(savedAudioMode);
                audioManager.abandonAudioFocus(null);
            }
        }
    }

    private boolean checkPermissionForMicrophone() {
        int resultMic = ContextCompat.checkSelfPermission(getReactApplicationContext(), Manifest.permission.RECORD_AUDIO);
        return resultMic == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissionForMicrophone() {
        if (getCurrentActivity() == null) {
            return;
        }
        if (ActivityCompat.shouldShowRequestPermissionRationale(getCurrentActivity(), Manifest.permission.RECORD_AUDIO)) {
//            Snackbar.make(coordinatorLayout,
//                    "Microphone permissions needed. Please allow in your application settings.",
//                    SNACKBAR_DURATION).show();
        } else {
            audioManager.abandonAudioFocus(null);
        }
    }

}
