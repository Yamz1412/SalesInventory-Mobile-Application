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
        String[] adjustmentTypes = {"Add Stock", "Remove Stock"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, adjustmentTypes);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAdjustmentType.setAdapter(typeAdapter);

        String[] reasons = {
                "Purchase/Receiving",
                "Sales Return",
                "Damaged Product",
                "Expired Product",
                "Lost/Stolen",
                "Inventory Count Correction",
                "Other"
        };
        ArrayAdapter<String> reasonAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, reasons);
        reasonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReason.setAdapter(reasonAdapter);
    }

    private void loadProducts() {
        productList = new ArrayList<>();
        List<String> productNames = new ArrayList<>();
        productNames.add("Select Product");

        productRepository.getAllProducts().observe(this, products -> {
            productList.clear();
            if (products != null) {
                for (Product p : products) {
                    if (p != null && p.isActive()) {
                        productList.add(p);
                        productNames.add(p.getProductName());
                    }
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
            return;
        }

        String quantityStr = etQuantity.getText().toString().trim();
        if (quantityStr.isEmpty()) {
            tvNewStock.setText("0");
            return;
        }

        try {
            int currentStock = selectedProduct.getQuantity();
            int adjustmentQty = Integer.parseInt(quantityStr);
            String adjustmentType = spinnerAdjustmentType.getSelectedItem().toString();

            int newStock = adjustmentType.equals("Add Stock")
                    ? currentStock + adjustmentQty
                    : currentStock - adjustmentQty;

            tvNewStock.setText(String.valueOf(Math.max(0, newStock)));

            if (newStock < 0) {
                tvNewStock.setTextColor(getResources().getColor(R.color.errorRed));
            } else {
                tvNewStock.setTextColor(getResources().getColor(R.color.successGreen));
            }
        } catch (NumberFormatException e) {
            tvNewStock.setText("0");
        }
    }

    private void performAdjustment() {
        if (selectedProduct == null) {
            Toast.makeText(this, "Please select a product", Toast.LENGTH_SHORT).show();
            return;
        }

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
            int newStock = adjustmentType.equals("Add Stock")
                    ? currentStock + adjustmentQty
                    : currentStock - adjustmentQty;

            int floor = Math.max(1, selectedProduct.getFloorLevel());
            int ceiling = selectedProduct.getCeilingLevel() <= 0 ? Math.max(selectedProduct.getQuantity(), Math.max(selectedProduct.getReorderLevel() * 2, 100)) : selectedProduct.getCeilingLevel();
            if (ceiling > 9999) ceiling = 9999;

            if (adjustmentType.equals("Add Stock") && newStock > ceiling) {
                int allowed = ceiling - currentStock;
                if (allowed <= 0) {
                    Toast.makeText(this, "Cannot add stock: product already at or above ceiling", Toast.LENGTH_LONG).show();
                    return;
                } else {
                    int finalAllowed = allowed;
                    int finalCurrentStock = currentStock;
                    String finalAdjustmentType = adjustmentType;
                    String finalReason = reason;
                    String finalRemarks = remarks;
                    new AlertDialog.Builder(this)
                            .setTitle("Limit reached")
                            .setMessage("Adding " + adjustmentQty + " would exceed ceiling. You can add up to " + finalAllowed + " units. Add allowed amount?")
                            .setPositiveButton("Add " + finalAllowed, (dialog, which) -> saveAdjustment(finalAdjustmentType, finalAllowed, finalCurrentStock, finalCurrentStock + finalAllowed, finalReason, finalRemarks))
                            .setNegativeButton("Cancel", null)
                            .show();
                    return;
                }
            }

            if (newStock < floor) {
                int finalCurrentStock = currentStock;
                int finalNewStock = Math.max(floor, newStock);
                int finalAdjustmentQty = adjustmentQty;
                String finalAdjustmentType = adjustmentType;
                String finalReason = reason;
                String finalRemarks = remarks;
                new AlertDialog.Builder(this)
                        .setTitle("Warning")
                        .setMessage("This will result in stock below floor (" + floor + "). Continue?")
                        .setPositiveButton("Continue", (dialog, which) ->
                                saveAdjustment(finalAdjustmentType, finalAdjustmentQty, finalCurrentStock, finalNewStock, finalReason, finalRemarks))
                        .setNegativeButton("Cancel", null)
                        .show();
            } else {
                int boundedNewStock = Math.min(newStock, 9999);
                saveAdjustment(adjustmentType, adjustmentQty, currentStock, boundedNewStock, reason, remarks);
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

        String adjustmentId = com.google.firebase.database.FirebaseDatabase.getInstance().getReference().push().getKey();
        String userId = currentUser.getUid();

        if (adjustmentId == null) {
            Toast.makeText(this, "Error generating adjustment ID", Toast.LENGTH_SHORT).show();
            return;
        }

        int finalNewStock = Math.max(1, Math.min(newStock, 9999));

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

        Map<String, Object> updates = new HashMap<>();
        updates.put("/StockAdjustments/" + adjustmentId, adjustment);

        com.google.firebase.database.FirebaseDatabase.getInstance().getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    productRepository.updateProductQuantity(selectedProduct.getProductId(), finalNewStock, new ProductRepository.OnProductUpdatedListener() {
                        @Override
                        public void onProductUpdated() {
                            runOnUiThread(() -> {
                                Toast.makeText(StockAdjustmentActivity.this, "Stock adjusted successfully", Toast.LENGTH_SHORT).show();
                                clearForm();
                            });
                        }
                        @Override
                        public void onError(String error) {
                            runOnUiThread(() -> Toast.makeText(StockAdjustmentActivity.this, "Error updating product: " + error, Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
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