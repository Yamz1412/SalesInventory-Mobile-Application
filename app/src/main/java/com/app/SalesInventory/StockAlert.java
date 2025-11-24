package com.app.SalesInventory;

public class StockAlert {
    private String alertId;
    private String productId;
    private String productName;
    private int currentQuantity;
    private int reorderLevel;
    private int criticalLevel;
    private int ceilingLevel;
    private String alertType; // "CRITICAL", "LOW", "OVERSTOCK"
    private String category;
    private long createdAt;
    private boolean isResolved;

    public StockAlert() {
    }

    public StockAlert(String alertId, String productId, String productName, int currentQuantity,
                      int reorderLevel, int criticalLevel, int ceilingLevel, String alertType,
                      String category, long createdAt, boolean isResolved) {
        this.alertId = alertId;
        this.productId = productId;
        this.productName = productName;
        this.currentQuantity = currentQuantity;
        this.reorderLevel = reorderLevel;
        this.criticalLevel = criticalLevel;
        this.ceilingLevel = ceilingLevel;
        this.alertType = alertType;
        this.category = category;
        this.createdAt = createdAt;
        this.isResolved = isResolved;
    }

    public String getAlertId() {
        return alertId;
    }

    public void setAlertId(String alertId) {
        this.alertId = alertId;
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

    public int getCurrentQuantity() {
        return currentQuantity;
    }

    public void setCurrentQuantity(int currentQuantity) {
        this.currentQuantity = currentQuantity;
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

    public String getAlertType() {
        return alertType;
    }

    public void setAlertType(String alertType) {
        this.alertType = alertType;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isResolved() {
        return isResolved;
    }

    public void setResolved(boolean resolved) {
        isResolved = resolved;
    }

    public int getSeverity() {
        if ("CRITICAL".equals(alertType)) {
            return 3; // Highest
        } else if ("LOW".equals(alertType)) {
            return 2;
        } else if ("OVERSTOCK".equals(alertType)) {
            return 1; // Lowest
        }
        return 0;
    }

    public String getAlertMessage() {
        switch (alertType) {
            case "CRITICAL":
                return "CRITICAL: Only " + currentQuantity + " units left! Urgent reorder needed.";
            case "LOW":
                return "LOW STOCK: " + currentQuantity + " units remaining. Reorder soon.";
            case "OVERSTOCK":
                return "OVERSTOCK: " + currentQuantity + " units exceed ceiling level of " + ceilingLevel;
            default:
                return "Stock alert";
        }
    }
}