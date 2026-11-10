package com.pessimaideia.inventory.scanner;

import android.app.Activity;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

public class WedgeScannerInput implements ScannerInput {

    private static final String TAG = "Inventory";

    private EditText field;
    private Listener listener;

    @Override
    public void attach(Activity activity, Listener listener) {
        this.listener = listener;

        field = new EditText(activity);
        field.setSingleLine(true);
        field.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        field.setImeOptions(EditorInfo.IME_ACTION_DONE);
        field.setBackgroundColor(Color.TRANSPARENT);
        field.setTextColor(Color.TRANSPARENT);
        field.setCursorVisible(false);
        field.setOnEditorActionListener((view, actionId, event) -> handleAction(actionId, event));
        field.setOnKeyListener((view, keyCode, event) -> handleKey(keyCode, event));
        field.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable text) {
                handleText(text);
            }
        });

        ViewGroup root = activity.findViewById(android.R.id.content);
        root.addView(field, new ViewGroup.LayoutParams(1, 1));
        requestFocus();
    }

    @Override
    public void requestFocus() {
        if (field != null) {
            field.requestFocus();
        }
    }

    private boolean handleAction(int actionId, KeyEvent event) {
        if (event != null) {
            if (event.getKeyCode() != KeyEvent.KEYCODE_ENTER) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) emitField();
            return true;
        }
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            emitField();
            return true;
        }
        return false;
    }

    private boolean handleKey(int keyCode, KeyEvent event) {
        if (keyCode != KeyEvent.KEYCODE_TAB && keyCode != KeyEvent.KEYCODE_NUMPAD_ENTER) return false;
        if (event.getAction() == KeyEvent.ACTION_DOWN) emitField();
        return true;
    }

    private void handleText(Editable text) {
        int end = firstTerminator(text);
        if (end < 0) return;
        String barcode = text.subSequence(0, end).toString();
        String rest = text.subSequence(end + 1, text.length()).toString();
        deliver(barcode);
        field.setText(rest);
    }

    static int firstTerminator(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r' || c == '\t') return i;
        }
        return -1;
    }

    private void emitField() {
        String text = field.getText().toString();
        field.setText("");
        deliver(text);
    }

    private void deliver(String raw) {
        String barcode = raw.trim();
        if (barcode.isEmpty()) return;
        Log.d(TAG, "Scanned: " + barcode);
        listener.onScan(barcode);
    }
}
