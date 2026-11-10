package com.pessimaideia.inventory.model;

import androidx.annotation.Nullable;

public class Suggestion {
    public final String name;
    @Nullable
    public final Category category;
    @Nullable
    public final Unit unit;
    public final double packageSize;
    @Nullable
    public final String imageUrl;
    public final String source;

    public Suggestion(String name, @Nullable Category category, @Nullable Unit unit, double packageSize,
                      @Nullable String imageUrl, String source) {
        this.name = name;
        this.category = category;
        this.unit = unit;
        this.packageSize = packageSize;
        this.imageUrl = imageUrl;
        this.source = source;
    }
}
