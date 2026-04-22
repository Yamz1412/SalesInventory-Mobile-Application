package com.app.SalesInventory;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.PropertyName;
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
    private double totalCost;
    private double discountAmount;
    private String paymentMethod;
    private String status = "COMPLETED";
    private String extraDetails;
    private String excludedIngredients;

    @ServerTimestamp
    @PropertyName("date")
    private Date date;

    @ServerTimestamp
    @PropertyName("timestamp")
    private Date timestamp;

    private String deliveryType;
    private String deliveryStatus;

    @ServerTimestamp
    @PropertyName("deliveryDate")
    private Date deliveryDate;

    private String deliveryName;
    private String deliveryPhone;
    private String deliveryAddress;
    private String deliveryPaymentMethod;

    public Sales() {
        // Default constructor required for Firestore
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

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

    public double getTotalCost() { return totalCost; }
    public void setTotalCost(double totalCost) { this.totalCost = totalCost; }

    public double getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(double discountAmount) { this.discountAmount = discountAmount; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getDeliveryType() { return deliveryType; }
    public void setDeliveryType(String deliveryType) { this.deliveryType = deliveryType; }

    public String getDeliveryStatus() { return deliveryStatus; }
    public void setDeliveryStatus(String deliveryStatus) { this.deliveryStatus = deliveryStatus; }

    public String getDeliveryName() { return deliveryName; }
    public void setDeliveryName(String deliveryName) { this.deliveryName = deliveryName; }

    public String getDeliveryPhone() { return deliveryPhone; }
    public void setDeliveryPhone(String deliveryPhone) { this.deliveryPhone = deliveryPhone; }

    public String getDeliveryAddress() { return deliveryAddress; }
    public void setDeliveryAddress(String deliveryAddress) { this.deliveryAddress = deliveryAddress; }

    public String getDeliveryPaymentMethod() { return deliveryPaymentMethod; }
    public void setDeliveryPaymentMethod(String deliveryPaymentMethod) { this.deliveryPaymentMethod = deliveryPaymentMethod; }

    public String getExtraDetails() { return extraDetails != null ? extraDetails : ""; }
    public void setExtraDetails(String extraDetails) { this.extraDetails = extraDetails; }

    public String getExcludedIngredients() { return excludedIngredients != null ? excludedIngredients : ""; }
    public void setExcludedIngredients(String excludedIngredients) { this.excludedIngredients = excludedIngredients; }

    // ====================================================================
    // FIREBASE SPECIFIC GETTERS & SETTERS (Uses exact Date object)
    // ====================================================================

    @PropertyName("date")
    public Date getFirebaseDate() { return date; }

    @PropertyName("date")
    public void setFirebaseDate(Date date) { this.date = date; }

    @PropertyName("timestamp")
    public Date getFirebaseTimestamp() { return timestamp; }

    @PropertyName("timestamp")
    public void setFirebaseTimestamp(Date timestamp) { this.timestamp = timestamp; }

    @PropertyName("deliveryDate")
    public Date getFirebaseDeliveryDate() { return deliveryDate; }

    @PropertyName("deliveryDate")
    public void setFirebaseDeliveryDate(Date deliveryDate) { this.deliveryDate = deliveryDate; }


    // ====================================================================
    // APP SPECIFIC GETTERS & SETTERS (Hidden from Firebase to avoid crashes)
    // ====================================================================

    @Exclude
    public long getDate() {
        return date != null ? date.getTime() : 0L;
    }

    @Exclude
    public void setDate(long date) {
        this.date = (date > 0) ? new Date(date) : null;
    }

    @Exclude
    public long getTimestamp() {
        return timestamp != null ? timestamp.getTime() : 0L;
    }

    @Exclude
    public void setTimestamp(long timestamp) {
        this.timestamp = (timestamp > 0) ? new Date(timestamp) : null;
    }

    @Exclude
    public long getDeliveryDate() {
        return deliveryDate != null ? deliveryDate.getTime() : 0L;
    }

    @Exclude
    public void setDeliveryDate(long deliveryDate) {
        this.deliveryDate = (deliveryDate > 0) ? new Date(deliveryDate) : null;
    }
}