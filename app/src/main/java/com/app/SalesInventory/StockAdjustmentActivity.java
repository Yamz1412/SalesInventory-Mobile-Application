package com.app.SalesInventory;

import androidx.appcompat.app.AlertDialog;
import androidx.lifecycle.Observer;
import android.content.Intent;
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
import java.util.Collections;
import java.util.Comparator;

public class StockAdjustmentActivity extends BaseActivity {

    private Spinner spinnerProduct;
    private Spinner spinnerAdjustmentType;
    private Spinner spinnerReason;
    private EditText etQuantity;
    private EditText etRemarks;
    private TextView tvCurrentStock;
    private TextView tvNewStock;
    private Button btnAdjust;
    private Button btnViewHistory;

    private List<Product> productList;
    private Product selectedProduct;
    private ProductRepository productRepository;
    private ArrayAdapter<String> productAdapter;
    private List<String> productNames;
    private static final int MAX_QUANTITY = 10000;

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
        initProductSpinnerAdapter();
        observeInventoryProducts();

        spinnerProduct.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position > 0 && productList != null && position - 1 < productList.size()) {
                    selectedProduct = productList.get(position - 1);
                    tvCurrentStock.setText(String.valueOf(selectedProduct.getQuantity()));
                } else {
                    selectedProduct = null;
                    tvCurrentStock.setText("0");
                }
                calculateNewStock();
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        spinnerAdjustmentType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { calculateNewStock(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });

        etQuantity.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { calculateNewStock(); }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        btnAdjust.setOnClickListener(v -> performAdjustment());
        btnViewHistory.setOnClickListener(v -> startActivity(new Intent(StockAdjustmentActivity.this, AdjustmentHistoryActivity.class)));
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
                "Low Stock",
                "Expired Product",
                "Lost/Stolen",
                "Inventory Count Correction",
                "Other"
        };
        ArrayAdapter<String> reasonAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, reasons);
        reasonAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerReason.setAdapter(reasonAdapter);
    }

    private void initProductSpinnerAdapter() {
        productList = new ArrayList<>();
        productNames = new ArrayList<>();
        productNames.add("Select Product");
        productAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new ArrayList<String>());
        productAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProduct.setAdapter(productAdapter);
        spinnerProduct.setEnabled(false);
    }

    private void observeInventoryProducts() {
        productRepository.getInventoryProducts().observe(this, new Observer<List<Product>>() {
            @Override
            public void onChanged(List<Product> products) {
                Map<String, Product> unique = new HashMap<>();
                if (products != null) {
                    for (Product p : products) {
                        if (p == null) continue;
                        if (!p.isActive()) continue;
                        String rawId = p.getProductId();
                        String pid = (rawId == null || rawId.trim().isEmpty()) ? String.valueOf(p.getLocalId()) : rawId;
                        unique.put(pid, p);
                    }
                }

                List<Product> sorted = new ArrayList<>(unique.values());
                Collections.sort(sorted, new Comparator<Product>() {
                    @Override
                    public int compare(Product a, Product b) {
                        String an = a.getProductName() == null ? "" : a.getProductName().toLowerCase();
                        String bn = b.getProductName() == null ? "" : b.getProductName().toLowerCase();
                        return an.compareTo(bn);
                    }
                });

                productList.clear();
                productNames.clear();
                productNames.add("Select Product");
                for (Product p : sorted) {
                    productList.add(p);
                    productNames.add(p.getProductName() == null ? "(no name)" : p.getProductName());
                }

                productAdapter.clear();
                productAdapter.addAll(productNames);
                productAdapter.notifyDataSetChanged();
                spinnerProduct.setEnabled(productNames.size() > 1);

                if (selectedProduct != null) {
                    String selRaw = selectedProduct.getProductId();
                    String selKey = (selRaw == null || selRaw.trim().isEmpty()) ? String.valueOf(selectedProduct.getLocalId()) : selRaw;
                    int idx = -1;
                    for (int i = 0; i < productList.size(); i++) {
                        Product p = productList.get(i);
                        String raw = p.getProductId();
                        String pid = (raw == null || raw.trim().isEmpty()) ? String.valueOf(p.getLocalId()) : raw;
                        if (pid.equals(selKey)) { idx = i + 1; break; }
                    }
                    if (idx >= 1 && idx < productAdapter.getCount()) {
                        final int selIndex = idx;
                        spinnerProduct.post(() -> spinnerProduct.setSelection(selIndex, false));
                    } else {
                        selectedProduct = null;
                        tvCurrentStock.setText("0");
                        spinnerProduct.post(() -> spinnerProduct.setSelection(0, false));
                    }
                } else {
                    spinnerProduct.post(() -> spinnerProduct.setSelection(0, false));
                }
            }
        });
    }

    private void calculateNewStock() {
        if (selectedProduct == null) {
            tvNewStock.setText("0");
            tvNewStock.setTextColor(getResources().getColor(R.color.successGreen));
            return;
        }
        String quantityStr = etQuantity.getText().toString().trim();
        if (quantityStr.isEmpty()) {
            tvNewStock.setText(String.valueOf(selectedProduct.getQuantity()));
            tvNewStock.setTextColor(getResources().getColor(R.color.successGreen));
            return;
        }
        try {
            int currentStock = selectedProduct.getQuantity();
            int adjustmentQty = Integer.parseInt(quantityStr);
            if (adjustmentQty < 0) { tvNewStock.setText("0"); return; }
            if (adjustmentQty > MAX_QUANTITY) adjustmentQty = MAX_QUANTITY;
            String adjustmentType = spinnerAdjustmentType.getSelectedItem().toString();
            long newStockLong = adjustmentType.equals("Add Stock") ? (long) currentStock + adjustmentQty : (long) currentStock - adjustmentQty;
            if (newStockLong > MAX_QUANTITY) newStockLong = MAX_QUANTITY;
            int newStock = (int) Math.max(0, Math.min(newStockLong, Integer.MAX_VALUE));
            tvNewStock.setText(String.valueOf(newStock));
            if (newStockLong < 0) tvNewStock.setTextColor(getResources().getColor(R.color.errorRed)); else tvNewStock.setTextColor(getResources().getColor(R.color.successGreen));
        } catch (NumberFormatException e) {
            tvNewStock.setText("0");
            tvNewStock.setTextColor(getResources().getColor(R.color.errorRed));
        }
    }

    private void performAdjustment() {
        if (selectedProduct == null) { Toast.makeText(this, "Please select a product", Toast.LENGTH_SHORT).show(); return; }
        String quantityStr = etQuantity.getText().toString().trim();
        if (quantityStr.isEmpty()) { etQuantity.setError("Quantity required"); return; }
        try {
            int adjustmentQty = Integer.parseInt(quantityStr);
            if (adjustmentQty <= 0) { etQuantity.setError("Quantity must be greater than 0"); return; }
            if (adjustmentQty > MAX_QUANTITY) adjustmentQty = MAX_QUANTITY;
            String adjustmentType = spinnerAdjustmentType.getSelectedItem().toString();
            String reason = spinnerReason.getSelectedItem().toString();
            String remarks = etRemarks.getText().toString().trim();
            int currentStock = selectedProduct.getQuantity();
            long tentativeNewStockLong = adjustmentType.equals("Add Stock") ? (long) currentStock + adjustmentQty : (long) currentStock - adjustmentQty;
            if (tentativeNewStockLong < 0) { Toast.makeText(this, "Resulting stock cannot be negative", Toast.LENGTH_LONG).show(); return; }
            int newStock = (int) Math.max(0, Math.min(tentativeNewStockLong, MAX_QUANTITY));
            saveAdjustment(adjustmentType, adjustmentQty, currentStock, newStock, reason, remarks);
        } catch (NumberFormatException e) { etQuantity.setError("Please enter a valid number"); }
    }

    private void navigateBackToInventoryWithExtras(boolean deleted, String productId, long localId, int updatedQuantity) {
        Intent intent = new Intent(StockAdjustmentActivity.this, Inventory.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (productId != null && !productId.trim().isEmpty()) intent.putExtra("updatedProductId", productId);
        else intent.putExtra("updatedLocalId", localId);
        intent.putExtra("updatedQuantity", updatedQuantity);
        intent.putExtra("deleted", deleted);
        startActivity(intent);
        finish();
    }

    private void saveAdjustment(String adjustmentType, int adjustmentQty, int currentStock, int newStock, String reason, String remarks) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) { Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show(); return; }
        String adjustmentId = com.google.firebase.database.FirebaseDatabase.getInstance().getReference().push().getKey();
        String userId = currentUser.getUid();
        if (adjustmentId == null) { Toast.makeText(this, "Error generating adjustment ID", Toast.LENGTH_SHORT).show(); return; }
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
        Map<String, Object> updates = new HashMap<>();
        updates.put("/StockAdjustments/" + adjustmentId, adjustment);
        Map<String, Object> movement = new HashMap<>();
        movement.put("movementId", adjustmentId);
        movement.put("productId", selectedProduct.getProductId());
        movement.put("productName", selectedProduct.getProductName());
        movement.put("change", adjustmentType.equals("Add Stock") ? adjustmentQty : -adjustmentQty);
        movement.put("quantityBefore", currentStock);
        movement.put("quantityAfter", newStock);
        movement.put("reason", reason);
        movement.put("remarks", remarks);
        movement.put("type", adjustmentType);
        movement.put("timestamp", System.currentTimeMillis());
        movement.put("performedBy", userId);
        updates.put("/InventoryMovements/" + adjustmentId, movement);
        com.google.firebase.database.FirebaseDatabase.getInstance().getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    String pid = selectedProduct.getProductId();
                    boolean hasPid = pid != null && !pid.trim().isEmpty();
                    if ("Remove Stock".equalsIgnoreCase(adjustmentType) && newStock <= 0) {
                        if (hasPid) {
                            productRepository.deleteProduct(pid, new ProductRepository.OnProductDeletedListener() {
                                @Override public void onProductDeleted(String archiveFilename) {
                                    runOnUiThread(() -> navigateBackToInventoryWithExtras(true, pid, selectedProduct.getLocalId(), newStock));
                                }
                                @Override public void onError(String error) {
                                    if (error != null && error.toLowerCase().contains("unauthorized")) {
                                        productRepository.updateProductQuantityIgnoreCeiling(pid, 0, new ProductRepository.OnProductUpdatedListener() {
                                            @Override public void onProductUpdated() {
                                                runOnUiThread(() -> navigateBackToInventoryWithExtras(false, pid, selectedProduct.getLocalId(), 0));
                                            }
                                            @Override public void onError(String err) {
                                                runOnUiThread(() -> navigateBackToInventoryWithExtras(false, pid, selectedProduct.getLocalId(), 0));
                                            }
                                        });
                                    } else {
                                        runOnUiThread(() -> navigateBackToInventoryWithExtras(true, pid, selectedProduct.getLocalId(), newStock));
                                    }
                                }
                            });
                        } else {
                            long localId = selectedProduct.getLocalId();
                            productRepository.deleteProductByLocalId(localId, new ProductRepository.OnProductDeletedListener() {
                                @Override public void onProductDeleted(String archiveFilename) {
                                    runOnUiThread(() -> navigateBackToInventoryWithExtras(true, null, localId, newStock));
                                }
                                @Override public void onError(String error) {
                                    if (error != null && error.toLowerCase().contains("unauthorized")) {
                                        productRepository.updateProductQuantityByLocalIdIgnoreCeiling(localId, 0, new ProductRepository.OnProductUpdatedListener() {
                                            @Override public void onProductUpdated() {
                                                runOnUiThread(() -> navigateBackToInventoryWithExtras(false, null, localId, 0));
                                            }
                                            @Override public void onError(String err) {
                                                runOnUiThread(() -> navigateBackToInventoryWithExtras(false, null, localId, 0));
                                            }
                                        });
                                    } else {
                                        runOnUiThread(() -> navigateBackToInventoryWithExtras(true, null, localId, newStock));
                                    }
                                }
                            });
                        }
                    } else {
                        if (hasPid) {
                            if ("Add Stock".equalsIgnoreCase(adjustmentType)) {
                                productRepository.updateProductQuantityIgnoreCeiling(pid, newStock, new ProductRepository.OnProductUpdatedListener() {
                                    @Override public void onProductUpdated() {
                                        runOnUiThread(() -> navigateBackToInventoryWithExtras(false, pid, selectedProduct.getLocalId(), newStock));
                                    }
                                    @Override public void onError(String error) {
                                        runOnUiThread(() -> navigateBackToInventoryWithExtras(false, pid, selectedProduct.getLocalId(), newStock));
                                    }
                                });
                            } else {
                                productRepository.updateProductQuantity(pid, newStock, new ProductRepository.OnProductUpdatedListener() {
                                    @Override public void onProductUpdated() {
                                        runOnUiThread(() -> navigateBackToInventoryWithExtras(false, pid, selectedProduct.getLocalId(), newStock));
                                    }
                                    @Override public void onError(String error) {
                                        runOnUiThread(() -> navigateBackToInventoryWithExtras(false, pid, selectedProduct.getLocalId(), newStock));
                                    }
                                });
                            }
                        } else {
                            long localId = selectedProduct.getLocalId();
                            if ("Add Stock".equalsIgnoreCase(adjustmentType)) {
                                productRepository.updateProductQuantityByLocalIdIgnoreCeiling(localId, newStock, new ProductRepository.OnProductUpdatedListener() {
                                    @Override public void onProductUpdated() {
                                        runOnUiThread(() -> navigateBackToInventoryWithExtras(false, null, localId, newStock));
                                    }
                                    @Override public void onError(String error) {
                                        runOnUiThread(() -> navigateBackToInventoryWithExtras(false, null, localId, newStock));
                                    }
                                });
                            } else {
                                productRepository.updateProductQuantityByLocalId(localId, newStock, new ProductRepository.OnProductUpdatedListener() {
                                    @Override public void onProductUpdated() {
                                        runOnUiThread(() -> navigateBackToInventoryWithExtras(false, null, localId, newStock));
                                    }
                                    @Override public void onError(String error) {
                                        runOnUiThread(() -> navigateBackToInventoryWithExtras(false, null, localId, newStock));
                                    }
                                });
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> runOnUiThread(() -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show()));
    }
}