package com.pessimaideia.inventory.ui.inventory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.pessimaideia.inventory.model.Category;
import com.pessimaideia.inventory.model.InventoryItem;
import com.pessimaideia.inventory.model.Product;
import com.pessimaideia.inventory.model.Unit;
import com.pessimaideia.inventory.ui.inventory.InventoryRows.Row;
import com.pessimaideia.inventory.ui.inventory.InventoryRows.SortColumn;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class InventoryRowsTest {

    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");

    private static InventoryItem item(String name, Category category, int packages, String updatedAt) {
        Product product = new Product(0, "", name, category, Unit.UN, 1, null);
        return new InventoryItem(product, packages, packages, updatedAt);
    }

    /** The product names of the non-header rows, in order. */
    private static List<String> names(List<Row> rows) {
        List<String> names = new ArrayList<>();
        for (Row row : rows) {
            if (!row.isHeader()) names.add(row.item.product.name);
        }
        return names;
    }

    @Test
    public void groupsByCategoryInEnumOrderWithOneHeaderEach() {
        List<Row> rows = InventoryRows.build(Arrays.asList(
                        item("Detergente", Category.CLEANING, 1, "2026-09-01T00:00:00Z"),
                        item("Arroz", Category.KITCHEN, 1, "2026-09-01T00:00:00Z"),
                        item("Café", Category.KITCHEN, 1, "2026-09-01T00:00:00Z")),
                SortColumn.NAME, true, PT_BR);

        assertEquals(5, rows.size());
        assertEquals(Category.KITCHEN, rows.get(0).header);
        assertEquals("Arroz", rows.get(1).item.product.name);
        assertEquals("Café", rows.get(2).item.product.name);
        assertEquals(Category.CLEANING, rows.get(3).header);
        assertEquals("Detergente", rows.get(4).item.product.name);
    }

    @Test
    public void emptyCategoriesHaveNoHeader() {
        List<Row> rows = InventoryRows.build(Arrays.asList(
                        item("Sabonete", Category.BATHROOM, 1, "2026-09-01T00:00:00Z")),
                SortColumn.NAME, true, PT_BR);

        assertEquals(2, rows.size());
        assertTrue(rows.get(0).isHeader());
        assertEquals(Category.BATHROOM, rows.get(0).header);
    }

    @Test
    public void nameSortIgnoresAccentsAndCase() {
        List<Row> rows = InventoryRows.build(Arrays.asList(
                        item("Sabão", Category.KITCHEN, 1, "2026-09-01T00:00:00Z"),
                        item("Óleo", Category.KITCHEN, 1, "2026-09-01T00:00:00Z"),
                        item("feijão", Category.KITCHEN, 1, "2026-09-01T00:00:00Z"),
                        item("Arroz", Category.KITCHEN, 1, "2026-09-01T00:00:00Z")),
                SortColumn.NAME, true, PT_BR);

        assertEquals(Arrays.asList("Arroz", "feijão", "Óleo", "Sabão"), names(rows));
    }

    @Test
    public void descendingReversesTheOrder() {
        List<Row> rows = InventoryRows.build(Arrays.asList(
                        item("A", Category.KITCHEN, 1, "2026-09-01T00:00:00Z"),
                        item("B", Category.KITCHEN, 3, "2026-09-01T00:00:00Z"),
                        item("C", Category.KITCHEN, 2, "2026-09-01T00:00:00Z")),
                SortColumn.PACKAGES, false, PT_BR);

        assertEquals(Arrays.asList("B", "C", "A"), names(rows));
    }

    @Test
    public void updatedSortsByTimeAndTiesFallBackToName() {
        List<Row> rows = InventoryRows.build(Arrays.asList(
                        item("Z", Category.KITCHEN, 1, "2026-09-10T08:00:00Z"),
                        item("B", Category.KITCHEN, 1, "2026-09-02T00:00:00Z"),
                        item("A", Category.KITCHEN, 1, "2026-09-02T00:00:00Z")),
                SortColumn.UPDATED, true, PT_BR);

        assertEquals(Arrays.asList("A", "B", "Z"), names(rows));
    }
}