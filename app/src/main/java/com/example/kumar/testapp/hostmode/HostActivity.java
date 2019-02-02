package com.example.kumar.testapp.hostmode;

import android.annotation.TargetApi;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pInfo;
import android.os.Build;
import android.os.CountDownTimer;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import com.example.kumar.testapp.MyTimer;
import com.example.kumar.testapp.PlaylistControl;
import com.example.kumar.testapp.Profile;
import com.example.kumar.testapp.ProfileActivity;
import com.example.kumar.testapp.ProfileControl;
import com.example.kumar.testapp.hostmode.HostDevListFragment.DeviceActionListener;

import com.example.kumar.testapp.R;

import java.io.File;
import java.util.ArrayList;

public class HostActivity extends AppCompatActivity implements WifiP2pManager.ChannelListener,
        DeviceActionListener
{

    public static final String TAG = "HostActivity";
    public final static int Host_MODE = 15;
    private boolean isHost = false;
    private boolean isWifiP2pEnabled = false;
    private boolean channelRetried = false;
    private WifiP2pManager manager;
    private WifiP2pDevice mydevice;
    private Channel channel;
    private final IntentFilter intentFilter = new IntentFilter();
    private BroadcastReceiver receiver = null;
    ProgressDialog progressDialog = null;
    private MyTimer timer;
    private CountDownTimer keepAliveTimer;
    // Keep the Wifi Alive every 5 seconds
    private static final int KEEPALIVE_INTERVAL = 5000;
    private static final int BUFFER_SIZE = 256;
    //Profile specific data
    private String profileName;
    private String profileAge;
    private String profileGender;
    //public shared profile list
    public static ArrayList<Profile> profileList = new ArrayList<>();


    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_host);

        //Get intent data from Profile activity
        profileName = getIntent().getStringExtra("NAME");
        profileAge = getIntent().getStringExtra("AGE");
        profileGender = getIntent().getStringExtra("GENDER");

        Log.i(TAG,profileName +" "+ profileAge +" "+ profileGender);
        Profile profile =new Profile();
        profile.setName(profileName);
        profile.setAge(profileAge);
        profile.setGender(profileGender);
        //Add to distributed profile list
        profileList.add(profile);


        // add necessary intent values to be matched.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), null);

        // start a timer with 25 ms precision
        this.timer = new MyTimer(MyTimer.DEFAULT_TIMER_PRECISION);
        // asynchronous call to start a timer
        this.timer.startTimer();

        keepAliveTimer = new CountDownTimer(KEEPALIVE_INTERVAL,
                KEEPALIVE_INTERVAL) {

            @Override
            public void onTick(long millisUntilFinished) {
            }

            @Override
            public void onFinish() {
                powerOnWifi();
                keepAliveTimer.start();
            }
        };


    }

    /**
     * register the BroadcastReceiver with the intent values to be matched
     */
    @Override
    public void onResume() {
        super.onResume();
        receiver = new WiFiDirectBroadcastReceiver(manager, channel, this);
        registerReceiver(receiver, intentFilter);

        // ***Start discovering right away!
        deviceDiscover();
        keepAliveTimer.start();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterReceiver(receiver);

        keepAliveTimer.cancel();
    }

    @Override
    public void onDestroy()
    {
        disconnect();

        super.onDestroy();
    }

    public void powerOnWifi() {
        WifiManager wifiManager = (WifiManager) this
                .getSystemService(this.WIFI_SERVICE);

        wifiManager.setWifiEnabled(true);
    }

    public void deviceDiscover() {
        //Need a better non-blocking UI to notify users we are discovering
        // onInitiateDiscovery();

        // first power on the wifi p2p
        powerOnWifi();

        channelRetried = false;

        manager.discoverPeers(channel, new ActionListener() {
            @Override
            public void onSuccess() {
                // Toast.makeText(HostActivity.this,
                // "Discovery Initiated",
                // Toast.LENGTH_SHORT).show();

                Log.d(TAG, "Discovery Initiated.");
            }

            // if we failed, then stop the discovery and start again
            @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
            @Override
            public void onFailure(int reasonCode) {
                Log.e(TAG, "Discovery Failed. Error Code is: " + reasonCode);
                manager.stopPeerDiscovery(channel,
                        new ActionListener() {
                            @Override
                            public void onFailure(int reason) {
                                // Toast.makeText(HostActivity.this,
                                // "Stopping Discovery Failed : " + reason,
                                // Toast.LENGTH_SHORT).show();
                                Log.e(TAG,
                                        "Stopping Discovery Failed. Error Code is: "
                                                + reason);
                            }

                            @Override
                            public void onSuccess() {
                                manager.discoverPeers(channel,
                                        new ActionListener() {
                                            @Override
                                            public void onSuccess() {
                                                // Toast.makeText(HostActivity.this,
                                                // "Discovery Initiated",
                                                // Toast.LENGTH_SHORT)
                                                // .show();

                                                Log.d(TAG,
                                                        "Discovery Initiated.");
                                            }

                                            @Override
                                            public void onFailure(int reasonCode) {
                                                Log.e(TAG,
                                                        "Discovery Failed. Error Code is: "
                                                                + reasonCode);
                                            }
                                        });
                            }
                        });
            }
        });
    }


    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    public void resetData() {

        HostDevListFragment fragmentList = (HostDevListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_dev_list);
        if (fragmentList != null) {
            fragmentList.clearPeers();
        }
    }

    @Override
    public void onChannelDisconnected()
    {
        // we will try once more
        if (manager != null && !channelRetried)
        {
            Toast.makeText(this, "Wi-fi Direct Channel lost. Trying again...",
                    Toast.LENGTH_LONG).show();
            resetData();

            channelRetried = true;
            manager.initialize(this, getMainLooper(), this);
        }
        else
        {
            Toast.makeText(
                    this,
                    "Wi-fi Direct Channel is still lost. Try disabling / re-enabling Wi-fi Direct in the P2P Settings.",
                    Toast.LENGTH_LONG).show();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.host_action_items, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {


            case R.id.atn_direct_discover:
                deviceDiscover();
                return true;

            case R.id.atn_wifi_enable:

                if (manager != null && channel != null)
                {
                    // Since this is the system wireless settings activity, it's
                    // not going to send us a result. We will be notified by
                    // WiFiDeviceBroadcastReceiver instead.
                    Intent intent = new Intent();
                    // jump to wi-fi Direct settings
                    intent.setClassName("com.android.settings",
                            "com.android.settings.Settings$WifiP2pSettingsActivity");

                    startActivity(intent);
                }
                else
                {
                    Log.e(TAG, "channel or manager is null");
                }
                return true;

            case R.id.atn_profile:
                if(profileList.size() == 0){
                    Toast.makeText(HostActivity.this, "Guest list not yet ready!!!",
                            Toast.LENGTH_SHORT).show();
                }
                else {
                    viewProfile();
                }

                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void showDetails(WifiP2pDevice device) {

        HostMusicFragment fragMusic = (HostMusicFragment) getFragmentManager()
                .findFragmentById(R.id.frag_host_music);

    }

    @Override
    public void showInfo(WifiP2pInfo info)
    {
        HostMusicFragment fragMusic = (HostMusicFragment) getFragmentManager()
                .findFragmentById(R.id.frag_host_music);

        if (info.isGroupOwner)
        {
            // fragMusic.setDebugText("I am the group owner.");
        }
        else
        {
            // fragMusic.setDebugText("I am not the group owner.");
        }
    }


    @Override
    public void connect(WifiP2pConfig config) {

        if (manager == null)
        {
            return;
        }
        // in Host mode, we want to become the group owner
        WifiP2pConfig hostConfig = config;
        hostConfig.groupOwnerIntent = Host_MODE;
        manager.connect(channel, hostConfig, new ActionListener() {

            @Override
            public void onSuccess() {
                // WiFiDirectBroadcastReceiver will notify us. Ignore for now.
                if (progressDialog != null && progressDialog.isShowing()) {
                    progressDialog.dismiss();
                }
            }

            @Override
            public void onFailure(int reason) {
                Toast.makeText(HostActivity.this, "Connect failed. Retry.",
                        Toast.LENGTH_SHORT).show();
                Log.e(TAG,
                        "Wi-fi Direct connection failed. The error code is: "
                                + reason);
            }
        });
    }

    @Override
    public void disconnect() {

        if (manager == null)
        {
            return;
        }

        manager.removeGroup(channel, new ActionListener() {

            @Override
            public void onFailure(int reasonCode) {
                Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);

            }

            @Override
            public void onSuccess() {

                Toast.makeText(HostActivity.this, "Disconnected a device.",
                        Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Disconnected from a device.");
            }

        });
    }

    public void cancelDisconnect() {

        /*
         * A cancel abort request by user. Disconnect i.e. removeGroup if
         * already connected. Else, request WifiP2pManager to abort the ongoing
         * request
         */
        if (manager != null) {
            final HostDevListFragment fragment = (HostDevListFragment) getFragmentManager()
                    .findFragmentById(R.id.frag_dev_list);
            if (fragment.getDevice() == null
                    || fragment.getDevice().status == WifiP2pDevice.CONNECTED) {
                disconnect();
            } else if (fragment.getDevice().status == WifiP2pDevice.AVAILABLE
                    || fragment.getDevice().status == WifiP2pDevice.INVITED) {

                manager.cancelConnect(channel, new ActionListener() {

                    @Override
                    public void onSuccess() {
                        Toast.makeText(HostActivity.this, "Aborting connection",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onFailure(int reasonCode) {
                        Toast.makeText(HostActivity.this,
                                "Connect abort request failed. Reason Code: " + reasonCode,
                                Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }

    }

    public void playRemoteMusic(String musicFilePath, long startTime,
                                int startPos)
    {
        HostDevListFragment fragmentList = (HostDevListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_dev_list);

        File audioFile = new File(musicFilePath);

        fragmentList.playMusicOnClients(audioFile, startTime, startPos);
    }

    public void stopRemoteMusic()
    {
        HostDevListFragment fragmentList = (HostDevListFragment) getFragmentManager()
                .findFragmentById(R.id.frag_dev_list);
        fragmentList.stopMusicOnClients();
    }

    public MyTimer getTimer()
    {
        return timer;
    }

    public void viewProfile(){

        System.out.println("You clicked viewProfile");
        Intent intent = new Intent(this, ProfileControl.class);
        intent.putExtra("MODE", "host");
        startActivity(intent);

    }

}
