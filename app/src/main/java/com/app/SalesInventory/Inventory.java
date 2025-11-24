package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.util.Log;
import android.widget.SearchView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class Inventory extends AppCompatActivity {
    private static final String TAG = "Inventory";

    // UI Components
    private RecyclerView productsRecyclerView;
    private SearchView searchView;
    private TextView emptyStateTV;

    // Adapter
    private ProductAdapter productAdapter;
    private List<Product> allProducts = new ArrayList<>();
    private List<Product> filteredProducts = new ArrayList<>();

    // Repository
    private ProductRepository productRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        // Initialize repository
        productRepository = SalesInventoryApplication.getProductRepository();

        // Initialize UI
        initializeUI();

        // Set up RecyclerView
        setupRecyclerView();

        // Observe products
        observeProducts();

        // Setup search
        setupSearch();
    }

    /**
     * Initialize UI components
     */
    private void initializeUI() {
        productsRecyclerView = findViewById(R.id.productsRecyclerView);
        searchView = findViewById(R.id.searchView);
        emptyStateTV = findViewById(R.id.emptyStateTV);
    }

    /**
     * Setup RecyclerView
     */
    private void setupRecyclerView() {
        productAdapter = new ProductAdapter(filteredProducts, this);
        productsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        productsRecyclerView.setAdapter(productAdapter);
    }

    /**
     * Observe products from Firestore
     */
    private void observeProducts() {
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                allProducts = new ArrayList<>(products);
                filteredProducts = new ArrayList<>(products);
                productAdapter.updateProducts(filteredProducts);

                // Update empty state
                updateEmptyState();

                Log.d(TAG, "Products loaded: " + products.size());
            }
        });
    }

    /**
     * Setup search functionality
     */
    private void setupSearch() {
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

    /**
     * Filter products based on search query
     */
    private void filterProducts(String query) {
        filteredProducts.clear();

        if (query.isEmpty()) {
            filteredProducts.addAll(allProducts);
        } else {
            String queryLowerCase = query.toLowerCase();
            for (Product product : allProducts) {
                if (product.getProductName().toLowerCase().contains(queryLowerCase)
                        || product.getCategoryName().toLowerCase().contains(queryLowerCase)) {
                    filteredProducts.add(product);
                }
            }
        }

        productAdapter.updateProducts(filteredProducts);
        updateEmptyState();
    }

    /**
     * Update empty state message
     */
    private void updateEmptyState() {
        if (filteredProducts.isEmpty()) {
            emptyStateTV.setText("No products found");
            emptyStateTV.setVisibility(android.view.View.VISIBLE);
        } else {
            emptyStateTV.setVisibility(android.view.View.GONE);
        }
    }
}