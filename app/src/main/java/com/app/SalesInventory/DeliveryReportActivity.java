package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.HashSet;
import java.util.TimeZone;

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
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class DeliveryReportActivity extends BaseActivity {

    private TextView tvTotalDeliveries, tvTotalPending, tvTotalDelivered, tvNoData;
    private Button btnDateFilter;
    private Spinner spinnerStatus;
    private ProgressBar progressBar;
    private RecyclerView recyclerView;

    private DeliveryReportAdapter adapter;
    private SalesRepository salesRepository;

    private List<DeliveryItemRecord> masterList = new ArrayList<>();
    private List<DeliveryItemRecord> filteredList = new ArrayList<>();

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
        tvTotalDeliveries = findViewById(R.id.tvTotalDeliveriesCount); // Now acts as Total Items
        tvTotalPending = findViewById(R.id.tvPendingDeliveriesCount);
        tvTotalDelivered = findViewById(R.id.tvTotalDelivered);
        tvNoData = findViewById(R.id.tvNoDataDelivery);
        btnDateFilter = findViewById(R.id.btnDateFilter);
        spinnerStatus = findViewById(R.id.spinnerStatus);
        progressBar = findViewById(R.id.progressBar);
        recyclerView = findViewById(R.id.recyclerViewDelivery);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeliveryReportAdapter(filteredList, this::markItemDelivered);
        recyclerView.setAdapter(adapter);
    }

    private ArrayAdapter<String> getAdaptiveAdapter(String[] items) {
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

            // 1. Extract exact dates that have delivery data
            HashSet<Long> validDates = new HashSet<>();
            Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

            for (DeliveryItemRecord item : masterList) {
                if (item.date > 0) {
                    utcCal.setTimeInMillis(item.date);
                    utcCal.set(Calendar.HOUR_OF_DAY, 0);
                    utcCal.set(Calendar.MINUTE, 0);
                    utcCal.set(Calendar.SECOND, 0);
                    utcCal.set(Calendar.MILLISECOND, 0);
                    validDates.add(utcCal.getTimeInMillis());
                }
            }

            // 2. Attach the custom Validator
            CalendarConstraints.Builder constraintsBuilder = new CalendarConstraints.Builder();
            constraintsBuilder.setValidator(new AvailableDateValidator(validDates));

            // 3. Build the Material Date Picker
            MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                    .setTitleText("Select Delivery Date")
                    .setCalendarConstraints(constraintsBuilder.build())
                    .setTheme(R.style.CustomCalendarTheme)
                    .build();

            // 4. Handle the user's selection
            datePicker.addOnPositiveButtonClickListener(selection -> {
                Calendar selectedCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                selectedCal.setTimeInMillis(selection);

                Calendar localCal = Calendar.getInstance();
                localCal.set(selectedCal.get(Calendar.YEAR), selectedCal.get(Calendar.MONTH), selectedCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
                filterStartDate = localCal.getTimeInMillis();

                localCal.set(Calendar.HOUR_OF_DAY, 23);
                localCal.set(Calendar.MINUTE, 59);
                localCal.set(Calendar.SECOND, 59);
                filterEndDate = localCal.getTimeInMillis();

                btnDateFilter.setText(filterFormat.format(localCal.getTime()));
                applyFilters();
            });

            datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
        });

        btnDateFilter.setOnLongClickListener(v -> {
            filterStartDate = 0;
            filterEndDate = System.currentTimeMillis();
            btnDateFilter.setText("All Time");
            applyFilters();
            return true;
        });

        // Setup the Status Spinner
        String[] statuses = {"All Statuses", "Pending", "Delivered", "Cancelled"};
        ArrayAdapter<String> statusAdapter = getAdaptiveAdapter(statuses);
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

            masterList.clear();
            for (Sales s : sales) {
                if (s.getDeliveryType() == null || !"DELIVERY".equalsIgnoreCase(s.getDeliveryType())) {
                    continue;
                }

                // Create a record for every individual item
                DeliveryItemRecord r = new DeliveryItemRecord();
                r.saleId = s.getId();
                r.productName = s.getProductName();
                r.orderId = s.getOrderId() != null ? s.getOrderId() : "N/A";
                r.customerName = s.getDeliveryName() != null && !s.getDeliveryName().isEmpty() ? s.getDeliveryName() : "Unknown Customer";
                r.paymentType = s.getDeliveryPaymentMethod() != null && !s.getDeliveryPaymentMethod().isEmpty() ? s.getDeliveryPaymentMethod() : s.getPaymentMethod();
                r.qty = s.getQuantity();
                r.subtotal = s.getTotalPrice();
                r.deliveryStatus = s.getDeliveryStatus() != null && !s.getDeliveryStatus().isEmpty() ? s.getDeliveryStatus() : "PENDING";

                // Show delivery date if completed, otherwise show order creation date
                if ("DELIVERED".equalsIgnoreCase(r.deliveryStatus) && s.getDeliveryDate() > 0) {
                    r.date = s.getDeliveryDate();
                } else {
                    r.date = s.getDate();
                }

                r.originalSaleObj = s;
                masterList.add(r);
            }

            // Sort newest first
            masterList.sort((a, b) -> Long.compare(b.date, a.date));
            applyFilters();
        });
    }

    private void applyFilters() {
        filteredList.clear();
        int pendingCount = 0;
        int deliveredCount = 0;

        for (DeliveryItemRecord o : masterList) {
            boolean matchesDate = (filterStartDate == 0 || (o.date >= filterStartDate && o.date <= filterEndDate));
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

    private void markItemDelivered(String saleId) {
        if (saleId == null || saleId.isEmpty()) return;

        long now = System.currentTimeMillis();

        for (DeliveryItemRecord record : masterList) {
            if (saleId.equals(record.saleId)) {
                Sales s = record.originalSaleObj;
                s.setDeliveryStatus("DELIVERED");
                s.setDeliveryDate(now);
                salesRepository.updateSaleDeliveryStatus(s); // LiveData auto-refreshes the list!
                Toast.makeText(this, "Item marked as delivered", Toast.LENGTH_SHORT).show();
                break;
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private static class DeliveryItemRecord {
        String saleId;
        String productName;
        String orderId;
        String customerName;
        String paymentType;
        int qty;
        double subtotal;
        String deliveryStatus;
        long date;
        Sales originalSaleObj;
    }

    private interface OnMarkDeliveredListener {
        void onMarkDelivered(String saleId);
    }

    private class DeliveryReportAdapter extends RecyclerView.Adapter<DeliveryReportAdapter.ViewHolder> {

        private List<DeliveryItemRecord> items;
        private OnMarkDeliveredListener listener;

        DeliveryReportAdapter(List<DeliveryItemRecord> items, OnMarkDeliveredListener listener) {
            this.items = items;
            this.listener = listener;
        }

        void setItems(List<DeliveryItemRecord> newItems) {
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
            DeliveryItemRecord r = items.get(position);

            holder.tvItemName.setText(r.productName);

            String shortOrderId = r.orderId.length() > 6 ? r.orderId.substring(0, 6).toUpperCase() : r.orderId.toUpperCase();
            holder.tvCustomerOrderRef.setText(r.customerName + " | Order #" + shortOrderId);

            String dateStr = r.date > 0 ? dateFormat.format(new Date(r.date)) : "Unknown Date";
            holder.tvDate.setText(dateStr);

            holder.tvSubtotal.setText(String.format(Locale.US, "₱%,.2f", r.subtotal));

            String paymentStr = r.paymentType != null && !r.paymentType.isEmpty() ? r.paymentType.toUpperCase() : "CASH";
            holder.tvQtyAndPayment.setText(r.qty + "x | " + paymentStr);

            String status = r.deliveryStatus != null ? r.deliveryStatus.toUpperCase() : "PENDING";
            holder.tvDeliveryStatus.setText(status);

            if ("DELIVERED".equals(status)) {
                holder.tvDeliveryStatus.setTextColor(getResources().getColor(R.color.successGreen));
                holder.tvDeliveryStatus.setBackgroundResource(0);
                holder.btnMarkDelivered.setVisibility(View.GONE);
            } else if ("CANCELLED".equals(status)) {
                holder.tvDeliveryStatus.setTextColor(getResources().getColor(R.color.errorRed));
                holder.tvDeliveryStatus.setBackgroundResource(0);
                holder.btnMarkDelivered.setVisibility(View.GONE);
            } else {
                holder.tvDeliveryStatus.setTextColor(getResources().getColor(R.color.warningYellow));
                holder.btnMarkDelivered.setVisibility(View.VISIBLE);
                holder.btnMarkDelivered.setOnClickListener(v -> {
                    if (listener != null) listener.onMarkDelivered(r.saleId);
                });
            }
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvItemName, tvDate, tvSubtotal, tvDeliveryStatus, tvCustomerOrderRef, tvQtyAndPayment;
            Button btnMarkDelivered;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvItemName = itemView.findViewById(R.id.tvItemName);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvSubtotal = itemView.findViewById(R.id.tvSubtotal);
                tvDeliveryStatus = itemView.findViewById(R.id.tvDeliveryStatus);
                tvCustomerOrderRef = itemView.findViewById(R.id.tvCustomerOrderRef);
                tvQtyAndPayment = itemView.findViewById(R.id.tvQtyAndPayment);
                btnMarkDelivered = itemView.findViewById(R.id.btnMarkDelivered);
            }
        }
    }

    public static class AvailableDateValidator implements CalendarConstraints.DateValidator {
        private final HashSet<Long> availableDates;

        public AvailableDateValidator(HashSet<Long> availableDates) {
            this.availableDates = availableDates;
        }

        protected AvailableDateValidator(Parcel in) {
            availableDates = (HashSet<Long>) in.readSerializable();
        }

        @Override
        public boolean isValid(long date) {
            if (availableDates == null || availableDates.isEmpty()) return false;
            return availableDates.contains(date);
        }

        @Override
        public int describeContents() { return 0; }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeSerializable(availableDates);
        }

        public static final Parcelable.Creator<AvailableDateValidator> CREATOR = new Parcelable.Creator<AvailableDateValidator>() {
            @Override
            public AvailableDateValidator createFromParcel(Parcel in) { return new AvailableDateValidator(in); }
            @Override
            public AvailableDateValidator[] newArray(int size) { return new AvailableDateValidator[size]; }
        };
    }
}
