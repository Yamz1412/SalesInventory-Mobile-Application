package com.app.SalesInventory;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.Spinner;
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
import java.util.Random;
import java.util.Set;

public class Reports extends BaseActivity {

    private StringBuilder detailedRefundsStr = new StringBuilder();
    private static final int REQUEST_WRITE_STORAGE = 1001;
    private static final String TAG = "ReportsActivity";

    private SwipeRefreshLayout swipeRefreshLayout;

    private Spinner spinnerDateFilter;
    private LinearLayout layoutCustomDate;
    private Button btnStartDate, btnEndDate, btnExportPDF, btnDetailedReport, btnInventoryReport, btnPOReport, btnOperatingExpenses;
    private ListView reportsListView;
    private TextView tvOverviewBestSeller, tvOverviewPayment, tvOverviewTransaction;

    // Income Statement UI
    private TextView tvISBusinessName, tvISDateRange;
    private TextView tvISGrossSales, tvISDiscounts, tvISNetSales;
    private TextView tvISCogs, tvISGrossProfit, tvISOpex, tvISNetIncome, tvNoData;
    private LinearLayout containerISExpenses;

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
    private double currentTaxAmount = 0.0;

    // Operating Expenses Tracking
    private Map<String, Double> databaseExpenses = new HashMap<>(); // Saved in DB
    private Map<String, Double> temporarySessionExpenses = new HashMap<>();
    private Map<String, Double> currentMergedExpenses = new HashMap<>();
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


    // --- NEW: Real-Time Cache Variables ---
    private List<DataSnapshot> cachedOpex = new ArrayList<>();
    private List<StockAdjustment> cachedAdjustments = new ArrayList<>();
    private List<DataSnapshot> cachedPOs = new ArrayList<>();
    private List<DataSnapshot> cachedReturns = new ArrayList<>();

    private ValueEventListener opexListener, adjListener, poListener, returnsListener;
    private String currentUserName = "Authorized User";
    private String currentUserRole = "Staff";

    private double savedTaxRate = 0.0;
    private String savedTaxType = "Inclusive";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Sales Reports");
            getSupportActionBar().setSubtitle("Comprehensive overview of gross revenue, discounts, and net sales");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();
        String uid = AuthManager.getInstance().getCurrentUserId();
        if (uid != null && !uid.isEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(uid).get().addOnSuccessListener(doc -> {
                if (doc.exists()) {
                    if (doc.getString("name") != null) currentUserName = doc.getString("name");
                    if (doc.getString("role") != null) currentUserRole = doc.getString("role");
                }
            });
        }

        tvNoData = findViewById(R.id.tvNoData);
        if (tvNoData != null) {
            tvNoData.setText("No sales recorded for this period. Completed transactions and daily revenue will appear here.");
        }

        productRepository = SalesInventoryApplication.getProductRepository();
        salesRepository = SalesRepository.getInstance(getApplication());

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        spinnerDateFilter = findViewById(R.id.spinnerDateFilter);
        layoutCustomDate = findViewById(R.id.layoutCustomDate);
        btnStartDate = findViewById(R.id.btnStartDate);
        btnEndDate = findViewById(R.id.btnEndDate);
        btnExportPDF = findViewById(R.id.btnExportPDF);
        btnDetailedReport = findViewById(R.id.btnDetailedReport);
        btnInventoryReport = findViewById(R.id.btnInventoryReport);
        btnPOReport = findViewById(R.id.btnPOReport);
        btnOperatingExpenses = findViewById(R.id.btnOperatingExpenses);
        ListView listView = findViewById(R.id.ReportsListView);
        TextView tvNoData = findViewById(R.id.tvNoData);
        listView.setEmptyView(tvNoData);

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
        containerISExpenses = findViewById(R.id.containerISExpenses);
        tvISOpex         = findViewById(R.id.tvISOpex);
        tvISNetIncome    = findViewById(R.id.tvISNetIncome);

        adapter = new ReportAdapter(this, allReportItems);
        listView.setAdapter(adapter);

        setupDateFilterSpinner();

        loadLocalData();
        fetchBusinessProfile();

        swipeRefreshLayout.setOnRefreshListener(() -> {
            salesRepository.reloadAllSales();
            fetchBusinessProfile();
            calculateMetricsAndList();
        });

        btnStartDate.setOnClickListener(v -> showDatePicker(true));
        btnEndDate.setOnClickListener(v -> showDatePicker(false));
        btnDetailedReport.setOnClickListener(v -> showDetailedOperationsDialog());
        btnPOReport.setOnClickListener(v -> showPOReportDialog());
        btnInventoryReport.setOnClickListener(v -> startActivity(new Intent(Reports.this, InventoryReportsActivity.class)));

        btnOperatingExpenses.setOnClickListener(v -> showOperatingExpensesDialog());
        btnOperatingExpenses.setOnLongClickListener(v -> {
            resetOperatingExpensesDatabase();
            return true;
        });

        btnExportPDF.setOnClickListener(v -> { if (ensureWritePermission()) showExportOptionsDialog(); });

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

