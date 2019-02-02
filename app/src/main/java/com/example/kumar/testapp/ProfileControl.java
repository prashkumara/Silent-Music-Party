package com.example.kumar.testapp;

import android.app.ListActivity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.example.kumar.testapp.clientmode.ClientMusicFragment;
import com.example.kumar.testapp.clientmode.ClientSocketHandler;
import com.example.kumar.testapp.hostmode.HostActivity;

import java.util.ArrayList;
import java.util.Map;

public class ProfileControl extends ListActivity {

    private final String TAG = "Profile list Control";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile_control);
        String mode = getIntent().getStringExtra("MODE");
        //Called by Host
        if(mode.equals("host")){
            MyAdapter adapter = new MyAdapter(HostActivity.profileList);
            //Adding header to Listview
            TextView textView = new TextView(this.getBaseContext());
            textView.setText("Guest list!!!");
            textView.setTextColor(Color.BLACK);
            textView.setTypeface(null, Typeface.BOLD);
            textView.setClickable(false);
            textView.setBackgroundColor(Color.GREEN);
            textView.setGravity(Gravity.CENTER);
            this.getListView().addHeaderView(textView, "Header", false);

            //Get List data from adapter
            this.setListAdapter(adapter);

        }
        //Called by Client
        else {
            MyAdapter adapter = new MyAdapter(ClientSocketHandler.clientProfileList);
            //Adding header to Listview
            TextView textView = new TextView(this.getBaseContext());
            textView.setText("Guest list!!!");
            textView.setTextColor(Color.BLACK);
            textView.setTypeface(null, Typeface.BOLD);
            textView.setClickable(false);
            textView.setBackgroundColor(Color.GREEN);
            textView.setGravity(Gravity.CENTER);
            this.getListView().addHeaderView(textView, "Header", false);

            //Get List data from adapter
            this.setListAdapter(adapter);

        }
    }

    private class MyAdapter extends BaseAdapter {
        private final ArrayList mData;

        public MyAdapter(ArrayList<Profile> profiles) {
            mData = new ArrayList();
            mData.addAll(profiles);
        }

        @Override
        public int getCount() {
            return mData.size();
        }

        @Override
        public Profile getItem(int position) {
            return (Profile) mData.get(position);
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
                        .inflate(R.layout.profile_item, parent, false);
                Profile item = getItem(position);

                ((TextView) result.findViewById(R.id.profileName)).setText(item.getName());
                ((TextView) result.findViewById(R.id.profileAge)).setText(item.getAge());
                ((TextView) result.findViewById(R.id.profileGender)).setText(item.getGender());
            } else {
                result = convertView;
            }
            return result;
        }
    }
}
