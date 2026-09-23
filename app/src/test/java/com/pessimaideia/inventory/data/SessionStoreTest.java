package com.pessimaideia.inventory.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.pessimaideia.inventory.model.Category;
import com.pessimaideia.inventory.model.Product;
import com.pessimaideia.inventory.model.SessionItem;
import com.pessimaideia.inventory.model.Unit;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

public class SessionStoreTest {

    /** JUnit creates a fresh temp directory per test and deletes it afterwards. */
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private SessionStore store;

    @Before
    public void setUp() {
        store = new SessionStore(temp.getRoot());
    }

    private static SessionItem sampleItem(int packages) {
        Product rice = new Product(1, "7896004000015", "Arroz 5kg",
                Category.KITCHEN, Unit.KG, 5, null);
        return new SessionItem(rice, packages);
    }

    @Test
    public void emptyWhenNothingSaved() {
        assertFalse(store.hasSession());
        assertTrue(store.load().isEmpty());
    }

    @Test
    public void saveThenLoadRoundTrips() {
        List<SessionItem> items = new ArrayList<>();
        items.add(sampleItem(3));
        store.save(items);

        assertTrue(store.hasSession());
        List<SessionItem> loaded = store.load();
        assertEquals(1, loaded.size());
        assertEquals(3, loaded.get(0).packages);
        assertEquals("Arroz 5kg", loaded.get(0).product.name);
        assertEquals(Unit.KG, loaded.get(0).product.unit);
        assertEquals(15.0, loaded.get(0).amount(), 0.0001);
    }

    @Test
    public void savingEmptyListClears() {
        List<SessionItem> items = new ArrayList<>();
        items.add(sampleItem(1));
        store.save(items);
        store.save(new ArrayList<>());

        assertFalse(store.hasSession());
    }

    @Test
    public void corruptFileLoadsAsEmpty() throws IOException {
        File file = new File(temp.getRoot(), "session.json");
        try (Writer w = new FileWriter(file)) {
            w.write("{ this is not json");
        }

        assertTrue(store.load().isEmpty());
    }
}
