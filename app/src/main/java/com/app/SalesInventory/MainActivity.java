package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends BaseActivity {
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
    private SwipeRefreshLayout swipeRefresh;
    private String currentUserRole = "Unknown";
    private ProductRepository productRepository;
    private CardView cardNearExpiry;
    private TextView tvNearExpiryCount;
    private NotificationBadgeManager notificationBadgeManager;
    private boolean isAdminFlag = false;

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
        cardNearExpiry = findViewById(R.id.card_near_expiry);
        tvNearExpiryCount = findViewById(R.id.tv_near_expiry_count);

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::onSwipeRefresh);
        }
    }

    private void setupNearExpiryCard() {
        if (cardNearExpiry == null || tvNearExpiryCount == null) return;
        cardNearExpiry.setVisibility(View.GONE);
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
        cardNearExpiry.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, NearExpiryItemsActivity.class)));
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        viewModel.getDashboardMetrics().observe(this, metrics -> {
            if (metrics != null) {
                updateStatisticsCards(metrics);
                updateLastUpdatedTime();
            }
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
        });
        viewModel.getRecentActivities().observe(this, activities -> {
            if (activities != null) {
                activityAdapter.setActivities(activities);
            }
        });
        viewModel.isLoading().observe(this, isLoading -> progressBar.setVisibility(isLoading != null && isLoading ? View.VISIBLE : View.GONE));
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
                viewModel.clearErrorMessage();
            }
        });
    }

    private void setupCharts() {
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
    }

    private void setupClickListeners() {
        if (btnSettings != null) btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        if (btnProfile != null) btnProfile.setOnClickListener(v -> startActivity(new Intent(this, Profile.class)));
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
        if (cardLowStock != null) cardLowStock.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LowStockItemsActivity.class)));
        if (cardPendingOrders != null) cardPendingOrders.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Pending Orders", Toast.LENGTH_SHORT).show());
        if (cardRevenue != null) cardRevenue.setOnClickListener(v -> Toast.makeText(MainActivity.this, "Revenue Report", Toast.LENGTH_SHORT).show());
        if (btnManageUsers != null) btnManageUsers.setOnClickListener(v -> {
            if (isAdminFlag) authManager.isCurrentUserAdminAsync(success -> runOnUiThread(() -> {
                if (success) startActivity(new Intent(MainActivity.this, AdminStaffList.class));
                else Toast.makeText(MainActivity.this, "Admin access required", Toast.LENGTH_LONG).show();
            }));
            else Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show();
        });
        if (swipeRefresh != null) swipeRefresh.setOnRefreshListener(this::loadDashboardData);
    }

    private void resolveUserRoleAndConfigureUI() {
        String businessOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();
        String currentUserId = FirestoreManager.getInstance().getCurrentUserId();

        if (businessOwnerId != null && currentUserId != null && businessOwnerId.equals(currentUserId)) {
            isAdminFlag = true;
        }
    }

    private void applyRoleVisibility() {
        boolean isAdmin = "Admin".equalsIgnoreCase(currentUserRole) || "admin".equalsIgnoreCase(currentUserRole);
        isAdminFlag = isAdmin;
        if (isAdmin) {
            if (btnCreateSale != null) btnCreateSale.setVisibility(View.VISIBLE);
            if (btnAddProduct != null) btnAddProduct.setVisibility(View.VISIBLE);
            if (btnCreatePO != null) btnCreatePO.setVisibility(View.VISIBLE);
            if (btnViewReports != null) btnViewReports.setVisibility(View.VISIBLE);
            if (btnInventory != null) btnInventory.setVisibility(View.VISIBLE);
            if (btnCustomers != null) btnCustomers.setVisibility(View.VISIBLE);
            if (btnManageUsers != null) btnManageUsers.setVisibility(View.VISIBLE);
        } else {
            if (btnCreateSale != null) btnCreateSale.setVisibility(View.VISIBLE);
            if (btnAddProduct != null) btnAddProduct.setVisibility(View.GONE);
            if (btnCreatePO != null) btnCreatePO.setVisibility(View.GONE);
            if (btnViewReports != null) btnViewReports.setVisibility(View.VISIBLE);
            if (btnInventory != null) btnInventory.setVisibility(View.VISIBLE);
            if (btnCustomers != null) btnCustomers.setVisibility(View.GONE);
            if (btnManageUsers != null) btnManageUsers.setVisibility(View.GONE);
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
        quickActionsGrid.invalidate();
        quickActionsGrid.requestLayout();
    }

    private void loadDashboardData() {
        viewModel.loadDashboardData();
        viewModel.loadRecentActivities();
        viewModel.loadChartData(salesTrendChart, topProductsChart, inventoryStatusChart);
    }

    private void onSwipeRefresh() {
        viewModel.refreshDashboardData();
        viewModel.loadChartData(salesTrendChart, topProductsChart, inventoryStatusChart);
    }

    private void updateStatisticsCards(DashboardMetrics metrics) {
        if (tvTotalSales != null) {
            tvTotalSales.setText(String.format("₱%,.2f", metrics.getTotalSalesToday()));
        }

        if (tvRevenue != null) {
            tvRevenue.setText(String.format("₱%,.2f", metrics.getRevenue()));
        }

        if (tvLowStockCount != null && cardLowStock != null) {
            int lowCount = metrics.getLowStockCount();
            tvLowStockCount.setText(String.valueOf(lowCount));
            if (lowCount > 0) {
                cardLowStock.setVisibility(View.VISIBLE);
            } else {
                cardLowStock.setVisibility(View.VISIBLE);
            }
        }

        if (tvNearExpiryCount != null && cardNearExpiry != null) {
            int expiryCount = metrics.getNearExpiryCount();
            tvNearExpiryCount.setText(String.valueOf(expiryCount));
            if (expiryCount > 0) {
                cardNearExpiry.setVisibility(View.VISIBLE);
                //updateCardColor(cardNearExpiry, true);
            } else {
                cardNearExpiry.setVisibility(View.GONE);
            }
        }

        if (tvPendingOrdersCount != null) {
            int pendingCount = metrics.getPendingOrdersCount();
            tvPendingOrdersCount.setText(String.valueOf(pendingCount));

            if (pendingCount > 0) {
                tvPendingOrdersCount.setTextColor(getColor(R.color.info_primary));
            } else {
                tvPendingOrdersCount.setTextColor(getColor(R.color.text_primary));
            }
        }
    }

    private void updateLastUpdatedTime() {
        if (tvLastUpdated != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
            String now = sdf.format(new Date());
            tvLastUpdated.setText("Last updated: " + now);
        }
    }

    public void onNotificationsClicked(View view) {
        AlertRepository repo = AlertRepository.getInstance(getApplication());
        List<Alert> alerts = repo.getUnreadAlerts().getValue();

        if (alerts == null || alerts.isEmpty()) {
            new AlertDialog.Builder(this).setTitle("Notifications").setMessage("No new notifications").setPositiveButton("OK", null).show();
            return;
        }

        String[] items = new String[alerts.size()];
        for (int i = 0; i < alerts.size(); i++) {
            Alert a = alerts.get(i);
            items[i] = (a.getType() != null ? a.getType() : "Alert") + " - " + a.getMessage();
        }

        AlertDialog.Builder listDialog = new AlertDialog.Builder(this);
        listDialog.setTitle("Notifications");
        listDialog.setItems(items, (d, which) -> showAlertDetail(alerts.get(which)));

        listDialog.setNeutralButton("Clear All", (dialog, which) -> {
            new AlertDialog.Builder(this)
                    .setTitle("Confirm Clear")
                    .setMessage("Permanently delete all notifications?")
                    .setPositiveButton("Clear All", (d, w) -> repo.clearAllAlerts())
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        listDialog.setNegativeButton("Close", null);
        AlertDialog dialog = listDialog.create();

        dialog.getListView().setOnItemLongClickListener((parent, v, position, id) -> {
            Alert selected = alerts.get(position);
            new AlertDialog.Builder(this)
                    .setTitle("Manage Notification")
                    .setItems(new String[]{"Mark as Read", "Delete Permanently"}, (d, which) -> {
                        if (which == 0) repo.markAlertAsRead(selected.getId(), null);
                        else repo.deleteAlert(selected.getId());
                        dialog.dismiss();
                    }).show();
            return true;
        });

        dialog.show();
    }

    private void showAlertDetail(Alert alert) {
        if (alert == null) return;
        LayoutInflater li = LayoutInflater.from(this);
        View v = li.inflate(R.layout.dialog_notification, null);
        TextView tvTitle = v.findViewById(R.id.dlg_title);
        TextView tvMessage = v.findViewById(R.id.dlg_message);
        TextView tvTime = v.findViewById(R.id.dlg_time);
        String title = alert.getType() == null ? "Notification" : alert.getType();
        String message = alert.getMessage() == null ? "" : alert.getMessage();
        tvTitle.setText(title);
        tvMessage.setText(message);
        long ts = 0;
        try {
            ts = alert.getTimestamp();
        } catch (Exception e) {
            ts = 0;
        }
        if (ts > 0) {
            Date d = new Date(ts);
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm aa", Locale.getDefault());
            tvTime.setText(sdf.format(d));
        } else {
            tvTime.setText("");
        }
        AlertRepository repo = AlertRepository.getInstance(getApplication());
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setView(v);
        b.setPositiveButton("Mark as read", (dialog, which) -> repo.markAlertAsRead(alert.getId(), new AlertRepository.OnAlertUpdatedListener() {
            @Override
            public void onAlertUpdated() {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Marked as read", Toast.LENGTH_SHORT).show());
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show());
            }
        }));
        b.setNeutralButton("Open", (dialog, which) -> {
            Intent intent = new Intent(MainActivity.this, StockAlertsActivity.class);
            intent.putExtra("alertId", alert.getId());
            startActivity(intent);
        });
        b.setNegativeButton("Close", null);
        b.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resolveUserRoleAndConfigureUI();
        loadDashboardData();
        if (viewModel != null) {
            viewModel.refreshDashboardData();
        }
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