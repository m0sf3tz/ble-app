package com.example.workmanager;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Application;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.os.Build;
import android.util.Log;

import androidx.annotation.MainThread;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

public class globalsApplication extends Application {
    private static MutableLiveData<Boolean> sInstanceProvisioned;
    private static MutableLiveData<Boolean> sIntanceWifi;
    private static MutableLiveData<ArrayList<String>> sInstanceDeviceArr;
    public static final String CHANNEL_FIRE_ALARM_NOTIFICATION = "CHANNEL_FIRE_ALARM_NOTIFICATION";
    public static final String TAG = "GLOBAL_CLASS";
    private static globalsApplication mGlobals;

    public static globalsApplication getGlobalSingleton(){
        if (mGlobals == null){
            mGlobals = new globalsApplication();
        }
        return mGlobals;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
    }
    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel1 = new NotificationChannel(
                    CHANNEL_FIRE_ALARM_NOTIFICATION,
                    "Fire Alarm Channel",
                    NotificationManager.IMPORTANCE_MAX
            );
            channel1.setDescription("Fire Alarm");
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel1);
        }
    }

    public static MutableLiveData<Boolean> getLiveDataSingletonProvisionedStatus() {
        if (sInstanceProvisioned == null) {
            sInstanceProvisioned = new MutableLiveData<Boolean>();
        }
        return sInstanceProvisioned;
    }

    public static MutableLiveData<Boolean> getLiveDataSingletonWifiStatus() {
        if (sIntanceWifi == null) {
            sIntanceWifi = new MutableLiveData<Boolean>();
        }
        return sIntanceWifi;
    }

    public static MutableLiveData<ArrayList<String>> getLiveDataSingletonDeviceArr() {
        if (sInstanceDeviceArr == null) {
            sInstanceDeviceArr = new MutableLiveData<ArrayList<String>>();

            sInstanceDeviceArr.setValue(new ArrayList<String>());
        }
        return sInstanceDeviceArr;
    }

    public void getLocation(Context context) {
        // Get the location manager
        LocationManager locationManager = (LocationManager) context.getSystemService(LOCATION_SERVICE);
        Criteria criteria = new Criteria();
        String bestProvider = locationManager.getBestProvider(criteria, false);


        @SuppressLint("MissingPermission") Location location = locationManager.getLastKnownLocation(bestProvider);
        Double lat = 0.0, lon = 0.0;
        try {
            lat = location.getLatitude();
            lon = location.getLongitude();
        } catch (NullPointerException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "getLocation: lat = " + lat.toString() + " long = " + lon.toString());
        //return new locationClass(lat, lon);
    }
}

