package com.app.SalesInventory;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.pm.PackageManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Reports extends BaseActivity {

    private static final int REQUEST_WRITE_STORAGE = 1001;

    private TextView totalProductsTV;
    private TextView lowStockTV;
    private TextView totalRevenueTV;
    private TextView totalSalesTV;
    private Button btnSalesReport;
    private Button btnInventoryReport;
    private Button btnInventoryMovements;
    private Button btnComprehensiveReports;
    private Button btnOverallPdf;
    private ListView reportsListView;

    private ProductRepository productRepository;
    private SalesRepository salesRepository;
    private InventoryMovementsRepository movementsRepository;
    private AuthManager authManager;

    private List<Product> productList = new ArrayList<>();
    private List<Sales> salesList = new ArrayList<>();
    private List<InventoryMovement> movementsList = new ArrayList<>();

    private List<ReportItem> reportItems;
    private ReportAdapter adapter;

    private List<SalesJournalEntry> journalEntries;
    private SalesJournalAdapter journalAdapter;

    private File lastGeneratedFile;
    private String lastGeneratedMimeType;

    private ActivityResultLauncher<Intent> saveFileLauncher;

    private CSVGenerator csvGenerator;
    private ReportExportUtil exportUtil;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);
        productRepository = SalesInventoryApplication.getProductRepository();
        salesRepository = SalesInventoryApplication.getSalesRepository();
        movementsRepository = InventoryMovementsRepository.getInstance(getApplication());
        authManager = AuthManager.getInstance();
        csvGenerator = new CSVGenerator();
        exportUtil = new ReportExportUtil(this);
        initializeUI();
        setupSaveLauncher();
        setupListeners();
        loadData();
        journalEntries = new ArrayList<>();
        journalAdapter = new SalesJournalAdapter(this, journalEntries);
        reportsListView.setAdapter(journalAdapter);
        SalesRealtimeSyncManager.getInstance(getApplication()).getJournalLiveData().observe(this, journalList -> {
            journalEntries.clear();
            if (journalList != null) journalEntries.addAll(journalList);
            journalAdapter.notifyDataSetChanged();
        });
        movementsRepository.getAllMovements().observe(this, movements -> {
            movementsList.clear();
            if (movements != null) movementsList.addAll(movements);
        });
    }

    private void initializeUI() {
        totalSalesTV = findViewById(R.id.TotalSalesTV);
        totalProductsTV = findViewById(R.id.totalProductsTV);
        lowStockTV = findViewById(R.id.lowStockTV);
        totalRevenueTV = findViewById(R.id.totalRevenueTV);
        btnSalesReport = findViewById(R.id.BtnSalesReport);
        btnInventoryReport = findViewById(R.id.BtnInventoryReport);
        btnInventoryMovements = findViewById(R.id.BtnInventoryMovements);
        btnComprehensiveReports = findViewById(R.id.BtnComprehensiveReports);
        btnOverallPdf = findViewById(R.id.BtnOverallPdf);
        reportsListView = findViewById(R.id.ReportsListView);
        reportItems = new ArrayList<>();
        adapter = new ReportAdapter(this, reportItems);
    }

    private void setupSaveLauncher() {
        saveFileLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null && lastGeneratedFile != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            copyFileToUri(lastGeneratedFile, uri);
                        }
                    }
                }
        );
    }

    private void setupListeners() {
        btnSalesReport.setOnClickListener(v -> reportsListView.setAdapter(journalAdapter));
        btnInventoryReport.setOnClickListener(v -> startActivity(new Intent(Reports.this, InventoryReportsActivity.class)));
        btnInventoryMovements.setOnClickListener(v -> startActivity(new Intent(Reports.this, InventoryMovementsActivity.class)));
        btnComprehensiveReports.setOnClickListener(v -> {
            if (ensureWritePermission()) {
                exportComprehensiveReports();
            }
        });
        btnOverallPdf.setOnClickListener(v -> {
            if (ensureWritePermission()) {
                exportOverallSummaryPdf();
            }
        });
    }

    private void loadData() {
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                productList = products;
                updateProductStats();
            }
        });
        salesRepository.getAllSales().observe(this, sales -> {
            if (sales != null) {
                salesList = sales;
                updateSalesStats();
            }
        });
    }

    private void updateProductStats() {
        if (productList == null) return;
        int total = 0;
        int lowStock = 0;
        for (Product p : productList) {
            if (p == null) continue;
            if (!p.isActive()) continue;
            String type = p.getProductType() == null ? "" : p.getProductType();
            if ("Menu".equalsIgnoreCase(type)) continue;
            total++;
            if (p.isLowStock() || p.isCriticalStock()) lowStock++;
        }
        totalProductsTV.setText(String.valueOf(total));
        lowStockTV.setText(String.valueOf(lowStock));
    }

    private void updateSalesStats() {
        if (salesList == null) return;
        int count = salesList.size();
        double revenue = 0;
        for (Sales s : salesList) revenue += s.getTotalPrice();
        totalSalesTV.setText(String.valueOf(count));
        totalRevenueTV.setText(String.format(Locale.getDefault(), "₱%.2f", revenue));
    }

    private void exportComprehensiveReports() {
        File exportDir = exportUtil.getExportDirectory();
        if (exportDir == null) {
            exportUtil.showExportError("Unable to access export directory");
            return;
        }
        try {
            String csvNameInventory = exportUtil.generateFileName("InventoryReport", ReportExportUtil.EXPORT_CSV);
            File invFile = new File(exportDir, csvNameInventory);
            writeInventoryCsv(invFile);
            lastGeneratedFile = invFile;
            lastGeneratedMimeType = "text/csv";
            promptUserToSaveFile(invFile.getName(), "text/csv");
        } catch (Exception e) {
            exportUtil.showExportError("Inventory export failed: " + e.getMessage());
        }
        try {
            String csvNameSales = exportUtil.generateFileName("SalesReport", ReportExportUtil.EXPORT_CSV);
            File salesFile = new File(exportDir, csvNameSales);
            writeSalesCsv(salesFile);
            lastGeneratedFile = salesFile;
            lastGeneratedMimeType = "text/csv";
            promptUserToSaveFile(salesFile.getName(), "text/csv");
        } catch (Exception e) {
            exportUtil.showExportError("Sales export failed: " + e.getMessage());
        }
        try {
            String csvNameProductJournal = exportUtil.generateFileName("SalesJournal_ProductReport", ReportExportUtil.EXPORT_CSV);
            File journalFile = new File(exportDir, csvNameProductJournal);
            writeSalesProductJournalCsv(journalFile);
            lastGeneratedFile = journalFile;
            lastGeneratedMimeType = "text/csv";
            promptUserToSaveFile(journalFile.getName(), "text/csv");
        } catch (Exception e) {
            exportUtil.showExportError("Sales product journal export failed: " + e.getMessage());
        }
        try {
            String csvNameDailyJournal = exportUtil.generateFileName("SalesJournal_DailyReport", ReportExportUtil.EXPORT_CSV);
            File dailyFile = new File(exportDir, csvNameDailyJournal);
            writeSalesJournalCsv(dailyFile);
            lastGeneratedFile = dailyFile;
            lastGeneratedMimeType = "text/csv";
            promptUserToSaveFile(dailyFile.getName(), "text/csv");
        } catch (Exception e) {
            exportUtil.showExportError("Sales daily journal export failed: " + e.getMessage());
        }
        try {
            String csvNameMovements = exportUtil.generateFileName("InventoryMovements", ReportExportUtil.EXPORT_CSV);
            File movFile = new File(exportDir, csvNameMovements);
            csvGenerator.generateInventoryMovementsCSV(movFile, movementsList);
            lastGeneratedFile = movFile;
            lastGeneratedMimeType = "text/csv";
            promptUserToSaveFile(movFile.getName(), "text/csv");
        } catch (Exception e) {
            exportUtil.showExportError("Inventory movements export failed: " + e.getMessage());
        }
        try {
            String csvNameSalesChart = exportUtil.generateFileName("SalesOverTime_ChartData", ReportExportUtil.EXPORT_CSV);
            File chartCsv = new File(exportDir, csvNameSalesChart);
            writeSalesChartDataCsv(chartCsv);
            lastGeneratedFile = chartCsv;
            lastGeneratedMimeType = "text/csv";
            promptUserToSaveFile(chartCsv.getName(), "text/csv");
        } catch (Exception e) {
            exportUtil.showExportError("Chart CSV export failed: " + e.getMessage());
        }
    }

    private void exportOverallSummaryPdf() {
        File exportDir = exportUtil.getExportDirectory();
        if (exportDir == null) {
            exportUtil.showExportError("Unable to access export directory");
            return;
        }
        try {
            String pdfName = exportUtil.generateFileName("OverallReports", ReportExportUtil.EXPORT_PDF);
            File pdfFile = new File(exportDir, pdfName);

            int totalProducts = 0;
            int lowOrCritical = 0;
            double inventoryValue = 0;
            if (productList != null) {
                for (Product p : productList) {
                    if (p == null || !p.isActive()) continue;
                    String type = p.getProductType() == null ? "" : p.getProductType();
                    if ("Menu".equalsIgnoreCase(type)) continue;
                    totalProducts++;
                    double unitMarket = InventoryValuationUtil.applyLCMWithDefaults(p.getSellingPrice(), p.getSellingPrice());
                    inventoryValue += p.getQuantity() * unitMarket;
                    if (p.isLowStock() || p.isCriticalStock()) lowOrCritical++;
                }
            }

            int totalTransactions = 0;
            int deliveryCount = 0;
            double totalSalesAmount = 0;
            double deliverySalesAmount = 0;
            if (salesList != null) {
                for (Sales s : salesList) {
                    totalTransactions++;
                    totalSalesAmount += s.getTotalPrice();
                    if ("DELIVERY".equalsIgnoreCase(s.getDeliveryType())) {
                        deliveryCount++;
                        deliverySalesAmount += s.getTotalPrice();
                    }
                }
            }

            List<Bitmap> charts = ChartRenderer.renderCharts(this, salesList, productList);

            PDFGenerator generator = new PDFGenerator(this);
            generator.generateOverallSummaryReportPDF(
                    pdfFile,
                    totalProducts,
                    lowOrCritical,
                    inventoryValue,
                    totalTransactions,
                    totalSalesAmount,
                    deliveryCount,
                    deliverySalesAmount,
                    charts
            );

            lastGeneratedFile = pdfFile;
            lastGeneratedMimeType = "application/pdf";
            promptUserToSaveFile(pdfFile.getName(), "application/pdf");
        } catch (Exception e) {
            exportUtil.showExportError("Overall PDF export failed: " + e.getMessage());
        }
    }

    public void writeSalesJournalCsv(File file) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            writer.append("Date,Transaction Count,Net Sales,Delivery Count,Delivery Net Sales\n");
            if (salesList != null && !salesList.isEmpty()) {
                SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Map<String, DailySummary> dailyMap = new HashMap<>();
                for (Sales s : salesList) {
                    long ts = s.getDate() > 0 ? s.getDate() : s.getTimestamp();
                    if (ts <= 0) continue;
                    String dayKey = dayFormat.format(new Date(ts));
                    DailySummary ds = dailyMap.get(dayKey);
                    if (ds == null) {
                        ds = new DailySummary(dayKey);
                        dailyMap.put(dayKey, ds);
                    }
                    ds.transactionCount++;
                    ds.netSales += s.getTotalPrice();
                    if ("DELIVERY".equalsIgnoreCase(s.getDeliveryType())) {
                        ds.deliveryCount++;
                        ds.deliverySales += s.getTotalPrice();
                    }
                }
                List<String> keys = new ArrayList<>(dailyMap.keySet());
                Collections.sort(keys);
                for (String day : keys) {
                    DailySummary ds = dailyMap.get(day);
                    if (ds == null) continue;
                    writer.append(String.format(Locale.getDefault(), "\"%s\",%d,%.2f,%d,%.2f\n",
                            day, ds.transactionCount, ds.netSales, ds.deliveryCount, ds.deliverySales));
                }
            }
            writer.flush();
        } finally {
            if (writer != null) writer.close();
        }
    }

    public void writeSalesChartDataCsv(File file) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            writer.append("Date,SalesAmount\n");
            if (salesList != null && !salesList.isEmpty()) {
                Map<String, Double> map = new HashMap<>();
                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                for (Sales s : salesList) {
                    long ts = s.getDate() > 0 ? s.getDate() : s.getTimestamp();
                    if (ts <= 0) continue;
                    String key = fmt.format(new Date(ts));
                    double v = map.containsKey(key) ? map.get(key) : 0;
                    v += s.getTotalPrice();
                    map.put(key, v);
                }
                List<String> keys = new ArrayList<>(map.keySet());
                Collections.sort(keys);
                for (String k : keys) {
                    writer.append(String.format(Locale.getDefault(), "\"%s\",%.2f\n", k, map.get(k)));
                }
            }
            writer.flush();
        } finally {
            if (writer != null) writer.close();
        }
    }

    private void writeInventoryCsv(File file) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            writer.append("Product Name,Category,Quantity,Floor Level,Selling Price,Total Value\n");
            if (productList != null) {
                for (Product p : productList) {
                    String name = sanitizeForCsv(p.getProductName());
                    String category = sanitizeForCsv(p.getCategoryName() != null ? p.getCategoryName() : "");
                    int qty = p.getQuantity();
                    int floor = p.getFloorLevel();
                    double price = p.getSellingPrice();
                    double unitMarket = InventoryValuationUtil.applyLCMWithDefaults(price, price);
                    double total = qty * unitMarket;
                    writer.append(String.format(Locale.getDefault(), "\"%s\",\"%s\",%d,%d,%.2f,%.2f\n", name, category, qty, floor, price, total));
                }
            }
            writer.flush();
        } finally {
            if (writer != null) writer.close();
        }
    }

    private void writeSalesCsv(File file) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            writer.append("Date,Product Name,Quantity,Total Price,Order Type,Delivery Name,Delivery Phone,Delivery Address,Delivery Payment\n");
            if (salesList != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                for (Sales s : salesList) {
                    String dateStr = sdf.format(new Date(s.getDate() > 0 ? s.getDate() : s.getTimestamp()));
                    String name = sanitizeForCsv(s.getProductName());
                    int qty = s.getQuantity();
                    double total = s.getTotalPrice();
                    String orderType = s.getDeliveryType() == null ? "" : s.getDeliveryType();
                    String dName = sanitizeForCsv(s.getDeliveryName());
                    String dPhone = sanitizeForCsv(s.getDeliveryPhone());
                    String dAddress = sanitizeForCsv(s.getDeliveryAddress());
                    String dPayment = sanitizeForCsv(s.getDeliveryPaymentMethod());
                    writer.append(String.format(Locale.getDefault(), "\"%s\",\"%s\",%d,%.2f,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\"\n",
                            dateStr, name, qty, total, orderType, dName, dPhone, dAddress, dPayment));
                }
            }
            writer.flush();
        } finally {
            if (writer != null) writer.close();
        }
    }

    private void writeSalesProductJournalCsv(File file) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            writer.append("Product ID,Product Name,Total Quantity,Total Amount,Last Sale Date\n");
            if (journalEntries != null && !journalEntries.isEmpty()) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                for (SalesJournalEntry e : journalEntries) {
                    String pid = sanitizeForCsv(e.getProductId());
                    String pname = sanitizeForCsv(e.getProductName());
                    int qty = e.getTotalQuantity();
                    double amt = e.getTotalAmount();
                    String dateStr = e.getLastSaleTimestamp() > 0 ? sdf.format(new Date(e.getLastSaleTimestamp())) : "";
                    writer.append(String.format(Locale.getDefault(), "\"%s\",\"%s\",%d,%.2f,\"%s\"\n", pid, pname, qty, amt, dateStr));
                }
            }
            writer.flush();
        } finally {
            if (writer != null) writer.close();
        }
    }

    private String sanitizeForCsv(String input) {
        if (input == null) return "";
        return input.replace("\"", "\"\"");
    }

    private boolean ensureWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return true;
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                exportComprehensiveReports();
            } else {
                Toast.makeText(this, "Storage permission is required to export reports", Toast.LENGTH_LONG).show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void promptUserToSaveFile(String suggestedName, String mimeType) {
        if (lastGeneratedFile == null) return;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_TITLE, suggestedName);
        saveFileLauncher.launch(intent);
    }

    private void copyFileToUri(File source, Uri destUri) {
        try {
            FileInputStream in = new FileInputStream(source);
            OutputStream out = getContentResolver().openOutputStream(destUri);
            if (out == null) {
                exportUtil.showExportError("Unable to open destination");
                in.close();
                return;
            }
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            out.flush();
            in.close();
            out.close();
            exportUtil.showExportSuccess(destUri.getPath());
        } catch (Exception e) {
            exportUtil.showExportError("Save failed: " + e.getMessage());
        }
    }

    private static class ReportItem {
        String name;
        String date;
        String quantity;
        String amount;
        public ReportItem(String name, String date, String quantity, String amount) {
            this.name = name;
            this.date = date;
            this.quantity = quantity;
            this.amount = amount;
        }
    }

    private static class DailySummary {
        String dateKey;
        int transactionCount;
        double netSales;
        int deliveryCount;
        double deliverySales;
        DailySummary(String dateKey) {
            this.dateKey = dateKey;
            this.transactionCount = 0;
            this.netSales = 0;
            this.deliveryCount = 0;
            this.deliverySales = 0;
        }
    }

    private class ReportAdapter extends android.widget.BaseAdapter {
        private android.content.Context context;
        private List<ReportItem> items;
        public ReportAdapter(android.content.Context context, List<ReportItem> items) {
            this.context = context;
            this.items = items;
        }
        @Override
        public int getCount() { return items.size(); }
        @Override
        public Object getItem(int position) { return items.get(position); }
        @Override
        public long getItemId(int position) { return position; }
        @Override
        public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(context).inflate(R.layout.item_report_row, parent, false);
            }
            TextView rowName = convertView.findViewById(R.id.RowName);
            TextView rowDate = convertView.findViewById(R.id.RowDate);
            TextView rowQty = convertView.findViewById(R.id.RowQty);
            TextView rowTotal = convertView.findViewById(R.id.RowTotal);
            ReportItem item = items.get(position);
            rowName.setText(item.name);
            rowDate.setText(item.date);
            rowQty.setText(item.quantity);
            rowTotal.setText(item.amount);
            return convertView;
        }
    }
}