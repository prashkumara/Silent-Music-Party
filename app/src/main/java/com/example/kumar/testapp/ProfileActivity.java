package com.example.kumar.testapp;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import com.example.kumar.testapp.clientmode.ClientActivity;
import com.example.kumar.testapp.clientmode.ClientSocketHandler;
import com.example.kumar.testapp.hostmode.HostActivity;

import static android.icu.lang.UProperty.NAME;

public class ProfileActivity extends AppCompatActivity{

    //For Profile Info
    public static final String TAG = "Profile Activity";
    EditText eName,eAge,eGender;
    Profile profileObj=new Profile();
    private String mode;
    private String name;
    private String age;
    private String gender;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);
        //Get Mode from main activity
        mode = getIntent().getStringExtra("MODE");

        //initialize all view objects for Profile
        eName = (EditText) findViewById(R.id.editName);
        eAge = (EditText) findViewById(R.id.editAge);
        eGender = (EditText) findViewById(R.id.editGender);

    }

    //Send profile data to appropriate Host or Client
    public void createProfile(View v)
    {
        //Get data from view
        profileObj.setName(eName.getText().toString());
        profileObj.setAge(eAge.getText().toString());
        profileObj.setGender(eGender.getText().toString());

        if (profileObj.getName().equals("") || profileObj.getName().equals(null)
                || profileObj.getName().equals("Name")){
            if (mode.equals("host")){
                name = "Host";
            }
            else {
                name = "UnknownGuest";
            }
        }
        else {
            name = profileObj.getName();
        }

        if(profileObj.getAge().equals("") || profileObj.getAge().equals(null)
                || profileObj.getAge().equals("Age")){
            age = "unknown";
        }
        else {
            age = profileObj.getAge();
        }

        if(profileObj.getGender().equals("")|| profileObj.getGender().equals(null)
                || profileObj.getGender().equals("Gender")){
            gender = "unknown";
        }
        else {
            gender = profileObj.getGender();
        }

        if (mode.equals("host")){
            Intent intent = new Intent(this, HostActivity.class);
            intent.putExtra("NAME", name);
            intent.putExtra("AGE", age);
            intent.putExtra("GENDER", gender);
            startActivity(intent);
        }
        else {
            Intent intent = new Intent(this, ClientActivity.class);
            intent.putExtra("NAME", name);
            intent.putExtra("AGE", age);
            intent.putExtra("GENDER", gender);
            startActivity(intent);
        }

    }

}
