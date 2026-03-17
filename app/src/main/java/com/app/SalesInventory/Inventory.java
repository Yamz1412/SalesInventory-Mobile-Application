package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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

    private DatabaseReference categoryRef;
    private ValueEventListener categoryListener;

    private boolean isReadOnly = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);

        authManager = AuthManager.getInstance();
        productRepository = SalesInventoryApplication.getProductRepository();

        spinnerProductLineFilter = findViewById(R.id.spinnerProductLineFilter);
        listenToProductLinesForFilter();

        productsRecyclerView = findViewById(R.id.productsRecyclerView);
        searchView = findViewById(R.id.searchView);
        emptyStateTV = findViewById(R.id.emptyStateTV);
        btnAddProduct = findViewById(R.id.btn_add_product);
        btnAdjustStock = findViewById(R.id.btn_adjust_stock);
        spinnerCategoryFilter = findViewById(R.id.spinnerCategoryFilter);
        tvTotalCount = findViewById(R.id.TotalCountTV);
        tvLowStockWarning = findViewById(R.id.tvLowStockWarning);

        showLowStockOnly = getIntent().getBooleanExtra(EXTRA_SHOW_LOW_STOCK_ONLY, false);
        showNearExpiryOnly = getIntent().getBooleanExtra(EXTRA_SHOW_NEAR_EXPIRY_ONLY, false);

        isReadOnly = getIntent().getBooleanExtra("readonly", false);

        productAdapter = new ProductAdapter(filteredProducts, this);
        productAdapter.setReadOnly(isReadOnly);

        productsRecyclerView.setLayoutManager(new GridLayoutManager(this, 3));
        productsRecyclerView.setAdapter(productAdapter);

        authManager.refreshCurrentUserStatus(success -> runOnUiThread(() -> {
            boolean isRealAdmin = authManager.isCurrentUserAdmin();
            if (!isRealAdmin || isReadOnly) {
                if (btnAddProduct != null) btnAddProduct.setVisibility(View.GONE);
                if (btnAdjustStock != null) btnAdjustStock.setVisibility(View.GONE);
            } else {
                if (btnAddProduct != null) btnAddProduct.setVisibility(View.VISIBLE);
                if (btnAdjustStock != null) btnAdjustStock.setVisibility(View.VISIBLE);
            }
        }));

        setupActionButtons();

        categoryRef = FirebaseDatabase.getInstance().getReference("Categories");

        setupSearchView();
        listenToCategoriesForFilter();

        // REPLACE lines 108-115 in onCreate():
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null && !products.isEmpty()) {
                allProducts.clear();
                allProducts.addAll(products);
                updateHeaderStats();
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

    // ADD this new method to Inventory.java:
    private void fetchInventoryFromFirestore() {
        String ownerId = AuthManager.getInstance().getCurrentUserId();
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
                            repo.upsertFromRemote(p); // saves to Room for next time
                        }
                    }
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

    private void listenToProductLinesForFilter() {
        String currentUserId = AuthManager.getInstance().getCurrentUserId();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("ProductLines");

        ref.orderByChild("ownerAdminId").equalTo(currentUserId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> lines = new ArrayList<>();
                lines.add("All Lines");
                lines.add("Core Products"); lines.add("Specialty / Unique Offerings");
                lines.add("Complementary Goods"); lines.add("Retail / Merchandise");
                lines.add("Seasonal Items"); lines.add("Grab-and-Go");

                for (DataSnapshot ds : snapshot.getChildren()) {
                    String lineName = ds.child("lineName").getValue(String.class);
                    if (lineName != null && !lines.contains(lineName)) lines.add(lineName);
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(Inventory.this, android.R.layout.simple_spinner_item, lines);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                if (spinnerProductLineFilter != null) {
                    spinnerProductLineFilter.setAdapter(adapter);
                    spinnerProductLineFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                        @Override
                        public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                            currentProductLineFilter = (String) parent.getItemAtPosition(position);
                            applyFilters();
                        }
                        @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                    });
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupSearchView() {
        int id = searchView.getContext().getResources().getIdentifier("android:id/search_src_text", null, null);
        android.widget.TextView searchEditText = searchView.findViewById(id);

        if (searchEditText != null) {
            searchEditText.setFilters(new android.text.InputFilter[]{
                    (source, start, end, dest, dstart, dend) -> {
                        if (source.toString().matches("^[a-zA-Z\\s]*$")) {
                            return null;
                        }
                        return source.toString().replaceAll("[^a-zA-Z\\s]", "");
                    }
            });
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
        if (btnAddProduct != null) {
            btnAddProduct.setOnClickListener(v -> startActivity(new Intent(Inventory.this, AddProductActivity.class)));
        }

        if (btnAdjustStock != null) {
            btnAdjustStock.setOnClickListener(v -> startActivity(new Intent(Inventory.this, StockAdjustmentActivity.class)));
        }
    }

    private void listenToCategoriesForFilter() {
        String currentUserId = AuthManager.getInstance().getCurrentUserId();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Categories");

        if (categoryListener != null) ref.removeEventListener(categoryListener);

        categoryListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> options = new ArrayList<>();
                options.add("All");

                for (DataSnapshot child : snapshot.getChildren()) {
                    Category c = child.getValue(Category.class);
                    if (c != null && c.isActive() &&
                            currentUserId.equals(c.getOwnerAdminId()) &&
                            !"Menu".equalsIgnoreCase(c.getType())) {
                        options.add(c.getCategoryName());
                    }
                }

                ArrayAdapter<String> adapter = new ArrayAdapter<>(Inventory.this,
                        android.R.layout.simple_spinner_item, options);
                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerCategoryFilter.setAdapter(adapter);

                spinnerCategoryFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                        currentCategoryFilter = (String) parent.getItemAtPosition(position);
                        applyFilters();
                    }
                    @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(Inventory.this, "Error loading categories", Toast.LENGTH_SHORT).show();
            }
        };
        ref.addValueEventListener(categoryListener);
    }

    private void setupCategoryFilterSpinnerFallback() {
        Set<String> categories = new HashSet<>();
        for (Product p : allProducts) {
            if (p == null || !p.isActive()) continue;
            String type = p.getProductType() == null ? "" : p.getProductType();
            if ("Menu".equalsIgnoreCase(type)) continue;
            String c = p.getCategoryName();
            if (c != null && !c.isEmpty()) categories.add(c);
        }
        List<String> options = new ArrayList<>();
        options.add("All");
        options.addAll(categories);

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategoryFilter.setAdapter(adapter);

        int index = options.indexOf(currentCategoryFilter);
        if (index < 0) index = 0;
        spinnerCategoryFilter.setSelection(index);

        spinnerCategoryFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selected = (String) parent.getItemAtPosition(position);
                currentCategoryFilter = selected == null ? "All" : selected;
                applyFilters();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
    }

    private void applyFilters() {
        String cat = currentCategoryFilter == null ? "All" : currentCategoryFilter;
        String line = currentProductLineFilter == null ? "All Lines" : currentProductLineFilter;
        String q = currentSearchQuery == null ? "" : currentSearchQuery.toLowerCase().trim();

        filteredProducts.clear();
        for (Product p : allProducts) {
            if (p == null || !p.isActive()) continue;

            String type = p.getProductType() == null ? "" : p.getProductType();
            if ("Menu".equalsIgnoreCase(type)) continue;

            boolean matchesSearch = q.isEmpty()
                    || p.getProductName().toLowerCase().contains(q)
                    || (p.getCategoryName() != null && p.getCategoryName().toLowerCase().contains(q));

            boolean matchesCategory = "All".equalsIgnoreCase(cat)
                    || (p.getCategoryName() != null && p.getCategoryName().equalsIgnoreCase(cat));

            boolean matchesProductLine = "All Lines".equalsIgnoreCase(line)
                    || p.getProductLine() == null        // ← null = show under all lines
                    || p.getProductLine().isEmpty()      // ← empty = show under all lines
                    || p.getProductLine().equalsIgnoreCase(line);

            if (!matchesSearch || !matchesCategory || !matchesProductLine) continue;

            if (showLowStockOnly) {
                if (!p.isCriticalStock() && !p.isLowStock()) continue;
            }

            if (showNearExpiryOnly) {
                long expiry = p.getExpiryDate();
                if (expiry <= 0) continue;
                long now = System.currentTimeMillis();
                long days = (expiry - now) / (24L * 60L * 60L * 1000L);
                if (expiry - now > 0 && days > 7) continue;
            }

            filteredProducts.add(p);
        }

        Collections.sort(filteredProducts, (p1, p2) -> {
            String n1 = p1.getProductName() != null ? p1.getProductName() : "";
            String n2 = p2.getProductName() != null ? p2.getProductName() : "";
            return n1.compareToIgnoreCase(n2);
        });

        productAdapter.updateProducts(filteredProducts);
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
            if (p.isCriticalStock() || p.isLowStock()) {
                lowOrCritical++;
            }
        }

        if (tvTotalCount != null) {
            tvTotalCount.setText(String.valueOf(total));
            tvTotalCount.setOnLongClickListener(v -> {
                MockDataInjector.injectHanZaiDefenseData(Inventory.this, () -> {
                    runOnUiThread(() -> fetchInventoryFromFirestore());
                });
                return true;
            });
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
    }
}