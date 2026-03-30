package com.app.SalesInventory;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface BatchDao {

    @Insert
    long insertBatch(BatchEntity batch);

    @Update
    void updateBatch(BatchEntity batch);

    // This is the core FIFO query: gets available batches sorted by oldest expiry first
    @Query("SELECT * FROM product_batches WHERE productId = :prodId AND remainingQuantity > 0 ORDER BY expiryDate ASC")
    List<BatchEntity> getAvailableBatchesFIFO(String prodId);

    @Query("SELECT SUM(remainingQuantity) FROM product_batches WHERE productId = :prodId")
    double getTotalBatchQuantity(String prodId);
}