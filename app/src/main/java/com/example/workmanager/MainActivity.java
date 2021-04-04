package com.example.workmanager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import android.Manifest;
import android.app.Activity;
import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;

class myFactory implements ViewModelProvider.Factory {
    private Application mApplication;

    myFactory (Application application) {
        this.mApplication = application;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        return (T) new MyViewModel(mApplication);
    }
}

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";
    public final static String INTENT_SERIAL_MESSAGE = "INTENT_SERIAL_KEY";
    private MyViewModel model;
    MyService mService;
    boolean mBound = false;
    ArrayAdapter<String> itemsAdapter;
    private String DeviceSerial = "NULL";
    
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (MyService.ACTION_GATT_CONNECTED.equals(action)) {
                Log.i(TAG, "onReceive: Connected to GATT server!");

                Intent ActivityIntent = new Intent(getApplicationContext(), GattConnected.class);
                ActivityIntent.putExtra(INTENT_SERIAL_MESSAGE, DeviceSerial);
                startActivity(ActivityIntent);
            } else if ( MyService.ACTION_GATT_DISCOVERED.equals(action)) {
                Log.i(TAG, "onReceive: Finished scanning for BLE devices!!");
                updateUiWithResults();
            }
        }
    };

    private void updateUiWithResults(){
        if (mService == null){
            Log.i(TAG, "updateUiWithResults: Service Null - returning");
            return;
        }

        final ArrayList<String> items =  mService.getDevices();
        if (items == null){
            Log.i(TAG, "updateUiWithResults: No Items found!");
            return;
        }

        for (String s: items){
            Log.i(TAG, "updateUiWithResults: " + s);
        }

        itemsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items);

        final ListView listView = (ListView) findViewById(R.id.bleItems);
        listView.setAdapter(itemsAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                String DeviceSerialTemp = itemsAdapter.getItem(position);
                if (DeviceSerialTemp != null){
                    DeviceSerial = DeviceSerialTemp;
                    Log.i(TAG, "onItemClick: setting DeviceSerial to " + DeviceSerial);
                } else {
                    Log.i(TAG, "onItemClick: Failed to find item");
                }

                Log.i(TAG, "onItemClick: ");
                mService.BleConnect(position);
            }
        });
    }

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

        // start the BLE service
        Intent intent = new Intent(this, MyService.class);
        startService(intent);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);

        // make sure we have proper permissions
        LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            Log.i("tag", "NO GPS!!");
        }

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
/*
        // set up the array list
        final ArrayList<String> items = new ArrayList<>();
        itemsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items);

        final ListView listView = (ListView) findViewById(R.id.bleItems);
        listView.setAdapter(itemsAdapter);

        final MutableLiveData<ArrayList<discoveredDevice>> myMutable =  ListLiveData.get();

        // Create the observer which updates the UI.
        final Observer<ArrayList<discoveredDevice>> nameObserver = new Observer<ArrayList<discoveredDevice>>() {
            @Override
            public void onChanged(@Nullable final ArrayList<discoveredDevice> newItems) {
                for(discoveredDevice device : newItems)
                {
                    System.out.println("here " + device.getSerial());
                    itemsAdapter.add(device.getSerial());
                }
            }
        };

        myMutable.observe(this, nameObserver);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
                String DeviceSerialTemp = itemsAdapter.getItem(position);
                if (DeviceSerialTemp != null){
                    DeviceSerial = DeviceSerialTemp;
                    Log.i(TAG, "onItemClick: setting DeviceSerial to " + DeviceSerial);
                } else {
                    Log.i(TAG, "onItemClick: Failed to find item");
                }
                
                Log.i(TAG, "onItemClick: ");
                mService.BleConnect(position);
            }
        });
  */
    }

    @Override
    protected void onStop() {
        super.onStop();
        if ( mBound ) {
            unbindService(connection);
            mBound = false;
        }
    }

    public void startScan(View v) {
        Log.i(TAG, "startScan: Starting Scan!" );
        if ( mService != null ) {
            mService.BleScan();
        }
    }

    public void listUuidService(View v) {
        Log.i(TAG, "Setting value!!");

        if ( mService != null ) {
            mService.listServices();
        }
    }

    public void setChar(View v) {
        Log.i(TAG, "Setting value!!");

        if ( mService != null ) {
            mService.setChar();
        }
    }

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            MyService.LocalBinder binder = (MyService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;

            // after a configuration change, get the latest
            updateUiWithResults();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
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
    
    public void read(View v){
        Log.i(TAG, "read: !");
        mService.readChar();
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MyService.ACTION_GATT_CONNECTED);
        //intentFilter.addAction(MyService.ACTION_GATT_DISCOVERED);
        //intentFilter.addAction(MyService.ACTION_GATT_DISCONNECTED);
        return intentFilter;
    }
}
