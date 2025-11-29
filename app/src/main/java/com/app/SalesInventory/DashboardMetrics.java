package com.app.SalesInventory;

public class DashboardMetrics {
    private double totalSalesToday;
    private double totalInventoryValue;
    private int lowStockCount;
    private int pendingOrdersCount;
    private double revenue;
    private long lastUpdated;

    public DashboardMetrics() {
        this.lastUpdated = System.currentTimeMillis();
    }

    public DashboardMetrics(double totalSalesToday, double totalInventoryValue, int lowStockCount, int pendingOrdersCount, double revenue) {
        this.totalSalesToday = totalSalesToday;
        this.totalInventoryValue = totalInventoryValue;
        this.lowStockCount = lowStockCount;
        this.pendingOrdersCount = pendingOrdersCount;
        this.revenue = revenue;
        this.lastUpdated = System.currentTimeMillis();
    }

    public double getTotalSalesToday() {
        return totalSalesToday;
    }

    public void setTotalSalesToday(double totalSalesToday) {
        this.totalSalesToday = totalSalesToday;
    }

    public double getTotalInventoryValue() {
        return totalInventoryValue;
    }

    public void setTotalInventoryValue(double totalInventoryValue) {
        this.totalInventoryValue = totalInventoryValue;
    }

    public int getLowStockCount() {
        return lowStockCount;
    }

    public void setLowStockCount(int lowStockCount) {
        this.lowStockCount = lowStockCount;
    }

    public int getPendingOrdersCount() {
        return pendingOrdersCount;
    }

    public void setPendingOrdersCount(int pendingOrdersCount) {
        this.pendingOrdersCount = pendingOrdersCount;
    }

    public double getRevenue() {
        return revenue;
    }

    public void setRevenue(double revenue) {
        this.revenue = revenue;
    }

    public long getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(long lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
}