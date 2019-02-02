package com.example.kumar.testapp.hostmode;

import android.os.Handler;
import android.util.Base64;
import android.util.Log;

import com.example.kumar.testapp.Profile;
import com.example.kumar.testapp.Playlist;
import com.example.kumar.testapp.VotedSong;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.example.kumar.testapp.hostmode.HostMusicFragment.musicPlayList;
import static com.example.kumar.testapp.hostmode.HostMusicFragment.sharedPlayList;

/**
 * Created by Kumar on 1/26/2017.
 */

public class HostSocketHandler extends Thread {
    ServerSocket serverSocket = null;
    public static ServerSocket hostSocket = null;
    ServerSocketChannel serverSocket1 = null;
    Selector selector = null;
    private static final String TAG = "HostSocketHandler";
    public static final int NUM_CONNECTIONS = 10;

    public static final int SERVER_PORT = 9001;

    public static final int SERVER_CALLBACK = 103;


    public static final int HOST_CALLBACK_PROFILE = 106;

    // use a 10 second time out to receive an ack message
    public static final int ACK_TIMEOUT = 10000;

    // a hashmap of all client socket connections and their corresponding output
    // streams for writing
    private HashSet<Socket> connections;
    public static HashSet<Socket> connections1;

    private boolean needReset = true;
    private Handler handler;

    // for splitting command messages, it should be a character that cannot be
    // used in a file name, all commands have to end with a delimiter
    public static final String CMD_DELIMITER = ";";
    public static final String LST_DELIMITER = "*";

    private static final int BUFFER_SIZE = 2048;
    private static final int BUFFER_SIZE1 = 1024 * 1024;

    // commands the server can send out to the clients
    public static final String PLAY_CMD = "PLAY";
    public static final String STOP_CMD = "STOP";
    public static final String SYNC_CMD = "SYNC";
    public static final String PRFL_CMD = "PRFL_CMD";
    public static final String PRFL = "PRFL";
    public static final String PLST_CMD = "PLST";
    public static final String VOTE_CMD = "VOTE_CMD";

    //hashmap for storing songNames and vote
    public static HashMap<String,Integer> VoteBank = new HashMap<String, Integer>();
    private ArrayList<byte[]> fileList = new ArrayList<byte[]>();
    private String clientFileName = new String();


    public HostSocketHandler(Handler handler) throws IOException
    {
        this.handler = handler;
        connections = new HashSet<Socket>();
        establishSocket();
    }

    @Override
    public void run()
    {
        // let the UI thread control the server

        handler.obtainMessage(SERVER_CALLBACK, this).sendToTarget();
        // thread will terminate when someone disconnects the server
        while (true)
        {

            try
            {
                int num = selector.select();
                if (num == 0) {
                    continue;
                }
                // Get the keys corresponding to the activity
                // that have been detected and process them
                // one by one.
                Set keys = selector.selectedKeys();
                Iterator it = keys.iterator();
                while (it.hasNext()) {
                    // Get a key representing one of bits of I/O
                    // activity.
                    SelectionKey key = (SelectionKey)it.next();
                    if ((key.readyOps() & SelectionKey.OP_ACCEPT) ==
                            SelectionKey.OP_ACCEPT) {
                        // Accept the incoming connection.
                        SocketChannel clientSocketChannel = serverSocket1.accept();
                        // ... Deal with incoming connection...
                        syncClientTime(clientSocketChannel.socket());
                        connections.add(clientSocketChannel.socket());
                        clientSocketChannel.configureBlocking(false);
                        clientSocketChannel.register(selector, SelectionKey.OP_READ|SelectionKey.OP_WRITE);

                        connections1 = connections;
                    }
                    else if ((key.readyOps() & SelectionKey.OP_READ) ==
                            SelectionKey.OP_READ) {
                        SocketChannel sc = (SocketChannel)key.channel();
                        recvSocketData(sc);
                    }

                }
                // Remove the selected keys because you've dealt
                // with them.
                keys.clear();

            }
            catch (IOException e)
            {
                Log.e(TAG, "Could not communicate to client socket.");
                try
                {
                    if (serverSocket != null && !serverSocket.isClosed())
                    {
                        needReset = true;

                        // close all client socket connections
                        for (Socket s : connections)
                        {
                            s.close();
                        }

                        // empty the stored connection list
                        connections = new HashSet<Socket>();
                    }
                }
                catch (IOException e1)
                {
                    Log.e(TAG, "Could not close all client sockets.");
                    disconnectServer();
                }
                break;
            }

        }
    }

