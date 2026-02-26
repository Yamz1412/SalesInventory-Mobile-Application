package com.app.SalesInventory;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import java.util.List;

@Dao
public interface SalesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insertOrder(SalesOrderEntity order);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertOrderItems(List<SalesOrderItemEntity> items);

    @Update
    void updateOrder(SalesOrderEntity order);

    @Query("SELECT * FROM sales_orders ORDER BY orderDate DESC")
    List<SalesOrderEntity> getAllOrdersSync();

    @Transaction
    @Query("SELECT * FROM sales_orders ORDER BY orderDate DESC")
    List<SalesOrderWithItems> getAllOrdersWithItemsSync();

    @Transaction
    @Query("SELECT * FROM sales_orders WHERE localId = :localId LIMIT 1")
    SalesOrderWithItems getOrderWithItemsByLocalId(long localId);

    @Query("DELETE FROM sales_orders")
    void deleteAllOrders();
}