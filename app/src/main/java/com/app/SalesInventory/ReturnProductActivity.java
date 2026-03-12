package com.app.SalesInventory;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReturnProductActivity extends BaseActivity {

    private AutoCompleteTextView actvReturnSupplier, actvReturnReason;
    private LinearLayout containerReturnItems;
    private ImageButton btnAddReturnItem;
    private MaterialButton btnSubmitReturn, btnCancelReturn;

    private List<Product> inventoryList = new ArrayList<>();

    // Dynamic Data Lists
    private List<String> supplierNames = new ArrayList<>();
    private List<String> filteredProductNames = new ArrayList<>();

    // Adapters
    private ArrayAdapter<String> supplierAdapter;
    private ArrayAdapter<String> reasonAdapter;
    private ArrayAdapter<String> productAdapter; // dynamically filtered
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

        // Listen for changes in the Supplier Dropdown and filter the products
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

    private void setupAdapters() {
        supplierAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, supplierNames);
        actvReturnSupplier.setAdapter(supplierAdapter);

        String[] reasons = {"Damaged / Defective", "Expired", "Wrong Item Delivered", "Excess Quantity"};
        reasonAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, reasons);
        actvReturnReason.setAdapter(reasonAdapter);

        // Product adapter relies on the dynamically filtered list
        productAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, filteredProductNames);

        String[] units = {"pcs", "ml", "L", "oz", "g", "kg", "box", "pack"};
        unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    }

    // ========================================================================
    // DATA FETCHING & FILTERING
    // ========================================================================
    private void loadData() {
        // FIX: Load suppliers using the Business Owner's ID
        String adminId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (adminId == null || adminId.isEmpty()) {
            adminId = AuthManager.getInstance().getCurrentUserId();
        }
        if (adminId == null) return;

        // 1. Load real Suppliers list exactly like the Purchase Order screen
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

        // 2. Load all raw inventory products
        productRepository.getAllProducts().observe(this, products -> {
            inventoryList.clear();
            if (products != null) {
                for (Product p : products) {
                    if (p.isActive() && !"Menu".equalsIgnoreCase(p.getProductType())) {
                        inventoryList.add(p);
                    }
                }
                // Pre-filter just in case the supplier was already typed
                filterProductsBySupplier(actvReturnSupplier.getText().toString().trim());
            }
        });
    }

    private void filterProductsBySupplier(String supplierName) {
        filteredProductNames.clear();

        // Find products that match this specific supplier
        for (Product p : inventoryList) {
            if (p.getSupplier() != null && p.getSupplier().equalsIgnoreCase(supplierName)) {
                filteredProductNames.add(p.getProductName());
            }
        }
        productAdapter.notifyDataSetChanged(); // Automatically updates the dropdowns in all rows

        // Safety feature: If they change the supplier, clear out any products they selected that don't belong to the new supplier
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
    // ========================================================================


    private void addReturnItemRow() {
        View row = LayoutInflater.from(this).inflate(R.layout.item_return_product, null);

        AutoCompleteTextView actvProduct = row.findViewById(R.id.actvReturnProductItem);
        Spinner spinnerUnit = row.findViewById(R.id.spinnerReturnUnitItem);
        ImageButton btnDelete = row.findViewById(R.id.btnDeleteReturnItem);

        // Sets the filtered adapter
        actvProduct.setAdapter(productAdapter);

        // Auto-show dropdown when clicked
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

            int returnQty = 0;
            try {
                // Ensure it reads correctly even if they typed a decimal accidentally
                returnQty = (int) Double.parseDouble(pQtyStr);
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

            if (existing.getQuantity() < returnQty) {
                Toast.makeText(this, "Cannot return more than available stock for " + pName + ". Current Stock: " + existing.getQuantity(), Toast.LENGTH_LONG).show();
                return;
            }

            Map<String, Object> itemData = new HashMap<>();
            itemData.put("productId", existing.getProductId());
            itemData.put("productName", existing.getProductName());
            itemData.put("returnQty", returnQty);
            itemData.put("unit", pUnit);
            itemData.put("costPrice", existing.getCostPrice());
            itemData.put("currentStock", existing.getQuantity());

            returnItems.add(itemData);
        }

        if (returnItems.isEmpty()) {
            Toast.makeText(this, "Please enter valid quantities for the items", Toast.LENGTH_SHORT).show();
            return;
        }

        // Show confirmation before deducting
        new AlertDialog.Builder(this)
                .setTitle("Confirm Return")
                .setMessage("This will deduct the items from your inventory. Proceed?")
                .setPositiveButton("Yes", (dialog, which) -> processDeductionsAndLog(supplier, reason, returnItems))
                .setNegativeButton("No", null)
                .show();
    }

    private void processDeductionsAndLog(String supplier, String reason, List<Map<String, Object>> returnItems) {
        for (Map<String, Object> item : returnItems) {
            String productId = (String) item.get("productId");
            int returnQty = (int) item.get("returnQty");
            int currentStock = (int) item.get("currentStock");

            int newStock = currentStock - returnQty;
            productRepository.updateProductQuantity(productId, newStock, null);
        }

        // FIX: Ensure the log saves under the Business Owner's ID
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = AuthManager.getInstance().getCurrentUserId();
        }

        // Log to Returns node
        DatabaseReference returnsRef = FirebaseDatabase.getInstance().getReference("Returns");
        String returnId = returnsRef.push().getKey();
        if (returnId != null) {
            Map<String, Object> returnData = new HashMap<>();
            returnData.put("returnId", returnId);
            returnData.put("supplierName", supplier);
            returnData.put("reason", reason);
            returnData.put("items", returnItems);
            returnData.put("date", System.currentTimeMillis());
            returnData.put("ownerAdminId", ownerId); // Logged to the correct unified business ID

            returnsRef.child(returnId).setValue(returnData);
        }

        Toast.makeText(this, "Products Returned & Inventory Deducted", Toast.LENGTH_LONG).show();
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