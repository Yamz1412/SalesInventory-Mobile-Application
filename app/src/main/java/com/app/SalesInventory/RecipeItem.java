package com.app.SalesInventory;

import java.io.Serializable;

public class RecipeItem implements Serializable {

    private String rawMaterialId;
    private String rawMaterialName;
    private double quantityRequired;
    private String unit;

    // Added fields for Adviser's Revision
    private boolean isEssential;
    private String substitutableGroup;

    public RecipeItem() {
        this.isEssential = true; // Default to true for required ingredients
    }

    public RecipeItem(String rawMaterialId, String rawMaterialName, double quantityRequired, String unit, boolean isEssential, String substitutableGroup) {
        this.rawMaterialId = rawMaterialId;
        this.rawMaterialName = rawMaterialName;
        this.quantityRequired = quantityRequired;
        this.unit = unit;
        this.isEssential = isEssential;
        this.substitutableGroup = substitutableGroup;
    }

    public String getRawMaterialId() {
        return rawMaterialId;
    }

    public void setRawMaterialId(String rawMaterialId) {
        this.rawMaterialId = rawMaterialId;
    }

    public String getRawMaterialName() {
        return rawMaterialName;
    }

    public void setRawMaterialName(String rawMaterialName) {
        this.rawMaterialName = rawMaterialName;
    }

    public double getQuantityRequired() {
        return quantityRequired;
    }

    public void setQuantityRequired(double quantityRequired) {
        this.quantityRequired = quantityRequired;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public boolean isEssential() {
        return isEssential;
    }

    public void setEssential(boolean essential) {
        isEssential = essential;
    }

    public String getSubstitutableGroup() {
        return substitutableGroup;
    }

    public void setSubstitutableGroup(String substitutableGroup) {
        this.substitutableGroup = substitutableGroup;
    }
}