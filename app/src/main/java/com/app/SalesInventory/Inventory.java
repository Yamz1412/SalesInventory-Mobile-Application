package com.app.SalesInventory;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Inventory extends BaseActivity {
    public static final String EXTRA_SHOW_LOW_STOCK_ONLY = "showLowStockOnly";
    public static final String EXTRA_SHOW_NEAR_EXPIRY_ONLY = "showNearExpiryOnly";

    private RecyclerView productsRecyclerView;
    private SearchView searchView;
    private Spinner spinnerProductLineFilter;
    private TextView emptyStateTV;
    private ProductAdapter productAdapter;
    private List<Product> allProducts = new ArrayList<>();
    private List<Product> filteredProducts = new ArrayList<>();
    private ProductRepository productRepository;
    private AuthManager authManager;
    private Button btnAddProduct;
    private Button btnAdjustStock;
    private Spinner spinnerCategoryFilter;
    private String currentSearchQuery = "";
    private String currentCategoryFilter = "All";
    private String currentProductLineFilter = "All Lines";
    private CriticalStockNotifier criticalNotifier;
    private ProductRepository.OnCriticalStockListener criticalListener;
    private boolean showLowStockOnly = false;
    private boolean showNearExpiryOnly = false;
    private TextView tvTotalCount, tvLowStockWarning;

    private Button btnArchive;

    private DatabaseReference categoryRef;
    private ValueEventListener categoryListener;

    private boolean isReadOnly = false;
    private Spinner spinnerSort;
    private String currentSortOption = "Alphabetical (A-Z)";
    private View layoutArchiveContainer;
    private TextView tvArchiveBadge;
    private com.google.firebase.firestore.ListenerRegistration archiveListener;

    private com.google.android.material.switchmaterial.SwitchMaterial switchShowOutOfStock;
    private boolean showOutOfStock = true;

    private ActionMode actionMode;

    private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu) {
            if (isReadOnly) return false;

            menu.add(0, 1, 0, "Edit").setIcon(android.R.drawable.ic_menu_edit).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            menu.add(0, 2, 0, "Delete").setIcon(android.R.drawable.ic_menu_delete).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
            return true;
        }

        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
            Set<String> selectedIds = productAdapter.getSelectedIds();
            mode.setTitle(selectedIds.size() + " Selected");
            menu.findItem(1).setVisible(selectedIds.size() == 1);
            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
            Set<String> selectedIds = productAdapter.getSelectedIds();

            if (item.getItemId() == 1) {
                if (selectedIds.size() == 1) {
                    String id = selectedIds.iterator().next();
                    Intent i = new Intent(Inventory.this, EditProduct.class);
                    i.putExtra("PRODUCT_ID", id);
                    startActivity(i);
                    mode.finish();
                }
                return true;
            } else if (item.getItemId() == 2) {
                new android.app.AlertDialog.Builder(Inventory.this)
                        .setTitle("Delete Selected")
                        .setMessage("Are you sure you want to delete " + selectedIds.size() + " selected items?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            for (String id : selectedIds) {
                                productRepository.deleteProduct(id, new ProductRepository.OnProductDeletedListener() {
                                    @Override public void onProductDeleted(String message) {}
                                    @Override public void onError(String error) {}
                                });
                            }
                            Toast.makeText(Inventory.this, "Items Deleted Successfully", Toast.LENGTH_SHORT).show();
                            mode.finish();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
                return true;
            }
            return false;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode) {
            productAdapter.clearSelection();
            productAdapter.setSelectionMode(false);
            actionMode = null;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        authManager = AuthManager.getInstance();
        productRepository = SalesInventoryApplication.getProductRepository();

        spinnerProductLineFilter = findViewById(R.id.spinnerProductLineFilter);
        spinnerSort = findViewById(R.id.spinnerSort);
        setupSortSpinner();

        productsRecyclerView = findViewById(R.id.productsRecyclerView);
        searchView = findViewById(R.id.searchView);
        emptyStateTV = findViewById(R.id.emptyStateTV);
        btnAddProduct = findViewById(R.id.btn_add_product);
        btnAdjustStock = findViewById(R.id.btn_adjust_stock);
        spinnerCategoryFilter = findViewById(R.id.spinnerCategoryFilter);
        tvTotalCount = findViewById(R.id.TotalCountTV);
        tvLowStockWarning = findViewById(R.id.tvLowStockWarning);
        btnArchive = findViewById(R.id.btn_archive);
        layoutArchiveContainer = findViewById(R.id.layout_archive_container);
        tvArchiveBadge = findViewById(R.id.tvArchiveBadge);

        switchShowOutOfStock = findViewById(R.id.switchShowOutOfStock);
        if (switchShowOutOfStock != null) {
            switchShowOutOfStock.setChecked(false);
            showOutOfStock = false;
            switchShowOutOfStock.setOnCheckedChangeListener((buttonView, isChecked) -> {
                showOutOfStock = isChecked;
                applyFilters();
            });
        }

        showLowStockOnly = getIntent().getBooleanExtra(EXTRA_SHOW_LOW_STOCK_ONLY, false);
        showNearExpiryOnly = getIntent().getBooleanExtra(EXTRA_SHOW_NEAR_EXPIRY_ONLY, false);

        isReadOnly = getIntent().getBooleanExtra("readonly", false);

        if (isReadOnly) {
            if (btnAddProduct != null) btnAddProduct.setVisibility(View.GONE);
            if (btnAdjustStock != null) btnAdjustStock.setVisibility(View.GONE);
            if (btnArchive != null) btnArchive.setVisibility(View.GONE);
            if (layoutArchiveContainer != null) layoutArchiveContainer.setVisibility(View.GONE);
        }

        productAdapter = new ProductAdapter(filteredProducts, this);
        productAdapter.setReadOnly(isReadOnly);

        productAdapter.setSelectionListener(selectedIds -> {
            if (selectedIds.isEmpty()) {
                if (actionMode != null) {
                    actionMode.finish();
                }
            } else {
                if (actionMode == null) {
                    actionMode = startActionMode(actionModeCallback);
                } else {
                    actionMode.invalidate();
                }
            }
        });

        productsRecyclerView.setLayoutManager(new GridLayoutManager(this, getResponsiveSpanCount()));
        productsRecyclerView.setAdapter(productAdapter);

        authManager.refreshCurrentUserStatus(success -> runOnUiThread(() -> {
            boolean hasAccess = authManager.hasManagerAccess();
            isReadOnly = !hasAccess;

            if (productAdapter != null) productAdapter.setReadOnly(isReadOnly);

            if (isReadOnly) {
                if (btnAddProduct != null) btnAddProduct.setVisibility(View.GONE);
                if (btnAdjustStock != null) btnAdjustStock.setVisibility(View.GONE);
                if (layoutArchiveContainer != null) layoutArchiveContainer.setVisibility(View.GONE);
                if (btnArchive != null) btnArchive.setVisibility(View.GONE);
            } else {
                if (btnAddProduct != null) btnAddProduct.setVisibility(View.VISIBLE);
                if (btnAdjustStock != null) btnAdjustStock.setVisibility(View.VISIBLE);
                listenToArchivedProductsCount();
            }
        }));

        setupActionButtons();

        categoryRef = FirebaseDatabase.getInstance().getReference("Categories");

        setupSearchView();

        productRepository.getAllProducts().observe(this, products -> {
            if (products != null && !products.isEmpty()) {
                allProducts.clear();
                allProducts.addAll(products);
                updateHeaderStats();
                updateDynamicMainCategories();
                applyFilters();
            } else {
                fetchInventoryFromFirestore();
            }
        });

        criticalNotifier = CriticalStockNotifier.getInstance();
        criticalListener = product -> runOnUiThread(() ->
                criticalNotifier.showCriticalDialog(this, product)
        );
        productRepository.registerCriticalStockListener(criticalListener);
    }

    private void listenToArchivedProductsCount() {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = AuthManager.getInstance().getCurrentUserId();
        }
        if (ownerId == null) return;

        archiveListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(ownerId).collection("products")
                .whereEqualTo("isActive", false)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    if (snapshot != null) {
                        int archiveCount = snapshot.size();
                        runOnUiThread(() -> {
                            boolean shouldShow = (archiveCount > 0 && !isReadOnly);

                            if (layoutArchiveContainer != null) {
                                layoutArchiveContainer.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
                            }
                            if (btnArchive != null) {
                                btnArchive.setVisibility(shouldShow ? View.VISIBLE : View.GONE);
                            }
                            if (tvArchiveBadge != null) {
                                tvArchiveBadge.setText(String.valueOf(archiveCount));
                                tvArchiveBadge.setVisibility(archiveCount > 0 ? View.VISIBLE : View.GONE);
                            }
                        });
                    }
                });
    }

    // FIX: Adaptive adapter now sets a background color so text doesn't blend
    private ArrayAdapter<String> getAdaptiveAdapter(List<String> items) {
        boolean isDark = false;
        try { isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark"); } catch (Exception e){}
        int textColor = isDark ? Color.WHITE : Color.BLACK;
        int bgColor = isDark ? Color.parseColor("#2C2C2C") : Color.WHITE;

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
                view.setBackgroundColor(bgColor);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void setupSortSpinner() {
        List<String> sortOptions = Arrays.asList(
                "Alphabetical (A-Z)",
                "Alphabetical (Z-A)",
                "Stock: High to Low",
                "Stock: Low to High",
                "Earliest Expiry",
                "Recently Added"
        );

        ArrayAdapter<String> adapter = getAdaptiveAdapter(sortOptions);
        spinnerSort.setAdapter(adapter);

        spinnerSort.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                currentSortOption = sortOptions.get(position);
                applyFilters();
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void fetchInventoryFromFirestore() {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = AuthManager.getInstance().getCurrentUserId();
        }
        if (ownerId == null) return;

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(ownerId).collection("products")
                .whereEqualTo("isActive", true)
                .get()
                .addOnSuccessListener(snapshot -> {
                    allProducts.clear();
                    ProductRepository repo = SalesInventoryApplication.getProductRepository();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                        Product p = doc.toObject(Product.class);
                        if (p != null) {
                            p.setProductId(doc.getId());
                            p.setActive(true);
                            allProducts.add(p);
                            repo.upsertFromRemote(p);
                        }
                    }
                    repo.refreshProducts();
                    runOnUiThread(() -> {
                        updateHeaderStats();
                        applyFilters();
                    });
                })
                .addOnFailureListener(e ->
                        runOnUiThread(() ->
                                Toast.makeText(this, "Could not load products", Toast.LENGTH_SHORT).show()
                        )
                );
    }

    private String normalizeCategoryName(String name) {
        if (name == null || name.trim().isEmpty()) return "uncategorized";
        return name.toLowerCase().replaceAll("\\s+", "");
    }

    private void updateDynamicMainCategories() {
        java.util.Map<String, String> mainCatMap = new java.util.HashMap<>();
        mainCatMap.put("alllines", "All Lines");

        for (Product p : allProducts) {
            if (p == null || !p.isActive() || p.isSellable() || "finished".equalsIgnoreCase(p.getProductType()) || "Menu".equalsIgnoreCase(p.getProductType())) continue;

            String mainCat = p.getProductLine() != null && !p.getProductLine().trim().isEmpty() ? p.getProductLine().trim() : "Uncategorized";
            String normKey = normalizeCategoryName(mainCat);

            if (!mainCatMap.containsKey(normKey)) {
                mainCatMap.put(normKey, mainCat);
            }
        }

        List<String> displayList = new ArrayList<>(mainCatMap.values());
        Collections.sort(displayList, (a, b) -> {
            if (a.equals("All Lines")) return -1;
            if (b.equals("All Lines")) return 1;
            return a.compareToIgnoreCase(b);
        });

        ArrayAdapter<String> mainAdapter = getAdaptiveAdapter(displayList);
        if (spinnerProductLineFilter != null) {
            spinnerProductLineFilter.setAdapter(mainAdapter);
            int spinnerPosition = mainAdapter.getPosition(currentProductLineFilter);
            spinnerProductLineFilter.setSelection(Math.max(spinnerPosition, 0));

            spinnerProductLineFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    currentProductLineFilter = (String) parent.getItemAtPosition(position);
                    updateDynamicSubCategories();
                    applyFilters();
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    private void updateDynamicSubCategories() {
        java.util.Map<String, String> subCatMap = new java.util.HashMap<>();
        subCatMap.put("all", "All");

        String currentMainNorm = normalizeCategoryName(currentProductLineFilter);

        for (Product p : allProducts) {
            if (p == null || !p.isActive() || p.isSellable() || "finished".equalsIgnoreCase(p.getProductType()) || "Menu".equalsIgnoreCase(p.getProductType())) continue;

            String mainCat = p.getProductLine() != null && !p.getProductLine().trim().isEmpty() ? p.getProductLine().trim() : "Uncategorized";
            String pMainNorm = normalizeCategoryName(mainCat);

            if (currentMainNorm.equals("alllines") || currentMainNorm.equals(pMainNorm)) {
                String subCat = p.getCategoryName() != null && !p.getCategoryName().trim().isEmpty() ? p.getCategoryName().trim() : "Uncategorized";
                String normKey = normalizeCategoryName(subCat);

                if (!subCatMap.containsKey(normKey)) {
                    subCatMap.put(normKey, subCat);
                }
            }
        }

        List<String> displayList = new ArrayList<>(subCatMap.values());
        Collections.sort(displayList, (a, b) -> {
            if (a.equals("All")) return -1;
            if (b.equals("All")) return 1;
            return a.compareToIgnoreCase(b);
        });

        ArrayAdapter<String> subAdapter = getAdaptiveAdapter(displayList);
        if (spinnerCategoryFilter != null) {
            spinnerCategoryFilter.setAdapter(subAdapter);
            currentCategoryFilter = "All";
            spinnerCategoryFilter.setSelection(0);

            spinnerCategoryFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    currentCategoryFilter = (String) parent.getItemAtPosition(position);
                    applyFilters();
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    private void setupSearchView() {
        int id = searchView.getContext().getResources().getIdentifier("android:id/search_src_text", null, null);
        android.widget.TextView searchEditText = searchView.findViewById(id);

        if (searchEditText != null) {
            boolean isDark = false;
            try { isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark"); } catch(Exception e){}
            searchEditText.setTextColor(isDark ? Color.WHITE : Color.BLACK);
            searchEditText.setHintTextColor(Color.GRAY);
        }

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentSearchQuery = query;
                applyFilters();
                searchView.clearFocus();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentSearchQuery = newText;
                applyFilters();
                return true;
            }
        });

        searchView.setOnClickListener(v -> searchView.setIconified(false));
    }

    private void setupActionButtons() {
        // 1. Forcefully hide the buttons immediately if it's a staff account
        if (isReadOnly) {
            if (btnAddProduct != null) btnAddProduct.setVisibility(View.GONE);
            if (btnAdjustStock != null) btnAdjustStock.setVisibility(View.GONE);
            if (btnArchive != null) btnArchive.setVisibility(View.GONE);
            if (layoutArchiveContainer != null) layoutArchiveContainer.setVisibility(View.GONE);
        }

        // 2. Add strict click-blocks just in case they somehow become visible
        if (btnAddProduct != null) {
            btnAddProduct.setOnClickListener(v -> {
                if (isReadOnly) {
                    Toast.makeText(Inventory.this, "Admin access required", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(Inventory.this, AddProductActivity.class));
            });
        }

        if (btnAdjustStock != null) {
            btnAdjustStock.setOnClickListener(v -> {
                if (isReadOnly) {
                    Toast.makeText(Inventory.this, "Admin access required", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(Inventory.this, StockAdjustmentActivity.class));
            });
        }

        if (btnArchive != null) {
            btnArchive.setOnClickListener(v -> {
                if (isReadOnly) {
                    Toast.makeText(Inventory.this, "Admin access required", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(Inventory.this, DeleteProductActivity.class));
            });
        }
    }

    private void applyFilters() {
        String cat = currentCategoryFilter == null ? "All" : currentCategoryFilter.trim();
        String line = currentProductLineFilter == null ? "All Lines" : currentProductLineFilter.trim();
        String q = currentSearchQuery == null ? "" : currentSearchQuery.toLowerCase().trim();

        filteredProducts.clear();

        for (Product p : allProducts) {
            if (p == null || !p.isActive() || p.isSellable() || "finished".equalsIgnoreCase(p.getProductType()) || "Menu".equalsIgnoreCase(p.getProductType())) continue;

            String pName = p.getProductName() != null ? p.getProductName().trim().toLowerCase() : "";

            String pCat = p.getCategoryName() != null ? p.getCategoryName().trim() : "Uncategorized";
            String pLine = p.getProductLine() != null ? p.getProductLine().trim() : "Uncategorized";

            boolean matchesSearch = q.isEmpty() || pName.contains(q);

            String pCatNorm = normalizeCategoryName(pCat);
            String pLineNorm = normalizeCategoryName(pLine);
            String filterCatNorm = normalizeCategoryName(cat);
            String filterLineNorm = normalizeCategoryName(line);

            boolean isCatAll = filterCatNorm.equals("all") || filterCatNorm.equals("alllines") || filterCatNorm.isEmpty();
            boolean isLineAll = filterLineNorm.equals("alllines") || filterLineNorm.equals("all") || filterLineNorm.isEmpty();

            boolean matchesCategory = isCatAll || pCatNorm.equals(filterCatNorm);
            boolean matchesProductLine = isLineAll || pLineNorm.equals(filterLineNorm);

            if (matchesSearch && matchesCategory && matchesProductLine) {

                if (showOutOfStock) {
                    if (p.getQuantity() > 0) continue;
                } else {
                    if (!showLowStockOnly && p.getQuantity() <= 0) continue;
                }

                if (showLowStockOnly && (!p.isCriticalStock() && !p.isLowStock())) continue;

                if (showNearExpiryOnly) {
                    long expiry = p.getExpiryDate() != null ? p.getExpiryDate().getTime() : 0L;
                    if (expiry <= 0) continue;
                    long days = (expiry - System.currentTimeMillis()) / (24L * 60L * 60L * 1000L);
                    if (days > 7) continue;
                }

                filteredProducts.add(p);
            }
        }

        Collections.sort(filteredProducts, (p1, p2) -> {
            switch (currentSortOption) {
                case "Alphabetical (A-Z)":
                    return p1.getProductName().compareToIgnoreCase(p2.getProductName());
                case "Alphabetical (Z-A)":
                    return p2.getProductName().compareToIgnoreCase(p1.getProductName());
                case "Stock: High to Low":
                    return Double.compare(p2.getQuantity(), p1.getQuantity());
                case "Stock: Low to High":
                    return Double.compare(p1.getQuantity(), p2.getQuantity());
                case "Earliest Expiry":
                    long e1 = (p1.getExpiryDate() == null || p1.getExpiryDate().getTime() <= 0) ? Long.MAX_VALUE : p1.getExpiryDate().getTime();
                    long e2 = (p2.getExpiryDate() == null || p2.getExpiryDate().getTime() <= 0) ? Long.MAX_VALUE : p2.getExpiryDate().getTime();

                    return Long.compare(e1, e2);
                case "Recently Added":
                    return Long.compare(p2.getDateAdded(), p1.getDateAdded());
                default:
                    return p1.getProductName().compareToIgnoreCase(p2.getProductName());
            }
        });

        productAdapter.updateProducts(filteredProducts);
        updateEmptyState();
    }

    private int getResponsiveSpanCount() {
        android.content.res.Configuration config = getResources().getConfiguration();
        int screenWidthDp = config.screenWidthDp;
        boolean isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;

        boolean isTablet = screenWidthDp >= 600;

        if (isTablet) {
            return isLandscape ? (screenWidthDp >= 900 ? 4 : 3) : 2;
        } else {
            return isLandscape ? 2 : 1;
        }
    }

    private void updateHeaderStats() {
        int total = 0;
        int lowOrCritical = 0;

        for (Product p : allProducts) {
            if (p == null || !p.isActive() || p.isSellable() || "finished".equalsIgnoreCase(p.getProductType()) || "Menu".equalsIgnoreCase(p.getProductType())) continue;

            total++;
            if (p.isCriticalStock() || p.isLowStock()) {
                lowOrCritical++;
            }
        }

        if (tvTotalCount != null) {
            tvTotalCount.setText(String.valueOf(total));
        }

        if (tvLowStockWarning != null) {
            if (lowOrCritical > 0) {
                tvLowStockWarning.setText(lowOrCritical + " alerts");
                tvLowStockWarning.setVisibility(View.VISIBLE);
            } else {
                tvLowStockWarning.setVisibility(View.GONE);
            }
        }
    }

    private void updateEmptyState() {
        if (filteredProducts.isEmpty()) {
            if (showLowStockOnly) emptyStateTV.setText("No low stock products found");
            else if (showNearExpiryOnly) emptyStateTV.setText("No near-expiry products found");
            else emptyStateTV.setText("No products found");
            emptyStateTV.setVisibility(View.VISIBLE);
        } else {
            emptyStateTV.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (categoryListener != null && categoryRef != null) categoryRef.removeEventListener(categoryListener);
        if (productRepository != null && criticalListener != null) productRepository.unregisterCriticalStockListener(criticalListener);
        if (archiveListener != null) archiveListener.remove();
    }
}