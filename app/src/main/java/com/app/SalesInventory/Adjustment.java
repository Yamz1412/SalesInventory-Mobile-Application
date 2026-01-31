package com.app.SalesInventory;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class Adjustment {
    private String id;
    private String productId;
    private String productName;
    private int quantityChange;
    private String reason;

    @ServerTimestamp
    public Date timestamp;

    public Adjustment() {
    }

    public Adjustment(String id, String productId, String productName, int quantityChange, String reason, long timestamp) {
        this.id = id;
        this.productId = productId;
        this.productName = productName;
        this.quantityChange = quantityChange;
        this.reason = reason;
        this.timestamp = new Date(timestamp);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getQuantityChange() {
        return quantityChange;
    }

    public void setQuantityChange(int quantityChange) {
        this.quantityChange = quantityChange;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public long getTimestamp() {
        return timestamp != null ? timestamp.getTime() : 0L;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = new Date(timestamp);
    }
}