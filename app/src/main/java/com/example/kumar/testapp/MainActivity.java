package com.example.kumar.testapp;


import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.View;

import com.example.kumar.testapp.clientmode.ClientActivity;
import com.example.kumar.testapp.hostmode.HostActivity;


public class MainActivity extends AppCompatActivity  {

    // public key for other activities to access to figure out the mode
    public final static String MODE = "MODE";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu)
    {
        // Inflate the menu; this adds items to the action bar if it is present.
        // getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    public void onBtnHost(View view)
    {
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra(MODE, "host");
        startActivity(intent);
    }

    public void onBtnClient(View view)
    {
        Intent intent = new Intent(this, ProfileActivity.class);
        intent.putExtra(MODE, "client");
        startActivity(intent);
    }

}