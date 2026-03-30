package com.app.SalesInventory;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "product_batches",
        foreignKeys = @ForeignKey(entity = ProductEntity.class,
                parentColumns = "productId",
                childColumns = "productId",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("productId")})
public class BatchEntity {

    @PrimaryKey(autoGenerate = true)
    public long batchId;

    public String productId;
    public double initialQuantity;
    public double remainingQuantity;
    public long receiveDate;
    public long expiryDate;
    public double costPrice;
}