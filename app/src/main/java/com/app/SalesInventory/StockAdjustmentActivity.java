package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StockAdjustmentActivity extends BaseActivity {

    private Spinner spinnerProduct, spinnerAdjustmentType, spinnerReason;
    private EditText etQuantity, etRemarks;
    private TextView tvCurrentStock, tvNewStock;
    private Button btnAdjust, btnViewHistory;

    private List<Product> productList;
    private Product selectedProduct;
    private ProductRepository productRepository;

    private static final int FLOOR_LEVEL = 1;
    private static final int MAX_STOCK = 99999;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_adjustment);

        spinnerProduct = findViewById(R.id.spinnerProduct);
        spinnerAdjustmentType = findViewById(R.id.spinnerAdjustmentType);
        spinnerReason = findViewById(R.id.spinnerReason);
        etQuantity = findViewById(R.id.etQuantity);
        etRemarks = findViewById(R.id.etRemarks);
        tvCurrentStock = findViewById(R.id.tvCurrentStock);
        tvNewStock = findViewById(R.id.tvNewStock);
        btnAdjust = findViewById(R.id.btnAdjust);
        btnViewHistory = findViewById(R.id.btnViewHistory);

        productRepository = SalesInventoryApplication.getProductRepository();

        setupSpinners();
        loadProducts();

        spinnerProduct.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && productList != null && productList.size() > position - 1) {
                    selectedProduct = productList.get(position - 1);
                    tvCurrentStock.setText(String.valueOf(selectedProduct.getQuantity()));
                    calculateNewStock();
                } else {
                    selectedProduct = null;
                    tvCurrentStock.setText("0");
                    calculateNewStock();
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        etQuantity.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { calculateNewStock(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        spinnerAdjustmentType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { calculateNewStock(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        btnAdjust.setOnClickListener(v -> performAdjustment());
        btnViewHistory.setOnClickListener(v -> startActivity(new android.content.Intent(this, AdjustmentHistoryActivity.class)));
    }

    private void setupSpinners() {
        // Adjustment Type Spinner - Simple Add or Remove
        String[] adjustmentTypes = {"Add Stock", "Remove Stock"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, adjustmentTypes);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAdjustmentType.setAdapter(typeAdapter);

        // Reason Spinner
        String[] reasons = {
                "Purchase/Receiving",
                "Sales Return",
                "Damaged Product",
                "Expired Product",
                "Lost/Stolen",
                "Inventory Count Correction",
                "Low Stock",
                "Other"
        };
        ArrayAdapter<String> reasonAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, reasons);
        reasonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReason.setAdapter(reasonAdapter);
    }

    private void loadProducts() {
        productRepository.getAllProducts().observe(this, products -> {
            productList = new ArrayList<>();
            List<String> productNames = new ArrayList<>();
            productNames.add("Select Product");

            if (products != null) {
                // 1. Filter out inactive products
                List<Product> activeProducts = new ArrayList<>();
                for (Product p : products) {
                    if (p != null && p.isActive()) {
                        activeProducts.add(p);
                    }
                }

                // 2. Sort Alphabetically (A to Z)
                java.util.Collections.sort(activeProducts, (p1, p2) -> {
                    String name1 = p1.getProductName() != null ? p1.getProductName() : "";
                    String name2 = p2.getProductName() != null ? p2.getProductName() : "";
                    return name1.compareToIgnoreCase(name2); // Ascending order
                });

                // 3. Populate the lists for the Spinner
                for (Product p : activeProducts) {
                    productList.add(p);
                    productNames.add(p.getProductName());
                }
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<>(StockAdjustmentActivity.this,
                    android.R.layout.simple_spinner_item, productNames);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerProduct.setAdapter(adapter);
            spinnerProduct.setEnabled(true);
        });
    }

    private void calculateNewStock() {
        if (selectedProduct == null) {
            tvNewStock.setText("0");
            tvNewStock.setTextColor(getResources().getColor(R.color.defaultColor));
            return;
        }

        String quantityStr = etQuantity.getText().toString().trim();
        if (quantityStr.isEmpty()) {
            tvNewStock.setText(String.valueOf(selectedProduct.getQuantity()));
            tvNewStock.setTextColor(getResources().getColor(R.color.defaultColor));
            return;
        }

        try {
            int currentStock = selectedProduct.getQuantity();
            int adjustmentQty = Integer.parseInt(quantityStr);
            String adjustmentType = spinnerAdjustmentType.getSelectedItem().toString();

            // Calculate new stock based on adjustment type
            int newStock;
            if ("Add Stock".equals(adjustmentType)) {
                newStock = currentStock + adjustmentQty;
            } else {
                // Remove Stock
                newStock = currentStock - adjustmentQty;
            }

            // Ensure stock doesn't go below floor level
            int finalStock = Math.max(FLOOR_LEVEL, newStock);
            tvNewStock.setText(String.valueOf(finalStock));

            // Color coding for visual feedback
            if (newStock < FLOOR_LEVEL) {
                tvNewStock.setTextColor(getResources().getColor(R.color.errorRed));
            } else {
                tvNewStock.setTextColor(getResources().getColor(R.color.successGreen));
            }
        } catch (NumberFormatException e) {
            tvNewStock.setText(String.valueOf(selectedProduct.getQuantity()));
            tvNewStock.setTextColor(getResources().getColor(R.color.defaultColor));
        }
    }

    private void performAdjustment() {
        // Validate product selected
        if (selectedProduct == null) {
            Toast.makeText(this, "Please select a product", Toast.LENGTH_SHORT).show();
            return;
        }

        // Validate quantity input
        String quantityStr = etQuantity.getText().toString().trim();
        if (quantityStr.isEmpty()) {
            etQuantity.setError("Quantity required");
            return;
        }

        try {
            int adjustmentQty = Integer.parseInt(quantityStr);
            if (adjustmentQty <= 0) {
                etQuantity.setError("Quantity must be greater than 0");
                return;
            }

            String adjustmentType = spinnerAdjustmentType.getSelectedItem().toString();
            String reason = spinnerReason.getSelectedItem().toString();
            String remarks = etRemarks.getText().toString().trim();

            int currentStock = selectedProduct.getQuantity();

            // Calculate new stock
            int newStock;
            if ("Add Stock".equals(adjustmentType)) {
                newStock = currentStock + adjustmentQty;
            } else {
                // Remove Stock
                newStock = currentStock - adjustmentQty;
            }

            // Cap at max stock value
            newStock = Math.min(newStock, MAX_STOCK);

            if (newStock < FLOOR_LEVEL) {
                int finalCurrentStock = currentStock;
                int finalNewStock = newStock;
                int finalAdjustmentQty = adjustmentQty;
                String finalAdjustmentType = adjustmentType;
                String finalReason = reason;
                String finalRemarks = remarks;

                new AlertDialog.Builder(this)
                        .setTitle("Warning - Stock Below Floor Level")
                        .setMessage("This adjustment will result in stock (" + finalNewStock + ") below the minimum floor level (" + FLOOR_LEVEL + ").\n\nDo you want to continue?")
                        .setPositiveButton("Continue", (dialog, which) ->
                                saveAdjustment(finalAdjustmentType, finalAdjustmentQty, finalCurrentStock, finalNewStock, finalReason, finalRemarks))
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                saveAdjustment(adjustmentType, adjustmentQty, currentStock, newStock, reason, remarks);
            }
        } catch (NumberFormatException e) {
            etQuantity.setError("Please enter a valid number");
        }
    }

    private void saveAdjustment(String adjustmentType, int adjustmentQty, int currentStock, int newStock, String reason, String remarks) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        String userId = currentUser.getUid();
        com.google.firebase.database.DatabaseReference ref = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("StockAdjustments");
        String adjustmentId = ref.push().getKey();

        if (adjustmentId == null) {
            Toast.makeText(this, "Error generating adjustment ID", Toast.LENGTH_SHORT).show();
            return;
        }

        // Ensure final stock is within bounds
        int finalNewStock = Math.max(FLOOR_LEVEL, Math.min(newStock, MAX_STOCK));

        // Create StockAdjustment record
        StockAdjustment adjustment = new StockAdjustment(
                adjustmentId,
                selectedProduct.getProductId(),
                selectedProduct.getProductName(),
                adjustmentType,
                currentStock,
                adjustmentQty,
                finalNewStock,
                reason,
                remarks,
                System.currentTimeMillis(),
                userId
        );

        // IMPORTANT: Update the Inventory quantity FIRST to guarantee it reflects on the dashboard
        productRepository.updateProductQuantity(selectedProduct.getProductId(), finalNewStock, new ProductRepository.OnProductUpdatedListener() {
            @Override
            public void onProductUpdated() {
                // Stock successfully updated in Inventory! Now save the history log.
                ref.child(adjustmentId).setValue(adjustment)
                        .addOnSuccessListener(aVoid -> {
                            runOnUiThread(() -> {
                                Toast.makeText(StockAdjustmentActivity.this, "Stock adjusted successfully", Toast.LENGTH_SHORT).show();
                                clearForm();
                            });
                        })
                        .addOnFailureListener(e -> runOnUiThread(() ->
                                Toast.makeText(StockAdjustmentActivity.this, "Stock updated, but history log failed: " + e.getMessage(), Toast.LENGTH_SHORT).show()
                        ));
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(StockAdjustmentActivity.this, "Error updating inventory: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void clearForm() {
        etQuantity.setText("");
        etRemarks.setText("");
        spinnerProduct.setSelection(0);
        spinnerAdjustmentType.setSelection(0);
        spinnerReason.setSelection(0);
        tvCurrentStock.setText("0");
        tvNewStock.setText("0");
        selectedProduct = null;
    }
}