package com.app.SalesInventory;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;

public class AddProductActivity extends AppCompatActivity {
    private TextInputEditText productNameET;
    private TextInputEditText sellingPriceET;
    private TextInputEditText quantityET;
    private Button addBtn;
    private ProductRepository productRepository;
    private AuthManager authManager;

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
        addBtn = findViewById(R.id.addBtn);
        addBtn.setOnClickListener(v -> attemptAdd());
    }

    private void attemptAdd() {
        String name = productNameET.getText() != null ? productNameET.getText().toString().trim() : "";
        double price = 0;
        int qty = 0;
        try {
            price = Double.parseDouble(sellingPriceET.getText() != null ? sellingPriceET.getText().toString() : "0");
        } catch (Exception ignored) {}
        try {
            qty = Integer.parseInt(quantityET.getText() != null ? quantityET.getText().toString() : "0");
        } catch (Exception ignored) {}
        if (name.isEmpty()) {
            return;
        }
        Product p = new Product();
        p.setProductName(name);
        p.setSellingPrice(price);
        p.setQuantity(qty);
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