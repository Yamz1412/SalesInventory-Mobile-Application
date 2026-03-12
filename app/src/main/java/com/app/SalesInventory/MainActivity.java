package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends BaseActivity {
    // UI Components
    private CardView cardSalesAmount, cardGrossProfit, cardLowStock, cardPendingOrders, cardNearExpiry;
    private TextView tvSalesAmount, tvGrossProfit, tvLowStockCount, tvPendingOrdersCount, tvNearExpiryCount;
    private TextView tvOverviewLabel;
    private LineChart salesTrendChart;
    private BarChart topProductsChart;
    private PieChart inventoryStatusChart;
    private LinearLayout btnCreateSale, btnCreatePO, btnViewReports, btnInventory;

    private FloatingActionButton btnAddProduct;

    // FAB MENU VARIABLES
    private FloatingActionButton btnButtons, fabCashMenu, fabHistory;
    private View dimOverlay;
    private LinearLayout layoutFabMenu;
    private boolean isFabOpen = false;

    private MaterialButton btnCustomers, btnManageUsers;
    private MaterialButtonToggleGroup toggleTimeFilter;
    private RecyclerView rvRecentActivity;
    private RecentActivityAdapter activityAdapter;
    private ProgressBar progressBar;
    private TextView tvLastUpdated;
    private View btnSettings, btnProfile;
    private SwipeRefreshLayout swipeRefresh;

    // DYNAMIC BUSINESS PROFILE COMPONENTS
    private ImageView ivBusinessLogo;
    private TextView tvBusinessName;

    // Layout Containers for Animation
    private GridLayout statsGrid, quickActionsGrid;

    // Logic & Data
    private DashboardViewModel viewModel;
    private AuthManager authManager;
    private ProductRepository productRepository;
    private SalesRepository salesRepository;
    private NotificationBadgeManager notificationBadgeManager;
    private boolean isAdminFlag = false;

    // Data Cache for Filtering
    private List<Sales> cachedSalesList = new ArrayList<>();
    private List<Product> cachedProductList = new ArrayList<>(); // NEW: Needed to verify accurate costs

    private LinearLayout layoutImpersonationBanner;
    private TextView tvImpersonationText;
    private Button btnExitImpersonation;
    private boolean isImpersonating = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AuthManager.getInstance().init(getApplication());

        productRepository = SalesInventoryApplication.getProductRepository();
        salesRepository = SalesRepository.getInstance(getApplication());
        productRepository.runExpirySweep();

        initializeUI();
        setupNearExpiryCard();
        setupProductObserver(); // NEW: Fetch inventory to calculate accurate costs
        setupViewModel();
        setupSalesObserver();
        setupCharts();
        setupClickListeners();

        notificationBadgeManager = new NotificationBadgeManager(this);
        notificationBadgeManager.start();

        startEntryAnimation();
        loadDashboardData();
    }

    private void initializeUI() {
        swipeRefresh = findViewById(R.id.swipe_refresh);
        cardSalesAmount = findViewById(R.id.card_sales_amount);
        cardGrossProfit = findViewById(R.id.card_gross_profit);
        cardLowStock = findViewById(R.id.card_low_stock);
        cardPendingOrders = findViewById(R.id.card_pending_orders);
        cardNearExpiry = findViewById(R.id.card_near_expiry);

        tvSalesAmount = findViewById(R.id.tv_sales_amount);
        tvGrossProfit = findViewById(R.id.tv_gross_profit);
        tvLowStockCount = findViewById(R.id.tv_low_stock_count);
        tvPendingOrdersCount = findViewById(R.id.tv_pending_orders_count);
        tvNearExpiryCount = findViewById(R.id.tv_near_expiry_count);
        tvOverviewLabel = findViewById(R.id.tv_overview_label);
        tvLastUpdated = findViewById(R.id.tv_last_updated);

        // Map Business UI
        ivBusinessLogo = findViewById(R.id.ivBusinessLogo);
        tvBusinessName = findViewById(R.id.tvBusinessName);

        toggleTimeFilter = findViewById(R.id.toggle_time_filter);

        salesTrendChart = findViewById(R.id.chart_sales_trend);
        topProductsChart = findViewById(R.id.chart_top_products);
        inventoryStatusChart = findViewById(R.id.chart_inventory_status);

        btnCreateSale = findViewById(R.id.btn_create_sale);
        btnAddProduct = findViewById(R.id.btn_add_product);
        dimOverlay = findViewById(R.id.dim_overlay);

        btnCreatePO = findViewById(R.id.btn_create_po);
        btnViewReports = findViewById(R.id.btn_view_reports);
        btnInventory = findViewById(R.id.btn_inventory);
        btnCustomers = findViewById(R.id.btn_customers);
        btnManageUsers = findViewById(R.id.btn_manage_users);
        btnSettings = findViewById(R.id.btn_settings);
        btnProfile = findViewById(R.id.btn_profile);

        statsGrid = findViewById(R.id.stats_grid);
        quickActionsGrid = findViewById(R.id.quick_actions_grid);
        progressBar = findViewById(R.id.progress_bar);

        rvRecentActivity = findViewById(R.id.rv_recent_activity);
        activityAdapter = new RecentActivityAdapter(this);
        rvRecentActivity.setAdapter(activityAdapter);
        rvRecentActivity.setLayoutManager(new LinearLayoutManager(this));

        layoutImpersonationBanner = findViewById(R.id.layout_impersonation_banner);
        tvImpersonationText = findViewById(R.id.tv_impersonation_text);
        btnExitImpersonation = findViewById(R.id.btn_exit_impersonation);

        if (btnExitImpersonation != null) {
            btnExitImpersonation.setOnClickListener(v -> exitImpersonation());
        }

        if (toggleTimeFilter != null) {
            toggleTimeFilter.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                if (isChecked) {
                    if (checkedId == R.id.btn_filter_daily) applyFilter(0);
                    else if (checkedId == R.id.btn_filter_weekly) applyFilter(1);
                    else if (checkedId == R.id.btn_filter_monthly) applyFilter(2);
                    else if (checkedId == R.id.btn_filter_all_time) applyFilter(3);
                }
            });
        }

        authManager = AuthManager.getInstance();

        if (swipeRefresh != null) {
            swipeRefresh.setOnRefreshListener(this::onSwipeRefresh);
        }
    }

    private void loadBusinessProfile() {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();

        if (ownerId != null && !ownerId.isEmpty() && com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser() != null) {
            FirebaseFirestore.getInstance().collection("users").document(ownerId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String bName = documentSnapshot.getString("businessName");
                            String bLogo = documentSnapshot.getString("businessLogoUrl");

                            if (bName != null && !bName.isEmpty()) tvBusinessName.setText(bName);
                            if (bLogo != null && !bLogo.isEmpty()) {
                                ivBusinessLogo.setVisibility(View.VISIBLE);
                                Glide.with(MainActivity.this).load(bLogo).into(ivBusinessLogo);
                            } else {
                                ivBusinessLogo.setVisibility(View.GONE);
                            }
                        }
                    });
        }
    }

    private void startEntryAnimation() {
        animateView(statsGrid, 0);
        animateView(quickActionsGrid, 100);
        animateView(salesTrendChart, 200);
        animateView(topProductsChart, 300);
        animateView(inventoryStatusChart, 300);
        animateView(rvRecentActivity, 400);
    }

    private void animateView(View view, long delay) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(50f);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(500)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void resolveUserRoleAndConfigureUI() {
        authManager.refreshCurrentUserStatus(success -> runOnUiThread(() -> {
            boolean isRealAdmin = authManager.isCurrentUserAdmin();
            if (isImpersonating) {
                isAdminFlag = false;
            } else {
                isAdminFlag = isRealAdmin;
            }
            applyRoleVisibility();
        }));
    }

    private void applyRoleVisibility() {
        if (isAdminFlag) {
            if (btnCreateSale != null) btnCreateSale.setVisibility(View.VISIBLE);
            if (btnAddProduct != null) btnAddProduct.setVisibility(View.VISIBLE);
            if (btnCreatePO != null) btnCreatePO.setVisibility(View.VISIBLE);
            if (btnViewReports != null) btnViewReports.setVisibility(View.VISIBLE);
            if (btnInventory != null) btnInventory.setVisibility(View.VISIBLE);
            if (btnCustomers != null) btnCustomers.setVisibility(View.VISIBLE);
            if (btnManageUsers != null) btnManageUsers.setVisibility(View.VISIBLE);
            if (btnButtons != null) btnButtons.setVisibility(View.VISIBLE);
        } else {
            if (btnCreateSale != null) btnCreateSale.setVisibility(View.VISIBLE);
            if (btnAddProduct != null) btnAddProduct.setVisibility(View.GONE);
            if (btnCreatePO != null) btnCreatePO.setVisibility(View.GONE);
            if (btnViewReports != null) btnViewReports.setVisibility(View.VISIBLE);
            if (btnInventory != null) btnInventory.setVisibility(View.VISIBLE);
            if (btnCustomers != null) btnCustomers.setVisibility(View.GONE);
            if (btnManageUsers != null) btnManageUsers.setVisibility(View.GONE);
            if (btnButtons != null) btnButtons.setVisibility(View.GONE);

            if (layoutFabMenu != null) layoutFabMenu.setVisibility(View.GONE);
            if (dimOverlay != null) dimOverlay.setVisibility(View.GONE);
            isFabOpen = false;
        }
        arrangeQuickActions();
    }

    private void exitImpersonation() {
        isImpersonating = false;
        getIntent().removeExtra("IMPERSONATE_STAFF_NAME");
        if (layoutImpersonationBanner != null) {
            layoutImpersonationBanner.setVisibility(View.GONE);
        }
        resolveUserRoleAndConfigureUI();
        Toast.makeText(this, "Restored Admin View", Toast.LENGTH_SHORT).show();
    }

    // =========================================================================
    // NEW: FETCH PRODUCTS TO ENSURE ACCURATE HISTORICAL COSTS
    // =========================================================================
    private void setupProductObserver() {
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                cachedProductList = products;

                // Reapply filters to update Gross Profit if sales were loaded before products
                if (!cachedSalesList.isEmpty() && toggleTimeFilter != null) {
                    int checkedId = toggleTimeFilter.getCheckedButtonId();
                    if (checkedId == R.id.btn_filter_daily) applyFilter(0);
                    else if (checkedId == R.id.btn_filter_weekly) applyFilter(1);
                    else if (checkedId == R.id.btn_filter_monthly) applyFilter(2);
                    else if (checkedId == R.id.btn_filter_all_time) applyFilter(3);
                    else applyFilter(0);
                }
            }
        });
    }

    private void setupSalesObserver() {
        salesRepository.getAllSales().observe(this, sales -> {
            if (sales != null) {
                cachedSalesList = sales;
                int checkedId = toggleTimeFilter.getCheckedButtonId();
                if (checkedId == R.id.btn_filter_daily) applyFilter(0);
                else if (checkedId == R.id.btn_filter_weekly) applyFilter(1);
                else if (checkedId == R.id.btn_filter_monthly) applyFilter(2);
                else if (checkedId == R.id.btn_filter_all_time) applyFilter(3);
                else applyFilter(0);
            }
        });
    }

    // =========================================================================
    // FIXED: GROSS PROFIT CALCULATION MATCHES REPORT.JAVA
    // =========================================================================
    private void applyFilter(int mode) {
        long startTime = 0;
        Calendar cal = Calendar.getInstance();

        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        String label = "Overview";

        switch (mode) {
            case 0:
                startTime = cal.getTimeInMillis();
                label = "Overview (Today)";
                break;
            case 1:
                cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
                startTime = cal.getTimeInMillis();
                label = "Overview (This Week)";
                break;
            case 2:
                cal.set(Calendar.DAY_OF_MONTH, 1);
                startTime = cal.getTimeInMillis();
                label = "Overview (This Month)";
                break;
            case 3:
                startTime = 0;
                label = "Overview (All Time)";
                break;
        }

        if (tvOverviewLabel != null) tvOverviewLabel.setText(label);

        double totalSales = 0.0;
        double totalCost = 0.0;

        for (Sales sale : cachedSalesList) {
            long ts = sale.getTimestamp() > 0 ? sale.getTimestamp() : sale.getDate();
            if (ts >= startTime) {
                double netPrice = sale.getTotalPrice();
                double cost = sale.getTotalCost();

                // SMART FALLBACK: Look up the product cost locally if the sale missed it (Just like Reports)
                if (cost <= 0) {
                    String rawName = sale.getProductName() != null ? sale.getProductName() : "";
                    String baseName = rawName;

                    int parenIdx = rawName.indexOf(" (");
                    int bracketIdx = rawName.indexOf(" [");
                    int minIdx = rawName.length();

                    if (parenIdx != -1) minIdx = Math.min(minIdx, parenIdx);
                    if (bracketIdx != -1) minIdx = Math.min(minIdx, bracketIdx);

                    if (minIdx != rawName.length()) {
                        baseName = rawName.substring(0, minIdx).trim();
                    }

                    for (Product p : cachedProductList) {
                        if (p.getProductName() != null && p.getProductName().equalsIgnoreCase(baseName)) {
                            cost = p.getCostPrice() * sale.getQuantity();
                            break;
                        }
                    }
                }

                totalSales += netPrice;
                totalCost += cost;
            }
        }

        if (tvSalesAmount != null) tvSalesAmount.setText(String.format(Locale.US, "₱%,.2f", totalSales));
        if (tvGrossProfit != null) {
            double grossProfit = totalSales - totalCost;
            tvGrossProfit.setText(String.format(Locale.US, "₱%,.2f", grossProfit));
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
                updateStaticCards(metrics);
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

    private void updateStaticCards(DashboardMetrics metrics) {
        if (tvLowStockCount != null && cardLowStock != null) {
            int lowCount = metrics.getLowStockCount();
            tvLowStockCount.setText(String.valueOf(lowCount));
            cardLowStock.setVisibility(View.VISIBLE);
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

    private void setupCharts() {
        if (salesTrendChart != null) {
            salesTrendChart.getDescription().setEnabled(false);
            salesTrendChart.setDrawGridBackground(false);
            salesTrendChart.setNoDataText("Loading Data...");
        }
        if (topProductsChart != null) {
            topProductsChart.getDescription().setEnabled(false);
            topProductsChart.setDrawGridBackground(false);
            topProductsChart.setNoDataText("Loading Data...");
        }
        if (inventoryStatusChart != null) {
            inventoryStatusChart.getDescription().setEnabled(false);
            inventoryStatusChart.setNoDataText("Loading Data...");
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

        if (cardSalesAmount != null) cardSalesAmount.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, Reports.class)));
        if (cardGrossProfit != null) cardGrossProfit.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, Reports.class)));
        if (cardLowStock != null) cardLowStock.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LowStockItemsActivity.class)));
        if (cardPendingOrders != null) cardPendingOrders.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, PurchaseOrderListActivity.class)));

        if (btnManageUsers != null) btnManageUsers.setOnClickListener(v -> {
            if (isAdminFlag) authManager.isCurrentUserAdminAsync(success -> runOnUiThread(() -> {
                if (success) startActivity(new Intent(MainActivity.this, AdminStaffList.class));
                else Toast.makeText(MainActivity.this, "Admin access required", Toast.LENGTH_LONG).show();
            }));
            else Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show();
        });
        if (swipeRefresh != null) swipeRefresh.setOnRefreshListener(this::loadDashboardData);
    }

    private void arrangeQuickActions() {
        if (quickActionsGrid == null) return;
        quickActionsGrid.invalidate();
        quickActionsGrid.requestLayout();
    }

    private void loadDashboardData() {
        viewModel.loadDashboardData();
        viewModel.loadRecentActivities();
        viewModel.loadChartData(salesTrendChart, topProductsChart, inventoryStatusChart);
        salesRepository.reloadAllSales();
    }

    private void onSwipeRefresh() {
        viewModel.refreshDashboardData();
        viewModel.loadChartData(salesTrendChart, topProductsChart, inventoryStatusChart);
        salesRepository.reloadAllSales();
    }

    public void onNotificationsClicked(View view) {
        showNotificationBottomSheet();
    }

    private void showNotificationBottomSheet() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this, com.google.android.material.R.style.Theme_Design_BottomSheetDialog);
        View sheetView = LayoutInflater.from(this).inflate(R.layout.layout_notifications_sheet, null);
        bottomSheetDialog.setContentView(sheetView);

        FrameLayout bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        if (bottomSheet != null) {
            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(bottomSheet);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            behavior.setSkipCollapsed(true);
            bottomSheet.getLayoutParams().height = android.view.ViewGroup.LayoutParams.MATCH_PARENT;
        }

        RecyclerView rvNotifications = sheetView.findViewById(R.id.rv_notifications);
        View emptyState = sheetView.findViewById(R.id.layout_empty_state);
        Button btnClearAll = sheetView.findViewById(R.id.btn_clear_all);

        rvNotifications.setLayoutManager(new LinearLayoutManager(this));

        AlertRepository repo = AlertRepository.getInstance(getApplication());
        NotificationAdapter adapter = new NotificationAdapter(this, alert -> {
            repo.markAlertAsRead(alert.getId(), null);
            bottomSheetDialog.dismiss();

            String type = alert.getType() != null ? alert.getType() : "";
            Intent intent;

            if (type.equals("LOW_STOCK") || type.equals("CRITICAL_STOCK")) {
                intent = new Intent(MainActivity.this, LowStockItemsActivity.class);
            } else if (type.contains("EXPIRY") || type.equals("EXPIRED")) {
                intent = new Intent(MainActivity.this, NearExpiryItemsActivity.class);
            } else if (type.equals("PO_RECEIVED")) {
                intent = new Intent(MainActivity.this, PurchaseOrderListActivity.class);
            } else {
                intent = new Intent(MainActivity.this, StockAlertsActivity.class);
            }

            intent.putExtra("alertId", alert.getId());
            startActivity(intent);
        });

        rvNotifications.setAdapter(adapter);

        productRepository.getAllProducts().observe(this, products -> {
            adapter.setProducts(products);
        });

        repo.getAllAlerts().observe(this, alerts -> {
            if (alerts == null || alerts.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                rvNotifications.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                rvNotifications.setVisibility(View.VISIBLE);
                adapter.setAlerts(alerts);
            }
        });

        btnClearAll.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Clear All")
                    .setMessage("Are you sure you want to delete all notifications?")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        repo.clearAllAlerts();
                        NotificationHelper.clearAllNotifications(MainActivity.this);
                        Toast.makeText(MainActivity.this, "Notifications cleared", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        bottomSheetDialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("IMPERSONATE_STAFF_NAME")) {
            isImpersonating = true;
            String staffName = intent.getStringExtra("IMPERSONATE_STAFF_NAME");
            if (layoutImpersonationBanner != null) {
                layoutImpersonationBanner.setVisibility(View.VISIBLE);
                tvImpersonationText.setText("👀 Viewing layout as: " + staffName);
            }
        }

        resolveUserRoleAndConfigureUI();
        loadDashboardData();

        // REFRESH BUSINESS PROFILE EVERY TIME DASHBOARD IS OPENED
        loadBusinessProfile();
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
