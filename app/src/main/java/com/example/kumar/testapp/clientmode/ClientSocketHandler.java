package com.example.kumar.testapp.clientmode;

import android.os.Handler;
import android.util.Base64;
import android.util.Log;

import com.example.kumar.testapp.Profile;
import com.example.kumar.testapp.MyTimer;
import com.example.kumar.testapp.Playlist;
import com.example.kumar.testapp.hostmode.HostSocketHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;

/**
 * Created by Kumar on 1/26/2017.
 */

public class ClientSocketHandler extends Thread {

    private static final String TAG = "ClientSocketHandler";
    private Handler handler;
    private InetAddress mAddress;
    private Socket socket;
    public static Socket socket1;

    // for syncing time with the server
    private MyTimer timer = null;

    // a time out for connecting to a server, unit is in milliseconds, 0 for
    // never timing out
    private static final int CONN_TIMEOUT = 0;

    public static final int BUFFER_SIZE = 1024 * 1024;

    public static final int EVENT_RECEIVE_MSG = 100;
    public static final int CLIENT_CALLBACK = 101;

    private static String profileDataString;
    //playList with meta data constructed from received data
    public static ArrayList<Playlist> clientPlayList = new ArrayList<Playlist>();
    //Data structure to store fragmented playlist data
    private ArrayList<byte[]> playListSocketData = new ArrayList<byte[]>();
    //Data structure to store fragmented playlist data
    private ArrayList<byte[]> profileListSocketData = new ArrayList<byte[]>();
    //playList with meta data constructed from received data
    public static ArrayList<Profile> clientProfileList = new ArrayList<Profile>();

    private Boolean isPlaylistData = false;
    private Boolean isProfileListData = false;


    public ClientSocketHandler(Handler handler, InetAddress hostAddress, MyTimer timer)
    {
        this.handler = handler;
        this.mAddress = hostAddress;
        this.timer = timer;
    }

    @Override
    public void run()
    {
        // let the UI thread control the server
        handler.obtainMessage(CLIENT_CALLBACK, this).sendToTarget();

        // connect the socket first
        connect();

        // thread will stop when disconnect is called, at that point the socket
        // should be closed and nullified
        while (socket != null)
        {
            try
            {
                InputStream iStream = socket.getInputStream();
                OutputStream oStream = socket.getOutputStream();

                // clear the buffer before reading
                byte[] byteArray = new byte[BUFFER_SIZE];
                int bytenumber;

                // Read from the InputStream
                bytenumber = iStream.read(byteArray);
                if (bytenumber == -1)
                {
                    continue;
                }

                byte[] startArray = Arrays.copyOfRange(byteArray, 0, 4);
                String recMsg1 = new String(startArray, "UTF-8");

                byte[] endArray = Arrays.copyOfRange(byteArray, bytenumber - 8, bytenumber);
                String recMsg2 = new String(endArray, "UTF-8");

                // need to handle sync messages
                // *** Time Sync here ***
                // receiving messages should be as fast as possible to ensure
                // the successful time synchronization
                if (recMsg1.equals(HostSocketHandler.SYNC_CMD))
                {
                    String recMsg = new String(byteArray);
                    Log.d(TAG, "Command received: " + recMsg);

                    String[] cmdString = recMsg
                            .split(HostSocketHandler.CMD_DELIMITER);
                    // check if we have received a timer parameter, if
                    // so, set the time, then send back an
                    // Acknowledgment
                    if (cmdString.length > 1){
                        timer.setCurrTime(Long.parseLong(cmdString[1]));

                        // just send the same message back to the server
                        oStream.write(recMsg.getBytes());
                        // Send the obtained bytes to the UI Activity
                        Log.d(TAG, "Command sent: " + recMsg);
                    }

                }
                else if (recMsg1.equals(HostSocketHandler.PLAY_CMD)){
                    handler.obtainMessage(EVENT_RECEIVE_MSG, byteArray).sendToTarget();
                }

                else if (recMsg1.equals(HostSocketHandler.STOP_CMD)){
                    handler.obtainMessage(EVENT_RECEIVE_MSG, byteArray).sendToTarget();
                }

                else if (recMsg1.equals(HostSocketHandler.PRFL))
                {
                    isProfileListData = true;
                    profileListSocketData = new ArrayList<byte[]>();
                    byte[] dataArray = Arrays.copyOfRange(byteArray, 5, byteArray.length);
                    profileListSocketData.add(dataArray);
                    Log.i(TAG, "Received part 1 of playlist data");

                    if (recMsg2.equals("FILE_END")){
                        //Leaving out last LST_DEMILITER  + end delimiter
                        byte[] endDataArray = Arrays.copyOfRange(byteArray, 5, bytenumber - 10);
                        profileListSocketData = new ArrayList<byte[]>();
                        profileListSocketData.add(endDataArray);

                        Log.i(TAG, "Received last part of profilelist data at once");

                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                        for (int i = 0; i < profileListSocketData.size(); i++) {
                            outputStream.write(profileListSocketData.get(i));
                        }
                        // this buffer gets the combined data
                        byte[] combined = outputStream.toByteArray();
                        String profileList = new String(combined);
                        //call prepare for profile List
                        prepareProfileList(profileList);
                        isProfileListData = false;

                        Log.i(TAG, "Combined Profile list data");
                    }
                }

                else if (recMsg1.equals(HostSocketHandler.PLST_CMD))
                {
                    isPlaylistData = true;
                    playListSocketData = new ArrayList<byte[]>();
                    byte[] dataArray = Arrays.copyOfRange(byteArray, 5, byteArray.length);
                    playListSocketData.add(dataArray);
                    Log.i(TAG, "Received part 1 of playlist data");

                    if (recMsg2.equals("FILE_END")){
                        //Leaving out last LST_DEMILITER  + end delimiter
                        byte[] endDataArray = Arrays.copyOfRange(byteArray, 5, bytenumber - 10);
                        playListSocketData = new ArrayList<byte[]>();
                        playListSocketData.add(endDataArray);

                        Log.i(TAG, "Received last part of playList data at once");

                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                        for (int i = 0; i < playListSocketData.size(); i++) {
                            outputStream.write(playListSocketData.get(i));
                        }
                        // this buffer gets the combined data
                        byte[] combined = outputStream.toByteArray();
                        String playListData = new String(combined);
                        //call prepare for playlist
                        preparePlayList(playListData);
                        isPlaylistData = false;

                        Log.i(TAG, "Combined Playlist data");
                    }
                }

                else if (recMsg2.equals("FILE_END"))
                {
                    if(isPlaylistData.equals(true)){
                        //Leaving out last LST_DEMILITER  + end delimiter
                        byte[] endDataArray = Arrays.copyOfRange(byteArray, 0, bytenumber - 10);
                        playListSocketData.add(endDataArray);

                        Log.i(TAG, "Received last part of playList data");

                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                        for (int i = 0; i < playListSocketData.size(); i++) {
                            outputStream.write(playListSocketData.get(i));
                        }
                        // this buffer gets the combined data
                        byte[] combined = outputStream.toByteArray();
                        String playListData = new String(combined);
                        //call prepare for playlist
                        preparePlayList(playListData);

                        isPlaylistData = false;
                        Log.i(TAG, "Combined Playlist data");
                    }
                    else  if(isProfileListData.equals(true)){
                        //Leaving out last LST_DEMILITER  + end delimiter
                        byte[] endDataArray = Arrays.copyOfRange(byteArray, 0, bytenumber - 10);
                        profileListSocketData.add(endDataArray);

                        Log.i(TAG, "Received last part of profilelist data at once");

                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                        for (int i = 0; i < profileListSocketData.size(); i++) {
                            outputStream.write(profileListSocketData.get(i));
                        }
                        // this buffer gets the combined data
                        byte[] combined = outputStream.toByteArray();
                        String profileList = new String(combined);
                        //call prepare for profile List
                        prepareProfileList(profileList);
                        isProfileListData = false;

                        Log.i(TAG, "Combined Profile list data");
                    }

                }

                else {
                    if(isPlaylistData.equals(true)){
                        //It is part of playlist data
                        playListSocketData.add(byteArray);
                        Log.i(TAG, "Received subsequent part of playlist data");
                    }
                    else if (isProfileListData.equals(true)){
                        //It is part of profileList data
                        profileListSocketData.add(byteArray);
                        Log.i(TAG, "Received subsequent part of profileList data");
                    }

                }

            }
            // this is an ok exception, because someone could have wanted this
            // connection to be closed in the middle of socket read
            catch (SocketException e)
            {
                Log.d(TAG, "Socket connection has ended.", e);
                disconnect();
            }
            catch (IOException e)
            {
                Log.e(TAG, "Unexpectedly disconnected during socket read.", e);
                disconnect();
            }
            catch (NumberFormatException e)
            {
                Log.e(TAG, "Cannot parse time received from server", e);
                disconnect();
            }
        }
        System.out.println("I am outside while loop");
    }

