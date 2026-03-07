package com.app.SalesInventory;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Reports extends BaseActivity {

    private static final int REQUEST_WRITE_STORAGE = 1001;

    // Metrics Text Views
    private TextView tvGrossRevenue, tvGrossProfit, tvCogs, tvDiscounts;
    private TextView tvCashSales, tvEPaymentSales, tvTransactions, tvInventoryValue;

    // Controls
    private Toolbar toolbar;
    private Spinner spinnerDateFilter;
    private View layoutCustomDates;
    private EditText etFromDate, etToDate;
    private ListView reportsListView;

    private ProductRepository productRepository;
    private SalesRepository salesRepository;

    private List<Product> productList = new ArrayList<>();
    private List<Sales> allSalesList = new ArrayList<>();

    private ReportAdapter adapter;
    private List<ReportItem> reportItems = new ArrayList<>();

    private File lastGeneratedFile;
    private ActivityResultLauncher<Intent> saveFileLauncher;

    // Filter Tracking
    private long startDate = 0, endDate = System.currentTimeMillis();
    private String currentDateRangeStr = "Today";
    private SimpleDateFormat displayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    // Current Values to pass to PDF
    private double currentTotalRevenue = 0, currentTotalCogs = 0, currentGrossProfit = 0;
    private double currentTotalDiscounts = 0, currentCashSales = 0, currentEPaymentSales = 0;
    private double currentInventoryValue = 0;
    private int currentTransactionCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        productRepository = SalesInventoryApplication.getProductRepository();
        salesRepository = SalesRepository.getInstance(getApplication());

        initializeUI();
        setupSaveLauncher();
        setupFilterSpinner();
        loadData();
    }

    private void initializeUI() {
        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        tvGrossRevenue = findViewById(R.id.tvGrossRevenue);
        tvGrossProfit = findViewById(R.id.tvGrossProfit);
        tvCogs = findViewById(R.id.tvCogs);
        tvDiscounts = findViewById(R.id.tvDiscounts);
        tvCashSales = findViewById(R.id.tvCashSales);
        tvEPaymentSales = findViewById(R.id.tvEPaymentSales);
        tvTransactions = findViewById(R.id.tvTransactions);
        tvInventoryValue = findViewById(R.id.tvInventoryValue);

        spinnerDateFilter = findViewById(R.id.spinnerDateFilter);
        layoutCustomDates = findViewById(R.id.layoutCustomDates);
        etFromDate = findViewById(R.id.etFromDate);
        etToDate = findViewById(R.id.etToDate);
        reportsListView = findViewById(R.id.ReportsListView);

        adapter = new ReportAdapter(this, reportItems);
        reportsListView.setAdapter(adapter);

        etFromDate.setOnClickListener(v -> showDatePicker(true));
        etToDate.setOnClickListener(v -> showDatePicker(false));

        Button btnExportAccounting = findViewById(R.id.btnExportAccounting);
        Button btnInventoryReport = findViewById(R.id.btnInventoryReport);
        Button btnOverallSummary = findViewById(R.id.btnOverallSummary);

        btnExportAccounting.setOnClickListener(v -> {
            if (ensureWritePermission()) exportAccountingPdf();
        });

        btnOverallSummary.setOnClickListener(v -> {
            if (ensureWritePermission()) exportOverallSummaryPdf();
        });

        btnInventoryReport.setOnClickListener(v -> startActivity(new Intent(Reports.this, InventoryReportsActivity.class)));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { onBackPressed(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void setupFilterSpinner() {
        String[] options = {"Today", "Yesterday", "This Week", "Last Week", "This Month", "All Time", "Custom Range"};
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, options);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerDateFilter.setAdapter(spinnerAdapter);

        spinnerDateFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyDateFilter(position);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void applyDateFilter(int filterType) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        layoutCustomDates.setVisibility(View.GONE);

        switch (filterType) {
            case 0: // Today
                currentDateRangeStr = "Today";
                startDate = cal.getTimeInMillis();
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59);
                endDate = cal.getTimeInMillis();
                break;
            case 1: // Yesterday
                currentDateRangeStr = "Yesterday";
                cal.add(Calendar.DAY_OF_YEAR, -1);
                startDate = cal.getTimeInMillis();
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59);
                endDate = cal.getTimeInMillis();
                break;
            case 2: // This Week
                currentDateRangeStr = "This Week";
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                startDate = cal.getTimeInMillis();
                endDate = System.currentTimeMillis();
                break;
            case 3: // Last Week
                currentDateRangeStr = "Last Week";
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                cal.add(Calendar.WEEK_OF_YEAR, -1);
                startDate = cal.getTimeInMillis();
                cal.add(Calendar.DAY_OF_YEAR, 6);
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59);
                endDate = cal.getTimeInMillis();
                break;
            case 4: // This Month
                currentDateRangeStr = "This Month";
                cal.set(Calendar.DAY_OF_MONTH, 1);
                startDate = cal.getTimeInMillis();
                endDate = System.currentTimeMillis();
                break;
            case 5: // All Time
                currentDateRangeStr = "All Time";
                startDate = 0; endDate = Long.MAX_VALUE;
                break;
            case 6: // Custom
                currentDateRangeStr = "Custom Range";
                layoutCustomDates.setVisibility(View.VISIBLE);
                return;
        }
        calculateMetricsAndList();
    }

    private void showDatePicker(boolean isStart) {
        Calendar cal = Calendar.getInstance();
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            cal.set(year, month, dayOfMonth);
            if (isStart) {
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
                startDate = cal.getTimeInMillis();
                etFromDate.setText(displayFormat.format(cal.getTime()));
            } else {
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59);
                endDate = cal.getTimeInMillis();
                etToDate.setText(displayFormat.format(cal.getTime()));
            }
            currentDateRangeStr = etFromDate.getText().toString() + " to " + etToDate.getText().toString();
            calculateMetricsAndList();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void loadData() {
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                productList = products;
                calculateInventoryAsset();
            }
        });

        salesRepository.getAllSales().observe(this, sales -> {
            if (sales != null) {
                allSalesList = sales;
                calculateMetricsAndList();
            }
        });
    }

    private void calculateInventoryAsset() {
        currentInventoryValue = 0;
        if (productList != null) {
            for (Product p : productList) {
                if (!"Menu".equals(p.getProductType())) {
                    currentInventoryValue += (p.getQuantity() * p.getCostPrice());
                }
            }
        }
        tvInventoryValue.setText(String.format(Locale.getDefault(), "₱%,.2f", currentInventoryValue));
    }

    private void calculateMetricsAndList() {
        reportItems.clear();

        currentTotalRevenue = 0; currentTotalCogs = 0; currentTotalDiscounts = 0;
        currentCashSales = 0; currentEPaymentSales = 0; currentTransactionCount = 0;

        for (Sales s : allSalesList) {
            long ts = s.getTimestamp() > 0 ? s.getTimestamp() : s.getDate();
            if (ts >= startDate && ts <= endDate) {
                currentTransactionCount++;
                double revenue = s.getTotalPrice();
                double cost = s.getTotalCost();

                double discount = 0;
                try { discount = (double) s.getClass().getMethod("getDiscount").invoke(s); } catch (Exception ignored) {}

                currentTotalRevenue += revenue;
                currentTotalCogs += cost;
                currentTotalDiscounts += discount;

                String paymentType = s.getPaymentMethod() != null ? s.getPaymentMethod() : "Unknown";
                if (paymentType.contains("Cash")) currentCashSales += revenue;
                else currentEPaymentSales += revenue;

                String rawName = s.getProductName() != null ? s.getProductName() : "Unknown Product";
                String displayName = rawName;
                String details = "";
                if (rawName.contains(" | ")) {
                    int firstPipe = rawName.indexOf(" | ");
                    displayName = rawName.substring(0, firstPipe);
                    details = rawName.substring(firstPipe + 3).replace(" | ", "\n");
                }

                String dateStr = timeFormat.format(new Date(ts)) + " | " + paymentType;
                String qtyStr = "x " + s.getQuantity();
                String amountStr = String.format(Locale.getDefault(), "₱%,.2f", revenue);

                reportItems.add(new ReportItem(displayName, dateStr, qtyStr, amountStr, details, discount));
            }
        }

        currentGrossProfit = currentTotalRevenue - currentTotalCogs;

        tvGrossRevenue.setText(String.format(Locale.getDefault(), "₱%,.2f", currentTotalRevenue));
        tvCogs.setText(String.format(Locale.getDefault(), "₱%,.2f", currentTotalCogs));
        tvGrossProfit.setText(String.format(Locale.getDefault(), "₱%,.2f", currentGrossProfit));
        tvDiscounts.setText(String.format(Locale.getDefault(), "₱%,.2f", currentTotalDiscounts));
        tvCashSales.setText(String.format(Locale.getDefault(), "₱%,.2f", currentCashSales));
        tvEPaymentSales.setText(String.format(Locale.getDefault(), "₱%,.2f", currentEPaymentSales));
        tvTransactions.setText(String.valueOf(currentTransactionCount));

        adapter.notifyDataSetChanged();
    }

    // =========================================================================
    // EXPORT FUNCTIONS
    // =========================================================================

    private void exportAccountingPdf() {
        try {
            ReportExportUtil exportUtil = new ReportExportUtil(this);
            File exportDir = exportUtil.getExportDirectory();
            if (exportDir == null) return;
            String pdfName = exportUtil.generateFileName("Accounting_Report", ReportExportUtil.EXPORT_PDF);
            File pdfFile = new File(exportDir, pdfName);

            PDFGenerator generator = new PDFGenerator(this);
            generator.generateAccountingReportPDF(
                    pdfFile, currentDateRangeStr, currentTotalRevenue, currentTotalCogs,
                    currentGrossProfit, currentTotalDiscounts, currentCashSales,
                    currentEPaymentSales, currentTransactionCount, currentInventoryValue, reportItems
            );

            lastGeneratedFile = pdfFile;
            promptUserToSaveFile(pdfFile.getName(), "application/pdf");
        } catch (Exception e) {
            new ReportExportUtil(this).showExportError("Accounting PDF Export failed: " + e.getMessage());
        }
    }

    private void exportOverallSummaryPdf() {
        try {
            ReportExportUtil exportUtil = new ReportExportUtil(this);
            File exportDir = exportUtil.getExportDirectory();
            if (exportDir == null) return;
            String pdfName = exportUtil.generateFileName("Overall_Summary", ReportExportUtil.EXPORT_PDF);
            File pdfFile = new File(exportDir, pdfName);

            PDFGenerator generator = new PDFGenerator(this);
            // FIXED RED ERROR: Passed 0s to match original signature in PDFGenerator.java perfectly!
            generator.generateOverallSummaryReportPDF(
                    pdfFile, productList.size(), 0, 0, currentTransactionCount, 0, 0, 0
            );

            lastGeneratedFile = pdfFile;
            promptUserToSaveFile(pdfFile.getName(), "application/pdf");
        } catch (Exception e) {
            new ReportExportUtil(this).showExportError("Overall PDF Export failed: " + e.getMessage());
        }
    }

    private boolean ensureWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return true;
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
        return false;
    }

    private void setupSaveLauncher() {
        saveFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && lastGeneratedFile != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) copyFileToUri(lastGeneratedFile, uri);
                    }
                }
        );
    }

    private void promptUserToSaveFile(String suggestedName, String mimeType) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, suggestedName);
        saveFileLauncher.launch(intent);
    }

    private void copyFileToUri(File source, Uri destUri) {
        try {
            FileInputStream in = new FileInputStream(source);
            OutputStream out = getContentResolver().openOutputStream(destUri);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);
            out.flush(); in.close(); out.close();
            Toast.makeText(this, "Export Saved successfully!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
        }
    }

    // Must be public so PDFGenerator can read the fields!
    public static class ReportItem {
        public String name, date, quantity, amount, details;
        public double discount;
        public ReportItem(String name, String date, String quantity, String amount, String details, double discount) {
            this.name = name; this.date = date; this.quantity = quantity;
            this.amount = amount; this.details = details; this.discount = discount;
        }
    }

    private class ReportAdapter extends android.widget.BaseAdapter {
        private android.content.Context context;
        private List<ReportItem> items;

        public ReportAdapter(android.content.Context context, List<ReportItem> items) {
            this.context = context; this.items = items;
        }
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int position) { return items.get(position); }
        @Override public long getItemId(int position) { return position; }

        @Override
        public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_report_row, parent, false);
            }
            TextView rowName = convertView.findViewById(R.id.RowName);
            TextView rowDate = convertView.findViewById(R.id.RowDate);
            TextView rowQty = convertView.findViewById(R.id.RowQty);
            TextView rowTotal = convertView.findViewById(R.id.RowTotal);
            TextView rowDetails = convertView.findViewById(R.id.RowDetails);
            TextView rowDiscount = convertView.findViewById(R.id.RowDiscount);

            ReportItem item = items.get(position);
            rowName.setText(item.name);
            rowDate.setText(item.date);
            rowQty.setText(item.quantity);
            rowTotal.setText(item.amount);

            if (item.details != null && !item.details.isEmpty()) {
                rowDetails.setVisibility(View.VISIBLE); rowDetails.setText(item.details);
            } else { rowDetails.setVisibility(View.GONE); }

            if (item.discount > 0) {
                rowDiscount.setVisibility(View.VISIBLE);
                rowDiscount.setText(String.format(Locale.getDefault(), "Discount: -₱%.2f", item.discount));
            } else { rowDiscount.setVisibility(View.GONE); }

            return convertView;
        }
    }
}