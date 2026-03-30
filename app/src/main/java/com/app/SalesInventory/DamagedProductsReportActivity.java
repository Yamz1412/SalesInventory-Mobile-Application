package com.app.SalesInventory;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
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
import java.util.List;
import java.util.Locale;

public class DamagedProductsReportActivity extends BaseActivity {

    private TextView tvTotalItems, tvTotalLoss, tvNoData;
    private Button btnDateFilter;
    private Spinner spinnerProductLine;
    private ProgressBar progressBar;
    private RecyclerView rvDamagedItems;
    private DamagedItemAdapter adapter;

    private String currentOwnerId;

    private long filterStartDate = 0;
    private long filterEndDate = System.currentTimeMillis();
    private String currentProductLineFilter = "All Lines";

    private List<Product> currentInventory = new ArrayList<>();
    private List<StockAdjustment> masterAdjustmentList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_damaged_products_report);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Damaged / Spoiled Report");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentOwnerId = AuthManager.getInstance().getCurrentUserId();

        initializeViews();
        setupFilters();

        SalesInventoryApplication.getProductRepository().getAllProducts().observe(this, products -> {
            if (products != null) {
                currentInventory.clear();
                currentInventory.addAll(products);
                loadDamagedData();
            }
        });

        loadProductLinesFilter();
    }

    private void initializeViews() {
        tvTotalItems = findViewById(R.id.tvTotalDamagedItems);
        tvTotalLoss = findViewById(R.id.tvTotalLossValue);
        tvNoData = findViewById(R.id.tvNoData);
        btnDateFilter = findViewById(R.id.btnDateFilter);
        spinnerProductLine = findViewById(R.id.spinnerProductLine);
        progressBar = findViewById(R.id.progressBar);
        rvDamagedItems = findViewById(R.id.rvDamagedItems);

        rvDamagedItems.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DamagedItemAdapter();
        rvDamagedItems.setAdapter(adapter);
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
                btnDateFilter.setText(sdf.format(calendar.getTime()));

                processDamagedData();
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnDateFilter.setOnLongClickListener(v -> {
            filterStartDate = 0;
            filterEndDate = System.currentTimeMillis();
            btnDateFilter.setText("All Time");
            processDamagedData();
            return true;
        });
    }

    // ================================================================
    // FIX: Adaptive Dropdown Adapter for Light/Dark Theme Spinners
    // ================================================================
    private ArrayAdapter<String> getAdaptiveAdapter(List<String> items) {
        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ((TextView) view).setTextColor(textColor);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                ((TextView) view).setTextColor(textColor);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void loadProductLinesFilter() {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("ProductLines");
        ref.orderByChild("ownerAdminId").equalTo(currentOwnerId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> lines = new ArrayList<>();
                lines.add("All Lines");

                for (DataSnapshot ds : snapshot.getChildren()) {
                    String lineName = ds.child("lineName").getValue(String.class);
                    if (lineName != null && !lines.contains(lineName)) lines.add(lineName);
                }

                // Use the adaptive adapter here!
                ArrayAdapter<String> spinAdapter = getAdaptiveAdapter(lines);
                spinnerProductLine.setAdapter(spinAdapter);

                spinnerProductLine.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        currentProductLineFilter = (String) parent.getItemAtPosition(position);
                        processDamagedData();
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent) {}
                });
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadDamagedData() {
        progressBar.setVisibility(View.VISIBLE);
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("StockAdjustments");
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                masterAdjustmentList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockAdjustment adj = ds.getValue(StockAdjustment.class);
                    if (adj != null && currentOwnerId.equals(adj.getOwnerAdminId())) {
                        masterAdjustmentList.add(adj);
                    }
                }
                processDamagedData();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void processDamagedData() {
        List<DamagedItemAdapter.DamagedItem> damagedLogs = new ArrayList<>();
        double totalMonetaryLoss = 0.0;
        int itemsDamagedCount = 0;

        for (StockAdjustment adj : masterAdjustmentList) {
            long ts = adj.getTimestamp();

            if (filterStartDate == 0 || (ts >= filterStartDate && ts <= filterEndDate)) {

                String reason = adj.getReason() != null ? adj.getReason().toLowerCase() : "";

                if ("Remove Stock".equalsIgnoreCase(adj.getAdjustmentType()) &&
                        (reason.contains("damage") || reason.contains("spoil") || reason.contains("expire") || reason.contains("waste"))) {

                    double qtyLost = Math.abs(adj.getQuantityAdjusted());
                    double unitCost = 0.0;
                    String productLine = "";

                    for (Product p : currentInventory) {
                        if (p.getProductId().equals(adj.getProductId())) {
                            unitCost = p.getQuantity() > 0 ? (p.getCostPrice() / p.getQuantity()) : 0.0;
                            productLine = p.getProductLine() != null ? p.getProductLine() : "";
                            break;
                        }
                    }

                    if (!"All Lines".equals(currentProductLineFilter)) {
                        if (!currentProductLineFilter.equalsIgnoreCase(productLine)) {
                            continue;
                        }
                    }

                    double lossAmount = qtyLost * unitCost;
                    totalMonetaryLoss += lossAmount;
                    itemsDamagedCount++;

                    damagedLogs.add(new DamagedItemAdapter.DamagedItem(
                            adj.getProductName(),
                            adj.getReason(),
                            ts,
                            qtyLost,
                            lossAmount
                    ));
                }
            }
        }

        damagedLogs.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));

        tvTotalItems.setText(String.valueOf(itemsDamagedCount));
        tvTotalLoss.setText(String.format(Locale.US, "₱%,.2f", totalMonetaryLoss));

        progressBar.setVisibility(View.GONE);
        adapter.setDamagedList(damagedLogs);

        if (damagedLogs.isEmpty()) {
            tvNoData.setVisibility(View.VISIBLE);
            rvDamagedItems.setVisibility(View.GONE);
        } else {
            tvNoData.setVisibility(View.GONE);
            rvDamagedItems.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}