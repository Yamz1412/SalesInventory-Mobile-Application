package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class AddProduct extends AppCompatActivity {
    private static final String TAG = "AddProduct";

    // UI Components
    private EditText productNameET, costPriceET, sellingPriceET, quantityET, unitET, minStockET;
    private Spinner categorySpinner;
    private Button addBtn, cancelBtn;

    // Repositories
    private ProductRepository productRepository;
    private CategoryRepository categoryRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);
        initializeRepositories();
        initializeUI();
        setupClickListeners();
    }

    private void initializeRepositories() {
        productRepository = SalesInventoryApplication.getProductRepository();
        categoryRepository = SalesInventoryApplication.getCategoryRepository();
        Log.d(TAG, "Repositories initialized");
    }

    private void initializeUI() {
        productNameET = findViewById(R.id.productNameET);
        costPriceET = findViewById(R.id.costPriceET);
        sellingPriceET = findViewById(R.id.sellingPriceET);
        quantityET = findViewById(R.id.quantityET);
        unitET = findViewById(R.id.unitET);
        minStockET = findViewById(R.id.minStockET);
        categorySpinner = findViewById(R.id.categorySpinner);
        addBtn = findViewById(R.id.addBtn);
        cancelBtn = findViewById(R.id.cancelBtn);
    }
    private void setupClickListeners() {
        addBtn.setOnClickListener(v -> addProduct());
        cancelBtn.setOnClickListener(v -> finish());
    }

    private void addProduct() {
        if (!validateInputs()) {
            return;
        }

        // Show loading indicator
        addBtn.setEnabled(false);
        addBtn.setText("Adding...");

        try {
            // Parse numbers from Strings
            String name = productNameET.getText().toString().trim();
            String category = categorySpinner.getSelectedItem() != null ? categorySpinner.getSelectedItem().toString() : "Uncategorized";
            double cost = Double.parseDouble(costPriceET.getText().toString().trim());
            double price = Double.parseDouble(sellingPriceET.getText().toString().trim());
            int quantity = Integer.parseInt(quantityET.getText().toString().trim());
            String unit = unitET.getText().toString().trim();
            int reorderLevel = Integer.parseInt(minStockET.getText().toString().trim());

        // Create product object
        Product product = new Product();
        product.setProductName(name);
        product.setCategoryName(category);
        product.setCostPrice(cost);
        product.setSellingPrice(price);
        product.setQuantity(quantity);
        product.setUnit(unit);
        product.setReorderLevel(reorderLevel);

        // Add product to Firestore
        productRepository.addProduct(product, new ProductRepository.OnProductAddedListener() {
            @Override
            public void onProductAdded(String productId) {
                Toast.makeText(AddProduct.this, "Product added successfully", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Product added: " + productId);
                clearFields();
                finish();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(AddProduct.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error adding product: " + error);

                // Re-enable button
                addBtn.setEnabled(true);
                addBtn.setText("Add Product");
            }
        });
    } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            addBtn.setEnabled(true);
            addBtn.setText("Add Product");
        }
    }

    private boolean validateInputs() {
        if (productNameET.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Product name is required", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (costPriceET.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Cost price is required", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (sellingPriceET.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Selling price is required", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (quantityET.getText().toString().trim().isEmpty()) {
            Toast.makeText(this, "Quantity is required", Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            double costPrice = Double.parseDouble(costPriceET.getText().toString().trim());
            double sellingPrice = Double.parseDouble(sellingPriceET.getText().toString().trim());
            int quantity = Integer.parseInt(quantityET.getText().toString().trim());

            if (costPrice < 0 || sellingPrice < 0 || quantity < 0) {
                Toast.makeText(this, "Prices and quantity must be positive", Toast.LENGTH_SHORT).show();
                return false;
            }

            if (sellingPrice < costPrice) {
                Toast.makeText(this, "Selling price must be greater than cost price", Toast.LENGTH_SHORT).show();
                return false;
            }
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    /**
     * Clear input fields
     */
    private void clearFields() {
        productNameET.setText("");
        costPriceET.setText("");
        sellingPriceET.setText("");
        quantityET.setText("");
        unitET.setText("");
        minStockET.setText("");
    }
}