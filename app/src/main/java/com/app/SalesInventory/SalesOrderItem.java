package com.app.SalesInventory;

public class SalesOrderItem {
    private long localId;
    private long orderLocalId;
    private String productId;
    private String productName;
    private int quantity;
    private double unitPrice;
    private double lineTotal;
    private String size;
    private String addons;

    public long getLocalId() {
        return localId;
    }

    public void setLocalId(long localId) {
        this.localId = localId;
    }

    public long getOrderLocalId() {
        return orderLocalId;
    }

    public void setOrderLocalId(long orderLocalId) {
        this.orderLocalId = orderLocalId;
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

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    public double getLineTotal() {
        return lineTotal;
    }

    public void setLineTotal(double lineTotal) {
        this.lineTotal = lineTotal;
    }

    public String getSize() {
        return size;
    }

    public void setSize(String size) {
        this.size = size;
    }

    public String getAddons() {
        return addons;
    }

    public void setAddons(String addons) {
        this.addons = addons;
    }
}