package com.app.SalesInventory;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SellList extends BaseActivity {
    private RecyclerView sellListView;
    private SellAdapter sellAdapter;
    private List<Product> allMenuProducts;
    private List<Product> filteredProducts;
    private ProductRepository productRepository;
    private Button btnCheckout;
    private CartManager cartManager;
    private LinearLayout layoutCategoryChips;
    private String selectedCategory = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sell_list);
        productRepository = SalesInventoryApplication.getProductRepository();
        cartManager = CartManager.getInstance();
        sellListView = findViewById(R.id.SellListD);
        btnCheckout = findViewById(R.id.btnCheckout);
        layoutCategoryChips = findViewById(R.id.layoutCategoryChips);

        allMenuProducts = new ArrayList<>();
        filteredProducts = new ArrayList<>();

        sellAdapter = new SellAdapter(this, filteredProducts);
        sellAdapter.setOnProductClickListener(this::showProductOptionsDialog);
        sellAdapter.setOnProductLongClickListener(this::handleProductLongClick);

        sellListView.setLayoutManager(new GridLayoutManager(this, 3));
        sellListView.setAdapter(sellAdapter);

        loadProducts();
        setupCheckoutButton();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadProducts();
    }

    private void loadProducts() {
        productRepository.getAllProducts().observe(this, products -> {
            allMenuProducts.clear();
            if (products != null) {
                for (Product p : products) {
                    if (p == null || !p.isActive()) continue;
                    String type = p.getProductType() == null ? "" : p.getProductType();
                    if ("Menu".equalsIgnoreCase(type)) {
                        allMenuProducts.add(p);
                    }
                }
            }
            buildCategoryChips();
            applyCategoryFilter();
        });
    }

    private void buildCategoryChips() {
        layoutCategoryChips.removeAllViews();
        Set<String> categories = new HashSet<>();
        for (Product p : allMenuProducts) {
            String c = p.getCategoryName();
            if (c != null && !c.isEmpty()) {
                categories.add(c);
            }
        }
        List<String> list = new ArrayList<>();
        list.add("All");
        list.addAll(categories);

        for (String cat : list) {
            TextView chip = new TextView(this);
            chip.setText(cat);
            chip.setPadding(32, 12, 32, 12);
            chip.setTextSize(14f);
            chip.setAllCaps(false);
            chip.setBackgroundResource(R.drawable.chip_background);
            chip.setTextColor(getResources().getColor(cat.equals(selectedCategory) ? android.R.color.white : android.R.color.black));
            chip.setOnClickListener(v -> {
                selectedCategory = cat;
                buildCategoryChips();
                applyCategoryFilter();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(8, 0, 8, 0);
            chip.setLayoutParams(lp);
            layoutCategoryChips.addView(chip);
        }
    }

    private void applyCategoryFilter() {
        filteredProducts.clear();
        for (Product p : allMenuProducts) {
            if ("All".equalsIgnoreCase(selectedCategory)) {
                filteredProducts.add(p);
            } else {
                String c = p.getCategoryName() == null ? "" : p.getCategoryName();
                if (c.equalsIgnoreCase(selectedCategory)) {
                    filteredProducts.add(p);
                }
            }
        }
        sellAdapter.updateProducts(new ArrayList<>(filteredProducts));
    }

    private void setupCheckoutButton() {
        btnCheckout.setOnClickListener(v -> {
            if (cartManager.getItems().isEmpty()) {
                Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(SellList.this, sellProduct.class);
            startActivity(intent);
        });
    }

    private void handleProductLongClick(Product product) {
        AuthManager authManager = AuthManager.getInstance();
        authManager.isCurrentUserAdminAsync(success -> runOnUiThread(() -> {
            if (!success) {
                Toast.makeText(this, "Only admins can manage products", Toast.LENGTH_SHORT).show();
                return;
            }
            Intent i = new Intent(SellList.this, ProductDetailActivity.class);
            i.putExtra("productId", product.getProductId());
            startActivity(i);
        }));
    }

    private void showProductOptionsDialog(Product product) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_product_options, null, false);
        TextView tvName = dialogView.findViewById(R.id.tvOptionProductName);
        TextView tvPrice = dialogView.findViewById(R.id.tvOptionProductPrice);
        RadioGroup rgSize = dialogView.findViewById(R.id.rgSize);
        RadioButton rbSmall = dialogView.findViewById(R.id.rbSizeSmall);
        RadioButton rbMedium = dialogView.findViewById(R.id.rbSizeMedium);
        RadioButton rbLarge = dialogView.findViewById(R.id.rbSizeLarge);
        CheckBox cbExtraShot = dialogView.findViewById(R.id.cbAddonExtraShot);
        CheckBox cbWhipped = dialogView.findViewById(R.id.cbAddonWhippedCream);
        CheckBox cbSyrup = dialogView.findViewById(R.id.cbAddonSyrup);
        TextInputEditText etQty = dialogView.findViewById(R.id.etOptionQty);
        Button btnCancel = dialogView.findViewById(R.id.btnOptionCancel);
        Button btnAddToCart = dialogView.findViewById(R.id.btnOptionAddToCart);

        tvName.setText(product.getProductName());
        double basePrice = product.getSellingPrice();
        tvPrice.setText("₱" + String.format(Locale.US, "%.2f", basePrice));

        rbMedium.setChecked(true);
        etQty.setText("1");

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        rgSize.setOnCheckedChangeListener((group, checkedId) -> updatePriceDisplay(
                tvPrice, basePrice, rbSmall.isChecked(), rbLarge.isChecked(),
                cbExtraShot.isChecked(), cbWhipped.isChecked(), cbSyrup.isChecked(),
                etQty.getText().toString()
        ));

        cbExtraShot.setOnCheckedChangeListener((buttonView, isChecked) -> updatePriceDisplay(
                tvPrice, basePrice, rbSmall.isChecked(), rbLarge.isChecked(),
                cbExtraShot.isChecked(), cbWhipped.isChecked(), cbSyrup.isChecked(),
                etQty.getText().toString()
        ));

        cbWhipped.setOnCheckedChangeListener((buttonView, isChecked) -> updatePriceDisplay(
                tvPrice, basePrice, rbSmall.isChecked(), rbLarge.isChecked(),
                cbExtraShot.isChecked(), cbWhipped.isChecked(), cbSyrup.isChecked(),
                etQty.getText().toString()
        ));

        cbSyrup.setOnCheckedChangeListener((buttonView, isChecked) -> updatePriceDisplay(
                tvPrice, basePrice, rbSmall.isChecked(), rbLarge.isChecked(),
                cbExtraShot.isChecked(), cbWhipped.isChecked(), cbSyrup.isChecked(),
                etQty.getText().toString()
        ));

        etQty.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                updatePriceDisplay(
                        tvPrice, basePrice, rbSmall.isChecked(), rbLarge.isChecked(),
                        cbExtraShot.isChecked(), cbWhipped.isChecked(), cbSyrup.isChecked(),
                        s.toString()
                );
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnAddToCart.setOnClickListener(v -> {
            String size;
            int checkedId = rgSize.getCheckedRadioButtonId();
            if (checkedId == R.id.rbSizeSmall) {
                size = "Small";
            } else if (checkedId == R.id.rbSizeLarge) {
                size = "Large";
            } else {
                size = "Medium";
            }

            List<String> addonList = new ArrayList<>();
            if (cbExtraShot.isChecked()) addonList.add("Extra Shot");
            if (cbWhipped.isChecked()) addonList.add("Whipped Cream");
            if (cbSyrup.isChecked()) addonList.add("Syrup");
            String addon = "";
            if (!addonList.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < addonList.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(addonList.get(i));
                }
                addon = sb.toString();
            }

            String qtyStr = etQty.getText() != null ? etQty.getText().toString().trim() : "";
            int q;
            try {
                q = Integer.parseInt(qtyStr.isEmpty() ? "0" : qtyStr);
            } catch (Exception e) {
                Toast.makeText(this, "Invalid quantity", Toast.LENGTH_SHORT).show();
                return;
            }
            if (q <= 0) {
                Toast.makeText(this, "Quantity must be at least 1", Toast.LENGTH_SHORT).show();
                return;
            }

            String displayName = product.getProductName() + " (" + size + ")";
            double calculatedPrice = calculateItemPrice(
                    product.getSellingPrice(),
                    rbSmall.isChecked(),
                    rbLarge.isChecked(),
                    cbExtraShot.isChecked(),
                    cbWhipped.isChecked(),
                    cbSyrup.isChecked()
            );

            cartManager.addItem(
                    product.getProductId(),
                    displayName,
                    calculatedPrice,
                    q,
                    product.getQuantity(),
                    size,
                    addon
            );

            Toast.makeText(this, "Added to cart", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }
    private double calculateItemPrice(double basePrice, boolean isSmall, boolean isLarge,
                                      boolean extraShot, boolean whipped, boolean syrup) {
        double price = basePrice;

        if (isSmall) {
            price *= 0.8;
        } else if (isLarge) {
            price *= 1.2;
        }

        if (extraShot) price += 15;
        if (whipped) price += 10;
        if (syrup) price += 10;

        return price;
    }
    private void updatePriceDisplay(TextView tvPrice, double basePrice, boolean isSmall, boolean isLarge,
                                    boolean extraShot, boolean whipped, boolean syrup, String qtyStr) {
        double itemPrice = calculateItemPrice(basePrice, isSmall, isLarge, extraShot, whipped, syrup);

        int qty = 1;
        try {
            qty = Integer.parseInt(qtyStr.isEmpty() ? "1" : qtyStr);
            if (qty <= 0) qty = 1;
        } catch (Exception e) {
            qty = 1;
        }

        double totalPrice = itemPrice * qty;
        tvPrice.setText("₱" + String.format(Locale.US, "%.2f", totalPrice));
    }
}