    public void establishSocket()
    {
        // only setup a new server socket if something went wrong
        try {
            selector = Selector.open();
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Log.e(TAG,"Error initializing selector");
        }

        try
        {
            serverSocket1 = ServerSocketChannel.open();
            serverSocket1.configureBlocking(false);
            serverSocket1.socket().bind(new InetSocketAddress(SERVER_PORT));
            serverSocket1.register(selector, SelectionKey.OP_ACCEPT);
            Log.d(TAG, "Socket Started");
            //System.out.println("Host socket address: " + serverSocket.getLocalPort());
            hostSocket = serverSocket;


        }
        catch (IOException e)
        {
            Log.e(TAG, "Could not start server socket.");
        }
    }

    private void syncClientTime(Socket clientSocket) throws IOException
    {
        Log.d(TAG, "Started syncing time.");

        // initialize the network latency trackers
        long prevLatency = 0;
        long currLatency = 0;
        long sendTime = System.currentTimeMillis();

        // this is the minimum latency we are willing to accept, we have to
        // relax this requirement if the network is poor
        long ACCEPTABLE_LATENCY = 50;

        InputStream iStream = clientSocket.getInputStream();
        OutputStream oStream = clientSocket.getOutputStream();

        boolean success = false;
        boolean ackReceived = false;

        clientSocket.setSoTimeout(ACK_TIMEOUT);

        while (!success)
        {
            // see if we can reach time synchronization within 7 attempts
            for (int i = 0; i < 7; i++)
            {
                // preparing command to send, this should be as fast as possible
                // ***********Warning: time sensitive code!***********
                String command = SYNC_CMD + CMD_DELIMITER;

                // use this to measure the latency
                sendTime = System.currentTimeMillis();

                // compensating time sync with network latencies:
                // assume our time sync message reaches our client in
                // approximately half of the send and receive time
                command += String.valueOf(currLatency / 2 + System.currentTimeMillis())
                        + CMD_DELIMITER;
                oStream.write(command.getBytes());
                // ***********End of time sensitive code************

                while (!ackReceived)
                {
                    // clear the buffer before reading
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytes;

                    // Read from the InputStream and determine the network
                    // latency, this will not block forever as the read timeout
                    // has been set
                    bytes = iStream.read(buffer);
                    if (bytes != -1)
                    {
                        // check for the correct acknowledge message, we don't
                        // want
                        // to respond to any other messages other than the SYNC
                        // ack
                        // from the client
                        String recMsg = new String(buffer);

                        String[] cmdString = recMsg.split(CMD_DELIMITER);

                        if (cmdString[0]
                                .equals(HostSocketHandler.SYNC_CMD)
                                && cmdString.length > 1)
                        {
                            ackReceived = true;

                            // let's hope that the current communication
                            // latencies is within our acceptable latency when
                            // compared to
                            // the previous communication latency
                            prevLatency = currLatency;

                            // just to make the method call similar to client's,
                            // to improve the accuracy of the client receive
                            // time is half of the round trip delay
                            Long.parseLong(cmdString[1]);

                            currLatency = System.currentTimeMillis() - sendTime;

                            // can this wrap around? producing a negative
                            // number?
                            if (currLatency < 0)
                            {
                                currLatency *= -1;
                            }

                            // comparing latency jitters:
                            // if this round of latency is acceptable, then the
                            // previously sent time should be reasonable enough
                            // to be used to sync the time for our clients
                            System.out.println("Difference: " + (currLatency - prevLatency));
                            System.out.println("Latency" + ACCEPTABLE_LATENCY);

                            if (Math.abs(currLatency - prevLatency) < ACCEPTABLE_LATENCY)
                            {
                                success = true;
                                Log.d(TAG, "Accepted latency: "
                                        + ACCEPTABLE_LATENCY);
                                break;
                            }

                        }
                    }
                    // socket read timed out, so treat it as an ack has been
                    // received and exit this while loop and send another
                    // message
                    else
                    {
                        ackReceived = true;

                        Log.d(TAG, "Socket read timed out.");
                    }
                }

                Log.d(TAG, "Command Sent: " + command
                        + ", and retrieved network latency of " + currLatency
                        + " ms.");
                ackReceived = false;

                if (success)
                {
                    break;
                }
            }

            // still can't get a satisfactory result, let's relax our
            // requirement by 2 folds
            ACCEPTABLE_LATENCY *= 2;

            // we have to call it quits some time
            if (ACCEPTABLE_LATENCY > 10000)
            {
                success = true;
            }
        }
    }

