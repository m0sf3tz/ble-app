package com.example.workmanager;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.CheckBox;
import android.widget.TextView;

public class GattConnected extends AppCompatActivity {
    private final static String TAG = "GattConnected";

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

        MutableLiveData<Boolean> mLiveData = bleLiveData.getLiveDataSingletonProvisionedStatus();

        // Create the observer which updates the UI.
        final Observer<Boolean> provisionedObserver = new Observer<Boolean>() {
            @Override
            public void onChanged(@Nullable final Boolean provisioned) {
                Log.i(TAG, "onChanged: new check box status = " + provisioned.toString());
                // Update the UI, in this case, a TextView.
                CheckBox provisionedDisplay = (CheckBox) findViewById(R.id.checkBoxProv);
                provisionedDisplay.setChecked(provisioned);
            }
        };

        mLiveData.observe(this, provisionedObserver);
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

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(MyService.ACTION_GATT_DISCONNECTED);
        return intentFilter;
    }
}
