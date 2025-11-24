package com.app.SalesInventory;

public class Adjustment {
    private String id;
    private String productId;
    private int quantity;
    private String type;
    private String reason;
    private long date;

    public Adjustment() {}

    public Adjustment(String productId, int quantity, String type, String reason, long date) {
        this.productId = productId;
        this.quantity = quantity;
        this.type = type;
        this.reason = reason;
        this.date = date;
    }

    // Getters and Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public long getDate() { return date; }
    public void setDate(long date) { this.date = date; }
}