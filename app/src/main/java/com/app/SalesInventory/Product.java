package com.app.SalesInventory;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

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
    private int floorLevel;
    private String unit;
    private String barcode;
    private String supplier;
    private long dateAdded;
    private String addedBy;
    private boolean isActive;
    private String productType;
    private long expiryDate;
    private String imagePath;
    private String imageUrl;

    public Product() {
        this.productType = "Raw";
        this.expiryDate = 0L;
        this.floorLevel = 0;
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
        this.floorLevel = 0;
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

    public int getFloorLevel() {
        return floorLevel;
    }

    public void setFloorLevel(int floorLevel) {
        this.floorLevel = Math.max(0, floorLevel);
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

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
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

    public boolean isBelowFloor() {
        return floorLevel > 0 && quantity <= floorLevel;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("localId", localId);
        m.put("productId", productId);
        m.put("productName", productName);
        m.put("categoryId", categoryId);
        m.put("categoryName", categoryName);
        m.put("description", description);
        m.put("costPrice", costPrice);
        m.put("sellingPrice", sellingPrice);
        m.put("quantity", quantity);
        m.put("reorderLevel", reorderLevel);
        m.put("criticalLevel", criticalLevel);
        m.put("ceilingLevel", ceilingLevel);
        m.put("floorLevel", floorLevel);
        m.put("unit", unit);
        m.put("barcode", barcode);
        m.put("supplier", supplier);
        m.put("dateAdded", dateAdded);
        m.put("addedBy", addedBy);
        m.put("isActive", isActive);
        m.put("productType", productType);
        m.put("expiryDate", expiryDate);
        m.put("imagePath", imagePath);
        m.put("imageUrl", imageUrl);
        return m;
    }

    public static Product fromMap(Map<String, Object> m) {
        Product p = new Product();
        if (m == null) return p;
        Object o;
        o = m.get("localId");
        if (o instanceof Number) p.localId = ((Number) o).longValue();
        o = m.get("productId");
        if (o != null) p.productId = String.valueOf(o);
        o = m.get("productName");
        if (o != null) p.productName = String.valueOf(o);
        o = m.get("categoryId");
        if (o != null) p.categoryId = String.valueOf(o);
        o = m.get("categoryName");
        if (o != null) p.categoryName = String.valueOf(o);
        o = m.get("description");
        if (o != null) p.description = String.valueOf(o);
        o = m.get("costPrice");
        if (o instanceof Number) p.costPrice = ((Number) o).doubleValue();
        else if (o instanceof String) {
            try { p.costPrice = Double.parseDouble(((String) o).replaceAll("[^\\d.-]", "")); } catch (Exception ignored) {}
        }
        o = m.get("sellingPrice");
        if (o instanceof Number) p.sellingPrice = ((Number) o).doubleValue();
        else if (o instanceof String) {
            try { p.sellingPrice = Double.parseDouble(((String) o).replaceAll("[^\\d.-]", "")); } catch (Exception ignored) {}
        }
        o = m.get("quantity");
        if (o instanceof Number) p.quantity = ((Number) o).intValue();
        else if (o instanceof String) {
            try { p.quantity = Integer.parseInt((String) o); } catch (Exception ignored) {}
        }
        o = m.get("reorderLevel");
        if (o instanceof Number) p.reorderLevel = ((Number) o).intValue();
        o = m.get("criticalLevel");
        if (o instanceof Number) p.criticalLevel = ((Number) o).intValue();
        o = m.get("ceilingLevel");
        if (o instanceof Number) p.ceilingLevel = ((Number) o).intValue();
        o = m.get("floorLevel");
        if (o instanceof Number) p.floorLevel = ((Number) o).intValue();
        o = m.get("unit");
        if (o != null) p.unit = String.valueOf(o);
        o = m.get("barcode");
        if (o != null) p.barcode = String.valueOf(o);
        o = m.get("supplier");
        if (o != null) p.supplier = String.valueOf(o);
        o = m.get("dateAdded");
        if (o instanceof Number) p.dateAdded = ((Number) o).longValue();
        o = m.get("addedBy");
        if (o != null) p.addedBy = String.valueOf(o);
        o = m.get("isActive");
        if (o instanceof Boolean) p.isActive = (Boolean) o;
        else if (o instanceof Number) p.isActive = ((Number) o).intValue() != 0;
        o = m.get("productType");
        if (o != null) p.productType = String.valueOf(o);
        o = m.get("expiryDate");
        if (o instanceof Number) p.expiryDate = ((Number) o).longValue();
        o = m.get("imagePath");
        if (o != null) p.imagePath = String.valueOf(o);
        o = m.get("imageUrl");
        if (o != null) p.imageUrl = String.valueOf(o);
        return p;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Product product = (Product) o;
        return Objects.equals(getProductId(), product.getProductId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getProductId());
    }

    @Override
    public String toString() {
        return "Product{" +
                "productId='" + getProductId() + '\'' +
                ", productName='" + getProductName() + '\'' +
                ", quantity=" + quantity +
                '}';
    }
}