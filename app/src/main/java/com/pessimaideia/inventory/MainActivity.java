package com.pessimaideia.inventory;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.pessimaideia.inventory.data.SessionStore;
import com.pessimaideia.inventory.ui.inventory.InventoryActivity;
import com.pessimaideia.inventory.ui.session.SessionActivity;

public class MainActivity extends AppCompatActivity {

    private SessionStore sessionStore;
    private Button continueButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        sessionStore = App.from(this).sessionStore();
        continueButton = findViewById(R.id.btn_continue);

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