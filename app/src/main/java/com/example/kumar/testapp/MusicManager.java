package com.example.kumar.testapp;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.util.Log;

import java.io.File;
import java.io.*;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by Kumar on 1/26/2017.
 */

public class MusicManager {
    private static final int REQUESTCODE_STORAGE_PERMISSION = 0 ;
    // Music storage Path
    //final String MEDIA_PATH = new String("/storage/emulated/0/Music");
    //final String MEDIA_PATH = new String("/storage/emulated/0/Android/data/com.example.kumar.testapp/files");
    private ArrayList<HashMap<String, String>> musicList = new ArrayList<HashMap<String, String>>();
    public static String TAG = "Music Manager";
    private Context context = null;
    public static String MEDIA_PATH =null;
    /**
     * Function to read all mp3 files from internal storage default Music folder
     * and store the details in ArrayList
     * */
    public ArrayList<HashMap<String, String>> getPlayList(Context context){

        this.context = context;

        //File location = (context.getExternalFilesDir(null));
        //File location = (context.getFilesDir());
        //Log.i(TAG,location.getPath() );
        File location1 = Environment.getExternalStorageDirectory();
        MEDIA_PATH = location1.getPath() + "/Music";
        File location = new File(MEDIA_PATH);
        Log.i(TAG,MEDIA_PATH );

        try{

            musicList = searchMusic(location);

        }
        catch (Exception e){
            Log.e(TAG,"Error retrieving playlist" );
        }

        // return the music list array
        return musicList;
    }

    public ArrayList<HashMap<String, String>> searchMusic(File location1)
    {

        ArrayList<HashMap<String, String>> tempList = new ArrayList<HashMap<String, String>>();
        for (File file : location1.listFiles()) {
            if(file.isDirectory()) {
                tempList.addAll(searchMusic(file));
            }
            else{
                if(file.getName().endsWith(".mp3")) {
                    HashMap<String, String> song = new HashMap<String, String>();
                    song.put("musicTitle", file.getName().substring(0, (file.getName().length() - 4)));
                    song.put("musicPath", file.getPath());

                    // Adding each song to MusicList
                    tempList.add(song);
                }
            }
        }
        return tempList;
    }

    public static boolean storagePermitted(Activity activity) {

        if (ActivityCompat.checkSelfPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&

                ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)

            return true;

        ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUESTCODE_STORAGE_PERMISSION);

        return false;

    }


    /**
     * Class to filter files having .mp3 extension
     * */
    class FilterFileExtension implements FilenameFilter {
        public boolean accept(File dir, String name) {
            return (name.endsWith(".mp3") || name.endsWith(".MP3"));
        }
    }
}
