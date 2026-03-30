package com.app.SalesInventory;

public class POItem {
    private String productId;
    private String productName;
    private double quantity;
    private double receivedQuantity;
    private double unitPrice;
    private String unit;
    private double subtotal;

    public POItem() {}

    public POItem(String productId, String productName, double quantity, double unitPrice, String unit) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.receivedQuantity = 0;
        this.unitPrice = unitPrice;
        this.unit = unit;
        this.subtotal = unitPrice * quantity;
    }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }
    public double getQuantity() { return quantity; }
    public void setQuantity(double quantity) {
        this.quantity = quantity;
        this.subtotal = this.unitPrice * quantity;
    }
    public double getReceivedQuantity() { return receivedQuantity; }
    public void setReceivedQuantity(double receivedQuantity) { this.receivedQuantity = receivedQuantity; }
    public double getUnitPrice() { return unitPrice; }
    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
        this.subtotal = unitPrice * this.quantity;
    }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    // This fixes the "Cannot resolve method getSubtotal" error
    public double getSubtotal() {
        return unitPrice * quantity;
    }
}