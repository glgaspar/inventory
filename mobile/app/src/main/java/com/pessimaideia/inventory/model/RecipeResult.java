package com.pessimaideia.inventory.model;

import java.util.List;

public class RecipeResult {
    public static class Ingredient {
        public final String name;
        public final String amount;
        public final boolean fromStock;

        public Ingredient(String name, String amount, boolean fromStock) {
            this.name = name;
            this.amount = amount;
            this.fromStock = fromStock;
        }
    }

    public static class Recipe {
        public final String title;
        public final String summary;
        public final int minutes;
        public final int servings;
        public final List<Ingredient> ingredients;
        public final List<String> missing;
        public final List<String> steps;

        public Recipe(String title, String summary, int minutes, int servings, List<Ingredient> ingredients,
                      List<String> missing, List<String> steps) {
            this.title = title;
            this.summary = summary;
            this.minutes = minutes;
            this.servings = servings;
            this.ingredients = ingredients;
            this.missing = missing;
            this.steps = steps;
        }
    }

    public final List<Recipe> recipes;
    public final String model;
    public final boolean cached;

    public RecipeResult(List<Recipe> recipes, String model, boolean cached) {
        this.recipes = recipes;
        this.model = model;
        this.cached = cached;
    }
}
