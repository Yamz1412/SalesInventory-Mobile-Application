package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StockAlertsActivity extends BaseActivity  {

    private RecyclerView recyclerViewAlerts;
    private ProgressBar progressBar;
    private TextView tvNoAlerts, tvCriticalCount, tvLowStockCount, tvOverstockCount;
    private LinearLayout llAlertStats;
    private StockAlertAdapter adapter;
    private List<StockAlert> alertList;
    private DatabaseReference productRef;

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
        productRef = FirebaseDatabase.getInstance().getReference("Product");
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

        productRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                alertList.clear();
                int criticalCount = 0;
                int lowStockCount = 0;
                int overstockCount = 0;

                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Product product = dataSnapshot.getValue(Product.class);
                    if (product != null && product.isActive()) {
                        StockAlert alert = null;

                        // Check for critical stock
                        if (product.isCriticalStock()) {
                            alert = new StockAlert(
                                    product.getProductId(),
                                    product.getProductId(),
                                    product.getProductName(),
                                    product.getQuantity(),
                                    product.getReorderLevel(),
                                    product.getCriticalLevel(),
                                    product.getCeilingLevel(),
                                    "CRITICAL",
                                    product.getCategoryName(),
                                    System.currentTimeMillis(),
                                    false
                            );
                            criticalCount++;
                        }
                        // Check for low stock
                        else if (product.isLowStock()) {
                            alert = new StockAlert(
                                    product.getProductId(),
                                    product.getProductId(),
                                    product.getProductName(),
                                    product.getQuantity(),
                                    product.getReorderLevel(),
                                    product.getCriticalLevel(),
                                    product.getCeilingLevel(),
                                    "LOW",
                                    product.getCategoryName(),
                                    System.currentTimeMillis(),
                                    false
                            );
                            lowStockCount++;
                        }
                        // Check for overstock
                        else if (product.isOverstock()) {
                            alert = new StockAlert(
                                    product.getProductId(),
                                    product.getProductId(),
                                    product.getProductName(),
                                    product.getQuantity(),
                                    product.getReorderLevel(),
                                    product.getCriticalLevel(),
                                    product.getCeilingLevel(),
                                    "OVERSTOCK",
                                    product.getCategoryName(),
                                    System.currentTimeMillis(),
                                    false
                            );
                            overstockCount++;
                        }

                        if (alert != null) {
                            alertList.add(alert);
                        }
                    }
                }

                // Sort by severity (Critical first, then Low, then Overstock)
                Collections.sort(alertList, (a, b) -> Integer.compare(b.getSeverity(), a.getSeverity()));

                progressBar.setVisibility(View.GONE);

                if (alertList.isEmpty()) {
                    tvNoAlerts.setVisibility(View.VISIBLE);
                    recyclerViewAlerts.setVisibility(View.GONE);
                    llAlertStats.setVisibility(View.GONE);
                } else {
                    tvNoAlerts.setVisibility(View.GONE);
                    recyclerViewAlerts.setVisibility(View.VISIBLE);
                    llAlertStats.setVisibility(View.VISIBLE);

                    // Update stats
                    tvCriticalCount.setText(String.valueOf(criticalCount));
                    tvLowStockCount.setText(String.valueOf(lowStockCount));
                    tvOverstockCount.setText(String.valueOf(overstockCount));

                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(StockAlertsActivity.this,
                        "Error loading alerts: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}