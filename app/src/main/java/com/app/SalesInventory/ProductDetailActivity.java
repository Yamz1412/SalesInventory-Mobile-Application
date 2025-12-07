package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;

public class ProductDetailActivity extends BaseActivity {

    private TextView tvName;
    private TextView tvCategory;
    private TextView tvType;
    private TextView tvPrice;
    private TextView tvCost;
    private TextView tvQty;
    private TextView tvUnit;
    private TextView tvExpiry;
    private ImageView imgProduct;
    private Button btnEdit;
    private Button btnDelete;
    private LinearLayout layoutAdminButtons;

    private ProductRepository productRepository;
    private String productId;
    private Product currentProduct;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_product_detail);

        tvName = findViewById(R.id.tvDetailName);
        tvCategory = findViewById(R.id.tvDetailCategory);
        tvType = findViewById(R.id.tvDetailType);
        tvPrice = findViewById(R.id.tvDetailPrice);
        tvCost = findViewById(R.id.tvDetailCost);
        tvQty = findViewById(R.id.tvDetailQty);
        tvUnit = findViewById(R.id.tvDetailUnit);
        tvExpiry = findViewById(R.id.tvDetailExpiry);
        imgProduct = findViewById(R.id.imgDetailProduct);
        btnEdit = findViewById(R.id.btnEditProduct);
        btnDelete = findViewById(R.id.btnDeleteProduct);
        layoutAdminButtons = findViewById(R.id.layoutAdminButtons);

        productRepository = SalesInventoryApplication.getProductRepository();

        productId = getIntent().getStringExtra("productId");
        if (productId == null || productId.isEmpty()) {
            finish();
            return;
        }

        AuthManager authManager = AuthManager.getInstance();
        authManager.refreshCurrentUserStatus(success -> runOnUiThread(() -> {
            if (authManager.isCurrentUserAdmin()) {
                layoutAdminButtons.setVisibility(View.VISIBLE);
            } else {
                layoutAdminButtons.setVisibility(View.GONE);
            }
        }));

        btnEdit.setOnClickListener(v -> {
            if (currentProduct == null) return;
            Intent i = new Intent(ProductDetailActivity.this, EditProduct.class);
            i.putExtra("productId", productId);
            startActivity(i);
        });

        btnDelete.setOnClickListener(v -> {
            if (currentProduct == null) return;
            new AlertDialog.Builder(ProductDetailActivity.this)
                    .setTitle("Delete Product")
                    .setMessage("Delete " + currentProduct.getProductName() + "?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        productRepository.deleteProduct(productId, new ProductRepository.OnProductDeletedListener() {
                            @Override
                            public void onProductDeleted(String archiveFilename) {
                                runOnUiThread(() -> finish());
                            }
                            @Override
                            public void onError(String error) {
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        productRepository.getAllProducts().observe(this, products -> {
            if (products == null) return;
            for (Product p : products) {
                if (p != null && productId.equals(p.getProductId())) {
                    currentProduct = p;
                    bindProduct(p);
                    break;
                }
            }
        });
    }

    private void bindProduct(Product p) {
        tvName.setText(p.getProductName());
        tvCategory.setText(p.getCategoryName() != null ? p.getCategoryName() : "");
        tvType.setText(p.getProductType() != null ? p.getProductType() : "");
        tvPrice.setText("Selling Price: ₱" + String.format(java.util.Locale.US, "%.2f", p.getSellingPrice()));
        tvCost.setText("Buying Price: ₱" + String.format(java.util.Locale.US, "%.2f", p.getCostPrice()));
        tvQty.setText("Quantity: " + p.getQuantity());
        tvUnit.setText("Unit: " + (p.getUnit() != null ? p.getUnit() : ""));
        if (p.getExpiryDate() > 0) {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
            tvExpiry.setText("Expiry: " + fmt.format(new java.util.Date(p.getExpiryDate())));
        } else {
            tvExpiry.setText("Expiry: N/A");
        }

        String imagePath = p.getImagePath();
        String imageUrl = p.getImageUrl();
        String toLoad = null;
        if (imageUrl != null && !imageUrl.isEmpty()) {
            toLoad = imageUrl;
        } else if (imagePath != null && !imagePath.isEmpty()) {
            toLoad = imagePath;
        }

        if (toLoad != null && !toLoad.isEmpty()) {
            Glide.with(this)
                    .load(toLoad)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(imgProduct);
        } else {
            imgProduct.setImageResource(R.drawable.ic_image_placeholder);
        }
    }
}