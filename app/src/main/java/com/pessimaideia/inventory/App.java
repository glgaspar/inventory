package com.pessimaideia.inventory;

import android.app.Application;
import android.content.Context;

import com.pessimaideia.inventory.api.InventoryApi;
import com.pessimaideia.inventory.api.MockInventoryApi;
import com.pessimaideia.inventory.data.SessionStore;

public class App extends Application {
    public static final String TAG = "Inventory";

    private InventoryApi api;
    private SessionStore sessionStore;

    @Override
    public void onCreate() {
        super.onCreate();
        api = MockInventoryApi.forAndroid();      // swap for HttpInventoryApi
        sessionStore = new SessionStore(getFilesDir());
    }

    public InventoryApi api() {
        return api;
    }

    public SessionStore sessionStore() {
        return sessionStore;
    }

    public static App from(Context context) {
        return (App) context.getApplicationContext();
    }
}