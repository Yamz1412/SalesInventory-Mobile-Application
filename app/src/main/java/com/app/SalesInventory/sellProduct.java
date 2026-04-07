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
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
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
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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

    private View layoutPaymentNotRegistered, layoutPaymentRegistered;
    private MaterialButton btnRegisterPaymentMethod;
    private TextView tvStoreAccountName, tvStoreAccountNumber, btnEditPaymentMethod;
    private ImageView imgStoreQrCode;
    private ActivityResultLauncher<Intent> qrCodeLauncher;
    private Uri newQrImageUri = null;
    private ImageView imgRegisterQrPreview;
    private String selectedPaymentMethod = "Cash";

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

    private Map<String, String> cartNotes = new HashMap<>();

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

        layoutPaymentNotRegistered = findViewById(R.id.layoutPaymentNotRegistered);
        layoutPaymentRegistered = findViewById(R.id.layoutPaymentRegistered);
        btnRegisterPaymentMethod = findViewById(R.id.btnRegisterPaymentMethod);
        tvStoreAccountName = findViewById(R.id.tvStoreAccountName);
        tvStoreAccountNumber = findViewById(R.id.tvStoreAccountNumber);
        imgStoreQrCode = findViewById(R.id.imgStoreQrCode);
        btnEditPaymentMethod = findViewById(R.id.btnEditPaymentMethod);

        if (btnRegisterPaymentMethod != null) btnRegisterPaymentMethod.setOnClickListener(v -> showRegisterPaymentMethodDialog(selectedPaymentMethod));
        if (btnEditPaymentMethod != null) btnEditPaymentMethod.setOnClickListener(v -> showRegisterPaymentMethodDialog(selectedPaymentMethod));

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

        String[] methods = new String[]{"Cash", "GCash", "Maya"};

        int currentNightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDark = false;
        try {
            isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        } catch (Exception e) {
            isDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES;
        }
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        ArrayAdapter<String> pmAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, methods) {
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

        qrCodeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        newQrImageUri = result.getData().getData();
                        if (imgRegisterQrPreview != null) {
                            imgRegisterQrPreview.setImageURI(newQrImageUri);
                        }
                    }
                }
        );
    }

    private void loadPaymentMethodDetails(String method) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        FirebaseFirestore.getInstance()
                .collection("users").document(ownerId)
                .collection("settings").document("payment_methods")
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc.exists() && doc.contains(method)) {
                        Map<String, String> data = (Map<String, String>) doc.get(method);
                        if (data != null) {
                            layoutPaymentNotRegistered.setVisibility(View.GONE);
                            layoutPaymentRegistered.setVisibility(View.VISIBLE);
                            tvStoreAccountName.setText("Name: " + data.get("accountName"));
                            tvStoreAccountNumber.setText("No.: " + data.get("accountNumber"));

                            String qrUrl = data.get("qrUrl");
                            if (qrUrl != null && !qrUrl.isEmpty()) {
                                Glide.with(this).load(qrUrl).into(imgStoreQrCode);
                            } else {
                                imgStoreQrCode.setImageResource(R.drawable.ic_image_placeholder);
                            }
                        }
                    } else {
                        layoutPaymentRegistered.setVisibility(View.GONE);
                        layoutPaymentNotRegistered.setVisibility(View.VISIBLE);
                    }
                });
    }

    private void showRegisterPaymentMethodDialog(String method) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_register_payment, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView tvTitle = view.findViewById(R.id.tvRegisterPaymentTitle);
        EditText etName = view.findViewById(R.id.etRegisterAccountName);
        EditText etNumber = view.findViewById(R.id.etRegisterAccountNumber);
        Button btnUploadQr = view.findViewById(R.id.btnUploadQr);
        Button btnCancel = view.findViewById(R.id.btnCancelRegister);
        Button btnSave = view.findViewById(R.id.btnSavePaymentMethod);
        imgRegisterQrPreview = view.findViewById(R.id.imgRegisterQrPreview);
        newQrImageUri = null;

        tvTitle.setText("Set Up " + method);

        btnUploadQr.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_PICK);
            intent.setType("image/*");
            qrCodeLauncher.launch(intent);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String number = etNumber.getText().toString().trim();

            if (name.isEmpty() || number.isEmpty()) {
                Toast.makeText(this, "Name and Number are required", Toast.LENGTH_SHORT).show();
                return;
            }

            loadingDialog.setMessage("Saving Details...");
            if (!loadingDialog.isShowing()) loadingDialog.show();

            if (newQrImageUri != null) {
                String path = "payment_qrs/" + UUID.randomUUID().toString() + ".jpg";
                StorageReference ref = FirebaseStorage.getInstance().getReference(path);
                ref.putFile(newQrImageUri).addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl().addOnSuccessListener(uri -> {
                    savePaymentDetailsToFirestore(method, name, number, uri.toString(), dialog);
                })).addOnFailureListener(e -> {
                    if (loadingDialog.isShowing()) loadingDialog.dismiss();
                    Toast.makeText(this, "Upload failed", Toast.LENGTH_SHORT).show();
                });
            } else {
                savePaymentDetailsToFirestore(method, name, number, "", dialog);
            }
        });

        dialog.show();
    }

    private void savePaymentDetailsToFirestore(String method, String name, String number, String qrUrl, AlertDialog dialog) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        Map<String, String> details = new HashMap<>();
        details.put("accountName", name);
        details.put("accountNumber", number);
        details.put("qrUrl", qrUrl);

        Map<String, Object> update = new HashMap<>();
        update.put(method, details);

        FirebaseFirestore.getInstance()
                .collection("users").document(ownerId)
                .collection("settings").document("payment_methods")
                .set(update, com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    if (loadingDialog.isShowing()) loadingDialog.dismiss();
                    dialog.dismiss();
                    loadPaymentMethodDetails(method);
                    Toast.makeText(this, "Details Saved", Toast.LENGTH_SHORT).show();
                });
    }

    @android.annotation.SuppressLint("MissingPermission")
    private boolean isBluetoothPrinterConnected() {
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
            BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
            if (adapter == null || !adapter.isEnabled()) return false;

            Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();
            if (pairedDevices != null) {
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getBluetoothClass() != null) {
                        int majorClass = device.getBluetoothClass().getMajorDeviceClass();
                        if (majorClass == BluetoothClass.Device.Major.IMAGING) {
                            return true;
                        }
                    }
                    if (device.getName() != null && (device.getName().toLowerCase().contains("printer") || device.getName().toLowerCase().contains("pos"))) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private void saveReceiptAsPdf(View receiptView, String orderId) {
        receiptView.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        receiptView.layout(0, 0, receiptView.getMeasuredWidth(), receiptView.getMeasuredHeight());

        android.graphics.pdf.PdfDocument document = new android.graphics.pdf.PdfDocument();
        android.graphics.pdf.PdfDocument.PageInfo pageInfo = new android.graphics.pdf.PdfDocument.PageInfo.Builder(receiptView.getMeasuredWidth(), receiptView.getMeasuredHeight(), 1).create();
        android.graphics.pdf.PdfDocument.Page page = document.startPage(pageInfo);

        android.graphics.Canvas canvas = page.getCanvas();
        receiptView.draw(canvas);
        document.finishPage(page);

        // CRITICAL FIX: Android 11+ Scoped Storage Compliance
        java.io.File directory;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Safe app-specific directory that requires NO permissions
            directory = getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
        } else {
            // Fallback for older Android phones
            directory = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
        }

        if (directory != null && !directory.exists()) {
            directory.mkdirs();
        }

        java.io.File file = new java.io.File(directory, "Receipt_" + orderId + ".pdf");

        try {
            document.writeTo(new java.io.FileOutputStream(file));
            android.widget.Toast.makeText(this, "Receipt Auto-Saved to Downloads", android.widget.Toast.LENGTH_SHORT).show();
        } catch (java.io.IOException e) {
            e.printStackTrace();
            android.widget.Toast.makeText(this, "Failed to save receipt copy", android.widget.Toast.LENGTH_SHORT).show();
        }
        document.close();
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
                selectedPaymentMethod = (String) spinnerPaymentMethod.getSelectedItem();
                boolean isCash = "Cash".equals(selectedPaymentMethod);
                setPaymentSections(isCash);
                if (!isCash) {
                    loadPaymentMethodDetails(selectedPaymentMethod);
                }
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
                EditText etNote = convertView.findViewById(R.id.etCartNote);

                btnRemove.setOnClickListener(v -> {
                    cartManager.removeItemById(item.productId);
                    cartNotes.remove(item.productId);
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
                tvLineTotal.setText("₱" + String.format(Locale.US, "%,.2f", item.getLineTotal()));

                if (etNote != null) {
                    if (etNote.getTag() instanceof TextWatcher) {
                        etNote.removeTextChangedListener((TextWatcher) etNote.getTag());
                    }
                    String currentNote = cartNotes.get(item.productId);
                    if (currentNote == null || currentNote.trim().isEmpty()) {
                        currentNote = "0% Sugar";
                        cartNotes.put(item.productId, currentNote);
                    }
                    etNote.setText(currentNote);
                    TextWatcher watcher = new TextWatcher() {
                        @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                        @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                        @Override public void afterTextChanged(Editable s) {
                            cartNotes.put(item.productId, s.toString());
                        }
                    };
                    etNote.addTextChangedListener(watcher);
                    etNote.setTag(watcher);
                }
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
        tvTotalPrice.setText(String.format(Locale.US, "₱%,.2f", finalTotal));

        if (finalTotal > 1000000.0) {
            btnConfirmSale.setEnabled(false);
            Toast.makeText(this, "Total exceeds maximum allowed payment of ₱1,000,000.00", Toast.LENGTH_LONG).show();
        } else {
            calculateChange();
        }
    }

    private void calculateChange() {
        String method = (String) spinnerPaymentMethod.getSelectedItem();

        if (!"Cash".equals(method)) {
            tvChange.setText("Change: ₱0.00");
            btnConfirmSale.setEnabled(finalTotal > 0);
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
                tvChange.setText(String.format(Locale.US, "Change: ₱%,.2f", changeBD.doubleValue()));
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
                    it.quantity, it.stock, it.size, it.addon, it.excludedIngredients
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

                View layoutNote = convertView.findViewById(R.id.etCartNote);
                if(layoutNote != null) layoutNote.setVisibility(View.GONE);

                tvName.setText(item.productName);
                String detailText = "";
                if (item.size != null && !item.size.isEmpty()) detailText += item.size;
                if (item.addon != null && !item.addon.isEmpty()) {
                    if (!detailText.isEmpty()) detailText += " / ";
                    detailText += item.addon;
                }
                tvDetails.setText(detailText);
                tvQty.setText("x" + item.quantity);
                tvLineTotal.setText("₱" + String.format(Locale.US, "%,.2f", item.getLineTotal()));

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
                            item.quantity, item.stock, item.size, item.addon, item.excludedIngredients
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
            if (layoutPaymentNotRegistered.getVisibility() == View.VISIBLE) {
                Toast.makeText(this, "Please set up the receiving account first", Toast.LENGTH_SHORT).show();
                return;
            }
            String refNum = etReferenceNumber.getText().toString().trim();
            if (refNum.isEmpty() && !receiptCaptured) {
                Toast.makeText(this, "Please enter Reference Number or Upload Receipt", Toast.LENGTH_SHORT).show();
                return;
            }
            paymentDetails = method + (refNum.isEmpty() ? "" : " (Ref: " + refNum + ")");
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
                saveSale(paymentMethod, isDelivery, dName, dPhone, dAddr, dPay, enrichedNames, orderId);
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

    @SuppressWarnings("unchecked")
    private void saveSale(String paymentMethod, boolean isDelivery, String dName, String dPhone,
                          String dAddr, String dPay, Map<String, String> enrichedNames, String orderId) {

        runOnUiThread(() -> { if (loadingDialog != null && !loadingDialog.isShowing()) loadingDialog.show(); });

        List<CartManager.CartItem> items = new ArrayList<>(cartManager.getItems());
        if (items.isEmpty()) {
            if (loadingDialog != null) loadingDialog.dismiss();
            return;
        }

        double subtotal = cartManager.getSubtotal();
        long now = System.currentTimeMillis();

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
                // =======================================================================================
                // CRITICAL FIX: Safe Inventory Deduction
                // Prevents deducting the phantom base quantity if the item strictly uses Recipes/Variations!
                // =======================================================================================
                boolean hasBaseRecipe = p.getBomList() != null && !p.getBomList().isEmpty();
                boolean hasVariationRecipe = p.getUnifiedVariations() != null && !p.getUnifiedVariations().isEmpty();

                if (!hasBaseRecipe && !hasVariationRecipe) {
                    p.setQuantity(Math.max(0, p.getQuantity() - item.quantity));
                    productRepository.updateProductQuantity(p.getProductId(), p.getQuantity(), null);
                }

                // =======================================================================================
                // COMBINED BOM LOGIC TO PREVENT DOUBLE-DEDUCTIONS AND NULL-POINTER CRASHES
                // =======================================================================================
                List<Map<String, Object>> activeRecipe = p.getBomList();
                if (hasVariationRecipe) {
                    for (Map<String, Object> var : p.getUnifiedVariations()) {
                        String varName = (String) var.get("name");
                        if (item.size != null && item.size.equals(varName)) {
                            Object recipeObj = var.get("recipe");
                            if (recipeObj instanceof List) {
                                activeRecipe = (List<Map<String, Object>>) recipeObj;
                            }
                            break;
                        }
                    }
                }

                if (activeRecipe != null && !activeRecipe.isEmpty()) {
                    String excluded = item.excludedIngredients != null ? item.excludedIngredients : "";

                    for (Map<String, Object> bomItem : activeRecipe) {
                        String rawId = (String) bomItem.get("rawMaterialId");
                        String matName = (String) bomItem.get("rawMaterialName");
                        if (matName == null) matName = (String) bomItem.get("materialName");

                        // SAFE EXCLUSION CHECK (Prevents deducting unchecked items)
                        String uniqueKey = rawId != null ? rawId : matName;
                        if (uniqueKey != null && excluded.contains(uniqueKey)) {
                            continue;
                        }

                        double bQty = 0;
                        try { bQty = Double.parseDouble(String.valueOf(bomItem.get("quantityRequired"))); } catch (Exception ignored) {}
                        if (bQty == 0) {
                            try { bQty = Double.parseDouble(String.valueOf(bomItem.get("quantity"))); } catch (Exception ignored) {}
                        }

                        // SAFE DYNAMIC SUGAR LOGIC (Prevents NullPointerException if matName is null)
                        String note = cartNotes.get(item.productId);
                        if (note != null && matName != null) {
                            String noteLower = note.toLowerCase();
                            String materialLower = matName.toLowerCase();

                            if (noteLower.contains("0%") || noteLower.contains("no sugar") || noteLower.equals("0")) {
                                if (materialLower.contains("sugar") || materialLower.contains("syrup") || materialLower.contains("sweetener")) {
                                    bQty = 0;
                                }
                            }
                        }

                        String bUnit = (String) bomItem.get("unit");
                        if (bQty > 0 && matName != null) {
                            deductFromMaterial(matName, bQty * item.quantity, bUnit);
                        }
                    }
                }
                // =======================================================================================

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
                        finalizeSaleCompletion(paymentMethod, orderId, enrichedNames);
                    }
                }
                @Override
                public void onError(String error) {
                    savedCount[0]++;
                    if (savedCount[0] == items.size()) {
                        finalizeSaleCompletion(paymentMethod, orderId, enrichedNames);
                    }
                }
            });
        }
    }

    private void finalizeSaleCompletion(String paymentMethod, String orderId, Map<String, String> enrichedNames) {
        updateCashManagementWallet(paymentMethod, finalTotal, orderId);

        for (CartManager.CartItem item : cartManager.getItems()) {
            SalesInventoryApplication.getProductRepository().getProductById(item.productId, new ProductRepository.OnProductFetchedListener() {
                @Override
                public void onProductFetched(Product p) {
                    if (p != null) {
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
        receiptText.append("Order ID: ").append(orderId.substring(0, 8).toUpperCase()).append("\n");
        receiptText.append("Date: ").append(new java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault()).format(new java.util.Date())).append("\n\n");
        for (CartManager.CartItem item : cartManager.getItems()) {
            String note = cartNotes.get(item.productId);
            String notePrint = (note != null && !note.trim().isEmpty()) ? "\n    Note: " + note : "";
            receiptText.append(item.quantity).append("x ").append(item.productName).append(notePrint).append("   ₱").append(String.format(Locale.US, "%,.2f", item.getLineTotal())).append("\n");
        }
        receiptText.append("\n-------------------\n");
        receiptText.append("TOTAL: ₱").append(String.format(Locale.US, "%,.2f", finalTotal)).append("\n");
        receiptText.append("PAYMENT: ").append(paymentMethod).append("\n");

        ReceiptStorageManager.generateAndSaveReceipt(this, orderId, receiptText.toString());

        runOnUiThread(() -> {
            if (loadingDialog != null) loadingDialog.dismiss();

            View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_receipt, null);
            AlertDialog receiptDialog = new AlertDialog.Builder(this)
                    .setView(dialogView)
                    .setCancelable(false)
                    .create();

            LinearLayout rootLayout = dialogView.findViewById(R.id.layoutReceiptRoot);
            TextView tvBusinessName = dialogView.findViewById(R.id.tvReceiptBusinessName);
            ImageView ivBusinessLogo = dialogView.findViewById(R.id.ivReceiptBusinessLogo);
            TextView tvOrderId = dialogView.findViewById(R.id.tvReceiptOrderId);
            TextView tvDate = dialogView.findViewById(R.id.tvReceiptDateTime);
            ListView lvReceiptItems = dialogView.findViewById(R.id.lvReceiptItems);
            TextView tvTotal = dialogView.findViewById(R.id.tvReceiptTotal);
            TextView tvPaymentMethodStr = dialogView.findViewById(R.id.tvReceiptPaymentMethod);
            View layoutChange = dialogView.findViewById(R.id.layoutReceiptChange);
            TextView tvReceiptChange = dialogView.findViewById(R.id.tvReceiptChange);
            Button btnPrint = dialogView.findViewById(R.id.btnPrintReceipt);
            Button btnFinalizeSale = dialogView.findViewById(R.id.btnFinalizeSale);

            if (tvBusinessName != null) tvBusinessName.setText(cachedBusinessName);
            if (ivBusinessLogo != null && cachedBusinessLogoUrl != null && !cachedBusinessLogoUrl.isEmpty()) {
                ivBusinessLogo.setVisibility(View.VISIBLE);
                Glide.with(this).load(cachedBusinessLogoUrl).into(ivBusinessLogo);
            }

            if (tvOrderId != null) tvOrderId.setText("Order ID: #" + orderId.substring(0, 8).toUpperCase());
            if (tvDate != null) tvDate.setText(new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(new Date()));

            if (tvTotal != null) tvTotal.setText(String.format(Locale.US, "₱%,.2f", finalTotal));
            if (tvPaymentMethodStr != null) tvPaymentMethodStr.setText(paymentMethod);

            if (lvReceiptItems != null) {
                lvReceiptItems.removeAllViews();
                for (CartManager.CartItem item : cartManager.getItems()) {
                    TextView tv = new TextView(this);
                    String extraDetails = enrichedNames != null ? enrichedNames.get(item.productId) : "";
                    if (extraDetails == null) extraDetails = "";
                    String nameWithDetails = item.productName + (extraDetails.isEmpty() ? "" : "\n" + extraDetails);

                    String cNote = cartNotes.get(item.productId);
                    if (cNote != null && !cNote.trim().isEmpty()) {
                        nameWithDetails += "\nNote: " + cNote;
                    }

                    tv.setText(item.quantity + "x " + nameWithDetails + " - " + String.format(Locale.US, "₱%,.2f", item.getLineTotal()));
                    tv.setPadding(0, 0, 0, 8);
                    lvReceiptItems.addView(tv);
                }
            }

            if ("Cash".equals(selectedPaymentMethod) || paymentMethod.startsWith("Cash")) {
                if (layoutChange != null) layoutChange.setVisibility(View.VISIBLE);
                try {
                    String cashStr = etCashGiven.getText().toString().trim().replace(",", "");
                    double cashGiven = cashStr.isEmpty() ? 0 : Double.parseDouble(cashStr);
                    if (tvReceiptChange != null) tvReceiptChange.setText(String.format(Locale.US, "₱%,.2f", (cashGiven - finalTotal)));
                } catch (Exception e) {}
            } else {
                if (layoutChange != null) layoutChange.setVisibility(View.GONE);
            }

            if (btnPrint != null) {
                if (isBluetoothPrinterConnected()) {
                    btnPrint.setVisibility(View.VISIBLE);
                    btnPrint.setOnClickListener(v -> {
                        ReceiptPrinterManager.printReceipt(sellProduct.this, orderId, receiptText.toString());
                    });
                } else {
                    btnPrint.setVisibility(View.GONE);
                }
            }

            if (btnFinalizeSale != null) {
                btnFinalizeSale.setOnClickListener(v -> {
                    if (rootLayout != null) saveReceiptAsPdf(rootLayout, orderId);
                    cartManager.clear();
                    cartNotes.clear();
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
        String matUnit = material.getUnit() != null ? material.getUnit().toLowerCase().trim() : "pcs";
        String targetUnit = bUnit != null ? bUnit.toLowerCase().trim() : "pcs";

        try {
            // 1. Calculate the exact fraction to deduct in terms of the Main Inventory Unit
            // Example: Sold 500ml, Inventory is in L. utilDeductAmt = 0.5 L.
            double utilDeductAmt = UnitConverterUtil.calculateDeductionAmount(deductAmt, matUnit, targetUnit, ppu);

            // 2. Subtract the fraction from the current stock (e.g., 5.0 L - 0.5 L = 4.5 L)
            double finalMQty = material.getQuantity() - utilDeductAmt;
            if (finalMQty < 0) finalMQty = 0;

            // 3. Pro-rate the cost value of the inventory
            double unitCost = material.getCostPrice() / material.getQuantity();
            double deductedCost = utilDeductAmt * unitCost;
            double newTotalCost = Math.max(0.0, material.getCostPrice() - deductedCost);

            // 4. Update Database (Unit is NEVER permanently overwritten to ml/pcs anymore!)
            productRepository.updateProductQuantityAndCost(material.getProductId(), finalMQty, newTotalCost, null);

            // Update local memory
            material.setQuantity(finalMQty);
            material.setCostPrice(newTotalCost);

        } catch (Exception e) {
            double finalMQty = material.getQuantity() - deductAmt;
            double unitCost = material.getCostPrice() / material.getQuantity();
            double newTotalCost = Math.max(0.0, material.getCostPrice() - (deductAmt * unitCost));

            productRepository.updateProductQuantityAndCost(material.getProductId(), Math.max(0, finalMQty), newTotalCost, null);

            material.setQuantity(Math.max(0, finalMQty));
            material.setCostPrice(newTotalCost);
        }
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