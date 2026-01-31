package com.app.SalesInventory;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class StockAdjustment {
    private String adjustmentId;
    private String productId;
    private String productName;
    private String adjustmentType;
    private int quantityBefore;
    private int quantityAdjusted;
    private int quantityAfter;
    private String reason;
    private String remarks;

    @ServerTimestamp
    public Date timestamp;

    private String adjustedBy;

    public StockAdjustment() {
    }

    public StockAdjustment(String adjustmentId, String productId, String productName,
                           String adjustmentType, int quantityBefore, int quantityAdjusted,
                           int quantityAfter, String reason, String remarks,
                           long timestamp, String adjustedBy) {
        this.adjustmentId = adjustmentId;
        this.productId = productId;
        this.productName = productName;
        this.adjustmentType = adjustmentType;
        this.quantityBefore = quantityBefore;
        this.quantityAdjusted = quantityAdjusted;
        this.quantityAfter = quantityAfter;
        this.reason = reason;
        this.remarks = remarks;
        this.timestamp = new Date(timestamp);
        this.adjustedBy = adjustedBy;
    }

    public String getAdjustmentId() { return adjustmentId; }
    public void setAdjustmentId(String adjustmentId) { this.adjustmentId = adjustmentId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getAdjustmentType() { return adjustmentType; }
    public void setAdjustmentType(String adjustmentType) { this.adjustmentType = adjustmentType; }

    public int getQuantityBefore() { return quantityBefore; }
    public void setQuantityBefore(int quantityBefore) { this.quantityBefore = quantityBefore; }

    public int getQuantityAdjusted() { return quantityAdjusted; }
    public void setQuantityAdjusted(int quantityAdjusted) { this.quantityAdjusted = quantityAdjusted; }

    public int getQuantityAfter() { return quantityAfter; }
    public void setQuantityAfter(int quantityAfter) { this.quantityAfter = quantityAfter; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public long getTimestamp() { return timestamp != null ? timestamp.getTime() : 0L; }
    public void setTimestamp(long timestamp) { this.timestamp = new Date(timestamp); }

    public String getAdjustedBy() { return adjustedBy; }
    public void setAdjustedBy(String adjustedBy) { this.adjustedBy = adjustedBy; }
}