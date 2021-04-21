package com.example.workmanager;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.work.Constraints;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class MyViewModel extends ViewModel {
    final static String TAG = "MyViewModel";
    private WorkManager mWorkManager;
    private MutableLiveData<List<String>> bleDevices;


    public MyViewModel(Application application) {
        mWorkManager = WorkManager.getInstance(application);
    }

    public void checkPeriod() {
        Log.i(TAG, "checkPeriod: Enqueued");
        PeriodicWorkRequest work = new PeriodicWorkRequest.Builder(MyWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(Constraints.NONE)
                .build();
        mWorkManager.enqueue(work);
    }
}
