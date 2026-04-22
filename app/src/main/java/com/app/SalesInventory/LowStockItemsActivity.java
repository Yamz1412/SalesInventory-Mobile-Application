package com.app.SalesInventory;

import android.app.Application;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class LowStockItemsActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvNoData, tvTotalLowStock;
    private LowStockItemsAdapter adapter;
    private List<Product> lowStockList = new ArrayList<>();
    private ProductRepository productRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_low_stock_items);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Low Stock Items");
            getSupportActionBar().setSubtitle("Monitors products falling below assigned reorder thresholds");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.recyclerViewLowStock);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);
        if (tvNoData != null) {
            tvNoData.setText("All stock levels are healthy. Products requiring immediate restock will appear here.");
        }        tvTotalLowStock = findViewById(R.id.tvTotalLowStock);

        productRepository = ProductRepository.getInstance((Application) getApplicationContext());
        adapter = new LowStockItemsAdapter(this, lowStockList, productRepository);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadLowStockItems();
    }

    private void loadLowStockItems() {
        progressBar.setVisibility(View.VISIBLE);
        tvNoData.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);

        // This observe method automatically listens for real-time updates!
        productRepository.getAllProducts().observe(this, products -> {
            lowStockList.clear();
            if (products != null) {
                for (Product p : products) {
                    if (p == null || !p.isActive()) continue;

                    String type = p.getProductType() == null ? "" : p.getProductType();
                    if ("Menu".equalsIgnoreCase(type)) continue;

                    boolean isCritical = p.isCriticalStock();
                    boolean isLow = p.isLowStock();

                    if (isCritical || isLow) {
                        lowStockList.add(p);
                    }
                }
            }

            // Update the new Summary Card
            if (tvTotalLowStock != null) {
                tvTotalLowStock.setText(lowStockList.size() + (lowStockList.size() == 1 ? " item at risk" : " items at risk"));
            }

            progressBar.setVisibility(View.GONE);
            if (lowStockList.isEmpty()) {
                tvNoData.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                tvNoData.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                adapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}