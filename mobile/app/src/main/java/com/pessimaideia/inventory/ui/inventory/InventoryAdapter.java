package com.pessimaideia.inventory.ui.inventory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.model.Category;
import com.pessimaideia.inventory.model.InventoryItem;
import com.pessimaideia.inventory.ui.Formats;
import com.pessimaideia.inventory.ui.inventory.InventoryRows.Row;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InventoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface Listener {
        void onItemClick(InventoryItem item);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ITEM = 1;

    private final Listener listener;
    private List<Row> rows = new ArrayList<>();

    public InventoryAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setRows(List<Row> rows) {
        this.rows = rows;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position).isHeader() ? TYPE_HEADER : TYPE_ITEM;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_inventory_header, parent, false));
        }
        return new ItemHolder(inflater.inflate(R.layout.item_inventory, parent, false), listener);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Row row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).bind(row.header);
        } else {
            ((ItemHolder) holder).bind(row.item);
        }
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        private final TextView title;

        HeaderHolder(View view) {
            super(view);
            title = view.findViewById(R.id.title);
        }

        void bind(Category category) {
            title.setText(category.labelRes);
        }
    }

    static class ItemHolder extends RecyclerView.ViewHolder {
        private final TextView name;
        private final TextView packages;
        private final TextView amount;
        private final TextView updated;
        private InventoryItem item;

        ItemHolder(View view, Listener listener) {
            super(view);
            view.setOnClickListener(v -> listener.onItemClick(item));
            name = view.findViewById(R.id.name);
            packages = view.findViewById(R.id.packages);
            amount = view.findViewById(R.id.amount);
            updated = view.findViewById(R.id.updated);
        }

        void bind(InventoryItem item) {
            this.item = item;
            Locale locale = Locale.getDefault();
            name.setText(item.product.name);
            packages.setText(String.valueOf(item.packages));
            amount.setText(Formats.amount(item.amount, item.product.unit, locale));
            Date date = Formats.parseIso(item.updatedAt);
            updated.setText(date != null ? Formats.shortDate(date, locale) : "");
        }
    }
}
