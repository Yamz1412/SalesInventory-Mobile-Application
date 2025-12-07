package com.app.SalesInventory;

import java.util.ArrayList;
import java.util.List;

public class AdjustmentSummaryData {
    private String productId;
    private String productName;
    private int totalAdjustments;
    private int totalAdditions;
    private int totalRemovals;
    private List<String> additionReasons = new ArrayList<>();
    private List<String> removalReasons = new ArrayList<>();

    public AdjustmentSummaryData() {
    }

    public AdjustmentSummaryData(String productId, String productName) {
        this.productId = productId;
        this.productName = productName;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public int getTotalAdjustments() {
        return totalAdjustments;
    }

    public int getTotalAdditions() {
        return totalAdditions;
    }

    public int getTotalRemovals() {
        return totalRemovals;
    }

    public List<String> getAdditionReasons() {
        return additionReasons;
    }

    public List<String> getRemovalReasons() {
        return removalReasons;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public void setTotalAdjustments(int totalAdjustments) {
        this.totalAdjustments = totalAdjustments;
    }

    public void setTotalAdditions(int totalAdditions) {
        this.totalAdditions = totalAdditions;
    }

    public void setTotalRemovals(int totalRemovals) {
        this.totalRemovals = totalRemovals;
    }

    public void addAdditionReason(String reason) {
        if (reason != null && !reason.isEmpty()) additionReasons.add(reason);
    }

    public void addRemovalReason(String reason) {
        if (reason != null && !reason.isEmpty()) removalReasons.add(reason);
    }
}