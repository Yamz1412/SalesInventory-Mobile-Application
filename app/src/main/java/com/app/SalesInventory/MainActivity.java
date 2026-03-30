package com.app.SalesInventory;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends BaseActivity {

    // CAFE OVERVIEW UI COMPONENTS
    private TextView tvTotalSales, tvTotalProfit, tvLowStock, tvPendingOrders;

    // ALERTS & OTHER UI
    private CardView cardNearExpiry;
    private TextView tvNearExpiryCount;

    private LineChart salesTrendChart;
    private BarChart topProductsChart;
    private PieChart inventoryStatusChart;
    private LinearLayout btnCreateSale, btnCreatePO, btnViewReports, btnInventory;

    // FAB Menu Components
    private FloatingActionButton btnAddProduct, fabAddManual, fabSuggestedProduct;
    private View dimOverlay;
    private LinearLayout layoutFabMenu;
    private boolean isFabOpen = false;

    private MaterialButton btnManageUsers;
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
    private GridLayout dashboardGrid, quickActionsGrid;

    // Logic & Data
    private DashboardViewModel viewModel;
    private AuthManager authManager;
    private ProductRepository productRepository;
    private SalesRepository salesRepository;
    private NotificationBadgeManager badgeManager;
    private boolean isAdminFlag = false;

    // Data Cache for Filtering
    private List<Sales> cachedSalesList = new ArrayList<>();
    private List<Product> cachedProductList = new ArrayList<>();

    private LinearLayout layoutImpersonationBanner;
    private TextView tvImpersonationText;
    private Button btnExitImpersonation;
    private boolean isImpersonating = false;

    // REAL-TIME RESET LISTENER
    private com.google.firebase.firestore.ListenerRegistration resetSignalListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        AuthManager.getInstance().init(getApplication());
        badgeManager = new NotificationBadgeManager(this);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        String currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (currentUid != null) {
            DatabaseReference userStatusRef = FirebaseDatabase.getInstance().getReference("UsersStatus").child(currentUid);
            DatabaseReference connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");

            connectedRef.addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                @Override
                public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                    Boolean connected = snapshot.getValue(Boolean.class);
                    if (connected != null && connected) {
                        userStatusRef.onDisconnect().setValue("offline");
                        userStatusRef.setValue("online");
                    }
                }
                @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
            });
        }

        productRepository = SalesInventoryApplication.getProductRepository();
        salesRepository = SalesRepository.getInstance(getApplication());
        productRepository.runExpirySweep();

        initializeUI();
        setupNearExpiryCard();
        setupProductObserver();
        setupViewModel();
        setupSalesObserver();
        setupCharts();
        setupClickListeners();

        listenForFactoryReset();

        startEntryAnimation();
        loadDashboardData();

        getOnBackPressedDispatcher().addCallback(this, new androidx.activity.OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                        .setTitle("Exit Application")
                        .setMessage("Are you sure you want to exit the application?")
                        .setCancelable(false)
                        .setPositiveButton("Exit", (dialog, which) -> {
                            finishAffinity();
                            System.exit(0);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .show();
            }
        });
    }

    private void listenForFactoryReset() {
        resetSignalListener = FirestoreManager.getInstance().getResetSignalRef()
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    if (snapshot != null && snapshot.exists()) {
                        com.google.firebase.Timestamp ts = snapshot.getTimestamp("lastResetTime");
                        if (ts != null) {
                            long resetTimeMillis = ts.toDate().getTime();
                            android.content.SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                            long localLastReset = prefs.getLong("localLastReset", 0);

                            if (resetTimeMillis > localLastReset) {
                                prefs.edit().putLong("localLastReset", resetTimeMillis).apply();

                                new Thread(() -> {
                                    try {
                                        if (SalesInventoryApplication.getProductRepository() != null) {
                                            SalesInventoryApplication.getProductRepository().clearLocalData();
                                        }
                                        if (SalesInventoryApplication.getSalesRepository() != null) {
                                            SalesInventoryApplication.getSalesRepository().clearData();
                                        }
                                        try {
                                            AlertRepository.getInstance(getApplication()).clearAllAlerts();
                                        } catch (Exception ignored) {}
                                    } catch (Exception ex) {
                                        android.util.Log.e("MainActivity", "Local wipe error: " + ex.getMessage());
                                    }

                                    runOnUiThread(() -> {
                                        Toast.makeText(MainActivity.this, "Admin performed a system reset. Local data cleared.", Toast.LENGTH_LONG).show();
                                        NotificationHelper.clearAllNotifications(MainActivity.this);
                                        if (activityAdapter != null) activityAdapter.setActivities(new ArrayList<>());
                                        loadDashboardData();
                                    });
                                }).start();
                            }
                        }
                    }
                });
    }

    private void initializeUI() {
        swipeRefresh = findViewById(R.id.swipe_refresh);

        tvTotalSales = findViewById(R.id.tvTotalSales);
        tvTotalProfit = findViewById(R.id.tvTotalProfit);
        tvLowStock = findViewById(R.id.tvLowStock);
        tvPendingOrders = findViewById(R.id.tvPendingOrders);

        cardNearExpiry = findViewById(R.id.card_near_expiry);
        tvNearExpiryCount = findViewById(R.id.tv_near_expiry_count);
        tvLastUpdated = findViewById(R.id.tv_last_updated);

        ivBusinessLogo = findViewById(R.id.ivBusinessLogo);
        tvBusinessName = findViewById(R.id.tvBusinessName);

        toggleTimeFilter = findViewById(R.id.toggle_time_filter);

        salesTrendChart = findViewById(R.id.chart_sales_trend);
        topProductsChart = findViewById(R.id.chart_top_products);
        inventoryStatusChart = findViewById(R.id.chart_inventory_status);

        btnCreateSale = findViewById(R.id.btn_create_sale);

        btnAddProduct = findViewById(R.id.btn_add_product);
        fabAddManual = findViewById(R.id.fab_add_manual);
        fabSuggestedProduct = findViewById(R.id.fab_suggested_product);
        dimOverlay = findViewById(R.id.dim_overlay);
        layoutFabMenu = findViewById(R.id.layout_fab_menu);

        btnCreatePO = findViewById(R.id.btn_create_po);
        btnViewReports = findViewById(R.id.btn_view_reports);
        btnInventory = findViewById(R.id.btn_inventory);
        btnManageUsers = findViewById(R.id.btn_manage_users);
        btnSettings = findViewById(R.id.btn_settings);
        btnProfile = findViewById(R.id.btn_profile);

        dashboardGrid = findViewById(R.id.dashboard_grid);
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
                            if (isDestroyed() || isFinishing()) {
                                return; }
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
        animateView(dashboardGrid, 0);
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
            if (btnManageUsers != null) btnManageUsers.setVisibility(View.VISIBLE);
        } else {
            if (btnCreateSale != null) btnCreateSale.setVisibility(View.VISIBLE);
            if (btnAddProduct != null) btnAddProduct.setVisibility(View.GONE);
            if (btnCreatePO != null) btnCreatePO.setVisibility(View.GONE);
            if (btnViewReports != null) btnViewReports.setVisibility(View.VISIBLE);
            if (btnInventory != null) btnInventory.setVisibility(View.VISIBLE);
            if (btnManageUsers != null) btnManageUsers.setVisibility(View.GONE);

            if (dimOverlay != null) dimOverlay.setVisibility(View.GONE);
            if (layoutFabMenu != null) layoutFabMenu.setVisibility(View.GONE);
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

    private void setupProductObserver() {
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                cachedProductList = products;

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

    private void applyFilter(int mode) {
        long startTime = 0;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);

        switch (mode) {
            case 0: startTime = cal.getTimeInMillis(); break;
            case 1: cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek()); startTime = cal.getTimeInMillis(); break;
            case 2: cal.set(Calendar.DAY_OF_MONTH, 1); startTime = cal.getTimeInMillis(); break;
            case 3: startTime = 0; break;
        }

        double totalSales = 0.0;
        double totalCost = 0.0;

        for (Sales sale : cachedSalesList) {
            long ts = sale.getTimestamp() > 0 ? sale.getTimestamp() : sale.getDate();
            if (ts >= startTime) {
                totalSales += sale.getTotalPrice();

                double cost = sale.getTotalCost();
                if (cost <= 0) {
                    String baseName = sale.getProductName() != null ? sale.getProductName() : "";
                    if (baseName.contains(" (")) baseName = baseName.substring(0, baseName.indexOf(" (")).trim();
                    for (Product p : cachedProductList) {
                        if (p.getProductName() != null && p.getProductName().equalsIgnoreCase(baseName)) {
                            cost = p.getCostPrice() * sale.getQuantity(); break;
                        }
                    }
                }
                totalCost += cost;
            }
        }

        double profit = totalSales - totalCost;

        if (tvTotalSales != null) tvTotalSales.setText(String.format(Locale.US, "₱%,.2f", totalSales));
        if (tvTotalProfit != null) tvTotalProfit.setText(String.format(Locale.US, "₱%,.2f", profit));
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
        if (tvLowStock != null) {
            int lowCount = metrics.getLowStockCount();
            tvLowStock.setText(lowCount + (lowCount == 1 ? " Item" : " Items"));
        }
        if (tvPendingOrders != null) {
            int poCount = metrics.getPendingOrdersCount();
            tvPendingOrders.setText(poCount + (poCount == 1 ? " Order" : " Orders"));
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
        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        if (salesTrendChart != null) {
            salesTrendChart.getDescription().setEnabled(false);
            salesTrendChart.setDrawGridBackground(false);
            salesTrendChart.setNoDataText("Loading Data...");
            salesTrendChart.getAxisLeft().setTextColor(textColor);
            salesTrendChart.getAxisRight().setTextColor(textColor);
            salesTrendChart.getXAxis().setTextColor(textColor);
            salesTrendChart.getLegend().setTextColor(textColor);
        }
        if (topProductsChart != null) {
            topProductsChart.getDescription().setEnabled(false);
            topProductsChart.setDrawGridBackground(false);
            topProductsChart.setNoDataText("Loading Data...");
            topProductsChart.getAxisLeft().setTextColor(textColor);
            topProductsChart.getAxisRight().setTextColor(textColor);
            topProductsChart.getXAxis().setTextColor(textColor);
            topProductsChart.getLegend().setTextColor(textColor);
        }
        if (inventoryStatusChart != null) {
            inventoryStatusChart.getDescription().setEnabled(false);
            inventoryStatusChart.setNoDataText("Loading Data...");
            inventoryStatusChart.getLegend().setTextColor(textColor);
            inventoryStatusChart.setEntryLabelColor(textColor);
        }
    }

    private void showFabMenu() {
        isFabOpen = true;
        if (layoutFabMenu != null) layoutFabMenu.setVisibility(View.VISIBLE);
        if (dimOverlay != null) dimOverlay.setVisibility(View.VISIBLE);
        if (btnAddProduct != null) btnAddProduct.animate().rotation(45f).setDuration(200).start();
    }

    private void closeFabMenu() {
        isFabOpen = false;
        if (layoutFabMenu != null) layoutFabMenu.setVisibility(View.GONE);
        if (dimOverlay != null) dimOverlay.setVisibility(View.GONE);
        if (btnAddProduct != null) btnAddProduct.animate().rotation(0f).setDuration(200).start();
    }

    private void setupClickListeners() {
        if (btnSettings != null) btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        if (btnProfile != null) btnProfile.setOnClickListener(v -> startActivity(new Intent(this, Profile.class)));
        if (btnCreateSale != null) btnCreateSale.setOnClickListener(v -> startActivity(new Intent(this, SellList.class)));

        if (btnAddProduct != null) {
            btnAddProduct.setOnClickListener(v -> {
                if (!isAdminFlag) {
                    Toast.makeText(this, "Admin access required", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isFabOpen) showFabMenu();
                else closeFabMenu();
            });
        }

        if (dimOverlay != null) {
            dimOverlay.setOnClickListener(v -> closeFabMenu());
        }

        if (fabAddManual != null) {
            fabAddManual.setOnClickListener(v -> {
                closeFabMenu();
                startActivity(new Intent(this, AddProductActivity.class));
            });
        }

        if (fabSuggestedProduct != null) {
            fabSuggestedProduct.setOnClickListener(v -> {
                closeFabMenu();
                startActivity(new Intent(this, SuggestedSuppliesActivity.class));
            });
        }

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

        if (tvTotalSales != null && tvTotalSales.getParent() != null) {
            ((View) tvTotalSales.getParent().getParent()).setOnClickListener(v -> startActivity(new Intent(MainActivity.this, Reports.class)));
        }
        if (tvTotalProfit != null && tvTotalProfit.getParent() != null) {
            ((View) tvTotalProfit.getParent().getParent()).setOnClickListener(v -> startActivity(new Intent(MainActivity.this, Reports.class)));
        }
        if (tvLowStock != null && tvLowStock.getParent() != null) {
            ((View) tvLowStock.getParent().getParent()).setOnClickListener(v -> startActivity(new Intent(MainActivity.this, LowStockItemsActivity.class)));
        }
        if (tvPendingOrders != null && tvPendingOrders.getParent() != null) {
            ((View) tvPendingOrders.getParent().getParent()).setOnClickListener(v -> startActivity(new Intent(MainActivity.this, PurchaseOrderListActivity.class)));
        }

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

    // Helper to keep dropdown text white in dark mode
    private ArrayAdapter<String> getAdaptiveAdapter(List<String> items) {
        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? android.graphics.Color.WHITE : android.graphics.Color.BLACK;

        ArrayAdapter<String> dropdownAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @androidx.annotation.NonNull
            @Override
            public android.view.View getView(int position, android.view.View convertView, @androidx.annotation.NonNull android.view.ViewGroup parent) {
                android.view.View v = super.getView(position, convertView, parent);
                ((android.widget.TextView) v).setTextColor(textColor);
                return v;
            }

            @Override
            public android.view.View getDropDownView(int position, android.view.View convertView, @androidx.annotation.NonNull android.view.ViewGroup parent) {
                android.view.View v = super.getDropDownView(position, convertView, parent);
                ((android.widget.TextView) v).setTextColor(textColor);
                return v;
            }
        };
        dropdownAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return dropdownAdapter;
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

        // FIX: Replaced with 3-parameter lambda to match updated Adapter
        NotificationAdapter adapter = new NotificationAdapter(this, (alert, product, exactCategory) -> {
            bottomSheetDialog.dismiss();
            Intent intent;

            // Route pure products directly to Product Details
            if (product != null && "Expiration".equals(exactCategory)) {
                intent = new Intent(MainActivity.this, ProductDetailActivity.class);
                intent.putExtra("productId", product.getProductId());
                startActivity(intent);
                return;
            }

            // Route Firebase Alerts
            if (alert != null) {
                repo.markAlertAsRead(alert.getId(), null);

                if ("Low Stock".equals(exactCategory)) {
                    intent = new Intent(MainActivity.this, LowStockItemsActivity.class);
                } else if ("Damaged".equals(exactCategory)) {
                    intent = new Intent(MainActivity.this, DamagedProductsReportActivity.class);
                } else if ("Supplier".equals(exactCategory)) {
                    intent = new Intent(MainActivity.this, PurchaseOrderListActivity.class);
                } else {
                    intent = new Intent(MainActivity.this, StockAlertsActivity.class);
                }
                intent.putExtra("alertId", alert.getId());
                startActivity(intent);
            }
        });

        rvNotifications.setAdapter(adapter);

        List<Alert> currentAlerts = new ArrayList<>();
        List<Product> currentProducts = new ArrayList<>();

        productRepository.getAllProducts().observe(this, products -> {
            currentProducts.clear();
            if (products != null) currentProducts.addAll(products);
            adapter.setAlertsAndProducts(currentAlerts, currentProducts);
        });

        repo.getAllAlerts().observe(this, alerts -> {
            currentAlerts.clear();
            if (alerts != null) currentAlerts.addAll(alerts);

            if (currentAlerts.isEmpty() && currentProducts.isEmpty()) {
                emptyState.setVisibility(View.VISIBLE);
                rvNotifications.setVisibility(View.GONE);
            } else {
                emptyState.setVisibility(View.GONE);
                rvNotifications.setVisibility(View.VISIBLE);
                adapter.setAlertsAndProducts(currentAlerts, currentProducts);
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

        Spinner spinnerFilter = sheetView.findViewById(R.id.spinnerNotificationFilter);
        String[] filterOptionsArray = {"All Notifications", "Low Stock", "Expiration", "Damaged/Spoiled", "Supplier Updates"};
        ArrayAdapter<String> spinnerAdapter = getAdaptiveAdapter(java.util.Arrays.asList(filterOptionsArray));
        spinnerFilter.setAdapter(spinnerAdapter);
        spinnerFilter.setSelection(0);


        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                adapter.filter(filterOptionsArray[position]);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        bottomSheetDialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (badgeManager != null) {
            badgeManager.start();
        }

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

        loadBusinessProfile();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (badgeManager != null) {
            badgeManager.stop();
        }
    }

    @Override
    protected void onDestroy() {
        if (resetSignalListener != null) {
            resetSignalListener.remove();
            resetSignalListener = null;
        }
        super.onDestroy();
    }
}