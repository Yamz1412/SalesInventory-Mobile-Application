package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Inventory extends BaseActivity {
    public static final String EXTRA_SHOW_LOW_STOCK_ONLY = "showLowStockOnly";
    public static final String EXTRA_SHOW_NEAR_EXPIRY_ONLY = "showNearExpiryOnly";

    private RecyclerView productsRecyclerView;
    private SearchView searchView;
    private TextView emptyStateTV;
    private ProductAdapter productAdapter;
    private List<Product> allProducts = new ArrayList<>();
    private List<Product> filteredProducts = new ArrayList<>();
    private ProductRepository productRepository;
    private AuthManager authManager;
    private Button btnAdjustStock;
    private Button btnAdjustmentHistory;
    private Button btnAdjustmentSummary;
    private Spinner spinnerCategoryFilter;
    private String currentSearchQuery = "";
    private String currentCategoryFilter = "All";
    private CriticalStockNotifier criticalNotifier;
    private ProductRepository.OnCriticalStockListener criticalListener;
    private boolean showLowStockOnly = false;
    private boolean showNearExpiryOnly = false;
    private TextView tvTotalCount;
    private TextView tvLowStockWarning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        authManager = AuthManager.getInstance();
        productRepository = SalesInventoryApplication.getProductRepository();

        productsRecyclerView = findViewById(R.id.productsRecyclerView);
        searchView = findViewById(R.id.searchView);
        emptyStateTV = findViewById(R.id.emptyStateTV);
        btnAdjustStock = findViewById(R.id.btn_adjust_stock);
        btnAdjustmentHistory = findViewById(R.id.btn_adjustment_history);
        btnAdjustmentSummary = findViewById(R.id.btn_adjustment_summary);
        spinnerCategoryFilter = findViewById(R.id.spinnerCategoryFilter);
        tvTotalCount = findViewById(R.id.TotalCountTV);
        tvLowStockWarning = findViewById(R.id.tvLowStockWarning);

        showLowStockOnly = getIntent().getBooleanExtra(EXTRA_SHOW_LOW_STOCK_ONLY, false);
        showNearExpiryOnly = getIntent().getBooleanExtra(EXTRA_SHOW_NEAR_EXPIRY_ONLY, false);

        productAdapter = new ProductAdapter(filteredProducts, this);
        productsRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        productsRecyclerView.setAdapter(productAdapter);

        authManager.refreshCurrentUserStatus(success -> runOnUiThread(() -> {
            boolean isAdmin = authManager.isCurrentUserAdmin();
            if (isAdmin) {
                if (btnAdjustStock != null) btnAdjustStock.setVisibility(View.VISIBLE);
                if (btnAdjustmentHistory != null) btnAdjustmentHistory.setVisibility(View.VISIBLE);
                if (btnAdjustmentSummary != null) btnAdjustmentSummary.setVisibility(View.VISIBLE);
            } else {
                if (btnAdjustStock != null) btnAdjustStock.setVisibility(View.GONE);
                if (btnAdjustmentHistory != null) btnAdjustmentHistory.setVisibility(View.GONE);
                if (btnAdjustmentSummary != null) btnAdjustmentSummary.setVisibility(View.GONE);
            }
        }));

        setupAdjustmentButtons();

        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                allProducts = new ArrayList<>(products);
                updateHeaderStats();
                setupCategoryFilterSpinner();
                applyFilters();
            }
        });

        criticalNotifier = CriticalStockNotifier.getInstance();
        criticalListener = product -> runOnUiThread(() ->
                criticalNotifier.showCriticalDialog(this, product)
        );
        productRepository.registerCriticalStockListener(criticalListener);

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentSearchQuery = query == null ? "" : query;
                applyFilters();
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentSearchQuery = newText == null ? "" : newText;
                applyFilters();
                return false;
            }
        });
    }

    private void setupAdjustmentButtons() {
        if (btnAdjustStock != null) {
            btnAdjustStock.setOnClickListener(v ->
                    startActivity(new Intent(Inventory.this, StockAdjustmentActivity.class)));
        }
        if (btnAdjustmentHistory != null) {
            btnAdjustmentHistory.setOnClickListener(v ->
                    startActivity(new Intent(Inventory.this, AdjustmentHistoryActivity.class)));
        }
        if (btnAdjustmentSummary != null) {
            btnAdjustmentSummary.setOnClickListener(v ->
                    startActivity(new Intent(Inventory.this, AdjustmentSummaryReportActivity.class)));
        }
    }

    private void setupCategoryFilterSpinner() {
        Set<String> categories = new HashSet<>();
        for (Product p : allProducts) {
            if (p == null || !p.isActive()) continue;
            String type = p.getProductType() == null ? "" : p.getProductType();
            if ("Menu".equalsIgnoreCase(type)) continue;
            String c = p.getCategoryName();
            if (c != null && !c.isEmpty()) {
                categories.add(c);
            }
        }
        List<String> options = new ArrayList<>();
        options.add("All");
        options.addAll(categories);

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                options
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategoryFilter.setAdapter(adapter);

        int index = options.indexOf(currentCategoryFilter);
        if (index < 0) index = 0;
        spinnerCategoryFilter.setSelection(index);

        spinnerCategoryFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selected = (String) parent.getItemAtPosition(position);
                currentCategoryFilter = selected == null ? "All" : selected;
                applyFilters();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });
    }

    private void applyFilters() {
        String q = currentSearchQuery == null ? "" : currentSearchQuery.toLowerCase();
        String cat = currentCategoryFilter == null ? "All" : currentCategoryFilter;

        filteredProducts.clear();
        for (Product p : allProducts) {
            if (p == null || !p.isActive()) continue;

            String type = p.getProductType() == null ? "" : p.getProductType();
            if ("Menu".equalsIgnoreCase(type)) continue;

            boolean matchesSearch =
                    q.isEmpty()
                            || p.getProductName().toLowerCase().contains(q)
                            || (p.getCategoryName() != null && p.getCategoryName().toLowerCase().contains(q));

            boolean matchesCategory =
                    "All".equalsIgnoreCase(cat)
                            || (p.getCategoryName() != null && p.getCategoryName().equalsIgnoreCase(cat));

            if (!matchesSearch || !matchesCategory) continue;

            if (showLowStockOnly) {
                boolean isCritical = p.isCriticalStock();
                boolean isLow = p.isLowStock();
                if (!isCritical && !isLow) continue;
            }

            if (showNearExpiryOnly) {
                long expiry = p.getExpiryDate();
                if (expiry <= 0) continue;
                long now = System.currentTimeMillis();
                long diffMillis = expiry - now;
                long days = diffMillis / (24L * 60L * 60L * 1000L);
                if (diffMillis > 0 && days > 7) {
                    continue;
                }
            }

            filteredProducts.add(p);
        }
        productAdapter.updateProducts(filteredProducts);
        updateEmptyState();
    }

    private void updateHeaderStats() {
        int total = 0;
        int lowOrCritical = 0;
        for (Product p : allProducts) {
            if (p == null || !p.isActive()) continue;
            String type = p.getProductType() == null ? "" : p.getProductType();
            if ("Menu".equalsIgnoreCase(type)) continue;
            total++;
            if (p.isCriticalStock() || p.isLowStock()) {
                lowOrCritical++;
            }
        }
        if (tvTotalCount != null) {
            tvTotalCount.setText(String.valueOf(total));
        }
        if (tvLowStockWarning != null) {
            if (lowOrCritical > 0) {
                tvLowStockWarning.setText(lowOrCritical + " alerts");
                tvLowStockWarning.setVisibility(View.VISIBLE);
            } else {
                tvLowStockWarning.setVisibility(View.GONE);
            }
        }
    }

    private void updateEmptyState() {
        if (filteredProducts.isEmpty()) {
            if (showLowStockOnly) {
                emptyStateTV.setText("No low stock products found");
            } else if (showNearExpiryOnly) {
                emptyStateTV.setText("No near-expiry products found");
            } else {
                emptyStateTV.setText("No products found");
            }
            emptyStateTV.setVisibility(View.VISIBLE);
        } else {
            emptyStateTV.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (productRepository != null && criticalListener != null) {
            productRepository.unregisterCriticalStockListener(criticalListener);
        }
    }
}