package com.app.SalesInventory;

import java.util.ArrayList;
import java.util.List;

public class AdjustmentSummaryReport {
    private String productId;
    private String productName;
    private int netChange;
    private int totalAdjustments;
    private int additions;
    private int removals;
    private List<String> reasons;

    public AdjustmentSummaryReport(String productId, String productName) {
        this.productId = productId;
        this.productName = productName;
        this.netChange = 0;
        this.totalAdjustments = 0;
        this.additions = 0;
        this.removals = 0;
        this.reasons = new ArrayList<>();
    }

    public void addAdjustment(StockAdjustment adj) {
        if ("Add Stock".equals(adj.getAdjustmentType())) {
            additions += adj.getQuantityAdjusted();
            netChange += adj.getQuantityAdjusted();
        } else {
            removals += adj.getQuantityAdjusted();
            netChange -= adj.getQuantityAdjusted();
        }
        totalAdjustments++;
        if (adj.getReason() != null && !adj.getReason().isEmpty()) {
            reasons.add(adj.getReason());
        }
    }

    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getNetChange() { return netChange; }
    public int getTotalAdjustments() { return totalAdjustments; }
    public int getAdditions() { return additions; }
    public int getRemovals() { return removals; }
    public String getReasonsText() {
        return String.join(", ", reasons);
    }
}