package com.app.SalesInventory;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ProductDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(ProductEntity product);

    @Update
    void update(ProductEntity product);

    @Query("SELECT * FROM products ORDER BY productName COLLATE NOCASE ASC")
    LiveData<List<ProductEntity>> getAllProductsLive();

    @Query("SELECT * FROM products WHERE productId = :productId LIMIT 1")
    ProductEntity getByProductIdSync(String productId);

    @Query("SELECT * FROM products WHERE localId = :localId LIMIT 1")
    ProductEntity getByLocalId(long localId);

    @Query("SELECT * FROM products WHERE syncState = 'PENDING' OR syncState = 'ERROR' OR syncState = 'DELETE_PENDING'")
    List<ProductEntity> getPendingProductsSync();

    @Query("UPDATE products SET productId = :productId, syncState = :syncState WHERE localId = :localId")
    void setSyncInfo(long localId, String productId, String syncState);

    @Query("DELETE FROM products WHERE productId = :productId")
    void deleteByProductId(String productId);

    @Query("DELETE FROM products WHERE localId = :localId")
    void deleteByLocalId(long localId);
}