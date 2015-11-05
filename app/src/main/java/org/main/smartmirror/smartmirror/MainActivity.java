package org.main.smartmirror.smartmirror;

import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener,
        WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener {

    private final String NEWS = "News";
    private final String CALENDAR = "Calendar";
    private final String WEATHER = "Weather";
    private final String SPORTS = "Sports";
    private final String LIGHT = "Light";
    private final String SETTINGS = "Settings";
    private TTSHelper mTTSHelper;
    private static Context mContext; // Hold the app context
    private Preferences mPreferences;
    private int RESULT_SPEECH = 1;

    // WiFiP2p
    private WifiP2pManager mManager;
    private WifiP2pManager.Channel mChannel;
    private WifiP2pDeviceList mDeviceList;
    private WifiP2pInfo mInfo;
    private BroadcastReceiver mWifiReceiver;
    private IntentFilter mIntentFilter;
    public final static int PORT = 8888;
    public final static int SOCKET_TIMEOUT = 500;
    private String mOwnerIP;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mContext = getApplicationContext();
        // Load any application preferences. If prefs do not exist, set them to defaults
        mPreferences = Preferences.getInstance();

        // check for permission to write system settings on API 23 and greater.
        // Get authorization on >= 23
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(!Settings.System.canWrite( getApplicationContext() )) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                startActivityForResult(intent, 1);
            }
        }

        // initialize TTS
        mTTSHelper = new TTSHelper(this);

        // Initialize WiFiP2P services
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);

        discoverPeers();

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.setDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // Hide UI and actionbar
        View decorView = getWindow().getDecorView();
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
                //| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION    // commented out to keep nav buttons for testing
                //| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY // req API 19
                //| View.SYSTEM_UI_FLAG_IMMERSIVE;      // req API 19
        decorView.setSystemUiVisibility(uiOptions);

        try {
            getSupportActionBar().hide();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        // start with weather displayed
        displayView(WEATHER);
    }

    @Override
    public void onResume(){
        super.onResume();
        mPreferences.setAppBrightness(this);
        mWifiReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        registerReceiver(mWifiReceiver, mIntentFilter);
    }

    @Override
    public void onPause(){
        super.onPause();
        unregisterReceiver(mWifiReceiver);

    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            displayView(item.toString());
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        Log.i("item selected", item.toString());
        displayView(item.toString());
        return true;
    }

    public void displayView(String viewName){
        Fragment fragment = null;
        String title = getString(R.string.app_name);
        if (mTTSHelper != null) mTTSHelper.stop();      // shut down any pending TTS
        switch (viewName) {
            case NEWS:
                fragment = new NewsFragment();
                title = NEWS;
                break;
            case CALENDAR:
                fragment = new CalendarFragment();
                title = CALENDAR;
                break;
            case LIGHT:
                fragment = new LightFragment();
                title = LIGHT;
                break;
            case WEATHER:
                fragment = new WeatherFragment();
                title = WEATHER;
                break;
            case SPORTS:
                fragment = new SportsFragment();
                title = SPORTS;
                break;
            case SETTINGS:
                fragment = new SettingsFragment();
                title= SETTINGS;
                break;
        }
        if(fragment != null){
            Log.i("Fragments", "Displaying " + viewName);
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.content_frame, fragment);
            if (!isFinishing() ) {
                ft.commit();
            }
        }

        if(getSupportActionBar() != null){
            getSupportActionBar().setTitle(title);
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data){
        super.onActivityResult(requestCode, resultCode, data);
        String voiceInput = null;
        if (requestCode == RESULT_SPEECH && resultCode == RESULT_OK){
            ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            voiceInput = matches.get(0);
        }
        if(voiceInput != null) {
            if (voiceInput.contains("show")) {
                if (voiceInput.contains(NEWS.toLowerCase())) {
                    startVoice(NEWS);
                    displayView(NEWS);
                } else if (voiceInput.contains(CALENDAR.toLowerCase())) {
                    startVoice(CALENDAR);
                    displayView(CALENDAR);
                } else if (voiceInput.contains(WEATHER.toLowerCase())) {
                    startVoice(WEATHER);
                    displayView(WEATHER);
                } else if (voiceInput.contains(SPORTS.toLowerCase())) {
                    startVoice(SPORTS);
                    displayView(SPORTS);
                } else if (voiceInput.contains(LIGHT.toLowerCase())) {
                    startVoice(LIGHT);
                    displayView(LIGHT);
                } else if (voiceInput.contains(SETTINGS.toLowerCase())) {
                    startVoice(SETTINGS);
                    displayView(SETTINGS);
                }                    
            }
        }
    }

    public void StartVoiceRecognitionActivity(View v) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, "en-US");
        try {
            startActivityForResult(intent, RESULT_SPEECH);
        } catch (ActivityNotFoundException a) {
            Toast tstNoSupport = Toast.makeText(getApplicationContext(), "Your device doesn't support Speech to Text", Toast.LENGTH_SHORT);
            tstNoSupport.show();
        }
    }

    public void startVoice(final String phrase){
        Thread mSpeechThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    mTTSHelper.speakText(phrase);
                    //Thread.sleep(2000);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        mSpeechThread.start();
    }

    /**
     * Start the speech recognizer
     */
    public void startSpeechRecognition(){
        // do stuff
    }

    /**
     * Stops the current speech recognition object
     */
    public void stopSpeechRecognition(){
        //mSpeechRecognizer.stopListening();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mTTSHelper != null) {
            mTTSHelper.destroy();
        }
        mPreferences.destroy();
    }

    public static Context getContextForApplication() {
        return mContext;
    }


    // calls the P2pManager to refresh peer list
    public void discoverPeers() {
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {  Log.i("Wifi", "Peer discovery successful"); }

            @Override
            public void onFailure(int reasonCode) {
                Log.i("Wifi", "discoverPeers failed: " + reasonCode);
            }
        });
    }

    // Interface passes back a device list when the peer list changes, or discovery is successful
    @Override
    public void onPeersAvailable(WifiP2pDeviceList peers) {
        mDeviceList = peers;
    }


    /** called when a connection is made to this device
     *
     * @param info
     */
    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        // make this the group owner and start the server to listen
        Toast.makeText(this, "Remote Connected", Toast.LENGTH_SHORT).show();
        Log.i("Wifi", "Connection info: " + info.toString());
        mInfo = info;
        WifiP2pConfig config = new WifiP2pConfig();
        config.groupOwnerIntent = 15;
        if (info.groupFormed && info.isGroupOwner) {
            Log.i("Wifi", "onConnectionInfo is starting server...");
            new RemoteServerAsyncTask(this).execute();
        } else if (info.groupFormed){
        }
    }
}
