package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BatchStockOperationActivity extends AppCompatActivity {

    private Spinner spinnerOperationType;
    private EditText etOperationName, etQuantity, etRemarks;
    private TextView tvSelectedProducts, tvProductCount;
    private Button btnSelectProducts, btnExecute, btnClear;
    private RecyclerView recyclerViewSelected;
    private ProgressBar progressBar;
    private LinearLayout llProductList;

    private DatabaseReference productRef, batchOpRef;
    private List<Product> allProducts;
    private List<Product> selectedProducts;
    private BatchOperationAdapter adapter;
    private FirebaseAuth fAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_batch_operations);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Batch Stock Operations");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initializeViews();
        setupRecyclerView();
        setupSpinners();
        loadAllProducts();

        btnSelectProducts.setOnClickListener(v -> showProductSelectionDialog());
        btnExecute.setOnClickListener(v -> performBatchOperation());
        btnClear.setOnClickListener(v -> clearForm());
    }

    private void initializeViews() {
        spinnerOperationType = findViewById(R.id.spinnerOperationType);
        etOperationName = findViewById(R.id.etOperationName);
        etQuantity = findViewById(R.id.etQuantity);
        etRemarks = findViewById(R.id.etRemarks);
        tvSelectedProducts = findViewById(R.id.tvSelectedProducts);
        tvProductCount = findViewById(R.id.tvProductCount);
        btnSelectProducts = findViewById(R.id.btnSelectProducts);
        btnExecute = findViewById(R.id.btnExecute);
        btnClear = findViewById(R.id.btnClear);
        recyclerViewSelected = findViewById(R.id.recyclerViewSelected);
        progressBar = findViewById(R.id.progressBar);
        llProductList = findViewById(R.id.llProductList);

        allProducts = new ArrayList<>();
        selectedProducts = new ArrayList<>();
        productRef = FirebaseDatabase.getInstance().getReference("Product");
        batchOpRef = FirebaseDatabase.getInstance().getReference("BatchOperations");
        fAuth = FirebaseAuth.getInstance();
    }

    private void setupRecyclerView() {
        adapter = new BatchOperationAdapter(selectedProducts, this, product -> {
            selectedProducts.remove(product);
            updateProductCount();
            adapter.notifyDataSetChanged();
        });
        recyclerViewSelected.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewSelected.setAdapter(adapter);
    }

    private void setupSpinners() {
        String[] operationTypes = {"Add Stock", "Subtract Stock", "Set Stock Level"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, operationTypes);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerOperationType.setAdapter(adapter);
    }

    private void loadAllProducts() {
        progressBar.setVisibility(View.VISIBLE);

        productRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allProducts.clear();

                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    Product product = dataSnapshot.getValue(Product.class);
                    if (product != null && product.isActive()) {
                        allProducts.add(product);
                    }
                }

                progressBar.setVisibility(View.GONE);

                if (allProducts.isEmpty()) {
                    Toast.makeText(BatchStockOperationActivity.this,
                            "No products available", Toast.LENGTH_SHORT).show();
                    btnSelectProducts.setEnabled(false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(BatchStockOperationActivity.this,
                        "Error loading products", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showProductSelectionDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Products");

        // Create a list of product names with checkboxes
        String[] productNames = new String[allProducts.size()];
        boolean[] checkedItems = new boolean[allProducts.size()];

        for (int i = 0; i < allProducts.size(); i++) {
            productNames[i] = allProducts.get(i).getProductName();
            checkedItems[i] = selectedProducts.contains(allProducts.get(i));
        }

        builder.setMultiChoiceItems(productNames, checkedItems, (dialog, which, isChecked) -> {
            Product product = allProducts.get(which);
            if (isChecked) {
                if (!selectedProducts.contains(product)) {
                    selectedProducts.add(product);
                }
            } else {
                selectedProducts.remove(product);
            }
        });

        builder.setPositiveButton("OK", (dialog, which) -> {
            updateProductCount();
            adapter.notifyDataSetChanged();
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void updateProductCount() {
        int count = selectedProducts.size();
        tvProductCount.setText(count + " product" + (count != 1 ? "s" : "") + " selected");

        if (count > 0) {
            llProductList.setVisibility(View.VISIBLE);
        } else {
            llProductList.setVisibility(View.GONE);
        }
    }

    private void performBatchOperation() {
        // Validation
        String operationName = etOperationName.getText().toString().trim();
        if (operationName.isEmpty()) {
            etOperationName.setError("Operation name required");
            return;
        }

        String quantityStr = etQuantity.getText().toString().trim();
        if (quantityStr.isEmpty()) {
            etQuantity.setError("Quantity required");
            return;
        }

        if (selectedProducts.isEmpty()) {
            Toast.makeText(this, "Select at least one product", Toast.LENGTH_SHORT).show();
            return;
        }

        int quantity = Integer.parseInt(quantityStr);
        if (quantity <= 0) {
            etQuantity.setError("Quantity must be greater than 0");
            return;
        }

        String operationType = spinnerOperationType.getSelectedItem().toString();
        String reason = "Batch Operation"; // Can be customized later
        String remarks = etRemarks.getText().toString().trim();

        // Show confirmation dialog
        String operationTypeCode = operationType.contains("Add") ? "ADD" :
                operationType.contains("Subtract") ? "SUBTRACT" : "SET";

        String confirmMessage = "Apply " + operationType + " (" + quantity + " units) to " +
                selectedProducts.size() + " products?\n\nThis action cannot be undone.";

        new AlertDialog.Builder(this)
                .setTitle("Confirm Batch Operation")
                .setMessage(confirmMessage)
                .setPositiveButton("Execute", (dialog, which) ->
                        executeBatchOperation(operationName, operationTypeCode, quantity, reason, remarks))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeBatchOperation(String operationName, String operationType,
                                       int quantity, String reason, String remarks) {
        progressBar.setVisibility(View.VISIBLE);

        String batchOpId = batchOpRef.push().getKey();
        String userId = fAuth.getCurrentUser().getUid();
        long timestamp = System.currentTimeMillis();

        BatchStockOperation batchOp = new BatchStockOperation(
                batchOpId, operationName, operationType, quantity, reason, remarks, timestamp, userId, selectedProducts.size()
        );

        Map<String, Object> updates = new HashMap<>();
        updates.put("/BatchOperations/" + batchOpId, batchOp);

        // Update each product
        for (Product product : selectedProducts) {
            int newQuantity;
            int oldQuantity = product.getQuantity();

            switch (operationType) {
                case "ADD":
                    newQuantity = oldQuantity + quantity;
                    break;
                case "SUBTRACT":
                    newQuantity = Math.max(0, oldQuantity - quantity);
                    break;
                case "SET":
                    newQuantity = quantity;
                    break;
                default:
                    newQuantity = oldQuantity;
            }

            updates.put("/Product/" + product.getProductId() + "/quantity", newQuantity);
            batchOp.addProductChange(product.getProductId(), newQuantity);

            // Also create stock adjustment records
            String adjustmentId = FirebaseDatabase.getInstance().getReference("StockAdjustments").push().getKey();
            if (adjustmentId != null) {
                StockAdjustment adjustment = new StockAdjustment(
                        adjustmentId,
                        product.getProductId(),
                        product.getProductName(),
                        operationType.equals("ADD") ? "Add Stock" :
                                operationType.equals("SUBTRACT") ? "Remove Stock" : "Set Stock",
                        oldQuantity,
                        Math.abs(newQuantity - oldQuantity),
                        newQuantity,
                        reason,
                        "Batch Operation: " + operationName,
                        timestamp,
                        userId
                );
                updates.put("/StockAdjustments/" + adjustmentId, adjustment);
            }
        }

        FirebaseDatabase.getInstance().getReference().updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(BatchStockOperationActivity.this,
                            "Batch operation completed successfully!", Toast.LENGTH_SHORT).show();
                    clearForm();
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(BatchStockOperationActivity.this,
                            "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void clearForm() {
        etOperationName.setText("");
        etQuantity.setText("");
        etRemarks.setText("");
        spinnerOperationType.setSelection(0);
        selectedProducts.clear();
        updateProductCount();
        adapter.notifyDataSetChanged();
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}