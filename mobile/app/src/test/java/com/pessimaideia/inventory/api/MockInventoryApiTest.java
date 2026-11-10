package com.pessimaideia.inventory.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.pessimaideia.inventory.model.Category;
import com.pessimaideia.inventory.model.InventoryItem;
import com.pessimaideia.inventory.model.Movement;
import com.pessimaideia.inventory.model.MovementBatch;
import com.pessimaideia.inventory.model.Product;
import com.pessimaideia.inventory.model.Unit;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MockInventoryApiTest {

    private MockInventoryApi api;

    @Before
    public void setUp() {
        api = MockInventoryApi.synchronous();   // fresh data before every test
    }

    @Test
    public void knownBarcodeReturnsProduct() {
        RecordingCallback<Product> cb = new RecordingCallback<>();
        api.getProductByBarcode("7896004000015", cb);

        assertNull(cb.error);
        assertNotNull(cb.result);
        assertEquals("Arroz 5kg", cb.result.name);
        assertEquals(Unit.KG, cb.result.unit);
    }

    @Test
    public void unknownBarcodeIs404() {
        RecordingCallback<Product> cb = new RecordingCallback<>();
        api.getProductByBarcode("0000000000000", cb);

        assertNull(cb.result);
        assertNotNull(cb.error);
        assertTrue(cb.error.isNotFound());
    }

    @Test
    public void createdProductCanBeFoundByBarcode() {
        Product draft = new Product(0, "1234567890123", "Vinagre 750mL",
                Category.KITCHEN, Unit.ML, 750, null);
        RecordingCallback<Product> created = new RecordingCallback<>();
        api.createProduct(draft, created);

        assertNull(created.error);
        assertTrue(created.result.id > 0);

        RecordingCallback<Product> found = new RecordingCallback<>();
        api.getProductByBarcode("1234567890123", found);
        assertEquals(created.result.id, found.result.id);
    }

    @Test
    public void sendingMovementsUpdatesInventory() {
        List<MovementBatch.Item> items = new ArrayList<>();
        items.add(new MovementBatch.Item(2, 2, 2.0));        // Feijão 1kg × 2
        MovementBatch batch = new MovementBatch(Movement.Type.IN, "2026-09-22T12:00:00Z", items);

        RecordingCallback<Long> sent = new RecordingCallback<>();
        api.sendMovements(batch, sent);
        assertNull(sent.error);

        RecordingCallback<List<InventoryItem>> inv = new RecordingCallback<>();
        api.getInventory(inv);
        InventoryItem feijao = null;
        for (InventoryItem item : inv.result) {
            if (item.product.id == 2) feijao = item;
        }
        assertNotNull(feijao);
        assertEquals(5.0, feijao.amount, 0.0001);            // seeded 3 + 2 sent
        assertEquals(5, feijao.packages);
        assertEquals("2026-09-22T12:00:00Z", feijao.updatedAt);
    }

    @Test
    public void movementsComeNewestFirst() {
        RecordingCallback<List<Movement>> cb = new RecordingCallback<>();
        api.getMovements(1, cb);

        assertEquals(2, cb.result.size());
        assertEquals(Movement.Type.OUT, cb.result.get(0).type);
        assertEquals(Movement.Type.IN, cb.result.get(1).type);
    }
}