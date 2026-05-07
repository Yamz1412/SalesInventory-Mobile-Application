package com.app.SalesInventory;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.HashSet;
import java.util.TimeZone;

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

    private TextView tvTotalItems, tvTotalLoss, tvNoData;
    private Button btnDateFilter;
    private ProgressBar progressBar;
    private RecyclerView recyclerViewReport;

    private DatabaseReference adjustmentRef;
    private String currentOwnerId;

    private List<Product> currentInventory = new ArrayList<>();
    private List<StockAdjustment> masterAdjustmentList = new ArrayList<>();
    private List<AdjustmentSummaryReport> summaryList = new ArrayList<>();
    private AdjustmentSummaryAdapter adapter;

    private long filterStartDate = 0;
    private long filterEndDate = System.currentTimeMillis();
    private Spinner spinnerTypeFilter;
    private String currentTypeFilter = "All Reports";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adjustment_summary_report);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Adjustment Summary");
            getSupportActionBar().setSubtitle("Tracks inventory discrepancies, damages, and corrections");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (currentOwnerId == null || currentOwnerId.isEmpty()) {
            currentOwnerId = AuthManager.getInstance().getCurrentUserId();
        }

        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");

        initializeViews();
        setupFilters();

        SalesInventoryApplication.getProductRepository().getAllProducts().observe(this, products -> {
            if (products != null) {
                currentInventory.clear();
                currentInventory.addAll(products);
                applyFilterAndGroup();
            }
        });
        loadAdjustments();
    }

    private void initializeViews() {
        tvTotalItems = findViewById(R.id.tvTotalAdjustedItems);
        tvTotalLoss = findViewById(R.id.tvTotalLossValue);
        tvNoData = findViewById(R.id.tvNoData);
        if (tvNoData != null) {
            tvNoData.setText("No adjustments found for this date. Manual corrections, damages, and stock additions will appear here.");
        }

        btnDateFilter = findViewById(R.id.btnDateFilter);
        progressBar = findViewById(R.id.progressBar);
        recyclerViewReport = findViewById(R.id.recyclerViewReport);

        spinnerTypeFilter = findViewById(R.id.spinnerTypeFilter);
        if (spinnerTypeFilter != null) {
            String[] types = {"All Reports", "Additions Only", "Deductions Only"};
            ArrayAdapter<String> spinAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, types);
            spinnerTypeFilter.setAdapter(spinAdapter);

            spinnerTypeFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    currentTypeFilter = types[position];
                    applyFilterAndGroup();
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        recyclerViewReport.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdjustmentSummaryAdapter(summaryList);
        recyclerViewReport.setAdapter(adapter);
    }

    private void setupFilters() {
        btnDateFilter.setOnClickListener(v -> {
            HashSet<Long> validDates = new HashSet<>();
            Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

            for (StockAdjustment adj : masterAdjustmentList) {
                utcCal.setTimeInMillis(adj.getTimestamp());
                utcCal.set(Calendar.HOUR_OF_DAY, 0); utcCal.set(Calendar.MINUTE, 0);
                utcCal.set(Calendar.SECOND, 0); utcCal.set(Calendar.MILLISECOND, 0);
                validDates.add(utcCal.getTimeInMillis());
            }

            CalendarConstraints.Builder constraintsBuilder = new CalendarConstraints.Builder();
            constraintsBuilder.setValidator(new DeliveryReportActivity.AvailableDateValidator(validDates));

            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Adjustment Date")
                    .setCalendarConstraints(constraintsBuilder.build())
                    .setTheme(R.style.CustomCalendarTheme)
                    .build();

            datePicker.addOnPositiveButtonClickListener(selection -> {
                Calendar selectedCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                selectedCal.setTimeInMillis(selection);
                Calendar localCal = Calendar.getInstance();
                localCal.set(selectedCal.get(Calendar.YEAR), selectedCal.get(Calendar.MONTH), selectedCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
                filterStartDate = localCal.getTimeInMillis();
                localCal.set(Calendar.HOUR_OF_DAY, 23); localCal.set(Calendar.MINUTE, 59); localCal.set(Calendar.SECOND, 59);
                filterEndDate = localCal.getTimeInMillis();

                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.US);
                btnDateFilter.setText("Filter: " + sdf.format(localCal.getTime()));
                applyFilterAndGroup();
            });
            datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
        });

        btnDateFilter.setOnLongClickListener(v -> {
            filterStartDate = 0; filterEndDate = System.currentTimeMillis();
            btnDateFilter.setText("Filter by Date: All Time");
            applyFilterAndGroup();
            return true;
        });
    }

    private ValueEventListener adjustmentListener;

    private void loadAdjustments() {
        progressBar.setVisibility(View.VISIBLE);
        if (adjustmentListener != null) {
            adjustmentRef.removeEventListener(adjustmentListener);
        }

        adjustmentListener = adjustmentRef.addValueEventListener(new ValueEventListener() {
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
        if (masterAdjustmentList == null) return;
        Map<String, AdjustmentSummaryReport> groupMap = new HashMap<>();
        double overallLossValue = 0.0;

        for (StockAdjustment adj : masterAdjustmentList) {
            long adjDate = adj.getTimestamp();

            if (filterStartDate == 0 || (adjDate >= filterStartDate && adjDate <= filterEndDate)) {

                String type = adj.getAdjustmentType() != null ? adj.getAdjustmentType() : "";
                boolean isDeduction = type.toLowerCase().contains("deduct") || type.toLowerCase().contains("remove") || type.equalsIgnoreCase("Deduction");

                // CRITICAL FIX: Ignore records that don't match the selected Spinner Filter
                if (currentTypeFilter.equals("Additions Only") && isDeduction) continue;
                if (currentTypeFilter.equals("Deductions Only") && !isDeduction) continue;

                String pId = adj.getProductId();
                if (pId == null || pId.isEmpty()) pId = adj.getProductName();
                if (pId == null || pId.isEmpty()) continue;

                AdjustmentSummaryReport report = groupMap.get(pId);
                if (report == null) {
                    report = new AdjustmentSummaryReport(pId, adj.getProductName() != null ? adj.getProductName() : "Unknown Item");
                    groupMap.put(pId, report);
                }

                double qty = adj.getQuantityAdjusted();

                if (isDeduction) {
                    double absQty = Math.abs(qty);
                    report.addRemoval(absQty);

                    double unitCost = 0.0;
                    for (Product p : currentInventory) {
                        String invId = p.getProductId() != null ? p.getProductId() : p.getProductName();
                        if (invId != null && invId.equals(pId)) {
                            unitCost = p.getCostPrice();
                            break;
                        }
                    }
                    overallLossValue += (absQty * unitCost);
                } else {
                    report.addAddition(Math.abs(qty));
                }

                String reason = adj.getReason();
                if (reason == null || reason.isEmpty()) reason = type;
                report.addReason(reason);
            }
        }

        summaryList.clear();

        // Remove items from the list if the filter stripped away all of their additions/deductions
        for (AdjustmentSummaryReport report : groupMap.values()) {
            if (report.getAdditions() > 0 || report.getRemovals() > 0) {
                summaryList.add(report);
            }
        }

        Collections.sort(summaryList, (r1, r2) -> r1.getProductName().compareToIgnoreCase(r2.getProductName()));

        if (tvTotalItems != null) tvTotalItems.setText(String.valueOf(summaryList.size()));
        if (tvTotalLoss != null) tvTotalLoss.setText(String.format(Locale.US, "₱%,.2f", overallLossValue));
        if (progressBar != null) progressBar.setVisibility(View.GONE);

        if (summaryList.isEmpty()) {
            if (tvNoData != null) tvNoData.setVisibility(View.VISIBLE);
            if (recyclerViewReport != null) recyclerViewReport.setVisibility(View.GONE);
        } else {
            if (tvNoData != null) tvNoData.setVisibility(View.GONE);
            if (recyclerViewReport != null) recyclerViewReport.setVisibility(View.VISIBLE);
            if (adapter != null) adapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}