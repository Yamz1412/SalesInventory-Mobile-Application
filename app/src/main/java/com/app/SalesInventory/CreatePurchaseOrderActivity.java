package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.graphics.pdf.PdfDocument;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CreatePurchaseOrderActivity extends BaseActivity {

    private Toolbar toolbar;
    private DatabaseReference poRef;

    private RecyclerView rvSuppliers, rvSupplierProducts;
    private TextView tvCartSummary, tvCartTotal, tvSelectedSupplierName;
    private Button btnReviewCheckout;
    private ImageButton btnQuickAddSupplier;

    private LinearLayout mainSplitLayout, layoutSupplierPane, layoutProductsPane;

    private MaterialCardView cvCartBadge;
    private TextView tvCartBadgeCount;

    private EditText etSearchSupplier, etSearchProduct;
    private Spinner spinnerSupplierFilter, spinnerProductFilter;

    private ProductRepository productRepository;
    private List<Supplier> dbSuppliersList = new ArrayList<>();
    private List<Product> dbInventoryProducts = new ArrayList<>();

    private List<SupplierItem> uiSupplierItems = new ArrayList<>();
    private SupplierItem selectedSupplier = null;
    private List<Product> currentSupplierProducts = new ArrayList<>();
    private List<POItem> cartItems = new ArrayList<>();

    private SupplierAdapter supplierAdapter;
    private SupplierProductAdapter productAdapter;
    private DatabaseReference suppliersRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(R.layout.activity_create_purchase_order);


        Intent intent = getIntent();
        if (intent.hasExtra("MULTI_PO_IDS")) {
            ArrayList<String> ids = intent.getStringArrayListExtra("MULTI_PO_IDS");
            ArrayList<String> names = intent.getStringArrayListExtra("MULTI_PO_NAMES");
            ArrayList<String> suppliers = intent.getStringArrayListExtra("MULTI_PO_SUPPLIERS");
            double[] qtys = intent.getDoubleArrayExtra("MULTI_PO_QTYS");
            double[] costs = intent.getDoubleArrayExtra("MULTI_PO_COSTS");

            if (ids != null && !ids.isEmpty()) {
                // 1. Set the supplier dropdown to the FIRST item's supplier automatically
                if (suppliers != null && !suppliers.isEmpty() && suppliers.get(0) != null) {
                    // Create a dummy supplier just for tracking the name
                    selectedSupplier = new SupplierItem("", suppliers.get(0), "", "", "", "", new ArrayList<>());
                    if (tvSelectedSupplierName != null) {
                        tvSelectedSupplierName.setText(suppliers.get(0));
                    }
                }

                // 2. Loop through all passed items and push them to cartItems
                for (int i = 0; i < ids.size(); i++) {
                    boolean exists = false;
                    for (POItem existingItem : cartItems) {
                        if (existingItem.getProductId().equals(ids.get(i))) {
                            existingItem.setQuantity(existingItem.getQuantity() + qtys[i]);
                            exists = true;
                            break;
                        }
                    }

                    if (!exists) {
                        POItem newItem = new POItem(
                                ids.get(i),
                                names.get(i) + " [" + suppliers.get(i) + "]",
                                qtys[i],
                                costs[i],
                                "pcs"
                        );
                        cartItems.add(newItem);
                    }
                }

                // 3. Just update the text totals, no adapter needed here!
                updateCartTotals();
                Toast.makeText(this, ids.size() + " items added to Purchase Order!", Toast.LENGTH_LONG).show();
            }
        }

            toolbar = findViewById(R.id.toolbar);
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null)
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);

            poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");
            suppliersRef = FirebaseDatabase.getInstance().getReference("Suppliers");
            productRepository = SalesInventoryApplication.getProductRepository();

            initViews();
            applySplitScreenLayout(getResources().getConfiguration().orientation);

            supplierAdapter = new SupplierAdapter(new ArrayList<>(), new SupplierAdapter.OnSupplierClickListener() {
                @Override
                public void onSupplierSelected(SupplierItem supplier) {
                    selectSupplier(supplier);
                }

                @Override
                public void onSupplierDoubleClicked(SupplierItem supplier) {
                    showSupplierDetailsDialog(supplier);
                }

                @Override
                public void onSupplierLongClicked(SupplierItem supplier) {
                    showSupplierOptionsDialog(supplier);
                }
            });

            productAdapter = new SupplierProductAdapter(new ArrayList<>(), new SupplierProductAdapter.OnProductClickListener() {
                @Override
                public void onProductClick(Product product) {
                    showAddProductToPODialog(product);
                }

                @Override
                public void onProductLongClick(Product product) {
                    showProductOptionsDialog(product);
                }
            });

            setupRecyclerViews();
            loadSuppliersAndProducts();
            setupSearchListeners();

            btnReviewCheckout.setOnClickListener(v -> {
                if (cartItems.isEmpty()) {
                    Toast.makeText(this, "Cart is empty!", Toast.LENGTH_SHORT).show();
                } else {
                    showCheckoutDialog();
                }
            });

            btnQuickAddSupplier.setOnClickListener(v -> {
                startActivity(new Intent(CreatePurchaseOrderActivity.this, AddSupplierActivity.class));
            });
            SyncScheduler.enqueueImmediateSync(this);
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

    private ArrayAdapter<String> getAdaptiveDropdownAdapter(String[] items) {
        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        return new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, items) {
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
    }

    private void selectSupplier(SupplierItem supplier) {
        selectedSupplier = supplier;
        if (tvSelectedSupplierName != null) {
            tvSelectedSupplierName.setText(supplier.name);
        }
        loadProductsForSupplier(supplier);
    }

    private void showAddProductToPODialog(Product product) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_to_po, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvName = dialogView.findViewById(R.id.tvDialogProductName);
        EditText etQty = dialogView.findViewById(R.id.etDialogQty);
        EditText etCost = dialogView.findViewById(R.id.etDialogCost);
        Button btnAdd = dialogView.findViewById(R.id.btnDialogAdd);
        Button btnCancel = dialogView.findViewById(R.id.btnDialogCancel);

        // Force text colors to match the theme dynamically
        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        if (tvName != null) tvName.setTextColor(textColor);
        if (etQty != null) { etQty.setTextColor(textColor); etQty.setHintTextColor(Color.GRAY); }
        if (etCost != null) { etCost.setTextColor(textColor); etCost.setHintTextColor(Color.GRAY); }

        tvName.setText(product.getProductName());

        double defaultCost = product.getCostPrice();
        if (product.getQuantity() > 0) {
            defaultCost = product.getCostPrice() / product.getQuantity();
        }
        etCost.setText(String.format(Locale.US, "%.2f", defaultCost));

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnAdd.setOnClickListener(v -> {
            String qtyStr = etQty.getText().toString().trim();
            String costStr = etCost.getText().toString().trim();

            if (!qtyStr.isEmpty() && !costStr.isEmpty()) {
                try {
                    double qty = Double.parseDouble(qtyStr);
                    double cost = Double.parseDouble(costStr);

                    if (qty <= 0) {
                        Toast.makeText(this, "Quantity must be greater than zero", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String displayName = product.getProductName() + " [" + selectedSupplier.name + "]";
                    boolean exists = false;

                    for (POItem item : cartItems) {
                        if (item.getProductName().equals(displayName)) {
                            item.setQuantity(item.getQuantity() + qty);
                            item.setUnitPrice(cost);
                            exists = true;
                            break;
                        }
                    }

                    if (!exists) {
                        cartItems.add(new POItem(
                                product.getProductId(),
                                displayName,
                                qty,
                                cost,
                                product.getUnit()
                        ));
                    }

                    updateCartTotals();
                    Toast.makeText(this, "Added to cart", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();

                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid number entered", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Please enter both quantity and cost", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void initViews() {
        rvSuppliers = findViewById(R.id.rvSuppliers);
        rvSupplierProducts = findViewById(R.id.rvSupplierProducts);
        tvCartSummary = findViewById(R.id.tvCartSummary);
        tvCartTotal = findViewById(R.id.tvCartTotal);
        tvSelectedSupplierName = findViewById(R.id.tvSelectedSupplierName);
        btnReviewCheckout = findViewById(R.id.btnReviewCheckout);
        btnQuickAddSupplier = findViewById(R.id.btnQuickAddSupplier);

        currentSupplierProducts = new ArrayList<>();

        mainSplitLayout = findViewById(R.id.mainSplitLayout);
        layoutSupplierPane = findViewById(R.id.layoutSupplierPane);
        layoutProductsPane = findViewById(R.id.layoutProductsPane);

        cvCartBadge = findViewById(R.id.cvCartBadge);
        tvCartBadgeCount = findViewById(R.id.tvCartBadgeCount);

        etSearchSupplier = findViewById(R.id.etSearchSupplier);
        etSearchProduct = findViewById(R.id.etSearchProduct);
        spinnerSupplierFilter = findViewById(R.id.spinnerSupplierFilter);
        spinnerProductFilter = findViewById(R.id.spinnerProductFilter);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            etSearchSupplier.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
            etSearchProduct.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
    }

    private void showSupplierOptionsDialog(SupplierItem supplier) {
        String[] options = {"✏️ Edit Supplier", "🗑️ Delete Supplier"};
        new AlertDialog.Builder(this)
                .setTitle("Manage: " + supplier.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(this, AddSupplierActivity.class);
                        intent.putExtra("EDIT_SUPPLIER_ID", supplier.id);
                        startActivity(intent);
                    } else if (which == 1) {
                        confirmSupplierDeletion(supplier);
                    }
                }).show();
    }

    private void showProductOptionsDialog(Product product) {
        String[] options = {"✏️ Edit Product Details", "📦 Archive Product"};
        new AlertDialog.Builder(this)
                .setTitle("Manage: " + product.getProductName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        Intent intent = new Intent(this, AddProductActivity.class);
                        intent.putExtra("EDIT_PRODUCT_ID", product.getProductId());
                        startActivity(intent);
                    } else if (which == 1) {
                        confirmProductArchiving(product);
                    }
                }).show();
    }

    private void loadSuppliersAndProducts() {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null) ownerId = AuthManager.getInstance().getCurrentUserId();
        if (ownerId == null) return;

        final String finalOwnerId = ownerId;

        DatabaseReference supplierRef = FirebaseDatabase.getInstance().getReference("Suppliers");
        supplierRef.keepSynced(true);

        supplierRef.orderByChild("ownerAdminId").equalTo(finalOwnerId)
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        uiSupplierItems.clear();
                        dbSuppliersList.clear();

                        for (DataSnapshot ds : snapshot.getChildren()) {
                            String supId = ds.getKey();
                            String name = ds.child("name").getValue(String.class);
                            String email = ds.child("email").getValue(String.class);
                            String phone = ds.child("contact").getValue(String.class);
                            String address = ds.child("address").getValue(String.class);
                            String categories = ds.child("categories").getValue(String.class);

                            if (name != null) {
                                Supplier supplier = new Supplier();
                                supplier.setId(supId);
                                supplier.setName(name);
                                supplier.setEmail(email);
                                supplier.setContact(phone);
                                supplier.setAddress(address);
                                supplier.setCategories(categories);
                                supplier.setOwnerAdminId(finalOwnerId);
                                dbSuppliersList.add(supplier);

                                SupplierItem item = new SupplierItem(supId, name, email, phone, address, categories, new ArrayList<>());
                                uiSupplierItems.add(item);
                            }
                        }

                        if (supplierAdapter != null) {
                            supplierAdapter.filterList(new ArrayList<>(uiSupplierItems));
                        }
                        populateSupplierSpinner();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                    }
                });

        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                dbInventoryProducts.clear();

                for (Product product : products) {
                    if (product.isActive() && !"Menu".equalsIgnoreCase(product.getProductType())) {
                        dbInventoryProducts.add(product);
                    }
                }

                if (selectedSupplier != null) {
                    loadProductsForSupplier(selectedSupplier);
                }
            }
        });
    }

    private void applySplitScreenLayout(int orientation) {
        if (mainSplitLayout == null || layoutSupplierPane == null || layoutProductsPane == null) return;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            mainSplitLayout.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams suppParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            layoutSupplierPane.setLayoutParams(suppParams);
            LinearLayout.LayoutParams prodParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.8f);
            layoutProductsPane.setLayoutParams(prodParams);
        } else {
            mainSplitLayout.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams suppParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
            layoutSupplierPane.setLayoutParams(suppParams);
            LinearLayout.LayoutParams prodParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.2f);
            layoutProductsPane.setLayoutParams(prodParams);
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applySplitScreenLayout(newConfig.orientation);
    }

    private void setupSearchListeners() {
        etSearchSupplier.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { filterSuppliers(); }
        });

        etSearchProduct.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { filterProducts(); }
        });

        if (spinnerSupplierFilter != null) {
            spinnerSupplierFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { filterSuppliers(); }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }

        if (spinnerProductFilter != null) {
            spinnerProductFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { filterProducts(); }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });
        }
    }

    private void populateSupplierSpinner() {
        Set<String> categories = new HashSet<>();
        categories.add("All");
        categories.add("Beverages");
        categories.add("Raw Materials");
        categories.add("Packaging");

        for (SupplierItem s : uiSupplierItems) {
            if (s.categories != null && !s.categories.isEmpty()) {
                String[] cats = s.categories.split(",");
                for (String c : cats) categories.add(c.trim());
            }
        }

        List<String> list = new ArrayList<>(categories);
        Collections.sort(list);
        list.remove("All");
        list.add(0, "All");

        ArrayAdapter<String> adapter = getAdaptiveAdapter(list);
        if (spinnerSupplierFilter != null) spinnerSupplierFilter.setAdapter(adapter);
    }

    private void populateProductSpinner(List<Product> products) {
        Set<String> categories = new HashSet<>();
        categories.add("All");
        categories.add("Ingredients");
        categories.add("Supplies");

        for (Product p : products) {
            if (p.getCategoryName() != null && !p.getCategoryName().isEmpty()) {
                categories.add(p.getCategoryName());
            }
        }

        List<String> list = new ArrayList<>(categories);
        Collections.sort(list);
        list.remove("All");
        list.add(0, "All");

        ArrayAdapter<String> adapter = getAdaptiveAdapter(list);
        if (spinnerProductFilter != null) spinnerProductFilter.setAdapter(adapter);
    }

    private void filterSuppliers() {
        String text = etSearchSupplier.getText() != null ? etSearchSupplier.getText().toString().toLowerCase().trim() : "";
        String filter = spinnerSupplierFilter != null && spinnerSupplierFilter.getSelectedItem() != null
                ? spinnerSupplierFilter.getSelectedItem().toString() : "All";

        List<SupplierItem> filteredList = new ArrayList<>();
        for (SupplierItem item : uiSupplierItems) {
            boolean matchesText = item.name.toLowerCase().contains(text) ||
                    (item.categories != null && item.categories.toLowerCase().contains(text));

            boolean matchesFilter = filter.equals("All");
            if (!matchesFilter && item.categories != null) {
                matchesFilter = item.categories.toLowerCase().contains(filter.toLowerCase());
            }

            if (matchesText && matchesFilter) {
                filteredList.add(item);
            }
        }
        if (supplierAdapter != null) supplierAdapter.filterList(filteredList);

        selectedSupplier = null;
        tvSelectedSupplierName.setText("2. Select Products");
        if (productAdapter != null) productAdapter.filterList(new ArrayList<>());
    }

    private void filterProducts() {
        if (currentSupplierProducts == null) return;

        String text = etSearchProduct.getText() != null ? etSearchProduct.getText().toString().toLowerCase().trim() : "";
        String filter = spinnerProductFilter != null && spinnerProductFilter.getSelectedItem() != null
                ? spinnerProductFilter.getSelectedItem().toString() : "All";

        List<Product> filteredList = new ArrayList<>();
        for (Product item : currentSupplierProducts) {
            boolean matchesText = item.getProductName().toLowerCase().contains(text);
            boolean matchesFilter = filter.equals("All") ||
                    (item.getCategoryName() != null && item.getCategoryName().equalsIgnoreCase(filter));

            if (matchesText && matchesFilter) {
                filteredList.add(item);
            }
        }

        if (productAdapter != null) productAdapter.filterList(filteredList);
    }

    private void setupRecyclerViews() {
        rvSuppliers.setLayoutManager(new LinearLayoutManager(this));
        if (supplierAdapter != null) {
            rvSuppliers.setAdapter(supplierAdapter);
        }

        int spanCount = 1;
        int orientation = getResources().getConfiguration().orientation;
        boolean isTablet = (getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE;

        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            spanCount = isTablet ? 3 : 2;
        } else {
            spanCount = isTablet ? 2 : 1;
        }

        rvSupplierProducts.setLayoutManager(new GridLayoutManager(this, spanCount));
        if (productAdapter != null) {
            rvSupplierProducts.setAdapter(productAdapter);
        }
    }

    private void loadProductsForSupplier(SupplierItem supplier) {
        currentSupplierProducts.clear();
        String supCats = supplier.categories != null ? supplier.categories.toLowerCase() : "";

        for (Product p : dbInventoryProducts) {
            boolean matchesSupplierName = p.getSupplier() != null && p.getSupplier().equalsIgnoreCase(supplier.name);
            boolean matchesCategory = false;
            if (p.getCategoryName() != null && !supCats.isEmpty()) {
                matchesCategory = supCats.contains(p.getCategoryName().toLowerCase());
            }

            if (matchesSupplierName || matchesCategory) {
                currentSupplierProducts.add(p);
            }
        }

        productAdapter.filterList(currentSupplierProducts);
        populateProductSpinner(currentSupplierProducts);
        filterProducts();

        if (currentSupplierProducts.isEmpty()) {
            Toast.makeText(this, "No products found for " + supplier.name, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateCartTotals() {
        double total = 0;
        int itemCount = cartItems.size();

        for (POItem item : cartItems) {
            total += item.getSubtotal();
        }

        tvCartSummary.setText(itemCount + " Items in PO");
        tvCartTotal.setText(String.format(Locale.getDefault(), "Total: ₱%.2f", total));

        if (itemCount > 0) {
            cvCartBadge.setVisibility(View.VISIBLE);
            tvCartBadgeCount.setText(String.valueOf(itemCount));
        } else {
            cvCartBadge.setVisibility(View.GONE);
        }
    }

    private void showCheckoutDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_checkout_po, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView tvName = view.findViewById(R.id.dlgSupplierName);
        TextView tvContact = view.findViewById(R.id.dlgSupplierContact);
        TextView tvEmail = view.findViewById(R.id.dlgSupplierEmail);
        TextView tvAddress = view.findViewById(R.id.dlgSupplierAddress);

        // Force text colors to match the theme
        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;
        if (tvName != null) tvName.setTextColor(textColor);
        if (tvContact != null) tvContact.setTextColor(textColor);
        if (tvEmail != null) tvEmail.setTextColor(textColor);
        if (tvAddress != null) tvAddress.setTextColor(textColor);

        Set<String> uniqueSuppliers = new HashSet<>();
        for (POItem item : cartItems) {
            String name = item.getProductName();
            if (name.contains("[") && name.endsWith("]")) {
                uniqueSuppliers.add(name.substring(name.lastIndexOf("[") + 1, name.length() - 1));
            }
        }

        if (uniqueSuppliers.size() > 1) {
            tvName.setText("Multiple Suppliers (" + uniqueSuppliers.size() + ")");
            tvContact.setText("Combined Purchase Order");
            tvEmail.setText("");
            tvAddress.setText("");
        } else if (selectedSupplier != null) {
            tvName.setText(selectedSupplier.name);
            tvContact.setText("Phone: " + selectedSupplier.phone);
            tvEmail.setText("Email: " + selectedSupplier.email);
            tvAddress.setText("Address: " + selectedSupplier.address);
        }

        RecyclerView rvCart = view.findViewById(R.id.rvCartItemsDialog);
        rvCart.setLayoutManager(new LinearLayoutManager(this));

        POItemAdapter dialogAdapter = new POItemAdapter(this, cartItems, position -> {
            cartItems.remove(position);
            updateCartTotals();
            if (cartItems.isEmpty()) dialog.dismiss();
        }, this::updateCartTotals);

        rvCart.setAdapter(dialogAdapter);

        AutoCompleteTextView actvPayment = view.findViewById(R.id.actvPaymentMethod);
        if (actvPayment != null) actvPayment.setTextColor(textColor);
        String[] paymentOptions = {"Cash", "GCash", "Custom (Bank Transfer / Credit Card)"};

        // Use Adaptive adapter so dropdown entries are visible
        ArrayAdapter<String> paymentAdapter = getAdaptiveDropdownAdapter(paymentOptions);
        actvPayment.setAdapter(paymentAdapter);

        final String[] finalPaymentMethod = {"Cash"};
        actvPayment.setText("Cash", false);

        actvPayment.setOnItemClickListener((parent, view1, position, id) -> {
            String selected = paymentOptions[position];
            if (selected.startsWith("Custom")) {
                EditText input = new EditText(this);
                input.setHint("e.g. BDO Transfer, BPI Credit Card");
                input.setPadding(30, 40, 30, 40);
                input.setTextColor(textColor);
                input.setHintTextColor(Color.GRAY);

                new AlertDialog.Builder(this)
                        .setTitle("Custom Payment Method")
                        .setMessage("Specify the bank or payment channel:")
                        .setView(input)
                        .setPositiveButton("OK", (d, i) -> {
                            String customType = input.getText().toString().trim();
                            if (!customType.isEmpty()) {
                                finalPaymentMethod[0] = customType;
                                actvPayment.setText(customType, false);
                            } else {
                                finalPaymentMethod[0] = "Cash";
                                actvPayment.setText("Cash", false);
                            }
                        })
                        .setNegativeButton("Cancel", (d, i) -> {
                            finalPaymentMethod[0] = "Cash";
                            actvPayment.setText("Cash", false);
                        })
                        .setCancelable(false)
                        .show();
            } else {
                finalPaymentMethod[0] = selected;
            }
        });

        view.findViewById(R.id.btnCancelDialog).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnConfirmPO).setOnClickListener(v -> {
            createPurchaseOrder(finalPaymentMethod[0]);
            dialog.dismiss();
        });

        dialog.show();
    }

    private void createPurchaseOrder(String paymentMethod) {
        if (cartItems.isEmpty()) { Toast.makeText(this, "Please add at least one item", Toast.LENGTH_SHORT).show(); return; }

        String id = poRef.push().getKey();
        if (id == null) return;

        String poNumber = "PO-" + System.currentTimeMillis() / 1000;
        double total = 0;
        for (POItem item : cartItems) total += item.getSubtotal();

        final double finalTotal = total;

        Set<String> uniqueSuppliers = new HashSet<>();
        for (POItem item : cartItems) {
            String name = item.getProductName();
            if (name.contains("[") && name.endsWith("]")) {
                uniqueSuppliers.add(name.substring(name.lastIndexOf("[") + 1, name.length() - 1));
            }
        }

        String supplierNames = uniqueSuppliers.size() == 1 ? uniqueSuppliers.iterator().next() : "Multiple Suppliers (" + String.join(", ", uniqueSuppliers) + ")";
        String phoneStr = uniqueSuppliers.size() == 1 && selectedSupplier != null ? selectedSupplier.phone : "Multiple Contacts";

        PurchaseOrder po = new PurchaseOrder(
                id, poNumber, supplierNames, phoneStr, PurchaseOrder.STATUS_PENDING,
                System.currentTimeMillis(), total, new ArrayList<>(cartItems)
        );
        po.setOwnerAdminId(AuthManager.getInstance().getCurrentUserId());

        Map<String, Object> poData = po.toMap();
        poData.put("expectedDeliveryDate", System.currentTimeMillis() + (3L * 24 * 60 * 60 * 1000));
        poData.put("paymentMethod", paymentMethod);

        poRef.child(id).setValue(poData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Purchase Order Created Successfully", Toast.LENGTH_LONG).show();

                    if (uniqueSuppliers.size() > 1) {
                        AlertDialog.Builder builder = new AlertDialog.Builder(this);
                        builder.setTitle("Combined PO Saved");
                        builder.setMessage("This Purchase Order contains items from multiple suppliers. It has been saved to your records.");
                        builder.setPositiveButton("OK", (d, which) -> finish());
                        builder.setCancelable(false);
                        builder.show();
                    } else {
                        fetchBusinessDetailsAndPrompt(poNumber, finalTotal, paymentMethod);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void fetchBusinessDetailsAndPrompt(String poNumber, double total, String paymentMethod) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = AuthManager.getInstance().getCurrentUserId();
        }

        FirebaseFirestore.getInstance().collection("users").document(ownerId).get()
                .addOnSuccessListener(doc -> {
                    String bizName = doc.getString("businessName");
                    String bizType = doc.getString("businessType");

                    if (bizName == null || bizName.isEmpty()) bizName = "Our Store";
                    if (bizType == null || bizType.isEmpty()) bizType = "Business";

                    promptSendPO(poNumber, total, paymentMethod, bizName, bizType);
                })
                .addOnFailureListener(e -> {
                    promptSendPO(poNumber, total, paymentMethod, "Our Store", "Business");
                });
    }

    private void sendPurchaseOrderViaEmail(PurchaseOrder po) {
        if (po == null || po.getItems() == null || po.getItems().isEmpty()) {
            Toast.makeText(this, "Cannot email an empty Purchase Order.", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            ReportExportUtil exportUtil = new ReportExportUtil(this);
            File exportDir = exportUtil.getExportDirectory();
            String pdfName = exportUtil.generateFileName("Purchase_Order_" + po.getPoNumber(), ReportExportUtil.EXPORT_PDF);
            File poPdfFile = new File(exportDir, pdfName);

            // 1. Generate the PDF using the iText7 generator we built
            PDFGenerator generator = new PDFGenerator(this);
            String businessName = "Sales Inventory System"; // Change this if you fetch the name dynamically
            generator.generatePurchaseOrderPDF(poPdfFile, businessName, po);

            // 2. Trigger the Email App Chooser
            exportUtil.shareFileViaEmail(poPdfFile, "Purchase Order: " + po.getPoNumber());

        } catch (Exception e) {
            Toast.makeText(this, "Failed to generate PO PDF: " + e.getMessage(), Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void promptSendPO(String poNumber, double total, String paymentMethod, String bizName, String bizType) {
        StringBuilder msg = new StringBuilder();
        msg.append("Hello ").append(selectedSupplier.name).append(",\n\n");
        msg.append("This is ").append(bizName).append(" (").append(bizType).append(").\n");
        msg.append("Please find our official Purchase Order (").append(poNumber).append(") attached as a PDF.\n\n");
        msg.append("Payment Method: ").append(paymentMethod).append("\n");
        msg.append("Total Amount: ₱").append(String.format(Locale.US, "%.2f", total)).append("\n\n");
        msg.append("Please confirm receipt of this order and your expected delivery schedule.\n\n");
        msg.append("Thank you,\n").append(bizName);

        String messageBody = msg.toString();
        String subject = "Purchase Order: " + poNumber + " - " + bizName;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Send Official Purchase Order");
        builder.setMessage("The PO has been successfully saved. Would you like to generate a PDF and send it to the supplier now?");

        builder.setPositiveButton("Send Email (PDF)", (dialog, which) ->
                generatePdfAndSendEmail(poNumber, total, paymentMethod, bizName, bizType, selectedSupplier.email, subject, messageBody));

        builder.setNeutralButton("Send SMS", (dialog, which) -> sendSMS(selectedSupplier.phone, messageBody));

        builder.setNegativeButton("Skip for Now", (dialog, which) -> finish());
        builder.setCancelable(false);
        builder.show();
    }

    private void generatePdfAndSendEmail(String poNumber, double total, String paymentMethod, String bizName, String bizType, String emailAddress, String subject, String body) {
        if (emailAddress == null || emailAddress.isEmpty()) {
            Toast.makeText(this, "No email address found for this supplier. Falling back to SMS if available.", Toast.LENGTH_LONG).show();
            return;
        }

        PdfDocument document = new PdfDocument();
        PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(595, 842, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);
        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();

        paint.setTextSize(24f);
        paint.setFakeBoldText(true);
        paint.setColor(Color.BLACK);
        canvas.drawText("PURCHASE ORDER", 50, 60, paint);

        paint.setTextSize(14f);
        paint.setFakeBoldText(false);
        canvas.drawText("From: " + bizName + " (" + bizType + ")", 50, 100, paint);
        canvas.drawText("To: " + selectedSupplier.name, 50, 120, paint);
        canvas.drawText("PO Number: " + poNumber, 350, 100, paint);
        canvas.drawText("Payment Method: " + paymentMethod, 350, 120, paint);

        paint.setStrokeWidth(2f);
        canvas.drawLine(50, 140, 545, 140, paint);

        paint.setFakeBoldText(true);
        canvas.drawText("Qty / Unit", 50, 170, paint);
        canvas.drawText("Description", 150, 170, paint);
        canvas.drawText("Unit Price", 400, 170, paint);
        canvas.drawText("Subtotal", 480, 170, paint);
        canvas.drawLine(50, 180, 545, 180, paint);

        paint.setFakeBoldText(false);
        int startY = 210;
        for (POItem item : cartItems) {
            canvas.drawText(item.getQuantity() + " " + item.getUnit(), 50, startY, paint);
            canvas.drawText(item.getProductName(), 150, startY, paint);
            canvas.drawText("₱" + String.format(Locale.US, "%.2f", item.getUnitPrice()), 400, startY, paint);
            canvas.drawText("₱" + String.format(Locale.US, "%.2f", item.getSubtotal()), 480, startY, paint);
            startY += 30;
        }

        canvas.drawLine(50, startY, 545, startY, paint);
        paint.setFakeBoldText(true);
        paint.setTextSize(16f);
        canvas.drawText("TOTAL AMOUNT: ₱" + String.format(Locale.US, "%.2f", total), 350, startY + 30, paint);

        document.finishPage(page);

        try {
            File pdfDirPath = new File(getCacheDir(), "pdfs");
            if (!pdfDirPath.exists()) pdfDirPath.mkdirs();
            File file = new File(pdfDirPath, poNumber + ".pdf");

            document.writeTo(new FileOutputStream(file));
            document.close();

            android.net.Uri pdfUri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file);

            Intent emailIntent = new Intent(Intent.ACTION_SEND);
            emailIntent.setType("application/pdf");
            emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{emailAddress});
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
            emailIntent.putExtra(Intent.EXTRA_TEXT, body);
            emailIntent.putExtra(Intent.EXTRA_STREAM, pdfUri);
            emailIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

            startActivity(Intent.createChooser(emailIntent, "Send PO Email via..."));
            finish();

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to generate PDF. Falling back to text email.", Toast.LENGTH_LONG).show();
            sendEmailFallback(emailAddress, subject, body);
        }
    }

    private void sendEmailFallback(String emailAddress, String subject, String body) {
        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(android.net.Uri.parse("mailto:"));
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{emailAddress});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, body);
        try {
            startActivity(Intent.createChooser(emailIntent, "Send Email..."));
        } catch (Exception e) {
            Toast.makeText(this, "No email client installed.", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void sendSMS(String phoneNumber, String body) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            Toast.makeText(this, "No phone number found for this supplier", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent smsIntent = new Intent(Intent.ACTION_SENDTO);
        smsIntent.setData(android.net.Uri.parse("smsto:" + phoneNumber));
        smsIntent.putExtra("sms_body", body);

        try {
            startActivity(smsIntent);
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "No SMS app installed.", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    private void promptDeleteSupplier(SupplierItem supplier) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Supplier")
                .setMessage("Are you sure you want to permanently delete " + supplier.name + " from your supplier list?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    DatabaseReference supRef = FirebaseDatabase.getInstance().getReference("Suppliers");
                    supRef.child(supplier.id).removeValue().addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Supplier deleted", Toast.LENGTH_SHORT).show();
                        if (selectedSupplier != null && selectedSupplier.id.equals(supplier.id)) {
                            selectedSupplier = null;
                            tvSelectedSupplierName.setText("2. Select Products");
                            if (productAdapter != null) productAdapter.filterList(new ArrayList<>());
                            cartItems.clear();
                            updateCartTotals();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showSupplierDetailsDialog(SupplierItem supplier) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_supplier_details);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvName = dialog.findViewById(R.id.tvDialogSupplierName);
        TextView tvCategories = dialog.findViewById(R.id.tvDialogSupplierCategories);
        TextView tvPhone = dialog.findViewById(R.id.tvDialogSupplierPhone);
        TextView tvEmail = dialog.findViewById(R.id.tvDialogSupplierEmail);
        TextView tvAddress = dialog.findViewById(R.id.tvDialogSupplierAddress);
        Button btnClose = dialog.findViewById(R.id.btnDialogClose);

        // Force text colors to match the theme
        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;
        if (tvName != null) tvName.setTextColor(textColor);
        if (tvCategories != null) tvCategories.setTextColor(textColor);
        if (tvPhone != null) tvPhone.setTextColor(textColor);
        if (tvEmail != null) tvEmail.setTextColor(textColor);
        if (tvAddress != null) tvAddress.setTextColor(textColor);

        tvName.setText(supplier.name);
        tvCategories.setText(supplier.categories);
        tvPhone.setText(supplier.phone);
        tvEmail.setText(supplier.email);
        tvAddress.setText(supplier.address);

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SupplierItem {
        public String id, name, email, phone, address, categories;
        public List<Product> catalog;

        public SupplierItem(String id, String name, String email, String phone, String address, String categories, List<Product> catalog) {
            this.id = id; this.name = name; this.email = email; this.phone = phone; this.address = address;
            this.categories = categories;
            this.catalog = catalog;
        }
    }

    private void confirmSupplierDeletion(SupplierItem supplier) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Deletion")
                .setMessage("Are you sure you want to delete " + supplier.name + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    suppliersRef.child(supplier.id).removeValue()
                            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Supplier removed", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null).show();
    }

    private void confirmProductArchiving(Product product) {
        new AlertDialog.Builder(this)
                .setTitle("Archive Product")
                .setMessage("Are you sure you want to archive   " + product.getProductName() + "?")
                .setPositiveButton("Archive", (dialog, which) -> {
                    SalesInventoryApplication.getProductRepository().deleteProduct(product.getProductId(), new ProductRepository.OnProductDeletedListener() {
                        @Override
                        public void onProductDeleted(String msg) {
                            runOnUiThread(() -> Toast.makeText(CreatePurchaseOrderActivity.this, "Product archived", Toast.LENGTH_SHORT).show());
                        }
                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> Toast.makeText(CreatePurchaseOrderActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Cancel", null).show();
    }
    private void showAddToDraftDialog(Product product) {
        // Create a simple layout for the dialog programmatically
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        int padding = (int) (20 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        // Text showing the item name and supplier
        android.widget.TextView tvDetails = new android.widget.TextView(this);
        tvDetails.setText("Item: " + product.getProductName() + "\nSupplier: " + product.getSupplier());
        tvDetails.setTextSize(16f);
        tvDetails.setPadding(0, 0, 0, 20);

        // Ensure text is visible in dark mode
        boolean isDark = false;
        try {
            isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        } catch (Exception e) {
            int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            isDark = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
        int textColor = isDark ? android.graphics.Color.WHITE : android.graphics.Color.BLACK;
        tvDetails.setTextColor(textColor);
        layout.addView(tvDetails);

        // Input field for Quantity
        final android.widget.EditText etQuantity = new android.widget.EditText(this);
        etQuantity.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etQuantity.setHint("Enter quantity to order (e.g., 50)");
        etQuantity.setTextColor(textColor);
        etQuantity.setHintTextColor(android.graphics.Color.GRAY);
        layout.addView(etQuantity);

        // ADDED: Input field for Unit Cost to fix the ₱0.00 bug
        final android.widget.EditText etUnitCost = new android.widget.EditText(this);
        etUnitCost.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etUnitCost.setHint("Unit Cost (₱)");
        etUnitCost.setTextColor(textColor);
        etUnitCost.setHintTextColor(android.graphics.Color.GRAY);
        if (product.getCostPrice() > 0) {
            etUnitCost.setText(String.valueOf(product.getCostPrice()));
        }
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 20, 0, 0);
        etUnitCost.setLayoutParams(params);
        layout.addView(etUnitCost);

        // Build and show the Dialog
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Add to Purchase Order")
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton("Add to PO", (dialog, which) -> {
                    String qtyStr = etQuantity.getText().toString().trim();
                    String costStr = etUnitCost.getText().toString().trim();

                    if (qtyStr.isEmpty() || costStr.isEmpty()) {
                        android.widget.Toast.makeText(this, "Quantity and Unit Cost are required", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    double quantityToOrder = Double.parseDouble(qtyStr);
                    double unitCost = Double.parseDouble(costStr);

                    String displayName = product.getProductName() + " [" + product.getSupplier() + "]";

                    // Check if item already exists in cart to prevent duplicates
                    boolean exists = false;
                    for (POItem item : cartItems) {
                        if (item.getProductName().equals(displayName)) {
                            item.setQuantity(item.getQuantity() + quantityToOrder);
                            item.setUnitPrice(unitCost);
                            exists = true;
                            break;
                        }
                    }

                    // Add the item to your main cart list
                    if (!exists) {
                        POItem newItem = new POItem(
                                product.getProductId(),
                                displayName,
                                quantityToOrder,
                                unitCost,
                                product.getUnit() != null ? product.getUnit() : "pcs"
                        );
                        cartItems.add(newItem);
                    }

                    // No adapter to notify here! Just update the text.
                    updateCartTotals();

                    android.widget.Toast.makeText(CreatePurchaseOrderActivity.this, product.getProductName() + " added to PO Cart!", android.widget.Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }
}