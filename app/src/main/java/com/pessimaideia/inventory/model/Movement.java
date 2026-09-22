package com.pessimaideia.inventory.model;
import com.google.gson.annotations.SerializedName;

public class Movement {

    public enum Type {
        @SerializedName("in")  IN,
        @SerializedName("out") OUT
    }

    public final long id;
    public final long productId;
    public final Type type;
    public final double amount;
    public final int packages;
    /** ISO-8601 UTC timestamp exactly as the backend sends it, e.g. 2026-09-22T13:05:00Z. */
    public final String at;

    public Movement(long id, long productId, Type type, double amount, int packages, String at) {
        this.id = id;
        this.productId = productId;
        this.type = type;
        this.amount = amount;
        this.packages = packages;
        this.at = at;
    }
}