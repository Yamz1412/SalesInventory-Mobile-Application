package com.app.SalesInventory;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.GridLayout;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends BaseActivity {
    private static final String TAG = "MainActivity";
    private CardView cardTotalSales;
    private CardView cardInventoryValue;
    private CardView cardLowStock;
    private CardView cardPendingOrders;
    private CardView cardRevenue;
    private TextView tvTotalSales;
    private TextView tvInventoryValue;
    private TextView tvLowStockCount;
    private TextView tvPendingOrdersCount;
    private TextView tvRevenue;
    private LineChart salesTrendChart;
    private BarChart topProductsChart;
    private PieChart inventoryStatusChart;
    private MaterialButton btnCreateSale;
    private MaterialButton btnAddProduct;
    private MaterialButton btnCreatePO;
    private MaterialButton btnViewReports;
    private MaterialButton btnInventory;
    private MaterialButton btnCustomers;
    private MaterialButton btnManageUsers;
    private RecyclerView rvRecentActivity;
    private RecentActivityAdapter activityAdapter;
    private ProgressBar progressBar;
    private TextView tvLastUpdated;
    private DashboardViewModel viewModel;
    private AuthManager authManager;
    private View btnSettings;
    private View btnProfile;
    private View btnNotifications;
    private SwipeRefreshLayout swipeRefresh;
    private String currentUserRole = "Unknown";
    private ProductRepository productRepository;
    private CardView cardNearExpiry;
    private TextView tvNearExpiryCount;
    private NotificationBadgeManager notificationBadgeManager;
    private boolean isAdminFlag = false;
    private FirebaseFirestore fStore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        productRepository = SalesInventoryApplication.getProductRepository();
        productRepository.runExpirySweep();
        initializeUI();
        setupNearExpiryCard();
        setupViewModel();
        setupCharts();
        setupClickListeners();
        fStore = FirebaseFirestore.getInstance();
        resolveUserRoleAndConfigureUI();
        loadDashboardData();
        notificationBadgeManager = new NotificationBadgeManager(this);
        notificationBadgeManager.start();
    }

    private void initializeUI() {
        swipeRefresh = findViewById(R.id.swipe_refresh);
        cardTotalSales = findViewById(R.id.card_total_sales);
        cardInventoryValue = findViewById(R.id.card_inventory_value);
        cardLowStock = findViewById(R.id.card_low_stock);
        cardPendingOrders = findViewById(R.id.card_pending_orders);
        cardRevenue = findViewById(R.id.card_revenue);
        tvTotalSales = findViewById(R.id.tv_total_sales);
        tvInventoryValue = findViewById(R.id.tv_inventory_value);
        tvLowStockCount = findViewById(R.id.tv_low_stock_count);
        tvPendingOrdersCount = findViewById(R.id.tv_pending_orders_count);
        tvRevenue = findViewById(R.id.tv_revenue);
        salesTrendChart = findViewById(R.id.chart_sales_trend);
        topProductsChart = findViewById(R.id.chart_top_products);
        inventoryStatusChart = findViewById(R.id.chart_inventory_status);
        btnCreateSale = findViewById(R.id.btn_create_sale);
        btnAddProduct = findViewById(R.id.btn_add_product);
        btnCreatePO = findViewById(R.id.btn_create_po);
        btnViewReports = findViewById(R.id.btn_view_reports);
        btnInventory = findViewById(R.id.btn_inventory);
        btnCustomers = findViewById(R.id.btn_customers);
        btnManageUsers = findViewById(R.id.btn_manage_users);
        rvRecentActivity = findViewById(R.id.rv_recent_activity);
        activityAdapter = new RecentActivityAdapter(this);
        rvRecentActivity.setAdapter(activityAdapter);
        rvRecentActivity.setLayoutManager(new LinearLayoutManager(this));
        progressBar = findViewById(R.id.progress_bar);
        tvLastUpdated = findViewById(R.id.tv_last_updated);
        authManager = AuthManager.getInstance();
        btnSettings = findViewById(R.id.btn_settings);
        btnProfile = findViewById(R.id.btn_profile);
        btnNotifications = findViewById(R.id.btn_notifications);
        cardNearExpiry = findViewById(R.id.card_near_expiry);
        tvNearExpiryCount = findViewById(R.id.tv_near_expiry_count);
    }

    private void setupNearExpiryCard() {
        if (cardNearExpiry == null || tvNearExpiryCount == null) return;
        cardNearExpiry.setVisibility(View.GONE);
        try {
            productRepository.getAllProducts().observe(this, products -> {
                int count = 0;
                if (products != null) {
                    long now = System.currentTimeMillis();
                    for (Product p : products) {
                        if (p == null || !p.isActive()) continue;
                        long expiry = p.getExpiryDate();
                        if (expiry <= 0) continue;
                        long diffMillis = expiry - now;
                        long days = diffMillis / (24L * 60L * 60L * 1000L);
                        if (diffMillis <= 0 || days <= 7) {
                            count++;
                        }
                    }
                }
                if (count > 0) {
                    tvNearExpiryCount.setText(String.valueOf(count));
                    cardNearExpiry.setVisibility(View.VISIBLE);
                } else {
                    cardNearExpiry.setVisibility(View.GONE);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "setupNearExpiryCard error", e);
        }
        cardNearExpiry.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, NearExpiryItemsActivity.class)));
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        viewModel.getDashboardMetrics().observe(this, metrics -> {
            try {
                if (metrics != null) {
                    updateStatisticsCards(metrics);
                    viewModel.loadChartData(salesTrendChart, topProductsChart, inventoryStatusChart);
                }
            } catch (Exception e) {
                Log.e(TAG, "metrics observer error", e);
            } finally {
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
            }
        });
        viewModel.getRecentActivities().observe(this, activities -> {
            try {
                if (activities != null) activityAdapter.setActivities(activities);
            } catch (Exception e) {
                Log.e(TAG, "recent activities observer error", e);
            }
        });
        viewModel.isLoading().observe(this, isLoading -> {
            try {
                progressBar.setVisibility(isLoading != null && isLoading ? View.VISIBLE : View.GONE);
            } catch (Exception e) {
                Log.e(TAG, "isLoading observer error", e);
            }
        });
        viewModel.getErrorMessage().observe(this, error -> {
            try {
                if (error != null && !error.isEmpty()) {
                    Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                    if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                    viewModel.clearErrorMessage();
                }
            } catch (Exception e) {
                Log.e(TAG, "errorMessage observer error", e);
            }
        });
    }

    private void setupCharts() {
        try {
            if (salesTrendChart != null) {
                salesTrendChart.getDescription().setEnabled(false);
                salesTrendChart.setDrawGridBackground(false);
                salesTrendChart.setBackgroundColor(getColor(R.color.dashboard_card_background));
            }
            if (topProductsChart != null) {
                topProductsChart.getDescription().setEnabled(false);
                topProductsChart.setDrawGridBackground(false);
                topProductsChart.setBackgroundColor(getColor(R.color.dashboard_card_background));
            }
            if (inventoryStatusChart != null) {
                inventoryStatusChart.getDescription().setEnabled(false);
                inventoryStatusChart.setBackgroundColor(getColor(R.color.dashboard_card_background));
            }
        } catch (Exception e) {
            Log.e(TAG, "setupCharts error", e);
        }
    }

    private void setupClickListeners() {
        try {
            if (btnSettings != null) btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
            if (btnProfile != null) btnProfile.setOnClickListener(v -> startActivity(new Intent(this, Profile.class)));
            if (btnNotifications != null) btnNotifications.setOnClickListener(this::onNotificationsClicked);
            if (btnCreateSale != null) btnCreateSale.setOnClickListener(v -> startActivity(new Intent(this, SellList.class)));
            if (btnAddProduct != null) btnAddProduct.setOnClickListener(v -> {
                if (isAdminFlag) startActivity(new Intent(this, AddProductActivity.class));
                else Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show();
            });
            if (btnCreatePO != null) btnCreatePO.setOnClickListener(v -> {
                if (isAdminFlag) startActivity(new Intent(this, PurchaseOrderListActivity.class));
                else Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show();
            });
            if (btnViewReports != null) btnViewReports.setOnClickListener(v -> startActivity(new Intent(this, Reports.class)));
            if (btnInventory != null) btnInventory.setOnClickListener(v -> {
                Intent i = new Intent(this, Inventory.class);
                i.putExtra("readonly", !isAdminFlag);
                startActivity(i);
            });
            if (btnCustomers != null) btnCustomers.setOnClickListener(v -> {
                if (isAdminFlag) startActivity(new Intent(MainActivity.this, CategoryManagementActivity.class));
                else Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show();
            });
            if (cardTotalSales != null) cardTotalSales.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Total Sales Today", Toast.LENGTH_SHORT).show());
            if (cardInventoryValue != null) cardInventoryValue.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Inventory Value", Toast.LENGTH_SHORT).show());
            if (cardLowStock != null) cardLowStock.setOnClickListener(v -> startActivity(new Intent(this, LowStockItemsActivity.class)));
            if (cardPendingOrders != null) cardPendingOrders.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Pending Orders", Toast.LENGTH_SHORT).show());
            if (cardRevenue != null) cardRevenue.setOnClickListener(v -> Toast.makeText(this, "Revenue Report", Toast.LENGTH_SHORT).show());
            if (btnManageUsers != null) btnManageUsers.setOnClickListener(v -> {
                if (isAdminFlag) authManager.isCurrentUserAdminAsync(success -> runOnUiThread(() -> {
                    if (success) startActivity(new Intent(MainActivity.this, AdminStaffList.class));
                    else Toast.makeText(MainActivity.this, "Admin access required", Toast.LENGTH_LONG).show();
                }));
                else Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show();
            });
            if (swipeRefresh != null) swipeRefresh.setOnRefreshListener(this::loadDashboardData);
        } catch (Exception e) {
            Log.e(TAG, "setupClickListeners error", e);
        }
    }

    private void resolveUserRoleAndConfigureUI() {
        authManager.getCurrentUserRoleAsync(role -> runOnUiThread(() -> {
            currentUserRole = role == null ? "Unknown" : role;
            String uid = authManager.getCurrentUserId();
            if (uid == null || uid.isEmpty()) {
                applyRoleVisibility();
                loadDashboardData();
                return;
            }
            FirebaseFirestore db = fStore != null ? fStore : FirebaseFirestore.getInstance();
            db.collection("users").document(uid).get().addOnCompleteListener(task -> {
                try {
                    if (task.isSuccessful()) {
                        DocumentSnapshot snap = task.getResult();
                        if (snap != null && snap.exists()) {
                            String ownerAdminId = null;
                            Object o = snap.get("ownerAdminId");
                            if (o instanceof String) ownerAdminId = (String) o;
                            if (ownerAdminId == null || ownerAdminId.isEmpty()) {
                                if ("Admin".equalsIgnoreCase(currentUserRole) || "admin".equalsIgnoreCase(currentUserRole)) {
                                    ownerAdminId = uid;
                                }
                            }
                            if (ownerAdminId != null && !ownerAdminId.isEmpty()) {
                                FirestoreManager.getInstance().setBusinessOwnerId(ownerAdminId);
                            }
                        } else {
                            if ("Admin".equalsIgnoreCase(currentUserRole) || "admin".equalsIgnoreCase(currentUserRole)) {
                                FirestoreManager.getInstance().setBusinessOwnerId(uid);
                            }
                        }
                    } else {
                        if ("Admin".equalsIgnoreCase(currentUserRole) || "admin".equalsIgnoreCase(currentUserRole)) {
                            FirestoreManager.getInstance().setBusinessOwnerId(uid);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to set business owner id for user", e);
                } finally {
                    applyRoleVisibility();
                    loadDashboardData();
                }
            });
        }));
    }

    private void applyRoleVisibility() {
        boolean isAdmin = "Admin".equalsIgnoreCase(currentUserRole) || "admin".equalsIgnoreCase(currentUserRole);
        isAdminFlag = isAdmin;
        try {
            if (isAdmin) {
                if (btnCreateSale != null) btnCreateSale.setVisibility(View.VISIBLE);
                if (btnAddProduct != null) btnAddProduct.setVisibility(View.VISIBLE);
                if (btnCreatePO != null) btnCreatePO.setVisibility(View.VISIBLE);
                if (btnViewReports != null) btnViewReports.setVisibility(View.VISIBLE);
                if (btnInventory != null) btnInventory.setVisibility(View.VISIBLE);
                if (btnCustomers != null) btnCustomers.setVisibility(View.VISIBLE);
                if (btnManageUsers != null) btnManageUsers.setVisibility(View.VISIBLE);
                if (cardTotalSales != null) cardTotalSales.setVisibility(View.VISIBLE);
                if (cardRevenue != null) cardRevenue.setVisibility(View.VISIBLE);
            } else {
                if (btnCreateSale != null) btnCreateSale.setVisibility(View.VISIBLE);
                if (btnAddProduct != null) btnAddProduct.setVisibility(View.GONE);
                if (btnCreatePO != null) btnCreatePO.setVisibility(View.GONE);
                if (btnViewReports != null) btnViewReports.setVisibility(View.VISIBLE);
                if (btnInventory != null) btnInventory.setVisibility(View.VISIBLE);
                if (btnCustomers != null) btnCustomers.setVisibility(View.GONE);
                if (btnManageUsers != null) btnManageUsers.setVisibility(View.GONE);
                if (cardTotalSales != null) cardTotalSales.setVisibility(View.GONE);
                if (cardRevenue != null) cardRevenue.setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(TAG, "applyRoleVisibility error", e);
        }
        arrangeQuickActions();
    }

    private void arrangeQuickActions() {
        GridLayout quickActionsGrid = findViewById(R.id.quick_actions_grid);
        if (quickActionsGrid == null) return;
        if (btnCreateSale != null) btnCreateSale.setVisibility(View.VISIBLE);
        if (btnViewReports != null) btnViewReports.setVisibility(View.VISIBLE);
        if (btnInventory != null) btnInventory.setVisibility(View.VISIBLE);
        if (isAdminFlag) {
            if (btnAddProduct != null) btnAddProduct.setVisibility(View.VISIBLE);
            if (btnCreatePO != null) btnCreatePO.setVisibility(View.VISIBLE);
            if (btnCustomers != null) btnCustomers.setVisibility(View.VISIBLE);
        } else {
            if (btnAddProduct != null) btnAddProduct.setVisibility(View.GONE);
            if (btnCreatePO != null) btnCreatePO.setVisibility(View.GONE);
            if (btnCustomers != null) btnCustomers.setVisibility(View.GONE);
        }
        quickActionsGrid.requestLayout();
    }

    private void loadDashboardData() {
        if (swipeRefresh != null && !swipeRefresh.isRefreshing()) swipeRefresh.setRefreshing(true);
        try {
            viewModel.loadDashboardData();
            viewModel.loadRecentActivities();
            viewModel.loadChartData(salesTrendChart, topProductsChart, inventoryStatusChart);
        } catch (Exception e) {
            Log.e(TAG, "loadDashboardData error", e);
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
        }
    }

    private void updateStatisticsCards(DashboardMetrics metrics) {
        if (metrics == null) return;
        try {
            if (isAdminFlag) {
                tvTotalSales.setText(formatCurrency(metrics.getTotalSalesToday()));
                tvRevenue.setText(formatCurrency(metrics.getRevenue()));
                if (cardTotalSales != null) cardTotalSales.setVisibility(View.VISIBLE);
                if (cardRevenue != null) cardRevenue.setVisibility(View.VISIBLE);
            } else {
                if (cardTotalSales != null) cardTotalSales.setVisibility(View.GONE);
                if (cardRevenue != null) cardRevenue.setVisibility(View.GONE);
            }
            tvInventoryValue.setText(formatCurrency(metrics.getTotalInventoryValue()));
            tvLowStockCount.setText(String.valueOf(metrics.getLowStockCount()));
            tvPendingOrdersCount.setText(String.valueOf(metrics.getPendingOrdersCount()));
            updateCardColor(cardLowStock, metrics.getLowStockCount() > 0);
            updateLastUpdatedTime();
        } catch (Exception e) {
            Log.e(TAG, "updateStatisticsCards error", e);
        }
    }

    private String formatCurrency(double value) {
        try {
            NumberFormat nf = NumberFormat.getNumberInstance(Locale.getDefault());
            nf.setGroupingUsed(true);
            nf.setMinimumFractionDigits(2);
            nf.setMaximumFractionDigits(2);
            return "₱" + nf.format(value);
        } catch (Exception e) {
            return String.format(Locale.getDefault(), "₱%.2f", value);
        }
    }

    private void updateCardColor(CardView card, boolean hasAlert) {
        if (card == null) return;
        if (hasAlert) {
            card.setCardBackgroundColor(getColor(R.color.warning_primary));
        } else {
            card.setCardBackgroundColor(getColor(R.color.dashboard_card_background));
        }
    }

    private void updateLastUpdatedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm aa", Locale.getDefault());
        String currentTime = sdf.format(new Date());
        if (tvLastUpdated != null) tvLastUpdated.setText("Last updated: " + currentTime);
    }

    public void onNotificationsClicked(View v) {
        AlertRepository repo = AlertRepository.getInstance((Application) getApplication());
        repo.fetchUnreadAlerts(new AlertRepository.OnAlertsFetchedListener() {
            @Override
            public void onAlertsFetched(List<Alert> alerts) {
                runOnUiThread(() -> {
                    if (alerts == null || alerts.isEmpty()) {
                        Toast.makeText(MainActivity.this, "No new notifications", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    View dlgView = LayoutInflater.from(MainActivity.this).inflate(R.layout.dialog_notification, null);
                    TextView tvTitle = dlgView.findViewById(R.id.dlg_title);
                    TextView tvTime = dlgView.findViewById(R.id.dlg_time);
                    TextView tvMessage = dlgView.findViewById(R.id.dlg_message);
                    tvTitle.setText("Notifications");
                    Alert latest = alerts.get(0);
                    String timeStr = "";
                    if (latest != null && latest.getTimestamp() > 0) {
                        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
                        timeStr = sdf.format(new Date(latest.getTimestamp()));
                    }
                    tvTime.setText(timeStr);
                    StringBuilder sb = new StringBuilder();
                    int limit = Math.min(alerts.size(), 10);
                    for (int i = 0; i < limit; i++) {
                        Alert a = alerts.get(i);
                        String text = a.getMessage() == null || a.getMessage().isEmpty() ? a.getType() : a.getMessage();
                        sb.append("• ").append(text).append("\n\n");
                    }
                    tvMessage.setText(sb.toString().trim());
                    AlertDialog.Builder b = new AlertDialog.Builder(MainActivity.this, com.google.android.material.R.style.Theme_MaterialComponents_Light_Dialog_Alert);
                    b.setView(dlgView);
                    b.setPositiveButton("View all", (dialog, which) -> {
                        try {
                            Class<?> cls2 = Class.forName("com.app.SalesInventory.AlertsActivity");
                            startActivity(new Intent(MainActivity.this, cls2));
                        } catch (Exception ex) {
                            Toast.makeText(MainActivity.this, "Cannot open alerts screen", Toast.LENGTH_SHORT).show();
                        }
                    });
                    b.setNegativeButton("Close", (dialog, which) -> dialog.dismiss());
                    b.show();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Failed to load notifications", Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        resolveUserRoleAndConfigureUI();
        loadDashboardData();
    }

    @Override
    protected void onDestroy() {
        if (notificationBadgeManager != null) {
            notificationBadgeManager.stop();
            notificationBadgeManager = null;
        }
        super.onDestroy();
    }
}