package com.app.SalesInventory;

public class StockMovementReport {
    private String productId;
    private String productName;
    private String category;
    private double openingStock;
    private int received;
    private int sold;
    private int adjusted;
    private double closingStock;
    private double movementPercentage;
    private long reportDate;

    public StockMovementReport(String productId, String productName, String category,
                               double openingStock, int received, int sold, int adjusted,
                               double closingStock, long reportDate) {
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
    public void addReceived(double qty) {
        this.received += qty;
    }

    public void addSold(int qty) {
        this.sold += qty;
    }

    public void addAdjusted(double qty) {
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
        double totalThroughput = openingStock + received;
        if (totalThroughput > 0) {
            this.movementPercentage = ((double) sold / totalThroughput) * 100;
        } else {
            this.movementPercentage = 0;
        }
    }

    // Getters
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public String getCategory() { return category; }
    public double getOpeningStock() { return openingStock; }
    public int getReceived() { return received; }
    public int getSold() { return sold; }
    public int getAdjusted() { return adjusted; }
    public double getClosingStock() { return closingStock; }
    public double getMovementPercentage() { return movementPercentage; }
    public long getReportDate() { return reportDate; }
    public int getTotalMovement() {
        return received + sold + Math.abs(adjusted);
    }
}