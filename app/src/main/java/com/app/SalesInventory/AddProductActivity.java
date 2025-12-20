package com.app.SalesInventory;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputFilter;
import android.text.Spanned;
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
    private TextInputEditText unitET;
    private TextInputEditText expiryDateET;
    private TextInputEditText costToCompleteET;
    private TextInputEditText sellingCostsET;
    private TextInputEditText normalProfitPercentET;
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
        authManager = AuthManager.getInstance();
        productRepository = SalesInventoryApplication.getProductRepository();
        btnAddPhoto = findViewById(R.id.btnAddPhoto);
        productNameET = findViewById(R.id.productNameET);
        sellingPriceET = findViewById(R.id.sellingPriceET);
        quantityET = findViewById(R.id.quantityET);
        costPriceET = findViewById(R.id.costPriceET);
        minStockET = findViewById(R.id.minStockET);
        unitET = findViewById(R.id.unitET);
        expiryDateET = findViewById(R.id.expiryDateET);
        addBtn = findViewById(R.id.addBtn);
        cancelBtn = findViewById(R.id.cancelBtn);
        categorySpinner = findViewById(R.id.categorySpinner);
        rgProductType = findViewById(R.id.rgProductType);
        rbInventory = findViewById(R.id.rbInventory);
        rbMenu = findViewById(R.id.rbMenu);
        layoutBuyingUnitQtyCritical = findViewById(R.id.layout_buying_unit_qty_critical);
        costToCompleteET = findViewById(R.id.costToCompleteET);
        sellingCostsET = findViewById(R.id.sellingCostsET);
        normalProfitPercentET = findViewById(R.id.normalProfitPercentET);
        rbInventory.setChecked(true);
        categoryRef = FirebaseDatabase.getInstance().getReference("Categories");
        setupCategorySpinner();
        setupImagePickers();
        applyInputLimits();
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

    private InputFilter createDecimalFilter(final int maxIntegerDigits, final int maxDecimalDigits) {
        return new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                String result = dest.subSequence(0, dstart) + source.toString() + dest.subSequence(dend, dest.length());
                if (result.equals(".")) return "0.";
                if (result.isEmpty()) return null;
                if (!result.matches("^\\d{0," + maxIntegerDigits + "}(\\.\\d{0," + maxDecimalDigits + "})?$")) {
                    return "";
                }
                return null;
            }
        };
    }

    private InputFilter createIntegerLengthFilter(final int maxDigits) {
        return new InputFilter.LengthFilter(maxDigits);
    }

    private void applyInputLimits() {
        costPriceET.setFilters(new InputFilter[] { createDecimalFilter(7, 2), createIntegerLengthFilter(10) });
        sellingPriceET.setFilters(new InputFilter[] { createDecimalFilter(7, 2), createIntegerLengthFilter(10) });
        costToCompleteET.setFilters(new InputFilter[] { createDecimalFilter(7, 2), createIntegerLengthFilter(10) });
        sellingCostsET.setFilters(new InputFilter[] { createDecimalFilter(7, 2), createIntegerLengthFilter(10) });
        normalProfitPercentET.setFilters(new InputFilter[] { createIntegerLengthFilter(3) });
        quantityET.setFilters(new InputFilter[] { createIntegerLengthFilter(4) });
        minStockET.setFilters(new InputFilter[] { createIntegerLengthFilter(3) });
    }

    @SuppressLint("WrongConstant")
    private void setupImagePickers() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            try {
                                Intent dataIntent = result.getData();
                                int takeFlags = dataIntent.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                                getContentResolver().takePersistableUriPermission(uri, takeFlags);
                            } catch (Exception ignored) {
                            }
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
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, false);
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        imagePickerLauncher.launch(intent);
    }

    private void setupCategorySpinner() {
        categoryRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<Category> temp = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    String id = null;
                    String name = null;
                    String type = null;
                    Boolean active = null;
                    if (child.child("categoryId").exists()) id = child.child("categoryId").getValue(String.class);
                    if (id == null && child.child("id").exists()) id = child.child("id").getValue(String.class);
                    if (id == null) id = child.getKey();
                    if (child.child("categoryName").exists()) name = child.child("categoryName").getValue(String.class);
                    if (name == null && child.child("name").exists()) name = child.child("name").getValue(String.class);
                    if (child.child("type").exists()) type = child.child("type").getValue(String.class);
                    if (type == null) type = "Inventory";
                    if (child.child("isActive").exists()) active = child.child("isActive").getValue(Boolean.class);
                    if (active == null && child.child("active").exists()) active = child.child("active").getValue(Boolean.class);
                    if (active == null) active = true;
                    if (name != null && !name.isEmpty() && active) {
                        Category c = new Category();
                        c.setCategoryId(id);
                        c.setCategoryName(name);
                        c.setType(type);
                        c.setActive(active);
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

    private void updateLayoutForSelectedType() {
        int checkedId = rgProductType.getCheckedRadioButtonId();
        if (checkedId == R.id.rbMenu) {
            layoutBuyingUnitQtyCritical.setVisibility(View.GONE);
            quantityET.setText("");
            costPriceET.setText("");
            minStockET.setText("");
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
        addBtn.setEnabled(false);
        String name = productNameET.getText() != null ? productNameET.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(this, "Product name is required", Toast.LENGTH_SHORT).show();
            addBtn.setEnabled(true);
            return;
        }
        int checkedId = rgProductType.getCheckedRadioButtonId();
        if (checkedId != R.id.rbInventory && checkedId != R.id.rbMenu) {
            Toast.makeText(this, "Please select a product type", Toast.LENGTH_SHORT).show();
            addBtn.setEnabled(true);
            return;
        }
        boolean wantMenu = rbMenu.isChecked();
        if (availableCategories.isEmpty()) {
            Toast.makeText(this, "Please create/select a category for the chosen product type", Toast.LENGTH_SHORT).show();
            addBtn.setEnabled(true);
            return;
        }
        int catIndex = categorySpinner.getSelectedItemPosition();
        if (catIndex < 0 || catIndex >= availableCategories.size()) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show();
            addBtn.setEnabled(true);
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
                    addBtn.setEnabled(true);
                    return;
                }
            } catch (ParseException ignored) {
            }
        }
        try {
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
                if (qty < 0) qty = 0;
                if (reorderLevel < 0) reorderLevel = 0;
                if (criticalLevel < 1) criticalLevel = 1;
            }
        } catch (Exception ignored) {
        }
        try {
            double ctc = 0;
            double sc = 0;
            int profit = 0;
            try {
                ctc = Double.parseDouble(costToCompleteET.getText() != null ? costToCompleteET.getText().toString() : "0");
            } catch (Exception ignored) {
            }
            try {
                sc = Double.parseDouble(sellingCostsET.getText() != null ? sellingCostsET.getText().toString() : "0");
            } catch (Exception ignored) {
            }
            try {
                profit = Integer.parseInt(normalProfitPercentET.getText() != null ? normalProfitPercentET.getText().toString() : "0");
            } catch (Exception ignored) {
            }
            if (costPrice < 0 || costPrice > 1000000) {
                Toast.makeText(this, "Buying price must be between 0 and 1,000,000", Toast.LENGTH_SHORT).show();
                addBtn.setEnabled(true);
                return;
            }
            if (ctc < 0 || ctc > 1000000) {
                Toast.makeText(this, "Estimated Cost to Complete must be between 0 and 1,000,000", Toast.LENGTH_SHORT).show();
                addBtn.setEnabled(true);
                return;
            }
            if (sc < 0 || sc > 1000000) {
                Toast.makeText(this, "Estimated Selling Costs must be between 0 and 1,000,000", Toast.LENGTH_SHORT).show();
                addBtn.setEnabled(true);
                return;
            }
            if (profit < 0 || profit > 999) {
                Toast.makeText(this, "Profit percent must be a 0-999 value", Toast.LENGTH_SHORT).show();
                addBtn.setEnabled(true);
                return;
            }
            if (qty < 0 || qty > 1000) {
                Toast.makeText(this, "Quantity must be 0 to 1000", Toast.LENGTH_SHORT).show();
                addBtn.setEnabled(true);
                return;
            }
            if (reorderLevel < 0 || reorderLevel > 999) {
                Toast.makeText(this, "Reorder level must be 0 to 999", Toast.LENGTH_SHORT).show();
                addBtn.setEnabled(true);
                return;
            }
            if (minStockET.getText() != null) {
                try {
                    int crit = Integer.parseInt(minStockET.getText().toString());
                    if (crit < 1) crit = 1;
                    if (crit > 50) {
                        Toast.makeText(this, "Critical level must be between 1 and 50", Toast.LENGTH_SHORT).show();
                        addBtn.setEnabled(true);
                        return;
                    }
                    criticalLevel = crit;
                } catch (Exception ignored) {
                }
            }
            if (sellingPrice < 0 || sellingPrice > 9999999) {
                Toast.makeText(this, "Selling price must be between 0 and 9,999,999", Toast.LENGTH_SHORT).show();
                addBtn.setEnabled(true);
                return;
            }
        } catch (Exception e) {
            Toast.makeText(this, "Invalid input", Toast.LENGTH_SHORT).show();
            addBtn.setEnabled(true);
            return;
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
                runOnUiThread(() -> {
                    Toast.makeText(AddProductActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    addBtn.setEnabled(true);
                });
            }
        });
    }
}