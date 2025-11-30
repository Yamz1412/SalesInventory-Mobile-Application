package com.app.SalesInventory;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AddProductActivity extends BaseActivity  {
    private TextInputEditText productNameET;
    private TextInputEditText sellingPriceET;
    private TextInputEditText quantityET;
    private TextInputEditText costPriceET;
    private TextInputEditText minStockET;
    private TextInputEditText unitET;
    private TextInputEditText expiryDateET;
    private Button addBtn;
    private Button cancelBtn;
    private Spinner categorySpinner;
    private LinearLayout layoutBuyingUnitQtyCritical;
    private ProductRepository productRepository;
    private AuthManager authManager;
    private SimpleDateFormat expiryFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private List<Category> categoryList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);
        authManager = AuthManager.getInstance();
        if (!authManager.isCurrentUserApproved()) {
            finish();
            return;
        }
        productRepository = SalesInventoryApplication.getProductRepository();
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
        layoutBuyingUnitQtyCritical = findViewById(R.id.layout_buying_unit_qty_critical);

        setupCategorySpinner();

        categorySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                updateLayoutForSelectedCategory();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        addBtn.setOnClickListener(v -> attemptAdd());
        cancelBtn.setOnClickListener(v -> finish());
    }

    private void setupCategorySpinner() {
        CategoryRepository categoryRepository = CategoryRepository.getInstance(getApplication());
        categoryRepository.getAllCategories(new CategoryRepository.CategoryListCallback() {
            @Override
            public void onSuccess(List<Category> categories) {
                runOnUiThread(() -> {
                    categoryList.clear();
                    categoryList.addAll(categories);
                    List<String> names = new ArrayList<>();
                    for (Category c : categoryList) {
                        names.add(c.getName());
                    }
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(AddProductActivity.this, android.R.layout.simple_spinner_item, names);
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    categorySpinner.setAdapter(adapter);
                    updateLayoutForSelectedCategory();
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() ->
                        Toast.makeText(AddProductActivity.this, "Error loading categories: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void updateLayoutForSelectedCategory() {
        int index = categorySpinner.getSelectedItemPosition();
        if (index < 0 || index >= categoryList.size()) {
            layoutBuyingUnitQtyCritical.setVisibility(View.VISIBLE);
            return;
        }
        Category selected = categoryList.get(index);
        String type = selected.getType();
        if ("Menu".equalsIgnoreCase(type)) {
            layoutBuyingUnitQtyCritical.setVisibility(View.GONE);
        } else {
            layoutBuyingUnitQtyCritical.setVisibility(View.VISIBLE);
        }
    }

    private void attemptAdd() {
        String name = productNameET.getText() != null ? productNameET.getText().toString().trim() : "";
        if (name.isEmpty()) {
            Toast.makeText(this, "Product name is required", Toast.LENGTH_SHORT).show();
            return;
        }

        int catIndex = categorySpinner.getSelectedItemPosition();
        if (catIndex < 0 || catIndex >= categoryList.size()) {
            Toast.makeText(this, "Please select a category", Toast.LENGTH_SHORT).show();
            return;
        }
        Category selectedCategory = categoryList.get(catIndex);
        String categoryName = selectedCategory.getName();
        String categoryId = selectedCategory.getId();
        String productType = selectedCategory.getType();

        double sellingPrice = 0;
        double costPrice = 0;
        int qty = 0;
        int criticalLevel = 0;
        long expiryDate = 0L;

        try {
            sellingPrice = Double.parseDouble(sellingPriceET.getText() != null ? sellingPriceET.getText().toString() : "0");
        } catch (Exception ignored) {}

        if (sellingPrice <= 0) {
            Toast.makeText(this, "Selling price is required", Toast.LENGTH_SHORT).show();
            return;
        }

        String expiryStr = expiryDateET.getText() != null ? expiryDateET.getText().toString().trim() : "";
        if (!expiryStr.isEmpty()) {
            try {
                Date d = expiryFormat.parse(expiryStr);
                if (d != null) expiryDate = d.getTime();
            } catch (ParseException ignored) {}
        }

        if ("Raw".equalsIgnoreCase(productType)) {
            try {
                costPrice = Double.parseDouble(costPriceET.getText() != null ? costPriceET.getText().toString() : "0");
            } catch (Exception ignored) {}
            try {
                qty = Integer.parseInt(quantityET.getText() != null ? quantityET.getText().toString() : "0");
            } catch (Exception ignored) {}
            try {
                criticalLevel = Integer.parseInt(minStockET.getText() != null ? minStockET.getText().toString() : "0");
            } catch (Exception ignored) {}
            if (qty < 0) qty = 0;
        } else {
            costPrice = 0;
            qty = 0;
            criticalLevel = 0;
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
        p.setUnit(unitET.getText() != null ? unitET.getText().toString().trim() : "");
        p.setExpiryDate(expiryDate);
        p.setReorderLevel(0);
        p.setCeilingLevel(0);
        p.setSupplier("");
        p.setDescription("");
        long now = System.currentTimeMillis();
        p.setDateAdded(now);
        p.setAddedBy("");
        p.setActive(true);
        p.setBarcode("");

        productRepository.addProduct(p, new ProductRepository.OnProductAddedListener() {
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