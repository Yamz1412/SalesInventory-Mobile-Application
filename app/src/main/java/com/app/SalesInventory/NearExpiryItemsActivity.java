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

public class NearExpiryItemsActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvNoData;
    private NearExpiryItemsAdapter adapter;
    private List<Product> nearExpiryList = new ArrayList<>();
    private ProductRepository productRepository;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_near_expiry_items);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Near Expiry Products");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerView = findViewById(R.id.recyclerViewNearExpiry);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);

        productRepository = ProductRepository.getInstance((Application) getApplicationContext());
        adapter = new NearExpiryItemsAdapter(this, nearExpiryList, productRepository);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadNearExpiryItems();
    }

    private void loadNearExpiryItems() {
        progressBar.setVisibility(View.VISIBLE);
        tvNoData.setVisibility(View.GONE);
        recyclerView.setVisibility(View.GONE);

        productRepository.getAllProducts().observe(this, products -> {
            nearExpiryList.clear();
            long now = System.currentTimeMillis();

            if (products != null) {
                for (Product p : products) {
                    if (p == null || !p.isActive()) continue;
                    long expiry = p.getExpiryDate();
                    if (expiry <= 0) continue;
                    long diffMillis = expiry - now;
                    long days = diffMillis / (24L * 60L * 60L * 1000L);
                    if (diffMillis <= 0 || days <= 7) {
                        nearExpiryList.add(p);
                    }
                }
            }

            progressBar.setVisibility(View.GONE);
            if (nearExpiryList.isEmpty()) {
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