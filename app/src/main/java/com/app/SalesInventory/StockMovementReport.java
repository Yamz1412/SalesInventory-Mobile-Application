package com.app.SalesInventory;

public class StockMovementReport {
    private String productId;
    private String productName;
    private String category;
    private int openingStock;
    private int received;
    private int sold;
    private int adjusted;
    private int closingStock;
    private double movementPercentage;
    private long reportDate;

    public StockMovementReport(String productId, String productName, String category,
                               int openingStock, int received, int sold, int adjusted,
                               int closingStock, long reportDate) {
        this.productId = productId;
        this.productName = productName;
        this.category = category;
        this.openingStock = openingStock;
        this.received = received;
        this.sold = sold;
        this.adjusted = adjusted;
        this.closingStock = closingStock;
        this.reportDate = reportDate;
        calculateMovementPercentage();
    }
    public void addReceived(int qty) {
        this.received += qty;
    }

    public void addSold(int qty) {
        this.sold += qty;
    }

    public void addAdjusted(int qty) {
        this.adjusted += qty;
    }

    public void setClosingStock(int qty) {
        this.closingStock = qty;
    }

    public void calculateOpening() {
        this.openingStock = closingStock - received + sold - adjusted;
        if (this.openingStock < 0) {
            this.openingStock = 0;
        }
        calculateMovementPercentage();
    }

    private void calculateMovementPercentage() {
        int totalThroughput = openingStock + received;
        if (totalThroughput > 0) {
            this.movementPercentage = ((double) sold / totalThroughput) * 100;
        } else {
            this.movementPercentage = 0;
        }
    }

    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public int getOpeningStock() { return openingStock; }
    public int getReceived() { return received; }
    public int getSold() { return sold; }
    public int getAdjusted() { return adjusted; }
    public int getClosingStock() { return closingStock; }
    public double getMovementPercentage() { return movementPercentage; }
    public long getReportDate() { return reportDate; }
    public int getTotalMovement() {
        return received + sold + Math.abs(adjusted);
    }
}