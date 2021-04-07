package com.example.workmanager;

import androidx.annotation.MainThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

public class bleLiveData {
    private static MutableLiveData<Boolean> sInstanceProvisioned;
    private static MutableLiveData<ArrayList<String>> sInstanceDeviceArr;

    public static MutableLiveData<Boolean> getLiveDataSingletonProvisionedStatus() {
        if (sInstanceProvisioned == null) {
            sInstanceProvisioned = new MutableLiveData<Boolean>();
        }
        return sInstanceProvisioned;
    }

    public static MutableLiveData<ArrayList<String>> getLiveDataSingletonDeviceArr() {
        if (sInstanceDeviceArr == null) {
            sInstanceDeviceArr = new MutableLiveData<ArrayList<String>>();

            sInstanceDeviceArr.setValue(new ArrayList<String>());
        }
        return sInstanceDeviceArr;
    }
}

