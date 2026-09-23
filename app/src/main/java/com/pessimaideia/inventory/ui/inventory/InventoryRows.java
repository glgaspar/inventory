package com.pessimaideia.inventory.ui.inventory;

import androidx.annotation.Nullable;

import com.pessimaideia.inventory.R;
import com.pessimaideia.inventory.model.Category;
import com.pessimaideia.inventory.model.InventoryItem;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class InventoryRows {

    public enum SortColumn {
        NAME(R.string.col_name),
        PACKAGES(R.string.col_packages),
        AMOUNT(R.string.col_amount),
        UPDATED(R.string.col_updated);
        public final int labelRes;
        SortColumn(int labelRes) {
            this.labelRes = labelRes;
        }
    }

    public static final class Row {
        @Nullable public final Category header;
        @Nullable public final InventoryItem item;

        private Row(@Nullable Category header, @Nullable InventoryItem item) {
            this.header = header;
            this.item = item;
        }

        public boolean isHeader() {
            return header != null;
        }
    }

    private InventoryRows() {
    }

    public static List<Row> build(List<InventoryItem> items, SortColumn column, boolean ascending, Locale locale) {
        Map<Category, List<InventoryItem>> byCategory = new EnumMap<>(Category.class);
        for (InventoryItem item : items) {
            List<InventoryItem> group = byCategory.get(item.product.category);
            if (group == null) {
                group = new ArrayList<>();
                byCategory.put(item.product.category, group);
            }
            group.add(item);
        }

        Comparator<InventoryItem> order = comparator(column, locale);
        if (!ascending) {
            order = Collections.reverseOrder(order);
        }

        List<Row> rows = new ArrayList<>();
        for (Map.Entry<Category, List<InventoryItem>> entry : byCategory.entrySet()) {
            List<InventoryItem> group = entry.getValue();
            Collections.sort(group, order);
            rows.add(new Row(entry.getKey(), null));
            for (InventoryItem item : group) {
                rows.add(new Row(null, item));
            }
        }
        return rows;
    }

    static Comparator<InventoryItem> comparator(SortColumn column, Locale locale) {
        Collator collator = Collator.getInstance(locale);
        collator.setStrength(Collator.PRIMARY);   // ignore accents and case: "Óleo" sorts with "oleo"
        return (a, b) -> {
            int result;
            switch (column) {
                case PACKAGES:
                    result = Integer.compare(a.packages, b.packages);
                    break;
                case AMOUNT:
                    result = Double.compare(a.amount, b.amount);
                    break;
                case UPDATED:
                    // Same ISO-8601 UTC format everywhere, so text order is time order.
                    result = a.updatedAt.compareTo(b.updatedAt);
                    break;
                default:
                    result = 0;
                    break;
            }
            return result != 0 ? result : collator.compare(a.product.name, b.product.name);
        };
    }
}