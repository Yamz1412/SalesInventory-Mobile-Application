package com.app.SalesInventory;

public class SalesJournalEntry {
    private String productId;
    private String productName;
    private long lastSaleTimestamp;
    private int totalQuantity;
    private double totalAmount;

    public SalesJournalEntry() {
    }

    public SalesJournalEntry(String productId, String productName) {
        this.productId = productId == null ? "" : productId;
        this.productName = productName == null ? "" : productName;
        this.lastSaleTimestamp = 0L;
        this.totalQuantity = 0;
        this.totalAmount = 0.0;
    }

    public String getProductId() {
        return productId == null ? "" : productId;
    }

    public void setProductId(String productId) {
        this.productId = productId == null ? "" : productId;
    }

    public String getProductName() {
        return productName == null ? "" : productName;
    }

    public void setProductName(String productName) {
        this.productName = productName == null ? "" : productName;
    }

    public long getLastSaleTimestamp() {
        return lastSaleTimestamp;
    }

    public void setLastSaleTimestamp(long lastSaleTimestamp) {
        this.lastSaleTimestamp = lastSaleTimestamp;
    }

    public int getTotalQuantity() {
        return totalQuantity;
    }

    public void setTotalQuantity(int totalQuantity) {
        this.totalQuantity = totalQuantity;
    }

    public double getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(double totalAmount) {
        this.totalAmount = totalAmount;
    }

    public void addSale(int qty, double amount, long ts) {
        this.totalQuantity += qty;
        this.totalAmount += amount;
        if (ts > this.lastSaleTimestamp) this.lastSaleTimestamp = ts;
    }
}