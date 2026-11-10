package com.pessimaideia.inventory;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.pessimaideia.inventory.data.SessionStore;
import com.pessimaideia.inventory.ui.ServerDialog;
import com.pessimaideia.inventory.ui.inventory.InventoryActivity;
import com.pessimaideia.inventory.ui.login.LoginActivity;
import com.pessimaideia.inventory.ui.session.SessionActivity;

import okhttp3.HttpUrl;

public class MainActivity extends AppCompatActivity {

    private SessionStore sessionStore;
    private Button continueButton;
    private TextView serverStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (App.from(this).needsLogin()) {
            openLogin();
            return;
        }
        setContentView(R.layout.activity_main);

        sessionStore = App.from(this).sessionStore();
        continueButton = findViewById(R.id.btn_continue);
        serverStatus = findViewById(R.id.server_status);

        continueButton.setOnClickListener(v -> openSession());
        findViewById(R.id.btn_new_session).setOnClickListener(v -> startNewSession());
        findViewById(R.id.btn_inventory).setOnClickListener(v ->
                startActivity(new Intent(this, InventoryActivity.class)));
    }

    /** Called every time this screen comes to the front, so the button reflects the file on disk. */
    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        if (App.from(this).needsLogin()) {
            openLogin();
            return;
        }
        int count = sessionStore.load().size();
        if (count > 0) {
            continueButton.setText(getResources()
                    .getQuantityString(R.plurals.home_continue_session, count, count));
            continueButton.setVisibility(View.VISIBLE);
        } else {
            continueButton.setVisibility(View.GONE);
        }
        updateServerStatus();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_logout).setVisible(App.from(this).username() != null);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_server) {
            ServerDialog.show(this, this::refresh);
            return true;
        }
        if (item.getItemId() == R.id.action_logout) {
            App.from(this).signOut();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateServerStatus() {
        App app = App.from(this);
        HttpUrl url = app.serverUrl();
        if (url == null) {
            serverStatus.setText(R.string.server_status_demo);
        } else if (app.username() != null) {
            serverStatus.setText(getString(R.string.server_status_user, url.toString(), app.username()));
        } else {
            serverStatus.setText(getString(R.string.server_status, url.toString()));
        }
        supportInvalidateOptionsMenu();
    }


    private void startNewSession() {
        if (!sessionStore.hasSession()) {
            openSession();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.discard_session_title)
                .setMessage(R.string.discard_session_message)
                .setPositiveButton(R.string.discard, (dialog, which) -> {
                    sessionStore.clear();
                    openSession();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void openSession() {
        startActivity(new Intent(this, SessionActivity.class));
    }

    private void openLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
