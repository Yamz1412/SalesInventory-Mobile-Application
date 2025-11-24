package com.app.SalesInventory;

public class PurchaseOrder {
    private String poId;
    private String poNumber;
    private String supplierName;
    private String status;
    private long orderDate;
    private double totalAmount;

    public PurchaseOrder() {
    }

    public PurchaseOrder(String poId, String poNumber, String supplierName, String status, long orderDate, double totalAmount) {
        this.poId = poId;
        this.poNumber = poNumber;
        this.supplierName = supplierName;
        this.status = status;
        this.orderDate = orderDate;
        this.totalAmount = totalAmount;
    }

    // Getters and Setters
    public String getPoId() { return poId; }
    public void setPoId(String poId) { this.poId = poId; }

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getOrderDate() { return orderDate; }
    public void setOrderDate(long orderDate) { this.orderDate = orderDate; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
}