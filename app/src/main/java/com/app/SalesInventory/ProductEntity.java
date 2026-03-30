package com.app.SalesInventory;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "products",
        indices = {@Index(value = {"productId"}, unique = true)}
)public class ProductEntity {

    public String sizesListJson;
    public String addonsListJson;
    public String notesListJson;
    public String variantsListJson;
    public String bomListJson;

    @PrimaryKey(autoGenerate = true)
    public long localId;
    @ColumnInfo(name = "productId")
    public String productId;
    @ColumnInfo(name = "productName")
    public String productName;
    @ColumnInfo(name = "categoryId")
    public String categoryId;
    @ColumnInfo(name = "categoryName")
    public String categoryName;
    @ColumnInfo(name = "description")
    public String description;
    @ColumnInfo(name = "productLine")
    public String productLine;
    @ColumnInfo(name = "costPrice")
    public double costPrice;
    @ColumnInfo(name = "sellingPrice")
    public double sellingPrice;

    @ColumnInfo(name = "quantity")
    public double quantity;

    @ColumnInfo(name = "reorderLevel")
    public int reorderLevel;
    @ColumnInfo(name = "criticalLevel")
    public int criticalLevel;
    @ColumnInfo(name = "ceilingLevel")
    public int ceilingLevel;
    @ColumnInfo(name = "floorLevel")
    public int floorLevel;

    // --- NEW: Variables for Automated Reorder Calculation ---
    @ColumnInfo(name = "leadTimeDays")
    public int leadTimeDays;

    // Using double to support fractional safety stock (e.g., 1.5 Liters of milk)
    @ColumnInfo(name = "safetyStock")
    public double safetyStock;

    // We keep this here so Room doesn't throw a schema mismatch error,
    // but we will no longer use it in the UI or logic.
    @ColumnInfo(name = "preOrderLevel")
    public int preOrderLevel;

    @ColumnInfo(name = "unit")
    public String unit;
    @ColumnInfo(name = "barcode")
    public String barcode;
    @ColumnInfo(name = "supplier")
    public String supplier;
    @ColumnInfo(name = "dateAdded")
    public long dateAdded;
    @ColumnInfo(name = "addedBy")
    public String addedBy;
    @ColumnInfo(name = "isActive")
    public boolean isActive;
    @ColumnInfo(name = "lastUpdated")
    public long lastUpdated;
    @ColumnInfo(name = "syncState")
    public String syncState;
    @ColumnInfo(name = "imagePath")
    public String imagePath;

    @ColumnInfo(name = "imageUrl")
    public String imageUrl;
    @ColumnInfo(name = "productType")
    public String productType;
    @ColumnInfo(name = "ownerAdminId")
    public String ownerAdminId;
    @ColumnInfo(name = "expiryDate")
    public long expiryDate;
    @ColumnInfo(name = "piecesPerUnit")
    public int piecesPerUnit = 1;
    @ColumnInfo(name = "salesUnit")
    public String salesUnit;
}