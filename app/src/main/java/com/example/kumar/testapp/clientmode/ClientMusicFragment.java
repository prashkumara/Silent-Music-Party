package com.example.kumar.testapp.clientmode;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.kumar.testapp.AssistMe;
import com.example.kumar.testapp.MusicManager;
import com.example.kumar.testapp.MyTimer;
import com.example.kumar.testapp.PlaylistControl;
import com.example.kumar.testapp.R;
import com.example.kumar.testapp.hostmode.HostSocketHandler;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

/**
 * Created by Kumar on 1/27/2017.
 */

public class ClientMusicFragment extends Fragment
{
    // Json object for client server communication
    private String isbtnPlay = "no";
    private static final String TAG = "Client Music Player";
    private String songTitle = "default SongTitle";
    private String songInfo = "Sorry, web service is streaming now....";
    private SeekBar songProgressBar;
    private TextView songTitleLabel;
    private TextView songCurrentDurationLabel;
    private TextView songTotalDurationLabel;
    // Media Player
    private MediaPlayer mp;
    // Handler to update UI timer, progress bar etc,.
    private Handler mHandler = new Handler();;
    private MusicManager songManager;
    private AssistMe utils;
    private MyTimer musicTimer = null;
    private Activity mActivity = null;
    private ClientActivity cActivity = null;
    private ClientMusicFragment mFragment = null;
    private View mContentView = null;
    private int currentPlayPosition = 0;

    private ArrayList<HashMap<String, String>> songsList = new ArrayList<HashMap<String, String>>();
    private HashMap<String, String> song = new HashMap<String, String>();
    private String[] clientMusicList = {"song1", "song2"};
    private int currentSongIndex = 0;
    private  String musicName = "default song name";


    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        mActivity = this.getActivity();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        super.onCreateView(inflater, container, savedInstanceState);
        mFragment = this;

        mContentView = inflater.inflate(R.layout.frag_client_music, null);

        // All player buttons
        songProgressBar = (SeekBar) mContentView
                .findViewById(R.id.songProgressBar);
        songTitleLabel = (TextView) mContentView.findViewById(R.id.songTitle);
        songCurrentDurationLabel = (TextView) mContentView
                .findViewById(R.id.songCurrentDurationLabel);
        songTotalDurationLabel = (TextView) mContentView
                .findViewById(R.id.songTotalDurationLabel);

        // Mediaplayer
        mp = new MediaPlayer();
        songManager = new MusicManager();
        utils = new AssistMe();

        // Add songs to shared play list
        ImageButton button = (ImageButton) mContentView
                .findViewById(R.id.btnPlstadd);
        button.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                // Perform action on click
                AlertDialog.Builder builder = new AlertDialog.Builder(
                        mActivity);

