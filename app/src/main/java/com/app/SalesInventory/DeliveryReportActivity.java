package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.DatePickerDialog;
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
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DeliveryReportActivity extends BaseActivity {

    private TextView tvTotalDeliveries, tvTotalPending, tvTotalDelivered, tvNoData;
    private Button btnDateFilter;
    private Spinner spinnerStatus;
    private ProgressBar progressBar;
    private RecyclerView recyclerView;

    private DeliveryReportAdapter adapter;
    private SalesRepository salesRepository;

    private List<DeliveryOrder> masterList = new ArrayList<>();
    private List<DeliveryOrder> filteredList = new ArrayList<>();

    // Filters
    private long filterStartDate = 0;
    private long filterEndDate = System.currentTimeMillis();
    private String selectedStatus = "All Statuses";

    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
    private SimpleDateFormat filterFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.US);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_report);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Delivery Report");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initializeViews();
        setupFilters();

        salesRepository = SalesRepository.getInstance();
        loadData();
    }

    private void initializeViews() {
        tvTotalDeliveries = findViewById(R.id.tvTotalDeliveries);
        tvTotalPending = findViewById(R.id.tvTotalPending);
        tvTotalDelivered = findViewById(R.id.tvTotalDelivered);
        tvNoData = findViewById(R.id.tvNoDataDelivery);
        btnDateFilter = findViewById(R.id.btnDateFilter);
        spinnerStatus = findViewById(R.id.spinnerStatus);
        progressBar = findViewById(R.id.progressBar);
        recyclerView = findViewById(R.id.recyclerViewDelivery);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeliveryReportAdapter(filteredList, this::markOrderDelivered);
        recyclerView.setAdapter(adapter);
    }

    private void setupFilters() {
        // Date Filter
        btnDateFilter.setOnClickListener(v -> {
            Calendar calendar = Calendar.getInstance();
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                calendar.set(year, month, dayOfMonth, 0, 0, 0);
                filterStartDate = calendar.getTimeInMillis();

                calendar.set(year, month, dayOfMonth, 23, 59, 59);
                filterEndDate = calendar.getTimeInMillis();

                btnDateFilter.setText(filterFormat.format(calendar.getTime()));
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

        // Status Spinner
        String[] statuses = {"All Statuses", "Pending", "Delivered", "Cancelled"};
        ArrayAdapter<String> statusAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, statuses);
        statusAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStatus.setAdapter(statusAdapter);

        spinnerStatus.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedStatus = statuses[position];
                applyFilters();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);
        salesRepository.getAllSales().observe(this, sales -> {
            if (sales == null || sales.isEmpty()) {
                masterList.clear();
                applyFilters();
                return;
            }

            Map<String, DeliveryOrder> map = new HashMap<>();
            for (Sales s : sales) {
                if (s.getDeliveryType() == null || !"DELIVERY".equalsIgnoreCase(s.getDeliveryType())) {
                    continue;
                }
                String orderId = s.getOrderId();
                if (orderId == null || orderId.isEmpty()) continue;

                DeliveryOrder o = map.get(orderId);
                if (o == null) {
                    o = new DeliveryOrder();
                    o.orderId = orderId;
                    o.deliveryStatus = s.getDeliveryStatus() != null && !s.getDeliveryStatus().isEmpty() ? s.getDeliveryStatus() : "PENDING";
                    o.deliveryDate = s.getDeliveryDate();
                    o.orderDate = s.getDate();
                    o.totalAmount = 0;
                    o.customerName = s.getDeliveryName() != null && !s.getDeliveryName().isEmpty() ? s.getDeliveryName() : "Unknown Customer";
                    o.paymentType = s.getDeliveryPaymentMethod() != null && !s.getDeliveryPaymentMethod().isEmpty() ? s.getDeliveryPaymentMethod() : s.getPaymentMethod();
                    map.put(orderId, o);
                }
                o.totalAmount += s.getTotalPrice();
            }

            masterList.clear();
            masterList.addAll(map.values());

            // Sort by newest first
            masterList.sort((a, b) -> Long.compare(b.orderDate, a.orderDate));

            applyFilters();
        });
    }

    private void applyFilters() {
        filteredList.clear();
        int pendingCount = 0;
        int deliveredCount = 0;

        for (DeliveryOrder o : masterList) {
            boolean matchesDate = (filterStartDate == 0 || (o.orderDate >= filterStartDate && o.orderDate <= filterEndDate));
            boolean matchesStatus = "All Statuses".equalsIgnoreCase(selectedStatus) || selectedStatus.equalsIgnoreCase(o.deliveryStatus);

            if (matchesDate && matchesStatus) {
                filteredList.add(o);

                if ("DELIVERED".equalsIgnoreCase(o.deliveryStatus)) deliveredCount++;
                else pendingCount++;
            }
        }

        tvTotalDeliveries.setText(String.valueOf(filteredList.size()));
        tvTotalPending.setText(String.valueOf(pendingCount));
        tvTotalDelivered.setText(String.valueOf(deliveredCount));

        progressBar.setVisibility(View.GONE);
        adapter.setItems(filteredList);

        if (filteredList.isEmpty()) {
            tvNoData.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            tvNoData.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }

    private void markOrderDelivered(String orderId) {
        if (orderId == null || orderId.isEmpty()) return;
        salesRepository.getAllSales().observe(this, sales -> {
            if (sales == null) return;
            long now = System.currentTimeMillis();
            boolean updated = false;

            for (Sales s : sales) {
                if (orderId.equals(s.getOrderId()) && "DELIVERY".equalsIgnoreCase(s.getDeliveryType())) {
                    s.setDeliveryStatus("DELIVERED");
                    s.setDeliveryDate(now);
                    salesRepository.updateSaleDeliveryStatus(s);
                    updated = true;
                }
            }

            if (updated) {
                Toast.makeText(this, "Order marked as delivered", Toast.LENGTH_SHORT).show();
                // Notice we do NOT manually reload data here, because the `updateSaleDeliveryStatus`
                // triggers Firestore, which will automatically trigger the `getAllSales().observe` again!
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private static class DeliveryOrder {
        String orderId;
        double totalAmount;
        String deliveryStatus;
        long orderDate;
        long deliveryDate;
        String customerName;
        String paymentType;
    }

    private interface OnMarkDeliveredListener {
        void onMarkDelivered(String orderId);
    }

    private class DeliveryReportAdapter extends RecyclerView.Adapter<DeliveryReportAdapter.ViewHolder> {

        private List<DeliveryOrder> items;
        private OnMarkDeliveredListener listener;

        DeliveryReportAdapter(List<DeliveryOrder> items, OnMarkDeliveredListener listener) {
            this.items = items;
            this.listener = listener;
        }

        void setItems(List<DeliveryOrder> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_delivery_order, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DeliveryOrder o = items.get(position);
            holder.tvOrderId.setText("Order #" + (o.orderId.length() > 6 ? o.orderId.substring(0, 6).toUpperCase() : o.orderId.toUpperCase()));

            String dateStr = o.orderDate > 0 ? dateFormat.format(new Date(o.orderDate)) : "Unknown Date";
            holder.tvOrderDate.setText(dateStr);
            holder.tvAmount.setText(String.format(Locale.US, "₱%,.2f", o.totalAmount));

            holder.tvCustomer.setText(o.customerName);
            holder.tvPayment.setText(o.paymentType != null && !o.paymentType.isEmpty() ? o.paymentType.toUpperCase() : "CASH");

            String status = o.deliveryStatus != null ? o.deliveryStatus.toUpperCase() : "PENDING";
            holder.tvStatus.setText(status);

            // Dynamic badge styling
            if ("DELIVERED".equals(status)) {
                holder.tvStatus.setTextColor(getResources().getColor(R.color.successGreen));
                holder.tvStatus.setBackgroundResource(0); // Optional: add a light green badge drawable if you have one
                holder.btnMarkDelivered.setVisibility(View.GONE);
            } else if ("CANCELLED".equals(status)) {
                holder.tvStatus.setTextColor(getResources().getColor(R.color.errorRed));
                holder.tvStatus.setBackgroundResource(0);
                holder.btnMarkDelivered.setVisibility(View.GONE);
            } else {
                holder.tvStatus.setTextColor(getResources().getColor(R.color.warningYellow));
                holder.btnMarkDelivered.setVisibility(View.VISIBLE);
                holder.btnMarkDelivered.setOnClickListener(v -> {
                    if (listener != null) listener.onMarkDelivered(o.orderId);
                });
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvOrderId, tvOrderDate, tvAmount, tvStatus, tvCustomer, tvPayment;
            Button btnMarkDelivered;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvOrderId = itemView.findViewById(R.id.tvOrderId);
                tvOrderDate = itemView.findViewById(R.id.tvOrderDate);
                tvAmount = itemView.findViewById(R.id.tvAmount);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                tvCustomer = itemView.findViewById(R.id.tvCustomer);
                tvPayment = itemView.findViewById(R.id.tvPayment);
                btnMarkDelivered = itemView.findViewById(R.id.btnMarkDelivered);
            }
        }
    }
}