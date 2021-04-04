package com.example.workmanager;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import org.w3c.dom.Text;

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
