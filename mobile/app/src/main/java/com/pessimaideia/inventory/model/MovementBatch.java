package com.pessimaideia.inventory.model;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Body of POST /movements. */
public class MovementBatch {

    public static class Item {
        public final long productId;
        public final int packages;
        public final double amount;

        public Item(long productId, int packages, double amount) {
            this.productId = productId;
            this.packages = packages;
            this.amount = amount;
        }
    }

    public final Movement.Type type;
    public final String at;
    public final List<Item> items;

    public MovementBatch(Movement.Type type, String at, List<Item> items) {
        this.type = type;
        this.at = at;
        this.items = items;
    }

    public static MovementBatch entryFrom(List<SessionItem> session, Date now) {
        List<Item> items = new ArrayList<>();
        for (SessionItem sessionItem : session) {
            items.add(new Item(sessionItem.product.id, sessionItem.packages, sessionItem.amount()));
        }
        return new MovementBatch(Movement.Type.IN, isoUtc(now), items);
    }

    static String isoUtc(Date date) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(date);
    }
}