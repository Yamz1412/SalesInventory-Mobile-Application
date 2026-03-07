package com.app.SalesInventory;

public class POItem {
    private String productId;
    private String productName;
    private int quantity;
    private int receivedQuantity;
    private double unitPrice;
    private String unit;

    public POItem() {
    }

    // 5-argument constructor expected by CreatePurchaseOrderActivity
    public POItem(String productId, String productName, int quantity, double unitPrice, String unit) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.receivedQuantity = 0;
        this.unitPrice = unitPrice;
        this.unit = unit;
    }

    // 4-argument constructor for backwards compatibility
    public POItem(String productId, String productName, int quantity, double unitPrice) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.receivedQuantity = 0;
        this.unitPrice = unitPrice;
        this.unit = "pcs"; // Default fallback
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public int getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(int receivedQuantity) { this.receivedQuantity = receivedQuantity; }

    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) { this.unitPrice = unitPrice; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public double getSubtotal() { return unitPrice * quantity; }
}