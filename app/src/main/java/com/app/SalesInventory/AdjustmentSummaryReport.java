package com.app.SalesInventory;

import java.util.HashSet;
import java.util.Set;

public class AdjustmentSummaryReport {
    private String productId;
    private String productName;
    private double additions = 0.0;
    private double removals = 0.0;
    private int totalAdjustments = 0;
    private Set<String> reasons = new HashSet<>();

    public AdjustmentSummaryReport(String productId, String productName) {
        this.productId = productId;
        this.productName = productName;
    }

    public String getProductId() { return productId; }
    public String getProductName() { return productName; }

    public double getAdditions() { return additions; }
    public void addAddition(double qty) { this.additions += qty; this.totalAdjustments++; }

    public double getRemovals() { return removals; }
    public void addRemoval(double qty) { this.removals += qty; this.totalAdjustments++; }

    public int getTotalAdjustments() { return totalAdjustments; }

    public double getNetChange() { return additions - removals; }

    public void addReason(String reason) {
        if (reason != null && !reason.trim().isEmpty()) {
            reasons.add(reason);
        }
    }

    public String getReasonsText() {
        return android.text.TextUtils.join(", ", reasons);
    }
}