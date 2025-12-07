package com.app.SalesInventory;

import java.util.HashMap;
import java.util.Map;

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

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("totalSalesToday", totalSalesToday);
        m.put("totalInventoryValue", totalInventoryValue);
        m.put("lowStockCount", lowStockCount);
        m.put("pendingOrdersCount", pendingOrdersCount);
        m.put("revenue", revenue);
        m.put("lastUpdated", lastUpdated);
        return m;
    }

    public static DashboardMetrics fromMap(Map<String, Object> m) {
        DashboardMetrics d = new DashboardMetrics();
        if (m == null) return d;
        Object o;
        o = m.get("totalSalesToday");
        if (o instanceof Number) d.totalSalesToday = ((Number) o).doubleValue();
        o = m.get("totalInventoryValue");
        if (o instanceof Number) d.totalInventoryValue = ((Number) o).doubleValue();
        o = m.get("lowStockCount");
        if (o instanceof Number) d.lowStockCount = ((Number) o).intValue();
        o = m.get("pendingOrdersCount");
        if (o instanceof Number) d.pendingOrdersCount = ((Number) o).intValue();
        o = m.get("revenue");
        if (o instanceof Number) d.revenue = ((Number) o).doubleValue();
        o = m.get("lastUpdated");
        if (o instanceof Number) d.lastUpdated = ((Number) o).longValue();
        return d;
    }
}