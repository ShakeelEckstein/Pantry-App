package com.example.pantryapp;

public class PantryItem {

    private long id;
    private String name;           // display: "Onions"
    private String canonicalName;  // matching key: "onion"
    private double quantity;
    private String unit;           // "g", "ml", "pcs"
    private String category;       // optional: "produce", "dairy"...
    private long expiryDate;       // epoch millis; 0 = no expiry

    /** Quick-add from the pantry screen; canonical name is filled in by the helper. */
    public PantryItem(String name, double quantity, String unit) {
        this(0, name, null, quantity, unit, null, 0);
    }

    public PantryItem(long id, String name, String canonicalName, double quantity,
                      String unit, String category, long expiryDate) {
        this.id = id;
        this.name = name;
        this.canonicalName = canonicalName;
        this.quantity = quantity;
        this.unit = unit;
        this.category = category;
        this.expiryDate = expiryDate;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCanonicalName() { return canonicalName; }
    public void setCanonicalName(String canonicalName) { this.canonicalName = canonicalName; }

    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) { this.quantity = quantity; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public long getExpiryDate() { return expiryDate; }
    public void setExpiryDate(long expiryDate) { this.expiryDate = expiryDate; }

    public boolean hasExpiry() { return expiryDate > 0; }
}