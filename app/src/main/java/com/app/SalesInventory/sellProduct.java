package com.app.SalesInventory;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.FirebaseFirestore;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class sellProduct extends BaseActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 3001;

    private SalesRepository salesRepository;
    private ProductRepository productRepository;

    private double finalTotal;
    private double discountPercent;

    private TextInputEditText etDiscount;
    // IDs from activity_sell_product.xml
    private TextView tvTotalPrice;       // @+id/TotalPriceTV
    private Button btnConfirmSale;       // @+id/BtnConfirmSale
    private Button btnEditCart;          // @+id/BtnEditCart

    private Spinner spinnerPaymentMethod;       // @+id/spinnerPaymentMethod
    private View layoutCashSection;             // @+id/layoutCashSection
    private TextInputEditText etCashGiven;      // @+id/etCashGiven
    private TextView tvChange;                  // @+id/tvChange
    private View layoutEPaymentSection;         // @+id/layoutEPaymentSection
    private Button btnCaptureReceipt;           // @+id/btnCaptureReceipt
    private TextView tvReceiptStatus;           // @+id/tvReceiptStatus
    private ImageView imgReceiptPreview;        // @+id/imgReceiptPreview

    private RadioGroup rgDeliveryType;          // @+id/rgDeliveryType
    private RadioButton rbWalkIn;               // @+id/rbWalkIn
    private RadioButton rbDelivery;             // @+id/rbDelivery
    private View layoutDeliveryDetails;         // @+id/layoutDeliveryDetails
    private TextInputEditText etDeliveryName;   // @+id/etDeliveryName
    private TextInputEditText etDeliveryPhone;  // @+id/etDeliveryPhone
    private TextInputEditText etDeliveryAddress;// @+id/etDeliveryAddress
    private RadioGroup rgDeliveryPayment;       // @+id/rgDeliveryPayment
    private RadioButton rbDeliveryCOD;          // @+id/rbDeliveryCOD
    private RadioButton rbDeliveryEPayment;     // @+id/rbDeliveryEPayment

    private ListView cartListView;              // @+id/cartListView

    private boolean receiptCaptured = false;

    private AlertDialog loadingDialog;
    private ImageCapture imageCapture;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private Camera camera;

    private CartManager cartManager;
    private CriticalStockNotifier criticalNotifier;
    private ProductRepository.OnCriticalStockListener criticalListener;

    private ActivityResultLauncher<String> galleryReceiptLauncher;
    private Uri galleryReceiptUri;
    private MaterialButton btnDiscountSC;       // @+id/btnDiscountSC
    private MaterialButton btnDiscountPWD;      // @+id/btnDiscountPWD
    private MaterialButton btnDiscountEmp;      // @+id/btnDiscountEmp
    private TextInputLayout manualDiscountTIL;  // @+id/manual_discount_TIL
    private TextInputEditText etManualDiscount; // @+id/et_manual_discount
    private TextInputEditText etReferenceNumber;// @+id/etReferenceNumber
    private double currentDiscountPercentage = 0.0;

    // BUSINESS CACHE FOR RECEIPT
    private String cachedBusinessName = "Store Name";
    private String cachedBusinessLogoUrl = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sell_product);
        cartManager = CartManager.getInstance();

        initRepositories();
        loadBusinessProfile();
        initViews();
        initGalleryLauncher();
        setupListeners();
        updateCartUI();
        calculateTotalFromCart();
    }

    private void initRepositories() {
        salesRepository = SalesInventoryApplication.getSalesRepository();
        productRepository = SalesInventoryApplication.getProductRepository();
        cartManager = CartManager.getInstance();
        criticalNotifier = CriticalStockNotifier.getInstance();
        criticalListener = product -> runOnUiThread(() ->
                criticalNotifier.showCriticalDialog(this, product)
        );
        productRepository.registerCriticalStockListener(criticalListener);
    }

    private void loadBusinessProfile() {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId != null && !ownerId.isEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(ownerId)
                    .get()
                    .addOnSuccessListener(documentSnapshot -> {
                        if (documentSnapshot.exists()) {
                            String bName = documentSnapshot.getString("businessName");
                            String bLogo = documentSnapshot.getString("businessLogoUrl");
                            if (bName != null && !bName.isEmpty()) cachedBusinessName = bName;
                            if (bLogo != null && !bLogo.isEmpty()) cachedBusinessLogoUrl = bLogo;
                        }
                    });
        }
    }

    private void initViews() {
        // -- activity_sell_product.xml IDs --
        etReferenceNumber = findViewById(R.id.etReferenceNumber);

        try {
            btnDiscountSC  = findViewById(R.id.btnDiscountSC);
            btnDiscountPWD = findViewById(R.id.btnDiscountPWD);
            btnDiscountEmp = findViewById(R.id.btnDiscountEmp);
            manualDiscountTIL  = findViewById(R.id.manual_discount_TIL);
            etManualDiscount   = findViewById(R.id.et_manual_discount);

            if (btnDiscountSC != null) {
                btnDiscountSC.setOnClickListener(v -> applyPresetDiscount(20.0));
                btnDiscountPWD.setOnClickListener(v -> applyPresetDiscount(20.0));
                btnDiscountEmp.setOnClickListener(v -> {
                    View buttonsLayout = findViewById(R.id.discount_buttons_layout);
                    if (buttonsLayout != null) buttonsLayout.setVisibility(View.GONE);
                    if (manualDiscountTIL != null) manualDiscountTIL.setVisibility(View.VISIBLE);
                    if (etManualDiscount != null) etManualDiscount.requestFocus();
                    currentDiscountPercentage = 0.0;
                });
            }
            if (etManualDiscount != null) {
                etManualDiscount.addTextChangedListener(new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override
                    public void afterTextChanged(Editable s) {
                        String input = s.toString();
                        if (!input.isEmpty()) {
                            try { currentDiscountPercentage = Double.parseDouble(input); }
                            catch (NumberFormatException e) { currentDiscountPercentage = 0.0; }
                        } else {
                            currentDiscountPercentage = 0.0;
                        }
                        calculateTotalFromCart();
                    }
                });
            }
        } catch (Exception ignored) {}

        tvTotalPrice    = findViewById(R.id.TotalPriceTV);
        btnConfirmSale  = findViewById(R.id.BtnConfirmSale);
        btnEditCart     = findViewById(R.id.BtnEditCart);

        spinnerPaymentMethod  = findViewById(R.id.spinnerPaymentMethod);
        layoutCashSection     = findViewById(R.id.layoutCashSection);
        etCashGiven           = findViewById(R.id.etCashGiven);
        tvChange              = findViewById(R.id.tvChange);
        layoutEPaymentSection = findViewById(R.id.layoutEPaymentSection);
        btnCaptureReceipt     = findViewById(R.id.btnCaptureReceipt);
        tvReceiptStatus       = findViewById(R.id.tvReceiptStatus);
        imgReceiptPreview     = findViewById(R.id.imgReceiptPreview);

        rgDeliveryType      = findViewById(R.id.rgDeliveryType);
        rbWalkIn            = findViewById(R.id.rbWalkIn);
        rbDelivery          = findViewById(R.id.rbDelivery);
        layoutDeliveryDetails = findViewById(R.id.layoutDeliveryDetails);
        etDeliveryName      = findViewById(R.id.etDeliveryName);
        etDeliveryPhone     = findViewById(R.id.etDeliveryPhone);
        etDeliveryAddress   = findViewById(R.id.etDeliveryAddress);
        rgDeliveryPayment   = findViewById(R.id.rgDeliveryPayment);
        rbDeliveryCOD       = findViewById(R.id.rbDeliveryCOD);
        rbDeliveryEPayment  = findViewById(R.id.rbDeliveryEPayment);

        cartListView = findViewById(R.id.cartListView);

        String[] methods = new String[]{"Cash", "E-Payment"};
        ArrayAdapter<String> pmAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, methods);
        pmAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPaymentMethod.setAdapter(pmAdapter);

        tvTotalPrice.setText("₱0.00");

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setView(new android.widget.ProgressBar(this));
        builder.setMessage("Processing Sale... Please wait.");
        loadingDialog = builder.create();
    }

    private void applyPresetDiscount(double percentage) {
        currentDiscountPercentage = percentage;
        if (manualDiscountTIL != null) manualDiscountTIL.setVisibility(View.GONE);
        View buttonsLayout = findViewById(R.id.discount_buttons_layout);
        if (buttonsLayout != null) buttonsLayout.setVisibility(View.VISIBLE);
        calculateTotalFromCart();
    }

    private void initGalleryLauncher() {
        galleryReceiptLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        galleryReceiptUri = uri;
                        receiptCaptured = true;
                        tvReceiptStatus.setText("Receipt selected");
                        if (imgReceiptPreview != null) {
                            imgReceiptPreview.setVisibility(View.VISIBLE);
                            imgReceiptPreview.setImageURI(uri);
                        }
                        calculateChange();
                    }
                }
        );
    }

    private void setupListeners() {
        etCashGiven.addTextChangedListener(new TextWatcher() {
            private String current = "";
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                if (!s.toString().equals(current)) {
                    etCashGiven.removeTextChangedListener(this);
                    String cleanString = s.toString().replaceAll("[^\\d.]", "");

                    if (!cleanString.isEmpty()) {
                        try {
                            double parsed = Double.parseDouble(cleanString);
                            if (parsed > 1000000) {
                                parsed = 1000000;
                                Toast.makeText(sellProduct.this, "Maximum cash limit is ₱1,000,000", Toast.LENGTH_SHORT).show();
                            }
                            String formatted = cleanString.contains(".") ? cleanString : String.format(Locale.US, "%,d", (long) parsed);
                            current = formatted;
                            etCashGiven.setText(formatted);
                            etCashGiven.setSelection(formatted.length());
                        } catch (NumberFormatException ignored) {}
                    } else {
                        current = "";
                    }
                    etCashGiven.addTextChangedListener(this);
                    calculateChange();
                }
            }
        });

        spinnerPaymentMethod.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String method = (String) spinnerPaymentMethod.getSelectedItem();
                boolean isCash = "Cash".equals(method);
                setPaymentSections(isCash);
                calculateChange();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnCaptureReceipt.setOnClickListener(v -> checkCameraPermissionAndShowDialog());
        btnConfirmSale.setOnClickListener(v -> confirmSale());
        btnEditCart.setOnClickListener(v -> showEditCartDialog());

        rgDeliveryType.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbDelivery) {
                layoutDeliveryDetails.setVisibility(View.VISIBLE);
                etDeliveryName.setError(null);
                etDeliveryPhone.setError(null);
                etDeliveryAddress.setError(null);
            } else {
                layoutDeliveryDetails.setVisibility(View.GONE);
            }
        });
    }

    private void updateCartUI() {
        List<CartManager.CartItem> currentItems = new ArrayList<>(cartManager.getItems());

        android.widget.BaseAdapter freshAdapter = new android.widget.BaseAdapter() {
            @Override public int getCount() { return currentItems.size(); }
            @Override public Object getItem(int position) { return currentItems.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.item_cart_product, parent, false);
                }
                CartManager.CartItem item = currentItems.get(position);
                // IDs from item_cart_product.xml
                TextView tvName      = convertView.findViewById(R.id.tvCartName);
                TextView tvDetails   = convertView.findViewById(R.id.tvCartDetails);
                TextView tvQty       = convertView.findViewById(R.id.tvCartQty);
                TextView tvLineTotal = convertView.findViewById(R.id.tvCartLineTotal);
                ImageButton btnRemove = convertView.findViewById(R.id.btnRemoveItem);

                btnRemove.setOnClickListener(v -> {
                    cartManager.removeItemById(item.productId);
                    updateCartUI();
                    calculateTotalFromCart();
                });

                tvName.setText(item.productName);
                String detailText = "";
                if (item.size != null && !item.size.isEmpty()) detailText += item.size;
                if (item.addon != null && !item.addon.isEmpty()) {
                    if (!detailText.isEmpty()) detailText += " / ";
                    detailText += item.addon;
                }
                tvDetails.setText(detailText);
                tvQty.setText("x" + item.quantity);
                tvLineTotal.setText("₱" + String.format(Locale.US, "%.2f", item.getLineTotal()));
                return convertView;
            }
        };

        if (cartListView != null) {
            cartListView.setAdapter(freshAdapter);
        }
    }

    private void calculateTotalFromCart() {
        double subtotal = cartManager.getSubtotal();
        double discountAmount = subtotal * (currentDiscountPercentage / 100.0);
        finalTotal = subtotal - discountAmount;

        if (finalTotal < 0) finalTotal = 0;
        tvTotalPrice.setText(String.format(Locale.US, "₱%.2f", finalTotal));

        if (finalTotal > 1000000.0) {
            btnConfirmSale.setEnabled(false);
            Toast.makeText(this, "Total exceeds maximum allowed payment of ₱1,000,000.00", Toast.LENGTH_LONG).show();
        } else {
            calculateChange();
        }
    }

    private void calculateChange() {
        String method = (String) spinnerPaymentMethod.getSelectedItem();

        if ("E-Payment".equals(method)) {
            tvChange.setText("Change: ₱0.00");
            btnConfirmSale.setEnabled(finalTotal > 0 && receiptCaptured);
            return;
        }

        String cashStr = etCashGiven.getText().toString().trim().replace(",", "");
        if (cashStr.isEmpty()) {
            tvChange.setText("Change: ₱0.00");
            btnConfirmSale.setEnabled(false);
            return;
        }

        try {
            BigDecimal cashBD   = new BigDecimal(cashStr);
            BigDecimal finalBD  = BigDecimal.valueOf(finalTotal);
            BigDecimal changeBD = cashBD.subtract(finalBD);

            if (changeBD.compareTo(BigDecimal.ZERO) >= 0) {
                tvChange.setText(String.format(Locale.US, "Change: ₱%.2f", changeBD.doubleValue()));
                btnConfirmSale.setEnabled(true);
            } else {
                tvChange.setText("Insufficient Cash");
                btnConfirmSale.setEnabled(false);
            }
        } catch (NumberFormatException e) {
            tvChange.setText("Invalid Amount");
            btnConfirmSale.setEnabled(false);
        }
    }

    private void setPaymentSections(boolean isCash) {
        if (isCash) {
            layoutCashSection.setVisibility(View.VISIBLE);
            layoutEPaymentSection.setVisibility(View.GONE);
        } else {
            layoutCashSection.setVisibility(View.GONE);
            layoutEPaymentSection.setVisibility(View.VISIBLE);
        }
    }

    private void showEditCartDialog() {
        List<CartManager.CartItem> items = cartManager.getItems();
        if (items.isEmpty()) {
            Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
            return;
        }

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_edit_cart, null, false);
        ListView editListView      = dialogView.findViewById(R.id.editCartListView);
        Button btnChangeProduct    = dialogView.findViewById(R.id.btnEditChangeProduct);
        Button btnCancel           = dialogView.findViewById(R.id.btnEditCancel);
        Button btnSave             = dialogView.findViewById(R.id.btnEditSave);

        List<CartManager.CartItem> editItems = new ArrayList<>();
        for (CartManager.CartItem it : items) {
            editItems.add(new CartManager.CartItem(
                    it.productId, it.productName, it.unitPrice,
                    it.quantity, it.stock, it.size, it.addon
            ));
        }

        final android.widget.BaseAdapter[] editAdapter = new android.widget.BaseAdapter[1];

        editAdapter[0] = new android.widget.BaseAdapter() {
            @Override public int getCount() { return editItems.size(); }
            @Override public Object getItem(int position) { return editItems.get(position); }
            @Override public long getItemId(int position) { return position; }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.item_cart_product, parent, false);
                }
                CartManager.CartItem item = editItems.get(position);
                // IDs from item_cart_product.xml
                TextView tvName      = convertView.findViewById(R.id.tvCartName);
                TextView tvDetails   = convertView.findViewById(R.id.tvCartDetails);
                TextView tvQty       = convertView.findViewById(R.id.tvCartQty);
                TextView tvLineTotal = convertView.findViewById(R.id.tvCartLineTotal);

                tvName.setText(item.productName);
                String detailText = "";
                if (item.size != null && !item.size.isEmpty()) detailText += item.size;
                if (item.addon != null && !item.addon.isEmpty()) {
                    if (!detailText.isEmpty()) detailText += " / ";
                    detailText += item.addon;
                }
                tvDetails.setText(detailText);
                tvQty.setText("x" + item.quantity);
                tvLineTotal.setText("₱" + String.format(Locale.US, "%.2f", item.getLineTotal()));

                convertView.setOnClickListener(v -> showEditSingleItemDialog(item, editAdapter[0]));
                return convertView;
            }
        };

        editListView.setAdapter(editAdapter[0]);
        AlertDialog editDialog = new AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create();

        btnCancel.setOnClickListener(v -> editDialog.dismiss());
        btnChangeProduct.setOnClickListener(v -> {
            editDialog.dismiss();
            Intent intent = new Intent(sellProduct.this, SellList.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        });

        btnSave.setOnClickListener(v -> {
            cartManager.clear();
            for (CartManager.CartItem item : editItems) {
                if (item.quantity > 0) {
                    cartManager.addItem(
                            item.productId, item.productName, item.unitPrice,
                            item.quantity, item.stock, item.size, item.addon
                    );
                }
            }
            updateCartUI();
            calculateTotalFromCart();
            editDialog.dismiss();
        });

        editDialog.show();
    }

    private void showEditSingleItemDialog(CartManager.CartItem item, android.widget.BaseAdapter parentAdapter) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(padding, padding, padding, padding);

        TextView tvName = new TextView(this);
        tvName.setText(item.productName);
        layout.addView(tvName);

        TextInputEditText etQty = new TextInputEditText(this);
        etQty.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etQty.setHint("Quantity");
        etQty.setText(String.valueOf(item.quantity));
        layout.addView(etQty);

        builder.setView(layout);
        builder.setPositiveButton("OK", (d, w) -> {
            String qStr = etQty.getText() != null ? etQty.getText().toString().trim() : "";
            try {
                int q = Integer.parseInt(qStr);
                if (q <= 0 || q > item.stock) Toast.makeText(this, "Quantity must be between 1 and " + item.stock, Toast.LENGTH_SHORT).show();
                else {
                    item.quantity = q;
                    parentAdapter.notifyDataSetChanged();
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid quantity", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancel", (d, w) -> {});
        builder.setNeutralButton("Remove", (d, w) -> {
            item.quantity = 0;
            parentAdapter.notifyDataSetChanged();
        });
        builder.show();
    }

    private void checkCameraPermissionAndShowDialog() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            galleryReceiptLauncher.launch("image/*");
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private void confirmSale() {
        if (cartManager.getItems().isEmpty() || finalTotal <= 0) {
            Toast.makeText(this, "Cart is empty or total is zero", Toast.LENGTH_SHORT).show();
            return;
        }

        if (finalTotal > 1000000.0) {
            Toast.makeText(this, "Total exceeds maximum allowed payment of ₱1,000,000.00", Toast.LENGTH_LONG).show();
            return;
        }

        int deliveryChecked = rgDeliveryType.getCheckedRadioButtonId();
        boolean isDelivery = deliveryChecked == R.id.rbDelivery;
        String deliveryName    = etDeliveryName.getText().toString().trim();
        String deliveryPhone   = etDeliveryPhone.getText().toString().trim();
        String deliveryAddress = etDeliveryAddress.getText().toString().trim();

        String deliveryPayment = "COD";
        int selectedDelPayment = rgDeliveryPayment.getCheckedRadioButtonId();
        if (selectedDelPayment == R.id.rbDeliveryEPayment) deliveryPayment = "E-Payment";

        if (isDelivery) {
            if (deliveryName.isEmpty())    { etDeliveryName.setError("Required"); return; }
            if (deliveryPhone.isEmpty())   { etDeliveryPhone.setError("Required"); return; }
            if (deliveryAddress.isEmpty()) { etDeliveryAddress.setError("Required"); return; }
        }

        String method = (String) spinnerPaymentMethod.getSelectedItem();
        String paymentDetails = method;

        if ("Cash".equals(method)) {
            String cashStr = etCashGiven.getText().toString().trim().replace(",", "");
            if (cashStr.isEmpty()) {
                etCashGiven.setError("Enter cash amount");
                return;
            }
            try {
                double cashGiven = Double.parseDouble(cashStr);
                if (cashGiven < finalTotal) {
                    Toast.makeText(this, "Insufficient cash given", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid cash amount", Toast.LENGTH_SHORT).show();
                return;
            }
        } else {
            String refNum = etReferenceNumber.getText().toString().trim();
            if (refNum.isEmpty()) {
                etReferenceNumber.setError("Reference number is required");
                return;
            }
            if (!receiptCaptured) {
                Toast.makeText(this, "Please attach payment receipt first", Toast.LENGTH_SHORT).show();
                return;
            }
            paymentDetails = "E-Payment (Ref: " + refNum + ")";
        }

        String orderId = java.util.UUID.randomUUID().toString();
        prepareReceiptData(orderId, paymentDetails, isDelivery, deliveryName, deliveryPhone, deliveryAddress, deliveryPayment);
    }

    private void prepareReceiptData(String orderId, String paymentMethod, boolean isDelivery,
                                    String dName, String dPhone, String dAddr, String dPay) {
        if (loadingDialog != null && !loadingDialog.isShowing()) loadingDialog.show();

        Map<String, String> enrichedNames = new HashMap<>();
        fetchCartItemDetails(0, new ArrayList<>(cartManager.getItems()), enrichedNames,
                orderId, paymentMethod, isDelivery, dName, dPhone, dAddr, dPay);
    }

    private void fetchCartItemDetails(int index, List<CartManager.CartItem> items, Map<String, String> enrichedNames,
                                      String orderId, String paymentMethod, boolean isDelivery,
                                      String dName, String dPhone, String dAddr, String dPay) {

        if (index >= items.size()) {
            runOnUiThread(() -> {
                if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
                showReceiptDialogWithFetch(orderId, paymentMethod, isDelivery, dName, dPhone, dAddr, dPay, enrichedNames);
            });
            return;
        }

        CartManager.CartItem item = items.get(index);

        productRepository.getProductById(item.productId, new ProductRepository.OnProductFetchedListener() {
            @Override
            public void onProductFetched(Product p) {
                StringBuilder detailsStr = new StringBuilder();

                if (p != null) {
                    if (p.getAddonsList() != null && !p.getAddonsList().isEmpty()) {
                        detailsStr.append("Add-ons: ");
                        for (Map<String, Object> addon : p.getAddonsList()) {
                            detailsStr.append(addon.get("name")).append(", ");
                        }
                        detailsStr.setLength(detailsStr.length() - 2);
                        detailsStr.append(" | ");
                    }

                    if (p.getNotesList() != null && !p.getNotesList().isEmpty()) {
                        detailsStr.append("Notes: ");
                        for (Map<String, String> note : p.getNotesList()) {
                            detailsStr.append(note.get("type")).append(" ").append(note.get("value")).append(", ");
                        }
                        detailsStr.setLength(detailsStr.length() - 2);
                        detailsStr.append(" | ");
                    }

                    if (p.getBomList() != null && !p.getBomList().isEmpty()) {
                        detailsStr.append("BOM: ");
                        for (Map<String, Object> b : p.getBomList()) {
                            detailsStr.append(b.get("quantity")).append(b.get("unit")).append(" ").append(b.get("materialName")).append(", ");
                        }
                        detailsStr.setLength(detailsStr.length() - 2);
                    }
                }

                String finalDetails = detailsStr.toString();
                if (finalDetails.endsWith(" | ")) finalDetails = finalDetails.substring(0, finalDetails.length() - 3);

                enrichedNames.put(item.productId, finalDetails.trim());
                fetchCartItemDetails(index + 1, items, enrichedNames, orderId, paymentMethod, isDelivery, dName, dPhone, dAddr, dPay);
            }

            @Override
            public void onError(String error) {
                enrichedNames.put(item.productId, "");
                fetchCartItemDetails(index + 1, items, enrichedNames, orderId, paymentMethod, isDelivery, dName, dPhone, dAddr, dPay);
            }
        });
    }

    private void showReceiptDialogWithFetch(String orderId, String paymentMethod, boolean isDelivery,
                                            String dName, String dPhone, String dAddr, String dPay,
                                            Map<String, String> enrichedNames) {

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_receipt, null);
        AlertDialog receiptDialog = new AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create();

        // IDs from dialog_receipt.xml
        TextView tvBusinessName   = dialogView.findViewById(R.id.tvReceiptBusinessName);
        ImageView ivBusinessLogo  = dialogView.findViewById(R.id.ivReceiptBusinessLogo);
        TextView tvOrderId        = dialogView.findViewById(R.id.tvReceiptOrderId);
        TextView tvDateTime       = dialogView.findViewById(R.id.tvReceiptDateTime);
        ListView lvItems          = dialogView.findViewById(R.id.lvReceiptItems);   // ListView in dialog_receipt.xml
        TextView tvTotal          = dialogView.findViewById(R.id.tvReceiptTotal);
        TextView tvAmountPaid     = dialogView.findViewById(R.id.tvReceiptAmountPaid);
        TextView tvPaymentMethod  = dialogView.findViewById(R.id.tvReceiptPaymentMethod);
        View layoutChange         = dialogView.findViewById(R.id.layoutReceiptChange);
        TextView tvReceiptChange  = dialogView.findViewById(R.id.tvReceiptChange);
        Button btnFinalize        = dialogView.findViewById(R.id.btnFinalizeSale);
        Button btnPrint           = dialogView.findViewById(R.id.btnPrintReceipt);

        if (tvBusinessName != null) tvBusinessName.setText(cachedBusinessName);
        if (ivBusinessLogo != null) {
            if (cachedBusinessLogoUrl != null && !cachedBusinessLogoUrl.isEmpty()) {
                ivBusinessLogo.setVisibility(View.VISIBLE);
                Glide.with(this).load(cachedBusinessLogoUrl).into(ivBusinessLogo);
            } else {
                ivBusinessLogo.setVisibility(View.GONE);
            }
        }

        if (tvOrderId != null) tvOrderId.setText("Order ID: " + orderId.substring(0, 8).toUpperCase());
        if (tvTotal != null) tvTotal.setText(String.format("₱%.2f", finalTotal));

        if (tvDateTime != null) {
            String currentDateTime = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
            tvDateTime.setText("Date & Time: " + currentDateTime);
        }

        // FIX: Use a proper ArrayAdapter on the ListView instead of the non-existent setList() method.
        if (lvItems != null) {
            List<CartManager.CartItem> receiptItems = new ArrayList<>(cartManager.getItems());

            android.widget.BaseAdapter receiptAdapter = new android.widget.BaseAdapter() {
                @Override public int getCount() { return receiptItems.size(); }
                @Override public Object getItem(int position) { return receiptItems.get(position); }
                @Override public long getItemId(int position) { return position; }

                @Override
                public View getView(int position, View convertView, ViewGroup parent) {
                    if (convertView == null) {
                        convertView = getLayoutInflater().inflate(R.layout.item_cart_product, parent, false);
                    }
                    CartManager.CartItem item = receiptItems.get(position);
                    // IDs from item_cart_product.xml
                    TextView tvName      = convertView.findViewById(R.id.tvCartName);
                    TextView tvDetails   = convertView.findViewById(R.id.tvCartDetails);
                    TextView tvQty       = convertView.findViewById(R.id.tvCartQty);
                    TextView tvLineTotal = convertView.findViewById(R.id.tvCartLineTotal);
                    ImageButton btnRemove = convertView.findViewById(R.id.btnRemoveItem);

                    // Receipt is read-only — hide the remove button
                    if (btnRemove != null) btnRemove.setVisibility(View.GONE);

                    tvName.setText(item.productName);

                    String extraDetails = enrichedNames.get(item.productId);
                    if (extraDetails == null) extraDetails = "";
                    tvDetails.setText(extraDetails.isEmpty() ? "" : extraDetails.replace(" | ", "\n"));

                    tvQty.setText("x" + item.quantity);
                    tvLineTotal.setText("₱" + String.format(Locale.US, "%.2f", item.getLineTotal()));
                    return convertView;
                }
            };

            lvItems.setAdapter(receiptAdapter);
        }

        if (tvPaymentMethod != null) tvPaymentMethod.setText(paymentMethod);

        if ("Cash".equals(paymentMethod) || paymentMethod.startsWith("Cash")) {
            String cashInput = etCashGiven.getText().toString().replace(",", "");
            double cashValue = cashInput.isEmpty() ? 0 : Double.parseDouble(cashInput);
            if (tvAmountPaid != null) tvAmountPaid.setText(String.format("₱%.2f", cashValue));
            if (layoutChange != null) layoutChange.setVisibility(View.VISIBLE);
            if (tvReceiptChange != null) tvReceiptChange.setText(String.format("₱%.2f", cashValue - finalTotal));
        } else {
            if (tvAmountPaid != null) tvAmountPaid.setText(String.format("₱%.2f", finalTotal));
            if (layoutChange != null) layoutChange.setVisibility(View.GONE);
        }

        if (btnPrint != null) btnPrint.setVisibility(View.GONE);

        if (btnFinalize != null) {
            btnFinalize.setOnClickListener(v -> {
                receiptDialog.dismiss();
                saveSale(paymentMethod, isDelivery, dName, dPhone, dAddr, dPay, enrichedNames);
            });
        }

        receiptDialog.show();
    }

    private void saveSale(String paymentMethod, boolean isDelivery, String dName, String dPhone,
                          String dAddr, String dPay, Map<String, String> enrichedNames) {
        runOnUiThread(() -> { if (loadingDialog != null && !loadingDialog.isShowing()) loadingDialog.show(); });

        List<CartManager.CartItem> items = new ArrayList<>(cartManager.getItems());
        if (items.isEmpty()) {
            if (loadingDialog != null) loadingDialog.dismiss();
            Toast.makeText(this, "Cannot save: Cart is empty.", Toast.LENGTH_SHORT).show();
            return;
        }

        AuthManager.getInstance().refreshCurrentUserStatus(success -> {
            double subtotal = cartManager.getSubtotal();
            long now = System.currentTimeMillis();
            String orderId = java.util.UUID.randomUUID().toString();
            String deliveryType   = isDelivery ? "DELIVERY" : "WALK_IN";
            String deliveryStatus = isDelivery ? "PENDING" : "DELIVERED";
            long deliveryDate     = isDelivery ? 0 : now;

            saveCartItemRecursively(0, items, orderId, subtotal, now, paymentMethod,
                    deliveryType, deliveryStatus, deliveryDate, dName, dPhone, dAddr, dPay, enrichedNames);
        });
    }

    private void saveCartItemRecursively(int index, List<CartManager.CartItem> items, String orderId,
                                         double subtotal, long now, String paymentMethod,
                                         String deliveryType, String deliveryStatus, long deliveryDate,
                                         String dName, String dPhone, String dAddr, String dPay,
                                         Map<String, String> enrichedNames) {

        if (index >= items.size()) {
            runOnUiThread(() -> {
                if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
                cartManager.clear();
                Toast.makeText(sellProduct.this, "Sale Recorded Successfully!", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(sellProduct.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                finish();
            });
            return;
        }

        CartManager.CartItem item = items.get(index);
        double lineTotal = item.getLineTotal();
        double ratio     = subtotal == 0 ? 0 : lineTotal / subtotal;
        double lineFinal = finalTotal * ratio;

        String tempProductName = item.productName;
        String extras = enrichedNames.get(item.productId);
        if (extras != null && !extras.isEmpty()) {
            tempProductName += " | " + extras;
        }

        final String finalDbProductName = tempProductName;

        productRepository.getProductById(item.productId, new ProductRepository.OnProductFetchedListener() {
            @Override
            public void onProductFetched(Product p) {
                Sales sale = new Sales();
                sale.setOrderId(orderId);
                sale.setProductId(item.productId);
                sale.setProductName(finalDbProductName);
                sale.setQuantity(item.quantity);
                sale.setPrice(item.unitPrice);
                sale.setTotalPrice(lineFinal);

                if (p != null) {
                    sale.setTotalCost(p.getCostPrice() * item.quantity);
                } else {
                    sale.setTotalCost(0.0);
                }

                sale.setPaymentMethod(paymentMethod);
                sale.setDate(now);
                sale.setTimestamp(now);
                sale.setDeliveryType(deliveryType);
                sale.setDeliveryStatus(deliveryStatus);
                sale.setDeliveryDate(deliveryDate);
                sale.setDeliveryName(dName);
                sale.setDeliveryPhone(dPhone);
                sale.setDeliveryAddress(dAddr);
                sale.setDeliveryPaymentMethod(dPay);

                salesRepository.addSale(sale, new SalesRepository.OnSaleAddedListener() {
                    @Override
                    public void onSaleAdded(String saleId) {
                        productRepository.updateProductQuantity(item.productId,
                                Math.max(0, item.stock - item.quantity),
                                new ProductRepository.OnProductUpdatedListener() {
                                    @Override
                                    public void onProductUpdated() {
                                        if (p != null) {
                                            List<Map<String, Object>> bomList = p.getBomList();
                                            if (bomList != null && !bomList.isEmpty()) {
                                                for (Map<String, Object> bomItem : bomList) {
                                                    String materialName = (String) bomItem.get("materialName");
                                                    double deductQty = 0;

                                                    if (bomItem.get("quantity") instanceof Number) {
                                                        deductQty = ((Number) bomItem.get("quantity")).doubleValue();
                                                    } else if (bomItem.get("quantity") instanceof String) {
                                                        try { deductQty = Double.parseDouble((String) bomItem.get("quantity")); } catch (Exception ignored) {}
                                                    }

                                                    if (materialName != null && !materialName.isEmpty() && deductQty > 0) {
                                                        double totalDeduction = deductQty * item.quantity;
                                                        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Products");
                                                        ref.orderByChild("productName").equalTo(materialName)
                                                                .addListenerForSingleValueEvent(new ValueEventListener() {
                                                                    @Override
                                                                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                                                                        for (DataSnapshot ds : snapshot.getChildren()) {
                                                                            Product material = ds.getValue(Product.class);
                                                                            if (material != null) {
                                                                                int currentQty = material.getQuantity();
                                                                                int newQty = Math.max(0, currentQty - (int) Math.ceil(totalDeduction));
                                                                                ds.getRef().child("quantity").setValue(newQty);
                                                                            }
                                                                        }
                                                                    }
                                                                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                                                                });
                                                    }
                                                }
                                            }
                                        }
                                        saveCartItemRecursively(index + 1, items, orderId, subtotal, now,
                                                paymentMethod, deliveryType, deliveryStatus, deliveryDate,
                                                dName, dPhone, dAddr, dPay, enrichedNames);
                                    }
                                    @Override public void onError(String error) {
                                        saveCartItemRecursively(index + 1, items, orderId, subtotal, now,
                                                paymentMethod, deliveryType, deliveryStatus, deliveryDate,
                                                dName, dPhone, dAddr, dPay, enrichedNames);
                                    }
                                });
                    }
                    @Override public void onError(String error) { handleSaveError(error); }
                });
            }
            @Override public void onError(String error) { handleSaveError(error); }
        });
    }

    private void handleSaveError(String msg) {
        runOnUiThread(() -> {
            if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
            Toast.makeText(sellProduct.this, "Error: " + msg, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (productRepository != null && criticalListener != null) {
            productRepository.unregisterCriticalStockListener(criticalListener);
        }
    }
}