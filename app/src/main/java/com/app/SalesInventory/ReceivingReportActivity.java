package com.app.SalesInventory;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
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
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ReceivingReportActivity extends BaseActivity {

    private TextView tvTotalOrders, tvTotalSpent, tvNoData;
    private ProgressBar progressBar;
    private RecyclerView recyclerViewReceiving;
    private Button btnDateFilter;
    private Spinner spinnerSupplier;

    private DatabaseReference poRef;
    private String currentOwnerId;

    private List<PurchaseOrder> masterPOList = new ArrayList<>();
    private List<PurchaseOrder> filteredPOList = new ArrayList<>();
    private ReceivingAdapter adapter;

    // Filters
    private long filterStartDate = 0;
    private long filterEndDate = System.currentTimeMillis();
    private String selectedSupplier = "All Suppliers";
    private List<String> supplierList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiving_report);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Receiving Report");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();
        poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");

        initializeViews();
        setupFilters();
        loadReceivingData();
    }

    private void initializeViews() {
        tvTotalOrders = findViewById(R.id.tvTotalOrders);
        tvTotalSpent = findViewById(R.id.tvTotalValue);
        tvNoData = findViewById(R.id.tvNoData);
        progressBar = findViewById(R.id.progressBar);
        recyclerViewReceiving = findViewById(R.id.recyclerViewReceiving);
        btnDateFilter = findViewById(R.id.btnDateFilter);
        spinnerSupplier = findViewById(R.id.spinnerSupplier);

        recyclerViewReceiving.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReceivingAdapter();
        recyclerViewReceiving.setAdapter(adapter);
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

                applyFilters();
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        btnDateFilter.setOnLongClickListener(v -> {
            filterStartDate = 0;
            filterEndDate = System.currentTimeMillis();
            btnDateFilter.setText("All Time");
            applyFilters();
            return true;
        });
    }

    private void loadReceivingData() {
        progressBar.setVisibility(View.VISIBLE);
        poRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                masterPOList.clear();
                supplierList.clear();
                supplierList.add("All Suppliers");

                for (DataSnapshot ds : snapshot.getChildren()) {
                    PurchaseOrder po = ds.getValue(PurchaseOrder.class);
                    if (po != null && currentOwnerId.equals(po.getOwnerAdminId())) {

                        if (PurchaseOrder.STATUS_RECEIVED.equalsIgnoreCase(po.getStatus()) ||
                                PurchaseOrder.STATUS_PARTIAL.equalsIgnoreCase(po.getStatus())) {

                            masterPOList.add(po);

                            String supName = po.getSupplierName();
                            if (supName != null && !supName.isEmpty() && !supplierList.contains(supName)) {
                                supplierList.add(supName);
                            }
                        }
                    }
                }

                Collections.sort(masterPOList, (a, b) -> Long.compare(
                        b.getOrderDate() != null ? b.getOrderDate().getTime() : 0L,
                        a.getOrderDate() != null ? a.getOrderDate().getTime() : 0L
                ));

                setupSupplierSpinner();
                applyFilters();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void setupSupplierSpinner() {
        // Apply the adaptive adapter!
        ArrayAdapter<String> spinAdapter = getAdaptiveAdapter(supplierList);
        spinnerSupplier.setAdapter(spinAdapter);

        spinnerSupplier.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedSupplier = supplierList.get(position);
                applyFilters();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void applyFilters() {
        filteredPOList.clear();
        double totalSpent = 0.0;
        int totalOrders = 0;

        for (PurchaseOrder po : masterPOList) {
            long poTime = po.getOrderDate() != null ? po.getOrderDate().getTime() : 0L;

            boolean matchesDate = (filterStartDate == 0 || (poTime >= filterStartDate && poTime <= filterEndDate));
            boolean matchesSupplier = selectedSupplier.equals("All Suppliers") || selectedSupplier.equals(po.getSupplierName());

            if (matchesDate && matchesSupplier) {
                filteredPOList.add(po);
                totalSpent += po.getTotalAmount();
                totalOrders++;
            }
        }

        tvTotalOrders.setText(String.valueOf(totalOrders));
        tvTotalSpent.setText(String.format(Locale.US, "₱%,.2f", totalSpent));
        progressBar.setVisibility(View.GONE);

        if (filteredPOList.isEmpty()) {
            tvNoData.setVisibility(View.VISIBLE);
            recyclerViewReceiving.setVisibility(View.GONE);
        } else {
            tvNoData.setVisibility(View.GONE);
            recyclerViewReceiving.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private class ReceivingAdapter extends RecyclerView.Adapter<ReceivingAdapter.ViewHolder> {
        private SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_receiving_report_row, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PurchaseOrder po = filteredPOList.get(position);

            holder.tvPoNumber.setText(po.getPoNumber());
            holder.tvSupplier.setText(po.getSupplierName());

            if (po.getOrderDate() != null) {
                holder.tvDate.setText(sdf.format(po.getOrderDate()));
            } else {
                holder.tvDate.setText("Unknown Date");
            }

            holder.tvTotalAmount.setText(String.format(Locale.US, "₱%,.2f", po.getTotalAmount()));
            holder.tvStatus.setText(po.getStatus() != null ? po.getStatus().toUpperCase() : "UNKNOWN");

            if (PurchaseOrder.STATUS_PARTIAL.equalsIgnoreCase(po.getStatus())) {
                holder.tvStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            } else if (PurchaseOrder.STATUS_RECEIVED.equalsIgnoreCase(po.getStatus())) {
                holder.tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                holder.tvStatus.setTextColor(Color.GRAY);
            }
        }

        @Override
        public int getItemCount() {
            return filteredPOList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvPoNumber, tvSupplier, tvDate, tvTotalAmount, tvStatus;

            ViewHolder(View itemView) {
                super(itemView);
                tvPoNumber = itemView.findViewById(R.id.tvPoNumber);
                tvSupplier = itemView.findViewById(R.id.tvSupplier);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvTotalAmount = itemView.findViewById(R.id.tvTotalAmount);
                tvStatus = itemView.findViewById(R.id.tvStatus);
            }
        }
    }
}