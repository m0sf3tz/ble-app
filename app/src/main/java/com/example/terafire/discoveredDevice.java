package com.example.terafire;

import android.bluetooth.BluetoothDevice;

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
