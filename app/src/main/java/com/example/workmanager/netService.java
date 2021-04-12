package com.example.workmanager;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class netService extends Service {
    final static String TAG = "netService";
    private final IBinder binder = new LocalBinder();
    static final String URL_FULL = "http://192.168.0.189:3000";
    static final String URL_IP = "192.168.0.189";
    static final int URL_POR = 3000;
    static final String REQUEST_BLE_PASS_DATA = "REQUEST_BLE_PASS_DATA";

    public netService() {
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class LocalBinder extends Binder {
        netService getService() {
            // Return this instance of LocalService so clients can call public methods
            return netService.this;
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Toast.makeText(this, "service net service!", Toast.LENGTH_SHORT).show();
        return START_STICKY;
    }

    public void provisionDevice(){
        // POST!
        final OkHttpClient postClient = new OkHttpClient().newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        RequestBody formBody = new FormBody.Builder()
                .add("uname", "abcd123")
                .add("psw", "abcd123")
                .build();
        Request postRequest = new Request.Builder()
                .url(URL_FULL)
                .post(formBody)
                .build();
        final Call postCall = postClient.newCall(postRequest);

        // GET!
        final OkHttpClient getClient = new OkHttpClient().newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        HttpUrl httpUrl = new HttpUrl.Builder()
                .scheme("http")
                .host(URL_IP)
                .port(URL_POR)
                .addQueryParameter("uname", "34")
                .addQueryParameter("psw", "69")
                .build();
        Request request = new Request.Builder()
                .url(httpUrl)
                .build();
        final Call getCall = getClient.newCall(request);

        class networkWorker extends Thread  {
            public void run() {
                try {
                    Response responsePost = postCall.execute();
                    Log.i(TAG, "run: PUT code = " + responsePost.code());
                }
                catch (IOException e){
                    System.out.println("Exception whilst posting!!" + e.toString());
                    return;
                }

                try {
                    sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                try {
                    Response responseGet = getCall.execute();
                    Log.i(TAG, "run: GET code = " + responseGet.code());
                }
                catch (IOException e){
                    System.out.println("Exception whilst GET!!" + e.toString());
                }

                Log.i(TAG, "Sending broadacst to BLE service!");
                final Intent intent = new Intent(netService.REQUEST_BLE_PASS_DATA);
                intent.putExtra("chunk", new byte[] {69,2,3,4,5});
                sendBroadcast(intent);
            }
        }

        networkWorker worker = new networkWorker();
        worker.start();
    }

    public JSONObject createJsonProvisionRequest() {
        JSONObject obj = new JSONObject();
        try {
            obj.put("id", "3");
            obj.put("name", "NAME OF STUDENT");
            obj.put("year", "3rd");
            obj.put("curriculum", "Arts");
            obj.put("birthday", "5/5/1993");
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        return obj;
    }
}
