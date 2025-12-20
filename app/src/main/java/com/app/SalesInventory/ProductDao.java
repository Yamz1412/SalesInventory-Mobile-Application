package com.app.SalesInventory;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ProductDao {

    @Query("SELECT * FROM products ORDER BY lastUpdated DESC")
    LiveData<List<ProductEntity>> getAllProductsLive();

    @Query("SELECT * FROM products WHERE syncState <> 'SYNCED' ORDER BY lastUpdated DESC")
    List<ProductEntity> getPendingProductsSync();

    @Query("SELECT * FROM products WHERE productId = :productId LIMIT 1")
    ProductEntity getByProductIdSync(String productId);

    @Query("SELECT * FROM products WHERE localId = :localId LIMIT 1")
    ProductEntity getByLocalId(long localId);

    @Query("SELECT * FROM products WHERE productName = :productName LIMIT 1")
    ProductEntity getByProductNameSync(String productName);

    @Insert
    long insert(ProductEntity entity);

    @Update
    void update(ProductEntity entity);

    @Query("DELETE FROM products WHERE localId = :localId")
    void deleteByLocalId(long localId);

    @Query("UPDATE products SET productId = :productId, syncState = :syncState WHERE localId = :localId")
    void setSyncInfo(long localId, String productId, String syncState);

    @Query("SELECT * FROM products ORDER BY lastUpdated DESC")
    List<ProductEntity> getAllProductsSync();

    @Query("UPDATE products SET quantity = :quantity, lastUpdated = :lastUpdated, syncState = :syncState WHERE localId = :localId")
    void updateQuantityByLocalId(long localId, int quantity, long lastUpdated, String syncState);
}