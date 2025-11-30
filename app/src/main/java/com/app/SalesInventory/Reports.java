package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
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
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Reports extends BaseActivity  {

    private static final int REQUEST_WRITE_STORAGE = 1001;

    private TextView totalProductsTV, lowStockTV, totalRevenueTV, totalSalesTV;
    private Button btnSalesReport, btnInventoryReport, btnComprehensiveReports;
    private ListView reportsListView;

    private ProductRepository productRepository;
    private SalesRepository salesRepository;

    private List<Product> productList = new ArrayList<>();
    private List<Sales> salesList = new ArrayList<>();
    private ReportAdapter adapter;
    private List<ReportItem> reportItems;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);
        productRepository = SalesInventoryApplication.getProductRepository();
        salesRepository = SalesInventoryApplication.getSalesRepository();
        initializeUI();
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
        reportsListView = findViewById(R.id.ReportsListView);
        reportItems = new ArrayList<>();
        adapter = new ReportAdapter(this, reportItems);
        reportsListView.setAdapter(adapter);
    }

    private void setupListeners() {
        btnSalesReport.setOnClickListener(v -> showSalesReport());
        btnInventoryReport.setOnClickListener(v -> showInventoryReport());
        btnComprehensiveReports.setOnClickListener(v -> {
            if (ensureWritePermission()) {
                exportComprehensiveReports();
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
            if (p.isLowStock()) lowStock++;
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
        if (salesList != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            for (Sales s : salesList) {
                String dateStr = sdf.format(new Date(s.getDate()));
                reportItems.add(new ReportItem(s.getProductName(), dateStr, "x " + s.getQuantity(), String.format(Locale.getDefault(), "₱%.2f", s.getTotalPrice())));
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
                reportItems.add(new ReportItem(p.getProductName(), dateStr, "Stock: " + p.getQuantity(), String.format(Locale.getDefault(), "₱%.2f", totalValue)));
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
            shareFile(invFile, "text/csv");
        } catch (Exception e) {
            new ReportExportUtil(this).showExportError("Inventory export failed: " + e.getMessage());
        }
        try {
            String csvNameSales = exportUtil.generateFileName("SalesReport", ReportExportUtil.EXPORT_CSV);
            File salesFile = new File(exportDir, csvNameSales);
            writeSalesCsv(salesFile);
            shareFile(salesFile, "text/csv");
        } catch (Exception e) {
            new ReportExportUtil(this).showExportError("Sales export failed: " + e.getMessage());
        }
    }

    private void writeInventoryCsv(File file) throws IOException {
        FileWriter writer = null;
        try {
            writer = new FileWriter(file);
            writer.append("Product Name,Category,Quantity,Selling Price,Total Value\n");
            if (productList != null) {
                for (Product p : productList) {
                    String name = sanitizeForCsv(p.getProductName());
                    String category = sanitizeForCsv(p.getCategoryName() != null ? p.getCategoryName() : "");
                    int qty = p.getQuantity();
                    double price = p.getSellingPrice();
                    double total = qty * price;
                    writer.append(String.format(Locale.getDefault(), "\"%s\",\"%s\",%d,%.2f,%.2f\n", name, category, qty, price, total));
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
            writer.append("Date,Product Name,Quantity,Total Price\n");
            if (salesList != null) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
                for (Sales s : salesList) {
                    String dateStr = sdf.format(new Date(s.getDate()));
                    String name = sanitizeForCsv(s.getProductName());
                    int qty = s.getQuantity();
                    double total = s.getTotalPrice();
                    writer.append(String.format(Locale.getDefault(), "\"%s\",\"%s\",%d,%.2f\n", dateStr, name, qty, total));
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