package com.example.pantryapp; // Make sure this matches your actual package name

public class Ingredient {

    private long id;
    private long recipeId;
    private String name;            // Display text: "Onion, diced"
    private String canonicalName;   // Matching key: "onion"
    private double quantity;
    private String unit;

    /** Used for user-entered ingredients; canonical name is resolved on insert. */
    public Ingredient(String name, double quantity, String unit) {
        this(0, 0, name, null, quantity, unit);
    }

    /** Used by the seed data. */
    public Ingredient(String name, String canonicalName, double quantity, String unit) {
        this(0, 0, name, canonicalName, quantity, unit);
    }

    /** Used by the seed data and when loading from the database. */
    public Ingredient(long id, long recipeId, String name, String canonicalName, double quantity, String unit) {
        this.id = id;
        this.recipeId = recipeId;
        this.name = name;
        this.canonicalName = canonicalName;
        this.quantity = quantity;
        this.unit = unit;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getRecipeId() { return recipeId; }
    public void setRecipeId(long recipeId) { this.recipeId = recipeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCanonicalName() { return canonicalName; }
    public void setCanonicalName(String canonicalName) { this.canonicalName = canonicalName; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    /** e.g. "2 pcs" — handy for list rows. */
    public String getDisplayQuantity() {
        String q = (quantity == Math.floor(quantity))
                ? String.valueOf((long) quantity)
                : String.valueOf(quantity);
        return unit == null || unit.isEmpty() ? q : q + " " + unit;
    }
}