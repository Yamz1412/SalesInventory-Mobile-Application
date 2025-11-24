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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdjustmentSummaryReportActivity extends AppCompatActivity {

    private RecyclerView recyclerViewReport;
    private ProgressBar progressBar;
    private TextView tvNoData, tvTotalAdjustments, tvAdditions, tvRemovals;
    private Button btnExportPDF, btnExportCSV;
    private AdjustmentSummaryAdapter adapter;
    private List<AdjustmentSummaryData> summaryList;
    private DatabaseReference adjustmentRef;
    private ReportExportUtil exportUtil;
    private PDFGenerator pdfGenerator;
    private CSVGenerator csvGenerator;

    private int totalAdjustmentsCount = 0;
    private int totalAdditionsCount = 0;
    private int totalRemovalsCount = 0;

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adjustment_summary_report);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Adjustment Summary Report");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initializeViews();
        setupRecyclerView();
        loadAdjustmentSummary();
    }

    private void initializeViews() {
        recyclerViewReport = findViewById(R.id.recyclerViewReport);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);
        tvTotalAdjustments = findViewById(R.id.tvTotalAdjustments);
        tvAdditions = findViewById(R.id.tvAdditions);
        tvRemovals = findViewById(R.id.tvRemovals);
        btnExportPDF = findViewById(R.id.btnExportPDF);
        btnExportCSV = findViewById(R.id.btnExportCSV);

        summaryList = new ArrayList<>();
        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");
        exportUtil = new ReportExportUtil(this);

        // FIX 1: Handle the unhandled exception for PDFGenerator
        try {
            pdfGenerator = new PDFGenerator(this);
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing PDF Generator", Toast.LENGTH_SHORT).show();
            btnExportPDF.setEnabled(false); // Disable button if initialization fails
        }

        csvGenerator = new CSVGenerator();

        // Export button listeners
        btnExportPDF.setOnClickListener(v -> exportToPDF());
        btnExportCSV.setOnClickListener(v -> exportToCSV());
    }

    private void setupRecyclerView() {
        adapter = new AdjustmentSummaryAdapter(summaryList);
        recyclerViewReport.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewReport.setAdapter(adapter);
    }

    private void loadAdjustmentSummary() {
        progressBar.setVisibility(View.VISIBLE);
        tvNoData.setVisibility(View.GONE);

        adjustmentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                summaryList.clear();
                Map<String, AdjustmentSummaryData> summaryMap = new HashMap<>();
                totalAdjustmentsCount = 0;
                totalAdditionsCount = 0;
                totalRemovalsCount = 0;

                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    StockAdjustment adjustment = dataSnapshot.getValue(StockAdjustment.class);
                    if (adjustment != null) {
                        String productId = adjustment.getProductId();
                        String productName = adjustment.getProductName();
                        String reason = adjustment.getReason();

                        // FIX 2: Safe retrieval of summary data to prevent NullPointerException
                        // Replaced the version check with standard Map logic
                        AdjustmentSummaryData summary = summaryMap.get(productId);
                        if (summary == null) {
                            summary = new AdjustmentSummaryData(productId, productName);
                        }

                        if ("Add Stock".equals(adjustment.getAdjustmentType())) {
                            summary.addAddition(adjustment.getQuantityAdjusted(), reason);
                            totalAdditionsCount += adjustment.getQuantityAdjusted();
                        } else if ("Remove Stock".equals(adjustment.getAdjustmentType())) {
                            summary.addRemoval(adjustment.getQuantityAdjusted(), reason);
                            totalRemovalsCount += adjustment.getQuantityAdjusted();
                        }

                        summaryMap.put(productId, summary);
                        totalAdjustmentsCount++;
                    }
                }

                summaryList.addAll(summaryMap.values());
                Collections.sort(summaryList, (a, b) ->
                        Integer.compare(b.getTotalAdjustments(), a.getTotalAdjustments()));

                progressBar.setVisibility(View.GONE);

                if (summaryList.isEmpty()) {
                    tvNoData.setVisibility(View.VISIBLE);
                    recyclerViewReport.setVisibility(View.GONE);
                    btnExportPDF.setEnabled(false);
                    btnExportCSV.setEnabled(false);
                } else {
                    tvNoData.setVisibility(View.GONE);
                    recyclerViewReport.setVisibility(View.VISIBLE);
                    btnExportPDF.setEnabled(true);
                    btnExportCSV.setEnabled(true);

                    // FIX 3: Removed unnecessary String.valueOf() calls
                    tvTotalAdjustments.setText(totalAdjustmentsCount + " adjustments");
                    tvAdditions.setText("+" + totalAdditionsCount + " units added");
                    tvRemovals.setText("-" + totalRemovalsCount + " units removed");

                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(AdjustmentSummaryReportActivity.this,
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

        if (pdfGenerator == null) {
            exportUtil.showExportError("PDF Generator not initialized");
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        try {
            File exportDir = exportUtil.getExportDirectory();
            String fileName = exportUtil.generateFileName("AdjustmentSummary_Report", ReportExportUtil.EXPORT_PDF);
            File outputFile = new File(exportDir, fileName);

            pdfGenerator.generateAdjustmentSummaryReportPDF(outputFile, summaryList,
                    totalAdjustmentsCount, totalAdditionsCount, totalRemovalsCount);

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
            String fileName = exportUtil.generateFileName("AdjustmentSummary_Report", ReportExportUtil.EXPORT_CSV);
            File outputFile = new File(exportDir, fileName);

            csvGenerator.generateAdjustmentSummaryReportCSV(outputFile, summaryList,
                    totalAdjustmentsCount, totalAdditionsCount, totalRemovalsCount);

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