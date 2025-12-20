package com.app.SalesInventory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardMetrics {
    private double totalSalesToday;
    private double revenue;
    private double totalInventoryValue;
    private int lowStockCount;
    private int pendingOrdersCount;
    private int nearExpiryCount;
    private List<TopProduct> topProducts;

    public DashboardMetrics() {
        topProducts = new ArrayList<>();
    }

    public DashboardMetrics(double totalSalesToday,
                            double totalInventoryValue,
                            int lowStockCount,
                            int pendingOrdersCount,
                            double revenue) {
        this();
        this.totalSalesToday = totalSalesToday;
        this.totalInventoryValue = totalInventoryValue;
        this.lowStockCount = lowStockCount;
        this.pendingOrdersCount = pendingOrdersCount;
        this.revenue = revenue;
    }

    public double getTotalSalesToday() {
        return totalSalesToday;
    }

    public void setTotalSalesToday(double totalSalesToday) {
        this.totalSalesToday = totalSalesToday;
    }

    public double getRevenue() {
        return revenue;
    }

    public void setRevenue(double revenue) {
        this.revenue = revenue;
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

    public int getNearExpiryCount() {
        return nearExpiryCount;
    }

    public void setNearExpiryCount(int nearExpiryCount) {
        this.nearExpiryCount = nearExpiryCount;
    }

    public List<TopProduct> getTopProducts() {
        return topProducts;
    }

    public void setTopProducts(List<TopProduct> topProducts) {
        this.topProducts = topProducts == null ? new ArrayList<>() : topProducts;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("totalSalesToday", totalSalesToday);
        m.put("revenue", revenue);
        m.put("totalInventoryValue", totalInventoryValue);
        m.put("lowStockCount", lowStockCount);
        m.put("pendingOrdersCount", pendingOrdersCount);
        m.put("nearExpiryCount", nearExpiryCount);
        List<Map<String, Object>> tops = new ArrayList<>();
        if (topProducts != null) {
            for (TopProduct tp : topProducts) {
                Map<String, Object> tm = new HashMap<>();
                tm.put("productName", tp.getProductName());
                tm.put("quantitySold", tp.getQuantitySold());
                tops.add(tm);
            }
        }
        m.put("topProducts", tops);
        return m;
    }

    public static DashboardMetrics fromMap(Map<String, Object> m) {
        DashboardMetrics dm = new DashboardMetrics();
        if (m == null) return dm;
        try {
            Object v;
            v = m.get("totalSalesToday");
            if (v instanceof Number) dm.setTotalSalesToday(((Number) v).doubleValue());
            else if (v instanceof String) dm.setTotalSalesToday(Double.parseDouble((String) v));

            v = m.get("revenue");
            if (v instanceof Number) dm.setRevenue(((Number) v).doubleValue());
            else if (v instanceof String) dm.setRevenue(Double.parseDouble((String) v));

            v = m.get("totalInventoryValue");
            if (v instanceof Number) dm.setTotalInventoryValue(((Number) v).doubleValue());
            else if (v instanceof String) dm.setTotalInventoryValue(Double.parseDouble((String) v));

            v = m.get("lowStockCount");
            if (v instanceof Number) dm.setLowStockCount(((Number) v).intValue());
            else if (v instanceof String) dm.setLowStockCount(Integer.parseInt((String) v));

            v = m.get("pendingOrdersCount");
            if (v instanceof Number) dm.setPendingOrdersCount(((Number) v).intValue());
            else if (v instanceof String) dm.setPendingOrdersCount(Integer.parseInt((String) v));

            v = m.get("nearExpiryCount");
            if (v instanceof Number) dm.setNearExpiryCount(((Number) v).intValue());
            else if (v instanceof String) dm.setNearExpiryCount(Integer.parseInt((String) v));

            Object topsObj = m.get("topProducts");
            List<TopProduct> tops = new ArrayList<>();
            if (topsObj instanceof List) {
                List<?> list = (List<?>) topsObj;
                for (Object o : list) {
                    if (o instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> tm = (Map<String, Object>) o;
                        String name = tm.get("productName") != null ? String.valueOf(tm.get("productName")) : "";
                        int qty = 0;
                        Object qo = tm.get("quantitySold");
                        if (qo instanceof Number) qty = ((Number) qo).intValue();
                        else if (qo instanceof String) {
                            try { qty = Integer.parseInt((String) qo); } catch (Exception ignored) {}
                        }
                        tops.add(new TopProduct(name, qty));
                    }
                }
            }
            dm.setTopProducts(tops);
        } catch (Exception ignored) {}
        return dm;
    }

    public static class TopProduct {
        private String productName;
        private int quantitySold;

        public TopProduct() {
        }

        public TopProduct(String productName, int quantitySold) {
            this.productName = productName;
            this.quantitySold = quantitySold;
        }

        public String getProductName() {
            return productName == null ? "" : productName;
        }

        public void setProductName(String productName) {
            this.productName = productName;
        }

        public int getQuantitySold() {
            return quantitySold;
        }

        public void setQuantitySold(int quantitySold) {
            this.quantitySold = quantitySold;
        }
    }
}