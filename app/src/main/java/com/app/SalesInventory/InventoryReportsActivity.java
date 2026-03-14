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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InventoryReportsActivity extends BaseActivity  {

    private Button btnStockValue, btnStockMovement, btnAdjustmentSummary, btnExport;
    private Button btnDeliveryReport, btnReceivingReport;

    private DatabaseReference adjustmentRef;

    private ProductRepository productRepository;
    private SalesRepository salesRepository;

    private List<Product> cachedProducts = new ArrayList<>();
    private List<Sales> cachedSales = new ArrayList<>();

    private ReportExportUtil exportUtil;
    private static final int PERMISSION_REQUEST_CODE = 300;
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor();

    private String currentOwnerId; // NEW: Track the owner ID for safe filtering

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_reports);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Inventory Reports");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();

        initializeViews();
        setupClickListeners();
        loadLocalData();
    }

    private void initializeViews() {
        btnStockValue = findViewById(R.id.btnStockValue);
        btnStockMovement = findViewById(R.id.btnStockMovement);
        btnAdjustmentSummary = findViewById(R.id.btnAdjustmentSummary);
        btnExport = findViewById(R.id.btnExport);
        btnDeliveryReport = findViewById(R.id.btnDeliveryReport);
        btnReceivingReport = findViewById(R.id.btnReceivingReport);

        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");
        exportUtil = new ReportExportUtil(this);

        productRepository = SalesInventoryApplication.getProductRepository();
        salesRepository = SalesRepository.getInstance(getApplication());
    }

    private void loadLocalData() {
        productRepository.getAllProducts().observe(this, products -> {
            cachedProducts.clear();
            if (products != null) cachedProducts.addAll(products);
        });

        salesRepository.getAllSales().observe(this, sales -> {
            cachedSales.clear();
            if (sales != null) cachedSales.addAll(sales);
        });
    }

    private void setupClickListeners() {
        btnStockValue.setOnClickListener(v -> startActivity(new android.content.Intent(this, StockValueReportActivity.class)));
        btnStockMovement.setOnClickListener(v -> startActivity(new android.content.Intent(this, StockMovementReportActivity.class)));
        btnAdjustmentSummary.setOnClickListener(v -> startActivity(new android.content.Intent(this, AdjustmentSummaryReportActivity.class)));
        btnReceivingReport.setOnClickListener(v -> startActivity(new android.content.Intent(this, ReceivingReportActivity.class)));
        btnDeliveryReport.setOnClickListener(v -> startActivity(new android.content.Intent(this, DeliveryReportActivity.class)));

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

        // 1. Organize Sales Data
        Map<String, Integer> soldMap = new HashMap<>();
        final int[] totalSoldHolder = new int[1];

        for (Sales s : cachedSales) {
            String pid = s.getProductId();
            double q = s.getQuantity();
            if (pid != null) {
                soldMap.put(pid, soldMap.getOrDefault(pid, 0) + (int) q);
                totalSoldHolder[0] += q;
            }
        }

        // 2. Fetch Adjustments & Generate PDF safely using the currentOwnerId
        adjustmentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot adjSnapshot) {
                Map<String, Integer> receivedMap = new HashMap<>();
                Map<String, Integer> adjustedMap = new HashMap<>();
                final int[] totalReceivedHolder = new int[1];
                final int[] totalAdjustedHolder = new int[1];

                for (DataSnapshot ads : adjSnapshot.getChildren()) {
                    StockAdjustment a = ads.getValue(StockAdjustment.class);
                    if (a == null || a.getProductId() == null) continue;

                    // FIXED: Ensures you are only exporting YOUR business's adjustments
                    String owner = ads.child("ownerAdminId").getValue(String.class);
                    if (owner != null && !owner.equals(currentOwnerId)) continue;

                    String pid = a.getProductId();
                    double qty = a.getQuantityAdjusted();

                    if ("Add Stock".equals(a.getAdjustmentType())) {
                        receivedMap.put(pid, receivedMap.getOrDefault(pid, 0) + (int)qty);
                        totalReceivedHolder[0] += qty;
                    } else {
                        adjustedMap.put(pid, adjustedMap.getOrDefault(pid, 0) + (int)qty);
                        totalAdjustedHolder[0] += qty;
                    }
                }

                try {
                    List<StockValueReport> valueReports = new ArrayList<>();
                    List<StockMovementReport> movementReports = new ArrayList<>();
                    List<AdjustmentSummaryData> adjustmentSummaries = new ArrayList<>();

                    for (Product p : cachedProducts) {
                        StockValueReport vr = new StockValueReport(
                                p.getProductId(), p.getProductName(), p.getCategoryName(),
                                p.getQuantity(), p.getCostPrice(), p.getSellingPrice(),
                                p.getReorderLevel(), p.getCriticalLevel(), p.getCeilingLevel(), p.getFloorLevel()
                        );
                        valueReports.add(vr);

                        int rec = receivedMap.getOrDefault(p.getProductId(), 0);
                        int sold = soldMap.getOrDefault(p.getProductId(), 0);
                        int adj = adjustedMap.getOrDefault(p.getProductId(), 0);

                        StockMovementReport mr = new StockMovementReport(
                                p.getProductId(), p.getProductName(), p.getCategoryName(),
                                p.getQuantity(), rec, sold, adj, p.getQuantity(), System.currentTimeMillis()
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
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}