package com.pessimaideia.inventory.scanner;

import android.app.Activity;
import android.graphics.Color;
import android.text.InputType;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;

/**
 * Keyboard-wedge scanner: the scanner types the barcode followed by Enter into whatever has
 * focus. We make that a 1x1 transparent EditText that keeps focus.
 */
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

    /** Returns true when we handled the key, so the EditText does not also act on it. */
    private boolean handleAction(int actionId, KeyEvent event) {
        if (event != null) {
            // Hardware Enter (scanner or PC keyboard) arrives twice: DOWN, then UP.
            if (!isEnter(event.getKeyCode())) return false;
            if (event.getAction() == KeyEvent.ACTION_DOWN) emit();
            return true;
        }
        // On-screen keyboard's "Done" key has no KeyEvent.
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            emit();
            return true;
        }
        return false;
    }

    private static boolean isEnter(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    private void emit() {
        String barcode = field.getText().toString().trim();
        field.setText("");
        if (barcode.isEmpty()) return;
        Log.d(TAG, "Scanned: " + barcode);
        listener.onScan(barcode);
    }
}