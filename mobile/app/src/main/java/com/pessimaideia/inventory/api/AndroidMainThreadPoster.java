package com.pessimaideia.inventory.api;
import android.os.Handler;
import android.os.Looper;

public class AndroidMainThreadPoster implements MainThreadPoster {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void post(Runnable runnable) {
        handler.post(runnable);
    }
}