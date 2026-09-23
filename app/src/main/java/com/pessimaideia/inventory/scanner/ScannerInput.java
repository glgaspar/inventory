package com.pessimaideia.inventory.scanner;

import android.app.Activity;

/** Delivers scanned barcodes to a screen, whatever the hardware does underneath. */
public interface ScannerInput {

    interface Listener {
        void onScan(String barcode);
    }

    /** Call once in onCreate, after setContentView. */
    void attach(Activity activity, Listener listener);

    /** Call in onResume and after a dialog closes, so the next scan lands here again. */
    void requestFocus();
}