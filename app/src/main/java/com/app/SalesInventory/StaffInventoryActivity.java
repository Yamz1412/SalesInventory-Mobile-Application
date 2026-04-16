package com.app.SalesInventory;

import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.SearchView; // FIX: Changed to standard widget to match XML
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class StaffInventoryActivity extends AppCompatActivity {

    private StaffDataManager staffDataManager;
    private ProductAdapter adapter;
    private RecyclerView rv;
    private ProgressBar progressBar;
    private TextView emptyStateTV;
    private SearchView searchView; // FIX: Changed to standard widget
    private Spinner spinnerCategoryFilter;
    private ProductRemoteSyncer productRemoteSyncer;
    private String currentOwnerAdminId;
    private List<Product> currentProducts = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);
        rv = findViewById(R.id.productsRecyclerView);
        View btnAddProduct = findViewById(R.id.btn_add_product);
        View btnAdjustStock = findViewById(R.id.btn_adjust_stock);
        View btnArchive = findViewById(R.id.btn_archive);
        View layoutArchiveContainer = findViewById(R.id.layout_archive_container);

        if (btnAddProduct != null) btnAddProduct.setVisibility(View.GONE);
        if (btnAdjustStock != null) btnAdjustStock.setVisibility(View.GONE);
        if (btnArchive != null) btnArchive.setVisibility(View.GONE);
        if (layoutArchiveContainer != null) layoutArchiveContainer.setVisibility(View.GONE);

        progressBar = findViewById(R.id.progressBar);
        emptyStateTV = findViewById(R.id.emptyStateTV);
        searchView = findViewById(R.id.searchView);
        spinnerCategoryFilter = findViewById(R.id.spinnerCategoryFilter);

        adapter = new ProductAdapter(currentProducts, this);

        rv.setLayoutManager(new GridLayoutManager(this, 2));
        rv.setAdapter(adapter);

        staffDataManager = StaffDataManager.getInstance();
        staffDataManager.startForCurrentUser(productsListener, null, null, ownerAdminId -> {
            if (ownerAdminId != null && !ownerAdminId.isEmpty()) {
                currentOwnerAdminId = ownerAdminId;
                productRemoteSyncer = new ProductRemoteSyncer((Application) getApplicationContext());
                productRemoteSyncer.startListening();
            }
        });

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                filterLocal(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterLocal(newText);
                return true;
            }
        });
    }

    private final StaffDataManager.ProductListener productsListener = new StaffDataManager.ProductListener() {
        @Override
        public void onProducts(List<Map<String, Object>> products) {
            runOnUiThread(() -> {
                if (progressBar != null) progressBar.setVisibility(android.view.View.GONE);
                currentProducts.clear();
                List<Product> list = new ArrayList<>();

                for (Map<String, Object> map : products) {
                    Product p = Product.fromMap(map);

                    boolean isSalesProduct = "Menu".equalsIgnoreCase(p.getProductType()) ||
                            "finished".equalsIgnoreCase(p.getProductType());

                    if (!isSalesProduct) {
                        currentProducts.add(p);
                        list.add(p);
                    }
                }

                if (emptyStateTV != null) {
                    emptyStateTV.setVisibility(list.isEmpty() ? android.view.View.VISIBLE : android.view.View.GONE);
                }
                if (adapter != null) {
                    adapter.updateProducts(list);
                }
            });
        }

        @Override
        public void onError(Exception e) {
            runOnUiThread(() -> {
                progressBar.setVisibility(android.view.View.GONE);
                Toast.makeText(StaffInventoryActivity.this, "Failed to load products: " + e.getMessage(), Toast.LENGTH_LONG).show();
            });
        }
    };

    private void filterLocal(String q) {
        if (q == null) q = "";
        q = q.trim().toLowerCase();
        List<Product> out = new ArrayList<>();
        for (Product p : currentProducts) {
            if (p == null) continue;
            String name = p.getProductName() != null ? p.getProductName().toLowerCase() : "";
            if (name.contains(q)) out.add(p);
        }
        adapter.updateProducts(out);
        if (out.isEmpty()) {
            emptyStateTV.setVisibility(android.view.View.VISIBLE);
        } else {
            emptyStateTV.setVisibility(android.view.View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (staffDataManager != null) staffDataManager.stopAll();
        if (productRemoteSyncer != null) productRemoteSyncer.stopListening();
    }
}