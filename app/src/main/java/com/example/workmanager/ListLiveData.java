package com.example.workmanager;

import androidx.annotation.MainThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.List;

public class ListLiveData {
    private static MutableLiveData<ArrayList<discoveredDevice>> sInstance;

    public static MutableLiveData<ArrayList<discoveredDevice>> get() {
        if (sInstance == null) {
            sInstance = new MutableLiveData<ArrayList<discoveredDevice>>();
        }
        return sInstance;
    }
}

