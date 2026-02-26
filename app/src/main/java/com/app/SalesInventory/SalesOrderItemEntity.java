package com.app.SalesInventory;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

@Entity(tableName = "sales_order_items",
        foreignKeys = @ForeignKey(entity = SalesOrderEntity.class,
                parentColumns = "localId",
                childColumns = "orderLocalId",
                onDelete = ForeignKey.CASCADE))
public class SalesOrderItemEntity {
    @PrimaryKey(autoGenerate = true)
    public long localId;
    public long orderLocalId;
    public String productId;
    public String productName;
    public int quantity;
    public double unitPrice;
    public double lineTotal;
    public String size;
    public String addons;
}