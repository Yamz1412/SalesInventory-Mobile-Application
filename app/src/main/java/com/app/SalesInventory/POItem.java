package com.app.SalesInventory;

public class POItem {
    private String productId;
    private String productName;
    private int quantity;
    private double unitPrice;

    public POItem(String productId, String productName, int quantity, double unitPrice) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public String getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public double getUnitPrice() { return unitPrice; }

    public double getSubtotal() { return quantity * unitPrice; }
}