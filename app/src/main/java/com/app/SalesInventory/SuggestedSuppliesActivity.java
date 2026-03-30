package com.app.SalesInventory;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
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

public class SuggestedSuppliesActivity extends BaseActivity {

    private RecyclerView rvSuggestedSupplies;
    private SuggestedAdapter adapter;
    private ProductRepository productRepository;
    private String ownerId;

    private EditText etSearchCatalog;
    private Spinner spinnerCatalogFilter;

    private List<CatalogItem> masterCatalog = new ArrayList<>();
    private List<CatalogItem> filteredCatalog = new ArrayList<>();
    private List<String> existingSuppliers = new ArrayList<>();

    // UPGRADED: Added Supplier and Brand fields
    public static class CatalogItem {
        String name;
        String category;
        String unit;
        double defaultCost;
        String supplier;
        String brand;

        public CatalogItem(String name, String category, String unit, double defaultCost, String supplier, String brand) {
            this.name = name;
            this.category = category;
            this.unit = unit;
            this.defaultCost = defaultCost;
            this.supplier = supplier;
            this.brand = brand;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_suggested_supplies);

        Toolbar toolbar = findViewById(R.id.toolbarSuggested);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        productRepository = SalesInventoryApplication.getProductRepository();
        ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();

        rvSuggestedSupplies = findViewById(R.id.rvSuggestedSupplies);
        rvSuggestedSupplies.setLayoutManager(new LinearLayoutManager(this));

        etSearchCatalog = findViewById(R.id.etSearchCatalog);
        spinnerCatalogFilter = findViewById(R.id.spinnerCatalogFilter);

        adapter = new SuggestedAdapter(filteredCatalog);
        rvSuggestedSupplies.setAdapter(adapter);

        // FIX: Adaptive Search Text Color
        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        etSearchCatalog.setTextColor(isDark ? Color.WHITE : Color.BLACK);
        etSearchCatalog.setHintTextColor(Color.GRAY);

        loadCatalogFromFirebase();
        loadExistingSuppliers();
    }

    // ================================================================
    // FIX: Adaptive Dropdown Adapter for Light/Dark Theme Spinners
    // ================================================================
    private ArrayAdapter<String> getAdaptiveAdapter(List<String> items) {
        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;

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
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void setupFilters() {
        Set<String> categories = new HashSet<>();
        categories.add("All Categories");
        for (CatalogItem item : masterCatalog) categories.add(item.category);

        List<String> catList = new ArrayList<>(categories);
        Collections.sort(catList);
        catList.remove("All Categories");
        catList.add(0, "All Categories");

        ArrayAdapter<String> catAdapter = getAdaptiveAdapter(catList);
        spinnerCatalogFilter.setAdapter(catAdapter);

        etSearchCatalog.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { applyFilters(); }
        });

        spinnerCatalogFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { applyFilters(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void applyFilters() {
        String query = etSearchCatalog.getText().toString().toLowerCase().trim();
        String cat = spinnerCatalogFilter.getSelectedItem() != null ? spinnerCatalogFilter.getSelectedItem().toString() : "All Categories";

        filteredCatalog.clear();
        for (CatalogItem item : masterCatalog) {
            boolean matchesSearch = item.name.toLowerCase().contains(query);
            boolean matchesCat = cat.equals("All Categories") || item.category.equals(cat);

            if (matchesSearch && matchesCat) filteredCatalog.add(item);
        }
        adapter.notifyDataSetChanged();
    }

    private void loadExistingSuppliers() {
        DatabaseReference supplierRef = FirebaseDatabase.getInstance().getReference("Suppliers");
        supplierRef.orderByChild("ownerAdminId").equalTo(ownerId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        existingSuppliers.clear();
                        existingSuppliers.add("No Specific Supplier");
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String name = ds.child("name").getValue(String.class);
                            if (name != null) existingSuppliers.add(name);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    // UPGRADED: Connects to "supplierProducts" node directly!
    private void loadCatalogFromFirebase() {
        DatabaseReference catalogRef = FirebaseDatabase.getInstance().getReference("supplierProducts");
        catalogRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                masterCatalog.clear();
                if (snapshot.exists() && snapshot.getChildrenCount() > 0) {
                    for (DataSnapshot ds : snapshot.getChildren()) {
                        String name = ds.child("name").getValue(String.class);
                        String category = ds.child("category").getValue(String.class);
                        String unit = ds.child("unit").getValue(String.class);
                        Double cost = ds.child("cost").getValue(Double.class);
                        if (cost == null) cost = ds.child("defaultCost").getValue(Double.class);

                        String supplier = ds.child("supplier").getValue(String.class);
                        String brand = ds.child("brand").getValue(String.class);

                        if (name != null) {
                            masterCatalog.add(new CatalogItem(
                                    name,
                                    category != null ? category : "Uncategorized",
                                    unit != null ? unit : "pcs",
                                    cost != null ? cost : 0.0,
                                    supplier != null ? supplier : "Global Supplier",
                                    brand != null ? brand : "N/A"
                            ));
                        }
                    }
                } else {
                    // Fallback to Mock Data if Firebase node is empty
                    masterCatalog = loadMockCoffeeShopCatalog();
                }

                filteredCatalog.clear();
                filteredCatalog.addAll(masterCatalog);
                setupFilters();
                applyFilters();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(SuggestedSuppliesActivity.this, "Failed to load catalog", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private List<CatalogItem> loadMockCoffeeShopCatalog() {
        List<CatalogItem> list = new ArrayList<>();
        list.add(new CatalogItem("Arabica Beans (1kg)", "Coffee Beans", "kg", 850.0, "Bean Crafters Co.", "Premium Roast"));
        list.add(new CatalogItem("Robusta Beans (1kg)", "Coffee Beans", "kg", 650.0, "Bean Crafters Co.", "Classic Roast"));
        list.add(new CatalogItem("Whole Milk (1L)", "Dairy", "L", 95.0, "Daily Dairy Suppliers", "Nestle"));
        list.add(new CatalogItem("Oat Milk (1L)", "Dairy", "L", 150.0, "Daily Dairy Suppliers", "Oatside"));
        list.add(new CatalogItem("Vanilla Syrup (750ml)", "Syrups", "ml", 350.0, "Sweet Syrups Inc.", "Torani"));
        list.add(new CatalogItem("Caramel Sauce (1L)", "Syrups", "ml", 400.0, "Sweet Syrups Inc.", "DaVinci"));
        list.add(new CatalogItem("Matcha Powder (1kg)", "Powders", "kg", 500.0, "Asian Imports Ltd.", "Uji Premium"));
        list.add(new CatalogItem("16oz Plastic Cups", "Packaging", "pcs", 2.0, "Packaging Pros", "Generic"));
        list.add(new CatalogItem("Paper Straws", "Packaging", "pcs", 0.5, "Packaging Pros", "EcoStraw"));
        return list;
    }

    // UPGRADED: Displays Brand and Origin Supplier
    private void showLinkSupplierDialog(CatalogItem item, Button btnAdd) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_inventory_selection, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView title = dialogView.findViewById(R.id.tvDialogTitle);
        title.setText("Item Details: " + item.name);

        EditText etSearch = dialogView.findViewById(R.id.etSearchInventory);
        etSearch.setVisibility(View.GONE);
        Spinner spinnerFilter = dialogView.findViewById(R.id.spinnerFilterCategory);

        // Allow overriding the supplier
        List<String> combinedSuppliers = new ArrayList<>(existingSuppliers);
        if (!combinedSuppliers.contains(item.supplier)) {
            combinedSuppliers.add(1, item.supplier + " (Catalog Default)");
        }

        ArrayAdapter<String> supAdapter = getAdaptiveAdapter(combinedSuppliers);
        spinnerFilter.setAdapter(supAdapter);

        // Pre-select the catalog's default supplier if it exists
        for (int i = 0; i < combinedSuppliers.size(); i++) {
            if (combinedSuppliers.get(i).contains(item.supplier)) {
                spinnerFilter.setSelection(i);
                break;
            }
        }

        dialogView.findViewById(R.id.lvInventoryItems).setVisibility(View.GONE);

        LinearLayout actionLayout = new LinearLayout(this);
        actionLayout.setOrientation(LinearLayout.VERTICAL);
        actionLayout.setPadding(20, 20, 20, 20);

        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        // Display the Brand and Source Info
        TextView tvInfo = new TextView(this);
        tvInfo.setText("Catalog Source: " + item.supplier + "\nBrand: " + item.brand + "\nEst. Cost: ₱" + item.defaultCost + "\n\nLink to your inventory supplier below:");
        tvInfo.setTextColor(textColor);
        tvInfo.setTextSize(14f);
        tvInfo.setPadding(0, 0, 0, 30);
        actionLayout.addView(tvInfo);

        Button btnAddOnly = new Button(this);
        btnAddOnly.setText("Save to Inventory Only");
        btnAddOnly.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")));
        btnAddOnly.setTextColor(Color.WHITE);

        Button btnAddAndOrder = new Button(this);
        btnAddAndOrder.setText("Save & Create Purchase Order");
        btnAddAndOrder.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF9800")));
        btnAddAndOrder.setTextColor(Color.WHITE);
        btnAddAndOrder.setPadding(0, 20, 0, 0);

        actionLayout.addView(btnAddOnly);
        actionLayout.addView(btnAddAndOrder);

        ((LinearLayout) dialogView.findViewById(R.id.lvInventoryItems).getParent()).addView(actionLayout);

        btnAddOnly.setOnClickListener(v -> {
            String selectedSup = spinnerFilter.getSelectedItem().toString();
            if (selectedSup.equals("No Specific Supplier")) selectedSup = "";
            else if (selectedSup.contains(" (Catalog Default)")) selectedSup = item.supplier;

            addToInventory(item, selectedSup, btnAdd, false);
            dialog.dismiss();
        });

        btnAddAndOrder.setOnClickListener(v -> {
            String selectedSup = spinnerFilter.getSelectedItem().toString();
            if (selectedSup.equals("No Specific Supplier")) selectedSup = "";
            else if (selectedSup.contains(" (Catalog Default)")) selectedSup = item.supplier;

            addToInventory(item, selectedSup, btnAdd, true);
            dialog.dismiss();
        });

        dialogView.findViewById(R.id.btnCloseSelection).setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void addToInventory(CatalogItem item, String supplierName, Button btnAdd, boolean redirectToPO) {
        Product p = new Product();
        p.setProductName(item.name);
        p.setCategoryName(item.category);
        p.setCategoryId(item.category.toLowerCase().replace(" ", "_"));
        p.setProductType("Raw");
        p.setUnit(item.unit);
        p.setSalesUnit(item.unit);
        p.setCostPrice(item.defaultCost);
        p.setSellingPrice(0.0);
        p.setQuantity(0.0);
        p.setReorderLevel(10);
        p.setCriticalLevel(5);
        p.setSupplier(supplierName);
        p.setOwnerAdminId(ownerId);
        p.setActive(true);
        p.setDateAdded(System.currentTimeMillis());

        productRepository.addProduct(p, (String) null, new ProductRepository.OnProductAddedListener() {
            @Override
            public void onProductAdded(String productId) {
                runOnUiThread(() -> {
                    Toast.makeText(SuggestedSuppliesActivity.this, item.name + " saved!", Toast.LENGTH_SHORT).show();
                    btnAdd.setText("ADDED ✓");
                    btnAdd.setEnabled(false);
                    btnAdd.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.GRAY));

                    if (redirectToPO) {
                        Intent intent = new Intent(SuggestedSuppliesActivity.this, CreatePurchaseOrderActivity.class);
                        startActivity(intent);
                        finish();
                    }
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(SuggestedSuppliesActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private class SuggestedAdapter extends RecyclerView.Adapter<SuggestedAdapter.ViewHolder> {
        private List<CatalogItem> items;

        public SuggestedAdapter(List<CatalogItem> items) { this.items = items; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout layout = new LinearLayout(parent.getContext());
            layout.setOrientation(LinearLayout.HORIZONTAL);
            layout.setPadding(32, 32, 32, 32);
            layout.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            LinearLayout textLayout = new LinearLayout(parent.getContext());
            textLayout.setOrientation(LinearLayout.VERTICAL);
            textLayout.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

            boolean isDark = ThemeManager.getInstance(SuggestedSuppliesActivity.this).getCurrentTheme().name.equals("dark");
            int textColor = isDark ? Color.WHITE : Color.BLACK;

            TextView tvName = new TextView(parent.getContext());
            tvName.setId(View.generateViewId());
            tvName.setTextSize(16f);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            tvName.setTextColor(textColor);

            TextView tvCategory = new TextView(parent.getContext());
            tvCategory.setId(View.generateViewId());
            tvCategory.setTextSize(12f);
            tvCategory.setTextColor(Color.GRAY);

            textLayout.addView(tvName);
            textLayout.addView(tvCategory);

            Button btnAdd = new Button(parent.getContext());
            btnAdd.setId(View.generateViewId());
            btnAdd.setText("+ ADD");
            btnAdd.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            btnAdd.setTextColor(Color.WHITE);

            layout.addView(textLayout);
            layout.addView(btnAdd);

            return new ViewHolder(layout, tvName, tvCategory, btnAdd);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            CatalogItem item = items.get(position);
            holder.tvName.setText(item.name);
            holder.tvCategory.setText(item.category + " | " + item.brand + " | " + item.unit + " | Est. ₱" + item.defaultCost);

            holder.btnAdd.setOnClickListener(v -> {
                showLinkSupplierDialog(item, holder.btnAdd);
            });
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvName, tvCategory;
            Button btnAdd;
            ViewHolder(View v, TextView name, TextView cat, Button btn) {
                super(v); tvName = name; tvCategory = cat; btnAdd = btn;
            }
        }
    }
}