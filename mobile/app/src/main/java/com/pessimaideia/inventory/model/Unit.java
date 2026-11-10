package com.pessimaideia.inventory.model;
import com.google.gson.annotations.SerializedName;

public enum Unit {
    @SerializedName("un") UN("un"),
    @SerializedName("kg") KG("kg"),
    @SerializedName("g")  G("g"),
    @SerializedName("L")  L("L"),
    @SerializedName("mL") ML("mL");

    public final String symbol;

    Unit(String symbol) {
        this.symbol = symbol;
    }
}