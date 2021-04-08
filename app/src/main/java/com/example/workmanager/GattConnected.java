package com.example.workmanager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.TextView;

public class GattConnected extends AppCompatActivity {
    private final static String TAG = "GattConnected";
    MyService mService;
    boolean mBound = false;

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (MyService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.i(TAG, "onReceive: Disconnected from GATT server!");

                Intent ActivityIntent = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(ActivityIntent);
            }
        }
    };

    @Override
    public void onStart(){
        super.onStart();

        // start/connect to the BLE service
        Intent intent = new Intent(this, MyService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gatt_connected);

        Intent intent = getIntent();
        if ( intent != null ) {
            String serial = intent.getStringExtra(MainActivity.INTENT_SERIAL_MESSAGE);
            TextView serialDisplay = (TextView) findViewById(R.id.serialTextView);
            if (serialDisplay != null){
                serialDisplay.setText("Connected to: " + serial);
            }
        }
        // Create the observer which updates the UI.
        MutableLiveData<Boolean> mLiveDataProvisioned = bleLiveData.getLiveDataSingletonProvisionedStatus();
        final Observer<Boolean> provisionedObserver = new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable final Boolean provisioned) {
                Log.i(TAG, "onChanged: new check box status = " + provisioned.toString());
                // Update the UI, in this case, a TextView.
                CheckBox provisionedDisplay = (CheckBox) findViewById(R.id.checkBoxProv);
                provisionedDisplay.setChecked(provisioned);
            }
        };
        mLiveDataProvisioned.observe(this, provisionedObserver);

        // Create the observer which updates the UI.
        MutableLiveData<Boolean> mLiveDataWifi = bleLiveData.getLiveDataSingletonWifiStatus();
        final Observer<Boolean> wifiObserver = new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable final Boolean provisioned) {
                Log.i(TAG, "onChanged: new check box status = " + provisioned.toString());
                // Update the UI, in this case, a TextView.
                CheckBox wifiDisplay = (CheckBox) findViewById(R.id.wifiCheckbox);
                wifiDisplay.setChecked(provisioned);
            }
        };
        mLiveDataWifi.observe(this, wifiObserver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if ( mBound ) {
            unbindService(connection);
            mBound = false;
        }
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MyService.ACTION_GATT_DISCONNECTED);
        return intentFilter;
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to BLE service, cast the IBinder and get BLE service instance
            MyService.LocalBinder binder = (MyService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };
}
