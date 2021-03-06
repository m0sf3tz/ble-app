package com.example.terafire;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.util.ArrayList;

public class MainPage extends AppCompatActivity {
    private static String TAG = "MainActivity";
    public final static String INTENT_SERIAL_MESSAGE = "INTENT_SERIAL_KEY";
    bleService mBleService;
    boolean mBoundBleService = false;
    netService mNet;
    boolean mBoundNetService = false;
    ArrayAdapter<String> itemsAdapter;
    private String DeviceSerial = "NULL";
    public static String API_NULL_VALUE = "NULL_VALUE";
    private NotificationManagerCompat notificationManager;
    final static int default_age = -1;

    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (bleService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.i(TAG, "onReceive: Connected to GATT server!");

                Intent ActivityIntent = new Intent(getApplicationContext(), GattConnected.class);
                ActivityIntent.putExtra(INTENT_SERIAL_MESSAGE, DeviceSerial);
                startActivity(ActivityIntent);
            } else if (bleService.ACTION_GATT_DISCOVERED.equals(action)) {
                Log.i(TAG, "onReceive: Finished scanning for BLE devices!!");
            } else if (netService.NEW_THERMAL_IMAGE_NETWORK.equals(action)) {
                Log.i(TAG, "New image!");
                // must get a reference to the service
                IBinder binder = peekService(context, new Intent(context, netService.class));
                if (binder == null)
                    return;
                netService net = ((netService.LocalBinder) binder).getService();
                final Bitmap thermalImage = net.imageHandler(true, null);
                final int age = intent.getIntExtra(netService.IMAGE_AGE_KEY, default_age);
                if (age == default_age){
                    Log.i(TAG, "onReceive: Something wrong with the age...");
                }
                Runnable run = new Runnable() {
                    @Override
                    public void run() {
                        ImageView image =(ImageView)findViewById(R.id.thermalImage);
                        image.setImageBitmap(thermalImage);

                        TextView debugText = (TextView) findViewById(R.id.fireStatus);
                        if (debugText != null) {
                            String ageString = "Image age (in seconds) = " + age;
                            debugText.setText(ageString);
                        }
                    }
                };
                runOnUiThread(run);
            }  else if (netService.NO_THERMAL_META.equals(action)) {
                Log.i(TAG, "NO meta data found!!");
                Runnable run = new Runnable() {
                    @Override
                    public void run() {
                        TextView debugText = (TextView) findViewById(R.id.fireStatus);
                        if (debugText != null) {
                            debugText.setText("Can't find any thermal information on the cloud!...");
                        }
                    }
                };
                runOnUiThread(run);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.i(TAG, "onCreate: Created!");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart: Started!");
        super.onStart();

        // start/connect to the BLE service
        Intent intent = new Intent(this, bleService.class);
        startService(intent);
        bindService(intent, bleConnection, Context.BIND_AUTO_CREATE);

        // start/connect to the net service
        intent = new Intent(this, netService.class);
        startService(intent);
        bindService(intent, netConnection, Context.BIND_AUTO_CREATE);

        // make sure we have proper permissions
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.i("tag", "NO GPS!!");
        }

        // Check if we have proper permissions, if not, request them
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {

            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.ACCESS_FINE_LOCATION)) {
            } else {
                // No explanation needed, we can request the permission.
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                        99);
            }
        }

        // Set up the live data observer for the list of devices found.
        final ArrayList<String> items = new ArrayList<>();
        itemsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items);
        final ListView listView = (ListView) findViewById(R.id.bleItems);
        listView.setAdapter(itemsAdapter);
        final MutableLiveData<ArrayList<String>> myMutable = globalsApplication.getLiveDataSingletonDeviceArr();
        // Create the observer which updates the UI.
        final Observer<ArrayList<String>> nameObserver = new Observer<ArrayList<String>>() {
            @Override
            public void onChanged(@Nullable final ArrayList<String> newItems) {
                Log.i(TAG, "onChanged: Called, sizeof newItems = " + newItems.size());
                itemsAdapter.clear();
                for (String device : newItems) {
                    itemsAdapter.add(device);
                }
                ProgressBar progressBarScan =(ProgressBar)findViewById(R.id.progressBarScan);
                progressBarScan.setVisibility(View.INVISIBLE);
            }
        };

        myMutable.observe(this, nameObserver);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                String DeviceSerialTemp = itemsAdapter.getItem(position);
                if (DeviceSerialTemp != null) {
                    DeviceSerial = DeviceSerialTemp;
                    Log.i(TAG, "onItemClick: setting DeviceSerial to " + DeviceSerial);
                } else {
                    Log.i(TAG, "onItemClick: Failed to find item");
                }
                Log.i(TAG, "onItemClick: ");
                mBleService.BleConnect(position);
            }
        });
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

    public void startScan(View v) {
        Log.i(TAG, "startScan: Starting Scan!");
        if (mBleService != null) {
            mBleService.BleScan();
            ProgressBar progressBarScan =(ProgressBar)findViewById(R.id.progressBarScan);
            progressBarScan.setVisibility(View.VISIBLE);
        }
    }

    public void getFireStatus(View v) {
        if(mBoundNetService) {
            String api_key = getApiKey();
            if (api_key == API_NULL_VALUE){
                TextView debugText = (TextView) findViewById(R.id.fireStatus);
                if (debugText != null) {
                    debugText.setText("No API key, please provision!");
                }
            } else {
                mNet.getThermalMeta(api_key);
            }
        } else {
            TextView debugText = (TextView) findViewById(R.id.fireStatus);
            if (debugText != null) {
                debugText.setText("Failed to connect to net service - BUG");
            }
        }
    }

    private ServiceConnection bleConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            Log.i(TAG, "onServiceConnected: " + className.getShortClassName());
            // We've bound to BLE service, cast the IBinder and get BLE service instance
            bleService.LocalBinder binder = (bleService.LocalBinder) service;
            mBleService = binder.getService();
            mBoundBleService = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) { mBoundBleService = false; }
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

    public String getApiKey(){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        return preferences.getString(bleService.API_KEY_STRING_KEY, API_NULL_VALUE);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(bleService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(netService.NEW_THERMAL_IMAGE_NETWORK);
        intentFilter.addAction(netService.NO_THERMAL_META);
        return intentFilter;
    }
}
