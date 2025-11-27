package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";
    private View btnSettings;
    private Button btnProfil;
    private Button btnStock;
    private Button btnSelles;
    private Button btnDelete;
    private Button btnPurchase;
    private Button btnAdminManage;
    private TextView welcomeT;
    private TextView criticalCountTV;
    private TextView totalCountTV;
    private TextView syncStatusTV;
    private FirebaseAuth fAuth;
    private FirebaseFirestore fStore;
    private String userID;
    private ProductRepository productRepository;
    private AlertRepository alertRepository;
    private FirestoreSyncListener syncListener;
    private ThemeManager themeManager;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        themeManager = ThemeManager.getInstance(this);
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_main_land);
        } else {
            setContentView(R.layout.activity_main);
        }
        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        authManager = AuthManager.getInstance();
        if (fAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, FirstActivity.class));
            finish();
            return;
        }
        userID = fAuth.getCurrentUser().getUid();
        syncListener = FirestoreSyncListener.getInstance();
        productRepository = SalesInventoryApplication.getProductRepository();
        alertRepository = SalesInventoryApplication.getAlertRepository();
        initializeUI();
        loadUserInfo();
        observeProducts();
        observeAlerts();
        observeSyncStatus();
    }

    private void initializeUI() {
        btnProfil = findViewById(R.id.ProfilBtn);
        btnStock = findViewById(R.id.StockBtn);
        btnSelles = findViewById(R.id.SellesBtn);
        btnDelete = findViewById(R.id.DeleteBtn);
        btnPurchase = findViewById(R.id.PruchaseBtn);
        btnSettings = findViewById(R.id.btnSettings);
        btnAdminManage = findViewById(R.id.btnAdminManage);
        welcomeT = findViewById(R.id.welcomeT);
        criticalCountTV = findViewById(R.id.CriticalCountTV);
        totalCountTV = findViewById(R.id.TotalCountTV);
        try {
            syncStatusTV = findViewById(R.id.syncStatusTV);
            if (syncStatusTV != null) syncStatusTV.setText("Syncing...");
        } catch (Exception e) {
            Log.w(TAG, "Sync status view not found");
        }
        if (btnSettings != null) btnSettings.setOnClickListener(v -> OpenSettings(v));
        if (btnAdminManage != null) btnAdminManage.setOnClickListener(v -> {
            authManager.isCurrentUserAdminAsync(new AuthManager.SimpleCallback() {
                @Override
                public void onComplete(final boolean success) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (success) {
                                Intent i = new Intent(MainActivity.this, AdminManageUsersActivity.class);
                                startActivity(i);
                            } else {
                                Toast.makeText(MainActivity.this, "Admin access required", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            });
        });
    }

    private void loadUserInfo() {
        DocumentReference docRef = fStore.collection("users").document(userID);
        docRef.addSnapshotListener((doc, e) -> {
            if (e != null) {
                Log.e(TAG, "Error loading user info", e);
                return;
            }
            if (doc != null && doc.exists()) {
                String userName = doc.getString("Name");
                if (welcomeT != null) welcomeT.setText(userName != null ? userName : "Welcome");
                String role = doc.getString("role");
                if (role == null) role = doc.getString("Role");
                if (role == null) role = "";
                boolean isAdmin = "Admin".equalsIgnoreCase(role);
                if (isAdmin) {
                    if (btnDelete != null) btnDelete.setVisibility(View.VISIBLE);
                    if (btnPurchase != null) btnPurchase.setVisibility(View.VISIBLE);
                    if (btnAdminManage != null) btnAdminManage.setVisibility(View.VISIBLE);
                    if (findViewById(R.id.HistoryBtn) != null) findViewById(R.id.HistoryBtn).setVisibility(View.VISIBLE);
                    if (findViewById(R.id.PruchaseBtn) != null) findViewById(R.id.PruchaseBtn).setVisibility(View.VISIBLE);
                } else {
                    if (btnDelete != null) btnDelete.setVisibility(View.GONE);
                    if (btnPurchase != null) btnPurchase.setVisibility(View.GONE);
                    if (btnAdminManage != null) btnAdminManage.setVisibility(View.GONE);
                    if (findViewById(R.id.HistoryBtn) != null) findViewById(R.id.HistoryBtn).setVisibility(View.GONE);
                    if (findViewById(R.id.PruchaseBtn) != null) findViewById(R.id.PruchaseBtn).setVisibility(View.GONE);
                }
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    private void observeProducts() {
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                updateDashboardStats(products);
                Log.d(TAG, "Products updated: " + products.size());
            }
        });
    }

    private void observeAlerts() {
        alertRepository.getUnreadAlertCount().observe(this, count -> {
            if (count != null && count > 0) {
                Log.d(TAG, "Unread alerts: " + count);
            }
        });
    }

    private void observeSyncStatus() {
        syncListener.productsSyncStatus.observe(this, status -> {
            if (status != null && syncStatusTV != null) {
                String statusText = status.getStatus().toString();
                syncStatusTV.setText(statusText);
                int color;
                if (status.isSynced()) {
                    color = getResources().getColor(android.R.color.holo_green_dark);
                } else if (status.isOffline()) {
                    color = getResources().getColor(android.R.color.holo_orange_dark);
                } else if (status.hasError()) {
                    color = getResources().getColor(android.R.color.holo_red_dark);
                } else {
                    color = getResources().getColor(android.R.color.holo_blue_dark);
                }
                syncStatusTV.setTextColor(color);
            }
        });
    }

    private void updateDashboardStats(List<Product> products) {
        int totalItems = products.size();
        int criticalItemsCount = 0;
        for (Product p : products) {
            int currentAmount = p.getQuantity();
            int minStock = p.getReorderLevel();
            if (currentAmount <= minStock) criticalItemsCount++;
        }
        final int finalTotal = totalItems;
        final int finalCritical = criticalItemsCount;
        runOnUiThread(() -> {
            if (totalCountTV != null) totalCountTV.setText(String.valueOf(finalTotal));
            if (criticalCountTV != null) criticalCountTV.setText(String.valueOf(finalCritical));
            if (syncStatusTV != null) {
                syncStatusTV.setText("✓ Synced");
                syncStatusTV.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            }
        });
        Log.d(TAG, "Dashboard updated - Total: " + totalItems + ", Critical: " + criticalItemsCount);
    }

    public void OpenManage(View view) {
        authManager.isCurrentUserAdminAsync(new AuthManager.SimpleCallback() {
            @Override
            public void onComplete(final boolean success) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (success) {
                            startActivity(new Intent(MainActivity.this, AdminManageUsersActivity.class));
                        } else {
                            Toast.makeText(MainActivity.this, "Admin access required", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
    }

    public void LogOut(View view) {
        syncListener.stopAllListeners();
        FirebaseAuth.getInstance().signOut();
        SalesInventoryApplication.resetRepositories();
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(getApplicationContext(), FirstActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    public void OpenAdd(View view) {
        startActivity(new Intent(this, AddProduct.class));
    }

    public void OpenProfil(View view) {
        startActivity(new Intent(this, Profile.class));
    }

    public void OpenStock(View view) {
        startActivity(new Intent(getApplicationContext(), Inventory.class));
    }

    public void OpenHistory(View view) {
        try {
            startActivity(new Intent(this, InventoryReportsActivity.class));
        } catch (Exception ex) {
            Toast.makeText(this, "Reports activity not available", Toast.LENGTH_LONG).show();
            Log.e(TAG, "InventoryReportsActivity not found", ex);
        }
    }

    public void OpenSell(View view) {
        startActivity(new Intent(getApplicationContext(), SellList.class));
    }

    public void OpenDelete(View view) {
        startActivity(new Intent(getApplicationContext(), DeleteProduct.class));
    }

    public void OpenSettings(View view) {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "Orientation changed");
    }
}