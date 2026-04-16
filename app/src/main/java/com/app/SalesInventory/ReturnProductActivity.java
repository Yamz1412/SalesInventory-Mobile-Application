package com.app.SalesInventory;

import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReturnProductActivity extends BaseActivity {

    private AutoCompleteTextView actvReturnSupplier, actvReturnReason;
    private LinearLayout containerReturnItems;
    private ImageButton btnAddReturnItem;
    private MaterialButton btnSubmitReturn, btnCancelReturn;

    private List<Product> inventoryList = new ArrayList<>();

    private List<String> supplierNames = new ArrayList<>();
    private List<String> filteredProductNames = new ArrayList<>();

    private ArrayAdapter<String> supplierAdapter;
    private ArrayAdapter<String> reasonAdapter;
    private ArrayAdapter<String> productAdapter;
    private ArrayAdapter<String> unitAdapter;

    private ProductRepository productRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_return_product);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        productRepository = SalesInventoryApplication.getProductRepository();

        actvReturnSupplier = findViewById(R.id.actvReturnSupplier);
        actvReturnReason = findViewById(R.id.actvReturnReason);
        containerReturnItems = findViewById(R.id.containerReturnItems);
        btnAddReturnItem = findViewById(R.id.btnAddReturnItem);
        btnSubmitReturn = findViewById(R.id.btnSubmitReturn);
        btnCancelReturn = findViewById(R.id.btnCancelReturn);

        setupAdapters();
        loadData();

        actvReturnSupplier.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                filterProductsBySupplier(s.toString().trim());
            }
        });

        btnAddReturnItem.setOnClickListener(v -> addReturnItemRow());
        btnSubmitReturn.setOnClickListener(v -> submitReturn());
        btnCancelReturn.setOnClickListener(v -> finish());
    }

    // ================================================================
    // FIX: Adaptive Adapters for Dropdowns and Spinners
    // ================================================================
    private ArrayAdapter<String> getAdaptiveAdapter(List<String> items) {
        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
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

    private ArrayAdapter<String> getAdaptiveDropdownAdapter(List<String> items) {
        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        return new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, items) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ((TextView) view).setTextColor(textColor);
                return view;
            }
        };
    }

    private void setupAdapters() {
        supplierAdapter = getAdaptiveDropdownAdapter(supplierNames);
        actvReturnSupplier.setAdapter(supplierAdapter);
        // FIXED: Prevent keyboard from popping up and blocking the dropdown
        actvReturnSupplier.setInputType(android.text.InputType.TYPE_NULL);

        List<String> reasons = Arrays.asList("Damaged / Defective", "Expired", "Wrong Item Delivered", "Excess Quantity");
        reasonAdapter = getAdaptiveDropdownAdapter(reasons);
        actvReturnReason.setAdapter(reasonAdapter);
        // FIXED: Prevent keyboard from popping up and blocking the dropdown
        actvReturnReason.setInputType(android.text.InputType.TYPE_NULL);

        productAdapter = getAdaptiveDropdownAdapter(filteredProductNames);

        List<String> units = Arrays.asList("pcs", "ml", "L", "oz", "g", "kg", "box", "pack");
        unitAdapter = getAdaptiveAdapter(units);
    }

    private void loadData() {
        String adminId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (adminId == null || adminId.isEmpty()) {
            adminId = AuthManager.getInstance().getCurrentUserId();
        }
        if (adminId == null) return;

        DatabaseReference supRef = FirebaseDatabase.getInstance().getReference("Suppliers");
        supRef.orderByChild("ownerAdminId").equalTo(adminId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                supplierNames.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Supplier s = ds.getValue(Supplier.class);
                    if (s != null && s.getName() != null) {
                        supplierNames.add(s.getName());
                    }
                }
                supplierAdapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        productRepository.getAllProducts().observe(this, products -> {
            inventoryList.clear();
            if (products != null) {
                for (Product p : products) {
                    if (p.isActive() && !"Menu".equalsIgnoreCase(p.getProductType())) {
                        inventoryList.add(p);
                    }
                }
                filterProductsBySupplier(actvReturnSupplier.getText().toString().trim());
            }
        });
    }

    private void filterProductsBySupplier(String supplierName) {
        filteredProductNames.clear();

        for (Product p : inventoryList) {
            if (p.getSupplier() != null && p.getSupplier().equalsIgnoreCase(supplierName)) {
                filteredProductNames.add(p.getProductName());
            }
        }
        productAdapter.notifyDataSetChanged();

        for (int i = 0; i < containerReturnItems.getChildCount(); i++) {
            View row = containerReturnItems.getChildAt(i);
            AutoCompleteTextView actvProduct = row.findViewById(R.id.actvReturnProductItem);
            String currentText = actvProduct.getText().toString();

            if (!currentText.isEmpty() && !filteredProductNames.contains(currentText)) {
                actvProduct.setText("");
                actvProduct.setHint("Select Product");
            }
        }
    }

    private void addReturnItemRow() {
        View row = LayoutInflater.from(this).inflate(R.layout.item_return_product, null);

        AutoCompleteTextView actvProduct = row.findViewById(R.id.actvReturnProductItem);
        Spinner spinnerUnit = row.findViewById(R.id.spinnerReturnUnitItem);
        ImageButton btnDelete = row.findViewById(R.id.btnDeleteReturnItem);
        actvProduct.setInputType(android.text.InputType.TYPE_NULL);

        actvProduct.setAdapter(productAdapter);
        actvProduct.setOnClickListener(v -> actvProduct.showDropDown());
        actvProduct.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) actvProduct.showDropDown();
        });

        spinnerUnit.setAdapter(unitAdapter);
        btnDelete.setOnClickListener(v -> containerReturnItems.removeView(row));
        containerReturnItems.addView(row);
    }

    private void submitReturn() {
        String supplier = actvReturnSupplier.getText().toString().trim();
        String reason = actvReturnReason.getText().toString().trim();

        if (supplier.isEmpty() || reason.isEmpty()) {
            Toast.makeText(this, "Please select a Supplier and a Reason", Toast.LENGTH_SHORT).show();
            return;
        }

        if (containerReturnItems.getChildCount() == 0) {
            Toast.makeText(this, "Please add at least one item to return", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Map<String, Object>> returnItems = new ArrayList<>();

        for (int i = 0; i < containerReturnItems.getChildCount(); i++) {
            View row = containerReturnItems.getChildAt(i);
            AutoCompleteTextView actvProduct = row.findViewById(R.id.actvReturnProductItem);
            EditText etQty = row.findViewById(R.id.etReturnQtyItem);
            Spinner spinUnit = row.findViewById(R.id.spinnerReturnUnitItem);

            String pName = actvProduct.getText().toString().trim();
            String pQtyStr = etQty.getText().toString().trim();
            String pUnit = spinUnit.getSelectedItem() != null ? spinUnit.getSelectedItem().toString() : "pcs";

            if (pName.isEmpty() || pQtyStr.isEmpty()) continue;

            double returnQty = 0.0;
            try {
                returnQty = Double.parseDouble(pQtyStr);
            } catch (Exception e) {
                continue;
            }

            if (returnQty <= 0) continue;

            Product existing = null;
            for (Product p : inventoryList) {
                if (p.getProductName().equalsIgnoreCase(pName)) {
                    existing = p;
                    break;
                }
            }

            if (existing == null) {
                Toast.makeText(this, "Product not found in inventory: " + pName, Toast.LENGTH_SHORT).show();
                return;
            }

            int ppu = existing.getPiecesPerUnit() > 0 ? existing.getPiecesPerUnit() : 1;
            String invUnit = existing.getUnit() != null ? existing.getUnit() : "pcs";

            Object[] conversion = UnitConverterUtil.convertBaseInventoryUnit(existing.getQuantity(), invUnit, pUnit, ppu);
            String newInvUnit = (String) conversion[1];

            double exactDeductQty = UnitConverterUtil.calculateDeductionAmount(returnQty, newInvUnit, pUnit, ppu);

            if (existing.getQuantity() < exactDeductQty) {
                Toast.makeText(this, "Cannot return more than available stock for " + pName + ". Current Stock: " + existing.getQuantity(), Toast.LENGTH_LONG).show();
                return;
            }

            double unitCost = existing.getQuantity() > 0 ? (existing.getCostPrice() / existing.getQuantity()) : 0.0;
            double costToDeduct = exactDeductQty * unitCost;

            Map<String, Object> itemData = new HashMap<>();
            itemData.put("productId", existing.getProductId());
            itemData.put("productName", existing.getProductName());
            itemData.put("returnQty", returnQty);
            itemData.put("unit", pUnit);
            itemData.put("exactDeductQty", exactDeductQty);
            itemData.put("costToDeduct", costToDeduct);
            itemData.put("currentStock", existing.getQuantity());
            itemData.put("currentTotalCost", existing.getCostPrice());

            returnItems.add(itemData);
        }

        if (returnItems.isEmpty()) {
            Toast.makeText(this, "Please enter valid quantities for the items", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Confirm Return")
                .setMessage("This will automatically deduct the items and their cost value from your inventory. Proceed?")
                .setPositiveButton("Yes", (dialog, which) -> processDeductionsAndLog(supplier, reason, returnItems))
                .setNegativeButton("No", null)
                .show();
    }

    private void processDeductionsAndLog(String supplier, String reason, List<Map<String, Object>> returnItems) {

        for (Map<String, Object> item : returnItems) {
            String productId = (String) item.get("productId");
            double exactDeductQty = (double) item.get("exactDeductQty");
            double costToDeduct = (double) item.get("costToDeduct");
            double currentStock = (double) item.get("currentStock");
            double currentTotalCost = (double) item.get("currentTotalCost");

            double newStock = Math.max(0.0, currentStock - exactDeductQty);
            double newTotalCost = Math.max(0.0, currentTotalCost - costToDeduct);

            productRepository.updateProductQuantityAndCost(productId, newStock, newTotalCost, null);

            new Thread(() -> {
                AppDatabase db = AppDatabase.getInstance(ReturnProductActivity.this);
                List<BatchEntity> batches = db.batchDao().getAvailableBatchesFIFO(productId);
                double remainingToDeduct = exactDeductQty;

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
            }).start();
        }

        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = AuthManager.getInstance().getCurrentUserId();
        }

        DatabaseReference returnsRef = FirebaseDatabase.getInstance().getReference("Returns");
        String returnId = returnsRef.push().getKey();
        if (returnId != null) {
            Map<String, Object> returnData = new HashMap<>();
            returnData.put("returnId", returnId);
            returnData.put("supplierName", supplier);
            returnData.put("reason", reason);
            returnData.put("items", returnItems);
            returnData.put("date", System.currentTimeMillis());
            returnData.put("ownerAdminId", ownerId);

            returnsRef.child(returnId).setValue(returnData);
        }

        Toast.makeText(this, "Products Returned & Inventory Value Deducted", Toast.LENGTH_LONG).show();
        finish();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}