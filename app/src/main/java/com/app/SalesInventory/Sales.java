package com.app.SalesInventory;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class Sales {
    private String id;
    private String orderId;
    private String productId;
    private String productName;
    private int quantity;
    private double price;
    private double totalPrice;
    private String paymentMethod;

    @ServerTimestamp
    public Date date;

    @ServerTimestamp
    public Date timestamp;

    private String deliveryType;
    private String deliveryStatus;

    @ServerTimestamp
    public Date deliveryDate;

    private String deliveryName;
    private String deliveryPhone;
    private String deliveryAddress;
    private String deliveryPaymentMethod;

    public Sales() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public double getTotalPrice() { return totalPrice; }
    public void setTotalPrice(double totalPrice) { this.totalPrice = totalPrice; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public long getDate() {
        return date != null ? date.getTime() : 0L;
    }

    public void setDate(long date) {
        this.date = new Date(date);
    }

    public long getTimestamp() {
        return timestamp != null ? timestamp.getTime() : 0L;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = new Date(timestamp);
    }

    public String getDeliveryType() { return deliveryType; }
    public void setDeliveryType(String deliveryType) { this.deliveryType = deliveryType; }

    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }

    public long getDeliveryDate() {
        return deliveryDate != null ? deliveryDate.getTime() : 0L;
    }

    public void setDeliveryDate(long deliveryDate) {
        this.deliveryDate = new Date(deliveryDate);
    }

    public String getDeliveryName() { return deliveryName; }
    public void setDeliveryName(String deliveryName) { this.deliveryName = deliveryName; }

    public String getDeliveryPhone() { return deliveryPhone; }
    public void setDeliveryPhone(String deliveryPhone) { this.deliveryPhone = deliveryPhone; }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public String getDeliveryPaymentMethod() { return deliveryPaymentMethod; }
    public void setDeliveryPaymentMethod(String deliveryPaymentMethod) { this.deliveryPaymentMethod = deliveryPaymentMethod; }
}