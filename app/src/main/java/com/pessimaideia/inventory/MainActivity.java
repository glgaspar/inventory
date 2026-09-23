package com.pessimaideia.inventory;

import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.pessimaideia.inventory.api.HttpInventoryApi;
import com.pessimaideia.inventory.data.SessionStore;
import com.pessimaideia.inventory.ui.inventory.InventoryActivity;
import com.pessimaideia.inventory.ui.session.SessionActivity;

import okhttp3.HttpUrl;

public class MainActivity extends AppCompatActivity {

    private SessionStore sessionStore;
    private Button continueButton;
    private TextView serverStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_server) {
            editServer();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateServerStatus() {
        HttpUrl url = App.from(this).serverUrl();
        serverStatus.setText(url != null
                ? getString(R.string.server_status, url.toString())
                : getString(R.string.server_status_demo));
    }

    private void editServer() {
        App app = App.from(this);
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(R.string.server_hint);
        HttpUrl current = app.serverUrl();
        if (current != null) {
            input.setText(current.toString());
            input.setSelection(input.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.server_title)
                .setMessage(R.string.server_message)
                .setView(input)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String text = input.getText().toString().trim();
                    HttpUrl url = HttpInventoryApi.parseBaseUrl(text);
                    if (!text.isEmpty() && url == null) {
                        input.setError(getString(R.string.server_invalid));
                        return;
                    }
                    app.setServerUrl(url);
                    updateServerStatus();
                    dialog.dismiss();
                }));
        dialog.show();
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
}