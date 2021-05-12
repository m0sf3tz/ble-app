package com.example.terafire;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
import android.location.LocationManager;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.tasks.OnSuccessListener;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class MyWorker extends Worker {
    public static final String TAG = "myWorker";
    private static final String IS_THERE_FIRE_KEY = "Fire_present";
    private NotificationManagerCompat notificationManager;
    private Context mContext;
    public MyWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
        mContext = appContext;
    }

    @NonNull
    @Override
    public Result doWork() {
        System.out.println("Starting to poll for fire!!");
        notificationManager = NotificationManagerCompat.from(mContext);
        LocationManager locationManager = (LocationManager)getApplicationContext().getSystemService(Context.LOCATION_SERVICE);


        FusedLocationProviderClient fusedLocationProviderClient = new FusedLocationProviderClient(getApplicationContext());
        fusedLocationProviderClient.getLastLocation()
                .addOnSuccessListener(new OnSuccessListener<Location>() {
                    @Override
                    public void onSuccess(Location location) {
                        if (location != null) {
                        }
                    }
                });


        String api_key = getApi();
        if (api_key == MainPage.API_NULL_VALUE){
            return Result.failure();
        }

        final OkHttpClient getClient = new OkHttpClient().newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();

        HttpUrl httpUrl = new HttpUrl.Builder()
                .scheme("http")
                .host(netService.URL_IP)
                .port(netService.URL_POR)
                .addPathSegment("api")
                .addPathSegment(netService.GET_META_DATA_URL_SEGMENT)
                .addQueryParameter("api_key", api_key)
                .build();
        Request request = new Request.Builder()
                .url(httpUrl)
                .build();
        final Call getCall = getClient.newCall(request);

        Response responseGet;
        try {
            responseGet = getCall.execute();
            Log.i(TAG, "run: PUT code = " + responseGet.code());
        } catch (IOException e) {
            System.out.println("Exception whilst posting!!" + e.toString());
            return Result.failure();
        }

        String thermel_meta_string = null;
        if (responseGet != null) {
            try {
                thermel_meta_string = responseGet.body().string();
                responseGet.close();
            } catch (IOException e) {
                Log.i(TAG, "doWork: Failed to get body");
                return Result.failure();
            }
        }

        if (responseGet.code() == netService.POST_STATUS_OK) {
            Log.i(TAG, "New thermal Meta Data!");
            JSONObject jsonResponse = netService.unMarshalMeta(thermel_meta_string);
            if (jsonResponse == null) {
                Log.i(TAG, "doWork: Failed to unmarshal");
                return Result.failure();
            }

            boolean isThereFire = false;
            try {
                isThereFire = (boolean)jsonResponse.get(IS_THERE_FIRE_KEY);
            } catch (JSONException e) {
                Log.i(TAG, "doWork: Failed to unmarshal");
                return Result.failure();
            }
            
            if (isThereFire) {
                Log.i(TAG, "doWork: Fire! oh Shit!");
                Intent notificationIntent = new Intent("android.intent.category.LAUNCHER");
                notificationIntent.setClassName("com.example.test",
                        "com.example.test.VideoActivity");
                notificationIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                PendingIntent contentIntent = PendingIntent.getActivity(getApplicationContext(), 0, notificationIntent, 0);

                Intent intent = new Intent(getApplicationContext(), MainPage.class);
                PendingIntent pIntent = PendingIntent.getActivity(getApplicationContext(), 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);

                Notification notification = new NotificationCompat.Builder(getApplicationContext(), globalsApplication.CHANNEL_FIRE_ALARM_NOTIFICATION)
                        .setSmallIcon(R.drawable.ic_launcher_background)
                        .setContentTitle("--WARNING--")
                        .setContentText("Fire Detected!")
                        .setContentIntent(pIntent)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setFullScreenIntent(contentIntent, true)
                        .build();

                notification.flags = NotificationCompat.FLAG_INSISTENT;
                notificationManager.notify(1, notification);
            }
        }
        return Result.success();


    }

    private String getApi(){
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        return preferences.getString(bleService.API_KEY_STRING_KEY, MainPage.API_NULL_VALUE);
    }
}
