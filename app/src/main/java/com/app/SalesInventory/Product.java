package com.app.SalesInventory;

public class Product {
    private long localId;
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
    private String productType;
    private long expiryDate;

    public Product() {
        this.productType = "Raw";
        this.expiryDate = 0L;
    }

    public Product(long localId, String productId, String productName, String categoryId, String categoryName, String description, double costPrice, double sellingPrice, int quantity, int reorderLevel, int criticalLevel, int ceilingLevel, String unit, String barcode, String supplier, long dateAdded, String addedBy, boolean isActive) {
        this.localId = localId;
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
        this.productType = "Raw";
        this.expiryDate = 0L;
    }

    public long getLocalId() {
        return localId;
    }

    public void setLocalId(long localId) {
        this.localId = localId;
    }

    public String getProductId() {
        return productId == null ? "" : productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName == null ? "" : productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getCategoryId() {
        return categoryId == null ? "" : categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName == null ? "" : categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getDescription() {
        return description == null ? "" : description;
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
        this.quantity = Math.max(0, quantity);
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
        return unit == null ? "" : unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getBarcode() {
        return barcode == null ? "" : barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getSupplier() {
        return supplier == null ? "" : supplier;
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
        return addedBy == null ? "" : addedBy;
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

    public String getProductType() {
        return productType == null || productType.isEmpty() ? "Raw" : productType;
    }

    public void setProductType(String productType) {
        this.productType = productType;
    }

    public long getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(long expiryDate) {
        this.expiryDate = expiryDate;
    }

    public boolean isCriticalStock() {
        return quantity <= reorderLevel;
    }

    public boolean isLowStock() {
        return quantity > reorderLevel && quantity <= Math.max(reorderLevel * 2, reorderLevel + 1);
    }

    public boolean isOverstock() {
        if (ceilingLevel <= 0) {
            return false;
        }
        return quantity > ceilingLevel;
    }
}