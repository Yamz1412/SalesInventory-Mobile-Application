package com.app.SalesInventory;

public class Product {
    private String productId;
    private String productName;
    private String categoryId;
    private String categoryName;
    private String description;
    private double costPrice;
    private double sellingPrice;
    private int quantity;
    private int reorderLevel;
    private int criticalLevel;
    private int ceilingLevel;
    private String unit;
    private String barcode;
    private String supplier;
    private long dateAdded;
    private String addedBy;
    private boolean isActive;

    public Product() {
    }

    public Product(String productId, String productName, String categoryId, String categoryName,
                   String description, double costPrice, double sellingPrice, int quantity,
                   int reorderLevel, int criticalLevel, int ceilingLevel, String unit,
                   String barcode, String supplier, long dateAdded, String addedBy, boolean isActive) {
        this.productId = productId;
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
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public double getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(double costPrice) {
        this.costPrice = costPrice;
    }

    public double getSellingPrice() {
        return sellingPrice;
    }

    public void setSellingPrice(double sellingPrice) {
        this.sellingPrice = sellingPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public int getReorderLevel() {
        return reorderLevel;
    }

    public void setReorderLevel(int reorderLevel) {
        this.reorderLevel = reorderLevel;
    }

    public int getCriticalLevel() {
        return criticalLevel;
    }

    public void setCriticalLevel(int criticalLevel) {
        this.criticalLevel = criticalLevel;
    }

    public int getCeilingLevel() {
        return ceilingLevel;
    }

    public void setCeilingLevel(int ceilingLevel) {
        this.ceilingLevel = ceilingLevel;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public long getDateAdded() {
        return dateAdded;
    }

    public void setDateAdded(long dateAdded) {
        this.dateAdded = dateAdded;
    }

    public String getAddedBy() {
        return addedBy;
    }

    public void setAddedBy(String addedBy) {
        this.addedBy = addedBy;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    public boolean isLowStock() {
        return quantity <= reorderLevel && quantity > criticalLevel;
    }

    public boolean isCriticalStock() {
        return quantity <= criticalLevel;
    }

    public boolean isOverstock() {
        return quantity >= ceilingLevel;
    }
}