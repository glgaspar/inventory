package com.pessimaideia.inventory.model;
import java.util.List;

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
}