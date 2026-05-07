package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class StockAdjustmentActivity extends BaseActivity {

    private Spinner spinnerProduct, spinnerAdjustmentType, spinnerReason;
    private EditText etQuantity, etRemarks;
    private TextView tvCurrentStock, tvNewStock, tvFinancialImpact;
    private Button btnAdjust, btnCancel;

    private List<Product> productList;
    private Product selectedProduct;
    private ProductRepository productRepository;

    private static final double FLOOR_LEVEL = 0.0;
    private static final double MAX_STOCK = 99999.0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_adjustment);

        // RESTORED ORIGINAL IDs
        spinnerProduct = findViewById(R.id.spinnerProduct);
        spinnerAdjustmentType = findViewById(R.id.spinnerAdjustmentType);
        spinnerReason = findViewById(R.id.spinnerReason);
        etQuantity = findViewById(R.id.etQuantity);
        etRemarks = findViewById(R.id.etRemarks);
        tvCurrentStock = findViewById(R.id.tvCurrentStock);
        tvNewStock = findViewById(R.id.tvNewStock);
        tvFinancialImpact = findViewById(R.id.tvFinancialImpact);
        btnAdjust = findViewById(R.id.btnAdjust);
        btnCancel = findViewById(R.id.btnCancel);

        productRepository = SalesInventoryApplication.getProductRepository();
        productList = new ArrayList<>();

        setupSpinners();
        loadProducts();

        etQuantity.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) { calculateImpact(); }
        });

        btnAdjust.setOnClickListener(v -> performAdjustment());
        btnCancel.setOnClickListener(v -> finish());
    }

    // Adaptive Spinner to prevent invisible text in Dark Mode
    private ArrayAdapter<String> getAdaptiveAdapter(List<String> items) {
        boolean isDark = false;
        try { isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark"); } catch (Exception e) {}
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ((TextView) view).setTextColor(textColor);
                return view;
            }
            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                ((TextView) view).setTextColor(textColor);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void setupSpinners() {
        List<String> types = Arrays.asList("Add Stock (+)", "Remove Stock (-)");
        spinnerAdjustmentType.setAdapter(getAdaptiveAdapter(types));

        spinnerAdjustmentType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateReasonSpinner(position == 0);
                calculateImpact();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        spinnerProduct.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (productList == null || productList.isEmpty()) {
                    selectedProduct = null;
                    tvCurrentStock.setText("0.0");
                    calculateImpact();
                    return;
                }

                selectedProduct = productList.get(position);
                tvCurrentStock.setText(String.valueOf(selectedProduct.getQuantity()));
                calculateImpact();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void updateReasonSpinner(boolean isAddition) {
        List<String> reasons;
        if (isAddition) {
            reasons = Arrays.asList("New Stock Discovered", "Return from Customer", "Inventory Recount", "Other (Specify in Remarks)");
        } else {
            reasons = Arrays.asList("Damaged", "Expired", "Lost/Stolen", "Inventory Recount", "Other (Specify in Remarks)");
        }
        spinnerReason.setAdapter(getAdaptiveAdapter(reasons));
    }

    private void loadProducts() {
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                productList.clear();
                List<String> productNames = new ArrayList<>();
                for (Product p : products) {
                    if (p.isActive() && !"Menu".equalsIgnoreCase(p.getProductType())) {
                        productList.add(p);
                        productNames.add(p.getProductName());
                    }
                }

                if (!productNames.isEmpty()) {
                    spinnerProduct.setAdapter(getAdaptiveAdapter(productNames));
                    selectedProduct = productList.get(0);
                    tvCurrentStock.setText(String.valueOf(selectedProduct.getQuantity()));
                } else {
                    spinnerProduct.setAdapter(getAdaptiveAdapter(Arrays.asList("No Products Available")));
                    selectedProduct = null;
                }
            }
        });
    }

    private void calculateImpact() {
        if (selectedProduct == null) return;

        String qtyStr = etQuantity.getText().toString().trim();
        if (qtyStr.isEmpty()) {
            tvNewStock.setText(String.valueOf(selectedProduct.getQuantity()));
            tvFinancialImpact.setText("₱0.00");
            tvFinancialImpact.setTextColor(getResources().getColor(R.color.textColorSecondary));
            return;
        }

        try {
            double currentStock = selectedProduct.getQuantity();
            double adjustmentQty = Double.parseDouble(qtyStr);
            boolean isAddition = spinnerAdjustmentType.getSelectedItemPosition() == 0;

            double newStock = isAddition ? (currentStock + adjustmentQty) : (currentStock - adjustmentQty);
            tvNewStock.setText(String.format(Locale.US, "%.2f", newStock));

            // CRITICAL FIX: Prevent the bulk multiplication bug by using TrueUnitCost!
            int ppu = selectedProduct.getPiecesPerUnit() > 0 ? selectedProduct.getPiecesPerUnit() : 1;
            double trueUnitCost = UnitConverterUtil.calculateTrueUnitCost(selectedProduct.getCostPrice(), selectedProduct.getUnit(), ppu);

            double impact = adjustmentQty * trueUnitCost;

            if (isAddition) {
                tvFinancialImpact.setText(String.format(Locale.US, "+₱%,.2f", impact));
                tvFinancialImpact.setTextColor(getResources().getColor(R.color.successGreen));
            } else {
                tvFinancialImpact.setText(String.format(Locale.US, "-₱%,.2f", impact));
                tvFinancialImpact.setTextColor(getResources().getColor(R.color.errorRed));
            }

            if (newStock < FLOOR_LEVEL) {
                tvNewStock.setTextColor(getResources().getColor(R.color.errorRed));
            } else {
                tvNewStock.setTextColor(getResources().getColor(R.color.textColorPrimary));
            }

        } catch (NumberFormatException e) {
            tvNewStock.setText("Invalid");
            tvFinancialImpact.setText("₱0.00");
        }
    }

    private void performAdjustment() {
        if (selectedProduct == null) {
            Toast.makeText(this, "Please select a product", Toast.LENGTH_SHORT).show();
            return;
        }

        String qtyStr = etQuantity.getText().toString().trim();
        if (qtyStr.isEmpty()) {
            etQuantity.setError("Required");
            return;
        }

        double adjustmentQty;
        try {
            adjustmentQty = Double.parseDouble(qtyStr);
            if (adjustmentQty <= 0) {
                etQuantity.setError("Must be greater than 0");
                return;
            }
        } catch (NumberFormatException e) {
            etQuantity.setError("Invalid number");
            return;
        }

        boolean isAddition = spinnerAdjustmentType.getSelectedItemPosition() == 0;
        String reason = spinnerReason.getSelectedItem().toString();
        String remarks = etRemarks.getText().toString().trim();

        double currentStock = selectedProduct.getQuantity();
        double newStock = isAddition ? (currentStock + adjustmentQty) : (currentStock - adjustmentQty);

        if (newStock < FLOOR_LEVEL) {
            new AlertDialog.Builder(this)
                    .setTitle("Warning")
                    .setMessage("This deduction will result in negative stock. Are you sure you want to proceed?")
                    .setPositiveButton("Proceed", (dialog, which) -> executeDatabaseUpdate(newStock, adjustmentQty, isAddition, reason, remarks))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else if (newStock > MAX_STOCK) {
            Toast.makeText(this, "Adjustment exceeds maximum allowed stock limit.", Toast.LENGTH_SHORT).show();
        } else {
            executeDatabaseUpdate(newStock, adjustmentQty, isAddition, reason, remarks);
        }
    }

    private void executeDatabaseUpdate(double newStock, double adjustmentQty, boolean isAddition, String reason, String remarks) {
        String productId = selectedProduct.getProductId();

        // CRITICAL FIX: Prevent the bulk multiplication bug!
        int ppu = selectedProduct.getPiecesPerUnit() > 0 ? selectedProduct.getPiecesPerUnit() : 1;
        double trueUnitCost = UnitConverterUtil.calculateTrueUnitCost(selectedProduct.getCostPrice(), selectedProduct.getUnit(), ppu);
        double impact = adjustmentQty * trueUnitCost;

        double currentStock = selectedProduct.getQuantity(); // NEEDED FOR ADAPTER HISTORY

        productRepository.updateProductQuantity(productId, newStock, new ProductRepository.OnProductUpdatedListener() {
                    @Override
                    public void onProductUpdated() {
                        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
                        if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();

                        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("StockAdjustments");
                        String adjustmentId = ref.push().getKey();

                        Map<String, Object> adjustment = new HashMap<>();
                        adjustment.put("id", adjustmentId);
                        adjustment.put("productId", productId);
                        adjustment.put("productName", selectedProduct.getProductName());
                        adjustment.put("productLine", selectedProduct.getProductLine() != null ? selectedProduct.getProductLine() : "Uncategorized");

                        // Ensure consistency so reports can read it easily
                        adjustment.put("type", isAddition ? "ADDITION" : "DEDUCTION");
                        adjustment.put("adjustmentType", isAddition ? "ADDITION" : "DEDUCTION");

                        adjustment.put("quantity", adjustmentQty);
                        adjustment.put("quantityAdjusted", adjustmentQty);
                        adjustment.put("quantityBefore", currentStock);
                        adjustment.put("quantityAfter", newStock);

                        adjustment.put("reason", reason);
                        adjustment.put("remarks", remarks);
                        adjustment.put("timestamp", System.currentTimeMillis());
                        adjustment.put("financialImpact", isAddition ? impact : -impact);
                        adjustment.put("unitCost", trueUnitCost); // Save the corrected cost
                        adjustment.put("ownerAdminId", ownerId);
                        adjustment.put("adjustedBy", AuthManager.getInstance().getCurrentUserId());

                        if (adjustmentId != null) {
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
        spinnerAdjustmentType.setSelection(0);
        spinnerReason.setSelection(0);

        if (spinnerProduct.getAdapter() != null && spinnerProduct.getAdapter().getCount() > 0) {
            spinnerProduct.setSelection(0);
            if (productList != null && !productList.isEmpty()) {
                selectedProduct = productList.get(0);
                tvCurrentStock.setText(String.valueOf(selectedProduct.getQuantity()));
            } else {
                tvCurrentStock.setText("0.0");
            }
        } else {
            tvCurrentStock.setText("0.0");
        }

        tvNewStock.setText("0.0");
        tvFinancialImpact.setText("₱0.00");
        tvFinancialImpact.setTextColor(getResources().getColor(R.color.textColorSecondary));
    }}