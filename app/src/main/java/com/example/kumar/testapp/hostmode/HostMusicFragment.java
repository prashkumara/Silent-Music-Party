package com.example.kumar.testapp.hostmode;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Random;

import com.example.kumar.testapp.MyTimer;
import com.example.kumar.testapp.Playlist;
import com.example.kumar.testapp.PlaylistControl;
import com.example.kumar.testapp.R;
import com.example.kumar.testapp.VotedSong;

/**
 * Created by Kumar on 1/27/2017.
 */

public class HostMusicFragment  extends Fragment implements MediaPlayer.OnCompletionListener,
        SeekBar.OnSeekBarChangeListener
{
    // Json object for client server communication
    private String isbtnPlay = "no";

    private ImageButton btnPlay;
    private ImageButton btnNext;
    private ImageButton btnPrevious;
    private ImageButton btnPlaylist;
    private ImageButton btnPlstadd;
    private ImageButton btnRepeat;
    private ImageButton btnShuffle;
    private SeekBar songProgressBar;
    private TextView songTitleLabel;
    private TextView songCurrentDurationLabel;
    private TextView songTotalDurationLabel;
    private ProgressDialog syncProgress;

    // Media Player
    private MediaPlayer mp;
    // Handler to update UI timer, progress bar etc,.
    private Handler mHandler = new Handler();;
    private MusicManager songManager;
    private AssistMe utils;
    private int currentSongIndex = 0;
    private int cSongIndex = 0;
    // for resuming music
    private int currentPlayPosition = PLAY_FROM_BEGINNING;
    private boolean isShuffle = false;
    private boolean isRepeat = false;
    //Song list from memory(Music folder)
    private ArrayList<HashMap<String, String>> songsList = new ArrayList<HashMap<String, String>>();
    //Distributed playlist(Only song and path) from where music is being played
    public static ArrayList<VotedSong> sharedPlayList = new ArrayList<VotedSong>();
    //playlist(with meta data) to be forwarded to all Clients
    public static ArrayList<Playlist> musicPlayList = new ArrayList<Playlist>();
    //default byte array for songart to be appended in playlist
    private static byte[] defaultbyte = null;

    private Activity mActivity = null;
    private View mContentView = null;
    private MyTimer musicTimer = null;
    private final static long DELAY = 4500;
    private final static int PLAY_FROM_BEGINNING = 0;
    private final String TAG = "Host Music Player";



    private String[] musicList = { "9129", "9231", "9232" };
    protected String selectMusic = new String();

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        mActivity = this.getActivity();
        try{
            defaultImage();
        }
        catch (IOException ioe){
            ioe.printStackTrace();
            Log.i(TAG,"Unable to generate default song art");
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState)
    {
        super.onCreateView(inflater, container, savedInstanceState);
        mContentView = inflater.inflate(R.layout.frag_host_music, null);


        // All player buttons
        btnPlay = (ImageButton) mContentView.findViewById(R.id.btnPlay);
        btnNext = (ImageButton) mContentView.findViewById(R.id.btnNext);
        btnPrevious = (ImageButton) mContentView.findViewById(R.id.btnPrevious);
        btnPlaylist = (ImageButton) mContentView.findViewById(R.id.btnPlaylist);
        btnPlstadd = (ImageButton) mContentView.findViewById(R.id.btnPlstadd);
        btnRepeat = (ImageButton) mContentView.findViewById(R.id.btnRepeat);
        btnShuffle = (ImageButton) mContentView.findViewById(R.id.btnShuffle);
        songProgressBar = (SeekBar) mContentView
                .findViewById(R.id.songProgressBar);
        songTitleLabel = (TextView) mContentView.findViewById(R.id.songTitle);
        songCurrentDurationLabel = (TextView) mContentView
                .findViewById(R.id.songCurrentDurationLabel);
        songTotalDurationLabel = (TextView) mContentView
                .findViewById(R.id.songTotalDurationLabel);

        // prepare for a progress bar dialog
        syncProgress = new ProgressDialog(this.getActivity());
        syncProgress.setCancelable(false);
        syncProgress.setInverseBackgroundForced(true);
        syncProgress.setMessage("Get Ready to Enjoy Your Awesome Music!");
        syncProgress.setProgressStyle(ProgressDialog.STYLE_SPINNER);

        // Mediaplayer
        mp = new MediaPlayer();
        songManager = new MusicManager();
        utils = new AssistMe();

        // Listeners
        songProgressBar.setOnSeekBarChangeListener(this); // Important
        mp.setOnCompletionListener(this); // Important

        // Getting all songs list
        if (songManager.storagePermitted(this.getActivity())) {
            songsList = songManager.getPlayList(this.getActivity());
        }
        // By default play first song
        // add time synchronize here()

        /**
         * Play button click event plays a song and changes button to pause
         * image pauses a song and changes button to play image
         */
        btnPlay.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View arg0)
            {
                // check for already playing, if it is, this acts as a pause
                // button
                if (mp.isPlaying())
                {
                    if (mp != null)
                    {
                        // pause music play, and save the current playing
                        // position
                        mp.pause();
                        currentPlayPosition = mp.getCurrentPosition();

                        ((HostActivity) mActivity).stopRemoteMusic();

                        // Changing button image to play button
                        btnPlay.setImageResource(R.drawable.btn_play);
                    }
                }
                else
                {
                    // Resume song
                    if (mp != null)
                    {
                        // resume music play
                        playSong(0, currentPlayPosition);

                        // Changing button image to pause button
                        btnPlay.setImageResource(R.drawable.btn_pause);
                    }
                }

            }
        });

        /**
         * Next button click event Plays next song by taking currentSongIndex +
         * 1
         */
        btnNext.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View arg0)
            {
                if (isShuffle)
                {
                    // shuffle is on - play a random song
                    Random rand = new Random();
                    currentSongIndex = rand.nextInt((sharedPlayList.size() - 1) - 0 + 1) + 0;
                    playSong(currentSongIndex, PLAY_FROM_BEGINNING);
                }
                else
                {
                    // check if next song is there or not
                    if (currentSongIndex < (sharedPlayList.size() - 1))
                    {
                        playSong(currentSongIndex + 1, PLAY_FROM_BEGINNING);
                        currentSongIndex = currentSongIndex + 1;
                    }
                    else
                    {
                        // play first song
                        playSong(0, PLAY_FROM_BEGINNING);
                        currentSongIndex = 0;
                    }
                }
            }
        });

        /**
         * Back button click event Plays previous song by currentSongIndex - 1
         */
        btnPrevious.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View arg0)
            {
                if (isShuffle)
                {
                    // shuffle is on - play a random song
                    Random rand = new Random();
                    currentSongIndex = rand.nextInt((sharedPlayList.size() - 1) - 0 + 1) + 0;
                    playSong(currentSongIndex, PLAY_FROM_BEGINNING);
                }
                else
                {
                    if (currentSongIndex > 0)
                    {
                        playSong(currentSongIndex - 1, PLAY_FROM_BEGINNING);
                        currentSongIndex = currentSongIndex - 1;
                    }
                    else
                    {
                        // play last song
                        playSong(sharedPlayList.size() - 1, PLAY_FROM_BEGINNING);
                        currentSongIndex = sharedPlayList.size() - 1;
                    }
                }
            }
        });

        /**
         * Button Click event for Repeat button Enables repeat flag to true
         */
        btnRepeat.setOnClickListener(new View.OnClickListener()
        {

            @Override
            public void onClick(View arg0)
            {
                if (isRepeat)
                {
                    isRepeat = false;
                    Toast.makeText(mContentView.getContext(), "Repeat is OFF",
                            Toast.LENGTH_SHORT).show();
                    btnRepeat.setImageResource(R.drawable.btn_repeat);
                }
                else
                {
                    // make repeat to true
                    isRepeat = true;
                    Toast.makeText(mContentView.getContext(), "Repeat is ON",
                            Toast.LENGTH_SHORT).show();
                    // make shuffle to false
                    isShuffle = false;
                    btnRepeat.setImageResource(R.drawable.btn_repeat_focused);
                    btnShuffle.setImageResource(R.drawable.btn_shuffle);
                }
            }
        });

        /**
         * Button Click event for Shuffle button Enables shuffle flag to true
         */
        btnShuffle.setOnClickListener(new View.OnClickListener()
        {

            @Override
            public void onClick(View arg0)
            {
                if (isShuffle)
                {
                    isShuffle = false;
                    Toast.makeText(mContentView.getContext(), "Shuffle is OFF",
                            Toast.LENGTH_SHORT).show();
                    btnShuffle.setImageResource(R.drawable.btn_shuffle);
                }
                else
                {
                    // make repeat to true
                    isShuffle = true;
                    Toast.makeText(mContentView.getContext(), "Shuffle is ON",
                            Toast.LENGTH_SHORT).show();
                    // make shuffle to false
                    isRepeat = false;
                    btnShuffle.setImageResource(R.drawable.btn_shuffle_focused);
                    btnRepeat.setImageResource(R.drawable.btn_repeat);
                }
            }
        });

        /**
         * Button Click event for Play list click event Launches list activity
         * which displays list of songs
         */
        btnPlaylist.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View arg0)
            {
                if(HostMusicFragment.sharedPlayList.size() == 0){
                    Toast.makeText(mContentView.getContext(), "Shared Playlist not yet ready!!!",
                            Toast.LENGTH_SHORT).show();
                }
                else {
                    System.out.println("You clicked viewPlaylist");
                    Intent intent = new Intent(mContentView.getContext(), PlaylistControl.class);
                    intent.putExtra("MODE", "host");
                    startActivity(intent);
                }

            }
        });

        //Add songs to shared playlist

        btnPlstadd.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View arg0)
            {

                AlertDialog.Builder builder = new AlertDialog.Builder(
                        mActivity);

                musicList = getMusicList().toArray(
                        new String[getMusicList().size()]);
                builder.setTitle("Select Your Favorite Music!");
                builder.setSingleChoiceItems(musicList, -1,
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog, int item)
                            {
                                cSongIndex = item;
                            }
                        });

                builder.setPositiveButton("OK",
                        new DialogInterface.OnClickListener()
                        {
                            public void onClick(DialogInterface dialog,
                                                int position)
                            {
                                //add music to shared playlist
                                try{
                                    if(musicList.length == 0){
                                        Toast.makeText(mContentView.getContext(), "No song selected," +
                                                        "Add songs to Music folder",
                                                Toast.LENGTH_SHORT).show();
                                    }
                                    else{
                                        VotedSong song = new VotedSong();
                                        song.setSongName(songsList.get(cSongIndex).get("musicTitle"));
                                        song.setSongPath(songsList.get(cSongIndex).get("musicPath"));
                                        song.setVote(0);
                                        sharedPlayList.add(song);
                                        //Create and send playlist for synchronization
                                        createPlaylist(HostMusicFragment.sharedPlayList);
                                        HostSocketHandler.sendPlayList(musicPlayList);
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

        return mContentView;
    }

    // get list of music from internal storage
    private ArrayList<String> getMusicList()
    {
        ArrayList<HashMap<String, String>> songsListData = new ArrayList<HashMap<String, String>>();
        ArrayList<String> musicList = new ArrayList<String>();
        MusicManager mpl = new MusicManager();
        // get all songs from Music memory

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

    //plays songs
    public void startSyncDialog()
    {
        syncProgress.show();

        new Thread(new Runnable()
        {
            public void run()
            {

                try
                {
                    Thread.sleep(DELAY);
                }
                catch (InterruptedException e)
                {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                // close the progress bar dialog
                syncProgress.dismiss();
            }
        }).start();
    }

    // for Host mode, input is index
    public void playSong(int songIndex, int playPosition)
    {
        // Play song
        try
        {
            // show the spinner and stop all user actions
            startSyncDialog();

            // first stop the remote music
            //if(mp.isPlaying()){
                ((HostActivity) mActivity).stopRemoteMusic();
            //}


            if (sharedPlayList.isEmpty())
            {
                Toast.makeText(mContentView.getContext(), "Empty Playlist",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            else if (sharedPlayList.get(songIndex) == null)
            {
                Toast.makeText(mContentView.getContext(),
                        "Cannot play this song", Toast.LENGTH_SHORT).show();
                return;
            }

            String musicFPath = sharedPlayList.get(songIndex).getSongPath();
            // Displaying Song title
            System.out.println("music path: " + musicFPath);
            String songTitle = sharedPlayList.get(songIndex).getSongName();
            System.out.println("music title: " + songTitle);
            mp.reset();

            // get the music timer
            musicTimer = ((HostActivity)mActivity).getTimer();

            // music path : Storage Music folder for Host mode
            mp.setDataSource(musicFPath);

            songTitleLabel.setText("Now Playing: " + songTitle);

            // Changing Button Image to pause image
            btnPlay.setImageResource(R.drawable.btn_pause);
            //mp.prepare();
            mp.prepareAsync();


           /* try
            {
                Thread.sleep(DELAY);
            }
            catch (InterruptedException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }*/

            mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer mp) {
                    isbtnPlay = "yes";
                }
            });

            // TODO: make sure we have buffered REALLY
            // buffered the music, currently this is a big
            // HACK and takes a lot of time. We can do
            // better!
            if (isbtnPlay == "yes") {
                System.out.println("I am inside player");
                mp.start();
                mp.pause();
                mp.start();
                mp.pause();
                mp.start();
                mp.pause();
                mp.start();
                mp.pause();
                mp.start();
                mp.pause();
            }

            // playRemoteMusic, time sensitive
            long futurePlayTime = musicTimer.getCurrTime() + DELAY;

            ((HostActivity) mActivity).playRemoteMusic(musicFPath,
                    futurePlayTime, playPosition);

            // let the music timer determine when to play the future playback
            if (isbtnPlay == "yes") {
                System.out.println("I am inside future");
                musicTimer.playFutureMusic(mp, futurePlayTime, playPosition);
            }
            // set the song Progress bar values
            songProgressBar.setProgress(0);
            songProgressBar.setMax(100);

            // Updating the song progress bar
            if (isbtnPlay == "yes") {
                System.out.println("I am inside progressbar");
                updateProgressBar();
            }
            isbtnPlay = "no";
            //Error Listener to catch ansyc thread errors(soft errors)
            mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                @Override
                public boolean onError(MediaPlayer mp, int what, int extra) {
                    return true;
                }
            });
        }
        catch (IllegalArgumentException e)
        {
            Log.e(TAG, "IllegalArgumentException");
        }
        catch (IllegalStateException e)
        {
            e.printStackTrace();
            Log.e(TAG, "illegalStateException");
        }
        catch (IOException e)
        {
            Log.e(TAG, "IOexception");
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
                // long currentDuration = mp.getCurrentPosition();
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
    public void onProgressChanged(SeekBar seekBar, int progress,
                                  boolean fromTouch)
    {

    }
    /**
     * When user starts moving the progress handler
     */
    @Override
    public void onStartTrackingTouch(SeekBar seekBar)
    {
        // remove message Handler from updating progress bar
        mHandler.removeCallbacks(mUpdateTimeTask);
    }

    /**
     * When user stops moving the progress handler
     */
    @Override
    public void onStopTrackingTouch(SeekBar seekBar)
    {
        // mHandler.removeCallbacks(mUpdateTimeTask);
        if (mp!= null){
            System.out.println("I am inside stoptracking touch");
            int totalDuration = mp.getDuration();

            // get the new playing position
            currentPlayPosition = utils.progressToTimer(seekBar.getProgress(),
                    totalDuration);

            playSong(currentSongIndex, currentPlayPosition);
        }

    }

    /**
     * On Song Playing completed if repeat is ON play same song again if shuffle
     * is ON play random song
     */
    @Override
    public void onCompletion(MediaPlayer arg0)
    {
        System.out.println("I am inside completion");
        // check for repeat is ON or OFF
        if (isRepeat)
        {
            // repeat is on play same song again
            playSong(currentSongIndex, PLAY_FROM_BEGINNING);
        }
        else if (isShuffle)
        {
            // shuffle is on - play a random song
            Random rand = new Random();
            currentSongIndex = rand.nextInt((sharedPlayList.size() - 1) - 0 + 1) + 0;
            playSong(currentSongIndex, PLAY_FROM_BEGINNING);
        }
        else
        {
            // no repeat or shuffle ON - play next song
            if (currentSongIndex < (sharedPlayList.size() - 1))
            {
                playSong(currentSongIndex + 1, PLAY_FROM_BEGINNING);
                currentSongIndex = currentSongIndex + 1;
            }
            else
            {
                // play first song
                playSong(0, PLAY_FROM_BEGINNING);
                currentSongIndex = 0;
            }
        }
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        mp.release();
        mp = null;
    }
    //Creating playlist with metadata
    public static void createPlaylist(ArrayList<VotedSong> sharedMusicList){

        MediaMetadataRetriever  metaRetriever = new MediaMetadataRetriever();

        String songPath = new String();
        String songTitle = new String();

        ArrayList<Playlist> mplayList = new ArrayList<Playlist>();


        for (int i = 0; i < sharedMusicList.size() ; i++) {
            songPath = sharedMusicList.get(i).getSongPath();
            songTitle = sharedMusicList.get(i).getSongName();
            Playlist songData = new Playlist();



            try{
                System.out.println("Songpath:" + songPath);
                metaRetriever.setDataSource(songPath);
                byte[] songArt = metaRetriever.getEmbeddedPicture();
                if (songArt == null){
                    songData.setArt(defaultbyte);
                }
                else {
                    songData.setArt(metaRetriever.getEmbeddedPicture());
                }

                if( metaRetriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) == null){
                    songData.setArtistName("Unknown Artist");
                }
                else{
                    songData.setArtistName(metaRetriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
                }

                if( metaRetriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) == null){
                    songData.setAlbum("Unknown Album");
                }
                else{
                    songData.setAlbum(metaRetriever
                            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
                }

                String title = metaRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                if( title == null || title == ""){
                    songData.setTrackName(songTitle);
                }
                else{
                    songData.setTrackName(title);
                }
                mplayList.add(songData);
            }
            catch (Exception e){
                songData.setArtistName("Unknown Name");
                songData.setAlbum("Unknown Album");
                songData.setTrackName("Unknown Title");
            }
        }
        musicPlayList = mplayList;
    }

    //Create byte array for default song art in playlist
    public void defaultImage() throws IOException {

        Bitmap bitmap=BitmapFactory.decodeResource(getResources(), R.drawable.song_art);
        ByteArrayOutputStream stream=new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 90, stream);
        byte[] image=stream.toByteArray();
        defaultbyte = image;
    }

    //Sort shared playlist based on vote
    public static void sortPlaylist(ArrayList<VotedSong> songsList){
        Collections.sort(songsList, new Comparator<VotedSong>() {
            @Override
            public int compare(VotedSong o1, VotedSong o2) {
                Integer vote1 = o1.getVote();
                Integer vote2 = o2.getVote();
                return vote2.compareTo(vote1);
            }
        });
        /*for (int i = 0; i <songsList.size() ; i++) {
            System.out.println("Song:"+ songsList.get(i).getSongName() + songsList.get(i).getVote());
            System.out.println("Shared_Song:"+ sharedPlayList.get(i).getSongName() + sharedPlayList.get(i).getVote());
        }*/
    }

}
