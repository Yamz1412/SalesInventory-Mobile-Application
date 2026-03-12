package com.app.SalesInventory;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;

public class ProductDetailActivity extends BaseActivity {

    private TextView tvName, tvCategory, tvType, tvPrice, tvCost, tvQty, tvUnit, tvExpiry;
    private ImageView imgProduct;
    private Button btnDelete; // btnEdit has been removed to match your XML

    private ProductRepository productRepository;
    private String productId;
    private Product currentProduct;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        // 1. Bind Views (Edit button removed)
        tvName = findViewById(R.id.tvDetailName);
        tvCategory = findViewById(R.id.tvDetailCategory);
        tvType = findViewById(R.id.tvDetailType);
        tvPrice = findViewById(R.id.tvDetailPrice);
        tvCost = findViewById(R.id.tvDetailCost);
        tvQty = findViewById(R.id.tvDetailQty);
        tvUnit = findViewById(R.id.tvDetailUnit);
        tvExpiry = findViewById(R.id.tvDetailExpiry);
        imgProduct = findViewById(R.id.imgDetailProduct);
        btnDelete = findViewById(R.id.btnDeleteProduct);

        productRepository = SalesInventoryApplication.getProductRepository();

        // 2. Get Product ID safely
        productId = getIntent().getStringExtra("productId");
        if (productId == null) productId = getIntent().getStringExtra("PRODUCT_ID");

        if (productId == null || productId.isEmpty()) {
            Toast.makeText(this, "Error: Product details not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 3. Setup Delete Listener (Edit listener removed)
        if (btnDelete != null) {
            btnDelete.setOnClickListener(v -> showDeleteConfirmation());
        }

        loadProductDetails();
    }

    private void loadProductDetails() {
        productRepository.getProductById(productId, new ProductRepository.OnProductFetchedListener() {
            @Override
            public void onProductFetched(Product p) {
                // FIXED: Must run on Main Thread to prevent crash
                runOnUiThread(() -> {
                    if (p != null) {
                        currentProduct = p;
                        bindDataToUI(p);
                    } else {
                        Toast.makeText(ProductDetailActivity.this, "Product no longer exists", Toast.LENGTH_SHORT).show();
                        finish();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(ProductDetailActivity.this, error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void bindDataToUI(Product p) {
        tvName.setText(p.getProductName());
        tvCategory.setText("Category: " + (p.getCategoryName() != null ? p.getCategoryName() : "N/A"));
        tvType.setText("Type: " + (p.getProductType() != null ? p.getProductType() : "Inventory"));
        tvPrice.setText("Selling Price: ₱" + String.format(java.util.Locale.US, "%.2f", p.getSellingPrice()));
        tvCost.setText("Buying Price: ₱" + String.format(java.util.Locale.US, "%.2f", p.getCostPrice()));
        tvQty.setText("Current Stock: " + p.getQuantity());
        tvUnit.setText("Unit: " + (p.getUnit() != null ? p.getUnit() : ""));

        if (p.getExpiryDate() > 0) {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
            tvExpiry.setText("Expiry: " + fmt.format(new java.util.Date(p.getExpiryDate())));
        } else {
            tvExpiry.setText("Expiry: N/A");
        }

        String toLoad = (p.getImageUrl() != null && !p.getImageUrl().isEmpty()) ? p.getImageUrl() : p.getImagePath();
        if (toLoad != null && !toLoad.isEmpty()) {
            Glide.with(this)
                    .load(toLoad)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .thumbnail(0.25f) // Instantly loads a low-res thumbnail while the full image decodes
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(imgProduct);
        } else {
            imgProduct.setImageResource(R.drawable.ic_image_placeholder);
        }
    }

    private void showDeleteConfirmation() {
        if (currentProduct == null) return;

        new AlertDialog.Builder(this)
                .setTitle("Archive Product")
                .setMessage("Are you sure you want to delete " + currentProduct.getProductName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    productRepository.deleteProduct(productId, new ProductRepository.OnProductDeletedListener() {
                        @Override
                        public void onProductDeleted(String msg) {
                            runOnUiThread(() -> {
                                Toast.makeText(ProductDetailActivity.this, "Successfully Deleted", Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }
                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> Toast.makeText(ProductDetailActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}