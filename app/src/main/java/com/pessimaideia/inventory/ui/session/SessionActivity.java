package com.pessimaideia.inventory.ui.session;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;
import com.pessimaideia.inventory.App;
import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.api.ApiCallback;
import com.pessimaideia.inventory.api.ApiException;
import com.pessimaideia.inventory.api.InventoryApi;
import com.pessimaideia.inventory.data.SessionStore;
import com.pessimaideia.inventory.model.MovementBatch;
import com.pessimaideia.inventory.model.Product;
import com.pessimaideia.inventory.model.SessionItem;
import com.pessimaideia.inventory.scanner.ScannerInput;
import com.pessimaideia.inventory.scanner.WedgeScannerInput;
import java.util.Date;
import java.util.List;

public class SessionActivity extends AppCompatActivity
        implements SessionAdapter.Listener, NewProductDialog.Listener {
    private final ScannerInput scanner = new WedgeScannerInput();
    private InventoryApi api;
    private SessionStore store;
    private List<SessionItem> items;
    private SessionAdapter adapter;
    private RecyclerView list;
    private ProgressBar progress;
    private TextView empty;
    private boolean busy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_session);
        setTitle(R.string.title_session);

        App app = App.from(this);
        api = app.api();
        store = app.sessionStore();
        items = store.load();

        list = findViewById(R.id.list);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty);

        adapter = new SessionAdapter(items, this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        list.setAdapter(adapter);
        updateEmptyState();

        scanner.attach(this, this::onScan);
    }

    @Override
    protected void onResume() {
        super.onResume();
        scanner.requestFocus();
    }

    // ---- toolbar: Send / Cancel session ---------------------------------------------------

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.session, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        menu.findItem(R.id.action_send).setEnabled(!items.isEmpty() && !busy);
        menu.findItem(R.id.action_cancel_session).setEnabled(!busy);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_send) {
            send();
            return true;
        }
        if (id == R.id.action_cancel_session) {
            confirmCancel();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void send() {
        final int count = items.size();
        MovementBatch batch = MovementBatch.entryFrom(items, new Date());
        setBusy(true);
        api.sendMovements(batch, new ApiCallback<Long>() {
            @Override
            public void onSuccess(Long batchId) {
                if (isDestroyed()) return;
                setBusy(false);
                store.clear();
                Toast.makeText(SessionActivity.this, getResources()
                        .getQuantityString(R.plurals.sent_toast, count, count), Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(ApiException error) {
                if (isDestroyed()) return;
                setBusy(false);
                Snackbar.make(list, getString(R.string.send_failed, error.getMessage()),
                                Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.retry, v -> send())
                        .show();
            }
        });
    }

    private void confirmCancel() {
        if (items.isEmpty()) {
            finish();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.discard_session_title)
                .setMessage(R.string.discard_session_message)
                .setPositiveButton(R.string.discard, (dialog, which) -> {
                    store.clear();
                    finish();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ---- scanning ------------------------------------------------------------------------

    private void onScan(String barcode) {
        if (busy) {
            toast(getString(R.string.scan_busy));
            return;
        }
        int position = indexOf(barcode);
        if (position >= 0) {
            onChangePackages(position, +1);
            return;
        }
        setBusy(true);
        api.getProductByBarcode(barcode, new ApiCallback<Product>() {
            @Override
            public void onSuccess(Product product) {
                if (isDestroyed()) return;
                setBusy(false);
                addItem(product);
            }

            @Override
            public void onError(ApiException error) {
                if (isDestroyed()) return;
                setBusy(false);
                if (error.isNotFound()) {
                    askForNewProduct(barcode);
                } else {
                    toast(getString(R.string.scan_failed, error.getMessage()));
                }
            }
        });
    }

    private void askForNewProduct(String barcode) {
        // If the user left the screen during the lookup, showing a dialog now would crash.
        if (getSupportFragmentManager().isStateSaved()) return;
        NewProductDialog.newInstance(barcode).show(getSupportFragmentManager(), "new_product");
    }

    @Override
    public void onNewProduct(Product draft) {
        setBusy(true);
        api.createProduct(draft, new ApiCallback<Product>() {
            @Override
            public void onSuccess(Product product) {
                if (isDestroyed()) return;
                setBusy(false);
                addItem(product);
            }

            @Override
            public void onError(ApiException error) {
                if (isDestroyed()) return;
                setBusy(false);
                toast(getString(R.string.create_failed, error.getMessage()));
            }
        });
    }

    private int indexOf(String barcode) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).product.barcode.equals(barcode)) return i;
        }
        return -1;
    }

    private void setBusy(boolean value) {
        busy = value;
        progress.setVisibility(value ? View.VISIBLE : View.INVISIBLE);
        invalidateOptionsMenu();
    }

    // ---- changing the list ---------------------------------------------------------------

    private void addItem(Product product) {
        items.add(new SessionItem(product, 1));
        int position = items.size() - 1;
        adapter.notifyItemInserted(position);
        list.scrollToPosition(position);
        save();
    }

    @Override
    public void onChangePackages(int position, int delta) {
        SessionItem item = items.get(position);
        if (item.packages + delta < 1) return;
        item.packages += delta;
        adapter.notifyItemChanged(position);
        list.scrollToPosition(position);
        save();
    }

    @Override
    public void onRemove(int position) {
        items.remove(position);
        adapter.notifyItemRemoved(position);
        save();
    }

    private void save() {
        store.save(items);
        updateEmptyState();
        invalidateOptionsMenu();
    }

    private void updateEmptyState() {
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }
}