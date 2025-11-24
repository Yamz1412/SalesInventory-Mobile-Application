package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import java.util.List;
import javax.annotation.Nullable;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    private Button BtnProfil, BtnStock, BtnSelles, btnDelete, btnPurchase;
    private TextView Welcome, criticalCountTV, totalCountTV, syncStatusTV;
    private LinearLayout actionButtonsLayout;

    private FirebaseAuth fAuth;
    private FirebaseFirestore fStore;
    private String userID;

    private ProductRepository productRepository;
    private AlertRepository alertRepository;
    private FirestoreManager firestoreManager;
    private FirestoreSyncListener syncListener;
    private ThemeManager themeManager;

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

        if (fAuth.getCurrentUser() == null) {
            startActivity(new Intent(this, FirstActivity.class));
            finish();
            return;
        }
        userID = fAuth.getCurrentUser().getUid();

        firestoreManager = FirestoreManager.getInstance();
        syncListener = FirestoreSyncListener.getInstance();

        initializeRepositories();
        initializeUI();

        SalesInventoryApplication.BaseContext.setContext(this);

        loadUserInfo();
        observeProducts();
        observeAlerts();
        observeSyncStatus();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "Orientation changed");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Activity destroyed");
    }

    private void initializeRepositories() {
        productRepository = SalesInventoryApplication.getProductRepository();
        alertRepository = SalesInventoryApplication.getAlertRepository();
        Log.d(TAG, "Repositories initialized");
    }

    private void initializeUI() {
        BtnProfil = findViewById(R.id.ProfilBtn);
        BtnStock = findViewById(R.id.StockBtn);
        BtnSelles = findViewById(R.id.SellesBtn);
        Welcome = findViewById(R.id.welcomeT);
        criticalCountTV = findViewById(R.id.CriticalCountTV);
        totalCountTV = findViewById(R.id.TotalCountTV);
        btnDelete = findViewById(R.id.DeleteBtn);
        btnPurchase = findViewById(R.id.PruchaseBtn);

        try {
            syncStatusTV = findViewById(R.id.syncStatusTV);
            if (syncStatusTV != null) {
                syncStatusTV.setText("Syncing...");
            }
        } catch (Exception e) {
            Log.w(TAG, "Sync status TextView not found in layout");
        }

        applyThemeToUI();
    }

    private void applyThemeToUI() {
        int primaryColor = themeManager.getPrimaryColor();

        if (BtnProfil != null) BtnProfil.setBackgroundColor(primaryColor);
        if (BtnStock != null) BtnStock.setBackgroundColor(primaryColor);
        if (BtnSelles != null) BtnSelles.setBackgroundColor(primaryColor);

        Log.d(TAG, "Theme colors applied to UI");
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
                Welcome.setText(userName != null ? userName : "Welcome");

                String role = doc.getString("Role");
                if ("Employee".equals(role)) {
                    if (btnDelete != null) btnDelete.setVisibility(View.GONE);
                    if (btnPurchase != null) btnPurchase.setVisibility(View.GONE);
                } else {
                    if (btnDelete != null) btnDelete.setVisibility(View.VISIBLE);
                    if (btnPurchase != null) btnPurchase.setVisibility(View.VISIBLE);
                }
            }
        });
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

            if (currentAmount <= minStock) {
                criticalItemsCount++;
            }
        }

        final int finalTotal = totalItems;
        final int finalCritical = criticalItemsCount;

        runOnUiThread(() -> {
            totalCountTV.setText(String.valueOf(finalTotal));
            criticalCountTV.setText(String.valueOf(finalCritical));

            if (syncStatusTV != null) {
                syncStatusTV.setText("✓ Synced");
                syncStatusTV.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            }
        });

        Log.d(TAG, "Dashboard updated - Total: " + totalItems + ", Critical: " + criticalItemsCount);
    }

    public void LogOut(View view) {
        if (syncListener != null) syncListener.stopAllListeners();
        fAuth.signOut();
        SalesInventoryApplication.resetRepositories();

        Log.d(TAG, "User logged out");
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
        startActivity(new Intent(getApplicationContext(), InventoryReportsActivity.class));
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
}