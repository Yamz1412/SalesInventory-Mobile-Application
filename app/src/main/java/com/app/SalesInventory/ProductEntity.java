package com.app.SalesInventory;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "products")
public class ProductEntity {
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
    @ColumnInfo(name = "costPrice")
    public double costPrice;
    @ColumnInfo(name = "sellingPrice")
    public double sellingPrice;
    @ColumnInfo(name = "quantity")
    public int quantity;
    @ColumnInfo(name = "reorderLevel")
    public int reorderLevel;
    @ColumnInfo(name = "criticalLevel")
    public int criticalLevel;
    @ColumnInfo(name = "ceilingLevel")
    public int ceilingLevel;
    @ColumnInfo(name = "floorLevel")
    public int floorLevel;
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
    @ColumnInfo(name = "expiryDate")
    public long expiryDate;
    @ColumnInfo(name = "productType")
    public String productType;

    public ProductEntity() {}
}