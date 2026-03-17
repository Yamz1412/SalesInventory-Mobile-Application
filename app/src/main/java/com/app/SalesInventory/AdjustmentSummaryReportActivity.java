package com.app.SalesInventory;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdjustmentSummaryReportActivity extends BaseActivity {

    private TextView tvTotalItems, tvTotalAdditions, tvTotalRemovals, tvNoData;
    private Button btnDateFilter;
    private ProgressBar progressBar;
    private RecyclerView recyclerViewReport;

    private DatabaseReference adjustmentRef;
    private String currentOwnerId;

    // Master list of raw Firebase data
    private List<StockAdjustment> masterAdjustmentList = new ArrayList<>();

    // Processed Summary List for Adapter
    private List<AdjustmentSummaryReport> summaryList = new ArrayList<>();
    private AdjustmentSummaryAdapter adapter;

    // Filter
    private long filterStartDate = 0;
    private long filterEndDate = System.currentTimeMillis();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adjustment_summary_report);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Adjustment Summary");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();
        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");

        initializeViews();
        setupFilters();
        loadAdjustments();
    }

    private void initializeViews() {
        tvTotalItems = findViewById(R.id.tvTotalItems);
        tvTotalAdditions = findViewById(R.id.tvTotalAdditions);
        tvTotalRemovals = findViewById(R.id.tvTotalRemovals);
        tvNoData = findViewById(R.id.tvNoData);
        btnDateFilter = findViewById(R.id.btnDateFilter);
        progressBar = findViewById(R.id.progressBar);
        recyclerViewReport = findViewById(R.id.recyclerViewReport);

        recyclerViewReport.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdjustmentSummaryAdapter(summaryList);
        recyclerViewReport.setAdapter(adapter);
    }

    private void setupFilters() {
        btnDateFilter.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                calendar.set(year, month, dayOfMonth, 0, 0, 0);
                filterStartDate = calendar.getTimeInMillis();

                calendar.set(year, month, dayOfMonth, 23, 59, 59);
                filterEndDate = calendar.getTimeInMillis();

                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                btnDateFilter.setText("Filter: " + sdf.format(calendar.getTime()));

                applyFilterAndGroup();
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        // Long press to reset filter
        btnDateFilter.setOnLongClickListener(v -> {
            filterStartDate = 0;
            filterEndDate = System.currentTimeMillis();
            btnDateFilter.setText("Filter by Date: All Time");
            applyFilterAndGroup();
            return true;
        });
    }

    private void loadAdjustments() {
        progressBar.setVisibility(View.VISIBLE);
        adjustmentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                masterAdjustmentList.clear();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockAdjustment adj = ds.getValue(StockAdjustment.class);
                    if (adj != null) {
                        String owner = ds.child("ownerAdminId").getValue(String.class);
                        if (currentOwnerId.equals(owner) || currentOwnerId.equals(adj.getOwnerAdminId())) {
                            masterAdjustmentList.add(adj);
                        }
                    }
                }
                applyFilterAndGroup();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void applyFilterAndGroup() {
        Map<String, AdjustmentSummaryReport> groupMap = new HashMap<>();

        double overallAdditions = 0.0;
        double overallRemovals = 0.0;

        for (StockAdjustment adj : masterAdjustmentList) {
            long adjDate = adj.getTimestamp();

            // Check Date Filter
            if (filterStartDate == 0 || (adjDate >= filterStartDate && adjDate <= filterEndDate)) {

                String pId = adj.getProductId();
                if (pId == null) continue;

                // Get or Create Report Object for this Product
                AdjustmentSummaryReport report = groupMap.get(pId);
                if (report == null) {
                    report = new AdjustmentSummaryReport(pId, adj.getProductName() != null ? adj.getProductName() : "Unknown Item");
                    groupMap.put(pId, report);
                }

                // Process Additions vs Removals
                double qty = adj.getQuantityAdjusted();
                if ("Add Stock".equalsIgnoreCase(adj.getAdjustmentType())) {
                    report.addAddition(qty);
                    overallAdditions += qty;
                } else {
                    report.addRemoval(Math.abs(qty));
                    overallRemovals += Math.abs(qty);
                }

                // Save Reason
                report.addReason(adj.getReason());
            }
        }

        // Convert Map to List for the Adapter
        summaryList.clear();
        summaryList.addAll(groupMap.values());

        // Sort alphabetically by product name
        Collections.sort(summaryList, (r1, r2) -> r1.getProductName().compareToIgnoreCase(r2.getProductName()));

        // Update Dashboard Cards
        tvTotalItems.setText(String.valueOf(summaryList.size()));
        tvTotalAdditions.setText("+" + formatQuantity(overallAdditions));
        tvTotalRemovals.setText("-" + formatQuantity(overallRemovals));

        progressBar.setVisibility(View.GONE);

        if (summaryList.isEmpty()) {
            tvNoData.setVisibility(View.VISIBLE);
            recyclerViewReport.setVisibility(View.GONE);
        } else {
            tvNoData.setVisibility(View.GONE);
            recyclerViewReport.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    private String formatQuantity(double value) {
        if (value % 1 == 0) return String.valueOf((long) value);
        return String.format(Locale.US, "%.2f", value);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}