                clientMusicList = getMusicList().toArray(
                        new String[getMusicList().size()]);
                builder.setTitle("Add Your Favorite Track!");
                builder.setSingleChoiceItems(clientMusicList, -1,
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int item)
                            {
                                currentSongIndex = item;
                            }
                        });

                builder.setPositiveButton("OK",
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog,
                                                int position)
                            {
                                // send song to shared playlist
                                try {
                                    if (clientMusicList.length == 0) {
                                        Toast.makeText(mContentView.getContext(), "No song selected," +
                                                        "Add songs to Music folder",
                                                Toast.LENGTH_SHORT).show();
                                    } else {
                                        song.put("musicTitle", songsList.get(currentSongIndex).get("musicTitle"));
                                        song.put("musicPath", songsList.get(currentSongIndex).get("musicPath"));
                                        sendSong(song);
                                    }
                                }
                                catch (Exception e){
                                    e.printStackTrace();
                                    Toast.makeText(mContentView.getContext(), "Add songs to Music folder",
                                            Toast.LENGTH_SHORT).show();
                                }

                            }
                        });
                AlertDialog dialog = builder.create();
                dialog.show();

            }
        });

        // View shared play list
        ImageButton button1 = (ImageButton) mContentView
                .findViewById(R.id.btnPlaylist);
        button1.setOnClickListener(new View.OnClickListener()
        {
            public void onClick(View v)
            {
                if(ClientSocketHandler.clientPlayList.size() == 0){
                    Toast.makeText(mContentView.getContext(), "Shared Playlist not yet ready!!!",
                            Toast.LENGTH_SHORT).show();
                }
                else{
                    System.out.println("You clicked viewPlaylist");
                    Intent intent = new Intent(mContentView.getContext(), PlaylistControl.class);
                    intent.putExtra("MODE", "client");
                    startActivity(intent);
                }
            }
        });
        return mContentView;
    }


    // for speaker, input is Url
    public void playSong(String url, long startTime, int startPos)
    {
        // This part of the code is time sensitive, it should be done as fast as
        // possible to avoid the delay in the music
        try
        {
            mp.reset();
            mp.setDataSource(url);

            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);

            //mp.setOnPreparedListener(this);
            //mp.prepare();
            mp.prepareAsync();

            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    isbtnPlay = "yes";
                }
            });



            // TODO: make sure we have buffered REALLY
            if (isbtnPlay == "yes")
            {
                mp.start();
                if (mp.isPlaying())
                {
                    mp.pause();
                }
                mp.start();
                if (mp.isPlaying())
                {
                    mp.pause();
                }
                mp.start();
                if (mp.isPlaying())
                {
                    mp.pause();
                }
                mp.start();
                if (mp.isPlaying())
                {
                    mp.pause();
                }
                mp.start();
                if (mp.isPlaying())
                {
                    mp.pause();
                }

                /*mp.start();
                mp.pause();
                mp.start();
                mp.pause();
                mp.start();
                mp.pause();
                mp.start();
                mp.pause();*/
            }

            musicTimer = //cActivity.getTimer();
                    ((ClientDevListFragment.ClientFragmentListener) getActivity())
                            .getTimer();

            // let the music timer determine when to play the future playback
            if (isbtnPlay == "yes") {
                musicTimer.playFutureMusic(mp, startTime, startPos);
            }
            // set Progress bar values
            songProgressBar.setProgress(0);
            songProgressBar.setMax(100);

            // Updating progress bar
            if (isbtnPlay == "yes") {
                updateProgressBar();
            }
            // parsing songTitle

            String temp[] = url.split("/");
            String tempTitle[] = temp[temp.length - 1].split("\\.");

            // initialize songTitle
            songTitle = "";

            // song title could have "." in the name, so let's replace them with
            // space we also ignore then last "." because that signifies the
            // file extension
            for (int i = 0; i < tempTitle.length - 1; i++)
            {
                songTitle = songTitle + " " + tempTitle[i];
            }

            // set the song title after playing the music
            songTitleLabel.setText("Now Playing: " + songTitle);

            try
            {
                // filter out all numbers and strange characters
                songTitle = songTitle.replaceAll("[0-9-_(){}]", " ");
                songTitle = URLEncoder.encode(songTitle, "utf-8");
            }
            catch (UnsupportedEncodingException e)
            {
                Log.e(TAG, e.getMessage());
            }
            //AsynWrap();
            isbtnPlay = "no";
        }
        catch (IllegalArgumentException e)
        {
            Log.e(TAG, "IllegalArgumentException");
        }
        catch (IllegalStateException e)
        {
            Log.e(TAG, "illegalStateException");
        }
        catch (IOException e)
        {
            Log.e(TAG, "IOexception");
        }
    }

    public void stopMusic()
    {
        if (mp != null)
        {
            //mp.pause();
            if (mp.isPlaying())
            {
                mp.pause();
            }
        }
    }

    /**
     * Update timer on seekbar
     */
    public void updateProgressBar()
    {
        // initialize the bar
        long totalDuration = mp.getDuration();

        // Displaying Total Duration time
        songTotalDurationLabel.setText(""
                + utils.milliSecondsToTimer(totalDuration));
        // Displaying time completed playing
        songCurrentDurationLabel.setText(""
                + utils.milliSecondsToTimer(currentPlayPosition));

        // Updating progress bar
        int progress = (int) (utils.getProgressPercentage(currentPlayPosition,
                totalDuration));
        songProgressBar.setProgress(progress);

        mHandler.postDelayed(mUpdateTimeTask, 100);
    }

    /**
     * Background Runnable thread for song progress
     */
    private Runnable mUpdateTimeTask = new Runnable()
    {
        public void run()
        {
            if (mp == null)
            {
                return;
            }

            // only update the progress if music is playing
            if (mp.isPlaying())
            {
                long totalDuration = mp.getDuration();
                currentPlayPosition = mp.getCurrentPosition();

                // Displaying Total Duration time
                songTotalDurationLabel.setText(""
                        + utils.milliSecondsToTimer(totalDuration));
                // Displaying time completed playing
                songCurrentDurationLabel.setText(""
                        + utils.milliSecondsToTimer(currentPlayPosition));

                // Updating progress bar
                int progress = (int) (utils.getProgressPercentage(
                        currentPlayPosition, totalDuration));
                songProgressBar.setProgress(progress);
            }

            // Running this thread after 100 milliseconds
            mHandler.postDelayed(this, 100);
        }
    };


    @Override
    public void onDestroy()
    {
        super.onDestroy();
        mp.release();
        mp = null;
    }

    // get list of music from internal storage
    private ArrayList<String> getMusicList()
    {
        ArrayList<HashMap<String, String>> songsListData = new ArrayList<HashMap<String, String>>();
        ArrayList<String> musicList = new ArrayList<String>();
        MusicManager mpl = new MusicManager();
        // get all songs from Music memory
        if (mpl.storagePermitted(this.getActivity())) {
            this.songsList = mpl.getPlayList(this.getActivity());
        }

        //this.songsList = mpl.getPlayList(this.getActivity());

        // looping through playlist
        for (int i = 0; i < songsList.size(); i++)
        {
            // creating new HashMap
            HashMap<String, String> song = songsList.get(i);
            // adding HashList to ArrayList
            songsListData.add(song);
        }

        for (int i = 0; i < songsListData.size(); i++)
        {
            // creating new HashMap
            musicList.add(songsListData.get(i).get("musicTitle"));
            // adding HashList to ArrayList
        }

        return musicList;
    }

    //Send vote for playList item

    public static void sendVote(String musicName)
    {
        final String songName = musicName;
        try {
            if (ClientSocketHandler.socket1 == null) {
                return;
            }

                ExecutorService executorService = Executors.newFixedThreadPool(5);

                executorService.execute(new Runnable() {
                    public void run() {
                        try
                        {
                            // get the corresponding output stream from the socket
                        OutputStream oStream = ClientSocketHandler.socket1.getOutputStream();
                        String command = HostSocketHandler.VOTE_CMD + HostSocketHandler.CMD_DELIMITER + songName
                                +HostSocketHandler.CMD_DELIMITER + "This is buffer data";
                        oStream.write(command.getBytes());
                        Log.d(TAG, "Vote Sent");
                        }
                        catch (IOException e)
                        {
                            Log.e(TAG, "Cannot send Vote to the host");
                        }
                    }
                });

                executorService.shutdown();


        }
        catch (Exception e){
            e.printStackTrace();
        }

    }

    //Send song to shared playlist
    private void sendSong(HashMap<String,String> song){
        final String songPath = song.get("musicPath");
        final String trackName = song.get("musicTitle");
        final File songName = new File(songPath);

        try {
            if (ClientSocketHandler.socket1 == null) {
                return;
            }

            ExecutorService executorService = Executors.newFixedThreadPool(5);

            executorService.execute(new Runnable() {
                public void run() {
                    try
                    {
                        byte[] mybytearray = FileUtils.readFileToByteArray(songName);
                        String encoded = Base64.encodeToString(mybytearray, 0);
                        int size = trackName.length();
                        String command = new String();
                        if (size <= 9){
                            command = "FILE_STR" + 0 + size + trackName + encoded + "FILE_END";
                        }
                        else{
                            command = "FILE_STR" + size + trackName + encoded + "FILE_END";
                        }


                        OutputStream oStream = ClientSocketHandler.socket1.getOutputStream();
                        //Sending file name and file to the server
                        oStream.write(command.getBytes());
                        Log.d(TAG, "Song Sent");
                    }
                    catch (IOException e)
                    {
                        Log.e(TAG, "Cannot send Song to the host");
                    }
                }
            });

            executorService.shutdown();

        }
        catch (Exception e){
            e.printStackTrace();
        }

    }

}
