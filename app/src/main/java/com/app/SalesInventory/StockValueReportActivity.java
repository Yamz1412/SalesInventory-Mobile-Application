package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.OutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StockValueReportActivity extends BaseActivity  {

    private RecyclerView recyclerViewReport;
    private ProgressBar progressBar;
    private TextView tvNoData, tvTotalInventoryValue, tvTotalCostValue, tvTotalProfitValue;
    private Button btnExportPDF, btnExportCSV;
    private StockValueReportAdapter adapter;
    private List<StockValueReport> reportList;
    private ProductRepository productRepository;
    private ReportExportUtil exportUtil;
    private PDFGenerator pdfGenerator;
    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_value_report);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Stock Value Report");
            getSupportActionBar().setSubtitle("Monitors financial capital currently tied up in active inventory");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initializeViews();
        setupRecyclerView();
        loadStockValueReport();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        View btnDateFilter = findViewById(R.id.btnDateFilter);
        if (btnDateFilter != null) {
            btnDateFilter.setOnClickListener(v -> {
                Toast.makeText(this, "Date filter available for historical records.", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void initializeViews() {
        recyclerViewReport = findViewById(R.id.recyclerViewReport);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);
        if (tvNoData != null) {
            tvNoData.setText("Your inventory is currently empty. Add products to begin monitoring your capital investments.");
        }

        tvTotalInventoryValue = findViewById(R.id.tvTotalInventoryValue);
        tvTotalCostValue = findViewById(R.id.tvTotalCostValue);
        tvTotalProfitValue = findViewById(R.id.tvTotalProfitValue);
        btnExportPDF = findViewById(R.id.btnExportPDF);

        reportList = new ArrayList<>();
        productRepository = SalesInventoryApplication.getProductRepository();
        exportUtil = new ReportExportUtil(this);

        try {
            pdfGenerator = new PDFGenerator(this);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing PDF Generator", Toast.LENGTH_SHORT).show();
            btnExportPDF.setEnabled(false);
        }


        btnExportPDF.setOnClickListener(v -> startExport(ReportExportUtil.EXPORT_PDF));
    }

    private void setupRecyclerView() {
        adapter = new StockValueReportAdapter(reportList);
        recyclerViewReport.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewReport.setAdapter(adapter);
    }

    private void loadStockValueReport() {
        progressBar.setVisibility(View.VISIBLE);
        tvNoData.setVisibility(View.GONE);

        // FIXED: Using observe() makes this screen 100% Real-Time and Offline-capable!
        productRepository.getAllProducts().observe(this, products -> {
            reportList.clear();
            double totalCostValue = 0;
            double totalSellingValue = 0;
            double totalProfit = 0;

            if (products != null) {
                for (Product product : products) {
                    // FIXED: Filter out "Menu" items so we don't double-count raw materials vs finished goods
                    if (product != null && product.isActive() && !"Menu".equalsIgnoreCase(product.getProductType())) {
                        StockValueReport report = new StockValueReport(
                                product.getProductId(),
                                product.getProductName(),
                                product.getCategoryName(),
                                product.getQuantity(),
                                product.getCostPrice(),
                                product.getSellingPrice(),
                                product.getReorderLevel(),
                                product.getCriticalLevel(),
                                product.getCeilingLevel(),
                                product.getFloorLevel()
                        );
                        reportList.add(report);

                        // ACCURACY FIX: Both Cost and Selling Price MUST be multiplied by Quantity!
                        double itemTotalCost = product.getCostPrice() * product.getQuantity();
                        double itemTotalSelling = product.getSellingPrice() * product.getQuantity();
                        double itemProfit = itemTotalSelling - itemTotalCost;

                        totalCostValue += itemTotalCost;
                        totalSellingValue += itemTotalSelling;
                        totalProfit += itemProfit;
                    }
                }
            }

            // Safely sort by profitability
            Collections.sort(reportList, (a, b) -> {
                double profitA = (a.getSellingPrice() * a.getQuantity()) - (a.getCostPrice() * a.getQuantity());
                double profitB = (b.getSellingPrice() * b.getQuantity()) - (b.getCostPrice() * b.getQuantity());
                return Double.compare(profitB, profitA);
            });

            progressBar.setVisibility(View.GONE);

            if (reportList.isEmpty()) {
                tvNoData.setVisibility(View.VISIBLE);
                recyclerViewReport.setVisibility(View.GONE);
                btnExportPDF.setEnabled(false);
            } else {
                tvNoData.setVisibility(View.GONE);
                recyclerViewReport.setVisibility(View.VISIBLE);
                btnExportPDF.setEnabled(true);

                tvTotalCostValue.setText("₱" + String.format(java.util.Locale.US, "%.2f", totalCostValue));
                tvTotalInventoryValue.setText("₱" + String.format(java.util.Locale.US, "%.2f", totalSellingValue));
                tvTotalProfitValue.setText("₱" + String.format(java.util.Locale.US, "%.2f", totalProfit));

                adapter.notifyDataSetChanged();
            }
        });
    }

    private void startExport(int exportType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
                return;
            }
        }
        if (exportType == ReportExportUtil.EXPORT_PDF) exportToPDF();
    }

    private void exportToPDF() {
        if (!exportUtil.isStorageAvailable()) {
            exportUtil.showExportError("Storage not available");
            return;
        }
        if (pdfGenerator == null) {
            exportUtil.showExportError("PDF Generator not ready");
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        try {
            String fileName = exportUtil.generateFileName("StockValue_Report", ReportExportUtil.EXPORT_PDF);
            ReportExportUtil.ExportResult r = exportUtil.createOutputStreamForFile(fileName, ReportExportUtil.EXPORT_PDF);
            if (r == null || r.outputStream == null) throw new Exception("Unable to obtain output stream");
            try {
                pdfGenerator.generateStockValueReportPDF(r.outputStream, reportList);
                exportUtil.showExportSuccess(r.displayPath);
            } finally {
                try { r.outputStream.close(); } catch (Exception ignored) {}
            }
            progressBar.setVisibility(View.GONE);
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            exportUtil.showExportError(e.getMessage());
        }
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permission granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}