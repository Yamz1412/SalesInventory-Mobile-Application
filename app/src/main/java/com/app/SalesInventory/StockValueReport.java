package com.app.SalesInventory;

public class StockValueReport {
    private String productId;
    private String productName;
    private String category;
    private int quantity;
    private double costPrice;
    private double sellingPrice;
    private double totalCostValue;
    private double totalSellingValue;
    private double profit;
    private String profitMargin;
    private int reorderLevel;
    private int criticalLevel;
    private String stockStatus; // CRITICAL, LOW, NORMAL, OVERSTOCK

    public StockValueReport(String productId, String productName, String category,
                            int quantity, double costPrice, double sellingPrice,
                            int reorderLevel, int criticalLevel, int ceilingLevel) {
        this.productId = productId;
        this.productName = productName;
        this.category = category;
        this.quantity = quantity;
        this.costPrice = costPrice;
        this.sellingPrice = sellingPrice;
        this.reorderLevel = reorderLevel;
        this.criticalLevel = criticalLevel;

        // Calculate values
        this.totalCostValue = quantity * costPrice;
        this.totalSellingValue = quantity * sellingPrice;
        this.profit = totalSellingValue - totalCostValue;

        // Calculate profit margin
        if (totalSellingValue > 0) {
            this.profitMargin = String.format("%.2f%%", (profit / totalSellingValue) * 100);
        } else {
            this.profitMargin = "0%";
        }

        // Determine stock status
        if (quantity <= criticalLevel) {
            this.stockStatus = "CRITICAL";
        } else if (quantity <= reorderLevel) {
            this.stockStatus = "LOW";
        } else if (quantity >= ceilingLevel) {
            this.stockStatus = "OVERSTOCK";
        } else {
            this.stockStatus = "NORMAL";
        }
    }

    // Getters
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public int getQuantity() { return quantity; }
    public double getCostPrice() { return costPrice; }
    public double getSellingPrice() { return sellingPrice; }
    public double getTotalCostValue() { return totalCostValue; }
    public double getTotalSellingValue() { return totalSellingValue; }
    public double getProfit() { return profit; }
    public String getProfitMargin() { return profitMargin; }
    public String getStockStatus() { return stockStatus; }
}