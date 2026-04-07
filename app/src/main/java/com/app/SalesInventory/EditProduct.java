package com.app.SalesInventory;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditProduct extends BaseActivity {

    private ImageButton btnEditPhoto;
    private TextInputEditText productNameET, costPriceET, lowStockET, expiryDateET, etPiecesPerUnit;
    private MaterialAutoCompleteTextView quantityET;
    private AutoCompleteTextView productLineET, productTypeET;
    private Button updateBtn, cancelBtn;
    private Spinner unitSpinner, subUnitSpinner;
    private LinearLayout layoutPiecesPerUnit;

    private ProductRepository productRepository;
    private AuthManager authManager;
    private SimpleDateFormat expiryFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private String selectedImagePath;
    private Calendar expiryCalendar = Calendar.getInstance();
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    private List<Product> inventoryProducts = new ArrayList<>();
    private String currentUserId;
    private String editProductId;
    private Product existingProductToEdit;
    private String lastSelectedUnit = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_product);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Edit Product");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Handle ID retrieval
        editProductId = getIntent().getStringExtra("PRODUCT_ID");
        if (editProductId == null) editProductId = getIntent().getStringExtra("productId");
        if (editProductId == null) editProductId = getIntent().getStringExtra("product_id");

        if (editProductId == null) {
            Toast.makeText(this, "Product ID missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        productRepository = SalesInventoryApplication.getProductRepository();
        authManager = AuthManager.getInstance();
        currentUserId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (currentUserId == null) currentUserId = authManager.getCurrentUserId();

        // Initialize Views
        btnEditPhoto = findViewById(R.id.btnEditPhoto);
        productNameET = findViewById(R.id.productNameET);
        productLineET = findViewById(R.id.productLineET);
        productTypeET = findViewById(R.id.productTypeET);
        costPriceET = findViewById(R.id.costPriceET);
        lowStockET = findViewById(R.id.lowStockET);
        quantityET = findViewById(R.id.quantityET);
        unitSpinner = findViewById(R.id.unitSpinner);
        subUnitSpinner = findViewById(R.id.subUnitSpinner);
        layoutPiecesPerUnit = findViewById(R.id.layoutPiecesPerUnit);
        etPiecesPerUnit = findViewById(R.id.etPiecesPerUnit);
        expiryDateET = findViewById(R.id.expiryDateET);
        updateBtn = findViewById(R.id.updateBtn);
        cancelBtn = findViewById(R.id.cancelBtn);

        setupImagePickers();
        loadInventoryForCalculations();

        // Setup Spinners
        String[] units = {"pcs", "ml", "L", "oz", "g", "kg", "box", "pack"};
        unitSpinner.setAdapter(getAdaptiveAdapter(units));
        subUnitSpinner.setAdapter(getAdaptiveAdapter(units));

        unitSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selected = unitSpinner.getSelectedItem().toString();
                boolean isBulk = selected.equalsIgnoreCase("box") || selected.equalsIgnoreCase("pack") ||
                        selected.equalsIgnoreCase("kg") || selected.equalsIgnoreCase("L");

                if (layoutPiecesPerUnit != null) {
                    layoutPiecesPerUnit.setVisibility(isBulk ? View.VISIBLE : View.GONE);
                }

                if (!selected.equals(lastSelectedUnit)) {
                    lastSelectedUnit = selected;
                    if (selected.equalsIgnoreCase("kg") || selected.equalsIgnoreCase("L")) {
                        etPiecesPerUnit.setText("1000");
                    } else if (!isBulk) {
                        etPiecesPerUnit.setText("1");
                    }
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        // Listeners
        updateBtn.setOnClickListener(v -> attemptEdit());
        cancelBtn.setOnClickListener(v -> finish());
        btnEditPhoto.setOnClickListener(v -> tryPickImage());
        expiryDateET.setOnClickListener(v -> showExpiryDatePicker());

        loadProductData();
    }

    private void loadProductData() {
        productRepository.getProductById(editProductId, new ProductRepository.OnProductFetchedListener() {
            @Override
            public void onProductFetched(Product p) {
                runOnUiThread(() -> {
                    if (p != null) {
                        existingProductToEdit = p;
                        productNameET.setText(p.getProductName());
                        productTypeET.setText(p.getCategoryName());
                        productLineET.setText(p.getProductLine());
                        costPriceET.setText(String.valueOf(p.getCostPrice()));
                        quantityET.setText(String.valueOf(p.getQuantity()));
                        lowStockET.setText(String.valueOf(p.getReorderLevel()));

                        if (p.getExpiryDate() > 0) {
                            expiryCalendar.setTimeInMillis(p.getExpiryDate());
                            expiryDateET.setText(expiryFormat.format(expiryCalendar.getTime()));
                        }

                        etPiecesPerUnit.setText(String.valueOf(p.getPiecesPerUnit()));

                        // Set Spinner selection
                        ArrayAdapter adapter = (ArrayAdapter) unitSpinner.getAdapter();
                        int pos = adapter.getPosition(p.getUnit());
                        if (pos >= 0) unitSpinner.setSelection(pos);

                        // Load Image
                        selectedImagePath = (p.getImagePath() != null && !p.getImagePath().isEmpty()) ? p.getImagePath() : p.getImageUrl();
                        if (selectedImagePath != null && !isFinishing()) {
                            Glide.with(EditProduct.this).load(selectedImagePath).diskCacheStrategy(DiskCacheStrategy.ALL).into(btnEditPhoto);
                        }
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(EditProduct.this, "Error: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void attemptEdit() {
        String name = productNameET.getText().toString().trim();
        String type = productTypeET.getText().toString().trim();
        String line = productLineET.getText().toString().trim();

        if (name.isEmpty() || type.isEmpty()) {
            Toast.makeText(this, "Name and Type are required", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double cost = Double.parseDouble(costPriceET.getText().toString().isEmpty() ? "0" : costPriceET.getText().toString());
            double qty = Double.parseDouble(quantityET.getText().toString().isEmpty() ? "0" : quantityET.getText().toString());
            int reorder = Integer.parseInt(lowStockET.getText().toString().isEmpty() ? "0" : lowStockET.getText().toString());
            int ppu = Integer.parseInt(etPiecesPerUnit.getText().toString().isEmpty() ? "1" : etPiecesPerUnit.getText().toString());

            if (existingProductToEdit == null) existingProductToEdit = new Product();

            existingProductToEdit.setProductName(name);
            existingProductToEdit.setCategoryName(type);
            existingProductToEdit.setProductLine(line);
            existingProductToEdit.setCostPrice(cost);
            existingProductToEdit.setQuantity(qty);
            existingProductToEdit.setReorderLevel(reorder);
            existingProductToEdit.setPiecesPerUnit(ppu > 0 ? ppu : 1);

            String mainUnit = unitSpinner.getSelectedItem().toString();
            existingProductToEdit.setUnit(mainUnit);

            String expiryStr = expiryDateET.getText().toString().trim();
            if (!expiryStr.isEmpty()) {
                Date d = expiryFormat.parse(expiryStr);
                if (d != null) existingProductToEdit.setExpiryDate(d.getTime());
            } else {
                existingProductToEdit.setExpiryDate(0L);
            }

            productRepository.updateProduct(existingProductToEdit, selectedImagePath, new ProductRepository.OnProductUpdatedListener() {
                @Override
                public void onProductUpdated() {
                    runOnUiThread(() -> {
                        Toast.makeText(EditProduct.this, "Updated successfully", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(EditProduct.this, "Update Error: " + error, Toast.LENGTH_SHORT).show());
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_SHORT).show();
        }
    }

    // Helper methods for Adapters and Image Picking
    private void setupImagePickers() {
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) { selectedImagePath = uri.toString(); btnEditPhoto.setImageURI(uri); }
            }
        });
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) openImagePicker();
        });
    }

    private void tryPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) openImagePicker();
        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) openImagePicker();
        else permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void showExpiryDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            expiryCalendar.set(y, m, d);
            expiryDateET.setText(expiryFormat.format(expiryCalendar.getTime()));
        }, expiryCalendar.get(Calendar.YEAR), expiryCalendar.get(Calendar.MONTH), expiryCalendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void loadInventoryForCalculations() {
        productRepository.getAllProducts().observe(this, products -> {
            inventoryProducts.clear();
            if (products != null) {
                for (Product p : products) if (p.isActive() && !"Menu".equals(p.getProductType())) inventoryProducts.add(p);
            }
        });
    }

    private ArrayAdapter<String> getAdaptiveAdapter(String[] items) {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, items);
        return new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, list);
    }
}