    public void sendPlay(String fileName, long playTime, int playPosition)
    {
        if (fileName == null)
        {
            return;
        }

        String command = PLAY_CMD + CMD_DELIMITER + fileName + CMD_DELIMITER
                + String.valueOf(playTime) + CMD_DELIMITER
                + String.valueOf(playPosition) + CMD_DELIMITER
                + "This is just a filler " + CMD_DELIMITER;

        Log.d(TAG, "Sending command: " + command);

        for (Socket s : connections)
        {
            sendCommand(s, command);
        }
    }

    public void sendStop()
    {
        String command = STOP_CMD + CMD_DELIMITER + "This message is just a filler"
                + CMD_DELIMITER;

        Log.d(TAG, "Sending command: " + command);

        for (Socket s : connections)
        {
            sendCommand(s, command);
        }
    }

    private void sendCommand(Socket clientSocket, String command)
    {
        final Socket cSocket = clientSocket;
        final String cmd = command;

        if (clientSocket == null)
        {
            return;
        }

        // automatically update the client connections, making sure the client
        // sockets are always "fresh"
        if (clientSocket.isClosed())
        {
            connections.remove(clientSocket);
            clientSocket = null;
            return;
        }

        try
        {
            ExecutorService executorService = Executors.newFixedThreadPool(5);
            executorService.execute(new Runnable() {
                public void run() {
                    try {
                        SocketChannel sChannel = cSocket.getChannel();
                        ByteBuffer buffer = ByteBuffer.wrap(cmd.getBytes());
                        sChannel.write(buffer);
                    }
                    catch (IOException e)
                    {
                        e.printStackTrace();
                    }


                    Log.d(TAG, "Command Sent: " + cmd);
                }
            });

            executorService.shutdown();
            // get the corresponding output stream from the socket

        }
        catch (Exception e)
        {
            try
            {
                // this client socket is no longer valid, remove it from the
                // list
                clientSocket.close();
                connections.remove(clientSocket);
                clientSocket = null;
            }
            catch (IOException e1)
            {
                Log.e(TAG, "Cannot remove invalid client socket.");
            }

            Log.e(TAG, "Cannot send command over to client: " + command);
        }
    }

