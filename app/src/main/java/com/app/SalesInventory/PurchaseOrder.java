package com.app.SalesInventory;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;
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

    // NEW: Needed for Firebase to map the schedule correctly
    private long expectedDeliveryDate;

    @ServerTimestamp
    public Date orderDate;

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
        this.orderDate = new Date(orderDate);
        this.totalAmount = totalAmount;
        this.items = items;
        this.deliveryNote = "";
        this.expectedDeliveryDate = 0L;
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

    public Date getOrderDate() { return orderDate; }
    public void setOrderDate(long orderDate) { this.orderDate = new Date(orderDate); }

    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }

    public List<POItem> getItems() { return items; }
    public void setItems(List<POItem> items) { this.items = items; }

    public String getOwnerAdminId() { return ownerAdminId; }
    public void setOwnerAdminId(String ownerAdminId) { this.ownerAdminId = ownerAdminId; }

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
        m.put("orderDate", this.orderDate != null ? this.orderDate.getTime() : 0L);
        m.put("totalAmount", this.totalAmount);
        m.put("items", this.items);
        return m;
    }

    public boolean isReceived() {
        return STATUS_RECEIVED.equals(this.status);
    }
}