package com.app.SalesInventory;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SearchView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class Inventory extends AppCompatActivity {
    private RecyclerView productsRecyclerView;
    private SearchView searchView;
    private TextView emptyStateTV;
    private ProductAdapter productAdapter;
    private List<Product> allProducts = new ArrayList<>();
    private List<Product> filteredProducts = new ArrayList<>();
    private ProductRepository productRepository;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_inventory_land);
        } else {
            setContentView(R.layout.activity_inventory);
        }
        authManager = AuthManager.getInstance();
        productRepository = SalesInventoryApplication.getProductRepository();
        productsRecyclerView = findViewById(R.id.productsRecyclerView);
        searchView = findViewById(R.id.searchView);
        emptyStateTV = findViewById(R.id.emptyStateTV);
        productAdapter = new ProductAdapter(filteredProducts, this);
        productsRecyclerView.setLayoutManager(new GridLayoutManager(this, 1));
        productsRecyclerView.setAdapter(productAdapter);
        Button batchBtn = findViewById(R.id.btnBatchOperation);
        if (batchBtn != null) {
            if (!authManager.isCurrentUserAdmin()) {
                batchBtn.setVisibility(View.GONE);
            } else {
                batchBtn.setVisibility(View.VISIBLE);
            }
        }
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
            emptyStateTV.setVisibility(View.VISIBLE);
        } else {
            emptyStateTV.setVisibility(View.GONE);
        }
    }
}