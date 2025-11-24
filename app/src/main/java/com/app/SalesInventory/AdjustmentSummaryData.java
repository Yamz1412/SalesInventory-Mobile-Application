package com.app.SalesInventory;

import java.util.ArrayList;
import java.util.List;

public class AdjustmentSummaryData {
    private String productId;
    private String productName;
    private int totalAdditions;
    private int totalRemovals;
    private int totalAdjustments;
    private List<String> additionReasons;
    private List<String> removalReasons;

    public AdjustmentSummaryData(String productId, String productName) {
        this.productId = productId;
        this.productName = productName;
        this.totalAdditions = 0;
        this.totalRemovals = 0;
        this.totalAdjustments = 0;
        this.additionReasons = new ArrayList<>();
        this.removalReasons = new ArrayList<>();
    }

    public void addAddition(int quantity, String reason) {
        this.totalAdditions += quantity;
        this.totalAdjustments++;
        if (!additionReasons.contains(reason)) {
            this.additionReasons.add(reason);
        }
    }

    public void addRemoval(int quantity, String reason) {
        this.totalRemovals += quantity;
        this.totalAdjustments++;
        if (!removalReasons.contains(reason)) {
            this.removalReasons.add(reason);
        }
    }

    // Getters
    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getTotalAdditions() { return totalAdditions; }
    public int getTotalRemovals() { return totalRemovals; }
    public int getTotalAdjustments() { return totalAdjustments; }
    public int getNetChange() { return totalAdditions - totalRemovals; }
    public List<String> getAdditionReasons() { return additionReasons; }
    public List<String> getRemovalReasons() { return removalReasons; }
}