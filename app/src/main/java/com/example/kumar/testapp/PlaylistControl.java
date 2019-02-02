package com.example.kumar.testapp;

import android.app.ListActivity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SearchView;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.example.kumar.testapp.clientmode.ClientMusicFragment;
import com.example.kumar.testapp.clientmode.ClientSocketHandler;
import com.example.kumar.testapp.hostmode.HostMusicFragment;
import com.example.kumar.testapp.hostmode.HostSocketHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import static com.example.kumar.testapp.hostmode.HostMusicFragment.sharedPlayList;

public class PlaylistControl extends ListActivity {

    private final String TAG = "Play list Control";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_control);

        String mode = getIntent().getStringExtra("MODE");
        //Called by Host
        if(mode.equals("host")){
            MyAdapter adapter = new MyAdapter(HostMusicFragment.musicPlayList);
            //Adding header to Listview
            TextView textView = new TextView(this.getBaseContext());
            textView.setText("Tap on Song to vote!!!");
            textView.setTextColor(Color.BLACK);
            textView.setTypeface(null, Typeface.BOLD);
            textView.setClickable(false);
            textView.setBackgroundColor(Color.YELLOW);
            textView.setGravity(Gravity.CENTER);
            this.getListView().addHeaderView(textView, "Header", false);

            //Get List data from adapter
            this.setListAdapter(adapter);

            this.getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    //Get clicked song name
                    Object songdata = parent.getAdapter().getItem(position);
                    String songName = ((Playlist) songdata).getTrackName();
                    Log.i(TAG, "Voted Song : " + songName);

                    //Update the shared playlist with vote count
                    for (int i = 0; i < HostMusicFragment.sharedPlayList.size() ; i++) {
                        //Find the song and update vote count
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
            });
        }
        //Called by Client
        else {
            MyAdapter adapter = new MyAdapter(ClientSocketHandler.clientPlayList);
            //Adding header to Listview
            TextView textView = new TextView(this.getBaseContext());
            textView.setText("Tap on Song to vote!!!");
            textView.setTextColor(Color.BLACK);
            textView.setTypeface(null, Typeface.BOLD);
            textView.setClickable(false);
            textView.setBackgroundColor(Color.YELLOW);
            textView.setGravity(Gravity.CENTER);
            this.getListView().addHeaderView(textView, "Header", false);

            //Get List data from adapter
            this.setListAdapter(adapter);
            this.getListView().setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    //Get clicked song name
                    Object songdata = parent.getAdapter().getItem(position);
                    String songName = ((Playlist) songdata).getTrackName();
                    Log.i(TAG, "Voted Song : " + songName);
                    //Cast and send Vote
                    ClientMusicFragment.sendVote(songName);
                }
            });
        }

    }

    private class MyAdapter extends BaseAdapter {
        private final ArrayList mData;
        //RecyclerView.ViewHolder viewHolder;

        public MyAdapter(ArrayList<Playlist> pList) {
            mData = new ArrayList();
            mData.addAll(pList);
        }

        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        public Playlist getItem(int position) {
            return (Playlist) mData.get(position);
        }

        @Override
        public long getItemId(int position) {
            // TODO implement you own logic with ID
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View result;

            if (convertView == null) {
                result = LayoutInflater.from(parent.getContext())
                            .inflate(R.layout.playlist_item, parent, false);
                Playlist item = getItem(position);
                byte[] songArt = item.getArt();
                if(songArt.length != 0 ){
                    Bitmap songImage = BitmapFactory
                            .decodeByteArray(songArt, 0, songArt.length);
                    ((ImageView) result.findViewById(R.id.songImage)).setImageBitmap(songImage);
                }

                // TODO replace findViewById by ViewHolder
                ((TextView) result.findViewById(R.id.songTitle)).setText(item.getTrackName());
                ((TextView) result.findViewById(R.id.albumName)).setText(item.getAlbum());
                ((TextView) result.findViewById(R.id.artistName)).setText(item.getArtistName());
            } else {
                result = convertView;
            }
            return result;
        }
    }
}
