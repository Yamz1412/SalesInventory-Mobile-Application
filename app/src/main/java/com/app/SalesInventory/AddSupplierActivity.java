package com.app.SalesInventory;

import android.Manifest;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class AddSupplierActivity extends BaseActivity {

    private TextInputEditText etSupplierName, etSupplierContact, etSupplierEmail, etSupplierAddress, etSupplierCategories;
    private MaterialButton btnSaveSupplier, btnCancelSupplier;
    private DatabaseReference suppliersRef;

    private LinearLayout containerSupplierProducts;
    private ImageButton btnAddProductRow;

    private ProductRepository productRepository;
    private ArrayAdapter<String> unitAdapter;

    // Image Handling Variables
    private ImageView currentImageViewProcessing;
    private Uri imageUriTemp;
    private final Map<View, Uri> rowImageUriMap = new HashMap<>();

    // Activity Result Launchers
    private ActivityResultLauncher<Intent> cameraLauncher;
    private ActivityResultLauncher<String> galleryLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;

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

        initLaunchers();
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

        ImageView ivProductImage = row.findViewById(R.id.ivProductEntryImage);
        Spinner spinnerUnit = row.findViewById(R.id.spinnerProductUnit);
        ImageButton btnDelete = row.findViewById(R.id.btnDeleteProductRow);

        spinnerUnit.setAdapter(unitAdapter);

        // Handle Image Selection on Click
        ivProductImage.setOnClickListener(v -> {
            currentImageViewProcessing = ivProductImage;
            showImagePickDialog();
        });

        btnDelete.setOnClickListener(v -> {
            rowImageUriMap.remove(row);
            containerSupplierProducts.removeView(row);
        });

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
                    processSuppliedProducts(name); // Link products
                    Toast.makeText(AddSupplierActivity.this, "Supplier Saved Successfully", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(AddSupplierActivity.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void processSuppliedProducts(String supplierName) {
        String adminId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (adminId == null || adminId.isEmpty()) adminId = AuthManager.getInstance().getCurrentUserId();

        for (int i = 0; i < containerSupplierProducts.getChildCount(); i++) {
            View row = containerSupplierProducts.getChildAt(i);
            EditText etNameEntry = row.findViewById(R.id.etProductNameEntry);
            EditText etQty = row.findViewById(R.id.etProductQty);
            EditText etCost = row.findViewById(R.id.etProductCost);
            Spinner spinUnit = row.findViewById(R.id.spinnerProductUnit);

            String pNameEntry = etNameEntry.getText().toString().trim();
            String pQtyStr = etQty.getText().toString().trim();
            String pCostStr = etCost.getText().toString().trim();
            String pUnit = spinUnit.getSelectedItem() != null ? spinUnit.getSelectedItem().toString() : "pcs";

            if (pNameEntry.isEmpty()) continue; // Skip incomplete rows

            int qty = pQtyStr.isEmpty() ? 0 : Integer.parseInt(pQtyStr);
            double cost = pCostStr.isEmpty() ? 0.0 : Double.parseDouble(pCostStr);

            // Fetch URI stored for this specific row view
            Uri selectedUri = null;
            ImageView ivInRow = row.findViewById(R.id.ivProductEntryImage);
            if (rowImageUriMap.containsKey(ivInRow)) {
                selectedUri = rowImageUriMap.get(ivInRow);
            }

            // Create new inventory definition specific to this supplier
            // We append supplier name to make the definition unique in the global inventory
            Product newProd = new Product();
            newProd.setProductName(pNameEntry + " - " + supplierName);
            newProd.setCostPrice(cost);
            newProd.setUnit(pUnit);
            newProd.setSupplier(supplierName);
            newProd.setQuantity(qty); // Optional initial stock
            newProd.setProductType("Inventory");
            newProd.setCategoryName("Raw Materials");
            newProd.setActive(true);
            newProd.setOwnerAdminId(adminId);

            // Add product, repository handles optional image upload automatically
            productRepository.addProduct(newProd, selectedUri, null);
        }
    }

    // =============================================================================================
    // MODIFIED: Professional Image Picker Logic (Optional, Camera or Gallery)
    // =============================================================================================

    private void initLaunchers() {
        // Handle Camera Result
        cameraLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && currentImageViewProcessing != null && imageUriTemp != null) {
                        currentImageViewProcessing.setImageURI(imageUriTemp);
                        rowImageUriMap.put(currentImageViewProcessing, imageUriTemp); // Store URI for this row
                    }
                }
        );

        // Handle Gallery Result (Modern Android Picker)
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null && currentImageViewProcessing != null) {
                        currentImageViewProcessing.setImageURI(uri);
                        rowImageUriMap.put(currentImageViewProcessing, uri); // Store URI for this row
                    }
                }
        );

        // Handle Permission Requests
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                result -> {
                    Boolean cameraGranted = result.getOrDefault(Manifest.permission.CAMERA, false);
                    Boolean storageGranted = false;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        storageGranted = result.getOrDefault(Manifest.permission.READ_MEDIA_IMAGES, false);
                    } else {
                        storageGranted = result.getOrDefault(Manifest.permission.WRITE_EXTERNAL_STORAGE, false);
                    }

                    if (cameraGranted && storageGranted) {
                        launchCameraIntent();
                    } else {
                        Toast.makeText(this, "Permissions required for Camera", Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    private void showImagePickDialog() {
        String[] options = {"Take Photo (Camera)", "Choose from Gallery"};
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Product Image (Optional)");
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                checkCameraPermissions();
            } else {
                galleryLauncher.launch("image/*");
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void checkCameraPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.READ_MEDIA_IMAGES};
        } else {
            permissions = new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
        }

        boolean allGranted = true;
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            launchCameraIntent();
        } else {
            permissionLauncher.launch(permissions);
        }
    }

    private void launchCameraIntent() {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "New Product Image");
        values.put(MediaStore.Images.Media.DESCRIPTION, "From Supplier Entry");

        // Save to temporary system file URI
        imageUriTemp = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUriTemp);
        cameraLauncher.launch(intent);
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