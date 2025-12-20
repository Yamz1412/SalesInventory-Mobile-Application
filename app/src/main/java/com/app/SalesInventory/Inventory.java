package com.app.SalesInventory;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

public class Inventory extends BaseActivity {
    public static final String EXTRA_SHOW_LOW_STOCK_ONLY = "showLowStockOnly";
    public static final String EXTRA_SHOW_NEAR_EXPIRY_ONLY = "showNearExpiryOnly";
    private static final int REQ_DELETE_PRODUCT = 1201;

    private RecyclerView productsRecyclerView;
    private SearchView searchView;
    private TextView emptyStateTV;
    private ProductAdapter productAdapter;
    private List<Product> allProducts = new ArrayList<>();
    private List<Product> filteredProducts = new ArrayList<>();
    private ProductRepository productRepository;
    private AuthManager authManager;
    private Button btnAdjustStock;
    private Button btnAdjustmentHistory;
    private Button btnAdjustmentSummary;
    private Button btnDeleteProduct;
    private Spinner spinnerCategoryFilter;
    private String currentSearchQuery = "";
    private String currentCategoryFilter = "All";
    private CriticalStockNotifier criticalNotifier;
    private ProductRepository.OnCriticalStockListener criticalListener;
    private boolean showLowStockOnly = false;
    private boolean showNearExpiryOnly = false;
    private TextView tvTotalCount;
    private TextView tvLowStockWarning;
    private View bottomButtonLayout;
    private View rootView;
    private boolean buttonsHidden = false;
    private int keyboardThresholdPx;
    private DatabaseReference categoryRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);
        authManager = AuthManager.getInstance();
        productRepository = SalesInventoryApplication.getProductRepository();
        productsRecyclerView = findViewById(R.id.productsRecyclerView);
        searchView = findViewById(R.id.searchView);
        emptyStateTV = findViewById(R.id.emptyStateTV);
        btnAdjustStock = findViewById(R.id.btn_adjust_stock);
        btnAdjustmentHistory = findViewById(R.id.btn_adjustment_history);
        btnAdjustmentSummary = findViewById(R.id.btn_adjustment_summary);
        btnDeleteProduct = findViewById(R.id.btn_delete_product);
        spinnerCategoryFilter = findViewById(R.id.spinnerCategoryFilter);
        tvTotalCount = findViewById(R.id.TotalCountTV);
        tvLowStockWarning = findViewById(R.id.tvLowStockWarning);
        bottomButtonLayout = findViewById(R.id.bottom_button_layout);
        rootView = findViewById(android.R.id.content);
        showLowStockOnly = getIntent().getBooleanExtra(EXTRA_SHOW_LOW_STOCK_ONLY, false);
        showNearExpiryOnly = getIntent().getBooleanExtra(EXTRA_SHOW_NEAR_EXPIRY_ONLY, false);
        productAdapter = new ProductAdapter(this);
        productsRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        productsRecyclerView.setAdapter(productAdapter);
        keyboardThresholdPx = (int)(150 * getResources().getDisplayMetrics().density);
        authManager.refreshCurrentUserStatus(success -> runOnUiThread(() -> {
            boolean isAdmin = authManager.isCurrentUserAdmin();
            if (isAdmin) {
                if (btnAdjustStock != null) btnAdjustStock.setVisibility(View.VISIBLE);
                if (btnAdjustmentHistory != null) btnAdjustmentHistory.setVisibility(View.VISIBLE);
                if (btnAdjustmentSummary != null) btnAdjustmentSummary.setVisibility(View.VISIBLE);
                if (btnDeleteProduct != null) btnDeleteProduct.setVisibility(View.VISIBLE);
            } else {
                if (btnAdjustStock != null) btnAdjustStock.setVisibility(View.GONE);
                if (btnAdjustmentHistory != null) btnAdjustmentHistory.setVisibility(View.GONE);
                if (btnAdjustmentSummary != null) btnAdjustmentSummary.setVisibility(View.GONE);
                if (btnDeleteProduct != null) btnDeleteProduct.setVisibility(View.GONE);
            }
        }));
        setupAdjustmentButtons();
        categoryRef = FirebaseDatabase.getInstance().getReference("Categories");
        listenToCategoriesForFilter();
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                allProducts = new ArrayList<>(products);
                updateHeaderStats();
                applyFilters();
                listenToCategoriesForFilter();
            }
        });
        criticalNotifier = CriticalStockNotifier.getInstance();
        criticalListener = product -> runOnUiThread(() -> criticalNotifier.showCriticalDialog(this, product));
        productRepository.registerCriticalStockListener(criticalListener);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { currentSearchQuery = query == null ? "" : query; applyFilters(); return false; }
            @Override public boolean onQueryTextChange(String newText) { currentSearchQuery = newText == null ? "" : newText; applyFilters(); return false; }
        });
        productsRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(RecyclerView rv, int dx, int dy) {
                if (dy > 5) hideBottomButtons();
                else if (dy < -5) showBottomButtons();
            }
        });
        rootView.getViewTreeObserver().addOnGlobalLayoutListener(keyboardLayoutListener);
        productAdapter.setOnProductLongClickListener((product, position) -> {
            if (product == null) return;
            showArchiveConfirmDialog(product, position);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        handleIntentUpdate(getIntent());
        Intent i = getIntent();
        if (i != null) {
            i.removeExtra("updatedProductId");
            i.removeExtra("updatedLocalId");
            i.removeExtra("updatedQuantity");
            i.removeExtra("deleted");
            setIntent(i);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntentUpdate(intent);
    }

    private void handleIntentUpdate(Intent intent) {
        if (intent == null) return;
        boolean deleted = intent.getBooleanExtra("deleted", false);
        if (intent.hasExtra("updatedProductId") || intent.hasExtra("updatedLocalId")) {
            String updatedProductId = intent.getStringExtra("updatedProductId");
            long updatedLocalId = intent.getLongExtra("updatedLocalId", 0L);
            int updatedQuantity = intent.getIntExtra("updatedQuantity", -1);
            if (updatedProductId != null && !updatedProductId.isEmpty()) {
                for (Product p : allProducts) {
                    if (p != null && updatedProductId.equals(p.getProductId())) {
                        if (updatedQuantity >= 0) p.setQuantity(updatedQuantity);
                        break;
                    }
                }
                for (Product p : filteredProducts) {
                    if (p != null && updatedProductId.equals(p.getProductId())) {
                        if (updatedQuantity >= 0) p.setQuantity(updatedQuantity);
                        break;
                    }
                }
                if (updatedQuantity >= 0) productAdapter.updateSingleProductQuantity(updatedProductId, updatedQuantity);
            } else if (updatedLocalId > 0) {
                String pid = null;
                for (Product p : allProducts) {
                    if (p != null && p.getLocalId() == updatedLocalId) {
                        if (updatedQuantity >= 0) p.setQuantity(updatedQuantity);
                        pid = p.getProductId();
                        break;
                    }
                }
                for (Product p : filteredProducts) {
                    if (p != null && p.getLocalId() == updatedLocalId) {
                        if (updatedQuantity >= 0) p.setQuantity(updatedQuantity);
                        if (pid == null || pid.isEmpty()) pid = p.getProductId();
                        break;
                    }
                }
                if (pid != null && !pid.trim().isEmpty() && updatedQuantity >= 0) {
                    productAdapter.updateSingleProductQuantity(pid, updatedQuantity);
                } else {
                    productAdapter.submitSortedList(filteredProducts);
                }
            }
            if (deleted) {
                String deletedId = intent.getStringExtra("updatedProductId");
                long deletedLocal = intent.getLongExtra("updatedLocalId", 0L);
                if (deletedId != null && !deletedId.isEmpty()) {
                    for (int i = allProducts.size()-1; i>=0; i--) {
                        Product p = allProducts.get(i);
                        if (p != null && deletedId.equals(p.getProductId())) allProducts.remove(i);
                    }
                    for (int i = filteredProducts.size()-1; i>=0; i--) {
                        Product p = filteredProducts.get(i);
                        if (p != null && deletedId.equals(p.getProductId())) filteredProducts.remove(i);
                    }
                    productAdapter.removeByProductId(deletedId);
                } else if (deletedLocal > 0) {
                    String pidLocal = null;
                    for (int i = allProducts.size()-1; i>=0; i--) {
                        Product p = allProducts.get(i);
                        if (p != null && p.getLocalId() == deletedLocal) {
                            pidLocal = p.getProductId();
                            allProducts.remove(i);
                        }
                    }
                    for (int i = filteredProducts.size()-1; i>=0; i--) {
                        Product p = filteredProducts.get(i);
                        if (p != null && p.getLocalId() == deletedLocal) {
                            if (pidLocal == null) pidLocal = p.getProductId();
                            filteredProducts.remove(i);
                        }
                    }
                    if (pidLocal != null && !pidLocal.trim().isEmpty()) productAdapter.removeByProductId(pidLocal);
                    else productAdapter.submitSortedList(filteredProducts);
                }
            }
            updateHeaderStats();
        }
    }

    private final ViewTreeObserver.OnGlobalLayoutListener keyboardLayoutListener = new ViewTreeObserver.OnGlobalLayoutListener() {
        private int lastVisibleHeight = 0;
        @Override public void onGlobalLayout() {
            Rect r = new Rect();
            rootView.getWindowVisibleDisplayFrame(r);
            int visibleHeight = r.height();
            if (lastVisibleHeight == 0) lastVisibleHeight = visibleHeight;
            int diff = lastVisibleHeight - visibleHeight;
            if (diff > keyboardThresholdPx) hideBottomButtons();
            else if (diff < -keyboardThresholdPx) showBottomButtons();
            lastVisibleHeight = visibleHeight;
        }
    };

    private void listenToCategoriesForFilter() {
        categoryRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(DataSnapshot snapshot) {
                Set<String> productCategoryNames = new HashSet<>();
                for (Product p : allProducts) {
                    if (p == null) continue;
                    String ptype = p.getProductType() == null ? "" : p.getProductType();
                    if ("Menu".equalsIgnoreCase(ptype)) continue;
                    String cname = p.getCategoryName();
                    if (cname != null && !cname.trim().isEmpty()) productCategoryNames.add(cname.trim());
                }
                List<String> options = new ArrayList<>();
                options.add("All");
                Set<String> added = new HashSet<>();
                if (snapshot != null) {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        Category c = child.getValue(Category.class);
                        if (c == null) continue;
                        if (!c.isActive()) continue;
                        String type = c.getType();
                        if (type == null || type.isEmpty()) type = "Inventory";
                        if ("Menu".equalsIgnoreCase(type)) continue;
                        String name = c.getCategoryName();
                        if (name == null) continue;
                        name = name.trim();
                        if (name.isEmpty()) continue;
                        if (!productCategoryNames.isEmpty() && !productCategoryNames.contains(name)) continue;
                        if (added.add(name)) options.add(name);
                    }
                }
                if (options.size() == 1) {
                    if (snapshot != null) {
                        for (DataSnapshot child : snapshot.getChildren()) {
                            Category c = child.getValue(Category.class);
                            if (c == null) continue;
                            if (!c.isActive()) continue;
                            String type = c.getType();
                            if (type == null || type.isEmpty()) type = "Inventory";
                            if ("Menu".equalsIgnoreCase(type)) continue;
                            String name = c.getCategoryName();
                            if (name == null) continue;
                            name = name.trim();
                            if (name.isEmpty()) continue;
                            if (added.add(name)) options.add(name);
                        }
                    }
                }
                if (!productCategoryNames.isEmpty()) {
                    for (String pc : productCategoryNames) {
                        if (pc == null) continue;
                        String t = pc.trim();
                        if (t.isEmpty()) continue;
                        if (added.add(t)) options.add(t);
                    }
                }
                if (options.size() > 1) {
                    Collections.sort(options.subList(1, options.size()), String::compareToIgnoreCase);
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(Inventory.this, android.R.layout.simple_spinner_item, options);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerCategoryFilter.setAdapter(adapter);
                int idx = options.indexOf(currentCategoryFilter);
                if (idx < 0) idx = 0;
                spinnerCategoryFilter.setSelection(idx, false);
                spinnerCategoryFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        String selected = (String) parent.getItemAtPosition(position);
                        currentCategoryFilter = selected == null ? "All" : selected;
                        applyFilters();
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent) { }
                });
            }
            @Override public void onCancelled(DatabaseError error) { }
        });
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_DELETE_PRODUCT && resultCode == RESULT_OK) {
            if (data != null) {
                String deletedId = data.getStringExtra("deletedProductId");
                if (deletedId != null) {
                    for (int i = filteredProducts.size() - 1; i >= 0; i--) {
                        Product p = filteredProducts.get(i);
                        if (p != null && deletedId.equals(p.getProductId())) {
                            filteredProducts.remove(i);
                            productAdapter.submitSortedList(filteredProducts);
                            updateHeaderStats();
                            break;
                        }
                    }
                }
            }
        }
    }

    private void showArchiveConfirmDialog(Product p, int position) {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        b.setTitle("Delete product");
        b.setMessage("Archive and remove \"" + (p.getProductName() == null ? "this product" : p.getProductName()) + "\" from inventory?");
        b.setPositiveButton("Archive", (dialog, which) -> {
            String prodId = p.getProductId();
            if (prodId == null || prodId.isEmpty()) {
                Toast.makeText(Inventory.this, "Cannot archive: missing product id", Toast.LENGTH_SHORT).show();
                return;
            }
            productRepository.deleteProduct(prodId, new ProductRepository.OnProductDeletedListener() {
                @Override public void onProductDeleted(String archiveFilename) {
                    runOnUiThread(() -> {
                        for (int i = allProducts.size() - 1; i >= 0; i--) {
                            Product ap = allProducts.get(i);
                            if (ap != null && prodId.equals(ap.getProductId())) allProducts.remove(i);
                        }
                        for (int i = filteredProducts.size() - 1; i >= 0; i--) {
                            Product fp = filteredProducts.get(i);
                            if (fp != null && prodId.equals(fp.getProductId())) { filteredProducts.remove(i); break; }
                        }
                        productAdapter.removeByProductId(prodId);
                        productAdapter.submitSortedList(filteredProducts);
                        updateHeaderStats();
                        Toast.makeText(Inventory.this, "Product archived and removed", Toast.LENGTH_SHORT).show();
                    });
                }
                @Override public void onError(String error) { runOnUiThread(() -> Toast.makeText(Inventory.this, "Archive failed: " + error, Toast.LENGTH_LONG).show()); }
            });
        });
        b.setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss());
        b.show();
    }

    private void setupAdjustmentButtons() {
        if (btnAdjustStock != null) btnAdjustStock.setOnClickListener(v -> startActivity(new Intent(Inventory.this, StockAdjustmentActivity.class)));
        if (btnAdjustmentHistory != null) btnAdjustmentHistory.setOnClickListener(v -> startActivity(new Intent(Inventory.this, AdjustmentHistoryActivity.class)));
        if (btnAdjustmentSummary != null) btnAdjustmentSummary.setOnClickListener(v -> startActivity(new Intent(Inventory.this, AdjustmentSummaryReportActivity.class)));
        if (btnDeleteProduct != null) btnDeleteProduct.setOnClickListener(v -> startActivity(new Intent(Inventory.this, DeleteProductActivity.class)));
    }

    private void applyFilters() {
        String q = currentSearchQuery == null ? "" : currentSearchQuery.toLowerCase();
        String cat = currentCategoryFilter == null ? "All" : currentCategoryFilter;
        filteredProducts.clear();
        for (Product p : allProducts) {
            if (p == null || !p.isActive()) continue;
            String type = p.getProductType() == null ? "" : p.getProductType();
            if ("Menu".equalsIgnoreCase(type)) continue;
            boolean matchesSearch = q.isEmpty() || (p.getProductName() != null && p.getProductName().toLowerCase().contains(q)) || (p.getCategoryName() != null && p.getCategoryName().toLowerCase().contains(q));
            boolean matchesCategory = "All".equalsIgnoreCase(cat) || (p.getCategoryName() != null && p.getCategoryName().equalsIgnoreCase(cat));
            if (!matchesSearch || !matchesCategory) continue;
            if (showLowStockOnly) {
                boolean isCritical = p.isCriticalStock();
                boolean isLow = p.isLowStock();
                if (!isCritical && !isLow) continue;
            }
            if (showNearExpiryOnly) {
                long expiry = p.getExpiryDate();
                if (expiry <= 0) continue;
                long now = System.currentTimeMillis();
                long diffMillis = expiry - now;
                long days = diffMillis / (24L * 60L * 60L * 1000L);
                if (diffMillis > 0 && days > 7) continue;
            }
            filteredProducts.add(p);
        }
        productAdapter.submitSortedList(filteredProducts);
        updateEmptyState();
    }

    private void updateHeaderStats() {
        int total = 0;
        int lowOrCritical = 0;
        for (Product p : allProducts) {
            if (p == null || !p.isActive()) continue;
            String type = p.getProductType() == null ? "" : p.getProductType();
            if ("Menu".equalsIgnoreCase(type)) continue;
            total++;
            if (p.isCriticalStock() || p.isLowStock()) lowOrCritical++;
        }
        if (tvTotalCount != null) tvTotalCount.setText(String.valueOf(total));
        if (tvLowStockWarning != null) {
            if (lowOrCritical > 0) {
                tvLowStockWarning.setText(lowOrCritical + " alerts");
                tvLowStockWarning.setVisibility(View.VISIBLE);
            } else tvLowStockWarning.setVisibility(View.GONE);
        }
    }

    private void updateEmptyState() {
        if (filteredProducts.isEmpty()) {
            if (showLowStockOnly) emptyStateTV.setText("No low stock products found");
            else if (showNearExpiryOnly) emptyStateTV.setText("No near-expiry products found");
            else emptyStateTV.setText("No products found");
            emptyStateTV.setVisibility(View.VISIBLE);
        } else emptyStateTV.setVisibility(View.GONE);
    }

    private void hideBottomButtons() {
        if (bottomButtonLayout == null || buttonsHidden) return;
        bottomButtonLayout.animate().translationY(bottomButtonLayout.getHeight()).setDuration(220).start();
        buttonsHidden = true;
    }

    private void showBottomButtons() {
        if (bottomButtonLayout == null || !buttonsHidden) return;
        bottomButtonLayout.animate().translationY(0).setDuration(220).start();
        buttonsHidden = false;
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (productRepository != null && criticalListener != null) productRepository.unregisterCriticalStockListener(criticalListener);
        if (rootView != null && keyboardLayoutListener != null) rootView.getViewTreeObserver().removeOnGlobalLayoutListener(keyboardLayoutListener);
    }
}