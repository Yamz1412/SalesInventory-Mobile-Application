package com.app.SalesInventory;

import android.Manifest;
import android.content.Intent;
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

    private Button btnFmiSmi, btnAdjustmentSummary, btnMasterSummary;
    private Button btnStockMovementReport, btnReceivingReport;

    private DatabaseReference adjustmentRef;

    private ProductRepository productRepository;
    private SalesRepository salesRepository;

    private List<Product> cachedProducts = new ArrayList<>();
    private List<Sales> cachedSales = new ArrayList<>();

    private ReportExportUtil exportUtil;
    private static final int PERMISSION_REQUEST_CODE = 300;
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor();

    private String currentOwnerId;

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
        btnFmiSmi = findViewById(R.id.btnFmiSmi);
        btnAdjustmentSummary = findViewById(R.id.btnAdjustmentSummary);
        btnStockMovementReport = findViewById(R.id.btnStockMovementReport);
        btnReceivingReport = findViewById(R.id.btnReceivingReport);
        btnMasterSummary = findViewById(R.id.btnMasterSummary);

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
        btnFmiSmi.setOnClickListener(v -> startActivity(new android.content.Intent(this, FmiSmiReportActivity.class)));
        btnAdjustmentSummary.setOnClickListener(v -> startActivity(new android.content.Intent(this, AdjustmentSummaryReportActivity.class)));
        btnMasterSummary.setOnClickListener(v -> startActivity(new Intent(this, InventoryMasterSummaryActivity.class)));

        if (btnStockMovementReport != null) {
            btnStockMovementReport.setOnClickListener(v ->
                    startActivity(new Intent(InventoryReportsActivity.this, StockMovementReportActivity.class))
            );
        }

        if (btnReceivingReport != null) {
            btnReceivingReport.setOnClickListener(v -> {
                String[] options = {"📦 Receiving Report (From Suppliers)", "🚚 Delivery Report (To Customers)"};

                new androidx.appcompat.app.AlertDialog.Builder(InventoryReportsActivity.this)
                        .setTitle("Select Report Type")
                        .setItems(options, (dialog, which) -> {
                            if (which == 0) {
                                startActivity(new Intent(InventoryReportsActivity.this, ReceivingReportActivity.class));
                            } else {
                                startActivity(new Intent(InventoryReportsActivity.this, DeliveryReportActivity.class));
                            }
                        })
                        .show();
            });
        }
    }

    private void exportAllReportsPdf() {
        Toast.makeText(this, "Preparing combined PDF...", Toast.LENGTH_SHORT).show();

        // CRITICAL FIX: Extract raw materials from Menu sales for accurate reports
        Map<String, Double> soldMap = new HashMap<>();
        final double[] totalSoldHolder = new double[1];

        for (Sales s : cachedSales) {
            if (s.getStatus() != null && s.getStatus().contains("VOID")) continue;

            double q = s.getQuantity();

            Product soldProduct = null;
            for (Product p : cachedProducts) {
                if (p.getProductId() != null && p.getProductId().equals(s.getProductId())) {
                    soldProduct = p;
                    break;
                }
            }

            if (soldProduct != null && "Menu".equalsIgnoreCase(soldProduct.getProductType()) && soldProduct.getBomList() != null) {
                for (Map<String, Object> bomItem : soldProduct.getBomList()) {
                    String rawName = (String) bomItem.get("materialName");
                    if (rawName == null) rawName = (String) bomItem.get("rawMaterialName");
                    if (rawName == null) continue;

                    double reqQty = 0;
                    try { reqQty = Double.parseDouble(String.valueOf(bomItem.get("quantityRequired"))); } catch (Exception ignored) {}
                    if (reqQty == 0) {
                        try { reqQty = Double.parseDouble(String.valueOf(bomItem.get("quantity"))); } catch (Exception ignored) {}
                    }
                    String reqUnit = (String) bomItem.get("unit");

                    Product rawMat = null;
                    for (Product p : cachedProducts) {
                        if (p.getProductName() != null && p.getProductName().equalsIgnoreCase(rawName)) {
                            rawMat = p;
                            break;
                        }
                    }

                    if (rawMat != null) {
                        int ppu = rawMat.getPiecesPerUnit() > 0 ? rawMat.getPiecesPerUnit() : 1;
                        String invUnit = rawMat.getUnit() != null ? rawMat.getUnit() : "pcs";
                        double deduction = UnitConverterUtil.calculateDeductionAmount(reqQty, invUnit, reqUnit, ppu);

                        double totalDeducted = deduction * q;
                        soldMap.put(rawMat.getProductId(), soldMap.getOrDefault(rawMat.getProductId(), 0.0) + totalDeducted);
                        totalSoldHolder[0] += totalDeducted;
                    }
                }
            } else {
                String pid = s.getProductId();
                if (pid != null) {
                    soldMap.put(pid, soldMap.getOrDefault(pid, 0.0) + q);
                    totalSoldHolder[0] += q;
                }
            }
        }

        adjustmentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot adjSnapshot) {
                Map<String, Double> receivedMap = new HashMap<>();
                Map<String, Double> adjustedMap = new HashMap<>();
                final double[] totalReceivedHolder = new double[1];
                final double[] totalAdjustedHolder = new double[1];

                for (DataSnapshot ads : adjSnapshot.getChildren()) {
                    StockAdjustment a = ads.getValue(StockAdjustment.class);
                    if (a == null || a.getProductId() == null) continue;

                    String owner = ads.child("ownerAdminId").getValue(String.class);
                    if (owner != null && !owner.equals(currentOwnerId)) continue;

                    String pid = a.getProductId();
                    double qty = Math.abs(a.getQuantityAdjusted());
                    String type = a.getAdjustmentType() != null ? a.getAdjustmentType().toUpperCase() : "";
                    String rawType = ads.child("type").getValue(String.class);
                    String dbType = rawType != null ? rawType.toUpperCase() : "";

                    if (type.contains("ADD") || dbType.contains("ADD")) {
                        receivedMap.put(pid, receivedMap.getOrDefault(pid, 0.0) + qty);
                        totalReceivedHolder[0] += qty;
                    } else if (type.contains("DEDUCT") || dbType.contains("DEDUCT") || type.contains("REMOVE")) {
                        adjustedMap.put(pid, adjustedMap.getOrDefault(pid, 0.0) + qty);
                        totalAdjustedHolder[0] += qty;
                    }
                }

                try {
                    List<StockValueReport> valueReports = new ArrayList<>();
                    List<StockMovementReport> movementReports = new ArrayList<>();
                    List<AdjustmentSummaryData> adjustmentSummaries = new ArrayList<>();

                    for (Product p : cachedProducts) {
                        if ("Menu".equalsIgnoreCase(p.getProductType())) {
                            continue;
                        }

                        StockValueReport vr = new StockValueReport(
                                p.getProductId(), p.getProductName(), p.getCategoryName(),
                                p.getQuantity(), p.getCostPrice(), p.getSellingPrice(),
                                p.getReorderLevel(), p.getCriticalLevel(), p.getCeilingLevel(), p.getFloorLevel()
                        );
                        valueReports.add(vr);

                        int rec = (int) Math.round(receivedMap.getOrDefault(p.getProductId(), 0.0));
                        int sold = (int) Math.round(soldMap.getOrDefault(p.getProductId(), 0.0));
                        int adj = (int) Math.round(adjustedMap.getOrDefault(p.getProductId(), 0.0));

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
                            generator.generateCombinedInventoryReportPDF(res.outputStream, valueReports, movementReports, adjustmentSummaries, (int)totalReceivedHolder[0], (int)totalSoldHolder[0], (int)totalAdjustedHolder[0]);
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