package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

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
import java.util.Date;
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
    private ImageButton btnSettings;
    private ImageButton btnProfile;
    private SwipeRefreshLayout swipeRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializeUI();
        setupViewModel();
        setupCharts();
        setupClickListeners();
        loadDashboardData();
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
    }

    private void setupViewModel() {
        viewModel = new ViewModelProvider(this).get(DashboardViewModel.class);
        viewModel.getDashboardMetrics().observe(this, metrics -> {
            if (metrics != null) {
                updateStatisticsCards(metrics);
            }
            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
        });
        viewModel.getRecentActivities().observe(this, activities -> {
            if (activities != null) {
                activityAdapter.setActivities(activities);
            }
        });
        viewModel.isLoading().observe(this, isLoading -> {
            progressBar.setVisibility(isLoading != null && isLoading ? View.VISIBLE : View.GONE);
        });
        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
                if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
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
        if (btnSettings != null)
            btnSettings.setOnClickListener(v ->
                    startActivity(new Intent(this, SettingsActivity.class)));

        if (btnProfile != null)
            btnProfile.setOnClickListener(v ->
                    startActivity(new Intent(this, Profile.class)));

        if (btnCreateSale != null)
            btnCreateSale.setOnClickListener(v ->
                    startActivity(new Intent(this, SellList.class)));

        if (btnAddProduct != null)
            btnAddProduct.setOnClickListener(v ->
                    startActivity(new Intent(this, AddProductActivity.class)));

        if (btnCreatePO != null)
            btnCreatePO.setOnClickListener(v ->
                    startActivity(new Intent(this, PurchaseOrderListActivity.class)));

        if (btnViewReports != null)
            btnViewReports.setOnClickListener(v ->
                    startActivity(new Intent(this, Reports.class)));

        if (btnInventory != null)
            btnInventory.setOnClickListener(v ->
                    startActivity(new Intent(this, Inventory.class)));

        if (btnCustomers != null)
            btnCustomers.setOnClickListener(v ->
                    startActivity(new Intent(MainActivity.this, CategoryManagementActivity.class)));

        if (cardTotalSales != null)
            cardTotalSales.setOnClickListener(v ->
                    Toast.makeText(MainActivity.this, "Total Sales Today", Toast.LENGTH_SHORT).show());

        if (cardInventoryValue != null)
            cardInventoryValue.setOnClickListener(v ->
                    Toast.makeText(MainActivity.this, "Inventory Value", Toast.LENGTH_SHORT).show());

        if (cardLowStock != null)
            cardLowStock.setOnClickListener(v ->
                    Toast.makeText(MainActivity.this, "Low Stock Items", Toast.LENGTH_SHORT).show());

        if (cardPendingOrders != null)
            cardPendingOrders.setOnClickListener(v ->
                    Toast.makeText(MainActivity.this, "Pending Orders", Toast.LENGTH_SHORT).show());

        if (cardRevenue != null)
            cardRevenue.setOnClickListener(v ->
                    Toast.makeText(MainActivity.this, "Revenue Report", Toast.LENGTH_SHORT).show());

        if (btnManageUsers != null)
            btnManageUsers.setOnClickListener(v -> {
                authManager.isCurrentUserAdminAsync(new AuthManager.SimpleCallback() {
                    @Override
                    public void onComplete(boolean success) {
                        runOnUiThread(() -> {
                            if (success) {
                                startActivity(new Intent(MainActivity.this, AdminManageUsersActivity.class));
                            } else {
                                Toast.makeText(MainActivity.this, "Admin access required", Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });
            });

        if (swipeRefresh != null)
            swipeRefresh.setOnRefreshListener(this::loadDashboardData);
    }

    private void loadDashboardData() {
        if (swipeRefresh != null && !swipeRefresh.isRefreshing())
            swipeRefresh.setRefreshing(true);
        viewModel.loadDashboardData();
        viewModel.loadRecentActivities();
        viewModel.loadChartData(salesTrendChart, topProductsChart, inventoryStatusChart);
    }

    private void updateStatisticsCards(DashboardMetrics metrics) {
        if (metrics == null) return;
        tvTotalSales.setText(String.format(Locale.getDefault(), "₱%.2f", metrics.getTotalSalesToday()));
        tvInventoryValue.setText(String.format(Locale.getDefault(), "₱%.2f", metrics.getTotalInventoryValue()));
        tvLowStockCount.setText(String.valueOf(metrics.getLowStockCount()));
        tvPendingOrdersCount.setText(String.valueOf(metrics.getPendingOrdersCount()));
        tvRevenue.setText(String.format(Locale.getDefault(), "₱%.2f", metrics.getRevenue()));
        updateCardColor(cardLowStock, metrics.getLowStockCount() > 0);
        updateCardColor(cardPendingOrders, metrics.getPendingOrdersCount() > 0);
        updateLastUpdatedTime();
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

    @Override
    protected void onResume() {
        super.onResume();
        loadDashboardData();
    }
}