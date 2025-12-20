package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StockMovementReportActivity extends AppCompatActivity {

    private TextView tvTotalReceived;
    private TextView tvTotalSold;
    private TextView tvTotalAdjustments;
    private Button btnExportPDF;
    private Button btnExportCSV;
    private ProgressBar progressBar;
    private TextView tvNoData;
    private RecyclerView recyclerViewReport;
    private StockMovementAdapter adapter;
    private ProductRepository productRepository;
    private InventoryMovementsRepository movementsRepository;
    private ReportExportUtil exportUtil;
    private PDFGenerator pdfGenerator;
    private CSVGenerator csvGenerator;
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_movement_report);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Stock Movement Report");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvTotalReceived = findViewById(R.id.tvTotalReceived);
        tvTotalSold = findViewById(R.id.tvTotalSold);
        tvTotalAdjustments = findViewById(R.id.tvTotalAdjustments);
        btnExportPDF = findViewById(R.id.btnExportPDF);
        btnExportCSV = findViewById(R.id.btnExportCSV);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);
        recyclerViewReport = findViewById(R.id.recyclerViewReport);

        adapter = new StockMovementAdapter(new ArrayList<>());
        recyclerViewReport.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewReport.setAdapter(adapter);

        productRepository = ProductRepository.getInstance(getApplication());
        movementsRepository = InventoryMovementsRepository.getInstance(getApplication());
        exportUtil = new ReportExportUtil(this);
        try {
            pdfGenerator = new PDFGenerator(this);
        } catch (Exception e) {
            pdfGenerator = null;
        }
        csvGenerator = new CSVGenerator();

        btnExportPDF.setOnClickListener(v -> exportPdf());
        btnExportCSV.setOnClickListener(v -> exportCsv());

        loadData();
    }

    private long getStartOfMonthMillis() {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);
        productRepository.getAllProducts().observe(this, products -> {
            movementsRepository.getAllMovements().observe(this, movements -> {
                try {
                    computeAndDisplay(products, movements);
                } catch (Exception e) {
                    progressBar.setVisibility(View.GONE);
                    tvNoData.setVisibility(View.VISIBLE);
                    recyclerViewReport.setVisibility(View.GONE);
                }
            });
        });
    }

    private void computeAndDisplay(List<Product> products, List<InventoryMovement> movements) {
        Map<String, StockMovementReport> map = new HashMap<>();
        if (products != null) {
            for (Product p : products) {
                if (p == null || !p.isActive()) continue;
                String pid = p.getProductId();
                if (pid == null) continue;
                StockMovementReport smr = new StockMovementReport(
                        pid,
                        p.getProductName() == null ? "" : p.getProductName(),
                        p.getCategoryName() == null ? "" : p.getCategoryName(),
                        0,
                        0,
                        0,
                        0,
                        p.getQuantity(),
                        System.currentTimeMillis()
                );
                map.put(pid, smr);
            }
        }

        long startMonth = getStartOfMonthMillis();
        int totalReceived = 0;
        int totalSold = 0;
        int totalAdjustments = 0;

        if (movements != null) {
            for (InventoryMovement m : movements) {
                if (m == null) continue;
                String pid = m.getProductId();
                if (pid == null) continue;
                long ts = m.getTimestamp();
                if (ts < startMonth) continue;
                StockMovementReport smr = map.get(pid);
                if (smr == null) {
                    smr = new StockMovementReport(
                            pid,
                            m.getProductName() == null ? "" : m.getProductName(),
                            "",
                            0, 0, 0, 0, 0, System.currentTimeMillis()
                    );
                    map.put(pid, smr);
                }
                String type = m.getType() == null ? "" : m.getType().toUpperCase(Locale.ROOT);
                int qty = m.getChange();
                if ("RECEIVE".equals(type) || "IN".equals(type) || "ADD".equals(type)) {
                    smr.addReceived(Math.max(0, qty));
                    totalReceived += Math.max(0, qty);
                } else if ("SALE".equals(type) || "OUT".equals(type) || "REMOVE".equals(type)) {
                    smr.addSold(Math.max(0, Math.abs(qty)));
                    totalSold += Math.max(0, Math.abs(qty));
                } else if ("ADJUST".equals(type) || "ADJUSTMENT".equals(type)) {
                    smr.addAdjusted(qty);
                    totalAdjustments += Math.abs(qty);
                } else {
                    if (qty > 0) {
                        smr.addReceived(qty);
                        totalReceived += qty;
                    } else {
                        smr.addSold(Math.abs(qty));
                        totalSold += Math.abs(qty);
                    }
                }
            }
        }

        List<StockMovementReport> list = new ArrayList<>(map.values());
        for (StockMovementReport s : list) {
            s.setClosingStock(s.getClosingStock());
            s.calculateOpening();
        }

        Collections.sort(list, (a, b) -> Integer.compare(b.getSold(), a.getSold()));

        tvTotalReceived.setText(String.format(Locale.getDefault(), "%d units", totalReceived));
        tvTotalSold.setText(String.format(Locale.getDefault(), "%d units", totalSold));
        tvTotalAdjustments.setText(String.format(Locale.getDefault(), "%d units", totalAdjustments));

        if (list.isEmpty()) {
            tvNoData.setVisibility(View.VISIBLE);
            recyclerViewReport.setVisibility(View.GONE);
        } else {
            tvNoData.setVisibility(View.GONE);
            recyclerViewReport.setVisibility(View.VISIBLE);
            adapter = new StockMovementAdapter(list);
            recyclerViewReport.setAdapter(adapter);
        }

        progressBar.setVisibility(View.GONE);
    }

    private void exportPdf() {
        if (exportUtil == null || !exportUtil.isStorageAvailable()) {
            if (exportUtil != null) exportUtil.showExportError("Storage not available");
            else Toast.makeText(this, "Export utility not available", Toast.LENGTH_SHORT).show();
            return;
        }
        List<StockMovementReport> items = adapter == null ? null : adapter.getReportList();
        if (items == null || items.isEmpty()) {
            exportUtil.showExportError("No movement data to export");
            return;
        }
        String fileName = exportUtil.generateFileName("StockMovement_Report", ReportExportUtil.EXPORT_PDF);
        ReportExportUtil.ExportResult r;
        try {
            r = exportUtil.createOutputStreamForFile(fileName, ReportExportUtil.EXPORT_PDF);
        } catch (Exception e) {
            exportUtil.showExportError("Unable to get output");
            return;
        }
        if (r == null || r.outputStream == null) {
            exportUtil.showExportError("Unable to get output");
            return;
        }
        final ReportExportUtil.ExportResult res = r;
        exportExecutor.execute(() -> {
            try {
                int totalReceived = 0;
                int totalSold = 0;
                int totalAdj = 0;
                for (StockMovementReport s : items) {
                    totalReceived += s.getReceived();
                    totalSold += s.getSold();
                    totalAdj += Math.abs(s.getAdjusted());
                }
                PDFGenerator gen = new PDFGenerator(StockMovementReportActivity.this);
                gen.generateStockMovementReportPDF(res.outputStream, items, totalReceived, totalSold, totalAdj);
                try { res.outputStream.close(); } catch (Exception ignored) {}
                runOnUiThread(() -> exportUtil.showExportSuccess(res.displayPath));
            } catch (Exception e) {
                try { res.outputStream.close(); } catch (Exception ignored) {}
                runOnUiThread(() -> exportUtil.showExportError(e.getMessage() == null ? "Export failed" : e.getMessage()));
            }
        });
    }

    private void exportCsv() {
        if (exportUtil == null || !exportUtil.isStorageAvailable()) {
            if (exportUtil != null) exportUtil.showExportError("Storage not available");
            else Toast.makeText(this, "Export utility not available", Toast.LENGTH_SHORT).show();
            return;
        }
        List<StockMovementReport> items = adapter == null ? null : adapter.getReportList();
        if (items == null || items.isEmpty()) {
            exportUtil.showExportError("No movement data to export");
            return;
        }
        String fileName = exportUtil.generateFileName("StockMovement_Report", ReportExportUtil.EXPORT_CSV);
        ReportExportUtil.ExportResult r;
        try {
            r = exportUtil.createOutputStreamForFile(fileName, ReportExportUtil.EXPORT_CSV);
        } catch (Exception e) {
            exportUtil.showExportError("Unable to get output");
            return;
        }
        if (r == null || r.outputStream == null) {
            exportUtil.showExportError("Unable to get output");
            return;
        }
        final ReportExportUtil.ExportResult res = r;
        exportExecutor.execute(() -> {
            try {
                int totalReceived = 0;
                int totalSold = 0;
                int totalAdj = 0;
                for (StockMovementReport s : items) {
                    totalReceived += s.getReceived();
                    totalSold += s.getSold();
                    totalAdj += Math.abs(s.getAdjusted());
                }
                CSVGenerator csv = new CSVGenerator();
                csv.generateStockMovementReportCSV(res.outputStream, items, totalReceived, totalSold, totalAdj);
                try { res.outputStream.close(); } catch (Exception ignored) {}
                runOnUiThread(() -> exportUtil.showExportSuccess(res.displayPath));
            } catch (Exception e) {
                try { res.outputStream.close(); } catch (Exception ignored) {}
                runOnUiThread(() -> exportUtil.showExportError(e.getMessage() == null ? "Export failed" : e.getMessage()));
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}