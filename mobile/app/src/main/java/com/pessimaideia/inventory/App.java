package com.pessimaideia.inventory;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.multidex.MultiDex;

import com.pessimaideia.inventory.api.ApiCallback;
import com.pessimaideia.inventory.api.ApiException;
import com.pessimaideia.inventory.api.HttpInventoryApi;
import com.pessimaideia.inventory.api.InventoryApi;
import com.pessimaideia.inventory.api.MockInventoryApi;
import com.pessimaideia.inventory.data.SessionStore;
import com.pessimaideia.inventory.model.LoginResult;
import com.pessimaideia.inventory.ui.login.LoginActivity;

import okhttp3.HttpUrl;

public class App extends Application {
    public static final String TAG = "Inventory";

    private static final String PREFS = "settings";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_USERNAME = "username";

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
        prefs.edit()
                .putString(KEY_SERVER_URL, url != null ? url.toString() : null)
                .remove(KEY_TOKEN)
                .remove(KEY_USERNAME)
                .apply();
        useServer(url);
    }

    public boolean needsLogin() {
        return serverUrl != null && prefs.getString(KEY_TOKEN, null) == null;
    }

    @Nullable
    public String username() {
        return prefs.getString(KEY_TOKEN, null) != null ? prefs.getString(KEY_USERNAME, null) : null;
    }

    @Nullable
    public String lastUsername() {
        return prefs.getString(KEY_USERNAME, null);
    }

    @Nullable
    public HttpInventoryApi loginApi() {
        return serverUrl != null ? HttpInventoryApi.forAndroid(serverUrl, null, null) : null;
    }

    public void signIn(LoginResult result) {
        prefs.edit()
                .putString(KEY_TOKEN, result.token)
                .putString(KEY_USERNAME, result.user.username)
                .apply();
        useServer(serverUrl);
    }

    public void signOut() {
        if (api instanceof HttpInventoryApi) {
            ((HttpInventoryApi) api).logout(new ApiCallback<Boolean>() {
                @Override
                public void onSuccess(Boolean result) {
                }

                @Override
                public void onError(ApiException error) {
                }
            });
        }
        clearLogin();
        openLogin();
    }

    private void onUnauthorized() {
        if (prefs.getString(KEY_TOKEN, null) == null) return;
        clearLogin();
        Toast.makeText(this, R.string.login_expired, Toast.LENGTH_LONG).show();
        openLogin();
    }

    private void clearLogin() {
        prefs.edit().remove(KEY_TOKEN).apply();
        useServer(serverUrl);
    }

    private void openLogin() {
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
    }

    private void useServer(@Nullable HttpUrl url) {
        serverUrl = url;
        api = url != null
                ? HttpInventoryApi.forAndroid(url, prefs.getString(KEY_TOKEN, null), this::onUnauthorized)
                : MockInventoryApi.forAndroid();
    }

    public static App from(Context context) {
        return (App) context.getApplicationContext();
    }
}
