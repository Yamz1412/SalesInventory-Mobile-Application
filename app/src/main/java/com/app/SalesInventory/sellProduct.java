package com.app.SalesInventory;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
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
import android.widget.EditText;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class sellProduct extends BaseActivity {

    private static final int REQUEST_CAMERA_PERMISSION = 3001;

    private SalesRepository salesRepository;
    private ProductRepository productRepository;

    private double finalTotal;

    private TextView tvTotalPrice;
    private Button btnConfirmSale;
    private Button btnEditCart;

    private Spinner spinnerPaymentMethod;
    private View layoutCashSection;
    private TextInputEditText etCashGiven;
    private TextView tvChange;

    private View layoutEPaymentSection;

    private Button btnCaptureReceipt;
    private TextView tvReceiptStatus;
    private ImageView imgReceiptPreview;

    private RadioGroup rgDeliveryType;
    private RadioButton rbDelivery;
    private View layoutDeliveryDetails;
    private TextInputEditText etDeliveryName;
    private TextInputEditText etDeliveryPhone;
    private TextInputEditText etDeliveryAddress;

    private ListView cartListView;
    private boolean receiptCaptured = false;

    private AlertDialog loadingDialog;
    private ActivityResultLauncher<String> galleryReceiptLauncher;
    private Uri galleryReceiptUri;

    private CartManager cartManager;
    private CriticalStockNotifier criticalNotifier;
    private ProductRepository.OnCriticalStockListener criticalListener;

    private MaterialButton btnDiscountSC, btnDiscountPWD, btnDiscountEmp;
    private TextInputLayout manualDiscountTIL;
    private TextInputEditText etManualDiscount, etReferenceNumber;

    private double currentDiscountPercentage = 0.0;
    private String activeDiscountType = "";

    private String cachedBusinessName = "Store Name";
    private String cachedBusinessLogoUrl = null;

    private List<Product> cachedInventoryList = new ArrayList<>();

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

        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                cachedInventoryList.clear();
                cachedInventoryList.addAll(products);
            }
        });
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
        etReferenceNumber = findViewById(R.id.etReferenceNumber);

        try {
            btnDiscountSC  = findViewById(R.id.btnDiscountSC);
            btnDiscountPWD = findViewById(R.id.btnDiscountPWD);
            btnDiscountEmp = findViewById(R.id.btnDiscountEmp);
            manualDiscountTIL  = findViewById(R.id.manual_discount_TIL);
            etManualDiscount   = findViewById(R.id.et_manual_discount);

            if (btnDiscountSC != null) {
                btnDiscountSC.setOnClickListener(v -> togglePresetDiscount(20.0, "SC"));
                btnDiscountPWD.setOnClickListener(v -> togglePresetDiscount(20.0, "PWD"));

                btnDiscountEmp.setOnClickListener(v -> {
                    View buttonsLayout = findViewById(R.id.discount_buttons_layout);
                    if (buttonsLayout != null) buttonsLayout.setVisibility(View.GONE);
                    if (manualDiscountTIL != null) manualDiscountTIL.setVisibility(View.VISIBLE);
                    if (etManualDiscount != null) {
                        etManualDiscount.setText("");
                        etManualDiscount.requestFocus();
                    }
                    currentDiscountPercentage = 0.0;
                    activeDiscountType = "MANUAL";
                    calculateTotalFromCart();
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
                            try {
                                currentDiscountPercentage = Double.parseDouble(input);
                                activeDiscountType = "MANUAL";
                            }
                            catch (NumberFormatException e) { currentDiscountPercentage = 0.0; }
                        } else {
                            currentDiscountPercentage = 0.0;
                            activeDiscountType = "";
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
        rbDelivery          = findViewById(R.id.rbDelivery);
        layoutDeliveryDetails = findViewById(R.id.layoutDeliveryDetails);
        etDeliveryName      = findViewById(R.id.etDeliveryName);
        etDeliveryPhone     = findViewById(R.id.etDeliveryPhone);
        etDeliveryAddress   = findViewById(R.id.etDeliveryAddress);

        cartListView = findViewById(R.id.cartListView);

        String[] methods = new String[]{"Cash", "GCash"};
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

    private void togglePresetDiscount(double percentage, String type) {
        if (activeDiscountType.equals(type)) {
            currentDiscountPercentage = 0.0;
            activeDiscountType = "";
            Toast.makeText(this, type + " Discount Removed", Toast.LENGTH_SHORT).show();
        } else {
            currentDiscountPercentage = percentage;
            activeDiscountType = type;
            if (manualDiscountTIL != null) manualDiscountTIL.setVisibility(View.GONE);
            Toast.makeText(this, type + " Discount Applied", Toast.LENGTH_SHORT).show();
        }
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

                StringBuilder detailBuilder = new StringBuilder();
                if (item.size != null && !item.size.isEmpty()) {
                    detailBuilder.append("Size: ").append(item.size);
                }
                if (item.addon != null && !item.addon.isEmpty()) {
                    if (detailBuilder.length() > 0) detailBuilder.append("\n");
                    detailBuilder.append(item.addon);
                }

                if (detailBuilder.length() > 0) {
                    tvDetails.setVisibility(View.VISIBLE);
                    tvDetails.setText(detailBuilder.toString());
                } else {
                    tvDetails.setVisibility(View.GONE);
                }

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

        if ("GCash".equals(method)) {
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
            paymentDetails = "GCash (Ref: " + refNum + ")";
        }

        String orderId = java.util.UUID.randomUUID().toString();
        prepareReceiptData(orderId, paymentDetails, isDelivery, deliveryName, deliveryPhone, deliveryAddress, method);
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
        String extra = "";
        if (item.size != null && !item.size.isEmpty()) extra += item.size;
        if (item.addon != null && !item.addon.isEmpty()) {
            if (!extra.isEmpty()) extra += " | ";
            extra += item.addon;
        }
        enrichedNames.put(item.productId, extra);

        fetchCartItemDetails(index + 1, items, enrichedNames, orderId, paymentMethod, isDelivery, dName, dPhone, dAddr, dPay);
    }

    @android.annotation.SuppressLint("MissingPermission")
    private boolean isBluetoothPrinterConnected() {
        try {
            android.bluetooth.BluetoothAdapter adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) return false;

            java.util.Set<android.bluetooth.BluetoothDevice> pairedDevices = adapter.getBondedDevices();
            if (pairedDevices != null) {
                for (android.bluetooth.BluetoothDevice device : pairedDevices) {
                    if (device.getBluetoothClass() != null) {
                        int majorClass = device.getBluetoothClass().getMajorDeviceClass();
                        if (majorClass == android.bluetooth.BluetoothClass.Device.Major.IMAGING) {
                            return true;
                        }
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private void showReceiptDialogWithFetch(String orderId, String paymentMethod, boolean isDelivery,
                                            String dName, String dPhone, String dAddr, String dPay,
                                            Map<String, String> enrichedNames) {

        View dialogView = getLayoutInflater().inflate(R.layout.dialog_receipt, null);
        AlertDialog receiptDialog = new AlertDialog.Builder(this).setView(dialogView).setCancelable(false).create();

        TextView tvBusinessName   = dialogView.findViewById(R.id.tvReceiptBusinessName);
        ImageView ivBusinessLogo  = dialogView.findViewById(R.id.ivReceiptBusinessLogo);
        TextView tvOrderId        = dialogView.findViewById(R.id.tvReceiptOrderId);
        TextView tvInvoiceNo      = dialogView.findViewById(R.id.tvReceiptInvoiceNo);
        TextView tvDateTime       = dialogView.findViewById(R.id.tvReceiptDateTime);
        ListView lvItems          = dialogView.findViewById(R.id.lvReceiptItems);
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

        String invoiceNumber = "INV-" + String.format(Locale.US, "%06d", (System.currentTimeMillis() % 1000000));
        if (tvInvoiceNo != null) tvInvoiceNo.setText("Invoice No: " + invoiceNumber);

        if (tvTotal != null) tvTotal.setText(String.format("₱%.2f", finalTotal));

        if (tvDateTime != null) {
            String currentDateTime = new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss", java.util.Locale.getDefault()).format(new java.util.Date());
            tvDateTime.setText("Date & Time: " + currentDateTime);
        }

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
                    TextView tvName      = convertView.findViewById(R.id.tvCartName);
                    TextView tvDetails   = convertView.findViewById(R.id.tvCartDetails);
                    TextView tvQty       = convertView.findViewById(R.id.tvCartQty);
                    TextView tvLineTotal = convertView.findViewById(R.id.tvCartLineTotal);
                    ImageButton btnRemove = convertView.findViewById(R.id.btnRemoveItem);

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

        if (btnPrint != null) {
            if (isBluetoothPrinterConnected()) {
                btnPrint.setVisibility(View.VISIBLE);
                btnPrint.setOnClickListener(v -> {
                    Toast.makeText(this, "Connecting to Bluetooth Printer...", Toast.LENGTH_SHORT).show();
                });
            } else {
                btnPrint.setVisibility(View.GONE);
            }
        }

        if (btnFinalize != null) {
            btnFinalize.setOnClickListener(v -> {
                receiptDialog.dismiss();
                saveSale(paymentMethod, isDelivery, dName, dPhone, dAddr, dPay, enrichedNames);
            });
        }

        receiptDialog.show();
    }

    @SuppressWarnings("unchecked")
    private void saveSale(String paymentMethod, boolean isDelivery, String dName, String dPhone,
                          String dAddr, String dPay, Map<String, String> enrichedNames) {

        runOnUiThread(() -> { if (loadingDialog != null && !loadingDialog.isShowing()) loadingDialog.show(); });

        List<CartManager.CartItem> items = new ArrayList<>(cartManager.getItems());
        if (items.isEmpty()) {
            if (loadingDialog != null) loadingDialog.dismiss();
            return;
        }

        double subtotal = cartManager.getSubtotal();
        long now = System.currentTimeMillis();
        String orderId = java.util.UUID.randomUUID().toString();

        int[] savedCount = {0};

        for (CartManager.CartItem item : items) {

            double lineTotal = item.getLineTotal();
            double ratio = subtotal == 0 ? 0 : lineTotal / subtotal;
            double lineFinal = finalTotal * ratio;
            double lineDiscount = lineTotal - lineFinal;

            StringBuilder nameBuilder = new StringBuilder(item.productName);
            if (item.size != null && !item.size.isEmpty()) nameBuilder.append(" (").append(item.size).append(")");
            if (item.addon != null && !item.addon.isEmpty()) nameBuilder.append(" [").append(item.addon).append("]");
            String finalDbProductName = nameBuilder.toString();

            Product p = null;
            for(Product cached : cachedInventoryList) {
                if(cached.getProductId().equals(item.productId)) { p = cached; break; }
            }
            double itemCost = p != null ? (p.getCostPrice() * item.quantity) : 0.0;

            Sales sale = new Sales();
            sale.setOrderId(orderId);
            sale.setProductId(item.productId);
            sale.setProductName(finalDbProductName);
            sale.setQuantity(item.quantity);
            sale.setPrice(item.unitPrice);
            sale.setTotalPrice(lineFinal);
            sale.setTotalCost(itemCost);
            sale.setDiscountAmount(lineDiscount);
            sale.setPaymentMethod(paymentMethod);
            sale.setDate(now);
            sale.setTimestamp(now);
            sale.setDeliveryType(isDelivery ? "Delivery" : "Walk-in");
            sale.setDeliveryStatus(isDelivery ? "PENDING" : "DELIVERED");
            sale.setDeliveryDate(isDelivery ? 0 : now);
            sale.setDeliveryName(dName);
            sale.setDeliveryPhone(dPhone);
            sale.setDeliveryAddress(dAddr);
            sale.setDeliveryPaymentMethod(dPay);

            if(p != null) {
                if (p.getBomList() == null || p.getBomList().isEmpty()) {
                    p.setQuantity(Math.max(0, p.getQuantity() - item.quantity));
                    productRepository.updateProductQuantity(p.getProductId(), p.getQuantity(), null);
                }

                if (p.getBomList() != null && !p.getBomList().isEmpty()) {
                    for (Map<String, Object> bomItem : p.getBomList()) {
                        String matName = (String) bomItem.get("materialName");
                        double bQty = 0;
                        try { bQty = Double.parseDouble(String.valueOf(bomItem.get("quantity"))); } catch (Exception ignored) {}
                        String bUnit = (String) bomItem.get("unit");
                        deductFromMaterial(matName, bQty * item.quantity, bUnit);
                    }
                }

                if (p.getSizesList() != null && item.size != null && !item.size.isEmpty()) {
                    for (Map<String, Object> sizeItem : p.getSizesList()) {
                        String sName = (String) sizeItem.get("name");
                        if (item.size.equals(sName)) {
                            String linkedMat = (String) sizeItem.get("linkedMaterial");
                            double deductQty = 0;
                            try { deductQty = Double.parseDouble(String.valueOf(sizeItem.get("deductQty"))); } catch (Exception ignored) {}
                            if (linkedMat != null && !linkedMat.isEmpty() && deductQty > 0) {
                                deductFromMaterial(linkedMat, deductQty * item.quantity, "pcs");
                            }
                            break;
                        }
                    }
                }

                if (p.getAddonsList() != null && item.addon != null && !item.addon.isEmpty()) {
                    for (Map<String, Object> addonItem : p.getAddonsList()) {
                        String aName = (String) addonItem.get("name");
                        if (item.addon.contains(aName)) {
                            String linkedMat = (String) addonItem.get("linkedMaterial");
                            double deductQty = 0;
                            try { deductQty = Double.parseDouble(String.valueOf(addonItem.get("deductQty"))); } catch (Exception ignored) {}
                            String aUnit = (String) addonItem.get("unit");
                            if (linkedMat != null && !linkedMat.isEmpty() && deductQty > 0) {
                                deductFromMaterial(linkedMat, deductQty * item.quantity, aUnit != null ? aUnit : "pcs");
                            }
                        }
                    }
                }
            }

            salesRepository.addSale(sale, new SalesRepository.OnSaleAddedListener() {
                @Override
                public void onSaleAdded(String saleId) {
                    savedCount[0]++;
                    if (savedCount[0] == items.size()) {
                        finalizeSaleCompletion(paymentMethod, orderId);
                    }
                }
                @Override
                public void onError(String error) {
                    savedCount[0]++;
                    if (savedCount[0] == items.size()) {
                        finalizeSaleCompletion(paymentMethod, orderId);
                    }
                }
            });
        }
    }

    private void finalizeSaleCompletion(String paymentMethod, String orderId) {
        updateCashManagementWallet(paymentMethod, finalTotal, orderId);

        for (CartManager.CartItem item : cartManager.getItems()) {
            SalesInventoryApplication.getProductRepository().getProductById(item.productId, new ProductRepository.OnProductFetchedListener() {
                @Override
                public void onProductFetched(Product p) {
                    if (p != null) {
                        CriticalStockNotifier.getInstance().showCriticalDialog(sellProduct.this, p);

                        if (p.getCriticalLevel() > 0 && p.getQuantity() <= p.getCriticalLevel()) {
                            NotificationHelper.showNotification(sellProduct.this,
                                    "🚨 Critical Stock: " + p.getProductName(),
                                    "Only " + p.getQuantity() + " left in stock!",
                                    p.getProductId());
                        }
                    }
                }
                @Override
                public void onError(String error) {}
            });
        }

        StringBuilder receiptText = new StringBuilder();
        receiptText.append("Order ID: ").append(orderId).append("\n");
        receiptText.append("Date: ").append(new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault()).format(new java.util.Date())).append("\n\n");
        for (CartManager.CartItem item : cartManager.getItems()) {
            receiptText.append(item.quantity).append("x ").append(item.productName).append("   ₱").append(item.getLineTotal()).append("\n");
        }
        receiptText.append("\n-------------------\n");
        receiptText.append("TOTAL: ₱").append(finalTotal).append("\n");
        receiptText.append("PAYMENT: ").append(paymentMethod).append("\n");

        ReceiptStorageManager.generateAndSaveReceipt(this, orderId, receiptText.toString());

        runOnUiThread(() -> {
            if (loadingDialog != null) loadingDialog.dismiss();
            cartManager.clear();

            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_receipt, null);
            AlertDialog receiptDialog = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            TextView tvOrderId = dialogView.findViewById(R.id.tvReceiptOrderId);
            TextView tvTotal = dialogView.findViewById(R.id.tvReceiptTotal);
            TextView tvPaymentMethod = dialogView.findViewById(R.id.tvReceiptPaymentMethod);
            Button btnPrint = dialogView.findViewById(R.id.btnPrintReceipt);
            Button btnFinalize = dialogView.findViewById(R.id.btnFinalizeSale);

            if (tvOrderId != null) tvOrderId.setText("Order ID: #" + orderId);
            if (tvTotal != null) tvTotal.setText("₱" + String.format(java.util.Locale.US, "%.2f", finalTotal));
            if (tvPaymentMethod != null) tvPaymentMethod.setText(paymentMethod);

            if (btnPrint != null) {
                btnPrint.setVisibility(View.VISIBLE);
                btnPrint.setOnClickListener(v -> {
                    ReceiptPrinterManager.printReceipt(sellProduct.this, orderId, receiptText.toString());
                });
            }

            if (btnFinalize != null) {
                btnFinalize.setOnClickListener(v -> {
                    receiptDialog.dismiss();
                    finish();
                });
            }

            if (receiptDialog.getWindow() != null) {
                receiptDialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            }

            receiptDialog.show();
        });
    }

    private void deductFromMaterial(String materialName, double deductAmt, String bUnit) {
        if (materialName == null || materialName.isEmpty() || deductAmt <= 0) return;

        Product material = null;
        for (Product p : cachedInventoryList) {
            if (p != null && materialName.equalsIgnoreCase(p.getProductName())) {
                material = p;
                break;
            }
        }

        if (material == null || material.getQuantity() <= 0) return;

        int ppu = material.getPiecesPerUnit() > 0 ? material.getPiecesPerUnit() : 1;

        try {
            String matUnit = material.getUnit() != null ? material.getUnit() : "pcs";
            String targetUnit = bUnit != null ? bUnit : "pcs";

            Object[] conversion = UnitConverterUtil.convertBaseInventoryUnit(
                    material.getQuantity(), matUnit, targetUnit, ppu);

            double convertedQty = (double) conversion[0];
            String newUnit = (String) conversion[1];
            boolean mUnitChanged = (boolean) conversion[2];

            double finalDeductAmt = UnitConverterUtil.calculateDeductionAmount(
                    deductAmt, newUnit, targetUnit, ppu);

            double finalMQty = UnitConverterUtil.calculateNewStock(convertedQty, finalDeductAmt);

            double unitCost = material.getCostPrice() / convertedQty;
            double deductedCost = finalDeductAmt * unitCost;
            double newTotalCost = Math.max(0.0, material.getCostPrice() - deductedCost);

            if (mUnitChanged) {
                FirebaseFirestore.getInstance().collection(FirestoreManager.getInstance().getUserProductsPath())
                        .document(material.getProductId()).update(
                                "unit", newUnit,
                                "reorderLevel", material.getReorderLevel() * ppu,
                                "criticalLevel", material.getCriticalLevel() * ppu
                        );
            }

            productRepository.updateProductQuantityAndCost(material.getProductId(), finalMQty, newTotalCost, null);

            material.setQuantity(finalMQty);
            material.setCostPrice(newTotalCost);
            material.setUnit(newUnit);

        } catch (Exception e) {
            double finalMQty = material.getQuantity() - deductAmt;
            double unitCost = material.getCostPrice() / material.getQuantity();
            double newTotalCost = Math.max(0.0, material.getCostPrice() - (deductAmt * unitCost));

            productRepository.updateProductQuantityAndCost(material.getProductId(), Math.max(0, finalMQty), newTotalCost, null);

            material.setQuantity(Math.max(0, finalMQty));
            material.setCostPrice(newTotalCost);
        }
    }

    private void saveCartItemRecursively(int index, List<CartManager.CartItem> items, String orderId,
                                         double subtotal, long now, String paymentMethod,
                                         String deliveryType, String deliveryStatus, long deliveryDate,
                                         String dName, String dPhone, String dAddr, String dPay,
                                         Map<String, String> enrichedNames) {

        if (index >= items.size()) {
            updateCashManagementWallet(paymentMethod, finalTotal, orderId);
            runOnUiThread(() -> {
                if (loadingDialog != null && loadingDialog.isShowing()) loadingDialog.dismiss();
                cartManager.clear();
                Toast.makeText(sellProduct.this, "Sale Recorded Successfully!", Toast.LENGTH_LONG).show();
                finish();
            });
            return;
        }

        CartManager.CartItem item = items.get(index);
        double lineTotal = item.getLineTotal();
        double ratio     = subtotal == 0 ? 0 : lineTotal / subtotal;
        double lineFinal = finalTotal * ratio;
        double lineDiscount = lineTotal - lineFinal;

        StringBuilder nameBuilder = new StringBuilder(item.productName);
        if (item.size != null && !item.size.isEmpty()) nameBuilder.append(" (").append(item.size).append(")");
        if (item.addon != null && !item.addon.isEmpty()) nameBuilder.append(" [").append(item.addon).append("]");
        final String finalDbProductName = nameBuilder.toString();

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
                sale.setTotalCost(p != null ? (p.getCostPrice() * item.quantity) : 0.0);
                sale.setDiscountAmount(lineDiscount);
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
                        if (p != null) {
                            try {
                                double ppu = p.getPiecesPerUnit() > 0 ? p.getPiecesPerUnit() : 1.0;
                                double amountUsed = p.getDeductionAmount() > 0 ? p.getDeductionAmount() : 1.0;
                                double baseDeduct = (amountUsed * item.quantity) / ppu;

                                String pUnit = p.getUnit() != null ? p.getUnit() : "pcs";
                                String sUnit = p.getSalesUnit() != null && !p.getSalesUnit().trim().isEmpty() ? p.getSalesUnit() : pUnit;

                                Object[] conversion = UnitConverterUtil.convertBaseInventoryUnit(
                                        p.getQuantity(), pUnit, sUnit, (int) ppu);

                                double convertedInvQty = (double) conversion[0];
                                String invUnit = (String) conversion[1];
                                boolean unitChanged = (boolean) conversion[2];

                                double finalDeductAmt = UnitConverterUtil.calculateDeductionAmount(
                                        baseDeduct, invUnit, sUnit, (int) ppu);

                                double newQty = UnitConverterUtil.calculateNewStock(convertedInvQty, finalDeductAmt);

                                if (unitChanged) {
                                    FirebaseFirestore.getInstance().collection(FirestoreManager.getInstance().getUserProductsPath())
                                            .document(p.getProductId()).update(
                                                    "unit", invUnit,
                                                    "reorderLevel", p.getReorderLevel() * ppu,
                                                    "criticalLevel", p.getCriticalLevel() * ppu
                                            );
                                }

                                productRepository.updateProductQuantity(item.productId, newQty, new ProductRepository.OnProductUpdatedListener() {
                                    @Override
                                    public void onProductUpdated() {
                                        executeSubDeductions(p, item, index, items, orderId, subtotal, now, paymentMethod, deliveryType, deliveryStatus, deliveryDate, dName, dPhone, dAddr, dPay, enrichedNames);
                                    }

                                    @Override
                                    public void onError(String error) { handleSaveError(error); }
                                });
                            } catch (Exception e) {
                                double newQty = p.getQuantity() - item.quantity;
                                productRepository.updateProductQuantity(item.productId, Math.max(0, newQty), new ProductRepository.OnProductUpdatedListener() {
                                    @Override
                                    public void onProductUpdated() {
                                        executeSubDeductions(p, item, index, items, orderId, subtotal, now, paymentMethod, deliveryType, deliveryStatus, deliveryDate, dName, dPhone, dAddr, dPay, enrichedNames);
                                    }

                                    @Override
                                    public void onError(String error) { handleSaveError(error); }
                                });
                            }
                        } else {
                            saveCartItemRecursively(index + 1, items, orderId, subtotal, now, paymentMethod,
                                    deliveryType, deliveryStatus, deliveryDate, dName, dPhone, dAddr, dPay, enrichedNames);
                        }
                    }

                    @Override
                    public void onError(String error) {
                        handleSaveError(error);
                    }
                });
            }

            @Override
            public void onError(String error) {
                handleSaveError(error);
            }
        });
    }

    // FIXED: Suppressed unchecked cast warning for reading the generic Map
    @SuppressWarnings("unchecked")
    private void executeSubDeductions(Product p, CartManager.CartItem item, int index, List<CartManager.CartItem> items,
                                      String orderId, double subtotal, long now, String paymentMethod, String deliveryType,
                                      String deliveryStatus, long deliveryDate, String dName, String dPhone, String dAddr,
                                      String dPay, Map<String, String> enrichedNames) {

        List<Map<String, Object>> targetRecipe = p.getBomList();

        if (p.getUnifiedVariations() != null && !p.getUnifiedVariations().isEmpty()) {
            for (Map<String, Object> var : p.getUnifiedVariations()) {
                String varName = (String) var.get("name");
                if (item.size != null && item.size.equals(varName)) {
                    Object recipeObj = var.get("recipe");
                    if (recipeObj instanceof List) {
                        targetRecipe = (List<Map<String, Object>>) recipeObj;
                    }
                    break;
                }
            }
        }

        if (targetRecipe != null && !targetRecipe.isEmpty()) {
            for (Map<String, Object> bomItem : targetRecipe) {
                String materialName = (String) bomItem.get("materialName");
                double bQty = bomItem.get("quantity") instanceof Number ? ((Number) bomItem.get("quantity")).doubleValue() : 0.0;
                String bUnit = (String) bomItem.get("unit");
                double finalQtyToDeduct = bQty * item.quantity;

                if (finalQtyToDeduct > 0) {
                    deductFromMaterial(materialName, finalQtyToDeduct, bUnit);
                }
            }
        }

        saveCartItemRecursively(index + 1, items, orderId, subtotal, now, paymentMethod,
                deliveryType, deliveryStatus, deliveryDate, dName, dPhone, dAddr, dPay, enrichedNames);
    }

    private void updateCashManagementWallet(String paymentMethod, double amount, String orderId) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) return;

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        String walletDocId = "CASH";
        String methodLower = paymentMethod.toLowerCase();

        if (methodLower.contains("gcash")) {
            walletDocId = "GCASH";
        }

        DocumentReference walletRef = db.collection("users")
                .document(ownerId)
                .collection("wallets")
                .document(walletDocId);

        String finalWalletDocId = walletDocId;

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(walletRef);
            double newBalance = amount;

            if (snapshot.exists() && snapshot.getDouble("balance") != null) {
                newBalance += snapshot.getDouble("balance");
                transaction.update(walletRef, "balance", newBalance);
            } else {
                Map<String, Object> newWallet = new HashMap<>();
                newWallet.put("name", finalWalletDocId.equals("CASH") ? "Cash on Hand" : "GCash");
                newWallet.put("type", finalWalletDocId.equals("CASH") ? "Physical Cash" : "E-Wallet");
                newWallet.put("balance", newBalance);
                transaction.set(walletRef, newWallet);
            }
            return null;
        }).addOnSuccessListener(aVoid -> {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy - hh:mm a", Locale.getDefault());
            Map<String, Object> transLog = new HashMap<>();
            transLog.put("title", "Sale: Order " + orderId.substring(0, 8).toUpperCase());
            transLog.put("date", sdf.format(new Date()));
            transLog.put("amount", amount);
            transLog.put("isIncome", true);
            transLog.put("timestamp", System.currentTimeMillis());

            db.collection("users").document(ownerId).collection("cash_transactions").add(transLog);

            String currentUserId = AuthManager.getInstance().getCurrentUserId();
            db.collection("users").document(ownerId).collection("shifts")
                    .whereEqualTo("status", "ACTIVE")
                    .whereEqualTo("cashierId", currentUserId)
                    .get()
                    .addOnSuccessListener(shiftSnapshot -> {
                        if (!shiftSnapshot.isEmpty()) {
                            DocumentSnapshot shiftDoc = shiftSnapshot.getDocuments().get(0);
                            double currentCashSales = shiftDoc.getDouble("cashSales") != null ? shiftDoc.getDouble("cashSales") : 0.0;
                            double currentEPaymentSales = shiftDoc.getDouble("ePaymentSales") != null ? shiftDoc.getDouble("ePaymentSales") : 0.0;

                            if (paymentMethod.equalsIgnoreCase("Cash")) {
                                shiftDoc.getReference().update("cashSales", currentCashSales + finalTotal);
                            } else {
                                shiftDoc.getReference().update("ePaymentSales", currentEPaymentSales + finalTotal);
                            }
                        }
                    });

        }).addOnFailureListener(e -> {
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