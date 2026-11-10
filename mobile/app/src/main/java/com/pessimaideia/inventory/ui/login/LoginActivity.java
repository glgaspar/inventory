package com.pessimaideia.inventory.ui.login;

import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.pessimaideia.inventory.App;
import com.pessimaideia.inventory.MainActivity;
import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.api.ApiCallback;
import com.pessimaideia.inventory.api.ApiException;
import com.pessimaideia.inventory.api.HttpInventoryApi;
import com.pessimaideia.inventory.model.LoginResult;
import com.pessimaideia.inventory.ui.ServerDialog;

import okhttp3.HttpUrl;

public class LoginActivity extends AppCompatActivity {

    private EditText username;
    private EditText password;
    private TextView error;
    private TextView server;
    private Button loginButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        username = findViewById(R.id.username);
        password = findViewById(R.id.password);
        error = findViewById(R.id.login_error);
        server = findViewById(R.id.login_server);
        loginButton = findViewById(R.id.btn_login);

        loginButton.setOnClickListener(v -> login());
        findViewById(R.id.btn_server).setOnClickListener(v -> ServerDialog.show(this, this::afterServerChange));
        password.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_DONE || enter) {
                login();
                return true;
            }
            return false;
        });
        String last = App.from(this).lastUsername();
        if (last != null) {
            username.setText(last);
            password.requestFocus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        afterServerChange();
    }

    private void afterServerChange() {
        App app = App.from(this);
        HttpUrl url = app.serverUrl();
        if (url == null || !app.needsLogin()) {
            openHome();
            return;
        }
        server.setText(getString(R.string.server_status, url.toString()));
    }

    private void login() {
        String user = username.getText().toString().trim();
        String pass = password.getText().toString();
        if (user.isEmpty() || pass.isEmpty()) {
            showError(getString(R.string.login_missing));
            return;
        }
        HttpInventoryApi api = App.from(this).loginApi();
        if (api == null) {
            openHome();
            return;
        }
        setBusy(true);
        error.setVisibility(View.GONE);
        api.login(user, pass, new ApiCallback<LoginResult>() {
            @Override
            public void onSuccess(LoginResult result) {
                App.from(LoginActivity.this).signIn(result);
                openHome();
            }

            @Override
            public void onError(ApiException e) {
                setBusy(false);
                if (e.statusCode == 401) {
                    password.setText("");
                    showError(getString(R.string.login_wrong));
                } else if (e.statusCode == 429) {
                    showError(getString(R.string.login_rate_limited));
                } else {
                    showError(getString(R.string.login_failed, e.getMessage()));
                }
            }
        });
    }

    private void setBusy(boolean busy) {
        loginButton.setEnabled(!busy);
        loginButton.setText(busy ? R.string.login_busy : R.string.login_button);
    }

    private void showError(String text) {
        error.setText(text);
        error.setVisibility(View.VISIBLE);
    }

    private void openHome() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
