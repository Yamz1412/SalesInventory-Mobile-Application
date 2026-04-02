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
    private ValueEventListener realTimeListener;

    private List<ReceivedItemRecord> masterItemList = new ArrayList<>();
    private List<ReceivedItemRecord> filteredItemList = new ArrayList<>();
    private ReceivingAdapter adapter;

    private long filterStartDate = 0;
    private long filterEndDate = System.currentTimeMillis();
    private String selectedSupplier = "All Suppliers";
    private List<String> supplierList = new ArrayList<>();

    private static class ReceivedItemRecord {
        String productName;
        String poNumber;
        String supplierName;
        long date;
        double qtyReceived;
        double unitPrice;
        double subtotal;
        String status;
        String unit;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiving_report);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Receiving Report");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (currentOwnerId == null || currentOwnerId.isEmpty()) {
            currentOwnerId = AuthManager.getInstance().getCurrentUserId();
        }

        poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");

        initializeViews();
        setupFilters();
        loadReceivingData();
    }

    private void initializeViews() {
        tvTotalOrders = findViewById(R.id.tvTotalOrders); // Will now show "Total Items"
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

        if (realTimeListener != null) {
            poRef.removeEventListener(realTimeListener);
        }

        realTimeListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                masterItemList.clear();
                supplierList.clear();
                supplierList.add("All Suppliers");

                for (DataSnapshot ds : snapshot.getChildren()) {
                    PurchaseOrder po = ds.getValue(PurchaseOrder.class);
                    if (po != null && currentOwnerId.equals(po.getOwnerAdminId())) {

                        if (PurchaseOrder.STATUS_RECEIVED.equalsIgnoreCase(po.getStatus()) ||
                                PurchaseOrder.STATUS_PARTIAL.equalsIgnoreCase(po.getStatus())) {

                            String supName = po.getSupplierName();
                            if (supName != null && !supName.isEmpty() && !supplierList.contains(supName)) {
                                supplierList.add(supName);
                            }

                            // EXTRACT INDIVIDUAL ITEMS
                            if (po.getItems() != null) {
                                for (POItem item : po.getItems()) {
                                    if (item.getReceivedQuantity() > 0) {
                                        ReceivedItemRecord record = new ReceivedItemRecord();
                                        record.productName = item.getProductName();
                                        record.poNumber = po.getPoNumber();
                                        record.supplierName = po.getSupplierName();
                                        record.date = po.getOrderDate();
                                        record.qtyReceived = item.getReceivedQuantity();
                                        record.unitPrice = item.getUnitPrice();
                                        record.subtotal = item.getReceivedQuantity() * item.getUnitPrice();
                                        record.status = po.getStatus();
                                        record.unit = item.getUnit();
                                        masterItemList.add(record);
                                    }
                                }
                            }
                        }
                    }
                }

                Collections.sort(masterItemList, (a, b) -> Long.compare(b.date, a.date));
                setupSupplierSpinner();
                applyFilters();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
            }
        };
        poRef.addValueEventListener(realTimeListener);
    }

    private void setupSupplierSpinner() {
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
        filteredItemList.clear();
        double totalSpent = 0.0;
        int totalItemsReceived = 0;

        for (ReceivedItemRecord item : masterItemList) {
            boolean matchesDate = (filterStartDate == 0 || (item.date >= filterStartDate && item.date <= filterEndDate));
            boolean matchesSupplier = selectedSupplier.equals("All Suppliers") || selectedSupplier.equals(item.supplierName);

            if (matchesDate && matchesSupplier) {
                filteredItemList.add(item);
                totalSpent += item.subtotal;
                totalItemsReceived++;
            }
        }

        tvTotalOrders.setText(String.valueOf(totalItemsReceived)); // Hijacked to show Item Count
        tvTotalSpent.setText(String.format(Locale.US, "₱%,.2f", totalSpent));
        progressBar.setVisibility(View.GONE);

        if (filteredItemList.isEmpty()) {
            tvNoData.setVisibility(View.VISIBLE);
            recyclerViewReceiving.setVisibility(View.GONE);
        } else {
            tvNoData.setVisibility(View.GONE);
            recyclerViewReceiving.setVisibility(View.VISIBLE);
            adapter.notifyDataSetChanged();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (poRef != null && realTimeListener != null) {
            poRef.removeEventListener(realTimeListener);
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
            ReceivedItemRecord item = filteredItemList.get(position);

            holder.tvItemName.setText(item.productName);
            holder.tvOrderRef.setText(item.supplierName + " | " + item.poNumber);

            if (item.date > 0) {
                holder.tvDate.setText(sdf.format(new java.util.Date(item.date)));
            } else {
                holder.tvDate.setText("Unknown Date");
            }

            holder.tvSubtotal.setText(String.format(Locale.US, "₱%,.2f", item.subtotal));

            // Format qty dynamically
            String qtyStr = (item.qtyReceived % 1 == 0) ? String.valueOf((long)item.qtyReceived) : String.format(Locale.US, "%.2f", item.qtyReceived);
            String unit = item.unit != null ? item.unit : "pcs";

            holder.tvQtyReceived.setText(qtyStr + " " + unit + " Rcvd");
            holder.tvQtyReceived.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
        }

        @Override
        public int getItemCount() {
            return filteredItemList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvItemName, tvOrderRef, tvDate, tvSubtotal, tvQtyReceived;

            ViewHolder(View itemView) {
                super(itemView);
                tvItemName = itemView.findViewById(R.id.tvItemName);
                tvOrderRef = itemView.findViewById(R.id.tvOrderRef);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvSubtotal = itemView.findViewById(R.id.tvSubtotal);
                tvQtyReceived = itemView.findViewById(R.id.tvQtyReceived);
            }
        }
    }
}