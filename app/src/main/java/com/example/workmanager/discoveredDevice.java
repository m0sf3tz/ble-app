package com.example.workmanager;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;

public class discoveredDevice {
    private String Name, Serial;
    private BluetoothDevice Device;

    public discoveredDevice(String Serial, BluetoothDevice Device) {
        this.Serial = Serial;
        this.Device = Device;
    }
    public String getName () {
        return Name;
    }

    public String getSerial () {
        return Serial;
    }

    public BluetoothDevice getDevice () {
        return Device;
    }
}
