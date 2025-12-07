package com.app.SalesInventory;

import java.util.Locale;

public class StockValueReport {
    private String productId;
    private String productName;
    private String category;
    private int quantity;
    private double costPrice;
    private double sellingPrice;
    private int reorderLevel;
    private int criticalLevel;
    private int ceilingLevel;
    private int floorLevel;

    public StockValueReport(String productId, String productName, String category, int quantity, double costPrice, double sellingPrice, int reorderLevel, int criticalLevel, int ceilingLevel, int floorLevel) {
        this.productId = productId;
        this.productName = productName;
        this.category = category;
        this.quantity = quantity;
        this.costPrice = costPrice;
        this.sellingPrice = sellingPrice;
        this.reorderLevel = reorderLevel;
        this.criticalLevel = criticalLevel;
        this.ceilingLevel = ceilingLevel;
        this.floorLevel = floorLevel;
    }

    public String getProductId() {
        return productId == null ? "" : productId;
    }

    public String getProductName() {
        return productName == null ? "" : productName;
    }

    public String getCategory() {
        return category == null ? "" : category;
    }

    public int getQuantity() {
        return quantity;
    }

    public double getCostPrice() {
        return costPrice;
    }

    public double getSellingPrice() {
        return sellingPrice;
    }

    public int getReorderLevel() {
        return reorderLevel;
    }

    public int getCriticalLevel() {
        return criticalLevel;
    }

    public int getCeilingLevel() {
        return ceilingLevel;
    }

    public int getFloorLevel() {
        return floorLevel;
    }

    public double getTotalCostValue() {
        return costPrice * quantity;
    }

    public double getTotalSellingValue() {
        return sellingPrice * quantity;
    }

    public double getProfit() {
        return getTotalSellingValue() - getTotalCostValue();
    }

    public String getProfitMargin() {
        double sell = getTotalSellingValue();
        if (sell <= 0) return "0.00%";
        double margin = (getProfit() / sell) * 100.0;
        return String.format(Locale.getDefault(), "%.2f%%", margin);
    }

    public String getStockStatus() {
        if (criticalLevel > 0 && quantity <= criticalLevel) {
            return "CRITICAL";
        }
        if (reorderLevel > 0 && quantity <= reorderLevel) {
            return "LOW";
        }
        if (ceilingLevel > 0 && quantity > ceilingLevel) {
            return "OVERSTOCK";
        }
        return "NORMAL";
    }
}