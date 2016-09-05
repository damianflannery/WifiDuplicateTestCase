package com.wifitest;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.ToggleButton;

public class MainActivity extends AppCompatActivity {

    boolean mIsRunning;

    ToggleButton mToggleBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mToggleBtn = (ToggleButton) findViewById(R.id.wifi_toggle_btn);

        Log.d("damian", "onCreate: " + (savedInstanceState != null));
        if (savedInstanceState != null) {
            mIsRunning = savedInstanceState.getBoolean("running");
            mToggleBtn.setChecked(mIsRunning);
        }

        mToggleBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    mIsRunning = true;
                    startWifiService(MainActivity.this);
                } else {
                    mIsRunning = false;
                    stopWifiService(MainActivity.this);
                }
            }
        });
    }

    public static void startWifiService(Context context) {
        Intent startIntent = new Intent(context, MyWifiService.class);
        startIntent.setAction(MyWifiService.Action.START_LOGGING.toString());
        context.startService(startIntent);
    }

    public static void stopWifiService(Context context) {
        Intent startIntent = new Intent(context, MyWifiService.class);
        startIntent.setAction(MyWifiService.Action.STOP_LOGGING.toString());
        context.startService(startIntent);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("running", mIsRunning);
        super.onSaveInstanceState(outState);
    }
}
