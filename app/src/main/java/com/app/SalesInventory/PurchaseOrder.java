package com.app.SalesInventory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PurchaseOrder {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PARTIAL = "PARTIAL";
    public static final String STATUS_RECEIVED = "RECEIVED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_DRAFT = "DRAFT";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_COMPLETED = "COMPLETED";


    private String poId;
    private String poNumber;
    private String supplierName;
    private String supplierPhone;
    private String status;
    private String ownerAdminId;
    private String deliveryNote;

    private long expectedDeliveryDate;
    private String purchaseOrderId;

    // FIXED: Use a 'long' instead of 'Date' for Realtime Database compatibility
    private long orderDate;

    private double totalAmount;
    private List<POItem> items;

    public PurchaseOrder() {
    }

    public PurchaseOrder(String poId, String poNumber, String supplierName, String supplierPhone, String status, long orderDate, double totalAmount, List<POItem> items) {
        this.poId = poId;
        this.poNumber = poNumber;
        this.supplierName = supplierName;
        this.supplierPhone = supplierPhone;
        this.status = status;
        this.orderDate = orderDate; // FIXED
        this.totalAmount = totalAmount;
        this.items = items;
    }

    public String getPoId() { return poId; }
    public void setPoId(String poId) { this.poId = poId; }

    public String getPoNumber() { return poNumber; }
    public void setPoNumber(String poNumber) { this.poNumber = poNumber; }

    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }

    public String getSupplierPhone() { return supplierPhone; }
    public void setSupplierPhone(String supplierPhone) { this.supplierPhone = supplierPhone; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getDeliveryNote() { return deliveryNote; }
    public void setDeliveryNote(String deliveryNote) { this.deliveryNote = deliveryNote; }

    public long getExpectedDeliveryDate() { return expectedDeliveryDate; }
    public void setExpectedDeliveryDate(long expectedDeliveryDate) { this.expectedDeliveryDate = expectedDeliveryDate; }

    // FIXED Getters and Setters for orderDate
    public long getOrderDate() { return orderDate; }
    public void setOrderDate(long orderDate) { this.orderDate = orderDate; }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public List<POItem> getItems() { return items; }
    public void setItems(List<POItem> items) { this.items = items; }

    public String getOwnerAdminId() { return ownerAdminId; }
    public void setOwnerAdminId(String ownerAdminId) { this.ownerAdminId = ownerAdminId; }
    public void setId(String id) {
        this.poId = id;
    }

    public String getPurchaseOrderId() { return purchaseOrderId; }
    public void setPurchaseOrderId(String purchaseOrderId) { this.purchaseOrderId = purchaseOrderId; }

    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("poId", this.poId);
        m.put("poNumber", this.poNumber);
        m.put("supplierName", this.supplierName);
        m.put("supplierPhone", this.supplierPhone);
        m.put("status", this.status);
        m.put("ownerAdminId", this.ownerAdminId);
        m.put("deliveryNote", this.deliveryNote);
        m.put("expectedDeliveryDate", this.expectedDeliveryDate);
        m.put("orderDate", this.orderDate); // FIXED
        m.put("totalAmount", this.totalAmount);
        m.put("items", this.items);
        return m;
    }
}