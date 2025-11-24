package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class Reports extends AppCompatActivity {

    private TextView totalProductsTV, lowStockTV, totalRevenueTV, totalSalesTV;
    private Button btnSalesReport, btnInventoryReport, btnComprehensiveReports;
    private ListView reportsListView;

    private ProductRepository productRepository;
    private SalesRepository salesRepository;

    private List<Product> productList;
    private List<Sales> salesList;
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
        btnComprehensiveReports.setOnClickListener(v -> Toast.makeText(this, "Comprehensive Report Generated", Toast.LENGTH_SHORT).show());
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
            if (p.isLowStock()) {
                lowStock++;
            }
        }

        totalProductsTV.setText(String.valueOf(total));
        lowStockTV.setText(String.valueOf(lowStock));
    }

    private void updateSalesStats() {
        if (salesList == null) return;

        int count = salesList.size();
        double revenue = 0;

        for (Sales s : salesList) {
            revenue += s.getTotalPrice();
        }

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

    private class ReportAdapter extends BaseAdapter {
        private Context context;
        private List<ReportItem> items;

        public ReportAdapter(Context context, List<ReportItem> items) {
            this.context = context;
            this.items = items;
        }

        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
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