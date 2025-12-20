package com.app.SalesInventory;

public class POItem {
    private String productId;
    private String productName;
    private int quantity;
    private double unitPrice;
    private double costPrice;

    public POItem() {
    }

    public POItem(String productId, String productName, int quantity, double unitPrice) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = Math.max(0, quantity);
        this.unitPrice = unitPrice;
        this.costPrice = 0.0;
    }

    public POItem(String productId, String productName, int quantity, double unitPrice, double costPrice) {
        this.productId = productId;
        this.productName = productName;
        this.quantity = Math.max(0, quantity);
        this.unitPrice = unitPrice;
        this.costPrice = costPrice;
    }

    public String getProductId() {
        return productId == null ? "" : productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getProductName() {
        return productName == null ? "" : productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = Math.max(0, quantity);
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public double getCostPrice() {
        return costPrice;
    }

    public void setCostPrice(double costPrice) {
        this.costPrice = costPrice;
    }

    public double getSubtotal() {
        double price = unitPrice > 0.0 ? unitPrice : costPrice;
        return price * quantity;
    }
}