package com.example.workmanager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import java.io.IOException;
import java.text.Normalizer;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

public class GattConnected extends AppCompatActivity {
    bleService mBle;
    boolean mBoundBleService = false;
    netService mNet;
    boolean mBoundNetService = false;
    private Handler bleServiceHandler = new Handler();
    private final static String TAG = "GattConnected";
    private final static String SERVICE_NAME_SHORT_BLE = ".bleService";
    private final static String SERVICE_NAME_SHORT_NET = ".netService";

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (bleService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.i(TAG, "onReceive: Disconnected from GATT server!");

                Intent ActivityIntent = new Intent(getApplicationContext(), MainActivity.class);
                startActivity(ActivityIntent);
            }
        }
    };

    private ServiceConnection bleConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {

            Log.i(TAG, "onServiceConnected: Connected to BLE service!");
            // We've bound to BLE service, cast the IBinder and get BLE service instance
            bleService.LocalBinder binder = (bleService.LocalBinder) service;
            mBle = binder.getService();
            mBoundBleService = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBoundBleService = false;
        }
    };

    private ServiceConnection netConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.i(TAG, "onServiceConnected: Connected to NET service!");
            netService.LocalBinder binder = (netService.LocalBinder) service;
            mNet = binder.getService();
            mBoundNetService = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBoundNetService = false;
        }
    };

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(bleService.ACTION_GATT_DISCONNECTED);
        return intentFilter;
    }

    @Override
    public void onStart() {
        super.onStart();

        // start/connect to the BLE service
        Intent intent = new Intent(this, bleService.class);
        startService(intent);
        bindService(intent, bleConnection, Context.BIND_AUTO_CREATE);

        // start/connect to the net service
        intent = new Intent(this, netService.class);
        startService(intent);
        bindService(intent, netConnection, Context.BIND_AUTO_CREATE);

        Runnable poll = new Runnable() {
            @Override
            public void run() {
                if (mBoundBleService) {
                    Log.i(TAG, "run: polling device characteristics.. ");
                    mBle.pollDeviceStats();
                    bleServiceHandler.postDelayed(this, 1000);
                } else {
                    Log.i(TAG, "run: Won't poll - not bound");
                }
            }
        };
        // uncomment to opll!
        // bleServiceHandler.postDelayed(poll, 1000);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gatt_connected);

        Intent intent = getIntent();
        if (intent != null) {
            String serial = intent.getStringExtra(MainActivity.INTENT_SERIAL_MESSAGE);
            TextView serialDisplay = (TextView) findViewById(R.id.serialTextView);
            if (serialDisplay != null) {
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

    public void getLocation(View v) {
        // Get the location manager
        LocationManager locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        String bestProvider = locationManager.getBestProvider(criteria, false);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Location location = locationManager.getLastKnownLocation(bestProvider);
        Double lat = 0.0, lon = 0.0;
        try {
            lat = location.getLatitude();
            lon = location.getLongitude();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }

        Log.i(TAG, "getLocation: lat = " + lat.toString() + "long = " + lon.toString());
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
        if (mBoundBleService) {
            unbindService(bleConnection);
            mBoundBleService = false;
        }
        if (mBoundNetService) {
            unbindService(netConnection);
            mBoundBleService = false;
        }
    }

    public void sendCommand(View v) {
        Log.i(TAG, "sen: sending!");
        mNet.provisionDevice();
    }
}