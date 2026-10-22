package com.example.pantryapp;

public class Ingredient {
    private long id;
    private long recipeId;
    private String name;
    private double quantity;
    private String unit;

    public Ingredient() {}

    /**Convenience constructor for new ingredients*/
    public Ingredient(String name, double quantity, String unit) {
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
    }

    public Ingredient(long id, long recipeId, String name, double quantity, String unit) {
        this.id = id;
        this.recipeId = recipeId;
        this.name = name;
        this.quantity = quantity;
        this.unit = unit;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public long getRecipeId() { return recipeId; }
    public void setRecipeId(long recipeId) { this.recipeId = recipeId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    /** e.g. "400 g Spaghetti", "1.5 tbsp Olive oil", "2 Eggs" */
    public String toDisplayString() {
        String qty = (quantity == Math.floor(quantity))
                ? String.valueOf((long) quantity)
                : String.valueOf(quantity);
        String u = (unit == null || unit.isEmpty()) ? "" : " " + unit;
        return qty + u + " " + name;
    }
}