        AuthManager.getInstance().refreshCurrentUserStatus(success -> {
            runOnUiThread(() -> {
                if (!AuthManager.getInstance().hasManagerAccess()) {
                    // Hide ALL 5 action buttons from Staff accounts
                    if (btnOperatingExpenses != null) btnOperatingExpenses.setVisibility(View.GONE);
                    if (btnExportPDF != null) btnExportPDF.setVisibility(View.GONE);
                    if (btnDetailedReport != null) btnDetailedReport.setVisibility(View.GONE);
                    if (btnInventoryReport != null) btnInventoryReport.setVisibility(View.GONE);
                    if (btnPOReport != null) btnPOReport.setVisibility(View.GONE);

                    // Hide the entire Income Statement Card safely
                    if (tvISBusinessName != null && tvISBusinessName.getParent() != null) {
                        View parent = (View) tvISBusinessName.getParent();
                        parent.setVisibility(View.GONE);

                        // If it's wrapped in a CardView layout, hide that too
                        if (parent.getParent() instanceof androidx.cardview.widget.CardView) {
                            ((View) parent.getParent()).setVisibility(View.GONE);
                        } else if (parent.getParent() instanceof View) {
                            ((View) parent.getParent()).setVisibility(View.GONE);
                        }
                    }
                }
            });
        });
    }

    private ArrayAdapter<String> getAdaptiveAdapter(String[] items) {
        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ((TextView) view).setTextColor(textColor);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                ((TextView) view).setTextColor(textColor);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void setupDateFilterSpinner() {
        String[] options = {
                "Today", "Yesterday", "This Week", "Last Week",
                "This Month", "Last Month", "This Year", "Last Year to Now", "Custom Range"
        };

        ArrayAdapter<String> spinAdapter = getAdaptiveAdapter(options);
        spinnerDateFilter.setAdapter(spinAdapter);

        spinnerDateFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selection = options[position];

                if (selection.equals("Custom Range")) {
                    layoutCustomDate.setVisibility(View.VISIBLE);
                    return;
                } else {
                    layoutCustomDate.setVisibility(View.GONE);
                }

                Calendar now = Calendar.getInstance();
                startCalendar.setTime(now.getTime());
                endCalendar.setTime(now.getTime());

                endCalendar.set(Calendar.HOUR_OF_DAY, 23); endCalendar.set(Calendar.MINUTE, 59); endCalendar.set(Calendar.SECOND, 59);
                startCalendar.set(Calendar.HOUR_OF_DAY, 0); startCalendar.set(Calendar.MINUTE, 0); startCalendar.set(Calendar.SECOND, 0);

                switch (selection) {
                    case "Today": break;
                    case "Yesterday":
                        startCalendar.add(Calendar.DAY_OF_MONTH, -1);
                        endCalendar.add(Calendar.DAY_OF_MONTH, -1);
                        break;
                    case "This Week":
                        startCalendar.set(Calendar.DAY_OF_WEEK, startCalendar.getFirstDayOfWeek());
                        break;
                    case "Last Week":
                        startCalendar.add(Calendar.WEEK_OF_YEAR, -1);
                        startCalendar.set(Calendar.DAY_OF_WEEK, startCalendar.getFirstDayOfWeek());
                        endCalendar.add(Calendar.WEEK_OF_YEAR, -1);
                        endCalendar.set(Calendar.DAY_OF_WEEK, endCalendar.getFirstDayOfWeek());
                        endCalendar.add(Calendar.DAY_OF_MONTH, 6);
                        break;
                    case "This Month":
                        startCalendar.set(Calendar.DAY_OF_MONTH, 1);
                        break;
                    case "Last Month":
                        startCalendar.add(Calendar.MONTH, -1);
                        startCalendar.set(Calendar.DAY_OF_MONTH, 1);
                        endCalendar.add(Calendar.MONTH, -1);
                        endCalendar.set(Calendar.DAY_OF_MONTH, endCalendar.getActualMaximum(Calendar.DAY_OF_MONTH));
                        break;
                    case "This Year":
                        startCalendar.set(Calendar.DAY_OF_YEAR, 1);
                        break;
                    case "Last Year to Now":
                        startCalendar.add(Calendar.YEAR, -1);
                        startCalendar.set(Calendar.DAY_OF_YEAR, 1);
                        break;
                }
                updateDateButtons();
                calculateMetricsAndList();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
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
                calculateMetricsAndList();
            }
        });

        // 2. Listen to Sales
        salesRepository.getAllSales().observe(this, sales -> {
            if (sales != null) {
                allSalesList = new ArrayList<>(sales);
                injectAdviserMockData();
                calculateMetricsAndList();
            }
        });

        // 3. NEW: Listen to Operating Expenses in Real-Time
        if (currentOwnerId != null && !currentOwnerId.isEmpty()) {
            DatabaseReference opexRef = FirebaseDatabase.getInstance().getReference("OperatingExpenses").child(currentOwnerId);
            opexListener = opexRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    cachedOpex.clear();
                    for (DataSnapshot ds : snapshot.getChildren()) cachedOpex.add(ds);
                    calculateMetricsAndList();
                }
                @Override public void onCancelled(@NonNull DatabaseError error) {}
            });
        }

        // 4. NEW: Listen to Stock Adjustments in Real-Time
        DatabaseReference adjRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");
        adjListener = adjRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cachedAdjustments.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockAdjustment adj = ds.getValue(StockAdjustment.class);
                    String owner = ds.child("ownerAdminId").getValue(String.class);
                    if (adj != null && (currentOwnerId.equals(owner) || currentOwnerId.equals(adj.getOwnerAdminId()))) {
                        cachedAdjustments.add(adj);
                    }
                }
                calculateMetricsAndList();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        // 5. NEW: Listen to Purchase Orders in Real-Time
        DatabaseReference poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");
        poListener = poRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cachedPOs.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    PurchaseOrder po = ds.getValue(PurchaseOrder.class);
                    if (po != null && currentOwnerId.equals(po.getOwnerAdminId())) {
                        cachedPOs.add(ds); // Store snapshot to ensure we can pull all fields
                    }
                }
                calculateMetricsAndList();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        // 6. NEW: Listen to Returns in Real-Time
        DatabaseReference returnsRef = FirebaseDatabase.getInstance().getReference("Returns");
        returnsListener = returnsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                cachedReturns.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String owner = ds.child("ownerAdminId").getValue(String.class);
                    if (currentOwnerId.equals(owner)) {
                        cachedReturns.add(ds);
                    }
                }
                calculateMetricsAndList();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void injectAdviserMockData() {
        boolean exists = false;
        for (Sales s : allSalesList) {
            if (s.getOrderId() != null && s.getOrderId().startsWith("MOCK-")) {
                exists = true;
                break;
            }
        }
        if (exists) return; // Prevent duplicate injection

        long now = System.currentTimeMillis();
        long oneDay = 24L * 60 * 60 * 1000L;
        long oneMonth = 30L * oneDay;

        long twoMonthsAgo = now - (2 * oneMonth);
        Sales mistake = new Sales();
        mistake.setOrderId("MOCK-ERR-99991");
        mistake.setProductName("Caramel Macchiato (MISTAKE)");
        mistake.setQuantity(1);
        mistake.setTotalPrice(5000.0);
        mistake.setTotalCost(45.0);
        mistake.setPaymentMethod("Cash (CASHIER TYPO - 5000 instead of 150)");
        mistake.setTimestamp(twoMonthsAgo);

        Sales refund = new Sales();
        refund.setOrderId("MOCK-REF-99991");
        refund.setProductName("Caramel Macchiato (REFUND)");
        refund.setQuantity(-1);
        refund.setTotalPrice(-4850.0);
        refund.setTotalCost(-45.0);
        refund.setPaymentMethod("Refund (Manager Override)");
        refund.setTimestamp(twoMonthsAgo + 300000);

        allSalesList.add(mistake);
        allSalesList.add(refund);

        long fifteenDaysAgo = now - (15 * oneDay);
        Sales bulkOrder = new Sales();
        bulkOrder.setOrderId("MOCK-VIP-1001");
        bulkOrder.setProductName("Iced Americano (Bulk Corporate)");
        bulkOrder.setQuantity(50);
        bulkOrder.setTotalPrice(6500.0);
        bulkOrder.setTotalCost(2000.0);
        bulkOrder.setDiscountAmount(1000.0);
        bulkOrder.setPaymentMethod("GCash (Corporate Account)");
        bulkOrder.setTimestamp(fifteenDaysAgo);
        allSalesList.add(bulkOrder);

        String[] mockProducts = {"Iced Latte", "Matcha Frappe", "Blueberry Cheesecake", "Espresso", "Mocha", "Strawberry Smoothie"};
        double[] mockPrices = {140.0, 160.0, 180.0, 110.0, 150.0, 155.0};
        double[] mockCosts = {45.0, 55.0, 60.0, 30.0, 50.0, 50.0};
        String[] mockPayments = {"Cash", "GCash", "Cash", "GCash", "PayMaya"};

        Random rand = new Random();

        for (int i = 0; i < 80; i++) {
            int pIndex = rand.nextInt(mockProducts.length);
            int qty = rand.nextInt(3) + 1;
            int daysAgo = rand.nextInt(365);
            long randomTime = now - (daysAgo * oneDay) - (rand.nextInt(24) * 60 * 60 * 1000L);

            Sales randSale = new Sales();
            randSale.setOrderId("MOCK-RND-" + i);
            randSale.setProductName(mockProducts[pIndex]);
            randSale.setQuantity(qty);
            randSale.setTotalPrice(mockPrices[pIndex] * qty);
            randSale.setTotalCost(mockCosts[pIndex] * qty);
            randSale.setPaymentMethod(mockPayments[rand.nextInt(mockPayments.length)]);
            randSale.setTimestamp(randomTime);

            if (rand.nextInt(10) == 0) {
                double originalPrice = mockPrices[pIndex] * qty;
                randSale.setDiscountAmount(originalPrice * 0.10);
                randSale.setTotalPrice(originalPrice * 0.90);
                randSale.setPaymentMethod(randSale.getPaymentMethod() + " (Discounted)");
            }

            allSalesList.add(randSale);
        }
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
        detailedRefundsStr.setLength(0);

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

                String status = s.getStatus() != null ? s.getStatus().toUpperCase() : "";
                String paymentTypeForCheck = s.getPaymentMethod() != null ? s.getPaymentMethod().toUpperCase() : "";
                if (status.equals("REFUNDED") || status.equals("VOIDED") || paymentTypeForCheck.contains("REFUNDED")) {
                    String rName = s.getProductName() != null ? s.getProductName() : "Unknown Product";
                    detailedRefundsStr.append("• ").append(rName)
                            .append("\n  Status: ").append(status.isEmpty() ? "REFUNDED" : status)
                            .append("\n  Amount: ₱").append(String.format(Locale.US, "%,.2f", Math.abs(s.getTotalPrice())))
                            .append("\n  Date: ").append(timeFormat.format(new Date(ts))).append("\n\n");
                    continue;
                }

                String orderId = s.getOrderId();
                if (orderId != null && !orderId.isEmpty()) uniqueOrderIds.add(orderId);
                else fallbackTxCount++;

                double netPrice = s.getTotalPrice();
                double cost = s.getTotalCost();

                // 1. Calculate missing cost for products from inventory
                if (cost <= 0 && s.getQuantity() > 0 && !s.getOrderId().startsWith("MOCK-")) {
                    String rawName = s.getProductName() != null ? s.getProductName() : "Unknown Product";
                    String baseName = rawName;
                    int bracketOrParen = rawName.length();
                    int parenIdx2 = rawName.indexOf(" (");
                    int bracketIdx2 = rawName.indexOf(" [");
                    if (parenIdx2 != -1) bracketOrParen = Math.min(bracketOrParen, parenIdx2);
                    if (bracketIdx2 != -1) bracketOrParen = Math.min(bracketOrParen, bracketIdx2);
                    if (bracketOrParen != rawName.length()) { baseName = rawName.substring(0, bracketOrParen).trim(); }

                    for (Product p : currentInventory) {
                        if (p.getProductName() != null && p.getProductName().equalsIgnoreCase(baseName)) {
                            if ("Menu".equalsIgnoreCase(p.getProductType()) && p.getBomList() != null && !p.getBomList().isEmpty()) {
                                double recipeCost = 0.0;
                                for (Map<String, Object> bomItem : p.getBomList()) {
                                    String materialName = (String) bomItem.get("materialName");
                                    double qtyUsed = 0.0;
                                    try {
                                        qtyUsed = Double.parseDouble(String.valueOf(bomItem.get("quantity")));
                                    } catch (Exception ignored) {}

                                    for (Product raw : currentInventory) {
                                        if (materialName != null && materialName.equalsIgnoreCase(raw.getProductName())) {
                                            double rawUnitCost = raw.getCostPrice();
                                            recipeCost += (rawUnitCost * qtyUsed);
                                            break;
                                        }
                                    }
                                }
                                cost = recipeCost * s.getQuantity();
                            } else {
                                double pUnitCost = p.getCostPrice();
                                cost = pUnitCost * s.getQuantity();
                            }
                            break;
                        }
                    }
                }

                // 2. Establish variables securely
                double discount = s.getDiscountAmount();
                String paymentType = s.getPaymentMethod() != null ? s.getPaymentMethod() : "Unknown";
                if (paymentType.toLowerCase().contains("cash")) { cashCount++; currentCashSales += netPrice; }
                else if (paymentType.toLowerCase().contains("gcash")) { gcashCount++; currentGcashSales += netPrice; }

                currentNetSales += netPrice;
                currentTotalCOGS += cost;
                currentTotalDiscounts += discount;

                // 3. Format Strings for PDF
                String rawName = s.getProductName() != null ? s.getProductName() : "Unknown Product";
                String baseName = rawName;
                String options = "";
                int bracketOrParen = rawName.length();
                int parenIdx2 = rawName.indexOf(" (");
                int bracketIdx2 = rawName.indexOf(" [");
                if (parenIdx2 != -1) bracketOrParen = Math.min(bracketOrParen, parenIdx2);
                if (bracketIdx2 != -1) bracketOrParen = Math.min(bracketOrParen, bracketIdx2);
                if (bracketOrParen != rawName.length()) { baseName = rawName.substring(0, bracketOrParen).trim(); options = rawName.substring(bracketOrParen).trim(); }

                String displayName = baseName;
                String details = paymentType;
                if (!options.isEmpty()) details += "\n" + options;

                // FIX: Use extraDetails to securely grab the Promo Information without crashing
                if (s.getExtraDetails() != null && !s.getExtraDetails().isEmpty()) {
                    details += "\n" + s.getExtraDetails();
                }

                String dateStr = timeFormat.format(new Date(ts));

                ReportItem ri = new ReportItem(displayName, dateStr, String.valueOf(s.getQuantity()), String.format(Locale.US, "₱%.2f", netPrice), details, discount);
                if (netPrice < 0) ri.isRefund = true;
                allReportItems.add(ri);

                // 4. Update Best Sellers
                if (!bsMap.containsKey(displayName)) bsMap.put(displayName, new BestSellerItem(displayName));
                BestSellerItem bsItem = bsMap.get(displayName);
                bsItem.quantitySold += s.getQuantity(); bsItem.totalRevenue += netPrice; bsItem.totalCost += cost;
            }
            if (detailedRefundsStr.length() == 0) detailedRefundsStr.append("No refunds recorded.\n");
        }

        bestSellersList.addAll(bsMap.values());
        Collections.sort(bestSellersList, (a, b) -> Integer.compare(b.quantitySold, a.quantitySold));
        Collections.sort(allReportItems, (a, b) -> b.date.compareTo(a.date));

        generateDetailedStrings();

        if (!bestSellersList.isEmpty()) tvOverviewBestSeller.setText(bestSellersList.get(0).productName + "\n(" + bestSellersList.get(0).quantitySold + " sold)");
        else tvOverviewBestSeller.setText("-");
        tvOverviewPayment.setText(gcashCount > cashCount ? "GCash" : (cashCount == 0 ? "-" : "Cash"));
        currentTransactionCount = uniqueOrderIds.isEmpty() ? fallbackTxCount : (uniqueOrderIds.size() + fallbackTxCount);
        if (tvOverviewTransaction != null) tvOverviewTransaction.setText(String.valueOf(currentTransactionCount));

        currentGrossSales = currentNetSales + currentTotalDiscounts;
        currentGrossProfit = currentNetSales - currentTotalCOGS;

        tvISGrossSales.setText(String.format(Locale.US, "₱ %,.2f", currentGrossSales));
        tvISDiscounts.setText(String.format(Locale.US, "%,.2f", currentTotalDiscounts));
        tvISNetSales.setText(String.format(Locale.US, "%,.2f", currentNetSales));
        tvISCogs.setText(String.format(Locale.US, "%,.2f", currentTotalCOGS));
        tvISGrossProfit.setText(String.format(Locale.US, "%,.2f", currentGrossProfit));

        adapter.notifyDataSetChanged();

        // Process the live-cached data natively
        processOpex(startMillis, endMillis);
        processOperations(startMillis, endMillis);
        processPODetails(startMillis, endMillis);

        if (swipeRefreshLayout != null) swipeRefreshLayout.setRefreshing(false);
    }

    private void processOpex(long startMillis, long endMillis) {
        databaseExpenses.clear();
        for (DataSnapshot ds : cachedOpex) {
            Long dateLogged = ds.child("dateLogged").getValue(Long.class);
            if (dateLogged != null && dateLogged >= startMillis && dateLogged <= endMillis) {
                DataSnapshot itemsSnapshot = ds.child("items");
                for (DataSnapshot itemDs : itemsSnapshot.getChildren()) {
                    String expName = itemDs.getKey();
                    Object val = itemDs.getValue();
                    if (val != null && expName != null) {
                        try {
                            double amt = Double.parseDouble(val.toString());
                            databaseExpenses.put(expName, databaseExpenses.getOrDefault(expName, 0.0) + amt);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
        }
        mergeAndRenderOperatingExpenses();
    }

    private void processOperations(long startMillis, long endMillis) {
        detailedDamagesStr.setLength(0);
        int countDamages = 0;
        for (StockAdjustment adj : cachedAdjustments) {
            long ts = adj.getTimestamp();
            if (ts >= startMillis && ts <= endMillis) {
                if ("Damage".equalsIgnoreCase(adj.getReason()) || "Loss".equalsIgnoreCase(adj.getReason())) {
                    countDamages++;
                    detailedDamagesStr.append("• ").append(adj.getProductName())
                            .append(" (").append(adj.getAdjustmentType()).append(" ")
                            .append(Math.abs(adj.getQuantityAdjusted())).append(")\n  Reason: ")
                            .append(adj.getReason()).append("\n\n");
                }
            }
        }
        if (countDamages == 0) detailedDamagesStr.append("No damages recorded.\n");

        detailedPOsStr.setLength(0);
        int countPOs = 0;
        for (DataSnapshot ds : cachedPOs) {
            PurchaseOrder po = ds.getValue(PurchaseOrder.class);
            if (po == null) continue;
            long ts = po.getOrderDate();
            if (ts >= startMillis && ts <= endMillis) {
                if (PurchaseOrder.STATUS_PARTIAL.equalsIgnoreCase(po.getStatus())) {
                    countPOs++;
                    detailedPOsStr.append("• PO: ").append(po.getPoNumber())
                            .append("\n  Supplier: ").append(po.getSupplierName())
                            .append("\n  Status: Incomplete/Partial\n\n");
                }
            }
        }
        if (countPOs == 0) detailedPOsStr.append("No partial deliveries found.\n");
    }

    private void processPODetails(long startMillis, long endMillis) {
        detailedPOFullStr.setLength(0);
        totalPOSpent = 0.0;
        totalPOCount = 0;
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

        for (DataSnapshot ds : cachedPOs) {
            PurchaseOrder po = ds.getValue(PurchaseOrder.class);
            if (po == null) continue;
            long ts = po.getOrderDate();
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
        if (totalPOCount == 0) detailedPOFullStr.append("No Purchase Orders found in this period.\n");

        detailedReturnsFullStr.setLength(0);
        totalReturnsCount = 0;
        SimpleDateFormat sdfReturns = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

        for (DataSnapshot ds : cachedReturns) {
            Long date = ds.child("date").getValue(Long.class);
            if (date != null && date >= startMillis && date <= endMillis) {
                totalReturnsCount++;
                String supplier = ds.child("supplierName").getValue(String.class);
                String reason = ds.child("reason").getValue(String.class);

                detailedReturnsFullStr.append("• Return on ").append(sdfReturns.format(new Date(date)))
                        .append("\n  Supplier: ").append(supplier)
                        .append("\n  Reason: ").append(reason)
                        .append("\n  Items Returned: \n");

                Iterable<DataSnapshot> items = ds.child("items").getChildren();
                for (DataSnapshot item : items) {
                    String pName = item.child("productName").getValue(String.class);
                    Long qty = item.child("returnQty").getValue(Long.class);
                    String unit = item.child("unit").getValue(String.class);
                    detailedReturnsFullStr.append("    - ").append(pName).append(" (").append(qty).append(" ").append(unit).append(")\n");
                }
                detailedReturnsFullStr.append("\n");
            }
        }
        if (totalReturnsCount == 0) detailedReturnsFullStr.append("No Supplier Returns found in this period.\n");
    }

    private void mergeAndRenderOperatingExpenses() {
        if (containerISExpenses == null) return;
        containerISExpenses.removeAllViews();
        Map<String, Double> mergedExpenses = new HashMap<>();

        for (Map.Entry<String, Double> entry : databaseExpenses.entrySet()) {
            mergedExpenses.put(entry.getKey(), mergedExpenses.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
        }
        for (Map.Entry<String, Double> entry : temporarySessionExpenses.entrySet()) {
            mergedExpenses.put(entry.getKey(), mergedExpenses.getOrDefault(entry.getKey(), 0.0) + entry.getValue());
        }

        currentMergedExpenses.clear();
        currentMergedExpenses.putAll(mergedExpenses);

        currentTotalOpex = 0.0;
        for (Map.Entry<String, Double> entry : mergedExpenses.entrySet()) {
            currentTotalOpex += entry.getValue();
            addExpenseRowToIncomeStatement(entry.getKey(), entry.getValue());
        }

        FirebaseDatabase.getInstance().getReference("SystemSettings").child(currentOwnerId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        double taxAmount = 0.0;
                        boolean taxEnabled = snapshot.child("taxEnabled").getValue(Boolean.class) != null ? snapshot.child("taxEnabled").getValue(Boolean.class) : false;

                        if (taxEnabled) {
                            double taxRate = snapshot.child("taxRate").getValue(Double.class) != null ? snapshot.child("taxRate").getValue(Double.class) : 0.0;
                            String taxType = snapshot.child("taxType").getValue(String.class) != null ? snapshot.child("taxType").getValue(String.class) : "Inclusive";

                            if (taxRate > 0) {
                                if (taxType.equals("Inclusive")) {
                                    double vatableSales = currentNetSales / (1 + (taxRate / 100));
                                    taxAmount = currentNetSales - vatableSales;
                                } else {
                                    taxAmount = currentNetSales * (taxRate / 100);
                                }

                                currentTaxAmount = taxAmount;
                                addExpenseRowToIncomeStatement("Tax / VAT Payable (" + taxRate + "%)", taxAmount);
                                currentTotalOpex += taxAmount;
                            }
                        }

                        // Finalize the Income Statement Math
                        currentNetIncome = currentGrossProfit - currentTotalOpex;
                        tvISOpex.setText(String.format(Locale.US, "%,.2f", currentTotalOpex));
                        tvISNetIncome.setText(String.format(Locale.US, "₱ %,.2f", currentNetIncome));
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void generateDetailedStrings() {
        detailedProductsStr.setLength(0);
        Map<String, List<DetailedProductReport>> catGrouped = new HashMap<>();
        List<String> sortedCategories = new ArrayList<>();
        Map<String, DetailedProductReport> allProductsMap = new HashMap<>();

        // 1. CRITICAL FIX: Only loop through items that were ACTUALLY sold during this period
        for (BestSellerItem b : bestSellersList) {

            // Find the matching product in the current inventory to get its category, stock, and recipe
            Product matchedProduct = null;
            for (Product p : currentInventory) {
                if (p.getProductName() != null && p.getProductName().equalsIgnoreCase(b.productName)) {
                    matchedProduct = p;
                    break;
                }
            }

            String catName = (matchedProduct != null && matchedProduct.getCategoryName() != null && !matchedProduct.getCategoryName().isEmpty())
                    ? matchedProduct.getCategoryName() : "Uncategorized";
            double stockLeft = matchedProduct != null ? matchedProduct.getQuantity() : 0;

            DetailedProductReport dp = new DetailedProductReport(b.productName, catName, stockLeft);
            dp.quantitySold = b.quantitySold;
            dp.totalRevenue = b.totalRevenue;
            dp.totalCost = b.totalCost;

            // 2. CRITICAL FIX: Calculate and format the Recipe / Raw Materials deducted!
            if (matchedProduct != null && "Menu".equalsIgnoreCase(matchedProduct.getProductType()) && matchedProduct.getBomList() != null && !matchedProduct.getBomList().isEmpty()) {
                StringBuilder recipeStr = new StringBuilder();
                for (Map<String, Object> bomItem : matchedProduct.getBomList()) {
                    String matName = (String) bomItem.get("materialName");
                    String unit = (String) bomItem.get("unit");
                    if (unit == null) unit = "";

                    double qtyUsedPerItem = 0.0;
                    try { qtyUsedPerItem = Double.parseDouble(String.valueOf(bomItem.get("quantity"))); } catch (Exception ignored) {}

                    // Multiply the recipe requirement by the total amount sold
                    double totalMatDeducted = qtyUsedPerItem * b.quantitySold;
                    recipeStr.append("    - ").append(matName).append(": ").append(String.format(Locale.US, "%.2f", totalMatDeducted)).append(" ").append(unit).append("\n");
                }
                dp.recipeDetails = recipeStr.toString();
            }

            allProductsMap.put(b.productName, dp);
        }

        // Group the dynamically created sold products by Category
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
                detailedProductsStr.append("  Sold: ").append(dp.quantitySold).append(" | Stock Left: ").append(dp.stockLeft).append("\n");
                detailedProductsStr.append("  Revenue: ₱").append(String.format(Locale.US, "%,.2f", dp.totalRevenue)).append(" | Total Cost: ₱").append(String.format(Locale.US, "%,.2f", dp.totalCost)).append("\n");

                // Print the recipe deductions if they exist
                if (dp.recipeDetails != null && !dp.recipeDetails.isEmpty()) {
                    detailedProductsStr.append("  Recipe Ingredients Deducted:\n").append(dp.recipeDetails);
                }
                detailedProductsStr.append("\n");
            }
        }
    }

    private void addExpenseRowToIncomeStatement(String name, double amount) {
        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        RelativeLayout row = new RelativeLayout(this);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(params);
        row.setPadding(0, 4, 0, 4);

        TextView tvName = new TextView(this);
        tvName.setText("   " + name);
        tvName.setTextColor(textColor);

        TextView tvAmt = new TextView(this);
        tvAmt.setText(String.format(Locale.US, "%,.2f", amount));
        tvAmt.setTextColor(textColor);

        RelativeLayout.LayoutParams amtParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        amtParams.addRule(RelativeLayout.ALIGN_PARENT_END);
        tvAmt.setLayoutParams(amtParams);

        row.addView(tvName);
        row.addView(tvAmt);
        containerISExpenses.addView(row);
    }

    private void resetOperatingExpensesDatabase() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Operating Expenses")
                .setMessage("Are you sure you want to permanently delete all saved Operating Expenses data from the database?")
                .setPositiveButton("Reset & Clear", (dialog, which) -> {
                    if (currentOwnerId != null && !currentOwnerId.isEmpty()) {
                        FirebaseDatabase.getInstance().getReference("OperatingExpenses")
                                .child(currentOwnerId).removeValue()
                                .addOnSuccessListener(aVoid -> {
                                    Toast.makeText(Reports.this, "Operating Expenses reset!", Toast.LENGTH_SHORT).show();
                                    databaseExpenses.clear();
                                    mergeAndRenderOperatingExpenses();
                                });
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showOperatingExpensesDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_operating_expenses, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));

        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        EditText etExpenseName = dialogView.findViewById(R.id.etExpenseName);
        EditText etExpenseAmount = dialogView.findViewById(R.id.etExpenseAmount);
        Button btnAddExpense = dialogView.findViewById(R.id.btnAddExpense);
        LinearLayout containerExpenses = dialogView.findViewById(R.id.containerExpenses);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelExpenses);
        Button btnSave = dialogView.findViewById(R.id.btnSaveExpenses);

        etExpenseName.setTextColor(textColor);
        etExpenseName.setHintTextColor(Color.GRAY);
        etExpenseAmount.setTextColor(textColor);
        etExpenseAmount.setHintTextColor(Color.GRAY);

        Button btnClearDb = new Button(this);
        btnClearDb.setText("RESET ALL SAVED EXPENSES (WIPE DATA)");
        btnClearDb.setTextColor(android.graphics.Color.RED);
        btnClearDb.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        btnClearDb.setOnClickListener(v -> {
            dialog.dismiss();
            resetOperatingExpensesDatabase();
        });
        containerExpenses.addView(btnClearDb);

        for(Map.Entry<String, Double> entry : temporarySessionExpenses.entrySet()){
            addExpenseRowToUI(containerExpenses, temporarySessionExpenses, entry.getKey(), entry.getValue());
        }

        btnAddExpense.setOnClickListener(v -> {
            String name = etExpenseName.getText().toString().trim();
            String amountStr = etExpenseAmount.getText().toString().trim();
            if (name.isEmpty() || amountStr.isEmpty()) { Toast.makeText(this, "Enter name and amount", Toast.LENGTH_SHORT).show(); return; }

            try {
                double amount = Double.parseDouble(amountStr);
                temporarySessionExpenses.put(name, temporarySessionExpenses.getOrDefault(name, 0.0) + amount);
                addExpenseRowToUI(containerExpenses, temporarySessionExpenses, name, amount);
                etExpenseName.setText(""); etExpenseAmount.setText(""); etExpenseName.requestFocus();
                mergeAndRenderOperatingExpenses();
            } catch (NumberFormatException e) { Toast.makeText(this, "Invalid amount", Toast.LENGTH_SHORT).show(); }
        });

        btnCancel.setOnClickListener(v -> {
            if(!temporarySessionExpenses.isEmpty()){
                new AlertDialog.Builder(this)
                        .setTitle("Unsaved Expenses")
                        .setMessage("You added expenses to the list but did not click SAVE. These temporary items will be lost. Continue?")
                        .setPositiveButton("Discard", (di, wh) -> {
                            temporarySessionExpenses.clear();
                            mergeAndRenderOperatingExpenses();
                            dialog.dismiss();
                        })
                        .setNegativeButton("Wait, Save Them", null)
                        .show();
            } else {
                dialog.dismiss();
            }
        });

        btnSave.setOnClickListener(v -> {
            if (temporarySessionExpenses.isEmpty()) { Toast.makeText(this, "No unsaved expenses found.", Toast.LENGTH_SHORT).show(); return; }
            saveExpensesToDatabase();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void addExpenseRowToUI(LinearLayout container, Map<String, Double> pendingMap, String name, double amount) {
        View row = LayoutInflater.from(this).inflate(R.layout.item_expense_row, null);
        TextView tvName = row.findViewById(R.id.tvRowExpenseName);
        TextView tvAmount = row.findViewById(R.id.tvRowExpenseAmount);

        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;
        tvName.setTextColor(textColor);
        tvAmount.setTextColor(textColor);

        ImageButton btnDelete = row.findViewById(R.id.btnDeleteExpenseRow);
        tvName.setText(name); tvAmount.setText(String.format(Locale.US, "₱%,.2f", amount));
        btnDelete.setOnClickListener(v -> {
            container.removeView(row);
            pendingMap.remove(name);
            mergeAndRenderOperatingExpenses();
        });
        container.addView(row);
    }

    private void saveExpensesToDatabase() {
        if (currentOwnerId == null || currentOwnerId.isEmpty() || temporarySessionExpenses.isEmpty()) return;

        DatabaseReference expensesRef = FirebaseDatabase.getInstance().getReference("OperatingExpenses").child(currentOwnerId);
        String expenseId = expensesRef.push().getKey();
        if (expenseId != null) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", expenseId);
            data.put("dateLogged", System.currentTimeMillis());
            data.put("items", new HashMap<>(temporarySessionExpenses));

            expensesRef.child(expenseId).setValue(data).addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "Operating Expenses Saved to Database!", Toast.LENGTH_SHORT).show();

                temporarySessionExpenses.clear();

                calculateMetricsAndList();
            });
        }
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
        TextView tvRefunds = dialogView.findViewById(R.id.tvDetailedRefunds);
        TextView tvPOs = dialogView.findViewById(R.id.tvDetailedPOs);
        Button btnClose = dialogView.findViewById(R.id.btnCloseDetailed);

        tvProducts.setText(detailedProductsStr.length() > 0 ? detailedProductsStr.toString() : "No products sold.");
        tvDamages.setText(detailedDamagesStr.toString());
        tvRefunds.setText(detailedRefundsStr.toString());
        tvPOs.setText(detailedPOsStr.toString());

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showExportOptionsDialog() {
        String[] exportOptions = {
                "📄 Financial & Income Statement",
                "📦 Inventory Master (Stock Value)",
                "🚚 Operations, POs & Adjustments"
        };

        new AlertDialog.Builder(this)
                .setTitle("Select Report to Export")
                .setItems(exportOptions, (dialog, which) -> exportSelectedReport(which))
                .show();
    }

    private void exportSelectedReport(int reportTypeIndex) {
        try {
            ReportExportUtil exportUtil = new ReportExportUtil(this);
            File exportDir = exportUtil.getExportDirectory();
            if (exportDir == null) return;

            String prefix = "";
            if (reportTypeIndex == 0) prefix = "Financial_Statement_";
            else if (reportTypeIndex == 1) prefix = "Inventory_Master_";
            else if (reportTypeIndex == 2) prefix = "Operations_PO_";

            String pdfName = exportUtil.generateFileName(prefix, ReportExportUtil.EXPORT_PDF);
            tempPdfFile = new File(exportDir, pdfName);

            PDFGenerator generator = new PDFGenerator(this);
            String dateRange = btnStartDate.getText().toString() + " to " + btnEndDate.getText().toString();
            String preparedBy = currentUserName + " (" + currentUserRole + ")"; // FORMATS NAME AND ROLE

            if (reportTypeIndex == 0) {
                generator.generateAccountingReportPDF(
                        tempPdfFile,
                        dateRange,
                        businessName,
                        currentGrossSales,
                        currentTotalDiscounts,
                        currentNetSales,
                        currentTotalCOGS,
                        currentGrossProfit,
                        currentMergedExpenses,
                        currentTotalOpex,
                        currentTaxAmount,
                        currentNetIncome,
                        currentCashSales,
                        currentGcashSales,
                        currentTransactionCount,
                        currentInventoryValue,
                        allReportItems,
                        bestSellersList,
                        preparedBy
                );
            } else if (reportTypeIndex == 1) {
                generator.generateInventoryMasterPDF(
                        tempPdfFile, businessName, currentInventory, currentInventoryValue, preparedBy
                );
            } else if (reportTypeIndex == 2) {
                String combinedDamagesAndRefunds = detailedDamagesStr.toString()
                        + "\n\n=== REFUNDS & VOIDS ===\n\n"
                        + detailedRefundsStr.toString();

                generator.generateOperationsAndReceivingReportPDF(
                        tempPdfFile, dateRange, businessName,
                        detailedPOFullStr.toString(), detailedReturnsFullStr.toString(), combinedDamagesAndRefunds,
                        preparedBy
                );
            }

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
    protected void onDestroy() {
        super.onDestroy();
        if (opexListener != null) FirebaseDatabase.getInstance().getReference("OperatingExpenses").child(currentOwnerId).removeEventListener(opexListener);
        if (adjListener != null) FirebaseDatabase.getInstance().getReference("StockAdjustments").removeEventListener(adjListener);
        if (poListener != null) FirebaseDatabase.getInstance().getReference("PurchaseOrders").removeEventListener(poListener);
        if (returnsListener != null) FirebaseDatabase.getInstance().getReference("Returns").removeEventListener(returnsListener);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    public static class ReportItem {
        public String name, date, quantity, amount, details;
        public double discount;
        public boolean isRefund = false;
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

            if (item.isRefund) {
                rowTotal.setTextColor(Color.RED);
            } else {
                boolean isDark = ThemeManager.getInstance(getContext()).getCurrentTheme().name.equals("dark");
                rowTotal.setTextColor(isDark ? Color.WHITE : Color.BLACK);
            }

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
        public String recipeDetails = ""; // NEW: Added this to hold the recipe string!

        public DetailedProductReport(String pn, String cat, double sLeft) {
            productName = pn;
            category = cat;
            stockLeft = sLeft;
        }
    }
}