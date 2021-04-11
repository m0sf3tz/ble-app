package com.example.workmanager;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

public class netService extends Service {
    private final IBinder binder = new LocalBinder();

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
}
