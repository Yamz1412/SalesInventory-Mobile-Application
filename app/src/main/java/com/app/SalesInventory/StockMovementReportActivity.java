package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StockMovementReportActivity extends AppCompatActivity {

    private RecyclerView recyclerViewReport;
    private ProgressBar progressBar;
    private TextView tvNoData, tvTotalReceived, tvTotalSold, tvTotalAdjustments;
    private Button btnExportPDF, btnExportCSV;

    private StockMovementAdapter adapter;
    private List<StockMovementReport> reportList;

    private DatabaseReference productRef, salesRef, adjustmentRef;
    private ReportExportUtil exportUtil;
    private PDFGenerator pdfGenerator;
    private CSVGenerator csvGenerator;

    // Totals
    private int grandTotalReceived = 0;
    private int grandTotalSold = 0;
    private int grandTotalAdjusted = 0;

    private static final int PERMISSION_REQUEST_CODE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_movement_report);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Stock Movement Report");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initializeViews();
        loadData();
    }

    private void initializeViews() {
        // Initialize Views
        recyclerViewReport = findViewById(R.id.recyclerViewReport);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);
        tvTotalReceived = findViewById(R.id.tvTotalReceived);
        tvTotalSold = findViewById(R.id.tvTotalSold);
        tvTotalAdjustments = findViewById(R.id.tvTotalAdjustments);
        btnExportPDF = findViewById(R.id.btnExportPDF);
        btnExportCSV = findViewById(R.id.btnExportCSV);

        // Initialize Utils
        exportUtil = new ReportExportUtil(this);
        csvGenerator = new CSVGenerator();

        // FIX: Wrap PDFGenerator in try-catch to fix the error
        try {
            pdfGenerator = new PDFGenerator(this);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing PDF Generator", Toast.LENGTH_SHORT).show();
            btnExportPDF.setEnabled(false); // Disable button if initialization fails
        }

        // Initialize Firebase Refs
        productRef = FirebaseDatabase.getInstance().getReference("Product");
        salesRef = FirebaseDatabase.getInstance().getReference("Sales");
        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");

        // Setup Recycler
        reportList = new ArrayList<>();
        adapter = new StockMovementAdapter(reportList);
        recyclerViewReport.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewReport.setAdapter(adapter);

        // Listeners
        btnExportPDF.setOnClickListener(v -> exportToPDF());
        btnExportCSV.setOnClickListener(v -> exportToCSV());
    }

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);
        Map<String, StockMovementReport> reportMap = new HashMap<>();

        // 1. Load Products first
        productRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Product p = ds.getValue(Product.class);
                    if (p != null) {
                        // Use ProductName as key to match Sales/Adjustments
                        String key = p.getProductName();
                        StockMovementReport report = new StockMovementReport(
                                p.getProductId(),
                                p.getProductName(),
                                p.getCategoryName(), // Changed from getCategory to getCategoryName based on your Product model
                                p.getQuantity(),
                                0, 0, 0,
                                p.getQuantity(),
                                System.currentTimeMillis()
                        );

                        reportMap.put(key, report);
                    }
                }
                // After products, load Sales
                loadSales(reportMap);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    private void loadSales(Map<String, StockMovementReport> reportMap) {
        salesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Sales s = ds.getValue(Sales.class);
                    if (s != null && reportMap.containsKey(s.getProductId())) {
                        StockMovementReport report = reportMap.get(s.getProductId());

                        // Add sold quantity
                        int qty = s.getQuantity();
                        report.addSold(qty);
                        grandTotalSold += qty;
                    }
                }
                loadAdjustments(reportMap);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadAdjustments(Map<String, StockMovementReport> reportMap) {
        adjustmentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockAdjustment adj = ds.getValue(StockAdjustment.class);
                    if (adj != null && reportMap.containsKey(adj.getProductName())) {
                        StockMovementReport report = reportMap.get(adj.getProductName());

                        if ("Add Stock".equals(adj.getAdjustmentType())) {
                            report.addReceived(adj.getQuantityAdjusted());
                            grandTotalReceived += adj.getQuantityAdjusted();
                        } else {
                            report.addAdjusted(adj.getQuantityAdjusted());
                            grandTotalAdjusted += adj.getQuantityAdjusted();
                        }
                    }
                }
                finalizeReport(reportMap);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void finalizeReport(Map<String, StockMovementReport> reportMap) {
        reportList.clear();
        for (StockMovementReport report : reportMap.values()) {
            report.calculateOpening();
            reportList.add(report);
        }

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

    private void exportToPDF() {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            return;
        }

        try {
            File exportDir = exportUtil.getExportDirectory();
            String fileName = exportUtil.generateFileName("StockMovement", ReportExportUtil.EXPORT_PDF);
            File file = new File(exportDir, fileName);

            if (pdfGenerator != null) {
                pdfGenerator.generateStockMovementReportPDF(file, reportList, grandTotalReceived, grandTotalSold, grandTotalAdjusted);
                exportUtil.showExportSuccess(file.getAbsolutePath());
            }
        } catch (Exception e) {
            exportUtil.showExportError(e.getMessage());
        }
    }

    private void exportToCSV() {
        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
            return;
        }

        try {
            File exportDir = exportUtil.getExportDirectory();
            String fileName = exportUtil.generateFileName("StockMovement", ReportExportUtil.EXPORT_CSV);
            File file = new File(exportDir, fileName);

            if (csvGenerator != null) {
                csvGenerator.generateStockMovementReportCSV(file, reportList, grandTotalReceived, grandTotalSold, grandTotalAdjusted);
                exportUtil.showExportSuccess(file.getAbsolutePath());
            }
        } catch (Exception e) {
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