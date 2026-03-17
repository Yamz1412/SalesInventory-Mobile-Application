package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfDocument;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.FileOutputStream;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.drawable.ColorDrawable;
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

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

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

    // Split Screen Layouts
    private LinearLayout mainSplitLayout, layoutSupplierPane, layoutProductsPane;

    // Notification Badge Variables
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        setContentView(R.layout.activity_create_purchase_order);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");
        productRepository = SalesInventoryApplication.getProductRepository();

        initViews();

        applySplitScreenLayout(getResources().getConfiguration().orientation);

        setupRecyclerViews();
        setupSearchListeners();
        fetchRealDataFromDatabase();

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

    private void initViews() {
        rvSuppliers = findViewById(R.id.rvSuppliers);
        rvSupplierProducts = findViewById(R.id.rvSupplierProducts);
        tvCartSummary = findViewById(R.id.tvCartSummary);
        tvCartTotal = findViewById(R.id.tvCartTotal);
        tvSelectedSupplierName = findViewById(R.id.tvSelectedSupplierName);
        btnReviewCheckout = findViewById(R.id.btnReviewCheckout);
        btnQuickAddSupplier = findViewById(R.id.btnQuickAddSupplier);
        currentSupplierProducts = new ArrayList<>();
        rvSupplierProducts.setLayoutManager(new GridLayoutManager(this, 2));
        rvSupplierProducts.setAdapter(productAdapter);

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

    // =========================================================================
    // DYNAMIC SPLIT SCREEN LOGIC
    // =========================================================================
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

    // =========================================================================
    // DATA FETCHING & UI SETUP
    // =========================================================================
    private void fetchRealDataFromDatabase() {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = AuthManager.getInstance().getCurrentUserId();
        }
        if (ownerId == null) return;

        final String finalOwnerId = ownerId;

        // Suppliers from Realtime DB
        DatabaseReference supRef = FirebaseDatabase.getInstance().getReference("Suppliers");
        supRef.orderByChild("ownerAdminId").equalTo(finalOwnerId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                dbSuppliersList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Supplier s = ds.getValue(Supplier.class);
                    if (s != null) dbSuppliersList.add(s);
                }
                buildCatalogModels();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        // Products — try Room LiveData first
        productRepository.getAllProducts().observe(this, products -> {
            dbInventoryProducts.clear();
            if (products != null) {
                for (Product p : products) {
                    if (p.isActive()
                            && !"Menu".equals(p.getProductType())
                            && p.getSupplier() != null
                            && !p.getSupplier().trim().isEmpty()) {
                        dbInventoryProducts.add(p);
                    }
                }
            }

            if (dbInventoryProducts.isEmpty()) {
                fetchProductsDirectlyFromFirestore(finalOwnerId);
            } else {
                buildCatalogModels();
            }
        });
    }

    private void fetchProductsDirectlyFromFirestore(String ownerId) {
        ProductRepository repo = SalesInventoryApplication.getProductRepository();
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(ownerId).collection("products")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Product p = doc.toObject(Product.class);
                        if (p != null) {
                            p.setProductId(doc.getId());
                            Object isActiveObj = doc.get("isActive");
                            boolean isActive = p.isActive()
                                    || Boolean.TRUE.equals(isActiveObj);
                            if (!isActive) continue;
                            if ("Menu".equals(p.getProductType())) continue;
                            if (p.getSupplier() == null || p.getSupplier().trim().isEmpty()) continue;
                            dbInventoryProducts.add(p);
                            repo.upsertFromRemote(p);
                        }
                    }
                    runOnUiThread(this::buildCatalogModels);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Could not load products: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void buildCatalogModels() {
        if (dbSuppliersList.isEmpty()) {
            uiSupplierItems.clear();
            if (supplierAdapter != null) supplierAdapter.filterList(uiSupplierItems);
            return;
        }

        uiSupplierItems.clear();
        for (Supplier s : dbSuppliersList) {
            List<Product> catalog = new ArrayList<>();
            for (Product p : dbInventoryProducts) {
                boolean matchesSupplier = p.getSupplier() != null
                        && p.getSupplier().equalsIgnoreCase(s.getName());
                if (matchesSupplier) catalog.add(p);
            }
            uiSupplierItems.add(new SupplierItem(
                    s.getId(), s.getName(), s.getEmail(),
                    s.getContact(), s.getAddress(), s.getCategories(), catalog));
        }

        populateSupplierSpinner();

        if (supplierAdapter != null) supplierAdapter.filterList(uiSupplierItems);

        if (selectedSupplier != null) {
            for (SupplierItem item : uiSupplierItems) {
                if (item.id.equals(selectedSupplier.id)) {
                    selectedSupplier = item;
                    loadProductsForSupplier(item);
                    break;
                }
            }
        }
    }

    // =========================================================================
    // SEARCH AND FILTER LOGIC
    // =========================================================================
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

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, list);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
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

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, list);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
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

        supplierAdapter = new SupplierAdapter(uiSupplierItems, new SupplierAdapter.OnSupplierClickListener() {
            @Override
            public void onSupplierSelected(SupplierItem supplier) {
                selectedSupplier = supplier;
                tvSelectedSupplierName.setText("Products for " + supplier.name);
                etSearchProduct.setText("");

                if (!cartItems.isEmpty()) {
                    new AlertDialog.Builder(CreatePurchaseOrderActivity.this)
                            .setTitle("Change Supplier?")
                            .setMessage("Switching suppliers will clear your current cart. Continue?")
                            .setPositiveButton("Yes", (dialog, which) -> {
                                cartItems.clear();
                                updateCartTotals();
                                loadProductsForSupplier(supplier);
                            })
                            .setNegativeButton("No", null)
                            .show();
                } else {
                    loadProductsForSupplier(supplier);
                }
            }

            @Override
            public void onSupplierDoubleClicked(SupplierItem supplier) {
                showSupplierDetailsDialog(supplier);
            }

            @Override
            public void onSupplierLongClicked(SupplierItem supplier) {
                promptDeleteSupplier(supplier);
            }
        });
        rvSuppliers.setAdapter(supplierAdapter);
        rvSupplierProducts.setLayoutManager(new GridLayoutManager(this, 2));
    }

    private void onProductSelected(Product product) {
        if (product != null) {
            promptForQuantityAndAddToCart(product);
        }
    }

    private void loadProductsForSupplier(SupplierItem supplier) {
        currentSupplierProducts = supplier.catalog != null
                ? new ArrayList<>(supplier.catalog)
                : new ArrayList<>();

        productAdapter = new SupplierProductAdapter(currentSupplierProducts,
                new SupplierProductAdapter.OnProductClickListener() {
                    @Override
                    public void onProductClick(Product product) {
                        promptForQuantityAndAddToCart(product);
                    }
                    @Override
                    public void onProductLongClick(Product product) {
                        promptDeleteProduct(product);
                    }
                });
        rvSupplierProducts.setAdapter(productAdapter);
        populateProductSpinner(currentSupplierProducts);
        filterProducts();

        if (currentSupplierProducts.isEmpty()) {
            Toast.makeText(this,
                    "No products found for " + supplier.name + ". Sync may still be in progress.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    // =========================================================================
    // CART & CHECKOUT LOGIC
    // =========================================================================
    private void promptForQuantityAndAddToCart(Product product) {
        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint("Quantity");

        new AlertDialog.Builder(this)
                .setTitle("Add to Cart")
                .setMessage("Enter order quantity for " + product.getProductName())
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    String qtyStr = input.getText().toString().trim();
                    if (!qtyStr.isEmpty()) {
                        int qty = Integer.parseInt(qtyStr);
                        addToCart(product, qty);
                    } else {
                        Toast.makeText(this, "Quantity cannot be empty", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addToCart(Product product, int qty) {
        boolean exists = false;
        for (POItem item : cartItems) {
            if (item.getProductName().equals(product.getProductName())) {
                item.setQuantity(item.getQuantity() + qty);
                exists = true;
                break;
            }
        }

        if (!exists) {
            cartItems.add(new POItem(product.getProductId(), product.getProductName(), qty, product.getCostPrice(), product.getUnit()));
        }

        updateCartTotals();
        Toast.makeText(this, "Added to cart", Toast.LENGTH_SHORT).show();
    }

    private void updateCartTotals() {
        double total = 0;
        int itemCount = 0;
        for (POItem item : cartItems) {
            total += item.getSubtotal();
            itemCount += item.getQuantity();
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

        if (selectedSupplier != null) {
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
        String[] paymentOptions = {"Cash", "GCash", "Custom (Bank Transfer / Credit Card)"};
        ArrayAdapter<String> paymentAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, paymentOptions);
        actvPayment.setAdapter(paymentAdapter);

        final String[] finalPaymentMethod = {"Cash"};
        actvPayment.setText("Cash", false);

        actvPayment.setOnItemClickListener((parent, view1, position, id) -> {
            String selected = paymentOptions[position];
            if (selected.startsWith("Custom")) {
                EditText input = new EditText(this);
                input.setHint("e.g. BDO Transfer, BPI Credit Card");
                input.setPadding(30, 40, 30, 40);

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
        if (selectedSupplier == null) { Toast.makeText(this, "No supplier selected", Toast.LENGTH_SHORT).show(); return; }
        if (cartItems.isEmpty()) { Toast.makeText(this, "Please add at least one item", Toast.LENGTH_SHORT).show(); return; }

        String id = poRef.push().getKey();
        if (id == null) return;

        String poNumber = "PO-" + System.currentTimeMillis() / 1000;
        double total = 0;
        for (POItem item : cartItems) total += item.getSubtotal();

        final double finalTotal = total;

        PurchaseOrder po = new PurchaseOrder(
                id, poNumber, selectedSupplier.name, selectedSupplier.phone, PurchaseOrder.STATUS_PENDING,
                System.currentTimeMillis(), total, new ArrayList<>(cartItems)
        );
        po.setOwnerAdminId(AuthManager.getInstance().getCurrentUserId());

        Map<String, Object> poData = po.toMap();
        poData.put("expectedDeliveryDate", System.currentTimeMillis() + (3L * 24 * 60 * 60 * 1000));
        poData.put("paymentMethod", paymentMethod);

        poRef.child(id).setValue(poData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Purchase Order Created Successfully", Toast.LENGTH_LONG).show();
                    fetchBusinessDetailsAndPrompt(poNumber, finalTotal, paymentMethod);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // =========================================================================
    // AUTOMATED EMAIL AND SMS COMMUNICATION LOGIC (WITH FIRESTORE CONTEXT)
    // =========================================================================
    // =========================================================================
    // PROFESSIONAL PDF GENERATION & COMMUNICATION ENGINE
    // =========================================================================
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

    private void promptDeleteProduct(Product product) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Product")
                .setMessage("Are you sure you want to completely delete " + product.getProductName() + " from your system inventory?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    productRepository.deleteProduct(product.getProductId(), new ProductRepository.OnProductDeletedListener() {
                        @Override
                        public void onProductDeleted(String archiveFilename) {
                            runOnUiThread(() -> Toast.makeText(CreatePurchaseOrderActivity.this, "Product deleted", Toast.LENGTH_SHORT).show());
                        }
                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> Toast.makeText(CreatePurchaseOrderActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show());
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
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.CYAN));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        TextView tvName = dialog.findViewById(R.id.tvDialogSupplierName);
        TextView tvCategories = dialog.findViewById(R.id.tvDialogSupplierCategories);
        TextView tvPhone = dialog.findViewById(R.id.tvDialogSupplierPhone);
        TextView tvEmail = dialog.findViewById(R.id.tvDialogSupplierEmail);
        TextView tvAddress = dialog.findViewById(R.id.tvDialogSupplierAddress);
        Button btnClose = dialog.findViewById(R.id.btnDialogClose);

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
}