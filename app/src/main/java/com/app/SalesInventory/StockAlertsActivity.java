package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StockAlertsActivity extends BaseActivity {

    private RecyclerView recyclerViewAlerts;
    private ProgressBar progressBar;
    private TextView tvNoAlerts, tvCriticalCount, tvLowStockCount, tvOverstockCount;
    private LinearLayout llAlertStats;
    private StockAlertAdapter adapter;
    private List<StockAlert> alertList;
    private AlertRepository alertRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_alerts);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Stock Alerts");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initializeViews();
        setupRecyclerView();
        loadStockAlerts();

        // If launched from a notification with a specific alertId, mark it as read and optionally scroll to it
        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("alertId")) {
            String alertId = intent.getStringExtra("alertId");
            if (alertId != null && !alertId.isEmpty()) {
                markSingleAlertAsRead(alertId);
            }
        }
    }

    private void initializeViews() {
        recyclerViewAlerts = findViewById(R.id.recyclerViewAlerts);
        progressBar = findViewById(R.id.progressBar);
        tvNoAlerts = findViewById(R.id.tvNoAlerts);
        tvCriticalCount = findViewById(R.id.tvCriticalCount);
        tvLowStockCount = findViewById(R.id.tvLowStockCount);
        tvOverstockCount = findViewById(R.id.tvOverstockCount);
        llAlertStats = findViewById(R.id.llAlertStats);
        alertList = new ArrayList<>();
        alertRepository = AlertRepository.getInstance(getApplication());
    }

    private void setupRecyclerView() {
        adapter = new StockAlertAdapter(alertList, this);
        recyclerViewAlerts.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewAlerts.setAdapter(adapter);
    }

    private void loadStockAlerts() {
        progressBar.setVisibility(View.VISIBLE);
        tvNoAlerts.setVisibility(View.GONE);
        llAlertStats.setVisibility(View.GONE);
        recyclerViewAlerts.setVisibility(View.GONE);

        alertRepository.getAllAlerts().observe(this, alerts -> {
            alertList.clear();
            int criticalCount = 0;
            int lowStockCount = 0;
            int overstockCount = 0;

            if (alerts != null) {
                for (Alert a : alerts) {
                    if (a == null) continue;
                    String type = a.getType() == null ? "" : a.getType();
                    if (!"CRITICAL_STOCK".equals(type) && !"LOW_STOCK".equals(type) && !"OVERSTOCK".equals(type)) {
                        continue;
                    }

                    StockAlert sa = new StockAlert();
                    sa.setAlertId(a.getId());
                    sa.setProductId(a.getProductId());
                    sa.setProductName(a.getMessage());
                    sa.setCurrentQuantity(0);
                    sa.setReorderLevel(0);
                    sa.setCriticalLevel(0);
                    sa.setCeilingLevel(0);
                    sa.setCategory("");
                    sa.setCreatedAt(a.getTimestamp());
                    sa.setResolved(a.isRead());

                    if ("CRITICAL_STOCK".equals(type)) {
                        sa.setAlertType("CRITICAL");
                        criticalCount++;
                    } else if ("LOW_STOCK".equals(type)) {
                        sa.setAlertType("LOW");
                        lowStockCount++;
                    } else if ("OVERSTOCK".equals(type)) {
                        sa.setAlertType("OVERSTOCK");
                        overstockCount++;
                    }

                    alertList.add(sa);
                }
            }

            Collections.sort(alertList, (a1, a2) -> Integer.compare(a2.getSeverity(), a1.getSeverity()));
            progressBar.setVisibility(View.GONE);

            if (alertList.isEmpty()) {
                tvNoAlerts.setVisibility(View.VISIBLE);
                recyclerViewAlerts.setVisibility(View.GONE);
                llAlertStats.setVisibility(View.GONE);
            } else {
                tvNoAlerts.setVisibility(View.GONE);
                recyclerViewAlerts.setVisibility(View.VISIBLE);
                llAlertStats.setVisibility(View.VISIBLE);
                tvCriticalCount.setText(String.valueOf(criticalCount));
                tvLowStockCount.setText(String.valueOf(lowStockCount));
                tvOverstockCount.setText(String.valueOf(overstockCount));
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void markSingleAlertAsRead(String alertId) {
        if (alertId == null || alertId.isEmpty()) return;
        alertRepository.markAlertAsRead(alertId, new AlertRepository.OnAlertUpdatedListener() {
            @Override
            public void onAlertUpdated() {
                // nothing else required, observers will update UI and badge
            }

            @Override
            public void onError(String error) {
                // ignore or log
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}