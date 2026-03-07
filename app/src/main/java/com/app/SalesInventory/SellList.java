package com.app.SalesInventory;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
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
import java.util.Map;
import java.util.Set;

public class SellList extends BaseActivity {
    private RecyclerView sellListView;
    private SellAdapter sellAdapter;
    private List<Product> allMenuProducts;
    private List<Product> filteredProducts;
    private ProductRepository productRepository;

    // Buttons & Search
    private Button btnCheckout; // This is now your top-right button
    private EditText etSearchProduct;

    private CartManager cartManager;
    private LinearLayout layoutCategoryChips;

    private String selectedCategory = "All";
    private String currentSearchQuery = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sell_list);

        productRepository = SalesInventoryApplication.getProductRepository();
        cartManager = CartManager.getInstance();

        sellListView = findViewById(R.id.SellListD);
        btnCheckout = findViewById(R.id.btnCheckout);
        etSearchProduct = findViewById(R.id.etSearchProduct);
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
        setupSearchBar();
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
            applyFilters();
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
                buildCategoryChips(); // Refresh chip colors
                applyFilters();       // Re-filter products
            });

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(8, 0, 8, 0);
            chip.setLayoutParams(lp);
            layoutCategoryChips.addView(chip);
        }
    }

    // ====================================================================
    // SEARCH & FILTER LOGIC
    // ====================================================================
    private void setupSearchBar() {
        if (etSearchProduct != null) {
            etSearchProduct.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentSearchQuery = s.toString().toLowerCase().trim();
                    applyFilters();
                }

                @Override public void afterTextChanged(Editable s) {}
            });
        }
    }

    private void applyFilters() {
        filteredProducts.clear();
        for (Product p : allMenuProducts) {

            // Check Category Filter
            boolean matchesCategory = "All".equalsIgnoreCase(selectedCategory) ||
                    (p.getCategoryName() != null && p.getCategoryName().equalsIgnoreCase(selectedCategory));

            // Check Search Text Filter
            boolean matchesSearch = currentSearchQuery.isEmpty() ||
                    (p.getProductName() != null && p.getProductName().toLowerCase().contains(currentSearchQuery));

            if (matchesCategory && matchesSearch) {
                filteredProducts.add(p);
            }
        }
        sellAdapter.updateProducts(new ArrayList<>(filteredProducts));
    }

    private void setupCheckoutButton() {
        if (btnCheckout != null) {
            btnCheckout.setOnClickListener(v -> {
                if (cartManager.getItems().isEmpty()) {
                    Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(SellList.this, sellProduct.class);
                startActivity(intent);
            });
        }
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

    // ====================================================================
    // FULLY DYNAMIC DIALOG GENERATOR FOR POS SELECTION
    // ====================================================================
    private void showProductOptionsDialog(Product product) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_product_options, null, false);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView tvName = dialogView.findViewById(R.id.tvOptionProductName);
        TextView tvPrice = dialogView.findViewById(R.id.tvOptionProductPrice);

        TextView tvSizeHeader = dialogView.findViewById(R.id.tvSizeHeader);
        RadioGroup rgSize = dialogView.findViewById(R.id.rgSize);

        TextView tvAddonsHeader = dialogView.findViewById(R.id.tvAddonsHeader);
        LinearLayout containerAddons = dialogView.findViewById(R.id.containerAddons);

        TextView tvNotesHeader = dialogView.findViewById(R.id.tvNotesHeader);
        LinearLayout containerNotes = dialogView.findViewById(R.id.containerNotes);

        TextInputEditText etQty = dialogView.findViewById(R.id.etOptionQty);
        Button btnCancel = dialogView.findViewById(R.id.btnOptionCancel);
        Button btnAdd = dialogView.findViewById(R.id.btnOptionAddToCart);

        tvName.setText(product.getProductName());

        // Helper function to calculate and update the live Total Price text
        Runnable updatePriceDisplay = () -> {
            double currentPrice = product.getSellingPrice();

            // Add selected size price
            int selectedSizeId = rgSize.getCheckedRadioButtonId();
            if (selectedSizeId != -1) {
                RadioButton selectedRb = dialogView.findViewById(selectedSizeId);
                if (selectedRb != null && selectedRb.getTag() != null) {
                    Map<String, Object> sizeMap = (Map<String, Object>) selectedRb.getTag();
                    currentPrice += Double.parseDouble(String.valueOf(sizeMap.get("price")));
                }
            }

            // Add selected addons price
            for (int i = 0; i < containerAddons.getChildCount(); i++) {
                View child = containerAddons.getChildAt(i);
                if (child instanceof CheckBox) {
                    CheckBox cb = (CheckBox) child;
                    if (cb.isChecked() && cb.getTag() != null) {
                        Map<String, Object> addonMap = (Map<String, Object>) cb.getTag();
                        currentPrice += Double.parseDouble(String.valueOf(addonMap.get("price")));
                    }
                }
            }

            // Multiply by quantity
            int qty = 1;
            String qtyStr = etQty.getText() != null ? etQty.getText().toString() : "1";
            try { qty = Integer.parseInt(qtyStr.isEmpty() ? "1" : qtyStr); } catch (Exception e) { qty = 1; }
            if (qty <= 0) qty = 1;

            double totalPrice = currentPrice * qty;
            tvPrice.setText("Total: ₱" + String.format(Locale.US, "%.2f", totalPrice));
        };

        // 1. GENERATE SIZES DYNAMICALLY
        if (product.getSizesList() != null && !product.getSizesList().isEmpty()) {
            tvSizeHeader.setVisibility(View.VISIBLE);
            rgSize.setVisibility(View.VISIBLE);

            for (int i = 0; i < product.getSizesList().size(); i++) {
                Map<String, Object> size = product.getSizesList().get(i);
                RadioButton rb = new RadioButton(this);
                rb.setId(View.generateViewId());

                String sizeName = (String) size.get("name");
                double priceDiff = Double.parseDouble(String.valueOf(size.get("price")));

                rb.setText(sizeName + " (+₱" + priceDiff + ")");
                rb.setTag(size);
                rgSize.addView(rb);

                rb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) updatePriceDisplay.run();
                });

                if (i == 0) rb.setChecked(true); // Select first size automatically
            }
        }

        // 2. GENERATE ADD-ONS DYNAMICALLY
        if (product.getAddonsList() != null && !product.getAddonsList().isEmpty()) {
            tvAddonsHeader.setVisibility(View.VISIBLE);
            containerAddons.setVisibility(View.VISIBLE);

            for (Map<String, Object> addon : product.getAddonsList()) {
                CheckBox cb = new CheckBox(this);
                String addonName = (String) addon.get("name");
                double addonPrice = Double.parseDouble(String.valueOf(addon.get("price")));

                cb.setText(addonName + " (+₱" + addonPrice + ")");
                cb.setTag(addon);
                containerAddons.addView(cb);

                cb.setOnCheckedChangeListener((buttonView, isChecked) -> updatePriceDisplay.run());
            }
        }

        // 3. GENERATE NOTES DYNAMICALLY
        if (product.getNotesList() != null && !product.getNotesList().isEmpty()) {
            tvNotesHeader.setVisibility(View.VISIBLE);
            containerNotes.setVisibility(View.VISIBLE);

            for (Map<String, String> note : product.getNotesList()) {
                com.google.android.material.textfield.TextInputLayout til = new com.google.android.material.textfield.TextInputLayout(this);
                til.setHint(note.get("type") + " (e.g. " + note.get("value") + ")");

                TextInputEditText et = new TextInputEditText(til.getContext());
                et.setTag(note.get("type"));
                til.addView(et);

                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                params.setMargins(0, 0, 0, 16);
                til.setLayoutParams(params);

                containerNotes.addView(til);
            }
        }

        // Trigger price display change when typing quantity
        etQty.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updatePriceDisplay.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        // Initial setup for the price
        updatePriceDisplay.run();

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnAdd.setOnClickListener(v -> {
            double calculatedUnitPrice = product.getSellingPrice();
            String finalSizeStr = "";
            StringBuilder finalExtrasStr = new StringBuilder();

            // Extract selected size
            int selectedSizeId = rgSize.getCheckedRadioButtonId();
            if (selectedSizeId != -1) {
                RadioButton selectedRb = dialogView.findViewById(selectedSizeId);
                if (selectedRb != null && selectedRb.getTag() != null) {
                    Map<String, Object> sizeMap = (Map<String, Object>) selectedRb.getTag();
                    finalSizeStr = (String) sizeMap.get("name");
                    calculatedUnitPrice += Double.parseDouble(String.valueOf(sizeMap.get("price")));
                }
            }

            // Extract checked add-ons
            for (int i = 0; i < containerAddons.getChildCount(); i++) {
                View child = containerAddons.getChildAt(i);
                if (child instanceof CheckBox) {
                    CheckBox cb = (CheckBox) child;
                    if (cb.isChecked() && cb.getTag() != null) {
                        Map<String, Object> addonMap = (Map<String, Object>) cb.getTag();
                        finalExtrasStr.append(addonMap.get("name")).append(", ");
                        calculatedUnitPrice += Double.parseDouble(String.valueOf(addonMap.get("price")));
                    }
                }
            }

            // Extract typed notes
            for (int i = 0; i < containerNotes.getChildCount(); i++) {
                com.google.android.material.textfield.TextInputLayout til = (com.google.android.material.textfield.TextInputLayout) containerNotes.getChildAt(i);
                TextInputEditText et = (TextInputEditText) til.getEditText();
                if (et != null && et.getText() != null && !et.getText().toString().isEmpty()) {
                    String noteType = (String) et.getTag();
                    String noteValue = et.getText().toString();
                    finalExtrasStr.append(noteType).append(": ").append(noteValue).append(", ");
                }
            }

            String extraDetails = finalExtrasStr.toString();
            if (extraDetails.endsWith(", ")) {
                extraDetails = extraDetails.substring(0, extraDetails.length() - 2);
            }

            String qtyStr = etQty.getText() != null ? etQty.getText().toString() : "1";
            int quantity = 1;
            try { quantity = Integer.parseInt(qtyStr); } catch (Exception ignored) {}
            if (quantity <= 0) {
                Toast.makeText(this, "Quantity must be at least 1", Toast.LENGTH_SHORT).show();
                return;
            }

            String displayName = product.getProductName();
            if (!finalSizeStr.isEmpty()) {
                displayName += " (" + finalSizeStr + ")";
            }

            cartManager.addItem(
                    product.getProductId(),
                    displayName,
                    calculatedUnitPrice,
                    quantity,
                    product.getQuantity(),
                    finalSizeStr,
                    extraDetails
            );

            Toast.makeText(this, "Added to cart", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }
}