package com.example.workmanager;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.google.android.gms.internal.age;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.internal.http.HttpHeaders;

import static com.example.workmanager.byteManager.createProvisionBlob;

public class netService extends Service {
    final static String TAG = "netService";
    private final IBinder binder = new LocalBinder();
    static final String URL_FULL_POST = "http://192.168.0.189:3000/api/provision_request/";
    static final String URL_FULL_GET =  "http://192.168.0.189:3000/api/provision_finalize/";
    static final String GET_META_DATA_URL_SEGMENT = "get_thermal_meta";
    static final String GET_THERMAL_IMAGE_URL_SEGMENT = "get_thermal_image";

    static final String URL_IP = "192.168.0.189";
    static final int URL_POR = 3000;
    static final String REQUEST_BLE_PASS_DATA = "REQUEST_BLE_PASS_DATA";
    static final String NEW_THERMAL_IMAGE_NETWORK= "NEW_THERMAL_IMAGE_NETWORK";
    static final String NO_THERMAL_META = "NO_THERMAL_META";
    static final String IMAGE_AGE_KEY = "IMAGE_AGE_KEY";

    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");
    public static final int POST_REQUEST = 0;
    public static final int POST_STATUS_OK = 200;
    public final static String ACTION_BACK_END_FAILED =
            "com.example.workManager.le.ACTION_BACK_END_FAILED";
    private Bitmap image;

    // Since a different thread updates the bitmap, we need to have a mutex for it
    public synchronized  Bitmap imageHandler(Boolean get, Bitmap bitmap){
        if (get){
            return image;
        } else {
            image = bitmap;
            return null;
        }
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

    public void provisionDevice(final String wifiSsid, final String wifiPassword, String Email, String Long, String Lat, String randomToken){
        // POST!
        final OkHttpClient postClient = new OkHttpClient().newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();
        RequestBody body = RequestBody.create(createJsonProvisionRequest(POST_REQUEST, randomToken, Email, Long, Lat).toString(), JSON);
        Request postRequest = new Request.Builder()
                .url(URL_FULL_POST)
                .post(body)
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
                .addPathSegment("api")
                .addPathSegment("provision_finalize")
                .addQueryParameter("Rand", randomToken)
                .build();
        Request request = new Request.Builder()
                .url(httpUrl)
                .build();
        final Call getCall = getClient.newCall(request);

        class networkWorker extends Thread  {
            public void run() {
                Response responsePost;
                try {
                    responsePost = postCall.execute();
                    Log.i(TAG, "run: PUT code = " + responsePost.code());
                }
                catch (IOException e){
                    System.out.println("Exception whilst posting!!" + e.toString());
                    broadcastUpdate (ACTION_BACK_END_FAILED);
                    return;
                }

                try {
                    sleep(5000);
                } catch (InterruptedException e) {
                    Log.i(TAG, "netService failed to start provisioning the device!");
                    broadcastUpdate (ACTION_BACK_END_FAILED);
                }

                if( responsePost.code() != POST_STATUS_OK) {
                    Log.i(TAG, "netService failed to start provisioning the device!");
                    broadcastUpdate (ACTION_BACK_END_FAILED);
                    return;
                }

                Response responseGet = null;
                try {
                    responseGet = getCall.execute();
                    Log.i(TAG, "run: GET code = " + responseGet.code());
                }
                catch (IOException e){
                    System.out.println("Exception whilst GET!!" + e.toString());
                    broadcastUpdate (ACTION_BACK_END_FAILED);
                }

                String api_key = null;
                if (responseGet != null) {
                    try {
                        api_key = responseGet.body().string();
                        responseGet.close();
                    } catch (IOException e) {
                        broadcastUpdate (ACTION_BACK_END_FAILED);
                        return;
                    }
                }

                if( responseGet.code() == POST_STATUS_OK) {
                    Log.i(TAG, "run: Status good!");
                    byte[] blob = createProvisionBlob(wifiSsid, wifiPassword, unMarshalApiKey(api_key));
                    Log.i(TAG, "Sending broadacst to BLE service!");
                    final Intent intent = new Intent(netService.REQUEST_BLE_PASS_DATA);
                    intent.putExtra("blob", blob);
                    intent.putExtra("api_key", unMarshalApiKey(api_key));
                    sendBroadcast(intent);
                }
            }
        }

        networkWorker worker = new networkWorker();
        worker.start();
    }

    // If we get here, we know the we have an image, and also the image age,
    // this function will bundle the image + age and send it to the main
    // page
    public void getThermal(String api_key, final int age){
        final OkHttpClient getClient = new OkHttpClient().newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();

        HttpUrl httpUrl = new HttpUrl.Builder()
                .scheme("http")
                .host(URL_IP)
                .port(URL_POR)
                .addPathSegment("api")
                .addPathSegment(GET_THERMAL_IMAGE_URL_SEGMENT)
                .addQueryParameter("api_key", api_key)
                .build();
        Request request = new Request.Builder()
                .url(httpUrl)
                .build();
        final Call getCall = getClient.newCall(request);

        class networkWorker extends Thread  {
            public void run() {
                Response responseGet;
                try {
                    responseGet = getCall.execute();
                    Log.i(TAG, "run: PUT code = " + responseGet.code());
                }
                catch (IOException e){
                    System.out.println("Exception whilst posting!!" + e.toString());
                    broadcastUpdate (ACTION_BACK_END_FAILED);
                    return;
                }

                InputStream inputStream = responseGet.body().byteStream();
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);

                if( responseGet.code() == POST_STATUS_OK) {
                    Log.i(TAG, "run: RXed a new thermal image!!");
                    final Intent intent = new Intent(netService.NEW_THERMAL_IMAGE_NETWORK);
                    if(bitmap != null){
                        imageHandler(false, bitmap);
                    }
                    intent.putExtra(IMAGE_AGE_KEY, age);
                    sendBroadcast(intent);
                }
            }
        }

        networkWorker worker = new networkWorker();
        worker.start();
    }

