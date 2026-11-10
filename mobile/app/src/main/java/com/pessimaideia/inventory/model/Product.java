package com.pessimaideia.inventory.model;
import androidx.annotation.Nullable;

public class Product {

    /** 0 means "not saved in the backend yet". */
    public final long id;
    public final String barcode;
    public final String name;
    public final Category category;
    public final Unit unit;
    /** How much of {@link #unit} one scanned package holds, e.g. rice 5 kg → 5. */
    public final double packageSize;
    @Nullable
    public final String imageUrl;

    public Product(long id, String barcode, String name, Category category,
                   Unit unit, double packageSize, @Nullable String imageUrl) {
        this.id = id;
        this.barcode = barcode;
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.packageSize = packageSize;
        this.imageUrl = imageUrl;
    }
}