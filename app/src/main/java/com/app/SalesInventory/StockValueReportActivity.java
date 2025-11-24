package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.pm.PackageManager;
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
import java.util.Collections;
import java.util.List;

public class StockValueReportActivity extends AppCompatActivity {

    private RecyclerView recyclerViewReport;
    private ProgressBar progressBar;
    private TextView tvNoData, tvTotalInventoryValue, tvTotalCostValue, tvTotalProfitValue;
    private Button btnExportPDF, btnExportCSV;
    private StockValueReportAdapter adapter;
    private List<StockValueReport> reportList;
    private DatabaseReference productRef;
    private ReportExportUtil exportUtil;
    private PDFGenerator pdfGenerator;
    private CSVGenerator csvGenerator;

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_value_report);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Stock Value Report");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initializeViews();
        setupRecyclerView();
        loadStockValueReport();
    }

    private void initializeViews() {
        recyclerViewReport = findViewById(R.id.recyclerViewReport);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);
        tvTotalInventoryValue = findViewById(R.id.tvTotalInventoryValue);
        tvTotalCostValue = findViewById(R.id.tvTotalCostValue);
        tvTotalProfitValue = findViewById(R.id.tvTotalProfitValue);
        btnExportPDF = findViewById(R.id.btnExportPDF);
        btnExportCSV = findViewById(R.id.btnExportCSV);

        reportList = new ArrayList<>();
        productRef = FirebaseDatabase.getInstance().getReference("Product");
        exportUtil = new ReportExportUtil(this);

        // FIX: Wrapped initialization in try-catch block
        try {
            pdfGenerator = new PDFGenerator(this);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing PDF Generator", Toast.LENGTH_SHORT).show();
            btnExportPDF.setEnabled(false);
        }

        csvGenerator = new CSVGenerator();

        // Export button listeners
        btnExportPDF.setOnClickListener(v -> exportToPDF());
        btnExportCSV.setOnClickListener(v -> exportToCSV());
    }

    private void setupRecyclerView() {
        adapter = new StockValueReportAdapter(reportList);
        recyclerViewReport.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewReport.setAdapter(adapter);
    }

    private void loadStockValueReport() {
        progressBar.setVisibility(View.VISIBLE);
        tvNoData.setVisibility(View.GONE);

        productRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                reportList.clear();
                double totalCostValue = 0;
                double totalSellingValue = 0;
                double totalProfit = 0;

                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Product product = dataSnapshot.getValue(Product.class);
                    if (product != null && product.isActive()) {
                        StockValueReport report = new StockValueReport(
                                product.getProductId(),
                                product.getProductName(),
                                product.getCategoryName(),
                                product.getQuantity(),
                                product.getCostPrice(),
                                product.getSellingPrice(),
                                product.getReorderLevel(),
                                product.getCriticalLevel(),
                                product.getCeilingLevel()
                        );
                        reportList.add(report);

                        totalCostValue += report.getTotalCostValue();
                        totalSellingValue += report.getTotalSellingValue();
                        totalProfit += report.getProfit();
                    }
                }

                // Sort by profit (highest first)
                Collections.sort(reportList, (a, b) -> Double.compare(b.getProfit(), a.getProfit()));

                progressBar.setVisibility(View.GONE);

                if (reportList.isEmpty()) {
                    tvNoData.setVisibility(View.VISIBLE);
                    recyclerViewReport.setVisibility(View.GONE);
                    btnExportPDF.setEnabled(false);
                    btnExportCSV.setEnabled(false);
                } else {
                    tvNoData.setVisibility(View.GONE);
                    recyclerViewReport.setVisibility(View.VISIBLE);
                    btnExportPDF.setEnabled(true);
                    btnExportCSV.setEnabled(true);

                    // Update summary
                    tvTotalCostValue.setText("₱" + String.format("%.2f", totalCostValue));
                    tvTotalInventoryValue.setText("₱" + String.format("%.2f", totalSellingValue));
                    tvTotalProfitValue.setText("₱" + String.format("%.2f", totalProfit));

                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(StockValueReportActivity.this,
                        "Error loading report: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void exportToPDF() {
        if (!exportUtil.isStorageAvailable()) {
            exportUtil.showExportError("Storage not available");
            return;
        }

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
            return;
        }

        // Check if generator initialized successfully
        if (pdfGenerator == null) {
            exportUtil.showExportError("PDF Generator not ready");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        try {
            File exportDir = exportUtil.getExportDirectory();
            String fileName = exportUtil.generateFileName("StockValue_Report", ReportExportUtil.EXPORT_PDF);
            File outputFile = new File(exportDir, fileName);

            pdfGenerator.generateStockValueReportPDF(outputFile, reportList);

            progressBar.setVisibility(View.GONE);
            exportUtil.showExportSuccess(outputFile.getAbsolutePath());
        } catch (Exception e) {
            progressBar.setVisibility(View.GONE);
            exportUtil.showExportError(e.getMessage());
        }
    }

    private void exportToCSV() {
        if (!exportUtil.isStorageAvailable()) {
            exportUtil.showExportError("Storage not available");
            return;
        }

        if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE);
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        try {
            File exportDir = exportUtil.getExportDirectory();
            String fileName = exportUtil.generateFileName("StockValue_Report", ReportExportUtil.EXPORT_CSV);
            File outputFile = new File(exportDir, fileName);

            csvGenerator.generateStockValueReportCSV(outputFile, reportList);

            progressBar.setVisibility(View.GONE);
            exportUtil.showExportSuccess(outputFile.getAbsolutePath());
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