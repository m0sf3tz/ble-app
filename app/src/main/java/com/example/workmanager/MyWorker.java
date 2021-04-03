package com.example.workmanager;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class MyWorker extends Worker {
    public static final String TAG = "myWorker";

    public MyWorker(
            @NonNull Context appContext,
            @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        System.out.println("Beep boop do work!");
        return Result.success();
    }
}
