package com.app.SalesInventory;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
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

public class EditProduct extends BaseActivity {
    private EditText productNameET, costPriceET, sellingPriceET, quantityET, unitET, minStockET, expiryDateET, floorLevelET;
    private Spinner categorySpinner;
    private Button updateBtn, cancelBtn;
    private ImageButton btnEditPhoto;
    private RadioGroup rgProductTypeEdit;
    private RadioButton rbInventoryEdit, rbMenuEdit;
    private ProductRepository productRepository;
    private String productId;
    private Product currentProduct;
    private String selectedImagePath;
    private SimpleDateFormat expiryFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private Calendar calendar = Calendar.getInstance();
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;
    private DatabaseReference categoryRef;
    private List<Category> categoryList = new ArrayList<>();
    private List<Category> availableCategories = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_product);
        productRepository = SalesInventoryApplication.getProductRepository();
        productNameET = findViewById(R.id.productNameET);
        costPriceET = findViewById(R.id.costPriceET);
        sellingPriceET = findViewById(R.id.sellingPriceET);
        quantityET = findViewById(R.id.quantityET);
        unitET = findViewById(R.id.unitET);
        minStockET = findViewById(R.id.minStockET);
        floorLevelET = findViewById(R.id.floorLevelET);
        expiryDateET = findViewById(R.id.expiryDateET);
        categorySpinner = findViewById(R.id.categorySpinner);
        updateBtn = findViewById(R.id.updateBtn);
        cancelBtn = findViewById(R.id.cancelBtn);
        btnEditPhoto = findViewById(R.id.btnEditPhoto);
        rgProductTypeEdit = findViewById(R.id.rgProductTypeEdit);
        rbInventoryEdit = findViewById(R.id.rbInventoryEdit);
        rbMenuEdit = findViewById(R.id.rbMenuEdit);
        setupImagePickers();
        categoryRef = FirebaseDatabase.getInstance().getReference("Categories");
        setupCategorySpinner();
        expiryDateET.setOnClickListener(v -> showDatePicker());
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            productId = extras.getString("productId");
            if (productId != null) {
                loadProductData();
            }
        }
        rgProductTypeEdit.setOnCheckedChangeListener((group, checkedId) -> {
            filterCategoriesByType();
            updateLayoutForSelectedType();
        });
        updateBtn.setOnClickListener(v -> updateProduct());
        cancelBtn.setOnClickListener(v -> finish());
        btnEditPhoto.setOnClickListener(v -> tryPickImage());
    }

    private void setupImagePickers() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            selectedImagePath = uri.toString();
                            btnEditPhoto.setImageURI(uri);
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

    private void showDatePicker() {
        long initial = currentProduct != null ? currentProduct.getExpiryDate() : 0L;
        if (initial > 0 && initial >= System.currentTimeMillis()) {
            calendar.setTimeInMillis(initial);
        } else {
            calendar.setTimeInMillis(System.currentTimeMillis());
        }
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            calendar.set(Calendar.YEAR, y);
            calendar.set(Calendar.MONTH, m);
            calendar.set(Calendar.DAY_OF_MONTH, d);
            Date date = calendar.getTime();
            expiryDateET.setText(expiryFormat.format(date));
        }, year, month, day);
        dialog.getDatePicker().setMinDate(System.currentTimeMillis());
        dialog.show();
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
                if (currentProduct != null) {
                    setSpinnerSelectionToCategory(currentProduct.getCategoryName());
                }
            }
            @Override
            public void onCancelled(DatabaseError error) {
                Toast.makeText(EditProduct.this, "Error loading categories: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void filterCategoriesByType() {
        boolean wantMenu = rbMenuEdit.isChecked();
        availableCategories.clear();
        List<String> names = new ArrayList<>();
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(EditProduct.this, R.layout.spinner_item, names);
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        categorySpinner.setAdapter(adapter);
        categorySpinner.setSelection(0);
    }

    private void loadProductData() {
        productRepository.getProductById(productId, new ProductRepository.OnProductFetchedListener() {
            @Override
            public void onProductFetched(Product product) {
                currentProduct = product;
                runOnUiThread(() -> populateFields());
            }
            @Override
            public void onError(String error) {
            }
        });
    }

    private void populateFields() {
        if (currentProduct != null) {
            productNameET.setText(currentProduct.getProductName());
            costPriceET.setText(String.valueOf(currentProduct.getCostPrice()));
            sellingPriceET.setText(String.valueOf(currentProduct.getSellingPrice()));
            quantityET.setText(String.valueOf(currentProduct.getQuantity()));
            unitET.setText(currentProduct.getUnit());
            minStockET.setText(String.valueOf(currentProduct.getReorderLevel()));
            floorLevelET.setText(String.valueOf(Math.max(1, currentProduct.getFloorLevel())));
            if (currentProduct.getExpiryDate() > 0 && currentProduct.getExpiryDate() >= System.currentTimeMillis()) {
                expiryDateET.setText(expiryFormat.format(new Date(currentProduct.getExpiryDate())));
            } else {
                expiryDateET.setText("");
            }
            String imagePath = currentProduct.getImagePath();
            String imageUrl = currentProduct.getImageUrl();
            String toLoad = null;
            if (imageUrl != null && !imageUrl.isEmpty()) {
                toLoad = imageUrl;
            } else if (imagePath != null && !imagePath.isEmpty()) {
                toLoad = imagePath;
            }
            if (toLoad != null && !toLoad.isEmpty()) {
                selectedImagePath = toLoad;
                try {
                    Uri uri = Uri.parse(toLoad);
                    btnEditPhoto.setImageURI(uri);
                } catch (Exception ignored) {
                }
            }
            String type = currentProduct.getProductType() == null ? "" : currentProduct.getProductType();
            if ("Menu".equalsIgnoreCase(type)) {
                rbMenuEdit.setChecked(true);
            } else {
                rbInventoryEdit.setChecked(true);
            }
            filterCategoriesByType();
            setSpinnerSelectionToCategory(currentProduct.getCategoryName());
        }
    }

    private void setSpinnerSelectionToCategory(String categoryName) {
        if (categoryName == null) return;
        for (int i = 0; i < availableCategories.size(); i++) {
            Category c = availableCategories.get(i);
            if (c != null && categoryName.equalsIgnoreCase(c.getCategoryName())) {
                categorySpinner.setSelection(i);
                return;
            }
        }
    }

    private void updateLayoutForSelectedType() {
        int checkedId = rgProductTypeEdit.getCheckedRadioButtonId();
        if (checkedId == R.id.rbMenuEdit) {
            quantityET.setText("");
            costPriceET.setText("");
            minStockET.setText("");
            floorLevelET.setText("");
            unitET.setText("");
        }
    }

    private void updateProduct() {
        if (currentProduct == null) {
            return;
        }
        if (!validateInputs()) {
            return;
        }
        updateBtn.setEnabled(false);
        updateBtn.setText("Updating...");
        try {
            String costStr = costPriceET.getText().toString().trim();
            String sellingStr = sellingPriceET.getText().toString().trim();
            String qtyStr = quantityET.getText().toString().trim();
            String minStockStr = minStockET.getText().toString().trim();
            boolean isMenu = rbMenuEdit.isChecked();
            currentProduct.setProductName(productNameET.getText().toString().trim());
            currentProduct.setCostPrice(Double.parseDouble(costStr.isEmpty() ? "0" : costStr));
            if (isMenu) {
                currentProduct.setSellingPrice(0);
            } else {
                currentProduct.setSellingPrice(Double.parseDouble(sellingStr.isEmpty() ? "0" : sellingStr));
            }
            int newQty = Integer.parseInt(qtyStr.isEmpty() ? "0" : qtyStr);
            if (newQty < 0) newQty = 0;
            currentProduct.setQuantity(newQty);
            currentProduct.setUnit(unitET.getText().toString().trim());
            currentProduct.setReorderLevel(Integer.parseInt(minStockStr.isEmpty() ? "0" : minStockStr));
            currentProduct.setFloorLevel(1);
            currentProduct.setCriticalLevel(1);
            String expiryStr = expiryDateET.getText().toString().trim();
            long expiry = 0L;
            if (!expiryStr.isEmpty()) {
                try {
                    Date d = expiryFormat.parse(expiryStr);
                    if (d != null) expiry = d.getTime();
                    if (expiry < System.currentTimeMillis()) {
                        Toast.makeText(this, "Expiry date must be today or in the future", Toast.LENGTH_SHORT).show();
                        updateBtn.setEnabled(true);
                        updateBtn.setText("Update Product");
                        return;
                    }
                } catch (ParseException ignored) {
                }
            }
            currentProduct.setExpiryDate(expiry);
            if (selectedImagePath != null && !selectedImagePath.isEmpty()) {
                currentProduct.setImagePath(selectedImagePath);
                currentProduct.setImageUrl(null);
            }
            currentProduct.setProductType(rbMenuEdit.isChecked() ? "Menu" : "Inventory");
            int sel = categorySpinner.getSelectedItemPosition();
            if (sel >= 0 && sel < availableCategories.size()) {
                Category selCat = availableCategories.get(sel);
                currentProduct.setCategoryId(selCat.getCategoryId());
                currentProduct.setCategoryName(selCat.getCategoryName());
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            updateBtn.setEnabled(true);
            updateBtn.setText("Update Product");
            return;
        }
        productRepository.updateProduct(currentProduct, new ProductRepository.OnProductUpdatedListener() {
            @Override
            public void onProductUpdated() {
                runOnUiThread(() -> {
                    Toast.makeText(EditProduct.this, "Product updated successfully", Toast.LENGTH_SHORT).show();
                    navigateBackAfterUpdate();
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(EditProduct.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    updateBtn.setEnabled(true);
                    updateBtn.setText("Update Product");
                });
            }
        });
    }

    private void navigateBackAfterUpdate() {
        String type = currentProduct.getProductType() == null ? "" : currentProduct.getProductType();
        Intent intent;
        if ("Menu".equalsIgnoreCase(type)) {
            intent = new Intent(EditProduct.this, SellList.class);
        } else {
            intent = new Intent(EditProduct.this, Inventory.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private boolean validateInputs() {
        String name = productNameET.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Product name is required", Toast.LENGTH_SHORT).show();
            return false;
        }
        String costStr = costPriceET.getText().toString().trim();
        String sellingStr = sellingPriceET.getText().toString().trim();
        boolean isMenu = rbMenuEdit.isChecked();
        if (isMenu && sellingStr.isEmpty()) {
            Toast.makeText(this, "Selling price is required for menu items", Toast.LENGTH_SHORT).show();
            return false;
        }
        try {
            double costPrice = Double.parseDouble(costStr.isEmpty() ? "0" : costStr);
            double sellingPrice = Double.parseDouble(sellingStr.isEmpty() ? "0" : sellingStr);
            if (costPrice < 0 || sellingPrice < 0) {
                Toast.makeText(this, "Prices must be positive", Toast.LENGTH_SHORT).show();
                return false;
            }
            if (isMenu && sellingPrice <= 0) {
                Toast.makeText(this, "Selling price must be greater than 0 for menu items", Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}