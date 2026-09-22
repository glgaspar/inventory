package com.pessimaideia.inventory.model;
import com.google.gson.annotations.SerializedName;
import com.pessimaideia.inventory.R;

public enum Category {
    @SerializedName("kitchen")  KITCHEN(R.string.category_kitchen),
    @SerializedName("cleaning") CLEANING(R.string.category_cleaning),
    @SerializedName("bathroom") BATHROOM(R.string.category_bathroom),
    @SerializedName("other")    OTHER(R.string.category_other);

    public final int labelRes;

    Category(int labelRes) {
        this.labelRes = labelRes;
    }
}