    public void disconnectClients()
    {
        // close all client socket connections
        for (Socket s : connections)
        {
            try
            {
                if (s != null && !s.isClosed())
                {
                    s.close();
                    s = null;
                }
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        // empty the stored connection list
        connections = new HashSet<Socket>();
    }

    public void disconnectServer()
    {
        needReset = true;

        // must disconnect all clients first
        disconnectClients();

        // empty the client connection list
        connections = null;
        connections = new HashSet<Socket>();

        try
        {
            if (serverSocket != null && !serverSocket.isClosed())
            {
                serverSocket.close();
            }
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
        serverSocket = null;
    }
    //Send profile data to clients
    public static void sendProfileData(){

        ArrayList<Profile> profiles = HostActivity.profileList;
        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append(PRFL + CMD_DELIMITER);
        for (int i = 0; i < profiles.size() ; i++) {
            cmdBuilder.append(profiles.get(i).getName());
            cmdBuilder.append(CMD_DELIMITER + profiles.get(i).getAge());
            cmdBuilder.append(CMD_DELIMITER + profiles.get(i).getGender() + LST_DELIMITER);
        }
        cmdBuilder.append("FILE_END");
        final String command = cmdBuilder.toString();

        ExecutorService executorService = Executors.newFixedThreadPool(5);
        executorService.execute(new Runnable() {
            public void run() {
                if (connections1 != null) {
                    for (Socket s : connections1)
                    {
                        try {
                            if (s == null) {
                                return;
                            }
                            try
                            {
                                // get the corresponding output stream from the socket
                                SocketChannel sChannel = s.getChannel();
                                ByteBuffer buffer = ByteBuffer.wrap(command.getBytes());
                                sChannel.write(buffer);
                                Log.d(TAG, "Profile List Sent");

                            }
                            catch (IOException e)
                            {

                                Log.e(TAG, "Cannot send profile list to clients");
                            }
                        }
                        catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                }
                else{
                    Log.d(TAG, "No Clients connected");
                }
            }
        });

        executorService.shutdown();
    }
    //Send playlist data to Clients
    public static void sendPlayList(ArrayList<Playlist> musicList){


        StringBuilder cmdBuilder = new StringBuilder();
        cmdBuilder.append(PLST_CMD + CMD_DELIMITER);
        for (int i = 0; i < musicList.size() ; i++) {
            cmdBuilder.append(musicList.get(i).getTrackName());
            cmdBuilder.append(CMD_DELIMITER + musicList.get(i).getArtistName());
            cmdBuilder.append(CMD_DELIMITER + musicList.get(i).getAlbum());
            //Base64 encoding of song art byte array
            String encodedbyteString = Base64.encodeToString(musicList.get(i).getArt(), 0);
            cmdBuilder.append(CMD_DELIMITER + encodedbyteString + LST_DELIMITER);
        }
        cmdBuilder.append("FILE_END");
        final String command = cmdBuilder.toString();

        ExecutorService executorService = Executors.newFixedThreadPool(5);
        executorService.execute(new Runnable() {
            public void run() {
                if (connections1 != null) {
                    for (Socket s : connections1)
                    {
                        try {
                            if (s == null) {
                                return;
                            }
                            try
                            {
                                // get the corresponding output stream from the socket
                                SocketChannel sChannel = s.getChannel();
                                ByteBuffer buffer = ByteBuffer.wrap(command.getBytes());
                                sChannel.write(buffer);
                                Log.d(TAG, "Playlist Sent");

                            }
                            catch (IOException e)
                            {

                                Log.e(TAG, "Cannot send playlist over to client");
                            }
                        }
                        catch (Exception e){
                            e.printStackTrace();
                        }
                    }
                }
                else{
                    Log.d(TAG, "No Clients connected");
                }
            }
        });

        executorService.shutdown();

    }

    private void recvSocketData(SocketChannel clientSockChannel){

        try {
            // clear the buffer before reading
            ByteBuffer buf = ByteBuffer.allocate(BUFFER_SIZE1);
            buf.clear();

            int bytenumber = clientSockChannel.read(buf);
            if (bytenumber > 0) {
                buf.flip();
            }
            byte[] byteArray = new byte[buf.remaining()];
            buf.get(byteArray);

            byte[] startArray = Arrays.copyOfRange(byteArray, 0, 8);
            String recMsg1 = new String(startArray, "UTF-8");

            byte[] endArray = Arrays.copyOfRange(byteArray, bytenumber - 8, bytenumber);
            String recMsg2 = new String(endArray, "UTF-8");

            if (recMsg1.equals("FILE_STR")) {

                byte[] lenArray = Arrays.copyOfRange(byteArray, 8, 10);
                String fileLen = new String(lenArray, "UTF-8");
                int intFileLen = Integer.valueOf(fileLen);

                byte[] NameArray = Arrays.copyOfRange(byteArray, 10, 10 + intFileLen);
                clientFileName = new String(NameArray, "UTF-8");

                fileList = new ArrayList<byte[]>();
                byte[] dataArray = Arrays.copyOfRange(byteArray, 10 + intFileLen, byteArray.length);
                fileList.add(dataArray);
                /*try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }*/
                Log.i(TAG, "Received part 1 of file data");
                //if File is short it is sent at once prepare the file now
                if(recMsg2.equals("FILE_END")){
                    byte[] endDataArray = Arrays.copyOfRange(byteArray, 0, bytenumber - 9);
                    fileList.add(endDataArray);

                    Log.i(TAG, "Received last part of file data at once");

                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                    for (int i = 0; i < fileList.size(); i++) {
                        outputStream.write(fileList.get(i));
                    }
                    byte[] combined = outputStream.toByteArray();
                    byte[] decoded = Base64.decode(combined, 0);
                    File fileName = new File("/storage/emulated/0/Music/" + clientFileName + ".mp3");
                    FileChannel outFile = new FileOutputStream(fileName).getChannel();
                    ByteBuffer buffer = ByteBuffer.wrap(decoded);
                    /*try {
                        Thread.sleep(5000);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    }*/
                    outFile.write(buffer);
                    Log.i(TAG, "Written file data");

                    VotedSong song = new VotedSong();
                    song.setSongName(clientFileName);
                    song.setSongPath(fileName.getPath());
                    song.setVote(0);
                    HostMusicFragment.sharedPlayList.add(song);

                    //Create and send playlist for synchronization
                    HostMusicFragment.createPlaylist(HostMusicFragment.sharedPlayList);
                    HostSocketHandler.sendPlayList(musicPlayList);
                }
            }
            else if (recMsg2.equals("FILE_END")) {
                byte[] endDataArray = Arrays.copyOfRange(byteArray, 0, bytenumber - 9);
                fileList.add(endDataArray);

                Log.i(TAG, "Received last part of file data");

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

                for (int i = 0; i < fileList.size(); i++) {
                    outputStream.write(fileList.get(i));
                }
                byte[] combined = outputStream.toByteArray();
                byte[] decoded = Base64.decode(combined, 0);
                File fileName = new File("/storage/emulated/0/Music/" + clientFileName + ".mp3");
                FileChannel outFile = new FileOutputStream(fileName).getChannel();
                ByteBuffer buffer = ByteBuffer.wrap(decoded);
                /*try {
                    Thread.sleep(5000);
                } catch (InterruptedException ie) {
                    ie.printStackTrace();
                }*/
                outFile.write(buffer);
                Log.i(TAG, "Written file data");

                VotedSong song = new VotedSong();
                song.setSongName(clientFileName);
                song.setSongPath(fileName.getPath());
                song.setVote(0);
                HostMusicFragment.sharedPlayList.add(song);

                //Create and send playlist for synchronization
                HostMusicFragment.createPlaylist(HostMusicFragment.sharedPlayList);
                HostSocketHandler.sendPlayList(musicPlayList);

            }
            else if (recMsg1.equals(VOTE_CMD)) {
                String recMsg = new String(byteArray, "UTF-8");
                String[] cmdString = recMsg
                        .split(CMD_DELIMITER);
                //Strip the voted song
                String songName = cmdString[1];

                //Find the song and update vote count
                for (int i = 0; i < HostMusicFragment.sharedPlayList.size() ; i++) {
                    if(HostMusicFragment.sharedPlayList.get(i).getSongName().equals(songName)){
                        int oldCount = HostMusicFragment.sharedPlayList.get(i).getVote();
                        HostMusicFragment.sharedPlayList.get(i).setVote(oldCount + 1);
                    }
                }
                //sort shared playlist
                HostMusicFragment.sortPlaylist(sharedPlayList);
                //Create and send playlist for synchronization
                HostMusicFragment.createPlaylist(HostMusicFragment.sharedPlayList);
                HostSocketHandler.sendPlayList(HostMusicFragment.musicPlayList);
            }
            else if (recMsg1.equals(PRFL_CMD)) {
                String recMsg = new String(byteArray, "UTF-8");
                String[] cmdString = recMsg
                        .split(CMD_DELIMITER);
                //Strip the profile data
                Profile profile = new Profile();
                profile.setName(cmdString[1]);
                profile.setAge(cmdString[2]);
                profile.setGender(cmdString[3]);

                //Add this data to shared Profile list
                HostActivity.profileList.add(profile);
                //send profile list to synchronize
                sendProfileData();
            }
            else {
                    //It is part of mp3 file data
                    fileList.add(byteArray);
                   /* try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        ie.printStackTrace();
                    } */
                    Log.i(TAG, "Received subsequent part of file data");
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
            Log.e(TAG, "Cannot receive data from client");
        }

    }

}
