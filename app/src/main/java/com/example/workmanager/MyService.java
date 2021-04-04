package com.example.workmanager;

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
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.util.SparseArray;

import androidx.lifecycle.MutableLiveData;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;




public class MyService extends Service {
    final static String TAG = "MyService";

    // When filtering for advertising packets, we filter for devices that have device name set to
    // TERA_FIRE_GUARD _AND_ manufacturer DI set to 52651
    // this is how we uniquely identify TeraHelion Devices
    final static String DEVICE_NAME = "TERA_FIRE_GUARD";
    final static int MANUFACTURER_ID = 52651;

    private final IBinder binder = new LocalBinder();
    private boolean scanning;
    private static final long SCAN_PERIOD = 2000;
    BluetoothAdapter bluetoothAdapter = null;
    private BluetoothLeScanner bluetoothLeScanner;
    private Handler handler = new Handler();
    private ArrayList<ScanFilter> filterList;
    private ScanSettings settings;

    ArrayList<discoveredDevice> discoveredDevicesArray;
    gattCallback gattCallback;
    static BluetoothGatt refGatt;

    static final String TERA_FIRE_UUID = "0000abcd-0000-1000-8000-00805f9b34fb";
    BluetoothGattCharacteristic deviceCloudKey;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    public final static String ACTION_GATT_CONNECTED =
            "com.example.workManager.le.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "com.example.workManager.le.ACTION_GATT_DISCONNECTED";

    class gattCallback extends BluetoothGattCallback {
        final static String TAG = "gattCallback";

        public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                            int newState) {
            Log.i(TAG, "onConnectionStateChange: " + status + newState);

            if ( status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED ){
                Log.i(TAG, "onConnectionStateChange: Connected!");
                MyService.refGatt = gatt;
                gatt.discoverServices();
                broadcastUpdate(ACTION_GATT_CONNECTED);
            }

            if ( newState == BluetoothGatt.STATE_DISCONNECTED ){
                Log.i(TAG, "onConnectionStateChange: STATE_DISCONNECTED!");
                MyService.refGatt = null;
                broadcastUpdate(ACTION_GATT_DISCONNECTED);
            }
        }

        private void broadcastUpdate(final String action) {
            Log.i(TAG, "broadcastUpdate: sending: " + action);
            final Intent intent = new Intent(action);
            sendBroadcast(intent);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
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
    }

    public class LocalBinder extends Binder {
        MyService getService() {
            // Return this instance of LocalService so clients can call public methods
            return MyService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void BleConnect(int position){
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
        final MutableLiveData<ArrayList<discoveredDevice>> myMutable = ListLiveData.get();

        if (bluetoothLeScanner != null) {
            if (!scanning) {
                if (refGatt != null){
                    refGatt.disconnect();
                }

                // Stops scanning after a pre-defined scan period.
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        Log.i(TAG, "BleScan: Done scanning for data");
                        scanning = false;
                        bluetoothLeScanner.stopScan(leScanCallback);

                        // post to the UI thread the discovered devices
                        myMutable.postValue(discoveredDevicesArray);
                    }
                }, SCAN_PERIOD);

                discoveredDevicesArray.clear(); // clear the discovered devices
                myMutable.postValue(discoveredDevicesArray);

                scanning = true;
                bluetoothLeScanner.startScan(filterList, settings, leScanCallback);
            } else {
                scanning = false;
                bluetoothLeScanner.stopScan(leScanCallback);
            }
        }
    }

    public void listServices(){
        if (refGatt == null)
            return;

        List<BluetoothGattService> serviceList = refGatt.getServices();
        for (BluetoothGattService service:serviceList){
            Log.i(TAG, "listServices: item" + service.getUuid() );

            //get a list of characteristics
            List<BluetoothGattCharacteristic> characteristicsList = service.getCharacteristics();
            for ( BluetoothGattCharacteristic characteristics: characteristicsList) {
                Log.i(TAG, "---->" + characteristics.getUuid().toString());
                }
            Log.i(TAG, "");
        }
    }

    public BluetoothGattCharacteristic getChar(){
        if (refGatt == null)
            return null;

        List<BluetoothGattService> serviceList = refGatt.getServices();
        for (BluetoothGattService service:serviceList){

            //get a list of characteristics
            List<BluetoothGattCharacteristic> characteristicsList = service.getCharacteristics();
            for ( BluetoothGattCharacteristic characteristics: characteristicsList) {
                if ( characteristics.getUuid().toString().equals(TERA_FIRE_UUID)) {
                    return characteristics;
                }
            }
            Log.i(TAG, "");
        }
        Log.e(TAG, "getChar: chould not find char");
        return null;
    }

    public void setChar() {
        BluetoothGattCharacteristic bleChar = getChar();
        if (bleChar != null) {
            bleChar.setValue("12");
            if ( ! refGatt.writeCharacteristic(bleChar) ){
                Log.e(TAG, "setChar: Failed to write characteristic");
            }

        } else {
            Log.i(TAG, "setChar: could not setChar");
        }
    }

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
                    byte arr[] = sb.get(MANUFACTURER_ID);
                    String serialFound = new String(arr, StandardCharsets.UTF_8);

                    discoveredDevicesArray.add(new discoveredDevice(serialFound, result.getDevice()));
                }

                @Override
                public void onScanFailed(final int errorCode) {
                    Log.e(TAG, "onScanFailed: Failed to scan!");
                }
            };

    // Sends broadcasts to the main activity
    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }
}
