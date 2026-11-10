package com.pessimaideia.inventory.model;

public class SessionItem {

    public final Product product;
    /** Not final: the user can change it with +/- or by re-scanning. */
    public int packages;

    public SessionItem(Product product, int packages) {
        this.product = product;
        this.packages = packages;
    }

    public double amount() {
        return packages * product.packageSize;
    }
}