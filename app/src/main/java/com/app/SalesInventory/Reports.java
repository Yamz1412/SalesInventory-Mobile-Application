package com.app.SalesInventory;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import java.util.Locale;
import java.util.Map;

public class Reports extends BaseActivity  {

    private static final int REQUEST_WRITE_STORAGE = 1001;

    private TextView totalProductsTV, lowStockTV, totalRevenueTV, totalSalesTV;
    private Button btnSalesReport, btnInventoryReport, btnComprehensiveReports, btnSampleData, btnOverallPdf;
    private ListView reportsListView;

    private ProductRepository productRepository;
    private SalesRepository salesRepository;
    private AuthManager authManager;

    private List<Product> productList = new ArrayList<>();
    private List<Sales> salesList = new ArrayList<>();
    private ReportAdapter adapter;
    private List<ReportItem> reportItems;

    private File lastGeneratedFile;
    private String lastGeneratedMimeType;

    private ActivityResultLauncher<Intent> saveFileLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);
        productRepository = SalesInventoryApplication.getProductRepository();
        salesRepository = SalesInventoryApplication.getSalesRepository();
        authManager = AuthManager.getInstance();
        initializeUI();
        setupSaveLauncher();
        setupListeners();
        loadData();
    }

    private void initializeUI() {
        totalSalesTV = findViewById(R.id.TotalSalesTV);
        totalProductsTV = findViewById(R.id.totalProductsTV);
        lowStockTV = findViewById(R.id.lowStockTV);
        totalRevenueTV = findViewById(R.id.totalRevenueTV);
        btnSalesReport = findViewById(R.id.BtnSalesReport);
        btnInventoryReport = findViewById(R.id.BtnInventoryReport);
        btnComprehensiveReports = findViewById(R.id.BtnComprehensiveReports);
        btnSampleData = findViewById(R.id.BtnSampleData);
        btnOverallPdf = findViewById(R.id.BtnOverallPdf);
        reportsListView = findViewById(R.id.ReportsListView);
        reportItems = new ArrayList<>();
        adapter = new ReportAdapter(this, reportItems);
        reportsListView.setAdapter(adapter);
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
        btnSalesReport.setOnClickListener(v -> showSalesReport());
        btnInventoryReport.setOnClickListener(v -> startActivity(new Intent(Reports.this, InventoryReportsActivity.class)));
        btnComprehensiveReports.setOnClickListener(v -> {
            if (ensureWritePermission()) {
                exportComprehensiveReports();
            }
        });
        btnSampleData.setOnClickListener(v -> loadSampleData());
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
                showSalesReport();
            }
        });
    }

    private void updateProductStats() {
        if (productList == null) return;
        int total = productList.size();
        int lowStock = 0;
        for (Product p : productList) {
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

    private void showSalesReport() {
        reportItems.clear();
        if (salesList != null && !salesList.isEmpty()) {
            SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Map<String, DailySummary> dailyMap = new HashMap<>();
            for (Sales s : salesList) {
                long ts = s.getDate() > 0 ? s.getDate() : s.getTimestamp();
                String dayKey = dayFormat.format(new Date(ts));
                DailySummary ds = dailyMap.get(dayKey);
                if (ds == null) {
                    ds = new DailySummary(dayKey);
                    dailyMap.put(dayKey, ds);
                }
                ds.transactionCount++;
                ds.netSales += s.getTotalPrice();
                if ("DELIVERY".equals(s.getDeliveryType())) {
                    ds.deliveryCount++;
                    ds.deliverySales += s.getTotalPrice();
                }
            }
            List<String> keys = new ArrayList<>(dailyMap.keySet());
            java.util.Collections.sort(keys);
            for (String day : keys) {
                DailySummary ds = dailyMap.get(day);
                if (ds == null) continue;
                String name = "Sales Journal";
                String dateStr = day;
                String qtyStr = "Txns: " + ds.transactionCount + " | Deliveries: " + ds.deliveryCount;
                String amountStr = String.format(Locale.getDefault(), "₱%.2f", ds.netSales);
                reportItems.add(new ReportItem(name, dateStr, qtyStr, amountStr));
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void showInventoryReport() {
        reportItems.clear();
        if (productList != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            for (Product p : productList) {
                String dateStr = sdf.format(new Date(p.getDateAdded()));
                double totalValue = p.getQuantity() * p.getSellingPrice();
                String qtyStr = "Stock: " + p.getQuantity() + " | Floor: " + p.getFloorLevel();
                reportItems.add(new ReportItem(p.getProductName(), dateStr, qtyStr, String.format(Locale.getDefault(), "₱%.2f", totalValue)));
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void exportComprehensiveReports() {
        ReportExportUtil exportUtil = new ReportExportUtil(this);
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
            new ReportExportUtil(this).showExportError("Inventory export failed: " + e.getMessage());
        }
        try {
            String csvNameSales = exportUtil.generateFileName("SalesReport", ReportExportUtil.EXPORT_CSV);
            File salesFile = new File(exportDir, csvNameSales);
            writeSalesCsv(salesFile);
            lastGeneratedFile = salesFile;
            lastGeneratedMimeType = "text/csv";
            promptUserToSaveFile(salesFile.getName(), "text/csv");
        } catch (Exception e) {
            new ReportExportUtil(this).showExportError("Sales export failed: " + e.getMessage());
        }
        try {
            String csvNameJournal = exportUtil.generateFileName("SalesJournal_Report", ReportExportUtil.EXPORT_CSV);
            File journalFile = new File(exportDir, csvNameJournal);
            writeSalesJournalCsv(journalFile);
            lastGeneratedFile = journalFile;
            lastGeneratedMimeType = "text/csv";
            promptUserToSaveFile(journalFile.getName(), "text/csv");
        } catch (Exception e) {
            new ReportExportUtil(this).showExportError("Sales journal export failed: " + e.getMessage());
        }
    }

    private void exportOverallSummaryPdf() {
        try {
            ReportExportUtil exportUtil = new ReportExportUtil(this);
            File exportDir = exportUtil.getExportDirectory();
            if (exportDir == null) {
                exportUtil.showExportError("Unable to access export directory");
                return;
            }
            String pdfName = exportUtil.generateFileName("OverallReports", ReportExportUtil.EXPORT_PDF);
            File pdfFile = new File(exportDir, pdfName);

            int totalProducts = productList != null ? productList.size() : 0;
            int lowOrCritical = 0;
            double inventoryValue = 0;
            if (productList != null) {
                for (Product p : productList) {
                    if (p.isLowStock() || p.isCriticalStock()) lowOrCritical++;
                    inventoryValue += p.getQuantity() * p.getSellingPrice();
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
                    if ("DELIVERY".equals(s.getDeliveryType())) {
                        deliveryCount++;
                        deliverySalesAmount += s.getTotalPrice();
                    }
                }
            }

            PDFGenerator generator = new PDFGenerator(this);
            generator.generateOverallSummaryReportPDF(
                    pdfFile,
                    totalProducts,
                    lowOrCritical,
                    inventoryValue,
                    totalTransactions,
                    totalSalesAmount,
                    deliveryCount,
                    deliverySalesAmount
            );

            lastGeneratedFile = pdfFile;
            lastGeneratedMimeType = "application/pdf";
            promptUserToSaveFile(pdfFile.getName(), "application/pdf");
        } catch (Exception e) {
            new ReportExportUtil(this).showExportError("Overall PDF export failed: " + e.getMessage());
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
                    double total = qty * price;
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
                    String dateStr = sdf.format(new Date(s.getDate()));
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

    private void writeSalesJournalCsv(File file) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            writer.append("Date,Transaction Count,Net Sales,Delivery Count,Delivery Net Sales\n");
            if (salesList != null && !salesList.isEmpty()) {
                SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                Map<String, DailySummary> dailyMap = new HashMap<>();
                for (Sales s : salesList) {
                    long ts = s.getDate() > 0 ? s.getDate() : s.getTimestamp();
                    String dayKey = dayFormat.format(new Date(ts));
                    DailySummary ds = dailyMap.get(dayKey);
                    if (ds == null) {
                        ds = new DailySummary(dayKey);
                        dailyMap.put(dayKey, ds);
                    }
                    ds.transactionCount++;
                    ds.netSales += s.getTotalPrice();
                    if ("DELIVERY".equals(s.getDeliveryType())) {
                        ds.deliveryCount++;
                        ds.deliverySales += s.getTotalPrice();
                    }
                }
                List<String> keys = new ArrayList<>(dailyMap.keySet());
                java.util.Collections.sort(keys);
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

    private void shareFile(File file, String mimeType) {
        try {
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType(mimeType);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Share report"));
        } catch (Exception e) {
            new ReportExportUtil(this).showExportError("Share failed: " + e.getMessage());
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
                new ReportExportUtil(this).showExportError("Unable to open destination");
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
            new ReportExportUtil(this).showExportSuccess(destUri.getPath());
        } catch (Exception e) {
            new ReportExportUtil(this).showExportError("Save failed: " + e.getMessage());
        }
    }

    private void loadSampleData() {
        authManager.isCurrentUserAdminAsync(success -> runOnUiThread(() -> {
            if (!success) {
                Toast.makeText(Reports.this, "Admin access required to load sample data", Toast.LENGTH_LONG).show();
                return;
            }
            insertSampleData();
        }));
    }

    private void insertSampleData() {
        long now = System.currentTimeMillis();
        long oneDay = 24L * 60L * 60L * 1000L;

        com.google.firebase.database.DatabaseReference productRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("Product");
        com.google.firebase.database.DatabaseReference salesRef = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("Sales");

        Product coffee = new Product();
        coffee.setProductId("P_SAMPLE_1");
        coffee.setProductName("Sample Coffee");
        coffee.setCategoryName("Beverages");
        coffee.setQuantity(50);
        coffee.setSellingPrice(50);
        coffee.setCostPrice(10);
        coffee.setReorderLevel(5);
        coffee.setCeilingLevel(200);
        coffee.setCriticalLevel(5);
        coffee.setFloorLevel(2);
        coffee.setDateAdded(now);
        coffee.setActive(true);
        productRef.child(coffee.getProductId()).setValue(coffee);

        Product tea = new Product();
        tea.setProductId("P_SAMPLE_2");
        tea.setProductName("Sample Tea");
        tea.setCategoryName("Beverages");
        tea.setQuantity(30);
        tea.setSellingPrice(30);
        tea.setCostPrice(8);
        tea.setReorderLevel(4);
        tea.setCeilingLevel(100);
        tea.setCriticalLevel(4);
        tea.setFloorLevel(3);
        tea.setDateAdded(now);
        tea.setActive(true);
        productRef.child(tea.getProductId()).setValue(tea);

        Product bread = new Product();
        bread.setProductId("P_SAMPLE_3");
        bread.setProductName("Sample Bread");
        bread.setCategoryName("Bakery");
        bread.setQuantity(20);
        bread.setSellingPrice(20);
        bread.setCostPrice(7);
        bread.setReorderLevel(3);
        bread.setCeilingLevel(60);
        bread.setCriticalLevel(3);
        bread.setFloorLevel(2);
        bread.setDateAdded(now);
        bread.setActive(true);
        productRef.child(bread.getProductId()).setValue(bread);

        List<Sales> sampleSales = new ArrayList<>();
        sampleSales.add(buildSampleSale("P_SAMPLE_1", "Sample Coffee", 2, 50, now - oneDay * 2, false));
        sampleSales.add(buildSampleSale("P_SAMPLE_2", "Sample Tea", 3, 30, now - oneDay * 2, true));
        sampleSales.add(buildSampleSale("P_SAMPLE_1", "Sample Coffee", 1, 50, now - oneDay, true));
        sampleSales.add(buildSampleSale("P_SAMPLE_3", "Sample Bread", 4, 20, now - oneDay, false));
        sampleSales.add(buildSampleSale("P_SAMPLE_2", "Sample Tea", 2, 30, now, true));

        for (Sales s : sampleSales) {
            String key = salesRef.push().getKey();
            if (key != null) {
                s.setId(key);
                salesRef.child(key).setValue(s);
            }
        }

        Toast.makeText(this, "Sample data loaded", Toast.LENGTH_SHORT).show();
    }

    private Sales buildSampleSale(String productId, String productName, int qty, double price, long ts, boolean delivery) {
        Sales s = new Sales();
        String orderId = "SAMPLE_" + ts + "_" + productId;
        s.setOrderId(orderId);
        s.setProductId(productId);
        s.setProductName(productName);
        s.setQuantity(qty);
        s.setPrice(price);
        s.setTotalPrice(price * qty);
        s.setPaymentMethod("Cash");
        s.setDate(ts);
        s.setTimestamp(ts);
        if (delivery) {
            s.setDeliveryType("DELIVERY");
            s.setDeliveryStatus("PENDING");
            s.setDeliveryDate(0);
            s.setDeliveryName("Sample Customer");
            s.setDeliveryPhone("09123456789");
            s.setDeliveryAddress("Sample Address");
            s.setDeliveryPaymentMethod("COD");
        } else {
            s.setDeliveryType("WALK_IN");
            s.setDeliveryStatus("DELIVERED");
            s.setDeliveryDate(ts);
            s.setDeliveryName("");
            s.setDeliveryPhone("");
            s.setDeliveryAddress("");
            s.setDeliveryPaymentMethod("");
        }
        return s;
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