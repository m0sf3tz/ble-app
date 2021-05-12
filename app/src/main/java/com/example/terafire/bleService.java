package com.example.terafire;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.SparseArray;

import androidx.lifecycle.MutableLiveData;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class bleService extends Service {
    public final static String ACTION_GATT_CONNECTED =
            "com.example.workManager.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.workManager.le.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_DISCOVERED =
            "com.example.workManager.le.ACTION_GATT_DISCOVERED";
    public final static String ACTION_GATT_WRITE_GOOD =
            "com.example.workManager.le.ACTION_GATT_WRITE_GOOD";
    public final static String ACTION_GATT_WRITE_BAD =
            "com.example.workManager.le.ACTION_GATT_BAD";
    public final static String API_KEY_STRING_KEY = "API_KEY_STRING_KEY";

    final static String TAG = "MyService";
    // When filtering for advertising packets, we filter for devices that have device name set to
    // TERA_FIRE_GUARD _AND_ manufacturer ID set to 52651
    // this is how we uniquely identify TeraHelion Devices
    final static String DEVICE_NAME = "TERA_FIRE_GUARD";
    final static int MANUFACTURER_ID = 52651;

    // WRITE, used to provision the device
    static final String PROVISION_UUID = "0000abcd-0000-1000-8000-00805f9b34fb";
    // READ, WiFi + provisioned status,
    static final String DEVICE_STATUS_UUID = "0000beef-0000-1000-8000-00805f9b34fb";

    private static final long SCAN_PERIOD = 2000;
    private static final int GATT_SHUT_DOWN = 1;
    static BluetoothGatt refGatt;
    private static ArrayList<discoveredDevice> discoveredDevicesArray = new ArrayList<discoveredDevice>();
    private final IBinder binder = new LocalBinder();
    BluetoothAdapter bluetoothAdapter = null;
    gattCallback gattCallback;
    private Handler mHandler;
    private boolean scanning;
    private BluetoothLeScanner bluetoothLeScanner;
    private Handler handler = new Handler();
    private ArrayList<ScanFilter> filterList;
    private ScanSettings settings;
    private MutableLiveData<String> currentName;
    private final int GET_PROVISION_STATUS = 0;
    private final int GET_PROVISION_CHAR = 1;
    private static String api_key;
    private static final int NO_WIFI_NO_PROVISIONED = 0;
    private static final int PROVISIONED            = 1;
    private static final int WIFI_OK                = 2;

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (netService.REQUEST_BLE_PASS_DATA.equals(action)) {
                Log.i(TAG, "onReceive: Will pass data to device!");
                byte blob[]= intent.getByteArrayExtra("blob" );
                // store the temp api_key, we will commit this to memory if the BLE
                // write to the device is good
                bleService.api_key = intent.getStringExtra("api_key" );
                // must get a reference to the service
                IBinder binder = peekService(context, new Intent(context, bleService.class));
                if (binder == null)
                    return;
                bleService ble = ((bleService.LocalBinder) binder).getService();
                ble.setChar(blob);
            }
        }
    };

    // Device scan callback.
    private ScanCallback leScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    Log.i(TAG, "onScanResult: " + result.getDevice() + "specfic = " + result.getScanRecord().getManufacturerSpecificData());

                    // In android, the Manufacturer specific Data is stored in a "hash map"
                    // were the key is the first 2 bytes of the Manufacturer specific data and
                    // the value is the remaining bytes, hence, for us, we will always fix the
                    // first two bytes to MANUFACTURER_ID and use the rest of this field to store
                    // our serial
                    SparseArray<byte[]> sb = result.getScanRecord().getManufacturerSpecificData();
                    byte[] arr = sb.get(MANUFACTURER_ID);
                    String serialFound = new String(arr, StandardCharsets.UTF_8);

                    discoveredDevicesArray.add(new discoveredDevice(serialFound, result.getDevice()));
                }

                @Override
                public void onScanFailed(final int errorCode) {
                    Log.e(TAG, "onScanFailed: Failed to scan!");
                }
            };

    public void pollDeviceStats() {
        readChar();
    }

    // clean up bluetooth against the OS
    public void close() {
        if (refGatt == null) {
            return;
        }
        refGatt.close();
        refGatt = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "onDestroy: service destroyed!");
        unregisterReceiver(broadcastReceiver);
        close();
    }

    // disconnects from BLE
    public void disconnect(){
        Log.i(TAG, "disconnect: disconnect from BLE!");
        if (refGatt == null) {
            return;
        }
        refGatt.disconnect();
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
        }

        if (bluetoothAdapter == null) {
            Log.i(TAG, "onCreate: Bluetooth adaptor NULL - not much to do... ");
            return;
        }

        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        ScanFilter filter = new ScanFilter.Builder().setDeviceName(DEVICE_NAME).build();
        filterList = new ArrayList<>();
        filterList.add(filter);

        settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_FIRST_MATCH)
                .build();

        discoveredDevicesArray = new ArrayList<discoveredDevice>();

        // Create a new background thread for processing messages or runnables sequentially
        HandlerThread handlerThread = new HandlerThread("HandlerThreadName");
        // Starts the background thread
        handlerThread.start();
        // Create a handler attached to the HandlerThread's Looper
        mHandler = new Handler(handlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                Log.i(TAG, "handleMessage: message" + msg.what);

                if (msg.what == GATT_SHUT_DOWN) {
                    Log.i(TAG, "handleMessage: Shutting down service...!");
                    stopSelf();
                }
            }
        };

        registerReceiver(broadcastReceiver, getItentFilter());
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void BleConnect(int position) {
        BluetoothDevice device = discoveredDevicesArray.get(position).getDevice();
        Log.i(TAG, "BleConnect: Connecting to device: " + device);
        gattCallback = new gattCallback();
        BluetoothGatt bluetoothGatt = device.connectGatt(this, false, gattCallback);
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public void BleScan() {
        Log.i(TAG, "BleScan: Starting BLE scan!");

        if (bluetoothLeScanner != null) {
            if (!scanning) {
                if (refGatt != null) {
                    refGatt.disconnect();
                }

                // Stops scanning after a pre-defined scan period.
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, "BleScan: Done scanning for data");
                        scanning = false;
                        bluetoothLeScanner.stopScan(leScanCallback);

                        updateLiveData();
                    }
                }, SCAN_PERIOD);

                scanning = true;
                bluetoothLeScanner.startScan(filterList, settings, leScanCallback);
                clearLiveData(); //Update the UI thread

            } else {
                scanning = false;
                bluetoothLeScanner.stopScan(leScanCallback);
            }
        }
    }

    private void updateLiveData() {
        MutableLiveData<ArrayList<String>> mMutable = globalsApplication.getLiveDataSingletonDeviceArr();

        ArrayList<String> deviceStringArr = new ArrayList<String>();
        if (discoveredDevicesArray != null) {
            for (discoveredDevice device : discoveredDevicesArray) {
                deviceStringArr.add(device.getSerial());
            }
        }
        // post to UI thread
        mMutable.setValue(deviceStringArr);
    }

    private void clearLiveData() {
        MutableLiveData<ArrayList<String>> mMutable = globalsApplication.getLiveDataSingletonDeviceArr();

        // clear the old discovered devices
        if (discoveredDevicesArray != null) {
            discoveredDevicesArray.clear();
        }
        // post to UI thread
        mMutable.setValue(new ArrayList<String>());
    }

    public void listServices() {
        if (refGatt == null)
            return;

        List<BluetoothGattService> serviceList = refGatt.getServices();
        for (BluetoothGattService service : serviceList) {
            Log.i(TAG, "listServices: item" + service.getUuid());

            //get a list of characteristics
            List<BluetoothGattCharacteristic> characteristicsList = service.getCharacteristics();
            for (BluetoothGattCharacteristic characteristics : characteristicsList) {
                Log.i(TAG, "---->" + characteristics.getUuid().toString());
            }
            Log.i(TAG, "");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    // this gets the handle to the characteristics, not the value itself
    public BluetoothGattCharacteristic getChar(int characteristic) {
        if (refGatt == null)
            return null;

        List<BluetoothGattService> serviceList = refGatt.getServices();
        for (BluetoothGattService service : serviceList) {

            //get a list of characteristics
            List<BluetoothGattCharacteristic> characteristicsList = service.getCharacteristics();
            for (BluetoothGattCharacteristic characteristics : characteristicsList) {
                if (characteristic == GET_PROVISION_CHAR) {
                    if (characteristics.getUuid().toString().equals(PROVISION_UUID)) {
                        return characteristics;
                    }
                }
                if (characteristic == GET_PROVISION_STATUS) {
                    if (characteristics.getUuid().toString().equals(DEVICE_STATUS_UUID)) {
                        return characteristics;
                    }
                }
            }
            Log.i(TAG, "");
        }
        Log.e(TAG, "getChar: Could not find char");
        return null;
    }

    public void setChar(byte[] blob) {
        BluetoothGattCharacteristic bleChar = getChar(GET_PROVISION_CHAR);
        if (bleChar != null) {
            bleChar.setValue(blob);
            if (!refGatt.writeCharacteristic(bleChar)) {
                Log.e(TAG, "setChar: Failed to write characteristic");
            }

        } else {
            Log.i(TAG, "setChar: could not setChar");
        }
    }

    //async
    public void readChar() {
        BluetoothGattCharacteristic bleChar = getChar(GET_PROVISION_STATUS);
        refGatt.readCharacteristic(bleChar);
    }

    // Sends broadcasts to the main activity
    private void broadcastUpdate(final String action) {
        Log.i(TAG, "broadcastUpdate: sending: " + action);
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    public MutableLiveData<String> getCurrentName() {
        if (currentName == null) {
            currentName = new MutableLiveData<String>();
        }
        return currentName;
    }

    @Override
    public void onRebind(Intent intent) {
        super.onUnbind(intent);

        if (mHandler != null) {
            Log.i(TAG, "onRebind: Removing destroy self callback!");
            mHandler.removeMessages(100);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        super.onUnbind(intent);
        Log.i(TAG, "onUnbind: Unbounded!");
        Message mMsg = new Message();
        mMsg.what = 100;

        if (mHandler != null) {
            mHandler.sendMessageDelayed(mMsg, 500);
        }

        // Default implementation does nothing and returns false
        // If we return True, onRebind will be called when a
        // Client connects
        return true;
    }

    class gattCallback extends BluetoothGattCallback {
        final static String TAG = "gattCallback";

        // Updates the UI, Lets the user know if the device is provisioned
        void updateUiStatus(BluetoothGattCharacteristic characteristic, final String uuid) {
            if (characteristic == null) {
                Log.i(TAG, "updateUiProvisioned: Arg == null!");
                return;
            }

            byte[] arr = characteristic.getValue();
            // This value is coming from the device, it will set it to 0xFF if the condition is true
            // else 0x00.
            Boolean wifiStatus = false;
            Boolean provisionStatus = false;
            if (arr.length == 0) {
                Log.i(TAG, "updateUiStatus: Error? Nothin read!");
                return;
            }

            if (arr[0] == NO_WIFI_NO_PROVISIONED) {
                MutableLiveData<Boolean> mMutableProv = globalsApplication.getLiveDataSingletonProvisionedStatus();
                mMutableProv.postValue(false);
                MutableLiveData<Boolean> mMutableWifi = globalsApplication.getLiveDataSingletonWifiStatus();
                mMutableWifi.postValue(false);
            } else if (arr[0] == PROVISIONED) {
                MutableLiveData<Boolean> mMutableProv = globalsApplication.getLiveDataSingletonProvisionedStatus();
                mMutableProv.postValue(true);
                MutableLiveData<Boolean> mMutableWifi = globalsApplication.getLiveDataSingletonWifiStatus();
                mMutableWifi.postValue(false);
            } else {
                MutableLiveData<Boolean> mMutableProv = globalsApplication.getLiveDataSingletonProvisionedStatus();
                mMutableProv.postValue(true);
                MutableLiveData<Boolean> mMutableWifi = globalsApplication.getLiveDataSingletonWifiStatus();
                mMutableWifi.postValue(true);
            }
        }

        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            Log.i(TAG, "onConnectionStateChange: " + status + newState);

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {
                Log.i(TAG, "onConnectionStateChange: Connected!");
                bleService.refGatt = gatt;
                gatt.discoverServices();
                broadcastUpdate(ACTION_GATT_CONNECTED);
            }

            if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                Log.i(TAG, "onConnectionStateChange: STATE_DISCONNECTED!");
                bleService.refGatt = null;
                broadcastUpdate(ACTION_GATT_DISCONNECTED);
            }
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            super.onMtuChanged(gatt, mtu, status);
            Log.i(TAG, "New MTU: " + mtu + " , Status: " + status + " , Succeed: " + (status == BluetoothGatt.GATT_SUCCESS));
        }

        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {

            if (status == BluetoothGatt.GATT_SUCCESS) {
                updateUiStatus(characteristic, characteristic.getUuid().toString());
            }
        }

        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            Log.i(TAG, "onServicesDiscovered");
        }

        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic, int status) {
            Log.i(TAG, "onCharacteristicWrite: Write callback, status = " + status);
            if(status == BluetoothGatt.GATT_SUCCESS){
                Log.i(TAG, "onCharacteristicWrite: Write Good!!");
                broadcastUpdate(ACTION_GATT_WRITE_GOOD);
                // commit stored API key in memory
                storeApiKey(bleService.api_key);
            } else {
                Log.i(TAG, "onCharacteristicWrite: Write Failed!");
                broadcastUpdate(ACTION_GATT_WRITE_BAD);
            }
        }
    }

    public class LocalBinder extends Binder {
        bleService getService() {
            // Return this instance of LocalService so clients can call public methods
            return bleService.this;
        }
    }

    private static IntentFilter getItentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(netService.REQUEST_BLE_PASS_DATA);
        return intentFilter;
    }

    private void storeApiKey(String api_key){
        Log.i(TAG, "storeApiKey: Storing API key = " + api_key);
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(API_KEY_STRING_KEY, api_key);
        editor.commit();
    }
}
