package com.example.kumar.testapp.clientmode;

import android.app.Activity;
import android.app.ListFragment;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
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
import com.example.kumar.testapp.MyTimer;
import com.example.kumar.testapp.R;
import com.example.kumar.testapp.hostmode.HostSocketHandler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import NanoHTTPD.NanoHTTPD;
import NanoHTTPD.SimpleWebServer;

/**
 * Created by Kumar on 11/25/2016.
 */

public class ClientDevListFragment extends ListFragment implements
        WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener, Handler.Callback
{

    private List<WifiP2pDevice> peers = new ArrayList<WifiP2pDevice>();
    ProgressDialog progressDialog = null;
    View mContentView = null;
    private WifiP2pDevice device;
    public static final String TAG = "Client device list";

    private ClientSocketHandler clientThread;
   // private Activity mActivity = null;

    private Handler handler = new Handler(this);


    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        this.setListAdapter(new WiFiPeerListAdapter(getActivity(), R.layout.row_devices, peers));
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mContentView = inflater.inflate(R.layout.device_list, null);
        return mContentView;
    }

    @Override
    public void onDestroyView()
    {
        stopClient();
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
                ((ClientFragmentListener) getActivity()).connect(config);
                break;

            case WifiP2pDevice.INVITED:
                if (progressDialog != null && progressDialog.isShowing())
                {
                    progressDialog.dismiss();
                }

                progressDialog = ProgressDialog.show(getActivity(),
                        "Press back to abort", "Revoking invitation to: "
                                + device.deviceName, true, true);

                ((ClientFragmentListener) getActivity()).cancelDisconnect();
                // start another discovery
                ((ClientFragmentListener) getActivity()).deviceDiscover();
                break;

            case WifiP2pDevice.CONNECTED:
                if (progressDialog != null && progressDialog.isShowing())
                {
                    progressDialog.dismiss();
                }

                progressDialog = ProgressDialog.show(getActivity(),
                        "Press back to abort", "Disconnecting: "
                                + device.deviceName, true, true);

                ((ClientFragmentListener) getActivity()).disconnect();
                // start another discovery
                ((ClientFragmentListener) getActivity()).deviceDiscover();
                break;

            // refresh the list of devices
            case WifiP2pDevice.FAILED:
            case WifiP2pDevice.UNAVAILABLE:
            default:
                ((ClientFragmentListener) getActivity()).deviceDiscover();
                break;
        }

        ((ClientFragmentListener) getActivity()).showDetails(device);
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
            case ClientSocketHandler.EVENT_RECEIVE_MSG:
                byte[] readBuf = (byte[]) msg.obj;
                // construct a string from the valid bytes in the buffer
                String readMessage = new String(readBuf);

                // interpret the command
                String[] cmdString = readMessage
                        .split(HostSocketHandler.CMD_DELIMITER);

                if (cmdString[0].equals(HostSocketHandler.PLAY_CMD)
                        && cmdString.length > 3)
                {
                    try
                    {
                        ((ClientFragmentListener) getActivity()).playMusic(
                                cmdString[1], Long.parseLong(cmdString[2]),
                                Integer.parseInt(cmdString[3]));
                    }
                    catch (NumberFormatException e)
                    {
                        Log.e(TAG,
                                "Could not convert to a proper time for these two strings: "
                                        + cmdString[2] + " and " + cmdString[3],
                                e);
                    }
                }
                else if (cmdString[0].equals(HostSocketHandler.STOP_CMD)
                        && cmdString.length > 0)
                {
                    ((ClientFragmentListener) getActivity()).stopMusic();
                }

                Log.d(TAG, readMessage);

                break;

            case ClientSocketHandler.CLIENT_CALLBACK:
                clientThread = (ClientSocketHandler) msg.obj;
                Log.d(TAG, "Retrieved client thread.");
                break;

            default:
                Log.d(TAG, "I thought we heard something? Message type: "
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
        ((ClientFragmentListener) getActivity()).showInfo(info);

        // The group owner IP is now known.
        if (info.groupFormed && info.isGroupOwner)
        {
            // In Client mode, we must not be the group owner, or else we have
            // a problem
            Log.d(TAG, "Client Mode became the group owner! >:(.");

            Toast.makeText(mContentView.getContext(),
                    "Client Mode became the group owner! >:(.",
                    Toast.LENGTH_SHORT).show();
        }
        else if (info.groupFormed)
        {
            if (this.clientThread == null)
            {
                    Thread client = new ClientSocketHandler(this.handler,
                            info.groupOwnerAddress,
                            ((ClientFragmentListener) getActivity())
                                    .getTimer());
                    client.start();

            }

            Toast.makeText(mContentView.getContext(),
                    "Client started.", Toast.LENGTH_SHORT).show();
        }

    }

    public void stopClient()
    {
        if (clientThread != null)
        {
            clientThread.disconnect();
            clientThread = null;
        }
    }

    /**
     * An interface-callback for the activity to listen to fragment interaction events.
     */
    public interface ClientFragmentListener {

        void showDetails(WifiP2pDevice device);

        void cancelDisconnect();

        void connect(WifiP2pConfig config);

        void disconnect();

        void deviceDiscover();

        void showInfo(WifiP2pInfo info);

        void playMusic(String url, long startTime, int startPos);

        void stopMusic();

        MyTimer getTimer();
    }

}
