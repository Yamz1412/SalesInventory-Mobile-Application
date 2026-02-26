package com.app.SalesInventory;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "sales_orders")
public class SalesOrderEntity {
    @PrimaryKey(autoGenerate = true)
    public long localId;
    public String remoteId;
    public String orderNumber;
    public long orderDate;
    public String customerName;
    public double subTotal;
    public double discountAmount;
    public double discountPercent;
    public double totalAmount;
    public String paymentStatus;
    public String paymentMethod;
    public double amountPaid;
    public double changeDue;
    public String deliveryStatus;
    public long deliveryDate;
    public String status;
}