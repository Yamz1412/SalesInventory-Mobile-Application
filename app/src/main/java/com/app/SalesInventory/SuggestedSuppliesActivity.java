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
import android.widget.ImageButton;
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
    private com.google.android.material.floatingactionbutton.FloatingActionButton fabCart;
    private TextView tvCartBadge;
    private List<CartSupplyItem> selectedSuppliesCart = new ArrayList<>();

    public static class CartSupplyItem {
        public CatalogItem item;
        public String supplier;
        public double quantity;

        public CartSupplyItem(CatalogItem item, String supplier, double quantity) {
            this.item = item;
            this.supplier = supplier;
            this.quantity = quantity;
        }
    }

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

        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        etSearchCatalog.setTextColor(isDark ? Color.WHITE : Color.BLACK);
        etSearchCatalog.setHintTextColor(Color.GRAY);

        loadCatalogFromFirebase();
        loadExistingSuppliers();

        fabCart = findViewById(R.id.fabCart);
        tvCartBadge = findViewById(R.id.tvCartBadge);

        if (fabCart != null) {
            fabCart.setOnClickListener(v -> showCartSummaryDialog());
        }
    }

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

    private void showCartSummaryDialog() {
        if (selectedSuppliesCart.isEmpty()) {
            Toast.makeText(this, "No items in the list yet.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Rely on Android's native theme colors to prevent the invisible text bug
        boolean isDark = false;
        try {
            isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        } catch (Exception e) {
            int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            isDark = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 20);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Selected Items (" + selectedSuppliesCart.size() + ")");
        tvTitle.setTextSize(20f);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        tvTitle.setTextColor(textColor);
        tvTitle.setPadding(0, 0, 0, 30);
        layout.addView(tvTitle);

        android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
        android.widget.LinearLayout listContainer = new android.widget.LinearLayout(this);
        listContainer.setOrientation(android.widget.LinearLayout.VERTICAL);
        scrollView.addView(listContainer);

        android.widget.LinearLayout.LayoutParams rowParams = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, 20);

        for (int i = 0; i < selectedSuppliesCart.size(); i++) {
            CartSupplyItem cartItem = selectedSuppliesCart.get(i);
            final int index = i;

            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.VERTICAL);
            row.setLayoutParams(rowParams);
            row.setPadding(20, 20, 20, 20);

            android.graphics.drawable.GradientDrawable border = new android.graphics.drawable.GradientDrawable();
            border.setColor(isDark ? Color.parseColor("#2C2C2C") : Color.parseColor("#F5F5F5"));
            border.setCornerRadius(16f);
            row.setBackground(border);

            TextView tvName = new TextView(this);
            tvName.setText(cartItem.item.name);
            tvName.setTextSize(16f);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            tvName.setTextColor(textColor);

            TextView tvSup = new TextView(this);
            tvSup.setText("Supplier: " + cartItem.supplier);
            tvSup.setTextSize(12f);
            tvSup.setTextColor(Color.GRAY);

            row.addView(tvName);
            row.addView(tvSup);

            android.widget.LinearLayout qtyRow = new android.widget.LinearLayout(this);
            qtyRow.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            qtyRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
            qtyRow.setPadding(0, 16, 0, 0);

            TextView tvQtyLabel = new TextView(this);
            tvQtyLabel.setText("Qty to Order: ");
            tvQtyLabel.setTextColor(textColor);

            EditText etQty = new EditText(this);
            etQty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            etQty.setText(String.valueOf(cartItem.quantity));
            etQty.setTextColor(textColor);
            etQty.setLayoutParams(new android.widget.LinearLayout.LayoutParams(150, android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

            etQty.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    try { cartItem.quantity = Double.parseDouble(s.toString()); } catch (Exception e) { cartItem.quantity = 0; }
                }
            });

            ImageButton btnRemove = new ImageButton(this);
            btnRemove.setImageResource(android.R.drawable.ic_menu_delete);
            btnRemove.setBackgroundColor(Color.TRANSPARENT);
            btnRemove.setColorFilter(Color.parseColor("#E53935"));

            View spacer = new View(this);
            spacer.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, 0, 1f));

            qtyRow.addView(tvQtyLabel);
            qtyRow.addView(etQty);
            qtyRow.addView(spacer);
            qtyRow.addView(btnRemove);

            row.addView(qtyRow);
            listContainer.addView(row);

            AlertDialog[] dialogRef = new AlertDialog[1];
            btnRemove.setOnClickListener(v -> {
                selectedSuppliesCart.remove(index);
                updateCartBadge();
                if (dialogRef[0] != null) dialogRef[0].dismiss();
                showCartSummaryDialog();
            });
        }

        android.widget.LinearLayout.LayoutParams scrollParams = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollView.setLayoutParams(scrollParams);
        layout.addView(scrollView);

        Button btnSaveOrder = new Button(this);
        btnSaveOrder.setText("Save & Create Purchase Order");
        btnSaveOrder.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FF9800")));
        btnSaveOrder.setTextColor(Color.WHITE);
        android.widget.LinearLayout.LayoutParams btnParams = new android.widget.LinearLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.setMargins(0, 24, 0, 0);
        btnSaveOrder.setLayoutParams(btnParams);

        layout.addView(btnSaveOrder);

        AlertDialog dialog = new AlertDialog.Builder(this).setView(layout).create();

        // Only one action now!
        btnSaveOrder.setOnClickListener(v -> { processMultiCartItems(true); dialog.dismiss(); });

        dialog.show();
    }

    private void processMultiCartItems(boolean redirectToPO) {
        int totalItems = selectedSuppliesCart.size();
        final int[] completedSaves = {0};

        ArrayList<String> poIds = new ArrayList<>();
        ArrayList<String> poNames = new ArrayList<>();
        ArrayList<String> poSuppliers = new ArrayList<>();
        double[] poQtys = new double[totalItems];
        double[] poCosts = new double[totalItems];

        Toast.makeText(this, "Saving " + totalItems + " items...", Toast.LENGTH_SHORT).show();

        for (int i = 0; i < totalItems; i++) {
            CartSupplyItem cartItem = selectedSuppliesCart.get(i);
            int currentIndex = i;

            Product p = new Product();
            p.setProductName(cartItem.item.name);
            p.setCategoryName(cartItem.item.category);
            p.setCategoryId(cartItem.item.category.toLowerCase().replace(" ", "_"));
            p.setProductType("Raw");
            p.setUnit(cartItem.item.unit);
            p.setSalesUnit(cartItem.item.unit);
            p.setCostPrice(cartItem.item.defaultCost);
            p.setSellingPrice(0.0);
            p.setQuantity(0.0); // Defaulting 0 since you are waiting for PO delivery
            p.setReorderLevel(10);
            p.setCriticalLevel(5);
            p.setSupplier(cartItem.supplier);
            p.setOwnerAdminId(ownerId);
            p.setActive(true);
            p.setDateAdded(System.currentTimeMillis());

            productRepository.addProduct(p, (String) null, new ProductRepository.OnProductAddedListener() {
                @Override
                public void onProductAdded(String productId) {
                    poIds.add(productId);
                    poNames.add(p.getProductName());
                    poSuppliers.add(p.getSupplier());
                    poQtys[currentIndex] = cartItem.quantity;
                    poCosts[currentIndex] = p.getCostPrice();

                    completedSaves[0]++;
                    checkAndLaunchPO(completedSaves[0], totalItems, redirectToPO, poIds, poNames, poSuppliers, poQtys, poCosts);
                }

                @Override
                public void onError(String error) {
                    completedSaves[0]++;
                    checkAndLaunchPO(completedSaves[0], totalItems, redirectToPO, poIds, poNames, poSuppliers, poQtys, poCosts);
                }
            });
        }
    }

    private void checkAndLaunchPO(int completed, int total, boolean redirect, ArrayList<String> ids, ArrayList<String> names, ArrayList<String> suppliers, double[] qtys, double[] costs) {
        if (completed == total) {
            runOnUiThread(() -> {
                if (redirect && !ids.isEmpty()) {
                    Intent intent = new Intent(SuggestedSuppliesActivity.this, CreatePurchaseOrderActivity.class);
                    intent.putStringArrayListExtra("MULTI_PO_IDS", ids);
                    intent.putStringArrayListExtra("MULTI_PO_NAMES", names);
                    intent.putStringArrayListExtra("MULTI_PO_SUPPLIERS", suppliers);
                    intent.putExtra("MULTI_PO_QTYS", qtys);
                    intent.putExtra("MULTI_PO_COSTS", costs);
                    startActivity(intent);
                    finish();
                } else if (!redirect) {
                    Toast.makeText(SuggestedSuppliesActivity.this, "Successfully saved to inventory!", Toast.LENGTH_SHORT).show();
                }

                selectedSuppliesCart.clear();
                updateCartBadge();
            });
        }
    }

    private void updateCartBadge() {
        if (tvCartBadge != null) {
            if (selectedSuppliesCart.isEmpty()) {
                tvCartBadge.setVisibility(View.GONE);
            } else {
                tvCartBadge.setVisibility(View.VISIBLE);
                tvCartBadge.setText(String.valueOf(selectedSuppliesCart.size()));
            }
        }
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

    private void showLinkSupplierDialog(CatalogItem item, Button btnAdd) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_inventory_selection, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView title = dialogView.findViewById(R.id.tvDialogTitle);
        if (title != null) {
            title.setText("Item Details: " + item.name);
        }

        EditText etSearch = dialogView.findViewById(R.id.etSearchInventory);
        if (etSearch != null) etSearch.setVisibility(View.GONE);

        Spinner spinnerFilter = dialogView.findViewById(R.id.spinnerFilterCategory);

        List<String> combinedSuppliers = new ArrayList<>(existingSuppliers);
        if (!combinedSuppliers.contains(item.supplier)) {
            combinedSuppliers.add(1, item.supplier + " (Catalog Default)");
        }

        ArrayAdapter<String> supAdapter = getAdaptiveAdapter(combinedSuppliers);
        if (spinnerFilter != null) {
            spinnerFilter.setAdapter(supAdapter);
            for (int i = 0; i < combinedSuppliers.size(); i++) {
                if (combinedSuppliers.get(i).contains(item.supplier)) {
                    spinnerFilter.setSelection(i);
                    break;
                }
            }
        }

        View lvItems = dialogView.findViewById(R.id.lvInventoryItems);
        if (lvItems != null) lvItems.setVisibility(View.GONE);

        LinearLayout actionLayout = new LinearLayout(this);
        actionLayout.setOrientation(LinearLayout.VERTICAL);
        actionLayout.setPadding(20, 20, 20, 20);

        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        TextView tvInfo = new TextView(this);
        tvInfo.setText("Item: " + item.name + "\nCatalog Source: " + item.supplier + "\nBrand: " + item.brand + "\nEst. Cost: ₱" + item.defaultCost + "\n\nLink to your inventory supplier below:");
        tvInfo.setTextColor(textColor);
        tvInfo.setTextSize(14f);
        tvInfo.setPadding(0, 0, 0, 30);
        actionLayout.addView(tvInfo);

        Button btnAddToCart = new Button(this);
        btnAddToCart.setText("Add to Cart List");
        btnAddToCart.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")));
        btnAddToCart.setTextColor(Color.WHITE);

        if (lvItems != null && lvItems.getParent() instanceof ViewGroup) {
            ((ViewGroup) lvItems.getParent()).addView(btnAddToCart);
        } else if (dialogView instanceof ViewGroup) {
            ((ViewGroup) dialogView).addView(btnAddToCart);
        }

        btnAddToCart.setOnClickListener(v -> {
            String selectedSup = (spinnerFilter != null && spinnerFilter.getSelectedItem() != null) ? spinnerFilter.getSelectedItem().toString() : "";
            if (selectedSup.equals("No Specific Supplier")) selectedSup = "";
            else if (selectedSup.contains(" (Catalog Default)")) selectedSup = item.supplier;

            selectedSuppliesCart.add(new CartSupplyItem(item, selectedSup, 1.0));
            updateCartBadge();
            Toast.makeText(this, item.name + " added to list!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        View btnClose = dialogView.findViewById(R.id.btnCloseSelection);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dialog.dismiss());
        }

        dialog.show();
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