package com.pessimaideia.inventory.model;

public class InventoryItem {
    public final Product product;
    public final double amount;
    public final int packages;
    public final String updatedAt;

    public InventoryItem(Product product, double amount, int packages, String updatedAt) {
        this.product = product;
        this.amount = amount;
        this.packages = packages;
        this.updatedAt = updatedAt;
    }
}