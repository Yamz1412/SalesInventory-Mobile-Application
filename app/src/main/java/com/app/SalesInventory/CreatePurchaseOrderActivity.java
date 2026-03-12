package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
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
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.card.MaterialCardView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

        // Dynamically set layout size based on portrait vs landscape tablet mode
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
    }

    private void initViews() {
        rvSuppliers = findViewById(R.id.rvSuppliers);
        rvSupplierProducts = findViewById(R.id.rvSupplierProducts);
        tvCartSummary = findViewById(R.id.tvCartSummary);
        tvCartTotal = findViewById(R.id.tvCartTotal);
        tvSelectedSupplierName = findViewById(R.id.tvSelectedSupplierName);
        btnReviewCheckout = findViewById(R.id.btnReviewCheckout);
        btnQuickAddSupplier = findViewById(R.id.btnQuickAddSupplier);

        mainSplitLayout = findViewById(R.id.mainSplitLayout);
        layoutSupplierPane = findViewById(R.id.layoutSupplierPane);
        layoutProductsPane = findViewById(R.id.layoutProductsPane);

        cvCartBadge = findViewById(R.id.cvCartBadge);
        tvCartBadgeCount = findViewById(R.id.tvCartBadgeCount);

        etSearchSupplier = findViewById(R.id.etSearchSupplier);
        etSearchProduct = findViewById(R.id.etSearchProduct);

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

        DatabaseReference supRef = FirebaseDatabase.getInstance().getReference("Suppliers");
        supRef.orderByChild("ownerAdminId").equalTo(ownerId).addValueEventListener(new ValueEventListener() {
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

        productRepository.getAllProducts().observe(this, products -> {
            dbInventoryProducts.clear();
            if (products != null) {
                for (Product p : products) {
                    if (p.isActive() && !"Menu".equals(p.getProductType())) {
                        dbInventoryProducts.add(p);
                    }
                }
            }
            buildCatalogModels();
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
                boolean noSupplier = p.getSupplier() == null || p.getSupplier().trim().isEmpty();
                boolean matchesSupplier = p.getSupplier() != null && p.getSupplier().equalsIgnoreCase(s.getName());

                if (noSupplier || matchesSupplier) catalog.add(p);
            }
            uiSupplierItems.add(new SupplierItem(s.getId(), s.getName(), s.getEmail(), s.getContact(), s.getAddress(), s.getCategories(), catalog));
        }

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

    private void setupSearchListeners() {
        etSearchSupplier.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { filterSuppliers(s.toString()); }
        });

        etSearchProduct.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { filterProducts(s.toString()); }
        });
    }

    private void filterSuppliers(String text) {
        List<SupplierItem> filteredList = new ArrayList<>();
        for (SupplierItem item : uiSupplierItems) {
            if (item.name.toLowerCase().contains(text.toLowerCase()) ||
                    (item.categories != null && item.categories.toLowerCase().contains(text.toLowerCase()))) {
                filteredList.add(item);
            }
        }
        supplierAdapter.filterList(filteredList);

        selectedSupplier = null;
        tvSelectedSupplierName.setText("2. Select Products");
        if (productAdapter != null) productAdapter.filterList(new ArrayList<>());
    }

    private void filterProducts(String text) {
        if (currentSupplierProducts == null) return;
        List<Product> filteredList = new ArrayList<>();
        for (Product item : currentSupplierProducts) {
            if (item.getProductName().toLowerCase().contains(text.toLowerCase())) {
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

    private void loadProductsForSupplier(SupplierItem supplier) {
        currentSupplierProducts = supplier.catalog;
        productAdapter = new SupplierProductAdapter(currentSupplierProducts, new SupplierProductAdapter.OnProductClickListener() {
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
                    if(!qtyStr.isEmpty()) {
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
            if(cartItems.isEmpty()) dialog.dismiss();
        }, this::updateCartTotals);

        rvCart.setAdapter(dialogAdapter);

        // Payment Method Dropdown
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

                    // Fetch Business Name & Prompt Email/SMS
                    fetchBusinessDetailsAndPrompt(poNumber, finalTotal, paymentMethod);
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // =========================================================================
    // AUTOMATED EMAIL AND SMS COMMUNICATION LOGIC (WITH FIRESTORE CONTEXT)
    // =========================================================================
    private void fetchBusinessDetailsAndPrompt(String poNumber, double total, String paymentMethod) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = AuthManager.getInstance().getCurrentUserId(); // Fallback
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
                    // Fallback if firestore fails
                    promptSendPO(poNumber, total, paymentMethod, "Our Store", "Business");
                });
    }

    private void promptSendPO(String poNumber, double total, String paymentMethod, String bizName, String bizType) {
        StringBuilder msg = new StringBuilder();
        msg.append("Hello ").append(selectedSupplier.name).append(",\n\n");
        msg.append("This is ").append(bizName).append(" (").append(bizType).append(").\n");
        msg.append("Please find our new Purchase Order details below:\n\n");
        msg.append("PO Number: ").append(poNumber).append("\n");
        msg.append("Payment Method: ").append(paymentMethod).append("\n\n");
        msg.append("ITEMS ORDERED:\n");

        for (POItem item : cartItems) {
            msg.append("- ").append(item.getQuantity()).append(" ").append(item.getUnit())
                    .append(" x ").append(item.getProductName()).append("\n");
        }

        msg.append("\nTotal Amount: ₱").append(String.format(Locale.US, "%.2f", total)).append("\n\n");
        msg.append("Please confirm receipt of this order and the expected delivery schedule.\n\n");
        msg.append("Thank you,\n").append(bizName);

        String messageBody = msg.toString();
        String subject = "New Purchase Order: " + poNumber + " from " + bizName;

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Send Purchase Order");
        builder.setMessage("The PO is saved. Would you like to send this order to the supplier now?");

        builder.setPositiveButton("Email", (dialog, which) -> {
            sendEmail(selectedSupplier.email, subject, messageBody);
        });

        builder.setNeutralButton("SMS", (dialog, which) -> {
            sendSMS(selectedSupplier.phone, messageBody);
        });

        builder.setNegativeButton("Skip", (dialog, which) -> {
            finish();
        });

        builder.setCancelable(false);
        builder.show();
    }

    private void sendEmail(String emailAddress, String subject, String body) {
        if (emailAddress == null || emailAddress.isEmpty()) {
            Toast.makeText(this, "No email address found for this supplier", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        Intent emailIntent = new Intent(Intent.ACTION_SENDTO);
        emailIntent.setData(android.net.Uri.parse("mailto:"));
        emailIntent.putExtra(Intent.EXTRA_EMAIL, new String[]{emailAddress});
        emailIntent.putExtra(Intent.EXTRA_SUBJECT, subject);
        emailIntent.putExtra(Intent.EXTRA_TEXT, body);

        try {
            startActivity(Intent.createChooser(emailIntent, "Send Email using..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "No email clients installed.", Toast.LENGTH_SHORT).show();
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

    // =========================================================================
    // DELETION PROMPTS & UTILS
    // =========================================================================
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
                            if(productAdapter != null) productAdapter.filterList(new ArrayList<>());
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
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
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