package com.app.SalesInventory;

import android.os.Bundle;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class DeleteProduct extends BaseActivity implements ProductDeleteAdapter.OnProductDeleteListener {
    private RecyclerView productsRecyclerView;
    private SearchView searchView;
    private TextView emptyStateTV;
    private ProductDeleteAdapter productAdapter;
    private List<Product> allProducts = new ArrayList<>();
    private List<Product> filteredProducts = new ArrayList<>();
    private ProductRepository productRepository;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_product);
        authManager = AuthManager.getInstance();
        if (!authManager.isCurrentUserAdmin()) {
            Toast.makeText(this, "Access denied", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        productRepository = SalesInventoryApplication.getProductRepository();
        productsRecyclerView = findViewById(R.id.productsRecyclerView);
        searchView = findViewById(R.id.searchView);
        emptyStateTV = findViewById(R.id.emptyStateTV);
        productAdapter = new ProductDeleteAdapter(filteredProducts, this, this);
        productsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        productsRecyclerView.setAdapter(productAdapter);
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                allProducts = new ArrayList<>(products);
                filteredProducts = new ArrayList<>(products);
                productAdapter.updateProducts(filteredProducts);
                updateEmptyState();
            }
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterProducts(query);
                return false;
            }
            @Override
            public boolean onQueryTextChange(String newText) {
                filterProducts(newText);
                return false;
            }
        });
    }

    private void filterProducts(String query) {
        filteredProducts.clear();
        if (query == null || query.isEmpty()) {
            filteredProducts.addAll(allProducts);
        } else {
            String q = query.toLowerCase();
            for (Product p : allProducts) {
                if (p.getProductName().toLowerCase().contains(q) || (p.getCategoryName() != null && p.getCategoryName().toLowerCase().contains(q))) {
                    filteredProducts.add(p);
                }
            }
        }
        productAdapter.updateProducts(filteredProducts);
        updateEmptyState();
    }

    private void updateEmptyState() {
        if (filteredProducts.isEmpty()) {
            emptyStateTV.setText("No products found");
            emptyStateTV.setVisibility(android.view.View.VISIBLE);
        } else {
            emptyStateTV.setVisibility(android.view.View.GONE);
        }
    }

    @Override
    public void onProductDelete(String productId, String productName) {
        productRepository.deleteProduct(productId, new ProductRepository.OnProductDeletedListener() {
            @Override
            public void onProductDeleted(String archiveFilename) {
                runOnUiThread(() -> Toast.makeText(DeleteProduct.this, productName + " deleted successfully", Toast.LENGTH_SHORT).show());
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(DeleteProduct.this, "Error: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }
}