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

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StockMovementReportActivity extends BaseActivity {

    private RecyclerView recyclerViewReport;
    private ProgressBar progressBar;
    private TextView tvNoData, tvTotalReceived, tvTotalSold, tvTotalAdjustments;
    private Button btnExportPDF, btnDateFilter;
    private Spinner spinnerCategory;

    private StockMovementAdapter adapter;
    private List<StockMovementReport> masterReportList = new ArrayList<>();
    private List<StockMovementReport> filteredReportList = new ArrayList<>();

    private ProductRepository productRepository;
    private SalesRepository salesRepository;
    private DatabaseReference adjustmentRef;

    private ReportExportUtil exportUtil;
    private PDFGenerator pdfGenerator;

    private double grandTotalReceived = 0.0;
    private double grandTotalSold = 0.0;
    private double grandTotalAdjusted = 0.0;

    private static final int PERMISSION_REQUEST_CODE = 200;
    private String currentOwnerId;

    private long filterStartDate = 0;
    private long filterEndDate = System.currentTimeMillis();
    private String selectedCategory = "All Categories";
    private List<String> categoryList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_movement_report);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Stock Movement Report");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();

        initializeViews();
        setupFilters();
        loadData();
    }

    private void initializeViews() {
        recyclerViewReport = findViewById(R.id.recyclerViewReport);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);
        tvTotalReceived = findViewById(R.id.tvTotalReceived);
        tvTotalSold = findViewById(R.id.tvTotalSold);
        tvTotalAdjustments = findViewById(R.id.tvTotalAdjustments);
        btnExportPDF = findViewById(R.id.btnExportPDF);
        btnDateFilter = findViewById(R.id.btnDateFilter);
        spinnerCategory = findViewById(R.id.spinnerCategory);

        exportUtil = new ReportExportUtil(this);

        try {
            pdfGenerator = new PDFGenerator(this);
        } catch (Exception e) {
            e.printStackTrace();
            btnExportPDF.setEnabled(false);
        }

        productRepository = SalesInventoryApplication.getProductRepository();
        salesRepository = SalesRepository.getInstance(getApplication());
        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");

        adapter = new StockMovementAdapter(filteredReportList);
        recyclerViewReport.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewReport.setAdapter(adapter);

        btnExportPDF.setOnClickListener(v -> startExportPDF());
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
            // We temporarily fetch the available dates directly from Firebase to build the calendar
            FirebaseDatabase.getInstance().getReference("Sales").addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    HashSet<Long> validDates = new HashSet<>();
                    Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

                    for (DataSnapshot ds : snapshot.getChildren()) {
                        Long ts = ds.child("timestamp").getValue(Long.class);
                        if (ts != null) {
                            utcCal.setTimeInMillis(ts);
                            utcCal.set(Calendar.HOUR_OF_DAY, 0); utcCal.set(Calendar.MINUTE, 0);
                            utcCal.set(Calendar.SECOND, 0); utcCal.set(Calendar.MILLISECOND, 0);
                            validDates.add(utcCal.getTimeInMillis());
                        }
                    }

                    CalendarConstraints.Builder constraintsBuilder = new CalendarConstraints.Builder();
                    if (!validDates.isEmpty()) constraintsBuilder.setValidator(new AvailableDateValidator(validDates));

                    MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                            .setTitleText("Select Movement Date")
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
                        btnDateFilter.setText(sdf.format(localCal.getTime()));

                        // CRITICAL FIX: We must completely reload the data so the math recalculates!
                        loadData();
                    });
                    datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        });

        btnDateFilter.setOnLongClickListener(v -> {
            filterStartDate = 0; filterEndDate = System.currentTimeMillis();
            btnDateFilter.setText("All Time");
            loadData(); // CRITICAL FIX
            return true;
        });
    }

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);
        Map<String, StockMovementReport> reportMap = new HashMap<>();
        categoryList.clear();
        categoryList.add("All Categories");

        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                for (Product p : products) {
                    if (p != null && p.isActive() && p.getProductId() != null) {
                        StockMovementReport report = new StockMovementReport(
                                p.getProductId(),
                                p.getProductName(),
                                p.getCategoryName(),
                                p.getQuantity(), 0, 0, 0, p.getQuantity(), System.currentTimeMillis()
                        );
                        reportMap.put(p.getProductId(), report);

                        String cat = p.getCategoryName();
                        if (cat != null && !cat.isEmpty() && !categoryList.contains(cat)) {
                            categoryList.add(cat);
                        }
                    }
                }
                setupCategorySpinner();
                loadSales(reportMap);
            }
        });
    }

    private void setupCategorySpinner() {
        // Use the adaptive adapter here!
        ArrayAdapter<String> spinAdapter = getAdaptiveAdapter(categoryList);
        spinnerCategory.setAdapter(spinAdapter);

        spinnerCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedCategory = categoryList.get(position);
                applyFilters();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void loadSales(Map<String, StockMovementReport> reportMap) {
        salesRepository.getAllSales().observe(this, sales -> {
            if (sales != null) {
                for (Sales s : sales) {
                    if (s == null || s.getProductId() == null) continue;

                    // CRITICAL FIX: Identify if the sale was refunded or voided
                    boolean isRefunded = s.getPaymentMethod() != null && s.getPaymentMethod().contains("REFUNDED");
                    boolean isVoided = "VOIDED".equals(s.getStatus());
                    if (isRefunded || isVoided) continue;

                    Long ts = s.getTimestamp();
                    long saleDate = (ts != null && ts > 0) ? ts : s.getDate();

                    if (reportMap.containsKey(s.getProductId())) {
                        if (filterStartDate == 0 || (saleDate >= filterStartDate && saleDate <= filterEndDate)) {
                            StockMovementReport report = reportMap.get(s.getProductId());
                            report.addSold((int) s.getQuantity());
                        }
                    }
                }
            }
            loadAdjustments(reportMap);
        });
    }

    private void loadAdjustments(Map<String, StockMovementReport> reportMap) {
        adjustmentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String owner = ds.child("ownerAdminId").getValue(String.class);
                    if (owner != null && !owner.equals(currentOwnerId)) continue;

                    String productId = ds.child("productId").getValue(String.class);
                    Double qtyObj = ds.child("quantityAdjusted").getValue(Double.class);
                    String adjType = ds.child("adjustmentType").getValue(String.class);
                    Long dateLogged = ds.child("dateLogged").getValue(Long.class);

                    if (productId == null || qtyObj == null || dateLogged == null) continue;

                    if (reportMap.containsKey(productId)) {
                        if (filterStartDate == 0 || (dateLogged >= filterStartDate && dateLogged <= filterEndDate)) {
                            StockMovementReport report = reportMap.get(productId);

                            if ("Add Stock".equals(adjType)) {
                                report.addReceived(qtyObj);
                            } else {
                                report.addAdjusted(Math.abs(qtyObj));
                            }
                        }
                    }
                }
                finalizeReport(reportMap);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) { finalizeReport(reportMap); }
        });
    }

    private void finalizeReport(Map<String, StockMovementReport> reportMap) {
        masterReportList.clear();
        for (StockMovementReport report : reportMap.values()) {
            report.calculateOpening();
            masterReportList.add(report);
        }

        masterReportList.sort((r1, r2) -> {
            String n1 = r1.getProductName() != null ? r1.getProductName() : "";
            String n2 = r2.getProductName() != null ? r2.getProductName() : "";
            return n1.compareToIgnoreCase(n2);
        });

        applyFilters();
    }

    private void applyFilters() {
        filteredReportList.clear();
        grandTotalReceived = 0.0;
        grandTotalSold = 0.0;
        grandTotalAdjusted = 0.0;

        for (StockMovementReport report : masterReportList) {
            boolean matchesCategory = selectedCategory.equals("All Categories") || selectedCategory.equals(report.getCategory());
            boolean hasMovement = (report.getReceived() > 0 || report.getSold() > 0 || report.getAdjusted() > 0);

            if (matchesCategory && (filterStartDate == 0 || hasMovement)) {
                filteredReportList.add(report);
                grandTotalReceived += report.getReceived();
                grandTotalSold += report.getSold();
                grandTotalAdjusted += report.getAdjusted();
            }
        }

        progressBar.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();

        tvTotalReceived.setText(formatQuantity(grandTotalReceived) + " units");
        tvTotalSold.setText(formatQuantity(grandTotalSold) + " units");
        tvTotalAdjustments.setText(formatQuantity(grandTotalAdjusted) + " units");

        tvNoData.setVisibility(filteredReportList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private String formatQuantity(double value) {
        if (value % 1 == 0) return String.valueOf((long) value);
        return String.format(Locale.US, "%.2f", value);
    }

    private void startExportPDF() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            return;
        }
        exportToPDF();
    }

    private void exportToPDF() {
        try {
            String fileName = exportUtil.generateFileName("StockMovement", ReportExportUtil.EXPORT_PDF);
            ReportExportUtil.ExportResult r = exportUtil.createOutputStreamForFile(fileName, ReportExportUtil.EXPORT_PDF);
            if (r == null || r.outputStream == null) return;

            pdfGenerator.generateStockMovementReportPDF(r.outputStream, filteredReportList, grandTotalReceived, grandTotalSold, grandTotalAdjusted);
            exportUtil.shareFileViaEmail(r.file, "Stock Movement Report - " + btnDateFilter.getText().toString());

        } catch (Exception e) {
            Toast.makeText(this, "Failed to generate PDF", Toast.LENGTH_SHORT).show();
        }
    }

    public static class AvailableDateValidator implements CalendarConstraints.DateValidator {
        private final HashSet<Long> availableDates;
        public AvailableDateValidator(HashSet<Long> availableDates) { this.availableDates = availableDates; }
        protected AvailableDateValidator(Parcel in) { availableDates = (HashSet<Long>) in.readSerializable(); }
        @Override public boolean isValid(long date) { return availableDates != null && availableDates.contains(date); }
        @Override public int describeContents() { return 0; }
        @Override public void writeToParcel(Parcel dest, int flags) { dest.writeSerializable(availableDates); }
        public static final Parcelable.Creator<AvailableDateValidator> CREATOR = new Parcelable.Creator<AvailableDateValidator>() {
            @Override public AvailableDateValidator createFromParcel(Parcel in) { return new AvailableDateValidator(in); }
            @Override public AvailableDateValidator[] newArray(int size) { return new AvailableDateValidator[size]; }
        };
    }

    @Override
    public boolean onSupportNavigateUp() { onBackPressed(); return true; }
}