    public void connect()
    {
        if (socket == null || socket.isClosed())
        {
            socket = new Socket();
        }

        try
        {
            socket.bind(null);

            socket.connect(new InetSocketAddress(mAddress.getHostAddress(),
                    HostSocketHandler.SERVER_PORT), CONN_TIMEOUT);

            Log.d(TAG, "Connected to server");

            socket.setSoTimeout(CONN_TIMEOUT);

            socket1 = socket;
        }
        catch (IOException e)
        {
            Log.e(TAG, "Cannot connect to server.", e);
            disconnect();
        }
    }

    public void disconnect()
    {
        if (socket == null)
        {
            return;
        }
        try
        {
            socket.close();
            socket = null;
        }
        catch (IOException e)
        {
            Log.e(TAG, "Could not close socket upon disconnect.", e);
            socket = null;
        }
    }

//Prepare playlist from received data from host
    private void preparePlayList(String cPlayList){
        String[] listData = cPlayList
                .split("\\*");
        ArrayList<Playlist> mplayList = new ArrayList<Playlist>();
        for (String listItem:listData) {
            String[] objectData =listItem
                    .split(HostSocketHandler.CMD_DELIMITER);
            Playlist playListItem = new Playlist();
            //get the byte array for song art image
            byte[] byteArray = objectData[3].getBytes();
            byte[] decoded = Base64.decode(byteArray, 0);
            //set data as sent from host

            playListItem.setTrackName(objectData[0]);
            playListItem.setArtistName(objectData[1]);
            playListItem.setAlbum(objectData[2]);
            playListItem.setArt(decoded);
            //Add data to local playlist
            mplayList.add(playListItem);
        }
        //copy data to static global playlist to be used by other activities
        clientPlayList = mplayList;
    }

    //
    private void prepareProfileList(String prflList){
        String[] listData = prflList
                .split("\\*");
        ArrayList<Profile> mprofileList = new ArrayList<Profile>();
        for (String listItem:listData) {
            String[] objectData =listItem
                    .split(HostSocketHandler.CMD_DELIMITER);
            Profile profileItem = new Profile();
            //set data as sent from host

            profileItem.setName(objectData[0]);
            profileItem.setAge(objectData[1]);
            profileItem.setGender(objectData[2]);

            //Add data to local profileList
            mprofileList.add(profileItem);
        }
        //copy data to static global profile list to be used by other activities
        clientProfileList = mprofileList;

    }


}
