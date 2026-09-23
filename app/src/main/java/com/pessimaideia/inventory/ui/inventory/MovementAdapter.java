package com.pessimaideia.inventory.ui.inventory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.model.Movement;
import com.pessimaideia.inventory.model.Unit;
import com.pessimaideia.inventory.ui.Formats;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MovementAdapter extends RecyclerView.Adapter<MovementAdapter.ViewHolder> {
    private final Unit unit;
    private List<Movement> movements = new ArrayList<>();

    public MovementAdapter(Unit unit) {
        this.unit = unit;
    }

    public void setMovements(List<Movement> movements) {
        this.movements = movements;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return movements.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View row = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_movement, parent, false);
        return new ViewHolder(row);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(movements.get(position), unit);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView when;
        private final TextView type;
        private final TextView amount;

        ViewHolder(View row) {
            super(row);
            when = row.findViewById(R.id.when);
            type = row.findViewById(R.id.type);
            amount = row.findViewById(R.id.amount);
        }

        void bind(Movement movement, Unit unit) {
            Locale locale = Locale.getDefault();
            Date date = Formats.parseIso(movement.at);
            when.setText(date != null ? Formats.shortDateTime(date, locale) : movement.at);

            boolean entry = movement.type == Movement.Type.IN;
            type.setText(itemView.getResources().getQuantityString(
                    entry ? R.plurals.movement_in : R.plurals.movement_out,
                    movement.packages, movement.packages));
            amount.setText(Formats.signedAmount(movement.amount, movement.type, unit, locale));
            int color = ContextCompat.getColor(itemView.getContext(),
                    entry ? R.color.movement_in : R.color.movement_out);
            amount.setTextColor(color);
            type.setTextColor(color);
            type.setBackgroundResource(entry ? R.drawable.bg_pill_in : R.drawable.bg_pill_out);
        }
    }
}
