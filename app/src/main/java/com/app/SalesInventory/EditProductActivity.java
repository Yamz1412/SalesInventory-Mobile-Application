package com.app.SalesInventory;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

public class EditProductActivity extends AppCompatActivity {
    private TextInputEditText productNameET;
    private TextInputEditText sellingPriceET;
    private TextInputEditText quantityET;
    private Button updateBtn;
    private ProductRepository productRepository;
    private AuthManager authManager;
    private String productId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_product);
        productNameET = findViewById(R.id.productNameET);
        sellingPriceET = findViewById(R.id.sellingPriceET);
        quantityET = findViewById(R.id.quantityET);
        updateBtn = findViewById(R.id.updateBtn);
        authManager = AuthManager.getInstance();
        if (!authManager.isCurrentUserAdmin()) {
            finish();
            return;
        }
        productRepository = SalesInventoryApplication.getProductRepository();
        productId = getIntent().getStringExtra("productId");
        if (productId != null && !productId.isEmpty()) {
            productRepository.getProductById(productId, new ProductRepository.OnProductFetchedListener() {
                @Override
                public void onProductFetched(Product product) {
                    runOnUiThread(() -> {
                        productNameET.setText(product.getProductName());
                        sellingPriceET.setText(String.valueOf(product.getSellingPrice()));
                        quantityET.setText(String.valueOf(product.getQuantity()));
                    });
                }
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(EditProductActivity.this, "Product not found", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            });
        }
        updateBtn.setOnClickListener(v -> attemptUpdate());
    }

    private void attemptUpdate() {
        String name = productNameET.getText() != null ? productNameET.getText().toString().trim() : "";
        double price = 0;
        int qty = 0;
        try { price = Double.parseDouble(sellingPriceET.getText() != null ? sellingPriceET.getText().toString() : "0"); } catch (Exception ignored) {}
        try { qty = Integer.parseInt(quantityET.getText() != null ? quantityET.getText().toString() : "0"); } catch (Exception ignored) {}
        if (productId == null || productId.isEmpty() || name.isEmpty()) {
            return;
        }
        Product p = new Product();
        p.setProductId(productId);
        p.setProductName(name);
        p.setSellingPrice(price);
        p.setQuantity(qty);
        productRepository.updateProduct(p, new ProductRepository.OnProductUpdatedListener() {
            @Override
            public void onProductUpdated() {
                runOnUiThread(() -> {
                    Toast.makeText(EditProductActivity.this, "Updated", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(EditProductActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }
}