package com.pessimaideia.inventory;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;
import androidx.multidex.MultiDex;

import com.pessimaideia.inventory.api.HttpInventoryApi;
import com.pessimaideia.inventory.api.InventoryApi;
import com.pessimaideia.inventory.api.MockInventoryApi;
import com.pessimaideia.inventory.data.SessionStore;

import okhttp3.HttpUrl;

public class App extends Application {
    public static final String TAG = "Inventory";

    private static final String PREFS = "settings";
    private static final String KEY_SERVER_URL = "server_url";

    private SharedPreferences prefs;
    private InventoryApi api;
    private SessionStore sessionStore;
    @Nullable
    private HttpUrl serverUrl;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        sessionStore = new SessionStore(getFilesDir());
        useServer(HttpInventoryApi.parseBaseUrl(prefs.getString(KEY_SERVER_URL, null)));
    }

    public InventoryApi api() {
        return api;
    }

    public SessionStore sessionStore() {
        return sessionStore;
    }

    @Nullable
    public HttpUrl serverUrl() {
        return serverUrl;
    }

    public void setServerUrl(@Nullable HttpUrl url) {
        prefs.edit().putString(KEY_SERVER_URL, url != null ? url.toString() : null).apply();
        useServer(url);
    }

    private void useServer(@Nullable HttpUrl url) {
        serverUrl = url;
        api = url != null ? HttpInventoryApi.forAndroid(url) : MockInventoryApi.forAndroid();
    }

    public static App from(Context context) {
        return (App) context.getApplicationContext();
    }
}
