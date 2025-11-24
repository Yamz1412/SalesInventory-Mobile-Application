package com.app.SalesInventory;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;
import java.util.List;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import com.app.SalesInventory.R;
import com.app.SalesInventory.SalesInventoryApplication;

public class ProductActivity extends AppCompatActivity {
    private static final String TAG = "ProductActivity";
    private ProductRepository productRepository;
    private RecyclerView productsRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        productRepository = SalesInventoryApplication.getProductRepository();

        SalesInventoryApplication.BaseContext.setContext(this);

        // Initialize UI
        initializeUI();

        // Start listening to products
        observeProducts();
    }

    /**
     * Initialize UI components
     */
    private void initializeUI() {
        productsRecyclerView = findViewById(R.id.productsRecyclerView);
        // Set up recycler view adapter
    }

    /**
     * Observe products from Firestore
     */
    private void observeProducts() {
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                Log.d(TAG, "Products updated: " + products.size());
                // Update RecyclerView with products
            }
        });
    }

    /**
     * Example: Add new product
     */
    public void addNewProduct(Product product) {
        productRepository.addProduct(product, new ProductRepository.OnProductAddedListener() {
            @Override
            public void onProductAdded(String productId) {
                Toast.makeText(ProductActivity.this, "Product added: " + productId, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Product added successfully");
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ProductActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error adding product: " + error);
            }
        });
    }

    /**
     * Example: Update product
     */
    public void updateProduct(Product product) {
        productRepository.updateProduct(product, new ProductRepository.OnProductUpdatedListener() {
            @Override
            public void onProductUpdated() {
                Toast.makeText(ProductActivity.this, "Product updated", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Product updated successfully");
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ProductActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error updating product: " + error);
            }
        });
    }

    /**
     * Example: Delete product
     */
    public void deleteProduct(String productId) {
        productRepository.deleteProduct(productId, new ProductRepository.OnProductDeletedListener() {
            @Override
            public void onProductDeleted() {
                Toast.makeText(ProductActivity.this, "Product deleted", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Product deleted successfully");
            }

            @Override
            public void onError(String error) {
                Toast.makeText(ProductActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error deleting product: " + error);
            }
        });
    }

    /**
     * Example: Get product by ID
     */
    public void getProductById(String productId) {
        productRepository.getProductById(productId, new ProductRepository.OnProductFetchedListener() {
            @Override
            public void onProductFetched(Product product) {
                Log.d(TAG, "Product fetched: " + product.getProductName());
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error fetching product: " + error);
            }
        });
    }

    public void getProductsByCategory(String category) {
        productRepository.getProductsByCategory(category, new ProductRepository.OnProductsFetchedListener() {
            @Override
            public void onProductsFetched(List<Product> products) {
                Log.d(TAG, "Products fetched for category: " + category + ", Count: " + products.size());
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error fetching products: " + error);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}