package com.pessimaideia.inventory.model;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

public class MovementBatchTest {

    private static final Product RICE =
            new Product(1, "7896004000015", "Arroz 5kg", Category.KITCHEN, Unit.KG, 5, null);
    private static final Product SOAP =
            new Product(8, "7896004000084", "Sabonete", Category.BATHROOM, Unit.UN, 1, null);

    @Test
    public void entryHasOneItemPerSessionRow() {
        List<SessionItem> session = Arrays.asList(new SessionItem(RICE, 2), new SessionItem(SOAP, 3));

        MovementBatch batch = MovementBatch.entryFrom(session, new Date(0));

        assertEquals(Movement.Type.IN, batch.type);
        assertEquals(2, batch.items.size());
        assertEquals(1, batch.items.get(0).productId);
        assertEquals(2, batch.items.get(0).packages);
        assertEquals(10.0, batch.items.get(0).amount, 0.0);
        assertEquals(8, batch.items.get(1).productId);
        assertEquals(3.0, batch.items.get(1).amount, 0.0);
    }

    @Test
    public void timestampIsUtcIso8601() {
        // Date(0) is the Unix epoch; 90061000 ms later is 1 day, 1 h, 1 min, 1 s.
        assertEquals("1970-01-01T00:00:00Z", MovementBatch.isoUtc(new Date(0)));
        assertEquals("1970-01-02T01:01:01Z", MovementBatch.isoUtc(new Date(90061000L)));
    }
}