    public void getThermalMeta(final String api_key) {
        final OkHttpClient getClient = new OkHttpClient().newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build();

        HttpUrl httpUrl = new HttpUrl.Builder()
                .scheme("http")
                .host(URL_IP)
                .port(URL_POR)
                .addPathSegment("api")
                .addPathSegment(GET_META_DATA_URL_SEGMENT)
                .addQueryParameter("api_key", api_key)
                .build();
        Request request = new Request.Builder()
                .url(httpUrl)
                .build();
        final Call getCall = getClient.newCall(request);

        class networkWorker extends Thread {
            public void run() {
                Response responseGet;
                try {
                    responseGet = getCall.execute();
                    Log.i(TAG, "run: PUT code = " + responseGet.code());
                } catch (IOException e) {
                    System.out.println("Exception whilst posting!!" + e.toString());
                    broadcastUpdate(ACTION_BACK_END_FAILED);
                    return;
                }

                String thermel_meta_string = null;
                if (responseGet != null) {
                    try {
                        thermel_meta_string = responseGet.body().string();
                        responseGet.close();
                    } catch (IOException e) {
                        broadcastUpdate(ACTION_BACK_END_FAILED);
                        return;
                    }
                }

                if (responseGet.code() == POST_STATUS_OK) {
                    Log.i(TAG, "New thermal Meta Data!");
                    JSONObject jsonResponse = unMarshalMeta(thermel_meta_string);
                    if (jsonResponse == null) {
                        broadcastUpdate(ACTION_BACK_END_FAILED);
                        return;
                    }

                    boolean valid = false;
                    int age = 0;
                    final Intent intent = new Intent(netService.NO_THERMAL_META);
                    try {
                        valid = (boolean)jsonResponse.get("Image_valid");
                        age   = (int)jsonResponse.get("Image_age");
                    }
                    catch (JSONException e) {
                        sendBroadcast(intent);
                    }
                    if (valid == false){
                        // did not find an image
                        sendBroadcast(intent);
                    } else {
                        Log.i(TAG, "Valid image on backend, fetching!");
                        getThermal(api_key, age);
                    }
                }
            }
        }

        networkWorker worker = new networkWorker();
        worker.start();
    }

    public JSONObject createJsonProvisionRequest(int type, String randomToken, String email, String Long,
                                                 String Lat) {
        JSONObject obj = new JSONObject();
        try {
            if (type == POST_REQUEST) {
                obj.put("Email", email);
                obj.put("Lat", Lat);
                obj.put("Long", Long);
                obj.put("Rand", randomToken);
            }
        } catch (JSONException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return obj;
    }

    public String unMarshalApiKey(String obj){
        try {
            return new JSONObject(obj).get("Api_key").toString();
        } catch (JSONException e) {
            return null;
        }
    }

    public static JSONObject unMarshalMeta(String obj){
        try {
            return new JSONObject(obj);
        } catch (JSONException e) {
            return null;
        }
    }

    // Sends broadcasts to the main activity
    private void broadcastUpdate(final String action) {
        Log.i(TAG, "broadcastUpdate (netService): sending: " + action);
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }
}
