package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class sellProduct extends BaseActivity  {

    private static final int REQUEST_CAMERA_PERMISSION = 3001;

    private SalesRepository salesRepository;
    private ProductRepository productRepository;

    private double finalTotal;
    private double discountPercent;

    private TextInputEditText etDiscount;
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

    private ListView cartListView;
    private List<CartManager.CartItem> cartItems;
    private android.widget.BaseAdapter cartAdapter;

    private boolean receiptCaptured = false;

    private AlertDialog cameraDialog;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private Camera camera;
    private boolean flashOn = false;

    private CartManager cartManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sell_product);
        cartManager = CartManager.getInstance();
        initRepositories();
        initViews();
        setupListeners();
        initCart();
        calculateTotalFromCart();
    }

    private void initRepositories() {
        salesRepository = SalesRepository.getInstance();
        productRepository = ProductRepository.getInstance(getApplication());
    }

    private void initViews() {
        etDiscount = findViewById(R.id.DiscountInput);
        tvTotalPrice = findViewById(R.id.TotalPriceTV);
        btnConfirmSale = findViewById(R.id.BtnConfirmSale);
        btnEditCart = findViewById(R.id.BtnEditCart);

        spinnerPaymentMethod = findViewById(R.id.spinnerPaymentMethod);
        layoutCashSection = findViewById(R.id.layoutCashSection);
        etCashGiven = findViewById(R.id.etCashGiven);
        tvChange = findViewById(R.id.tvChange);
        layoutEPaymentSection = findViewById(R.id.layoutEPaymentSection);
        btnCaptureReceipt = findViewById(R.id.btnCaptureReceipt);
        tvReceiptStatus = findViewById(R.id.tvReceiptStatus);

        cartListView = findViewById(R.id.cartListView);

        String[] methods = new String[]{"Cash", "E-Payment"};
        android.widget.ArrayAdapter<String> pmAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, methods);
        pmAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerPaymentMethod.setAdapter(pmAdapter);

        tvTotalPrice.setText("₱0.00");
    }

    private void setupListeners() {
        etDiscount.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { calculateTotalFromCart(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        etCashGiven.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { calculateChange(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        spinnerPaymentMethod.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String method = (String) spinnerPaymentMethod.getSelectedItem();
                boolean isCash = "Cash".equals(method);
                setPaymentSections(isCash);
                calculateChange();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        btnCaptureReceipt.setOnClickListener(v -> checkCameraPermissionAndShowDialog());
        btnConfirmSale.setOnClickListener(v -> confirmSale());
        btnEditCart.setOnClickListener(v -> showEditCartDialog());
    }

    private void initCart() {
        cartItems = cartManager.getItems();
        cartAdapter = new android.widget.BaseAdapter() {
            @Override
            public int getCount() {
                return cartItems.size();
            }

            @Override
            public Object getItem(int position) {
                return cartItems.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.item_cart_product, parent, false);
                }
                CartManager.CartItem item = cartItems.get(position);
                TextView tvName = convertView.findViewById(R.id.tvCartName);
                TextView tvDetails = convertView.findViewById(R.id.tvCartDetails);
                TextView tvQty = convertView.findViewById(R.id.tvCartQty);
                TextView tvLineTotal = convertView.findViewById(R.id.tvCartLineTotal);

                tvName.setText(item.productName);
                String detailText = "";
                if (item.size != null && !item.size.isEmpty()) {
                    detailText += item.size;
                }
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
        cartListView.setAdapter(cartAdapter);
    }

    private void refreshCart() {
        cartItems = cartManager.getItems();
        cartAdapter.notifyDataSetChanged();
    }

    private void calculateTotalFromCart() {
        double subtotal = cartManager.getSubtotal();
        String discountStr = etDiscount.getText() != null ? etDiscount.getText().toString().trim() : "";
        try {
            discountPercent = discountStr.isEmpty() ? 0 : Double.parseDouble(discountStr);
            if (discountPercent < 0 || discountPercent > 100) {
                discountPercent = 0;
                etDiscount.setText("0");
            }
        } catch (NumberFormatException e) {
            discountPercent = 0;
            etDiscount.setText("0");
        }
        double discountAmount = subtotal * (discountPercent / 100.0);
        finalTotal = subtotal - discountAmount;
        if (finalTotal < 0) finalTotal = 0;
        tvTotalPrice.setText("₱" + String.format(Locale.US, "%.2f", finalTotal));
        calculateChange();
    }

    private void calculateChange() {
        String method = (String) spinnerPaymentMethod.getSelectedItem();
        if (!"Cash".equals(method)) {
            tvChange.setText("Change: ₱0.00");
            return;
        }
        if (finalTotal <= 0) {
            tvChange.setText("Change: ₱0.00");
            return;
        }
        String cashStr = etCashGiven.getText() != null ? etCashGiven.getText().toString().trim() : "";
        if (cashStr.isEmpty()) {
            tvChange.setText("Change: ₱0.00");
            return;
        }
        try {
            double cash = Double.parseDouble(cashStr);
            double change = cash - finalTotal;
            if (change < 0) {
                tvChange.setText("Change: ₱0.00");
            } else {
                tvChange.setText("Change: ₱" + String.format(Locale.US, "%.2f", change));
            }
        } catch (NumberFormatException e) {
            tvChange.setText("Change: ₱0.00");
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
        ListView editListView = dialogView.findViewById(R.id.editCartListView);
        Button btnChangeProduct = dialogView.findViewById(R.id.btnEditChangeProduct);
        Button btnCancel = dialogView.findViewById(R.id.btnEditCancel);
        Button btnSave = dialogView.findViewById(R.id.btnEditSave);

        List<CartManager.CartItem> editItems = new ArrayList<>();
        for (CartManager.CartItem it : items) {
            editItems.add(new CartManager.CartItem(
                    it.productId,
                    it.productName,
                    it.unitPrice,
                    it.quantity,
                    it.stock,
                    it.size,
                    it.addon
            ));
        }

        final android.widget.BaseAdapter[] editAdapter = new android.widget.BaseAdapter[1];

        editAdapter[0] = new android.widget.BaseAdapter() {
            @Override
            public int getCount() {
                return editItems.size();
            }

            @Override
            public Object getItem(int position) {
                return editItems.get(position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null) {
                    convertView = getLayoutInflater().inflate(R.layout.item_cart_product, parent, false);
                }
                CartManager.CartItem item = editItems.get(position);
                TextView tvName = convertView.findViewById(R.id.tvCartName);
                TextView tvDetails = convertView.findViewById(R.id.tvCartDetails);
                TextView tvQty = convertView.findViewById(R.id.tvCartQty);
                TextView tvLineTotal = convertView.findViewById(R.id.tvCartLineTotal);

                tvName.setText(item.productName);
                String detailText = "";
                if (item.size != null && !item.size.isEmpty()) {
                    detailText += item.size;
                }
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

        AlertDialog editDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

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
                            item.productId,
                            item.productName,
                            item.unitPrice,
                            item.quantity,
                            item.stock,
                            item.size,
                            item.addon
                    );
                }
            }
            refreshCart();
            calculateTotalFromCart();
            editDialog.dismiss();
        });

        editDialog.show();
    }

    private void showEditSingleItemDialog(CartManager.CartItem item, android.widget.BaseAdapter parentAdapter) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
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
                if (q <= 0 || q > item.stock) {
                    Toast.makeText(this, "Quantity must be between 1 and " + item.stock, Toast.LENGTH_SHORT).show();
                } else {
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            showReceiptDialog();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
        }
    }

    private void showReceiptDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_capture_receipt, null, false);
        previewView = dialogView.findViewById(R.id.previewView);
        android.widget.ImageButton btnFlash = dialogView.findViewById(R.id.btnFlash);
        android.widget.ImageButton btnSwitchCamera = dialogView.findViewById(R.id.btnSwitchCamera);
        android.widget.ImageButton btnCapture = dialogView.findViewById(R.id.btnCapture);
        Button btnClose = dialogView.findViewById(R.id.btnClose);

        cameraDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        btnFlash.setOnClickListener(v -> toggleFlash());
        btnSwitchCamera.setOnClickListener(v -> switchCamera());
        btnCapture.setOnClickListener(v -> captureReceipt());
        btnClose.setOnClickListener(v -> cameraDialog.dismiss());

        cameraDialog.setOnShowListener(dialog -> startCamera());
        cameraDialog.show();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                Toast.makeText(this, "Error starting camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases(@NonNull ProcessCameraProvider cameraProvider) {
        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build();

        camera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageCapture
        );

        flashOn = false;
        updateFlashUi();
    }

    private void toggleFlash() {
        if (camera == null || imageCapture == null) return;
        if (!camera.getCameraInfo().hasFlashUnit()) {
            Toast.makeText(this, "No flash available", Toast.LENGTH_SHORT).show();
            return;
        }
        flashOn = !flashOn;
        imageCapture.setFlashMode(flashOn
                ? ImageCapture.FLASH_MODE_ON
                : ImageCapture.FLASH_MODE_OFF);
        updateFlashUi();
    }

    private void updateFlashUi() {
        if (cameraDialog == null) return;
        android.widget.ImageButton btnFlash = cameraDialog.findViewById(R.id.btnFlash);
        if (btnFlash == null) return;
        int color = flashOn
                ? android.R.color.holo_orange_light
                : android.R.color.white;
        btnFlash.setColorFilter(ContextCompat.getColor(this, color));
    }

    private void switchCamera() {
        cameraSelector = cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA
                ? CameraSelector.DEFAULT_FRONT_CAMERA
                : CameraSelector.DEFAULT_BACK_CAMERA;
        startCamera();
    }

    private void captureReceipt() {
        if (imageCapture == null) {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show();
            return;
        }
        receiptCaptured = true;
        tvReceiptStatus.setText("Receipt captured");
        Toast.makeText(this, "Receipt captured", Toast.LENGTH_SHORT).show();
        if (cameraDialog != null) cameraDialog.dismiss();
    }

    private void confirmSale() {
        if (cartManager.getItems().isEmpty() || finalTotal <= 0) {
            Toast.makeText(this, "Cart is empty or total is zero", Toast.LENGTH_SHORT).show();
            return;
        }
        String method = (String) spinnerPaymentMethod.getSelectedItem();
        if ("Cash".equals(method)) {
            String cashStr = etCashGiven.getText() != null ? etCashGiven.getText().toString().trim() : "";
            if (cashStr.isEmpty()) {
                etCashGiven.setError("Enter cash amount");
                return;
            }
            double cash;
            try {
                cash = Double.parseDouble(cashStr);
            } catch (NumberFormatException e) {
                etCashGiven.setError("Invalid amount");
                return;
            }
            if (cash < finalTotal) {
                etCashGiven.setError("Cash is less than total");
                return;
            }
            saveSale("Cash");
        } else {
            if (!receiptCaptured) {
                Toast.makeText(this, "Please capture payment receipt first", Toast.LENGTH_SHORT).show();
                return;
            }
            saveSale("E-Payment");
        }
    }

    private void saveSale(String paymentMethod) {
        List<CartManager.CartItem> items = cartManager.getItems();
        if (items.isEmpty()) return;

        double subtotal = cartManager.getSubtotal();
        for (CartManager.CartItem item : items) {
            double lineTotal = item.getLineTotal();
            double ratio = subtotal == 0 ? 0 : lineTotal / subtotal;
            double lineFinal = finalTotal * ratio;

            Sales sale = new Sales();
            sale.setProductId(item.productId);
            sale.setProductName(item.productName);
            sale.setQuantity(item.quantity);
            sale.setPrice(item.unitPrice);
            sale.setTotalPrice(lineFinal);
            sale.setPaymentMethod(paymentMethod);
            sale.setDate(System.currentTimeMillis());
            sale.setTimestamp(System.currentTimeMillis());

            salesRepository.addSale(sale, new SalesRepository.OnSaleAddedListener() {
                @Override
                public void onSaleAdded(String saleId) {
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() ->
                            Toast.makeText(sellProduct.this, "Failed: " + error, Toast.LENGTH_SHORT).show());
                }
            });

            productRepository.updateProductQuantity(item.productId, Math.max(0, item.stock - item.quantity), new ProductRepository.OnProductUpdatedListener() {
                @Override
                public void onProductUpdated() {
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() ->
                            Toast.makeText(sellProduct.this, "Error updating stock: " + error, Toast.LENGTH_SHORT).show());
                }
            });
        }

        Toast.makeText(this, "Sale recorded successfully", Toast.LENGTH_SHORT).show();
        cartManager.clear();
        clearInputs();
        refreshCart();
        calculateTotalFromCart();
    }

    private void clearInputs() {
        etDiscount.setText("0");
        tvTotalPrice.setText("₱0.00");
        etCashGiven.setText("");
        tvChange.setText("Change: ₱0.00");
        discountPercent = 0;
        receiptCaptured = false;
        tvReceiptStatus.setText("No receipt captured");
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showReceiptDialog();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}