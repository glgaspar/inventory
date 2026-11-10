package com.pessimaideia.inventory.ui;

import android.view.View;
import android.widget.FrameLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatEditText;

import com.pessimaideia.inventory.App;
import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.api.HttpInventoryApi;

import okhttp3.HttpUrl;

public final class ServerDialog {

    private ServerDialog() {
    }

    public static void show(AppCompatActivity activity, Runnable onSaved) {
        App app = App.from(activity);
        AppCompatEditText input = new AppCompatEditText(activity);
        input.setSingleLine(true);
        input.setHint(R.string.server_hint);
        HttpUrl current = app.serverUrl();
        if (current != null) {
            input.setText(current.toString());
            input.setSelection(input.length());
        }

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.server_title)
                .setMessage(R.string.server_message)
                .setView(padded(activity, input))
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(shown -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String text = String.valueOf(input.getText()).trim();
                    HttpUrl url = HttpInventoryApi.parseBaseUrl(text);
                    if (!text.isEmpty() && url == null) {
                        input.setError(activity.getString(R.string.server_invalid));
                        return;
                    }
                    if (url == null ? current != null : !url.equals(current)) {
                        app.setServerUrl(url);
                    }
                    dialog.dismiss();
                    onSaved.run();
                }));
        dialog.show();
    }

    private static FrameLayout padded(AppCompatActivity activity, View view) {
        int side = Math.round(20 * activity.getResources().getDisplayMetrics().density);
        FrameLayout frame = new FrameLayout(activity);
        frame.setPadding(side, 0, side, 0);
        frame.addView(view);
        return frame;
    }
}
