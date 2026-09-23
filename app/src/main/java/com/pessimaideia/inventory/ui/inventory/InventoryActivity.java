package com.pessimaideia.inventory.ui.inventory;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;
import com.pessimaideia.inventory.App;
import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.api.ApiCallback;
import com.pessimaideia.inventory.api.ApiException;
import com.pessimaideia.inventory.model.InventoryItem;
import com.pessimaideia.inventory.ui.inventory.InventoryRows.SortColumn;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class InventoryActivity extends AppCompatActivity {
    private static final String PREFS = "inventory";
    private static final String KEY_COLUMN = "sort_column";
    private static final String KEY_ASCENDING = "sort_ascending";
    private final InventoryAdapter adapter = new InventoryAdapter(this::openDetail);
    private final Map<SortColumn, TextView> headers = new EnumMap<>(SortColumn.class);
    private SharedPreferences prefs;
    private List<InventoryItem> items = new ArrayList<>();
    private SortColumn column;
    private boolean ascending;
    private RecyclerView list;
    private ProgressBar progress;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);
        setTitle(R.string.title_inventory);

        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        column = readColumn();
        ascending = prefs.getBoolean(KEY_ASCENDING, true);

        list = findViewById(R.id.list);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        bindHeader(SortColumn.NAME, R.id.col_name);
        bindHeader(SortColumn.PACKAGES, R.id.col_packages);
        bindHeader(SortColumn.AMOUNT, R.id.col_amount);
        bindHeader(SortColumn.UPDATED, R.id.col_updated);
        updateHeaders();

        load();
    }

    // ---- loading -------------------------------------------------------------------------

    private void load() {
        progress.setVisibility(View.VISIBLE);
        App.from(this).api().getInventory(new ApiCallback<List<InventoryItem>>() {
            @Override
            public void onSuccess(List<InventoryItem> result) {
                if (isDestroyed()) return;
                progress.setVisibility(View.INVISIBLE);
                items = result;
                showRows();
            }

            @Override
            public void onError(ApiException error) {
                if (isDestroyed()) return;
                progress.setVisibility(View.INVISIBLE);
                Snackbar.make(list, getString(R.string.inventory_failed, error.getMessage()),
                                Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.retry, v -> load())
                        .show();
            }
        });
    }

    private void showRows() {
        adapter.setRows(InventoryRows.build(items, column, ascending, Locale.getDefault()));
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void openDetail(InventoryItem item) {
        startActivity(ProductDetailActivity.intentFor(this, item));
    }

    // ---- sorting -------------------------------------------------------------------------

    private void bindHeader(SortColumn headerColumn, int viewId) {
        TextView view = findViewById(viewId);
        headers.put(headerColumn, view);
        view.setOnClickListener(v -> sortBy(headerColumn));
    }

    private void sortBy(SortColumn newColumn) {
        if (newColumn == column) {
            ascending = !ascending;
        } else {
            column = newColumn;
            ascending = true;
        }
        prefs.edit()
                .putString(KEY_COLUMN, column.name())
                .putBoolean(KEY_ASCENDING, ascending)
                .apply();
        updateHeaders();
        showRows();
    }

    private void updateHeaders() {
        for (Map.Entry<SortColumn, TextView> entry : headers.entrySet()) {
            String label = getString(entry.getKey().labelRes);
            if (entry.getKey() == column) {
                label = getString(ascending ? R.string.sort_ascending : R.string.sort_descending, label);
            }
            entry.getValue().setText(label);
        }
    }

    private SortColumn readColumn() {
        String saved = prefs.getString(KEY_COLUMN, SortColumn.NAME.name());
        try {
            return SortColumn.valueOf(saved);
        } catch (IllegalArgumentException e) {
            return SortColumn.NAME;   // a column name from an older version of the app
        }
    }
}