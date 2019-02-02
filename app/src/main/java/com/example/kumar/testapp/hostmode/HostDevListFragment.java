package com.example.kumar.testapp.hostmode;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.DialogFragment;
import android.app.FragmentManager;
import android.app.ListFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.kumar.testapp.AssistMe;
import com.example.kumar.testapp.MainActivity;
import com.example.kumar.testapp.R;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import NanoHTTPD.NanoHTTPD;
import NanoHTTPD.SimpleWebServer;

/**
 * Created by Kumar on 11/25/2016.
 */

public class HostDevListFragment extends ListFragment implements
        WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener, Handler.Callback
{

    private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    ProgressDialog progressDialog = null;
    View mContentView = null;
    private WifiP2pDevice device;
    public static final String TAG = "Host device list";

    private HostSocketHandler serverThread;
    private String httpHostIP = null;
    private Activity mActivity = null;

    private final Handler handler = new Handler(this);
    private File wwwroot = null;
    private NanoHTTPD httpServer = null;
    public static final int HTTP_PORT = 9002;


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.setListAdapter(new WiFiPeerListAdapter(getActivity(), R.layout.row_devices, peers));
        // get the application directory
        mActivity = this.getActivity();
        wwwroot = mActivity.getApplicationContext().getFilesDir();
        System.out.println("wwwroot: " + wwwroot.toString());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContentView = inflater.inflate(R.layout.device_list, null);
        return mContentView;
    }

    @Override
    public void onDestroyView()
    {
        stopServer();
        super.onDestroyView();
    }


    /**
     * @return this device
     */
    public WifiP2pDevice getDevice() {
        return device;
    }

    private static String getDeviceStatus(int deviceStatus) {

        switch (deviceStatus) {
            case WifiP2pDevice.AVAILABLE:
                return "Available";
            case WifiP2pDevice.INVITED:
                return "Invited";
            case WifiP2pDevice.CONNECTED:
                return "Connected";
            case WifiP2pDevice.FAILED:
                return "Failed";
            case WifiP2pDevice.UNAVAILABLE:
                return "Unavailable";
            default:
                return "Unknown";

        }
    }
    /**
     * Interact with peers depending on their states
     */
    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        WifiP2pDevice device = (WifiP2pDevice) getListAdapter().getItem(position);

        switch (device.status)
        {
            case WifiP2pDevice.AVAILABLE:
                // add a progress bar

                if (progressDialog != null && progressDialog.isShowing())
                {
                    progressDialog.dismiss();
                }

                progressDialog = ProgressDialog.show(getActivity(),
                        "Press back to cancel", "Connecting to: "
                                + device.deviceName, true, true);

                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = device.deviceAddress;
                config.wps.setup = WpsInfo.PBC;
                ((DeviceActionListener) getActivity()).connect(config);
                break;

            case WifiP2pDevice.INVITED:
                if (progressDialog != null && progressDialog.isShowing())
                {
                    progressDialog.dismiss();
                }

                progressDialog = ProgressDialog.show(getActivity(),
                        "Press back to abort", "Revoking invitation to: "
                                + device.deviceName, true, true);

                ((DeviceActionListener) getActivity()).cancelDisconnect();
                // start another discovery
                ((DeviceActionListener) getActivity()).deviceDiscover();
                break;

            case WifiP2pDevice.CONNECTED:
                if (progressDialog != null && progressDialog.isShowing())
                {
                    progressDialog.dismiss();
                }

                progressDialog = ProgressDialog.show(getActivity(),
                        "Press back to abort", "Disconnecting: "
                                + device.deviceName, true, true);

                ((DeviceActionListener) getActivity()).disconnect();
                // start another discovery
                ((DeviceActionListener) getActivity()).deviceDiscover();
                break;

            // refresh the list of devices
            case WifiP2pDevice.FAILED:
            case WifiP2pDevice.UNAVAILABLE:
            default:
                ((DeviceActionListener) getActivity()).deviceDiscover();
                break;
        }

        ((DeviceActionListener) getActivity()).showDetails(device);
    }



    /**
     * Array adapter for ListFragment that maintains WifiP2pDevice list.
     */
    private class WiFiPeerListAdapter extends ArrayAdapter<WifiP2pDevice> {

        private List<WifiP2pDevice> items;

        /**
         * @param context
         * @param textViewResourceId
         * @param objects
         */
        public WiFiPeerListAdapter(Context context, int textViewResourceId,
                                   List<WifiP2pDevice> objects) {
            super(context, textViewResourceId, objects);
            items = objects;

        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                LayoutInflater vi = (LayoutInflater) getActivity().getSystemService(
                        Context.LAYOUT_INFLATER_SERVICE);
                v = vi.inflate(R.layout.row_devices, null);
            }
            WifiP2pDevice device = items.get(position);
            if (device != null) {
                TextView top = (TextView) v.findViewById(R.id.device_name);
                TextView bottom = (TextView) v.findViewById(R.id.device_details);
                if (top != null) {
                    top.setText(device.deviceName);
                }
                if (bottom != null) {
                    bottom.setText(getDeviceStatus(device.status));
                }
            }

            return v;

        }
    }

    /**
     * Update UI for this device.
     *
     * @param device WifiP2pDevice object
     */
    public void updateThisDevice(WifiP2pDevice device) {
        this.device = device;
        TextView view = (TextView) mContentView.findViewById(R.id.my_name);
        view.setTextColor(Color.WHITE);
        view.setText(device.deviceName);
        view = (TextView) mContentView.findViewById(R.id.my_status);
        view.setText(getDeviceStatus(device.status));
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
        peers.clear();
        peers.addAll(peerList.getDeviceList());
        ((WiFiPeerListAdapter) getListAdapter()).notifyDataSetChanged();
        if (peers.size() == 0)
        {
            Log.d(TAG, "No devices found");
            return;
        }

    }

    public void clearPeers() {
        peers.clear();
        ((WiFiPeerListAdapter) getListAdapter()).notifyDataSetChanged();
    }

    @Override
    public boolean handleMessage(Message msg)
    {
        switch (msg.what)
        {
            case HostSocketHandler.SERVER_CALLBACK:
                serverThread = (HostSocketHandler) msg.obj;
                Log.d(TAG, "Retrieved server thread.");
                break;

            default:
                Log.d(TAG, "Did we hear something? Message type: "
                        + msg.what);
                break;
        }
        return true;
    }


    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info)
    {
        if (progressDialog != null && progressDialog.isShowing())
        {
            progressDialog.dismiss();
        }

        // display info if necessary
        ((DeviceActionListener) getActivity()).showInfo(info);

        // The group owner IP is now known.
        if (info.groupFormed && info.isGroupOwner)
        {
            try
            {
                // WARNING:
                // depends on the timing, if we don't get a server back in time,
                // we may end up running multiple threads of the server
                // instance!
                if (this.serverThread == null)
                {
                    Thread server = new HostSocketHandler(this.handler);
                    server.start();

                    if (wwwroot != null)
                    {
                        if (httpServer == null)
                        {
                            httpHostIP = info.groupOwnerAddress
                                    .getHostAddress();

                            boolean quiet = false;

                            httpServer = new SimpleWebServer(httpHostIP,
                                    HTTP_PORT, wwwroot, quiet);
                            try
                            {
                                httpServer.start();
                                Log.d("HTTP Server",
                                        "Started web server with IP address: "
                                                + httpHostIP);
                                Toast.makeText(mContentView.getContext(),
                                        "Party host Server started.",
                                        Toast.LENGTH_SHORT).show();
                            }
                            catch (IOException ioe)
                            {
                                Log.e("HTTP Server", "Couldn't start server:\n");
                            }
                        }
                    }
                    else
                    {
                        Log.e("HTTP Server",
                                "Could not retrieve a directory for the HTTP server.");
                    }
                }
            }
            catch (IOException e)
            {
                Log.e(TAG, "Cannot start server.", e);
            }
        }
        else if (info.groupFormed)
        {
            // In Host mode, we must be the group owner, or else we have a problem
            Log.d(TAG, "Host Mode did not become the group owner :(.");

            Toast.makeText(mContentView.getContext(),
                    "Host Mode did not become the group owner :(.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    public void stopServer()
    {
        if (serverThread != null)
        {
            serverThread.disconnectServer();
            serverThread = null;
        }
        if (httpServer != null)
        {
            httpServer.stop();
            httpServer = null;
        }
    }


    public void playMusicOnClients(File musicFile, long startTime, int startPos)
    {
        if (serverThread == null)
        {
            Log.d(TAG,
                    "Server has not started. No music will be played remotely.");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Toast.makeText(this.getContext(),
                        "Server has not started. No music will be played remotely..",
                        Toast.LENGTH_SHORT).show();
            }
            return;
        }

        try
        {
            // copy the actual file to the web server directory, then pass the
            // URL to the client
            File webFile = new File(wwwroot, musicFile.getName());

            AssistMe.copyFile(musicFile, webFile);

            Uri webMusicURI = Uri.parse("http://" + httpHostIP + ":"
                    + String.valueOf(HTTP_PORT) + "/" + webFile.getName());

            serverThread.sendPlay(webMusicURI.toString(), startTime, startPos);
        }
        catch (IOException e1)
        {
            Log.i(TAG, "Cant copy file to HTTP server.", e1);
        }
    }

    public void stopMusicOnClients()
    {
        if (serverThread != null)
        {
            serverThread.sendStop();
        }
    }

    /**
     * An interface-callback for the activity to listen to fragment interaction events.
     */
    public interface DeviceActionListener {

        void showDetails(WifiP2pDevice device);

        void cancelDisconnect();

        void connect(WifiP2pConfig config);

        void disconnect();

        void deviceDiscover();

        void showInfo(WifiP2pInfo info);
    }

}
