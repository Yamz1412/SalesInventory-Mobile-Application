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
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
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
    private TextInputEditText productNameET;
    private TextInputEditText sellingPriceET;
    private TextInputEditText quantityET;
    private TextInputEditText costPriceET;
    private TextInputEditText minStockET;
    private TextInputEditText floorLevelET;
    private TextInputEditText unitET;
    private TextInputEditText expiryDateET;
    private Button addBtn;
    private Button cancelBtn;
    private Spinner categorySpinner;
    private RadioGroup rgProductType;
    private RadioButton rbInventory;
    private RadioButton rbMenu;
    private LinearLayout layoutBuyingUnitQtyCritical;
    private ProductRepository productRepository;
    private AuthManager authManager;
    private SimpleDateFormat expiryFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private List<Category> categoryList = new ArrayList<>();
    private List<Category> availableCategories = new ArrayList<>();
    private DatabaseReference categoryRef;
    private String selectedImagePath;
    private Calendar expiryCalendar = Calendar.getInstance();
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);
        productRepository = SalesInventoryApplication.getProductRepository();
        btnAddPhoto = findViewById(R.id.btnAddPhoto);
        productNameET = findViewById(R.id.productNameET);
        sellingPriceET = findViewById(R.id.sellingPriceET);
        quantityET = findViewById(R.id.quantityET);
        costPriceET = findViewById(R.id.costPriceET);
        minStockET = findViewById(R.id.minStockET);
        floorLevelET = findViewById(R.id.floorLevelET);
        unitET = findViewById(R.id.unitET);
        expiryDateET = findViewById(R.id.expiryDateET);
        addBtn = findViewById(R.id.addBtn);
        cancelBtn = findViewById(R.id.cancelBtn);
        categorySpinner = findViewById(R.id.categorySpinner);
        rgProductType = findViewById(R.id.rgProductType);
        rbInventory = findViewById(R.id.rbInventory);
        rbMenu = findViewById(R.id.rbMenu);
        layoutBuyingUnitQtyCritical = findViewById(R.id.layout_buying_unit_qty_critical);
        rbInventory.setChecked(true);
        categoryRef = FirebaseDatabase.getInstance().getReference("Categories");
        setupCategorySpinner();
        setupImagePickers();

        authManager = AuthManager.getInstance();
        authManager.refreshCurrentUserStatus(success -> {
            if (!authManager.isCurrentUserAdmin()) {
                Toast.makeText(AddProductActivity.this, "Error: User not approved", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        rgProductType.setOnCheckedChangeListener((group, checkedId) -> {
            updateLayoutForSelectedType();
            filterCategoriesByType();
        });
        addBtn.setOnClickListener(v -> attemptAdd());
        cancelBtn.setOnClickListener(v -> finish());
        btnAddPhoto.setOnClickListener(v -> tryPickImage());
        expiryDateET.setOnClickListener(v -> showExpiryDatePicker());
        updateLayoutForSelectedType();
    }

    private void setupCategorySpinner() {
        categoryRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<Category> temp = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Category c = child.getValue(Category.class);
                    if (c != null && c.getCategoryName() != null && !c.getCategoryName().isEmpty() && c.isActive()) {
                        if (c.getType() == null || c.getType().isEmpty()) {
                            c.setType("Inventory");
                        }
                        temp.add(c);
                    }
                }
                categoryList.clear();
                categoryList.addAll(temp);
                filterCategoriesByType();
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(AddProductActivity.this, "Error loading categories: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterCategoriesByType() {
        boolean wantMenu = rbMenu.isChecked();
        List<String> names = new ArrayList<>();
        availableCategories.clear();
        for (Category c : categoryList) {
            String type = c.getType();
            if (type == null) type = "";
            if (wantMenu) {
                if ("Menu".equalsIgnoreCase(type)) {
                    availableCategories.add(c);
                    names.add(c.getCategoryName());
                }
            } else {
                if (!"Menu".equalsIgnoreCase(type)) {
                    availableCategories.add(c);
                    names.add(c.getCategoryName());
                }
            }
        }
        if (names.isEmpty()) {
            names.add("No categories");
            categorySpinner.setEnabled(false);
        } else {
            categorySpinner.setEnabled(true);
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(AddProductActivity.this, R.layout.spinner_item, names);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);
        categorySpinner.setSelection(0);
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
        int checkedId = rgProductType.getCheckedRadioButtonId();
        if (checkedId == R.id.rbMenu) {
            layoutBuyingUnitQtyCritical.setVisibility(View.GONE);
            quantityET.setText("");
            costPriceET.setText("");
            minStockET.setText("");
            floorLevelET.setText("");
            unitET.setText("");
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
        int checkedId = rgProductType.getCheckedRadioButtonId();
        if (checkedId != R.id.rbInventory && checkedId != R.id.rbMenu) {
            Toast.makeText(this, "Please select a product type", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean wantMenu = rbMenu.isChecked();
        if (availableCategories.isEmpty()) {
            Toast.makeText(this, "Please create/select a category for the chosen product type", Toast.LENGTH_SHORT).show();
            return;
        }
        int catIndex = categorySpinner.getSelectedItemPosition();
        if (catIndex < 0 || catIndex >= availableCategories.size()) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }
        Category selectedCategory = availableCategories.get(catIndex);
        String categoryName = selectedCategory.getCategoryName();
        String categoryId = selectedCategory.getCategoryId();
        String productType = wantMenu ? "Menu" : "Inventory";
        double sellingPrice = 0;
        double costPrice = 0;
        int qty = 0;
        int criticalLevel = 1;
        int reorderLevel = 0;
        int floorLevel = 1;
        int ceilingLevel = 0;
        long expiryDate = 0L;
        try {
            sellingPrice = Double.parseDouble(sellingPriceET.getText() != null ? sellingPriceET.getText().toString() : "0");
        } catch (Exception ignored) {
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
            } catch (ParseException ignored) {
            }
        }
        if ("Menu".equalsIgnoreCase(productType)) {
            costPrice = 0;
            qty = 0;
            criticalLevel = 1;
            reorderLevel = 0;
            floorLevel = 0;
        } else {
            try {
                costPrice = Double.parseDouble(costPriceET.getText() != null ? costPriceET.getText().toString() : "0");
            } catch (Exception ignored) {
            }
            try {
                qty = Integer.parseInt(quantityET.getText() != null ? quantityET.getText().toString() : "0");
            } catch (Exception ignored) {
            }
            try {
                reorderLevel = Integer.parseInt(minStockET.getText() != null ? minStockET.getText().toString() : "0");
            } catch (Exception ignored) {
            }
            floorLevel = 1;
            criticalLevel = 1;
            try {
                String floorStr = floorLevelET.getText() != null ? floorLevelET.getText().toString() : "";
                if (!floorStr.isEmpty()) floorLevel = Integer.parseInt(floorStr);
            } catch (Exception ignored) {
                floorLevel = 1;
            }
            if (floorLevel < 1) floorLevel = 1;
            if (qty < 0) qty = 0;
            if (reorderLevel < 0) reorderLevel = 0;
            if (criticalLevel < 1) criticalLevel = 1;
            sellingPrice = 0;
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
        p.setFloorLevel(floorLevel);
        p.setUnit(unitET.getText() != null ? unitET.getText().toString().trim() : "");
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