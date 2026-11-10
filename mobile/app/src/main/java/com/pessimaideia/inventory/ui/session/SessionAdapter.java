package com.pessimaideia.inventory.ui.session;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.model.SessionItem;
import com.pessimaideia.inventory.ui.Formats;
import java.util.List;
import java.util.Locale;

public class SessionAdapter extends RecyclerView.Adapter<SessionAdapter.ViewHolder> {

    public interface Listener {
        void onChangePackages(int position, int delta);
        void onRemove(int position);
    }

    private final List<SessionItem> items;
    private final Listener listener;

    public SessionAdapter(List<SessionItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_session, parent, false);
        return new ViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    class ViewHolder extends RecyclerView.ViewHolder {

        private final TextView name;
        private final TextView amount;
        private final TextView packages;
        private final View minus;

        ViewHolder(View row) {
            super(row);
            name = row.findViewById(R.id.name);
            amount = row.findViewById(R.id.amount);
            packages = row.findViewById(R.id.packages);
            minus = row.findViewById(R.id.minus);

            minus.setOnClickListener(v -> changePackages(-1));
            row.findViewById(R.id.plus).setOnClickListener(v -> changePackages(+1));
            row.findViewById(R.id.remove).setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) listener.onRemove(position);
            });
        }

        void bind(SessionItem item) {
            Context context = itemView.getContext();
            Locale locale = Locale.getDefault();
            name.setText(item.product.name);
            amount.setText(context.getString(R.string.session_row_amount,
                    item.packages,
                    Formats.amount(item.product.packageSize, item.product.unit, locale),
                    Formats.amount(item.amount(), item.product.unit, locale)));
            packages.setText(String.valueOf(item.packages));
            minus.setEnabled(item.packages > 1);
        }

        private void changePackages(int delta) {
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) listener.onChangePackages(position, delta);
        }
    }
}