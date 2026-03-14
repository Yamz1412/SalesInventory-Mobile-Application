package com.app.SalesInventory;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class Reports extends BaseActivity {

    private static final int REQUEST_WRITE_STORAGE = 1001;

    private Button btnStartDate, btnEndDate, btnExportPDF, btnDetailedReport, btnInventoryReport, btnPOReport, btnOperatingExpenses;
    private ListView reportsListView;
    private TextView tvOverviewBestSeller, tvOverviewPayment, tvOverviewTransaction;

    // Income Statement UI
    private TextView tvISBusinessName, tvISDateRange;
    private TextView tvISGrossSales, tvISDiscounts, tvISNetSales;
    private TextView tvISCogs, tvISGrossProfit, tvISOpex, tvISNetIncome;

    private Calendar startCalendar = Calendar.getInstance();
    private Calendar endCalendar = Calendar.getInstance();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
    private SimpleDateFormat timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    private List<ReportItem> allReportItems = new ArrayList<>();
    private List<BestSellerItem> bestSellersList = new ArrayList<>();
    private ReportAdapter adapter;
    private ActivityResultLauncher<Intent> createDocumentLauncher;
    private File tempPdfFile;
    private String currentOwnerId;
    private String businessName = "Store Name";

    private double currentGrossSales = 0.0, currentTotalDiscounts = 0.0, currentNetSales = 0.0;
    private double currentTotalCOGS = 0.0, currentGrossProfit = 0.0, currentTotalOpex = 0.0, currentNetIncome = 0.0;
    private double currentCashSales = 0, currentGcashSales = 0, currentInventoryValue = 0;
    private int currentTransactionCount = 0;

    private StringBuilder detailedProductsStr = new StringBuilder();
    private StringBuilder detailedDamagesStr = new StringBuilder();
    private StringBuilder detailedPOsStr = new StringBuilder();

    private StringBuilder detailedPOFullStr = new StringBuilder();
    private StringBuilder detailedReturnsFullStr = new StringBuilder();
    private double totalPOSpent = 0.0;
    private int totalPOCount = 0, totalReturnsCount = 0;

    private ProductRepository productRepository;
    private SalesRepository salesRepository;
    private List<Product> currentInventory = new ArrayList<>();
    private List<Sales> allSalesList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }

        currentOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();
        productRepository = SalesInventoryApplication.getProductRepository();
        salesRepository = SalesRepository.getInstance(getApplication());

        btnStartDate = findViewById(R.id.btnStartDate);
        btnEndDate = findViewById(R.id.btnEndDate);
        btnExportPDF = findViewById(R.id.btnExportPDF);
        btnDetailedReport = findViewById(R.id.btnDetailedReport);
        btnInventoryReport = findViewById(R.id.btnInventoryReport);
        btnPOReport = findViewById(R.id.btnPOReport);
        btnOperatingExpenses = findViewById(R.id.btnOperatingExpenses); // NEW
        reportsListView = findViewById(R.id.ReportsListView);

        tvOverviewBestSeller = findViewById(R.id.tvOverviewBestSeller);
        tvOverviewPayment = findViewById(R.id.tvOverviewPayment);
        tvOverviewTransaction = findViewById(R.id.tvOverviewTransaction);

        tvISBusinessName = findViewById(R.id.tvISBusinessName);
        tvISDateRange    = findViewById(R.id.tvISDateRange);
        tvISGrossSales   = findViewById(R.id.tvISGrossSales);
        tvISDiscounts    = findViewById(R.id.tvISDiscounts);
        tvISNetSales     = findViewById(R.id.tvISNetSales);
        tvISCogs         = findViewById(R.id.tvISCogs);
        tvISGrossProfit  = findViewById(R.id.tvISGrossProfit);
        tvISOpex         = findViewById(R.id.tvISOpex);
        tvISNetIncome    = findViewById(R.id.tvISNetIncome);

        adapter = new ReportAdapter(this, allReportItems);
        reportsListView.setAdapter(adapter);

        startCalendar.set(Calendar.DAY_OF_MONTH, 1);
        updateDateButtons();

        loadLocalData();
        fetchBusinessProfile();

        btnStartDate.setOnClickListener(v -> showDatePicker(true));
        btnEndDate.setOnClickListener(v -> showDatePicker(false));
        btnDetailedReport.setOnClickListener(v -> showDetailedOperationsDialog());
        btnPOReport.setOnClickListener(v -> showPOReportDialog());
        btnInventoryReport.setOnClickListener(v -> startActivity(new Intent(Reports.this, InventoryReportsActivity.class)));
        btnOperatingExpenses.setOnClickListener(v -> showOperatingExpensesDialog()); // NEW

        btnExportPDF.setOnClickListener(v -> { if (ensureWritePermission()) exportAccountingPdf(); });

        createDocumentLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                try (OutputStream out = getContentResolver().openOutputStream(uri);
                     FileInputStream in = new FileInputStream(tempPdfFile)) {
                    byte[] buffer = new byte[1024];
                    int read;
                    while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                    Toast.makeText(this, "Report Saved Successfully", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Error saving: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void loadLocalData() {
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                currentInventory = products;
                currentInventoryValue = 0;
                for (Product p : products) {
                    if (!"Menu".equalsIgnoreCase(p.getProductType())) {
                        currentInventoryValue += (p.getQuantity() * p.getCostPrice());
                    }
                }
            }
        });

        salesRepository.getAllSales().observe(this, sales -> {
            if (sales != null) {
                allSalesList = sales;
                calculateMetricsAndList();
            }
        });
    }

    private void fetchBusinessProfile() {
        if (currentOwnerId != null && !currentOwnerId.isEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(currentOwnerId)
                    .get().addOnSuccessListener(doc -> {
                        if (doc.exists() && doc.getString("businessName") != null) {
                            businessName = doc.getString("businessName");
                            tvISBusinessName.setText(businessName.toUpperCase());
                        }
                    });
        }
    }

    private void showDatePicker(final boolean isStartDate) {
        Calendar cal = isStartDate ? startCalendar : endCalendar;
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            cal.set(year, month, dayOfMonth);
            if (isStartDate) { cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); }
            else { cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59); cal.set(Calendar.SECOND, 59); }
            updateDateButtons();
            calculateMetricsAndList();
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateButtons() {
        btnStartDate.setText(dateFormat.format(startCalendar.getTime()));
        btnEndDate.setText(dateFormat.format(endCalendar.getTime()));
        tvISDateRange.setText("For the Period: " + btnStartDate.getText().toString() + " to " + btnEndDate.getText().toString());
    }

    private void calculateMetricsAndList() {
        allReportItems.clear();
        bestSellersList.clear();
        detailedProductsStr.setLength(0);

        currentNetSales = 0; currentTotalCOGS = 0; currentTotalDiscounts = 0;
        currentCashSales = 0; currentGcashSales = 0;

        long startMillis = startCalendar.getTimeInMillis();
        long endMillis = endCalendar.getTimeInMillis();

        Map<String, BestSellerItem> bsMap = new HashMap<>();
        Set<String> uniqueOrderIds = new HashSet<>();
        int cashCount = 0, gcashCount = 0, fallbackTxCount = 0;

        for (Sales s : allSalesList) {
            long ts = s.getTimestamp() > 0 ? s.getTimestamp() : s.getDate();
            if (ts >= startMillis && ts <= endMillis) {
                String orderId = s.getOrderId();
                if (orderId != null && !orderId.isEmpty()) uniqueOrderIds.add(orderId);
                else fallbackTxCount++;

                double netPrice = s.getTotalPrice();
                double cost = s.getTotalCost();

                if (cost <= 0) {
                    String rawName = s.getProductName() != null ? s.getProductName() : "";
                    String baseName = rawName;

                    int parenIdx = rawName.indexOf(" (");
                    int bracketIdx = rawName.indexOf(" [");
                    int minIdx = rawName.length();

                    if (parenIdx != -1) minIdx = Math.min(minIdx, parenIdx);
                    if (bracketIdx != -1) minIdx = Math.min(minIdx, bracketIdx);

                    if (minIdx != rawName.length()) {
                        baseName = rawName.substring(0, minIdx).trim();
                    }

                    for (Product p : currentInventory) {
                        if (p.getProductName() != null && p.getProductName().equalsIgnoreCase(baseName)) {
                            cost = p.getCostPrice() * s.getQuantity();
                            break;
                        }
                    }
                }

                double discount = 0;
                try { discount = (double) s.getClass().getMethod("getDiscountAmount").invoke(s); } catch (Exception ignored) {}

                String paymentType = s.getPaymentMethod() != null ? s.getPaymentMethod() : "Unknown";
                if (paymentType.toLowerCase().contains("cash")) { cashCount++; currentCashSales += netPrice; }
                else if (paymentType.toLowerCase().contains("gcash")) { gcashCount++; currentGcashSales += netPrice; }

                currentNetSales += netPrice;
                currentTotalCOGS += cost;
                currentTotalDiscounts += discount;

                String rawName = s.getProductName() != null ? s.getProductName() : "Unknown Product";
                String baseName = rawName;
                String options = "";

                int bracketOrParen = rawName.length();
                int parenIdx = rawName.indexOf(" (");
                int bracketIdx = rawName.indexOf(" [");

                if (parenIdx != -1) bracketOrParen = Math.min(bracketOrParen, parenIdx);
                if (bracketIdx != -1) bracketOrParen = Math.min(bracketOrParen, bracketIdx);

                if (bracketOrParen != rawName.length()) {
                    baseName = rawName.substring(0, bracketOrParen).trim();
                    options = rawName.substring(bracketOrParen).trim();
                }

                String displayName = baseName;
                String details = paymentType;
                if (!options.isEmpty()) {
                    details += "\n" + options;
                }

                String dateStr = timeFormat.format(new Date(ts));
                allReportItems.add(new ReportItem(displayName, dateStr, String.valueOf(s.getQuantity()), String.format(Locale.US, "₱%.2f", netPrice), details, discount));

                if (!bsMap.containsKey(displayName)) bsMap.put(displayName, new BestSellerItem(displayName));
                BestSellerItem bsItem = bsMap.get(displayName);
                bsItem.quantitySold += s.getQuantity();
                bsItem.totalRevenue += netPrice;
                bsItem.totalCost += cost;
            }
        }

        bestSellersList.addAll(bsMap.values());
        Collections.sort(bestSellersList, (a, b) -> Integer.compare(b.quantitySold, a.quantitySold));

        detailedProductsStr.setLength(0);
        Map<String, List<DetailedProductReport>> catGrouped = new HashMap<>();
        List<String> sortedCategories = new ArrayList<>();
        Map<String, DetailedProductReport> allProductsMap = new HashMap<>();

        // 1. Register ALL newly added products and existing inventory first (even with 0 sales)
        for (Product p : currentInventory) {
            if (p != null && p.getProductName() != null && !"Menu".equals(p.getProductType())) {
                String catName = p.getCategoryName() != null && !p.getCategoryName().isEmpty() ? p.getCategoryName() : "Uncategorized";
                allProductsMap.put(p.getProductName(), new DetailedProductReport(p.getProductName(), catName, p.getQuantity()));
            }
        }

        // 2. Overlay actual sales data onto the registered inventory items
        for (BestSellerItem b : bestSellersList) {
            if (allProductsMap.containsKey(b.productName)) {
                DetailedProductReport dp = allProductsMap.get(b.productName);
                dp.quantitySold = b.quantitySold;
                dp.totalRevenue = b.totalRevenue;
                dp.totalCost = b.totalCost;
            } else {
                // If a product was sold but later deleted from inventory
                DetailedProductReport dp = new DetailedProductReport(b.productName, "Deleted Products", 0);
                dp.quantitySold = b.quantitySold;
                dp.totalRevenue = b.totalRevenue;
                dp.totalCost = b.totalCost;
                allProductsMap.put(b.productName, dp);
            }
        }

        // 3. Group them by category for the Detailed Report
        for (DetailedProductReport dp : allProductsMap.values()) {
            if (!sortedCategories.contains(dp.category)) sortedCategories.add(dp.category);
            if (!catGrouped.containsKey(dp.category)) catGrouped.put(dp.category, new ArrayList<>());
            catGrouped.get(dp.category).add(dp);
        }

        Collections.sort(sortedCategories);
        for (String cat : sortedCategories) {
            detailedProductsStr.append("----- ").append(cat.toUpperCase()).append(" -----\n\n");
            List<DetailedProductReport> catItems = catGrouped.get(cat);
            Collections.sort(catItems, (a, b) -> Integer.compare(b.quantitySold, a.quantitySold));

            for (DetailedProductReport dp : catItems) {
                detailedProductsStr.append("• ").append(dp.productName).append("\n");
                detailedProductsStr.append("  Sold: ").append(dp.quantitySold)
                        .append(" | Stock Left: ").append(dp.stockLeft).append("\n");
                detailedProductsStr.append("  Revenue: ₱").append(String.format(Locale.US, "%,.2f", dp.totalRevenue))
                        .append(" | Total Cost: ₱").append(String.format(Locale.US, "%,.2f", dp.totalCost)).append("\n\n");
            }
        }

        if (!bestSellersList.isEmpty()) tvOverviewBestSeller.setText(bestSellersList.get(0).productName + "\n(" + bestSellersList.get(0).quantitySold + " sold)");
        else tvOverviewBestSeller.setText("-");

        tvOverviewPayment.setText(gcashCount > cashCount ? "GCash" : (cashCount == 0 ? "-" : "Cash"));

        currentTransactionCount = uniqueOrderIds.isEmpty() ? fallbackTxCount : (uniqueOrderIds.size() + fallbackTxCount);
        if (tvOverviewTransaction != null) tvOverviewTransaction.setText(String.valueOf(currentTransactionCount));

        currentGrossSales = currentNetSales + currentTotalDiscounts;
        currentGrossProfit = currentNetSales - currentTotalCOGS;

        // Safety initialization before OPEX loads
        if (currentTotalOpex == 0.0) {
            currentNetIncome = currentGrossProfit;
        } else {
            currentNetIncome = currentGrossProfit - currentTotalOpex;
        }

        tvISGrossSales.setText(String.format(Locale.US, "₱ %,.2f", currentGrossSales));
        tvISDiscounts.setText(String.format(Locale.US, "%,.2f", currentTotalDiscounts));
        tvISNetSales.setText(String.format(Locale.US, "%,.2f", currentNetSales));
        tvISCogs.setText(String.format(Locale.US, "%,.2f", currentTotalCOGS));
        tvISGrossProfit.setText(String.format(Locale.US, "%,.2f", currentGrossProfit));
        tvISOpex.setText(String.format(Locale.US, "%,.2f", currentTotalOpex));
        tvISNetIncome.setText(String.format(Locale.US, "₱ %,.2f", currentNetIncome));

        adapter.notifyDataSetChanged();

        fetchOperationsData(startMillis, endMillis);
        fetchPODetailsData(startMillis, endMillis);

        fetchOperatingExpenses(startMillis, endMillis);
    }

    private void fetchOperatingExpenses(long startMillis, long endMillis) {
        if (currentOwnerId == null || currentOwnerId.isEmpty()) return;

        DatabaseReference opexRef = FirebaseDatabase.getInstance().getReference("OperatingExpenses").child(currentOwnerId);
        opexRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentTotalOpex = 0.0;

                for (DataSnapshot ds : snapshot.getChildren()) {
                    Long dateLogged = ds.child("dateLogged").getValue(Long.class);
                    // Check if the expense was logged within the selected date range
                    if (dateLogged != null && dateLogged >= startMillis && dateLogged <= endMillis) {
                        DataSnapshot itemsSnapshot = ds.child("items");
                        for (DataSnapshot itemDs : itemsSnapshot.getChildren()) {
                            Number amount = itemDs.getValue(Number.class);
                            if (amount != null) {
                                currentTotalOpex += amount.doubleValue();
                            }
                        }
                    }
                }

                // Recalculate Net Income asynchronously
                currentNetIncome = currentGrossProfit - currentTotalOpex;

                // Update UI visually
                tvISOpex.setText(String.format(Locale.US, "%,.2f", currentTotalOpex));
                tvISNetIncome.setText(String.format(Locale.US, "₱ %,.2f", currentNetIncome));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showOperatingExpensesDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_operating_expenses, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));

        EditText etExpenseName = dialogView.findViewById(R.id.etExpenseName);
        EditText etExpenseAmount = dialogView.findViewById(R.id.etExpenseAmount);
        Button btnAddExpense = dialogView.findViewById(R.id.btnAddExpense);
        LinearLayout containerExpenses = dialogView.findViewById(R.id.containerExpenses);

        Button btnCancel = dialogView.findViewById(R.id.btnCancelExpenses);
        Button btnSave = dialogView.findViewById(R.id.btnSaveExpenses);

        Map<String, Double> pendingExpenses = new HashMap<>();

        btnAddExpense.setOnClickListener(v -> {
            String name = etExpenseName.getText().toString().trim();
            String amountStr = etExpenseAmount.getText().toString().trim();

            if (name.isEmpty() || amountStr.isEmpty()) {
                Toast.makeText(this, "Please enter both name and amount", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                double amount = Double.parseDouble(amountStr);
                pendingExpenses.put(name, amount);
                addExpenseRowToUI(containerExpenses, pendingExpenses, name, amount);

                etExpenseName.setText("");
                etExpenseAmount.setText("");
                etExpenseName.requestFocus();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSave.setOnClickListener(v -> {
            if (pendingExpenses.isEmpty()) {
                Toast.makeText(this, "No expenses to save", Toast.LENGTH_SHORT).show();
                return;
            }
            saveExpensesToDatabase(pendingExpenses);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void addExpenseRowToUI(LinearLayout container, Map<String, Double> pendingMap, String name, double amount) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_expense_row, null);

        TextView tvName = row.findViewById(R.id.tvRowExpenseName);
        TextView tvAmount = row.findViewById(R.id.tvRowExpenseAmount);
        ImageButton btnDelete = row.findViewById(R.id.btnDeleteExpenseRow);

        tvName.setText(name);
        tvAmount.setText(String.format(Locale.US, "₱%,.2f", amount));

        btnDelete.setOnClickListener(v -> {
            container.removeView(row);
            pendingMap.remove(name);
        });

        container.addView(row);
    }

    private void saveExpensesToDatabase(Map<String, Double> expensesMap) {
        if (currentOwnerId == null || currentOwnerId.isEmpty()) return;

        DatabaseReference expensesRef = FirebaseDatabase.getInstance().getReference("OperatingExpenses").child(currentOwnerId);
        String expenseId = expensesRef.push().getKey();
        if (expenseId != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", expenseId);
            data.put("dateLogged", System.currentTimeMillis());
            data.put("items", expensesMap);

            expensesRef.child(expenseId).setValue(data).addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "Operating Expenses Saved!", Toast.LENGTH_SHORT).show();
                calculateMetricsAndList();
            });
        }
    }

    private void fetchOperationsData(long startMillis, long endMillis) {
        DatabaseReference adjRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");
        adjRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                detailedDamagesStr.setLength(0);
                int count = 0;
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockAdjustment adj = ds.getValue(StockAdjustment.class);
                    if (adj != null) {
                        String owner = ds.child("ownerAdminId").getValue(String.class);
                        if (currentOwnerId.equals(owner) || currentOwnerId.equals(adj.getOwnerAdminId())) {
                            long ts = adj.getTimestamp();
                            if (ts >= startMillis && ts <= endMillis) {
                                if ("Damage".equalsIgnoreCase(adj.getReason()) || "Loss".equalsIgnoreCase(adj.getReason())) {
                                    count++;
                                    detailedDamagesStr.append("• ").append(adj.getProductName())
                                            .append(" (").append(adj.getAdjustmentType()).append(" ")
                                            .append(Math.abs(adj.getQuantityAdjusted())).append(")\n  Reason: ")
                                            .append(adj.getReason()).append("\n\n");
                                }
                            }
                        }
                    }
                }
                if (count == 0) detailedDamagesStr.append("No damages recorded.\n");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        DatabaseReference poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");
        poRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                detailedPOsStr.setLength(0);
                int count = 0;
                for (DataSnapshot ds : snapshot.getChildren()) {
                    PurchaseOrder po = ds.getValue(PurchaseOrder.class);
                    if (po != null && currentOwnerId.equals(po.getOwnerAdminId())) {
                        long ts = po.getOrderDate() != null ? po.getOrderDate().getTime() : 0L;
                        if (ts >= startMillis && ts <= endMillis) {
                            if (PurchaseOrder.STATUS_PARTIAL.equalsIgnoreCase(po.getStatus())) {
                                count++;
                                detailedPOsStr.append("• PO: ").append(po.getPoNumber())
                                        .append("\n  Supplier: ").append(po.getSupplierName())
                                        .append("\n  Status: Incomplete/Partial\n\n");
                            }
                        }
                    }
                }
                if (count == 0) detailedPOsStr.append("No partial deliveries found.\n");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void fetchPODetailsData(long startMillis, long endMillis) {
        DatabaseReference poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");
        poRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                detailedPOFullStr.setLength(0);
                totalPOSpent = 0.0;
                totalPOCount = 0;
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

                for (DataSnapshot ds : snapshot.getChildren()) {
                    PurchaseOrder po = ds.getValue(PurchaseOrder.class);
                    if (po != null && currentOwnerId.equals(po.getOwnerAdminId())) {
                        long ts = po.getOrderDate() != null ? po.getOrderDate().getTime() : 0L;
                        if (ts >= startMillis && ts <= endMillis) {
                            totalPOCount++;
                            totalPOSpent += po.getTotalAmount();
                            String payment = ds.child("paymentMethod").getValue(String.class);
                            if (payment == null || payment.isEmpty()) payment = "Not Specified";

                            detailedPOFullStr.append("• ").append(po.getPoNumber())
                                    .append(" | ").append(sdf.format(new Date(ts)))
                                    .append("\n  Supplier: ").append(po.getSupplierName())
                                    .append("\n  Status: ").append(po.getStatus().toUpperCase())
                                    .append("\n  Payment: ").append(payment)
                                    .append("\n  Total Amount: ₱").append(String.format(Locale.US, "%.2f", po.getTotalAmount()))
                                    .append("\n\n");
                        }
                    }
                }
                if (totalPOCount == 0) detailedPOFullStr.append("No Purchase Orders found in this period.\n");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        DatabaseReference returnsRef = FirebaseDatabase.getInstance().getReference("Returns");
        returnsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                detailedReturnsFullStr.setLength(0);
                totalReturnsCount = 0;
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

                for (DataSnapshot ds : snapshot.getChildren()) {
                    String owner = ds.child("ownerAdminId").getValue(String.class);
                    if (currentOwnerId.equals(owner)) {
                        Long date = ds.child("date").getValue(Long.class);
                        if (date != null && date >= startMillis && date <= endMillis) {
                            totalReturnsCount++;
                            String supplier = ds.child("supplierName").getValue(String.class);
                            String reason = ds.child("reason").getValue(String.class);

                            detailedReturnsFullStr.append("• Return on ").append(sdf.format(new Date(date)))
                                    .append("\n  Supplier: ").append(supplier)
                                    .append("\n  Reason: ").append(reason)
                                    .append("\n  Items Returned: \n");

                            Iterable<DataSnapshot> items = ds.child("items").getChildren();
                            for(DataSnapshot item : items) {
                                String pName = item.child("productName").getValue(String.class);
                                Long qty = item.child("returnQty").getValue(Long.class);
                                String unit = item.child("unit").getValue(String.class);
                                detailedReturnsFullStr.append("    - ").append(pName).append(" (").append(qty).append(" ").append(unit).append(")\n");
                            }
                            detailedReturnsFullStr.append("\n");
                        }
                    }
                }
                if (totalReturnsCount == 0) detailedReturnsFullStr.append("No Supplier Returns found in this period.\n");
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showPOReportDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_po_report, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).setCancelable(true).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView tvPODateRange = dialogView.findViewById(R.id.tvPODateRange);
        TextView tvTotalPOCount = dialogView.findViewById(R.id.tvTotalPOCount);
        TextView tvTotalPOSpent = dialogView.findViewById(R.id.tvTotalPOSpent);
        TextView tvTotalReturns = dialogView.findViewById(R.id.tvTotalReturns);
        TextView tvDetailedPOs = dialogView.findViewById(R.id.tvDetailedPOs);
        TextView tvDetailedReturns = dialogView.findViewById(R.id.tvDetailedReturns);
        Button btnClose = dialogView.findViewById(R.id.btnClosePODetailed);

        tvPODateRange.setText("Filtered Period: " + btnStartDate.getText().toString() + "  to  " + btnEndDate.getText().toString());
        tvTotalPOCount.setText(String.valueOf(totalPOCount));
        tvTotalPOSpent.setText(String.format(Locale.US, "₱%.2f", totalPOSpent));
        tvTotalReturns.setText(String.valueOf(totalReturnsCount));

        tvDetailedPOs.setText(detailedPOFullStr.toString());
        tvDetailedReturns.setText(detailedReturnsFullStr.toString());

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showDetailedOperationsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_detailed_report, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).setCancelable(true).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView tvProducts = dialogView.findViewById(R.id.tvDetailedProducts);
        TextView tvDamages = dialogView.findViewById(R.id.tvDetailedDamages);
        TextView tvPOs = dialogView.findViewById(R.id.tvDetailedPOs);
        Button btnClose = dialogView.findViewById(R.id.btnCloseDetailed);

        tvProducts.setText(detailedProductsStr.length() > 0 ? detailedProductsStr.toString() : "No products sold.");
        tvDamages.setText(detailedDamagesStr.toString());
        tvPOs.setText(detailedPOsStr.toString());

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void exportAccountingPdf() {
        try {
            ReportExportUtil exportUtil = new ReportExportUtil(this);
            File exportDir = exportUtil.getExportDirectory();
            if (exportDir == null) return;
            String pdfName = exportUtil.generateFileName("Financial_Report", ReportExportUtil.EXPORT_PDF);
            tempPdfFile = new File(exportDir, pdfName);

            PDFGenerator generator = new PDFGenerator(this);
            generator.generateAccountingReportPDF(
                    tempPdfFile,
                    btnStartDate.getText().toString() + " to " + btnEndDate.getText().toString(),
                    businessName,
                    currentGrossSales, currentTotalDiscounts, currentNetSales,
                    currentTotalCOGS, currentGrossProfit, currentTotalOpex, currentNetIncome,
                    currentCashSales, currentGcashSales, currentTransactionCount, currentInventoryValue,
                    allReportItems
            );

            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("application/pdf");
            intent.putExtra(Intent.EXTRA_TITLE, pdfName);
            createDocumentLauncher.launch(intent);
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private boolean ensureWritePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) return true;
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    public static class ReportItem {
        public String name, date, quantity, amount, details;
        public double discount;
        public ReportItem(String n, String d, String q, String a, String det, double disc) {
            name = n; date = d; quantity = q; amount = a; details = det; discount = disc;
        }
    }

    public static class BestSellerItem {
        public String productName;
        public int quantitySold = 0;
        public double totalRevenue = 0.0, totalCost = 0.0;
        public BestSellerItem(String p) { productName = p; }
    }

    private class ReportAdapter extends ArrayAdapter<ReportItem> {
        private List<ReportItem> items;
        public ReportAdapter(Reports context, List<ReportItem> items) {
            super(context, 0, items); this.items = items;
        }
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_report_row, parent, false);
            TextView rowName = convertView.findViewById(R.id.RowName);
            TextView rowDate = convertView.findViewById(R.id.RowDate);
            TextView rowQty = convertView.findViewById(R.id.RowQty);
            TextView rowTotal = convertView.findViewById(R.id.RowTotal);
            TextView rowDetails = convertView.findViewById(R.id.RowDetails);
            TextView rowDiscount = convertView.findViewById(R.id.RowDiscount);

            ReportItem item = items.get(position);
            rowName.setText(item.name); rowDate.setText(item.date); rowQty.setText(item.quantity); rowTotal.setText(item.amount);

            if (item.details != null && !item.details.isEmpty()) { rowDetails.setVisibility(View.VISIBLE); rowDetails.setText(item.details); }
            else rowDetails.setVisibility(View.GONE);

            if (item.discount > 0) { rowDiscount.setVisibility(View.VISIBLE); rowDiscount.setText(String.format(Locale.US, "Discount: -₱%.2f", item.discount)); }
            else rowDiscount.setVisibility(View.GONE);
            return convertView;
        }
    }

    public static class DetailedProductReport {
        public String productName;
        public String category;
        public int quantitySold = 0;
        public double totalRevenue = 0.0, totalCost = 0.0;
        public double stockLeft = 0;
        public DetailedProductReport(String pn, String cat, double sLeft) {
            productName = pn;
            category = cat;
            stockLeft = sLeft;
        }
    }
}