package com.app.SalesInventory;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InventoryMasterSummaryActivity extends BaseActivity {

    private TextView tvTotalValue, tvTotalSold, tvTotalReceived, tvTotalAdjustments, tvTotalDeliveries;
    private Button btnExportMasterPDF;
    private RecyclerView rvCategorySummary;

    private DatabaseReference adjustmentRef;
    private ProductRepository productRepository;
    private SalesRepository salesRepository;

    private List<Product> cachedProducts = new ArrayList<>();
    private List<Sales> cachedSales = new ArrayList<>();

    private CategoryValuationAdapter categoryAdapter;
    private List<CategoryValuation> categoryList = new ArrayList<>();

    private ReportExportUtil exportUtil;
    private static final int PERMISSION_REQUEST_CODE = 300;
    private final ExecutorService exportExecutor = Executors.newSingleThreadExecutor();

    private String currentOwnerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_master_summary);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();

        initializeViews();
        loadLocalData();

        btnExportMasterPDF.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_CODE);
                    return;
                }
            }
            exportMasterReportPdf();
        });
    }

    private void initializeViews() {
        tvTotalValue = findViewById(R.id.tvTotalValue);
        tvTotalSold = findViewById(R.id.tvTotalSold);
        tvTotalReceived = findViewById(R.id.tvTotalReceived);
        tvTotalAdjustments = findViewById(R.id.tvTotalAdjustments);
        tvTotalDeliveries = findViewById(R.id.tvTotalDeliveries);
        btnExportMasterPDF = findViewById(R.id.btnExportMasterPDF);
        rvCategorySummary = findViewById(R.id.rvCategorySummary);

        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");
        exportUtil = new ReportExportUtil(this);
        productRepository = SalesInventoryApplication.getProductRepository();
        salesRepository = SalesRepository.getInstance(getApplication());

        rvCategorySummary.setLayoutManager(new LinearLayoutManager(this));
        categoryAdapter = new CategoryValuationAdapter(categoryList);
        rvCategorySummary.setAdapter(categoryAdapter);
    }

    private void loadLocalData() {
        productRepository.getAllProducts().observe(this, products -> {
            cachedProducts.clear();
            if (products != null) cachedProducts.addAll(products);
            calculateDashboardMetrics();
        });

        salesRepository.getAllSales().observe(this, sales -> {
            cachedSales.clear();
            if (sales != null) cachedSales.addAll(sales);
            calculateDashboardMetrics();
        });
    }

    // =========================================================================
    // FIX: Accurate Master Category Valuation
    // =========================================================================
    private void calculateDashboardMetrics() {
        // 1. Calculate Total Warehouse Value & Group By Category
        double totalCostValue = 0.0;
        Map<String, CategoryValuation> catMap = new HashMap<>();

        for (Product p : cachedProducts) {
            if ("Menu".equalsIgnoreCase(p.getProductType())) continue; // Skip BOM double-counting

            // FIX: Cost Price IS the total value. No multiplication needed!
            double pValue = p.getCostPrice();
            totalCostValue += pValue;

            String catName = p.getCategoryName() != null && !p.getCategoryName().isEmpty() ? p.getCategoryName() : "Uncategorized";
            CategoryValuation cv = catMap.getOrDefault(catName, new CategoryValuation(catName));
            cv.totalItems += p.getQuantity();
            cv.totalValue += pValue;
            catMap.put(catName, cv);
        }

        tvTotalValue.setText(String.format(Locale.US, "₱%,.2f", totalCostValue));

        // Update the Real-time Category List
        categoryList.clear();
        categoryList.addAll(catMap.values());
        Collections.sort(categoryList, (a, b) -> Double.compare(b.totalValue, a.totalValue)); // Highest value first
        categoryAdapter.notifyDataSetChanged();

        // 2. Calculate Total Sold & Deliveries
        int totalSold = 0;
        int deliveryCount = 0;
        for (Sales s : cachedSales) {
            totalSold += s.getQuantity();
            if (s.getDeliveryType() != null && !s.getDeliveryType().isEmpty() && !"Dine-In".equalsIgnoreCase(s.getDeliveryType()) && !"Takeout".equalsIgnoreCase(s.getDeliveryType())) {
                deliveryCount++;
            }
        }
        tvTotalSold.setText(String.valueOf(totalSold));
        tvTotalDeliveries.setText(String.valueOf(deliveryCount));

        // 3. Fetch Adjustments and Receiving
        adjustmentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot adjSnapshot) {
                int totalReceived = 0;
                int totalAdjusted = 0;

                for (DataSnapshot ads : adjSnapshot.getChildren()) {
                    String owner = ads.child("ownerAdminId").getValue(String.class);
                    if (owner != null && !owner.equals(currentOwnerId)) continue;

                    Double qtyObj = ads.child("quantityAdjusted").getValue(Double.class);
                    if (qtyObj == null) continue;

                    String type = ads.child("adjustmentType").getValue(String.class);
                    if ("Add Stock".equals(type)) {
                        totalReceived += qtyObj.intValue();
                    } else {
                        totalAdjusted += qtyObj.intValue();
                    }
                }
                tvTotalReceived.setText(String.valueOf(totalReceived));
                tvTotalAdjustments.setText(String.valueOf(totalAdjusted));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void exportMasterReportPdf() {
        Toast.makeText(this, "Generating Full Master PDF...", Toast.LENGTH_SHORT).show();

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

        adjustmentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot adjSnapshot) {
                Map<String, Integer> receivedMap = new HashMap<>();
                Map<String, Integer> adjustedMap = new HashMap<>();
                final int[] totalReceivedHolder = new int[1];
                final int[] totalAdjustedHolder = new int[1];

                for (DataSnapshot ads : adjSnapshot.getChildren()) {
                    StockAdjustment a = ads.getValue(StockAdjustment.class);
                    if (a == null || a.getProductId() == null) continue;

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
                        if ("Menu".equalsIgnoreCase(p.getProductType())) continue;

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

                    String fileName = exportUtil.generateFileName("Inventory_Master_Report", ReportExportUtil.EXPORT_PDF);
                    ReportExportUtil.ExportResult res = exportUtil.createOutputStreamForFile(fileName, ReportExportUtil.EXPORT_PDF);

                    if (res == null || res.outputStream == null) throw new Exception("Unable to create export stream");

                    exportExecutor.execute(() -> {
                        try {
                            PDFGenerator generator = new PDFGenerator(InventoryMasterSummaryActivity.this);
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
            public void onCancelled(@NonNull DatabaseError error) {
                exportUtil.showExportError("Failed to load adjustments: " + error.getMessage());
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private static class CategoryValuation {
        String categoryName;
        double totalItems = 0;
        double totalValue = 0.0;

        CategoryValuation(String categoryName) {
            this.categoryName = categoryName;
        }
    }

    private class CategoryValuationAdapter extends RecyclerView.Adapter<CategoryValuationAdapter.ViewHolder> {
        private List<CategoryValuation> items;

        CategoryValuationAdapter(List<CategoryValuation> items) {
            this.items = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_master_category_value, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CategoryValuation cat = items.get(position);
            holder.tvCategoryName.setText(cat.categoryName);

            String countStr = (cat.totalItems % 1 == 0) ? String.valueOf((long)cat.totalItems) : String.format(Locale.US, "%.2f", cat.totalItems);
            holder.tvItemCount.setText(countStr + " units in stock");

            holder.tvCategoryValue.setText(String.format(Locale.US, "₱%,.2f", cat.totalValue));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvCategoryName, tvItemCount, tvCategoryValue;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvCategoryName = itemView.findViewById(R.id.tvCategoryName);
                tvItemCount = itemView.findViewById(R.id.tvItemCount);
                tvCategoryValue = itemView.findViewById(R.id.tvCategoryValue);
            }
        }
    }
}