package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class StockAdjustmentActivity extends BaseActivity {

    private Spinner spinnerProduct, spinnerAdjustmentType, spinnerReason;
    private EditText etQuantity, etRemarks;
    private TextView tvCurrentStock, tvNewStock, tvFinancialImpact;
    private Button btnAdjust, btnViewHistory;

    private List<Product> productList;
    private Product selectedProduct;
    private ProductRepository productRepository;

    private static final double FLOOR_LEVEL = 0.0;
    private static final double MAX_STOCK = 99999.0;

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
        tvFinancialImpact = findViewById(R.id.tvFinancialImpact);
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
                    String unit = selectedProduct.getUnit() == null || selectedProduct.getUnit().isEmpty() ? "Units" : selectedProduct.getUnit();
                    tvCurrentStock.setText(String.format(Locale.getDefault(), "%.2f %s", selectedProduct.getQuantity(), unit));
                    calculateNewStock();
                } else {
                    selectedProduct = null;
                    tvCurrentStock.setText("0.0");
                    tvFinancialImpact.setText("₱0.00");
                    calculateNewStock();
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        etQuantity.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { calculateNewStock(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        spinnerAdjustmentType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { calculateNewStock(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        btnAdjust.setOnClickListener(v -> performAdjustment());

        // FIXED: Changed the History button to function as a Cancel button
        btnViewHistory.setOnClickListener(v -> finish());
    }

    private void setupSpinners() {
        String[] adjustmentTypes = {"Add Stock", "Remove Stock"};
        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, adjustmentTypes);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerAdjustmentType.setAdapter(typeAdapter);

        String[] reasons = {
                "Manual Restock / Found Stock",
                "Spoilage / Expired",
                "Damaged in Store",
                "Staff Consumption",
                "Inventory Recount Correction",
                "Promotional Giveaway",
                "Return to Supplier",
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
                List<Product> activeProducts = new ArrayList<>();
                for (Product p : products) {
                    if (p != null && p.isActive() && !"Menu".equalsIgnoreCase(p.getProductType())) {
                        activeProducts.add(p);
                    }
                }

                Collections.sort(activeProducts, (p1, p2) -> {
                    String name1 = p1.getProductName() != null ? p1.getProductName() : "";
                    String name2 = p2.getProductName() != null ? p2.getProductName() : "";
                    return name1.compareToIgnoreCase(name2);
                });

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

    // =========================================================================
    // FIX: Accurate Financial Impact Calculation
    // Calculates EXACT fractional cost instead of treating Total Cost as Unit Cost
    // =========================================================================
    private void calculateNewStock() {
        if (selectedProduct == null) {
            tvNewStock.setText("0.0");
            tvNewStock.setTextColor(getResources().getColor(android.R.color.darker_gray));
            tvFinancialImpact.setText("₱0.00");
            return;
        }

        String quantityStr = etQuantity.getText().toString().trim();
        String unit = selectedProduct.getUnit() == null || selectedProduct.getUnit().isEmpty() ? "Units" : selectedProduct.getUnit();

        if (quantityStr.isEmpty()) {
            tvNewStock.setText(String.format(Locale.getDefault(), "%.2f %s", selectedProduct.getQuantity(), unit));
            tvNewStock.setTextColor(getResources().getColor(android.R.color.darker_gray));
            tvFinancialImpact.setText("₱0.00");
            return;
        }

        try {
            double currentStock = selectedProduct.getQuantity();
            double currentTotalCost = selectedProduct.getCostPrice();

            // Protect against dividing by zero if stock is somehow 0
            double unitCost = currentStock > 0 ? (currentTotalCost / currentStock) : 0.0;

            double adjustmentQty = Double.parseDouble(quantityStr);
            String adjustmentType = spinnerAdjustmentType.getSelectedItem().toString();

            double newStock;
            double valueImpact = adjustmentQty * unitCost; // Exact fractional value!

            if ("Add Stock".equals(adjustmentType)) {
                newStock = currentStock + adjustmentQty;
                tvFinancialImpact.setText(String.format(Locale.getDefault(), "+ ₱%.2f (Value Gained)", valueImpact));
                tvFinancialImpact.setTextColor(getResources().getColor(R.color.successGreen));
            } else {
                newStock = currentStock - adjustmentQty;
                tvFinancialImpact.setText(String.format(Locale.getDefault(), "- ₱%.2f (Value Lost)", valueImpact));
                tvFinancialImpact.setTextColor(getResources().getColor(R.color.errorRed));
            }

            tvNewStock.setText(String.format(Locale.getDefault(), "%.2f %s", newStock, unit));

            if (newStock < FLOOR_LEVEL) {
                tvNewStock.setTextColor(getResources().getColor(R.color.errorRed));
            } else {
                tvNewStock.setTextColor(getResources().getColor(R.color.successGreen));
            }
        } catch (NumberFormatException e) {
            tvNewStock.setText(String.format(Locale.getDefault(), "%.2f %s", selectedProduct.getQuantity(), unit));
            tvNewStock.setTextColor(getResources().getColor(android.R.color.darker_gray));
            tvFinancialImpact.setText("₱0.00");
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
            double adjustmentQty = Double.parseDouble(quantityStr);
            if (adjustmentQty <= 0) {
                etQuantity.setError("Quantity must be greater than 0");
                return;
            }

            String adjustmentType = spinnerAdjustmentType.getSelectedItem().toString();
            String reason = spinnerReason.getSelectedItem().toString();
            String remarks = etRemarks.getText().toString().trim();

            double currentStock = selectedProduct.getQuantity();
            double newStock;

            if ("Add Stock".equals(adjustmentType)) {
                newStock = currentStock + adjustmentQty;
            } else {
                newStock = currentStock - adjustmentQty;
            }

            newStock = Math.min(newStock, MAX_STOCK);

            if (newStock < FLOOR_LEVEL) {
                new AlertDialog.Builder(this)
                        .setTitle("Invalid Adjustment")
                        .setMessage("You cannot remove " + adjustmentQty + " items. You only have " + currentStock + " in stock.")
                        .setPositiveButton("OK", null)
                        .show();
            } else {
                saveAdjustment(adjustmentType, adjustmentQty, currentStock, newStock, reason, remarks);
            }
        } catch (NumberFormatException e) {
            etQuantity.setError("Please enter a valid number");
        }
    }

    // =========================================================================
    // UPGRADE: Perfectly Syncs Quantity, Total Value, and History
    // =========================================================================
    private void saveAdjustment(String adjustmentType, double adjustmentQty, double currentStock, double newStock, String reason, String remarks) {
        String userId = AuthManager.getInstance().getCurrentUserId();
        if (userId == null || userId.isEmpty()) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show();
            return;
        }

        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("StockAdjustments");
        String adjustmentId = ref.push().getKey();

        if (adjustmentId == null) {
            Toast.makeText(this, "Error generating adjustment ID", Toast.LENGTH_SHORT).show();
            return;
        }

        // Calculate the Exact New Total Cost
        double currentTotalCost = selectedProduct.getCostPrice();
        double unitCost = currentStock > 0 ? (currentTotalCost / currentStock) : 0.0;
        double valueImpact = adjustmentQty * unitCost;

        double newTotalCost;
        if ("Add Stock".equals(adjustmentType)) {
            newTotalCost = currentTotalCost + valueImpact;
        } else {
            newTotalCost = Math.max(0.0, currentTotalCost - valueImpact);
        }

        StockAdjustment adjustment = new StockAdjustment(
                adjustmentId,
                selectedProduct.getProductId(),
                selectedProduct.getProductName(),
                adjustmentType,
                currentStock,
                adjustmentQty,
                newStock,
                reason,
                remarks,
                System.currentTimeMillis(),
                userId
        );

        // Update Database directly using our super-method!
        productRepository.updateProductQuantityAndCost(
                selectedProduct.getProductId(),
                newStock,
                newTotalCost,
                new ProductRepository.OnProductUpdatedListener() {
                    @Override
                    public void onProductUpdated() {

                        // Handle Offline Room Database Batches securely in the background
                        new Thread(() -> {
                            AppDatabase db = AppDatabase.getInstance(StockAdjustmentActivity.this);
                            if ("Add Stock".equals(adjustmentType)) {
                                BatchEntity newBatch = new BatchEntity();
                                newBatch.productId = selectedProduct.getProductId();
                                newBatch.initialQuantity = adjustmentQty;
                                newBatch.remainingQuantity = adjustmentQty;
                                newBatch.receiveDate = System.currentTimeMillis();
                                newBatch.expiryDate = System.currentTimeMillis() + (14L * 24 * 60 * 60 * 1000);
                                newBatch.costPrice = unitCost;
                                db.batchDao().insertBatch(newBatch);
                            } else {
                                // Manual FIFO deduction for batches
                                List<BatchEntity> batches = db.batchDao().getAvailableBatchesFIFO(selectedProduct.getProductId());
                                double remainingToDeduct = adjustmentQty;
                                for (BatchEntity batch : batches) {
                                    if (remainingToDeduct <= 0) break;
                                    if (batch.remainingQuantity <= remainingToDeduct) {
                                        remainingToDeduct -= batch.remainingQuantity;
                                        batch.remainingQuantity = 0;
                                    } else {
                                        batch.remainingQuantity -= remainingToDeduct;
                                        remainingToDeduct = 0;
                                    }
                                    db.batchDao().updateBatch(batch);
                                }
                            }
                        }).start();

                        // Log History to Firebase
                        ref.child(adjustmentId).setValue(adjustment)
                                .addOnSuccessListener(aVoid -> {
                                    runOnUiThread(() -> {
                                        Toast.makeText(StockAdjustmentActivity.this, "Stock & Financials Updated Successfully", Toast.LENGTH_SHORT).show();
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
                }
        );
    }

    private void clearForm() {
        etQuantity.setText("");
        etRemarks.setText("");
        spinnerProduct.setSelection(0);
        spinnerAdjustmentType.setSelection(0);
        spinnerReason.setSelection(0);
        tvCurrentStock.setText("0.0");
        tvNewStock.setText("0.0");
        tvFinancialImpact.setText("₱0.00");
        selectedProduct = null;
    }
}