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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StockMovementReportActivity extends BaseActivity {

    private RecyclerView recyclerViewReport;
    private ProgressBar progressBar;
    private TextView tvNoData, tvTotalReceived, tvTotalSold, tvTotalAdjustments;
    private Button btnExportPDF, btnExportCSV;

    private StockMovementAdapter adapter;
    private List<StockMovementReport> reportList;

    // ALIGNED: Using Repositories instead of direct Realtime DB references for Products and Sales
    private ProductRepository productRepository;
    private SalesRepository salesRepository;
    private DatabaseReference adjustmentRef;

    private ReportExportUtil exportUtil;
    private PDFGenerator pdfGenerator;
    private CSVGenerator csvGenerator;

    private int grandTotalReceived = 0;
    private int grandTotalSold = 0;
    private int grandTotalAdjusted = 0;

    private static final int PERMISSION_REQUEST_CODE = 200;
    private int pendingExportType = 0;

    private String currentOwnerId;

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
        btnExportCSV = findViewById(R.id.btnExportCSV);

        exportUtil = new ReportExportUtil(this);
        csvGenerator = new CSVGenerator();

        try {
            pdfGenerator = new PDFGenerator(this);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing PDF Generator", Toast.LENGTH_SHORT).show();
            btnExportPDF.setEnabled(false);
        }

        // ALIGNED: Initialize Repositories
        productRepository = SalesInventoryApplication.getProductRepository();
        salesRepository = SalesRepository.getInstance(getApplication());
        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");

        reportList = new ArrayList<>();
        adapter = new StockMovementAdapter(reportList);
        recyclerViewReport.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewReport.setAdapter(adapter);

        btnExportPDF.setOnClickListener(v -> startExport(ReportExportUtil.EXPORT_PDF));
        btnExportCSV.setOnClickListener(v -> startExport(ReportExportUtil.EXPORT_CSV));
    }

    private void startExport(int exportType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                pendingExportType = exportType;
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
                return;
            }
        }
        if (exportType == ReportExportUtil.EXPORT_PDF) exportToPDF();
        else exportToCSV();
    }

    private void exportToPDF() {
        try {
            String fileName = exportUtil.generateFileName("StockMovement", ReportExportUtil.EXPORT_PDF);
            ReportExportUtil.ExportResult r = exportUtil.createOutputStreamForFile(fileName, ReportExportUtil.EXPORT_PDF);
            if (r == null || r.outputStream == null) throw new Exception("Unable to obtain output stream");
            try {
                pdfGenerator.generateStockMovementReportPDF(r.outputStream, reportList, grandTotalReceived, grandTotalSold, grandTotalAdjusted);
                exportUtil.showExportSuccess(r.displayPath);
            } finally {
                try { r.outputStream.close(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            exportUtil.showExportError(e.getMessage() == null ? "Error exporting PDF" : e.getMessage());
        }
    }

    private void exportToCSV() {
        try {
            String fileName = exportUtil.generateFileName("StockMovement", ReportExportUtil.EXPORT_CSV);
            ReportExportUtil.ExportResult r = exportUtil.createOutputStreamForFile(fileName, ReportExportUtil.EXPORT_CSV);
            if (r == null || r.outputStream == null) throw new Exception("Unable to obtain output stream");
            try {
                csvGenerator.generateStockMovementReportCSV(r.outputStream, reportList, grandTotalReceived, grandTotalSold, grandTotalAdjusted);
                exportUtil.showExportSuccess(r.displayPath);
            } finally {
                try { r.outputStream.close(); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            exportUtil.showExportError(e.getMessage() == null ? "Error exporting CSV" : e.getMessage());
        }
    }

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);
        grandTotalReceived = 0;
        grandTotalSold = 0;
        grandTotalAdjusted = 0;

        Map<String, StockMovementReport> reportMap = new HashMap<>();

        // ALIGNED: Using offline-first ProductRepository (Avoids DatabaseException crash completely)
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                for (Product p : products) {
                    if (p != null && p.isActive() && p.getProductId() != null) {
                        StockMovementReport report = new StockMovementReport(
                                p.getProductId(),
                                p.getProductName(),
                                p.getCategoryName(),
                                p.getQuantity(), // This is the Current/Closing Stock
                                0,
                                0,
                                0,
                                p.getQuantity(),
                                System.currentTimeMillis()
                        );
                        reportMap.put(p.getProductId(), report);
                    }
                }
                loadSales(reportMap);
            } else {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void loadSales(Map<String, StockMovementReport> reportMap) {
        // ALIGNED: Using offline-first SalesRepository
        salesRepository.getAllSales().observe(this, sales -> {
            if (sales != null) {
                for (Sales s : sales) {
                    if (s != null && s.getProductId() != null && reportMap.containsKey(s.getProductId())) {
                        StockMovementReport report = reportMap.get(s.getProductId());
                        int qty = s.getQuantity();
                        report.addSold(qty);
                        grandTotalSold += qty;
                    }
                }
                loadAdjustments(reportMap);
            } else {
                loadAdjustments(reportMap);
            }
        });
    }

    private void loadAdjustments(Map<String, StockMovementReport> reportMap) {
        // ALIGNED: Safe fetch for adjustments using owner ID
        adjustmentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockAdjustment adj = ds.getValue(StockAdjustment.class);
                    if (adj == null || adj.getProductId() == null) continue;

                    String owner = ds.child("ownerAdminId").getValue(String.class);
                    if (owner != null && !owner.equals(currentOwnerId)) continue; // Keep data secure

                    if (reportMap.containsKey(adj.getProductId())) {
                        StockMovementReport report = reportMap.get(adj.getProductId());

                        // Capture "Received" (Purchases/Additions) vs "Adjusted" (Damages/Removals)
                        if ("Add Stock".equals(adj.getAdjustmentType())) {
                            report.addReceived(adj.getQuantityAdjusted());
                            grandTotalReceived += adj.getQuantityAdjusted();
                        } else {
                            // Convert negative adjustment numbers to positive for the math logic
                            int absQty = Math.abs(adj.getQuantityAdjusted());
                            report.addAdjusted(absQty);
                            grandTotalAdjusted += absQty;
                        }
                    }
                }
                finalizeReport(reportMap);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                finalizeReport(reportMap); // Finish rendering even if adjustments fail
            }
        });
    }

    private void finalizeReport(Map<String, StockMovementReport> reportMap) {
        reportList.clear();
        for (StockMovementReport report : reportMap.values()) {
            report.calculateOpening(); // Reverse calculates opening stock: Closing + Sold + Adjusted - Received
            reportList.add(report);
        }

        // Sort alphabetically
        reportList.sort((r1, r2) -> {
            String n1 = r1.getProductName() != null ? r1.getProductName() : "";
            String n2 = r2.getProductName() != null ? r2.getProductName() : "";
            return n1.compareToIgnoreCase(n2);
        });

        progressBar.setVisibility(View.GONE);
        adapter.notifyDataSetChanged();

        tvTotalReceived.setText(grandTotalReceived + " units");
        tvTotalSold.setText(grandTotalSold + " units");
        tvTotalAdjustments.setText(grandTotalAdjusted + " units");

        if (reportList.isEmpty()) {
            tvNoData.setVisibility(View.VISIBLE);
        } else {
            tvNoData.setVisibility(View.GONE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            pendingExportType = 0;
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