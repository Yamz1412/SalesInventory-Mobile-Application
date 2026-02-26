package com.app.SalesInventory;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InventoryReportsActivity extends BaseActivity  {

    private Button btnStockValue;
    private Button btnStockMovement;
    private Button btnAdjustmentSummary;
    private Button btnExport;
    private Button btnDeliveryReport;
    private Button btnReceivingReport;

    private DatabaseReference productRef;
    private DatabaseReference salesRef;
    private DatabaseReference adjustmentRef;

    private ReportExportUtil exportUtil;

    private static final int PERMISSION_REQUEST_CODE = 300;

    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_reports);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Inventory Reports");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        initializeViews();
        setupClickListeners();
    }

    private void initializeViews() {
        btnStockValue = findViewById(R.id.btnStockValue);
        btnStockMovement = findViewById(R.id.btnStockMovement);
        btnAdjustmentSummary = findViewById(R.id.btnAdjustmentSummary);
        btnExport = findViewById(R.id.btnExport);
        btnDeliveryReport = findViewById(R.id.btnDeliveryReport);
        btnReceivingReport = findViewById(R.id.btnReceivingReport);
        productRef = FirebaseDatabase.getInstance().getReference("Product");
        salesRef = FirebaseDatabase.getInstance().getReference("Sales");
        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");
        exportUtil = new ReportExportUtil(this);
    }

    private void setupClickListeners() {
        btnStockValue.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, StockValueReportActivity.class)));
        btnStockMovement.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, StockMovementReportActivity.class)));
        btnAdjustmentSummary.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, AdjustmentSummaryReportActivity.class)));
        btnReceivingReport.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, ReceivingReportActivity.class)));
        btnDeliveryReport.setOnClickListener(v ->
                startActivity(new android.content.Intent(this, DeliveryReportActivity.class)));
        btnExport.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
                    return;
                }
            }
            exportAllReportsPdf();
        });
    }

    private void exportAllReportsPdf() {
        Toast.makeText(this, "Preparing combined PDF...", Toast.LENGTH_SHORT).show();
        productRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot productSnapshot) {
                List<Product> products = new ArrayList<>();
                Map<String, Product> productMap = new HashMap<>();
                for (DataSnapshot ds : productSnapshot.getChildren()) {
                    Product p = ds.getValue(Product.class);
                    if (p != null && p.isActive()) {
                        products.add(p);
                        if (p.getProductId() != null) productMap.put(p.getProductId(), p);
                    }
                }
                salesRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(DataSnapshot salesSnapshot) {
                        Map<String, Integer> soldMap = new HashMap<>();
                        final int[] totalSoldHolder = new int[1];
                        for (DataSnapshot sds : salesSnapshot.getChildren()) {
                            Sales s = sds.getValue(Sales.class);
                            if (s == null) continue;
                            String pid = s.getProductId();
                            int q = s.getQuantity();
                            if (pid != null) {
                                if (soldMap.containsKey(pid)) soldMap.put(pid, soldMap.get(pid) + q);
                                else soldMap.put(pid, q);
                                totalSoldHolder[0] += q;
                            }
                        }
                        adjustmentRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot adjSnapshot) {
                                Map<String, Integer> receivedMap = new HashMap<>();
                                Map<String, Integer> adjustedMap = new HashMap<>();
                                final int[] totalReceivedHolder = new int[1];
                                final int[] totalAdjustedHolder = new int[1];
                                for (DataSnapshot ads : adjSnapshot.getChildren()) {
                                    StockAdjustment a = ads.getValue(StockAdjustment.class);
                                    if (a == null) continue;
                                    String pid = a.getProductId();
                                    int qty = a.getQuantityAdjusted();
                                    if (pid == null) continue;
                                    if ("Add Stock".equals(a.getAdjustmentType())) {
                                        if (receivedMap.containsKey(pid)) receivedMap.put(pid, receivedMap.get(pid) + qty);
                                        else receivedMap.put(pid, qty);
                                        totalReceivedHolder[0] += qty;
                                    } else {
                                        if (adjustedMap.containsKey(pid)) adjustedMap.put(pid, adjustedMap.get(pid) + qty);
                                        else adjustedMap.put(pid, qty);
                                        totalAdjustedHolder[0] += qty;
                                    }
                                }
                                try {
                                    List<StockValueReport> valueReports = new ArrayList<>();
                                    List<StockMovementReport> movementReports = new ArrayList<>();
                                    List<AdjustmentSummaryData> adjustmentSummaries = new ArrayList<>();
                                    for (Product p : products) {
                                        StockValueReport vr = new StockValueReport(
                                                p.getProductId(),
                                                p.getProductName(),
                                                p.getCategoryName(),
                                                p.getQuantity(),
                                                p.getCostPrice(),
                                                p.getSellingPrice(),
                                                p.getReorderLevel(),
                                                p.getCriticalLevel(),
                                                p.getCeilingLevel(),
                                                p.getFloorLevel()
                                        );
                                        valueReports.add(vr);
                                        int rec = receivedMap.containsKey(p.getProductId()) ? receivedMap.get(p.getProductId()) : 0;
                                        int sold = soldMap.containsKey(p.getProductId()) ? soldMap.get(p.getProductId()) : 0;
                                        int adj = adjustedMap.containsKey(p.getProductId()) ? adjustedMap.get(p.getProductId()) : 0;
                                        StockMovementReport mr = new StockMovementReport(
                                                p.getProductId(),
                                                p.getProductName(),
                                                p.getCategoryName(),
                                                p.getQuantity(),
                                                rec,
                                                sold,
                                                adj,
                                                p.getQuantity(),
                                                System.currentTimeMillis()
                                        );
                                        mr.calculateOpening();
                                        movementReports.add(mr);
                                        AdjustmentSummaryData asd = new AdjustmentSummaryData(p.getProductId(), p.getProductName());
                                        asd.setTotalAdditions(rec);
                                        asd.setTotalRemovals(adj);
                                        asd.setTotalAdjustments(rec + adj);
                                        adjustmentSummaries.add(asd);
                                    }
                                    String fileName = exportUtil.generateFileName("Inventory_AllReports", ReportExportUtil.EXPORT_PDF);
                                    ReportExportUtil.ExportResult res = exportUtil.createOutputStreamForFile(fileName, ReportExportUtil.EXPORT_PDF);
                                    if (res == null || res.outputStream == null) throw new Exception("Unable to create export stream");
                                    exportExecutor.execute(() -> {
                                        try {
                                            PDFGenerator generator = new PDFGenerator(InventoryReportsActivity.this);
                                            generator.generateCombinedInventoryReportPDF(res.outputStream, valueReports, movementReports, adjustmentSummaries, totalReceivedHolder[0], totalSoldHolder[0], totalAdjustedHolder[0]);
                                            try { res.outputStream.close(); } catch (Exception ignored) {}
                                            runOnUiThread(() -> exportUtil.showExportSuccess(res.displayPath));
                                        } catch (Exception e) {
                                            try { res.outputStream.close(); } catch (Exception ignored) {}
                                            runOnUiThread(() -> exportUtil.showExportError(e.getMessage() == null ? "Export failed" : e.getMessage()));
                                        }
                                    });
                                } catch (Exception e) {
                                    exportUtil.showExportError(e.getMessage() == null ? "Export failed" : e.getMessage());
                                }
                            }
                            @Override
                            public void onCancelled(DatabaseError error) {
                                exportUtil.showExportError("Failed to load adjustments: " + error.getMessage());
                            }
                        });
                    }
                    @Override
                    public void onCancelled(DatabaseError error) {
                        exportUtil.showExportError("Failed to load sales: " + error.getMessage());
                    }
                });
            }
            @Override
            public void onCancelled(DatabaseError error) {
                exportUtil.showExportError("Failed to load products: " + error.getMessage());
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}