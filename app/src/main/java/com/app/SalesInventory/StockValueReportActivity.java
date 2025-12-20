package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class StockValueReportActivity extends BaseActivity {

    private RecyclerView recyclerViewReport;
    private ProgressBar progressBar;
    private TextView tvNoData, tvTotalInventoryValue, tvTotalCostValue, tvTotalProfitValue;
    private Button btnExportPDF, btnExportCSV;
    private StockValueReportAdapter adapter;
    private List<StockValueReport> reportList;
    private ReportExportUtil exportUtil;
    private PDFGenerator pdfGenerator;
    private CSVGenerator csvGenerator;
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor();
    private ProductRepository productRepository;
    private SalesRepository salesRepository;
    private List<Product> latestProducts;
    private List<Sales> latestSales;

    private static final int PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_value_report);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Stock Value Report");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        recyclerViewReport = findViewById(R.id.recyclerViewReport);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);
        tvTotalInventoryValue = findViewById(R.id.tvTotalInventoryValue);
        tvTotalCostValue = findViewById(R.id.tvTotalCostValue);
        tvTotalProfitValue = findViewById(R.id.tvTotalProfitValue);
        btnExportPDF = findViewById(R.id.btnExportPDF);
        btnExportCSV = findViewById(R.id.btnExportCSV);
        reportList = new ArrayList<>();
        adapter = new StockValueReportAdapter(reportList);
        recyclerViewReport.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewReport.setAdapter(adapter);
        exportUtil = new ReportExportUtil(this);
        try {
            pdfGenerator = new PDFGenerator(this);
        } catch (Exception e) {
            pdfGenerator = null;
        }
        csvGenerator = new CSVGenerator();
        btnExportPDF.setOnClickListener(v -> startExport(ReportExportUtil.EXPORT_PDF));
        btnExportCSV.setOnClickListener(v -> startExport(ReportExportUtil.EXPORT_CSV));
        productRepository = SalesInventoryApplication.getProductRepository();
        salesRepository = SalesInventoryApplication.getSalesRepository();
        observeData();
    }

    private void observeData() {
        try {
            LiveData<List<Product>> productsLive = productRepository.getAllProducts();
            if (productsLive != null) {
                productsLive.observe(this, new Observer<List<Product>>() {
                    @Override
                    public void onChanged(List<Product> products) {
                        latestProducts = products;
                        rebuildReport();
                    }
                });
            }
        } catch (Exception ignored) {}
        try {
            LiveData<List<Sales>> salesLive = salesRepository.getAllSales();
            if (salesLive != null) {
                salesLive.observe(this, new Observer<List<Sales>>() {
                    @Override
                    public void onChanged(List<Sales> sales) {
                        latestSales = sales;
                        rebuildReport();
                    }
                });
            }
        } catch (Exception ignored) {}
    }

    private void rebuildReport() {
        if (progressBar != null) progressBar.setVisibility(android.view.View.VISIBLE);
        reportList.clear();
        double totalCostValue = 0;
        double totalSellingValue = 0;
        double totalProfit = 0;
        Map<String, Integer> soldMap = new HashMap<>();
        if (latestSales != null) {
            for (Sales s : latestSales) {
                if (s == null) continue;
                String pid = s.getProductId();
                if (pid == null) continue;
                int q = s.getQuantity();
                if (q <= 0) continue;
                Integer prev = soldMap.get(pid);
                soldMap.put(pid, (prev == null ? 0 : prev) + q);
            }
        }
        if (latestProducts != null) {
            for (Product p : latestProducts) {
                if (p == null) continue;
                if (!p.isActive()) continue;
                String pid = p.getProductId();
                int sold = 0;
                if (pid != null) {
                    Integer v = soldMap.get(pid);
                    sold = v == null ? 0 : v;
                }
                int currentQty = p.getQuantity();
                double costToComplete = p.getCostToComplete();
                double sellingCosts = p.getSellingCosts();
                double normalProfitPercent = p.getNormalProfitPercent();
                double unitCeiling = p.getSellingPrice() - (costToComplete + sellingCosts);
                if (unitCeiling < 0) unitCeiling = 0;
                double unitFloor = unitCeiling * (1.0 - (normalProfitPercent / 100.0));
                if (unitFloor < 0) unitFloor = 0;
                double unitMarket = Math.max(unitFloor, Math.min(p.getSellingPrice(), unitCeiling));
                StockValueReport r = new StockValueReport(
                        pid,
                        p.getProductName(),
                        p.getCategoryName(),
                        currentQty,
                        p.getCostPrice(),
                        p.getSellingPrice(),
                        p.getReorderLevel(),
                        p.getCriticalLevel(),
                        p.getCeilingLevel(),
                        p.getFloorLevel(),
                        unitCeiling,
                        unitFloor,
                        unitMarket
                );
                reportList.add(r);
                totalCostValue += r.getTotalCostValue();
                totalSellingValue += r.getTotalSellingValue();
                totalProfit += r.getProfit();
            }
        }
        Collections.sort(reportList, (a, b) -> Double.compare(b.getProfit(), a.getProfit()));
        if (reportList.isEmpty()) {
            if (tvNoData != null) tvNoData.setVisibility(android.view.View.VISIBLE);
            if (recyclerViewReport != null) recyclerViewReport.setVisibility(android.view.View.GONE);
            if (btnExportPDF != null) btnExportPDF.setEnabled(false);
            if (btnExportCSV != null) btnExportCSV.setEnabled(false);
        } else {
            if (tvNoData != null) tvNoData.setVisibility(android.view.View.GONE);
            if (recyclerViewReport != null) recyclerViewReport.setVisibility(android.view.View.VISIBLE);
            if (btnExportPDF != null) btnExportPDF.setEnabled(pdfGenerator != null);
            if (btnExportCSV != null) btnExportCSV.setEnabled(true);
        }
        if (tvTotalCostValue != null) tvTotalCostValue.setText("₱" + String.format(Locale.getDefault(), "%.2f", totalCostValue));
        if (tvTotalInventoryValue != null) tvTotalInventoryValue.setText("₱" + String.format(Locale.getDefault(), "%.2f", totalSellingValue));
        if (tvTotalProfitValue != null) tvTotalProfitValue.setText("₱" + String.format(Locale.getDefault(), "%.2f", totalProfit));
        adapter.notifyDataSetChanged();
        if (progressBar != null) progressBar.setVisibility(android.view.View.GONE);
    }

    private void startExport(int exportType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
                return;
            }
        }
        if (exportType == ReportExportUtil.EXPORT_PDF) exportToPDF();
        else exportToCSV();
    }

    private void exportToPDF() {
        if (exportUtil == null || !exportUtil.isStorageAvailable()) {
            if (exportUtil != null) exportUtil.showExportError("Storage not available");
            else Toast.makeText(this, "Export utility not available", Toast.LENGTH_SHORT).show();
            return;
        }
        if (pdfGenerator == null) {
            exportUtil.showExportError("PDF Generator not ready");
            return;
        }
        if (reportList == null || reportList.isEmpty()) {
            exportUtil.showExportError("No inventory data to export");
            return;
        }
        if (progressBar != null) progressBar.setVisibility(android.view.View.VISIBLE);
        String fileName = exportUtil.generateFileName("StockValue_Report", ReportExportUtil.EXPORT_PDF);
        ReportExportUtil.ExportResult r;
        try {
            r = exportUtil.createOutputStreamForFile(fileName, ReportExportUtil.EXPORT_PDF);
        } catch (Exception e) {
            if (progressBar != null) progressBar.setVisibility(android.view.View.GONE);
            exportUtil.showExportError("Unable to obtain output stream");
            return;
        }
        if (r == null || r.outputStream == null) {
            if (progressBar != null) progressBar.setVisibility(android.view.View.GONE);
            exportUtil.showExportError("Unable to obtain output stream");
            return;
        }
        final ReportExportUtil.ExportResult res = r;
        exportExecutor.execute(() -> {
            try {
                pdfGenerator.generateStockValueReportPDF(res.outputStream, reportList);
                try { res.outputStream.close(); } catch (Exception ignored) {}
                runOnUiThread(() -> {
                    exportUtil.showExportSuccess(res.displayPath);
                    if (progressBar != null) progressBar.setVisibility(android.view.View.GONE);
                });
            } catch (Exception e) {
                try { res.outputStream.close(); } catch (Exception ignored) {}
                runOnUiThread(() -> {
                    exportUtil.showExportError(e.getMessage() == null ? "Export failed" : e.getMessage());
                    if (progressBar != null) progressBar.setVisibility(android.view.View.GONE);
                });
            }
        });
    }

    private void exportToCSV() {
        if (exportUtil == null || !exportUtil.isStorageAvailable()) {
            if (exportUtil != null) exportUtil.showExportError("Storage not available");
            else Toast.makeText(this, "Export utility not available", Toast.LENGTH_SHORT).show();
            return;
        }
        if (reportList == null || reportList.isEmpty()) {
            exportUtil.showExportError("No inventory data to export");
            return;
        }
        if (progressBar != null) progressBar.setVisibility(android.view.View.VISIBLE);
        String fileName = exportUtil.generateFileName("StockValue_Report", ReportExportUtil.EXPORT_CSV);
        ReportExportUtil.ExportResult r;
        try {
            r = exportUtil.createOutputStreamForFile(fileName, ReportExportUtil.EXPORT_CSV);
        } catch (Exception e) {
            if (progressBar != null) progressBar.setVisibility(android.view.View.GONE);
            exportUtil.showExportError("Unable to obtain output stream");
            return;
        }
        if (r == null || r.outputStream == null) {
            if (progressBar != null) progressBar.setVisibility(android.view.View.GONE);
            exportUtil.showExportError("Unable to obtain output stream");
            return;
        }
        final ReportExportUtil.ExportResult res = r;
        exportExecutor.execute(() -> {
            try {
                csvGenerator.generateStockValueReportCSV(res.outputStream, reportList);
                try { res.outputStream.close(); } catch (Exception ignored) {}
                runOnUiThread(() -> {
                    exportUtil.showExportSuccess(res.displayPath);
                    if (progressBar != null) progressBar.setVisibility(android.view.View.GONE);
                });
            } catch (Exception e) {
                try { res.outputStream.close(); } catch (Exception ignored) {}
                runOnUiThread(() -> {
                    exportUtil.showExportError(e.getMessage() == null ? "Export failed" : e.getMessage());
                    if (progressBar != null) progressBar.setVisibility(android.view.View.GONE);
                });
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}