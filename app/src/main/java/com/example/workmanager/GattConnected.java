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
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.UUID;

public class GattConnected extends AppCompatActivity {
    bleService mBle;
    boolean mBoundBleService = false;
    netService mNet;
    boolean mBoundNetService = false;
    private Handler bleServiceHandler = new Handler();
    private final static String TAG = "GattConnected";
    private final static int MAX_SSID_PW_LEN = 100;

    static class locationClass{
        private double Lat;
        private double Long;
        private Boolean status;
        locationClass (Boolean status){
            this.status = status;
        }
        locationClass(double Lat, double Long){
            this.status = true;
            this.Lat = Lat;
            this.Long = Long;
        }
    }

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (bleService.ACTION_GATT_DISCONNECTED.equals(action)) {
                Log.i(TAG, "onReceive: Disconnected from GATT server!");
                Runnable run = new Runnable() {
                    @Override
                    public void run() {
                        Intent ActivityIntent = new Intent(getApplicationContext(), MainPage.class);
                        startActivity(ActivityIntent);
                    }
                };
                // In the event of a BLE error, two things will happen,
                // If we are in the middle of provisioning the device, we
                // want to give the user feedback, however, a broadacst will
                // also come from the ble service that will take us back to the
                // main activity. We will delay going back to the main
                // activity so we can display that we failed to provision the device
                final Handler handler = new Handler(Looper.getMainLooper());
                handler.postDelayed(run, 1000);
            }
            if (bleService.ACTION_GATT_WRITE_GOOD.equals(action)){
                Log.i(TAG, "onReceive: Write good!!");

                Runnable stopProgressBar = new Runnable() {
                    @Override
                    public void run() {
                        ProgressBar progressBarProvision = (ProgressBar)findViewById(R.id.progressBarProvision);
                        progressBarProvision.setVisibility(View.INVISIBLE);

                        TextView provisionStatus = (TextView)findViewById(R.id.provisionStatus);
                        provisionStatus.setText("Device provisioned!");
                    }
                };
                runOnUiThread(stopProgressBar);
            }

            if (bleService.ACTION_GATT_WRITE_BAD.equals(action)){
                Log.i(TAG, "onReceive: Write bad!!");

                Runnable stopProgressBar = new Runnable() {
                    @Override
                    public void run() {
                        ProgressBar progressBarProvision = (ProgressBar)findViewById(R.id.progressBarProvision);
                        progressBarProvision.setVisibility(View.INVISIBLE);

                        TextView provisionStatus = (TextView)findViewById(R.id.provisionStatus);
                        provisionStatus.setText("Failed to provision: BLE issues!");
                    }
                };
                runOnUiThread(stopProgressBar);
            }

            if (netService.ACTION_BACK_END_FAILED.equals(action)){
                Log.i(TAG, "onReceive: Network issues!!!");

                Runnable stopProgressBar = new Runnable() {
                    @Override
                    public void run() {
                        ProgressBar progressBarProvision = (ProgressBar)findViewById(R.id.progressBarProvision);
                        progressBarProvision.setVisibility(View.INVISIBLE);

                        TextView provisionStatus = (TextView)findViewById(R.id.provisionStatus);
                        provisionStatus.setText("Failed to provision: Network issues!!");
                    }
                };
                runOnUiThread(stopProgressBar);
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
        intentFilter.addAction(bleService.ACTION_GATT_WRITE_GOOD);
        intentFilter.addAction(bleService.ACTION_GATT_WRITE_BAD);
        intentFilter.addAction(netService.ACTION_BACK_END_FAILED);
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
        bleServiceHandler.postDelayed(poll, 1000);

        ProgressBar progressBarProvision =(ProgressBar)findViewById(R.id.progressBarProvision);
        progressBarProvision.setVisibility(View.INVISIBLE);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_gatt_connected);

        Intent intent = getIntent();
        if (intent != null) {
            String serial = intent.getStringExtra(MainPage.INTENT_SERIAL_MESSAGE);
            TextView serialDisplay = (TextView) findViewById(R.id.serialTextView);
            if (serialDisplay != null) {
                serialDisplay.setText("Connected to: " + serial);
            }
        }
        // Create the observer which updates the UI.
        MutableLiveData<Boolean> mLiveDataProvisioned = globalsApplication.getLiveDataSingletonProvisionedStatus();
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
        MutableLiveData<Boolean> mLiveDataWifi = globalsApplication.getLiveDataSingletonWifiStatus();
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

    public locationClass getLocation(View v) {
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
            return new locationClass(false);
        }
        Location location = locationManager.getLastKnownLocation(bestProvider);
        Double lat = 0.0, lon = 0.0;
        try {
            lat = location.getLatitude();
            lon = location.getLongitude();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "getLocation: lat = " + lat.toString() + " long = " + lon.toString());
        return new locationClass(lat, lon);
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
            mBle.disconnect();
            mBoundBleService = false;
        }
        if (mBoundNetService) {
            unbindService(netConnection);
            mBoundBleService = false;
        }
    }

    public void provisionDevice(View v) {
        Log.i(TAG, "Provisioning device!");

        TextView provisionStatus = (TextView)findViewById(R.id.provisionStatus);
        provisionStatus.setText("");

        String wifiSsid = ((EditText)findViewById(R.id.wifiSsid)).getText().toString();
        String wifiPassword = ((EditText)findViewById(R.id.wifiPassword)).getText().toString();
        String userEmail = ((EditText)findViewById(R.id.userEmail)).getText().toString();

        locationClass location = getLocation(v);
        if (!location.status){
            provisionStatus.setText("Failed to get location!");
        }

        if (wifiSsid.equals("")) {
            Log.i(TAG, "provisionDevice: Invalid Wifi Name!");
            Toast.makeText(getApplicationContext(),"Invalid Wifi Name!",Toast.LENGTH_SHORT).show();
            return;
        }
        if (wifiPassword.equals("")) {
            Log.i(TAG, "provisionDevice: Invalid WiFi Password!");
            Toast.makeText(getApplicationContext(),"Invalid WiFi Password!",Toast.LENGTH_SHORT).show();
            return;
        }
        if (userEmail.equals("")) {
            Log.i(TAG, "provisionDevice: Invalid Email!");
            Toast.makeText(getApplicationContext(),"Invalid Email!",Toast.LENGTH_SHORT).show();
            return;
        }

        if (wifiSsid.length() > MAX_SSID_PW_LEN) {
            Log.i(TAG, "provisionDevice: WiFi name too long!");
            Toast.makeText(getApplicationContext(),"WiFi name too long!",Toast.LENGTH_SHORT).show();
            return;
        }
        if (wifiPassword.length() > MAX_SSID_PW_LEN) {
            Log.i(TAG, "provisionDevice: WiFi Password too long!");
            Toast.makeText(getApplicationContext(),"WiFi Password too long!",Toast.LENGTH_SHORT).show();
            return;
        }

        String randomToken = UUID.randomUUID().toString();
        Log.i(TAG, "provisionDevice: id" + randomToken);
        mNet.provisionDevice(wifiSsid, wifiPassword, userEmail,String.valueOf(location.Long),
                String.valueOf(location.Lat), randomToken);

        ProgressBar progressBarProvision =(ProgressBar)findViewById(R.id.progressBarProvision);
        progressBarProvision.setVisibility(View.VISIBLE);
    }

}
