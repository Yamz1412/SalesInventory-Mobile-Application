package com.app.SalesInventory;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity(tableName = "products")
public class ProductEntity {
    @PrimaryKey(autoGenerate = true)
    public long localId;
    public String productId;
    public String productName;
    public String categoryId;
    public String categoryName;
    public String description;
    public double costPrice;
    public double sellingPrice;
    public int quantity;
    public int reorderLevel;
    public int criticalLevel;
    public int ceilingLevel;
    public String unit;
    public String barcode;
    public String supplier;
    public long dateAdded;
    public String addedBy;
    public boolean isActive;
    public long lastUpdated;
    public String syncState;

    public ProductEntity() {}

    @Ignore
    public ProductEntity(String productName, String categoryId, String categoryName, String description, double costPrice, double sellingPrice, int quantity, int reorderLevel, int criticalLevel, int ceilingLevel, String unit, String barcode, String supplier, long dateAdded, String addedBy, boolean isActive, long lastUpdated, String syncState) {
        this.productName = productName;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.description = description;
        this.costPrice = costPrice;
        this.sellingPrice = sellingPrice;
        this.quantity = quantity;
        this.reorderLevel = reorderLevel;
        this.criticalLevel = criticalLevel;
        this.ceilingLevel = ceilingLevel;
        this.unit = unit;
        this.barcode = barcode;
        this.supplier = supplier;
        this.dateAdded = dateAdded;
        this.addedBy = addedBy;
        this.isActive = isActive;
        this.lastUpdated = lastUpdated;
        this.syncState = syncState;
    }
}