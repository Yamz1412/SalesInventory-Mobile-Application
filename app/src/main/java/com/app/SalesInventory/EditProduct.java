package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

public class EditProduct extends AppCompatActivity {
    private EditText productNameET, costPriceET, sellingPriceET, quantityET, unitET, minStockET;
    private Spinner categorySpinner;
    private Button updateBtn, cancelBtn;
    private ProductRepository productRepository;
    private String productId;
    private Product currentProduct;

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
        categorySpinner = findViewById(R.id.categorySpinner);
        updateBtn = findViewById(R.id.updateBtn);
        cancelBtn = findViewById(R.id.cancelBtn);
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            productId = extras.getString("productId");
            if (productId != null) {
                loadProductData();
            }
        }
        updateBtn.setOnClickListener(v -> updateProduct());
        cancelBtn.setOnClickListener(v -> finish());
    }

    private void loadProductData() {
        productRepository.getProductById(productId, new ProductRepository.OnProductFetchedListener() {
            @Override
            public void onProductFetched(Product product) {
                currentProduct = product;
                populateFields();
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
        }
    }

    private void updateProduct() {
        if (!validateInputs() || currentProduct == null) {
            return;
        }
        updateBtn.setEnabled(false);
        updateBtn.setText("Updating...");
        try {
            currentProduct.setProductName(productNameET.getText().toString().trim());
            currentProduct.setCategoryName(categorySpinner.getSelectedItem().toString());
            currentProduct.setCostPrice(Double.parseDouble(costPriceET.getText().toString().trim()));
            currentProduct.setSellingPrice(Double.parseDouble(sellingPriceET.getText().toString().trim()));
            currentProduct.setQuantity(Integer.parseInt(quantityET.getText().toString().trim()));
            currentProduct.setUnit(unitET.getText().toString().trim());
            currentProduct.setReorderLevel(Integer.parseInt(minStockET.getText().toString().trim()));
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            updateBtn.setEnabled(true);
            return;
        }
        productRepository.updateProduct(currentProduct, new ProductRepository.OnProductUpdatedListener() {
            @Override
            public void onProductUpdated() {
                Toast.makeText(EditProduct.this, "Product updated successfully", Toast.LENGTH_SHORT).show();
                finish();
            }
            @Override
            public void onError(String error) {
                Toast.makeText(EditProduct.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                updateBtn.setEnabled(true);
                updateBtn.setText("Update Product");
            }
        });
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
        try {
            double costPrice = Double.parseDouble(costPriceET.getText().toString().trim());
            double sellingPrice = Double.parseDouble(sellingPriceET.getText().toString().trim());
            if (costPrice < 0 || sellingPrice < 0) {
                Toast.makeText(this, "Prices must be positive", Toast.LENGTH_SHORT).show();
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
}