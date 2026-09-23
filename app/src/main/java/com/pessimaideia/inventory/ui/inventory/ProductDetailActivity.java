package com.pessimaideia.inventory.ui.inventory;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.snackbar.Snackbar;
import com.google.gson.Gson;
import com.pessimaideia.inventory.App;
import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.api.ApiCallback;
import com.pessimaideia.inventory.api.ApiException;
import com.pessimaideia.inventory.model.InventoryItem;
import com.pessimaideia.inventory.model.Movement;
import com.pessimaideia.inventory.model.Product;
import com.pessimaideia.inventory.ui.Formats;

import java.util.List;
import java.util.Locale;

public class ProductDetailActivity extends AppCompatActivity {
    private static final String EXTRA_ITEM = "item";

    public static Intent intentFor(Context context, InventoryItem item) {
        return new Intent(context, ProductDetailActivity.class)
                .putExtra(EXTRA_ITEM, new Gson().toJson(item));
    }

    private InventoryItem item;
    private MovementAdapter adapter;

    private RecyclerView list;
    private ProgressBar progress;
    private TextView empty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        item = new Gson().fromJson(getIntent().getStringExtra(EXTRA_ITEM), InventoryItem.class);
        if (item == null) {
            finish();
            return;
        }
        Product product = item.product;
        setTitle(product.name);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        showHeader(product);

        list = findViewById(R.id.list);
        progress = findViewById(R.id.progress);
        empty = findViewById(R.id.empty);
        adapter = new MovementAdapter(product.unit);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.addItemDecoration(new DividerItemDecoration(this, DividerItemDecoration.VERTICAL));
        list.setAdapter(adapter);

        load();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void showHeader(Product product) {
        ImageView image = findViewById(R.id.image);
        TextView name = findViewById(R.id.name);
        TextView category = findViewById(R.id.category);
        TextView stock = findViewById(R.id.stock);

        Glide.with(this)
                .load(product.imageUrl)
                .placeholder(android.R.drawable.ic_menu_gallery)
                .fallback(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image)
                .into(image);

        name.setText(product.name);
        category.setText(product.category.labelRes);
        String amount = Formats.amount(item.amount, product.unit, Locale.getDefault());
        stock.setText(getResources().getQuantityString(
                R.plurals.detail_stock, item.packages, amount, item.packages));
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        App.from(this).api().getMovements(item.product.id, new ApiCallback<List<Movement>>() {
            @Override
            public void onSuccess(List<Movement> movements) {
                if (isDestroyed()) return;
                progress.setVisibility(View.INVISIBLE);
                adapter.setMovements(movements);
                empty.setVisibility(movements.isEmpty() ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(ApiException error) {
                if (isDestroyed()) return;
                progress.setVisibility(View.INVISIBLE);
                Snackbar.make(list, getString(R.string.detail_failed, error.getMessage()),
                                Snackbar.LENGTH_INDEFINITE)
                        .setAction(R.string.retry, v -> load())
                        .show();
            }
        });
    }
}
