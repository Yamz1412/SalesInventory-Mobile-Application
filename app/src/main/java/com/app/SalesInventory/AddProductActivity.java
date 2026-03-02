package com.app.SalesInventory;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AddProductActivity extends BaseActivity {
    private ImageButton btnAddPhoto;
    private TextInputEditText productNameET, productGroupET, sellingPriceET, quantityET, costPriceET, lowStockLevelET, expiryDateET;
    private Button addBtn, cancelBtn;
    private Spinner unitSpinner, existingGroupSpinner;
    private SwitchMaterial switchAddons, switchNotes, switchVariants, switchForSaleOnly;
    private LinearLayout layoutBuyingUnitQtyCritical;

    private ProductRepository productRepository;
    private AuthManager authManager;
    private SimpleDateFormat expiryFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private String selectedImagePath;
    private Calendar expiryCalendar = Calendar.getInstance();
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);
        productRepository = SalesInventoryApplication.getProductRepository();

        // Basic Info
        btnAddPhoto = findViewById(R.id.btnAddPhoto);
        productNameET = findViewById(R.id.productNameET);
        productGroupET = findViewById(R.id.productGroupET);
        sellingPriceET = findViewById(R.id.sellingPriceET);
        expiryDateET = findViewById(R.id.expiryDateET);

        // Inventory / Cost Info
        costPriceET = findViewById(R.id.costPriceET);
        quantityET = findViewById(R.id.quantityET);
        lowStockLevelET = findViewById(R.id.lowStockLevelET);
        unitSpinner = findViewById(R.id.unitSpinner);
        layoutBuyingUnitQtyCritical = findViewById(R.id.layout_buying_unit_qty_critical);

        // Buttons & Switches
        addBtn = findViewById(R.id.addBtn);
        cancelBtn = findViewById(R.id.cancelBtn);
        switchAddons = findViewById(R.id.switchAddons);
        switchNotes = findViewById(R.id.switchNotes);
        switchVariants = findViewById(R.id.switchVariants);
        switchForSaleOnly = findViewById(R.id.switchForSaleOnly);

        setupCategorySpinner();
        setupImagePickers();

        // Populate the Unit of Measurement dropdown
        String[] units = {"pcs", "ml", "oz", "g", "kg", "box", "pack"};
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(unitAdapter);

        authManager = AuthManager.getInstance();
        authManager.refreshCurrentUserStatus(success -> {
            if (!authManager.isCurrentUserAdmin()) {
                Toast.makeText(AddProductActivity.this, "Error: User not approved", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        // Hide inventory fields if the item is "For Sale Only" (Menu)
        switchForSaleOnly.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateLayoutForSelectedType();
        });

        addBtn.setOnClickListener(v -> attemptAdd());
        cancelBtn.setOnClickListener(v -> finish());
        btnAddPhoto.setOnClickListener(v -> tryPickImage());
        expiryDateET.setOnClickListener(v -> showExpiryDatePicker());

        updateLayoutForSelectedType();
    }

    private void setupCategorySpinner() {
        existingGroupSpinner = findViewById(R.id.existingGroupSpinner);
        DatabaseReference categoryRef = FirebaseDatabase.getInstance().getReference("Categories");
        categoryRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<String> groups = new ArrayList<>();
                groups.add("Select Group");
                for (DataSnapshot child : snapshot.getChildren()) {
                    Category c = child.getValue(Category.class);
                    if (c != null) groups.add(c.getCategoryName());
                }
                ArrayAdapter<String> groupAdapter = new ArrayAdapter<>(AddProductActivity.this, android.R.layout.simple_spinner_item, groups);
                existingGroupSpinner.setAdapter(groupAdapter);
            }
            @Override public void onCancelled(DatabaseError error) {}
        });

        existingGroupSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    productGroupET.setText(parent.getItemAtPosition(position).toString());
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setupImagePickers() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            selectedImagePath = uri.toString();
                            btnAddPhoto.setImageURI(uri);
                        }
                    }
                });
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openImagePicker();
                    } else {
                        Toast.makeText(this, "Permission required to select image", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void tryPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            openImagePicker();
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                openImagePicker();
            } else {
                permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
            }
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void updateLayoutForSelectedType() {
        if (switchForSaleOnly.isChecked()) {
            layoutBuyingUnitQtyCritical.setVisibility(View.GONE);
            quantityET.setText("");
            costPriceET.setText("");
            lowStockLevelET.setText("");
        } else {
            layoutBuyingUnitQtyCritical.setVisibility(View.VISIBLE);
        }
    }

    private void showExpiryDatePicker() {
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH);
        int day = now.get(Calendar.DAY_OF_MONTH);
        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            expiryCalendar.set(Calendar.YEAR, y);
            expiryCalendar.set(Calendar.MONTH, m);
            expiryCalendar.set(Calendar.DAY_OF_MONTH, d);
            Date date = expiryCalendar.getTime();
            expiryDateET.setText(expiryFormat.format(date));
        }, year, month, day);
        dialog.getDatePicker().setMinDate(System.currentTimeMillis());
        dialog.show();
    }

    private void attemptAdd() {
        String name = productNameET.getText() != null ? productNameET.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(this, "Product name is required", Toast.LENGTH_SHORT).show();
            return;
        }

        String categoryName = productGroupET.getText() != null ? productGroupET.getText().toString().trim() : "";
        if (categoryName.isEmpty()) {
            Toast.makeText(this, "Product Group is required", Toast.LENGTH_SHORT).show();
            return;
        }

        String categoryId = categoryName.toLowerCase(Locale.ROOT).replace(" ", "_");
        boolean wantMenu = switchForSaleOnly.isChecked();
        String productType = wantMenu ? "Menu" : "Inventory";

        double sellingPrice = 0;
        double costPrice = 0;
        int qty = 0;
        int criticalLevel = 1;
        int reorderLevel = 0;
        int ceilingLevel = 0;
        long expiryDate = 0L;

        try {
            sellingPrice = Double.parseDouble(sellingPriceET.getText() != null ? sellingPriceET.getText().toString() : "0");
        } catch (Exception ignored) {}

        if (sellingPrice > 1000000) {
            Toast.makeText(this, "Selling Price cannot exceed 1,000,000", Toast.LENGTH_SHORT).show();
            return;
        }

        String expiryStr = expiryDateET.getText() != null ? expiryDateET.getText().toString().trim() : "";
        if (!expiryStr.isEmpty()) {
            try {
                Date d = expiryFormat.parse(expiryStr);
                if (d != null) expiryDate = d.getTime();
                if (expiryDate < System.currentTimeMillis()) {
                    Toast.makeText(this, "Expiry date must be today or in the future", Toast.LENGTH_SHORT).show();
                    return;
                }
            } catch (ParseException ignored) {}
        }

        if ("Menu".equalsIgnoreCase(productType)) {
            costPrice = 0;
            qty = 0;
            criticalLevel = 1;
            reorderLevel = 0;
        } else {
            try {
                costPrice = Double.parseDouble(costPriceET.getText() != null ? costPriceET.getText().toString() : "0");
            } catch (Exception ignored) {}
            try {
                qty = Integer.parseInt(quantityET.getText() != null ? quantityET.getText().toString() : "0");
            } catch (Exception ignored) {}
            try {
                reorderLevel = Integer.parseInt(lowStockLevelET.getText() != null ? lowStockLevelET.getText().toString() : "0");
            } catch (Exception ignored) {}

            criticalLevel = 1;
            if (qty < 0) qty = 0;
            if (reorderLevel < 0) reorderLevel = 0;
        }

        Product p = new Product();
        p.setProductName(name);
        p.setCategoryName(categoryName);
        p.setCategoryId(categoryId);
        p.setProductType(productType);
        p.setSellingPrice(sellingPrice);
        p.setCostPrice(costPrice);
        p.setQuantity(qty);
        p.setCriticalLevel(criticalLevel);
        p.setReorderLevel(reorderLevel);
        p.setCeilingLevel(ceilingLevel);
        p.setUnit(unitSpinner.getSelectedItem() != null ? unitSpinner.getSelectedItem().toString() : "");
        p.setExpiryDate(expiryDate);
        p.setSupplier("");
        p.setDescription("");
        long now = System.currentTimeMillis();
        p.setDateAdded(now);
        p.setAddedBy("");
        p.setActive(true);
        p.setBarcode("");
        p.setImagePath(selectedImagePath);

        productRepository.addProduct(p, selectedImagePath, new ProductRepository.OnProductAddedListener() {
            @Override
            public void onProductAdded(String productId) {
                runOnUiThread(() -> {
                    Toast.makeText(AddProductActivity.this, "Product added", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(AddProductActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }
}