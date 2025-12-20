package com.app.SalesInventory;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InventoryReportsActivity extends BaseActivity {

    private static final String TAG = "InventoryReportsActivity";
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

        if (btnStockValue != null) btnStockValue.setClickable(true);
        if (btnStockMovement != null) btnStockMovement.setClickable(true);
        if (btnAdjustmentSummary != null) btnAdjustmentSummary.setClickable(true);
        if (btnExport != null) btnExport.setClickable(true);
        if (btnDeliveryReport != null) btnDeliveryReport.setClickable(true);
        if (btnReceivingReport != null) btnReceivingReport.setClickable(true);
    }

    private void setupClickListeners() {
        if (btnStockValue != null) btnStockValue.setOnClickListener(v -> {
            Toast.makeText(this, "Opening Stock Value Report...", Toast.LENGTH_SHORT).show();
            safeStartActivity(StockValueReportActivity.class);
        });
        if (btnStockMovement != null) btnStockMovement.setOnClickListener(v -> {
            Toast.makeText(this, "Opening Stock Movement Report...", Toast.LENGTH_SHORT).show();
            safeStartActivity(StockMovementReportActivity.class);
        });
        if (btnAdjustmentSummary != null) btnAdjustmentSummary.setOnClickListener(v -> {
            Toast.makeText(this, "Opening Adjustment Summary...", Toast.LENGTH_SHORT).show();
            safeStartActivity(AdjustmentSummaryReportActivity.class);
        });
        if (btnReceivingReport != null) btnReceivingReport.setOnClickListener(v -> {
            Toast.makeText(this, "Opening Receiving Report...", Toast.LENGTH_SHORT).show();
            safeStartActivity(ReceivingReportActivity.class);
        });
        if (btnDeliveryReport != null) btnDeliveryReport.setOnClickListener(v -> {
            Toast.makeText(this, "Opening Delivery Report...", Toast.LENGTH_SHORT).show();
            safeStartActivity(DeliveryReportActivity.class);
        });

        if (btnExport != null) btnExport.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
                    return;
                }
            }
            Toast.makeText(this, "Preparing combined PDF...", Toast.LENGTH_SHORT).show();
            exportAllReportsPdf();
        });
    }

    private void safeStartActivity(Class<?> cls) {
        try {
            Intent intent = new Intent(this, cls);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Failed to start activity " + (cls == null ? "null" : cls.getName()), e);
            String msg = "Cannot open report: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
    }

    private void exportAllReportsPdf() {
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
                                Integer prev = soldMap.get(pid);
                                soldMap.put(pid, (prev != null ? prev : 0) + q);
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
                                        Integer prev = receivedMap.get(pid);
                                        receivedMap.put(pid, (prev != null ? prev : 0) + qty);
                                        totalReceivedHolder[0] += qty;
                                    } else {
                                        Integer prevAdj = adjustedMap.get(pid);
                                        adjustedMap.put(pid, (prevAdj != null ? prevAdj : 0) + qty);
                                        totalAdjustedHolder[0] += qty;
                                    }
                                }
                                try {
                                    List<StockValueReport> valueReports = new ArrayList<>();
                                    List<StockMovementReport> movementReports = new ArrayList<>();
                                    List<AdjustmentSummaryData> adjustmentSummaries = new ArrayList<>();
                                    for (Product p : products) {
                                        double costToComplete = p.getCostToComplete();
                                        double sellingCosts = p.getSellingCosts();
                                        double normalProfitPercent = p.getNormalProfitPercent();
                                        double unitCeiling = p.getSellingPrice() - (costToComplete + sellingCosts);
                                        if (unitCeiling < 0) unitCeiling = 0;
                                        double unitFloor = unitCeiling * (1.0 - (normalProfitPercent / 100.0));
                                        if (unitFloor < 0) unitFloor = 0;
                                        double unitMarket = Math.max(unitFloor, Math.min(p.getSellingPrice(), unitCeiling));
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
                                                p.getFloorLevel(),
                                                unitCeiling,
                                                unitFloor,
                                                unitMarket
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