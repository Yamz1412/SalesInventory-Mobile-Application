package com.app.SalesInventory;

import android.app.Application;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.SearchView;
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
    private SearchView searchView;
    private Spinner spinnerCategoryFilter;
    private ProductRemoteSyncer productRemoteSyncer;
    private String currentOwnerAdminId;
    private List<Product> currentProducts = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);
        rv = findViewById(R.id.productsRecyclerView);
        progressBar = findViewById(R.id.progressBar);
        emptyStateTV = findViewById(R.id.emptyStateTV);
        searchView = findViewById(R.id.searchView);
        spinnerCategoryFilter = findViewById(R.id.spinnerCategoryFilter);
        adapter = new ProductAdapter(this);
        rv.setLayoutManager(new GridLayoutManager(this, 2));
        rv.setAdapter(adapter);
        staffDataManager = StaffDataManager.getInstance();
        staffDataManager.startForCurrentUser(productsListener, null, null, ownerAdminId -> {
            if (ownerAdminId != null && !ownerAdminId.isEmpty()) {
                currentOwnerAdminId = ownerAdminId;
                productRemoteSyncer = new ProductRemoteSyncer((Application) getApplicationContext());
                productRemoteSyncer.startRealtimeSync(ownerAdminId);
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
            List<Product> list = new ArrayList<>();
            if (products != null) {
                for (Map<String, Object> m : products) {
                    list.add(Product.fromMap(m));
                }
            }
            currentProducts = list;
            runOnUiThread(() -> {
                progressBar.setVisibility(android.view.View.GONE);
                if (list.isEmpty()) {
                    emptyStateTV.setVisibility(android.view.View.VISIBLE);
                } else {
                    emptyStateTV.setVisibility(android.view.View.GONE);
                }
                adapter.setItems(list);
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
        adapter.setItems(out);
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
        if (productRemoteSyncer != null) productRemoteSyncer.stopRealtimeSync();
    }
}