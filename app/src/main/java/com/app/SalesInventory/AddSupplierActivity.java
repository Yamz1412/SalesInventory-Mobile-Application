package com.app.SalesInventory;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddSupplierActivity extends BaseActivity {

    private TextInputEditText etSupplierName, etSupplierContact, etSupplierEmail, etSupplierAddress, etSupplierCategories;
    private MaterialButton btnSaveSupplier, btnCancelSupplier;
    private DatabaseReference suppliersRef;

    private LinearLayout containerSupplierProducts;
    private ImageButton btnAddProductRow;

    private ProductRepository productRepository;
    private ArrayAdapter<String> unitAdapter;

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
        etSupplierCategories = findViewById(R.id.etSupplierCategories);
        btnSaveSupplier = findViewById(R.id.btnSaveSupplier);
        btnCancelSupplier = findViewById(R.id.btnCancelSupplier);

        containerSupplierProducts = findViewById(R.id.containerSupplierProducts);
        btnAddProductRow = findViewById(R.id.btnAddProductRow);

        setupAdapters();

        btnAddProductRow.setOnClickListener(v -> addProductRow());
        btnSaveSupplier.setOnClickListener(v -> saveSupplier());
        btnCancelSupplier.setOnClickListener(v -> finish());
    }

    private void setupAdapters() {
        String[] units = {"pcs", "ml", "L", "oz", "g", "kg", "box", "pack"};
        unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    }

    private void addProductRow() {
        View row = LayoutInflater.from(this).inflate(R.layout.item_supplier_product_entry, null);

        Spinner spinnerUnit = row.findViewById(R.id.spinnerProductUnit);
        ImageButton btnDelete = row.findViewById(R.id.btnDeleteProductRow);

        spinnerUnit.setAdapter(unitAdapter);

        btnDelete.setOnClickListener(v -> containerSupplierProducts.removeView(row));

        containerSupplierProducts.addView(row);
    }

    private void saveSupplier() {
        String name = etSupplierName.getText().toString().trim();
        String contact = etSupplierContact.getText().toString().trim();
        String email = etSupplierEmail.getText().toString().trim();
        String address = etSupplierAddress.getText().toString().trim();
        String categories = etSupplierCategories.getText().toString().trim();

        if (name.isEmpty()) {
            etSupplierName.setError("Supplier Name is required");
            etSupplierName.requestFocus();
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
                    // Process products — this will add stock to existing ones
                    // and open AddProductActivity for any brand-new products.
                    processSuppliedProducts(name);
                    Toast.makeText(AddSupplierActivity.this, "Supplier Saved Successfully", Toast.LENGTH_SHORT).show();
                    // Note: finish() is called inside processSuppliedProducts
                    // after handling the product rows so we don't close too early.
                })
                .addOnFailureListener(e -> Toast.makeText(AddSupplierActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    // -------------------------------------------------------------------------
    // UPDATED: processSuppliedProducts
    //
    // For each product row the user filled in:
    //   • If a product with the same name (case-insensitive) already exists in
    //     the inventory for this owner → just add the entered quantity to its
    //     current stock (no duplicate created).
    //   • If it does NOT exist yet → collect it into a "registration queue" and
    //     open AddProductActivity with the data pre-filled so the user can
    //     properly register it.  Only products that go through AddProductActivity
    //     get a supplier tag, which means they will show up in the supplier
    //     product panel later.
    //
    // Products added manually from the dashboard button (without a supplier
    // name set) will NEVER appear in the supplier product panel because
    // CreatePurchaseOrderActivity now filters by supplier != null/empty.
    // -------------------------------------------------------------------------
    private void processSuppliedProducts(String supplierName) {
        String adminId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (adminId == null || adminId.isEmpty()) adminId = AuthManager.getInstance().getCurrentUserId();
        final String finalAdminId = adminId;

        // Collect all filled-in product rows from the form
        List<ProductRow> rows = collectProductRows();

        if (rows.isEmpty()) {
            // No product rows entered — just close the screen
            finish();
            return;
        }

        // We need the current inventory to check for existing products.
        // Fetch once from Firestore so we always have the latest state.
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(finalAdminId).collection("products")
                .whereEqualTo("isActive", true)
                .get()
                .addOnSuccessListener(snapshot -> {

                    // Build a name → productId map of existing inventory products
                    Map<String, String> existingByName = new HashMap<>();   // lowerName → productId
                    Map<String, Double> existingQtyById = new HashMap<>();  // productId → currentQty

                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                        Product p = doc.toObject(Product.class);
                        if (p == null) continue;
                        p.setProductId(doc.getId());
                        String lowerName = p.getProductName() != null
                                ? p.getProductName().trim().toLowerCase()
                                : "";
                        if (!lowerName.isEmpty()) {
                            existingByName.put(lowerName, doc.getId());
                            existingQtyById.put(doc.getId(), p.getQuantity());
                        }
                    }

                    // Separate rows into "existing" (add stock) vs "new" (register)
                    ArrayList<Bundle> registrationQueue = new ArrayList<>();

                    for (ProductRow row : rows) {
                        String lowerName = row.name.toLowerCase();

                        if (existingByName.containsKey(lowerName)) {
                            // ---- Product already exists → increment stock ----
                            String productId = existingByName.get(lowerName);
                            double currentQty = existingQtyById.containsKey(productId)
                                    ? existingQtyById.get(productId) : 0;
                            double newQty = currentQty + row.qty;

                            // Update quantity in both Firestore and the local Room cache
                            // via the existing updateProductQuantityAndCost method.
                            // Passing 0 for cost price means the cost won't be overwritten.
                            productRepository.updateProductQuantityAndCost(
                                    productId,
                                    newQty,
                                    0,  // 0 = keep existing cost price unchanged
                                    new ProductRepository.OnProductUpdatedListener() {
                                        @Override
                                        public void onProductUpdated() {
                                            // Also push the new quantity to Firestore
                                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                                    .collection("users").document(finalAdminId)
                                                    .collection("products").document(productId)
                                                    .update("quantity", newQty);
                                        }
                                        @Override
                                        public void onError(String error) {
                                            runOnUiThread(() -> Toast.makeText(
                                                    AddSupplierActivity.this,
                                                    "Failed to update stock for " + row.name,
                                                    Toast.LENGTH_SHORT).show());
                                        }
                                    });

                        } else {
                            // ---- Product does not exist → queue for registration ----
                            Bundle b = new Bundle();
                            b.putString("PREFILL_NAME", row.name);
                            b.putDouble("PREFILL_COST", row.cost);
                            b.putInt("PREFILL_QTY", row.qty);
                            b.putString("PREFILL_UNIT", row.unit);
                            // Pass supplier name so AddProductActivity can tag it
                            b.putString("PREFILL_SUPPLIER", supplierName);
                            registrationQueue.add(b);
                        }
                    }

                    runOnUiThread(() -> {
                        if (!registrationQueue.isEmpty()) {
                            // Open AddProductActivity with the queue so each new
                            // product gets properly registered in the inventory.
                            Intent intent = new Intent(AddSupplierActivity.this, AddProductActivity.class);
                            intent.putParcelableArrayListExtra("REGISTRATION_QUEUE", registrationQueue);
                            startActivity(intent);
                        }
                        // Close AddSupplierActivity — supplier is already saved.
                        finish();
                    });
                })
                .addOnFailureListener(e -> runOnUiThread(() -> {
                    Toast.makeText(this, "Could not verify existing products. Please try again.", Toast.LENGTH_SHORT).show();
                    // Still close; the supplier was saved successfully.
                    finish();
                }));
    }

    // -------------------------------------------------------------------------
    // Helper: read every filled product row from the form into a plain list.
    // -------------------------------------------------------------------------
    private List<ProductRow> collectProductRows() {
        List<ProductRow> rows = new ArrayList<>();
        for (int i = 0; i < containerSupplierProducts.getChildCount(); i++) {
            View row = containerSupplierProducts.getChildAt(i);

            EditText etName = row.findViewById(R.id.etProductNameEntry);
            EditText etQty  = row.findViewById(R.id.etProductQty);
            EditText etCost = row.findViewById(R.id.etProductCost);
            Spinner  spinUnit = row.findViewById(R.id.spinnerProductUnit);

            String name = etName.getText().toString().trim();
            if (name.isEmpty()) continue; // Skip blank rows

            String qtyStr  = etQty.getText().toString().trim();
            String costStr = etCost.getText().toString().trim();
            String unit    = spinUnit.getSelectedItem() != null
                    ? spinUnit.getSelectedItem().toString() : "pcs";

            int qty     = qtyStr.isEmpty()  ? 0   : Integer.parseInt(qtyStr);
            double cost = costStr.isEmpty() ? 0.0 : Double.parseDouble(costStr);

            rows.add(new ProductRow(name, qty, cost, unit));
        }
        return rows;
    }

    // Simple data-holder for one product row
    private static class ProductRow {
        final String name;
        final int    qty;
        final double cost;
        final String unit;

        ProductRow(String name, int qty, double cost, String unit) {
            this.name = name;
            this.qty  = qty;
            this.cost = cost;
            this.unit = unit;
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