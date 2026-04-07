package com.app.SalesInventory;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddSupplierActivity extends BaseActivity {

    private TextInputEditText etSupplierName, etSupplierContact, etSupplierEmail, etSupplierAddress;
    private MaterialButton btnSaveSupplier, btnCancelSupplier;
    private DatabaseReference suppliersRef;

    private ChipGroup chipGroupCategories;
    private Button btnAddCustomCategory;

    private LinearLayout containerSupplierProducts;
    private ImageButton btnAddProductRow;

    private ProductRepository productRepository;
    private ArrayAdapter<String> measurementAdapter;
    private List<String> measurementList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_supplier);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        suppliersRef = FirebaseDatabase.getInstance().getReference("Suppliers");
        productRepository = SalesInventoryApplication.getProductRepository();

        etSupplierName = findViewById(R.id.etSupplierName);
        etSupplierContact = findViewById(R.id.etSupplierContact);
        etSupplierEmail = findViewById(R.id.etSupplierEmail);
        etSupplierAddress = findViewById(R.id.etSupplierAddress);

        chipGroupCategories = findViewById(R.id.chipGroupCategories);
        btnAddCustomCategory = findViewById(R.id.btnAddCustomCategory);

        btnSaveSupplier = findViewById(R.id.btnSaveSupplier);
        btnCancelSupplier = findViewById(R.id.btnCancelSupplier);

        containerSupplierProducts = findViewById(R.id.containerSupplierProducts);
        btnAddProductRow = findViewById(R.id.btnAddProductRow);

        setupAdapters();

        btnAddCustomCategory.setOnClickListener(v -> showCustomCategoryDialog());
        btnAddProductRow.setOnClickListener(v -> addProductRow());
        btnSaveSupplier.setOnClickListener(v -> saveSupplier());
        btnCancelSupplier.setOnClickListener(v -> finish());
    }

    private ArrayAdapter<String> getMeasurementAdapter(List<String> items) {
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @NonNull
            @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                view.setBackgroundColor(Color.WHITE);
                ((TextView) view).setTextColor(Color.BLACK);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                view.setBackgroundColor(Color.WHITE);
                ((TextView) view).setTextColor(Color.BLACK);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private void setupAdapters() {
        measurementList = new ArrayList<>(Arrays.asList("pcs", "ml", "L", "g", "kg", "box", "pack", "Custom..."));
        measurementAdapter = getMeasurementAdapter(measurementList);
    }

    private void showCustomCategoryDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Custom Category");

        final EditText input = new EditText(this);
        input.setHint("e.g. Cleaning Supplies");

        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;
        input.setTextColor(textColor);
        input.setHintTextColor(Color.GRAY);

        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(50, 20, 50, 20);
        layout.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        builder.setView(layout);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String newCategory = input.getText().toString().trim();
            if (!newCategory.isEmpty()) {
                addCustomChip(newCategory);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void addCustomChip(String category) {
        Chip chip = new Chip(this);
        chip.setText(category);
        chip.setCheckable(true);
        chip.setChecked(true);
        chip.setClickable(true);
        chip.setFocusable(true);

        chip.setCloseIconVisible(true);
        chip.setOnCloseIconClickListener(v -> chipGroupCategories.removeView(chip));

        chipGroupCategories.addView(chip);
    }

    private void showCustomUnitDialog(Spinner spinner) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Custom Unit");

        final EditText input = new EditText(this);
        input.setHint("e.g. sack, crate, bundle");

        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        input.setTextColor(isDark ? Color.WHITE : Color.BLACK);
        input.setHintTextColor(Color.GRAY);

        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(50, 20, 50, 20);
        layout.addView(input, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        builder.setView(layout);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String newUnit = input.getText().toString().trim();
            if (!newUnit.isEmpty()) {
                int customIndex = measurementList.indexOf("Custom...");
                if (customIndex == -1) customIndex = measurementList.size();
                measurementList.add(customIndex, newUnit);
                measurementAdapter.notifyDataSetChanged();
                spinner.setSelection(customIndex);
            } else {
                spinner.setSelection(0);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            spinner.setSelection(0);
            dialog.cancel();
        });
        builder.show();
    }

    private void addProductRow() {
        View row = LayoutInflater.from(this).inflate(R.layout.item_supplier_product_entry, null);

        Spinner spinnerMeasurement = row.findViewById(R.id.spinnerMeasurementType);
        ImageButton btnDelete = row.findViewById(R.id.btnDeleteProductRow);
        EditText etPcsPerPack = row.findViewById(R.id.etPcsPerPack);

        spinnerMeasurement.setAdapter(measurementAdapter);

        spinnerMeasurement.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedUnit = parent.getItemAtPosition(position).toString();

                if (selectedUnit.equalsIgnoreCase("Custom...")) {
                    showCustomUnitDialog(spinnerMeasurement);
                    return;
                }

                boolean isBulkUnit = !selectedUnit.equalsIgnoreCase("pcs") &&
                        !selectedUnit.equalsIgnoreCase("ml") &&
                        !selectedUnit.equalsIgnoreCase("L") &&
                        !selectedUnit.equalsIgnoreCase("g") &&
                        !selectedUnit.equalsIgnoreCase("kg");

                if (isBulkUnit) {
                    etPcsPerPack.setVisibility(View.VISIBLE);
                    etPcsPerPack.setHint("Qty per " + selectedUnit);
                } else {
                    etPcsPerPack.setVisibility(View.GONE);
                    etPcsPerPack.setText("");
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                etPcsPerPack.setVisibility(View.GONE);
            }
        });

        btnDelete.setOnClickListener(v -> containerSupplierProducts.removeView(row));
        containerSupplierProducts.addView(row);
    }

    private void saveSupplier() {
        String name = etSupplierName.getText().toString().trim();
        String contact = etSupplierContact.getText().toString().trim();
        String email = etSupplierEmail.getText().toString().trim();
        String address = etSupplierAddress.getText().toString().trim();

        if (name.isEmpty()) {
            etSupplierName.setError("Supplier Name is required");
            etSupplierName.requestFocus();
            return;
        }

        StringBuilder categoriesBuilder = new StringBuilder();
        for (int i = 0; i < chipGroupCategories.getChildCount(); i++) {
            View child = chipGroupCategories.getChildAt(i);
            if (child instanceof Chip) {
                Chip chip = (Chip) child;
                if (chip.isChecked()) {
                    if (categoriesBuilder.length() > 0) {
                        categoriesBuilder.append(", ");
                    }
                    categoriesBuilder.append(chip.getText().toString());
                }
            }
        }

        String categories = categoriesBuilder.toString();
        if (categories.isEmpty()) {
            Toast.makeText(this, "Please select or add at least one category.", Toast.LENGTH_SHORT).show();
            return;
        }

        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();

        String id = suppliersRef.push().getKey();
        if (id == null) return;

        Map<String, Object> supplierData = new HashMap<>();
        supplierData.put("id", id);
        supplierData.put("name", name);
        supplierData.put("contact", contact);
        supplierData.put("email", email);
        supplierData.put("address", address);
        supplierData.put("categories", categories);
        supplierData.put("ownerAdminId", ownerId);
        supplierData.put("dateAdded", System.currentTimeMillis());

        suppliersRef.child(id).setValue(supplierData)
                .addOnSuccessListener(aVoid -> {
                    processSuppliedProducts(name, categories);
                    Toast.makeText(AddSupplierActivity.this, "Supplier Saved Successfully", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> Toast.makeText(AddSupplierActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void processSuppliedProducts(String supplierName, String supplierCategories) {
        String adminId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (adminId == null || adminId.isEmpty()) adminId = AuthManager.getInstance().getCurrentUserId();

        if (adminId == null || adminId.isEmpty()) {
            runOnUiThread(this::finish);
            return;
        }
        final String finalAdminId = adminId;

        List<ProductRow> rows = collectProductRows();

        if (rows.isEmpty()) {
            finish();
            return;
        }

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(finalAdminId).collection("products")
                .whereEqualTo("isActive", true)
                .get()
                .addOnSuccessListener(snapshot -> {
                    try {
                        Map<String, String> existingByName = new HashMap<>();

                        for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                            try {
                                Product p = doc.toObject(Product.class);
                                if (p == null) continue;
                                String lowerName = p.getProductName() != null ? p.getProductName().trim().toLowerCase() : "";
                                if (!lowerName.isEmpty()) {
                                    existingByName.put(lowerName, doc.getId());
                                }
                            } catch (Exception e) {}
                        }

                        for (ProductRow row : rows) {
                            String lowerName = row.name.toLowerCase();

                            if (existingByName.containsKey(lowerName)) {
                                String productId = existingByName.get(lowerName);
                                com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                        .collection("users").document(finalAdminId)
                                        .collection("products").document(productId)
                                        .update("costPrice", row.cost, "supplier", supplierName);
                            } else {
                                Product newProduct = new Product();
                                String newId = java.util.UUID.randomUUID().toString();

                                String inheritedCategory = "Supplies";
                                if (supplierCategories != null && !supplierCategories.isEmpty()) {
                                    inheritedCategory = supplierCategories.split(",")[0].trim();
                                }

                                newProduct.setProductId(newId);
                                newProduct.setProductName(row.name);
                                newProduct.setQuantity(row.qty);
                                newProduct.setCostPrice(row.cost);
                                newProduct.setSellingPrice(row.cost * 1.5);
                                newProduct.setUnit(row.unit);
                                newProduct.setSupplier(supplierName);
                                newProduct.setOwnerAdminId(finalAdminId);
                                newProduct.setActive(true);
                                newProduct.setProductType("raw");

                                // =======================================================================
                                // CRITICAL FIX: Ensure the product is fully visible and math-ready!
                                // =======================================================================
                                newProduct.setCategoryName(inheritedCategory);
                                newProduct.setProductLine(inheritedCategory); // Forces visibility in dropdowns
                                newProduct.setPiecesPerUnit(row.pcs);         // Forces PO conversion math to work
                                newProduct.setSalesUnit("pcs");
                                // =======================================================================

                                newProduct.setDateAdded(System.currentTimeMillis());

                                productRepository.addProduct(newProduct, "", null);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        runOnUiThread(this::finish);
                    }
                })
                .addOnFailureListener(e -> runOnUiThread(() -> {
                    Toast.makeText(this, "Could not verify existing products.", Toast.LENGTH_SHORT).show();
                    finish();
                }));
    }

    private List<ProductRow> collectProductRows() {
        List<ProductRow> rows = new ArrayList<>();
        for (int i = 0; i < containerSupplierProducts.getChildCount(); i++) {
            View row = containerSupplierProducts.getChildAt(i);

            EditText etName = row.findViewById(R.id.etProductNameEntry);
            Spinner spinMeasurement = row.findViewById(R.id.spinnerMeasurementType);
            EditText etQty  = row.findViewById(R.id.etProductQty);
            EditText etCost = row.findViewById(R.id.etProductCost);
            EditText etPcsPerPack = row.findViewById(R.id.etPcsPerPack);

            String baseName = etName.getText().toString().trim();
            if (baseName.isEmpty()) continue;

            String measurement = spinMeasurement.getSelectedItem() != null
                    ? spinMeasurement.getSelectedItem().toString() : "pcs";

            String qtyStr  = etQty.getText().toString().trim();
            String costStr = etCost.getText().toString().trim();

            int qty = 0;
            try {
                if (!qtyStr.isEmpty()) qty = Integer.parseInt(qtyStr);
            } catch (NumberFormatException e) {
                try { qty = (int) Double.parseDouble(qtyStr); } catch (Exception ex) { qty = 0; }
            }

            double cost = 0.0;
            try {
                if (!costStr.isEmpty()) cost = Double.parseDouble(costStr);
            } catch (NumberFormatException e) { cost = 0.0; }

            String finalProductName = baseName;

            // CRITICAL FIX: Track Pieces Per Unit so the delivery checklist math works!
            int pcs = 1;

            boolean isBulkUnit = !measurement.equalsIgnoreCase("pcs") &&
                    !measurement.equalsIgnoreCase("ml") &&
                    !measurement.equalsIgnoreCase("L") &&
                    !measurement.equalsIgnoreCase("g") &&
                    !measurement.equalsIgnoreCase("kg") &&
                    !measurement.equalsIgnoreCase("Custom...");

            if (isBulkUnit && etPcsPerPack != null && etPcsPerPack.getVisibility() == View.VISIBLE) {
                String pcsStr = etPcsPerPack.getText().toString().trim();
                if (!pcsStr.isEmpty()) {
                    finalProductName = baseName + " (" + pcsStr + "pcs/" + measurement + ")";
                    try { pcs = Integer.parseInt(pcsStr); } catch (Exception ignored) {}
                }
            }

            rows.add(new ProductRow(finalProductName, qty, cost, measurement, pcs));
        }
        return rows;
    }

    private static class ProductRow {
        final String name;
        final int    qty;
        final double cost;
        final String unit;
        final int    pcs; // CRITICAL FIX: Added pcs tracking

        ProductRow(String name, int qty, double cost, String unit, int pcs) {
            this.name = name;
            this.qty  = qty;
            this.cost = cost;
            this.unit = unit;
            this.pcs  = pcs;
        }
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