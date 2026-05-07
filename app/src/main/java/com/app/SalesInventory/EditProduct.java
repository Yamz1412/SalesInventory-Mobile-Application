package com.app.SalesInventory;

import static android.content.Intent.getIntent;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditProduct extends BaseActivity {

    private ImageButton btnEditPhoto;
    private TextInputEditText productNameET, costPriceET, expiryDateET, quantityET, etPiecesPerUnit;
    private AutoCompleteTextView productLineET, productTypeET;
    private MaterialButton updateBtn, cancelBtn; // Updated to MaterialButton
    private Spinner unitSpinner, subUnitSpinner;
    private LinearLayout layoutPiecesPerUnit;

    private TextView tvReorderLevel, tvCriticalLevel;
    private ProductRepository productRepository;
    private AuthManager authManager;
    private SimpleDateFormat expiryFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private String selectedImagePath;
    private Calendar expiryCalendar = Calendar.getInstance();
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    private List<Product> inventoryProducts = new ArrayList<>();
    private String currentUserId;
    private String editProductId;
    private Product existingProductToEdit;
    private String lastSelectedUnit = "";
    private SwitchMaterial switchSellOnPOS;
    private TextView tvTotalCalculatedStock;
    private TextInputEditText sellingPriceET;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_product);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Edit Product");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Handle ID retrieval
        editProductId = getIntent().getStringExtra("PRODUCT_ID");
        if (editProductId == null) editProductId = getIntent().getStringExtra("productId");
        if (editProductId == null) editProductId = getIntent().getStringExtra("product_id");

        if (editProductId == null) {
            Toast.makeText(this, "Product ID missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        productRepository = SalesInventoryApplication.getProductRepository();
        authManager = AuthManager.getInstance();
        currentUserId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (currentUserId == null) currentUserId = authManager.getCurrentUserId();

        // Initialize Views
        btnEditPhoto = findViewById(R.id.btnEditPhoto);
        productNameET = findViewById(R.id.productNameET);
        productLineET = findViewById(R.id.productLineET);
        productTypeET = findViewById(R.id.productTypeET);
        costPriceET = findViewById(R.id.costPriceET);
        quantityET = findViewById(R.id.quantityET); // Now mapped as TextInputEditText
        unitSpinner = findViewById(R.id.unitSpinner);
        subUnitSpinner = findViewById(R.id.subUnitSpinner);
        layoutPiecesPerUnit = findViewById(R.id.layoutPiecesPerUnit);
        etPiecesPerUnit = findViewById(R.id.etPiecesPerUnit);
        expiryDateET = findViewById(R.id.expiryDateET);
        updateBtn = findViewById(R.id.updateBtn);
        cancelBtn = findViewById(R.id.cancelBtn);

        setupImagePickers();
        loadInventoryForCalculations();
        setupDynamicCategoryDropdowns();
        setupBidirectionalMath();

        // Setup Spinners
        String[] units = {"pcs", "ml", "L", "oz", "g", "kg", "box", "pack"};
        unitSpinner.setAdapter(getAdaptiveAdapter(units));
        subUnitSpinner.setAdapter(getAdaptiveAdapter(units));

        unitSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selected = unitSpinner.getSelectedItem().toString();
                boolean isBulk = selected.equalsIgnoreCase("box") || selected.equalsIgnoreCase("pack") ||
                        selected.equalsIgnoreCase("kg") || selected.equalsIgnoreCase("L");

                if (layoutPiecesPerUnit != null) {
                    layoutPiecesPerUnit.setVisibility(isBulk ? View.VISIBLE : View.GONE);
                }

                if (!selected.equals(lastSelectedUnit)) {
                    lastSelectedUnit = selected;
                    if (selected.equalsIgnoreCase("kg")) {
                        etPiecesPerUnit.setHint("Total grams (g)");
                        setSubUnitSpinnerValue("g");
                        // Trigger Math
                        try { double q = Double.parseDouble(quantityET.getText().toString()); etPiecesPerUnit.setText(String.valueOf((int)(q * 1000))); } catch(Exception ignored){}
                    } else if (selected.equalsIgnoreCase("L")) {
                        etPiecesPerUnit.setHint("Total ml");
                        setSubUnitSpinnerValue("ml");
                        // Trigger Math
                        try { double q = Double.parseDouble(quantityET.getText().toString()); etPiecesPerUnit.setText(String.valueOf((int)(q * 1000))); } catch(Exception ignored){}
                    } else if (selected.equalsIgnoreCase("box") || selected.equalsIgnoreCase("pack")) {
                        etPiecesPerUnit.setHint("Total Pcs (User Input)");
                        etPiecesPerUnit.setText(""); // Leave blank for user input
                        setSubUnitSpinnerValue("pcs");
                    } else {
                        etPiecesPerUnit.setText("1");
                    }
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });


        productTypeET.setOnLongClickListener(v -> {
            showDeleteMetadataDialog("Delete Category", "Categories", "categoryName");
            return true;
        });

        productLineET.setOnLongClickListener(v -> {
            showDeleteMetadataDialog("Delete Product Line", "ProductLines", "lineName");
            return true;
        });

        // Listeners
        updateBtn.setOnClickListener(v -> attemptEdit());
        cancelBtn.setOnClickListener(v -> finish());
        btnEditPhoto.setOnClickListener(v -> tryPickImage());
        expiryDateET.setOnClickListener(v -> showExpiryDatePicker());

        tvReorderLevel = findViewById(R.id.tvReorderLevel);
        tvCriticalLevel = findViewById(R.id.tvCriticalLevel);

        quantityET.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateStockAlertsDisplay();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadProductData();
    }

    private boolean isAutoFormatting = false;

    private void setupBidirectionalMath() {
        TextWatcher mainQtyWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (isAutoFormatting) return;
                String unit = unitSpinner.getSelectedItem() != null ? unitSpinner.getSelectedItem().toString() : "";
                if (unit.equalsIgnoreCase("kg") || unit.equalsIgnoreCase("L")) {
                    try {
                        double qty = Double.parseDouble(s.toString());
                        isAutoFormatting = true;
                        etPiecesPerUnit.setText(String.valueOf((int)(qty * 1000))); // 200 kg -> 200000 g
                        isAutoFormatting = false;
                    } catch (Exception e) {
                        isAutoFormatting = true;
                        etPiecesPerUnit.setText("");
                        isAutoFormatting = false;
                    }
                }
                updateStockAlertsDisplay();
            }
        };

        TextWatcher subQtyWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                if (isAutoFormatting) return;
                String unit = unitSpinner.getSelectedItem() != null ? unitSpinner.getSelectedItem().toString() : "";
                if (unit.equalsIgnoreCase("kg") || unit.equalsIgnoreCase("L")) {
                    try {
                        double subQty = Double.parseDouble(s.toString());
                        isAutoFormatting = true;
                        double mainQty = subQty / 1000.0; // 200000 g -> 200 kg
                        if (mainQty % 1 == 0) {
                            quantityET.setText(String.valueOf((int)mainQty));
                        } else {
                            quantityET.setText(String.valueOf(mainQty));
                        }
                        isAutoFormatting = false;
                    } catch (Exception e) {
                        isAutoFormatting = true;
                        quantityET.setText("");
                        isAutoFormatting = false;
                    }
                }
            }
        };

        if (quantityET != null) quantityET.addTextChangedListener(mainQtyWatcher);
        if (etPiecesPerUnit != null) etPiecesPerUnit.addTextChangedListener(subQtyWatcher);
    }

    private void setSubUnitSpinnerValue(String targetUnit) {
        if (subUnitSpinner != null && subUnitSpinner.getAdapter() != null) {
            for (int i = 0; i < subUnitSpinner.getAdapter().getCount(); i++) {
                if (subUnitSpinner.getAdapter().getItem(i).toString().equalsIgnoreCase(targetUnit)) {
                    subUnitSpinner.setSelection(i);
                    break;
                }
            }
        }
    }

    private void updateStockAlertsDisplay() {
        int qty = 0;
        try {
            String qtyStr = quantityET.getText() != null ? quantityET.getText().toString() : "0";
            qty = Integer.parseInt(qtyStr.isEmpty() ? "0" : qtyStr);
        } catch (Exception ignored) {}

        if (qty < 0) qty = 0;

        // 20% for Reorder, 5% for Critical
        int reorderLevel = (int) Math.ceil(qty * 0.20);
        int criticalLevel = (int) Math.ceil(qty * 0.05);
        if (criticalLevel == 0 && qty > 0) criticalLevel = 1;

        if (tvReorderLevel != null) tvReorderLevel.setText("Reorder: " + reorderLevel);
        if (tvCriticalLevel != null) tvCriticalLevel.setText("Critical: " + criticalLevel);
    }

    private void loadProductData() {
        productRepository.getProductById(editProductId, new ProductRepository.OnProductFetchedListener() {
            @Override
            public void onProductFetched(Product p) {
                runOnUiThread(() -> {
                    if (p != null) {
                        existingProductToEdit = p;
                        productNameET.setText(p.getProductName());
                        productTypeET.setText(p.getCategoryName());
                        productLineET.setText(p.getProductLine());

                        double rawCost = p.getCostPrice();
                        if (rawCost % 1 == 0) {
                            costPriceET.setText(String.valueOf((long) rawCost));
                        } else {
                            costPriceET.setText(String.format(Locale.US, "%.4f", rawCost).replaceAll("0*$", "").replaceAll("\\.$", ""));
                        }
                        double qty = p.getQuantity();
                        if (qty % 1 == 0) {
                            quantityET.setText(String.valueOf((long) qty));
                        } else {
                            quantityET.setText(String.valueOf(qty));
                        }

                        if (p.getExpiryDate() != null && p.getExpiryDate().getTime() > 0) {
                            expiryCalendar.setTimeInMillis(p.getExpiryDate().getTime());
                            expiryDateET.setText(expiryFormat.format(expiryCalendar.getTime()));
                        }

                        etPiecesPerUnit.setText(String.valueOf(p.getPiecesPerUnit()));

                        // Set Main Unit Spinner selection
                        ArrayAdapter adapter = (ArrayAdapter) unitSpinner.getAdapter();
                        String savedUnit = p.getUnit() != null ? p.getUnit().trim() : "pcs";
                        int pos = adapter.getPosition(savedUnit);
                        if (pos >= 0) unitSpinner.setSelection(pos);

                        // CRITICAL FIX: Safely load the Sub-Unit using .post() to prevent UI wipe
                        String savedSubUnit = p.getSalesUnit() != null ? p.getSalesUnit().trim() : "pcs";
                        if (subUnitSpinner != null) {
                            subUnitSpinner.post(() -> {
                                ArrayAdapter subAdapter = (ArrayAdapter) subUnitSpinner.getAdapter();
                                if (subAdapter != null) {
                                    int subPos = subAdapter.getPosition(savedSubUnit);
                                    if (subPos >= 0) subUnitSpinner.setSelection(subPos);
                                }
                            });
                        }

                        // Load Image
                        selectedImagePath = (p.getImagePath() != null && !p.getImagePath().isEmpty()) ? p.getImagePath() : p.getImageUrl();
                        if (selectedImagePath != null && !isFinishing()) {
                            Glide.with(EditProduct.this).load(selectedImagePath).diskCacheStrategy(DiskCacheStrategy.ALL).into(btnEditPhoto);
                        }

                        // Refresh the UI text views once data is loaded
                        updateStockAlertsDisplay();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(EditProduct.this, "Error: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void showDeleteMetadataDialog(String title, String collection, String fieldName) {
        FirebaseFirestore.getInstance().collection(collection)
                .whereEqualTo("ownerAdminId", currentUserId)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<String> items = new ArrayList<>();
                    Map<String, String> docMap = new HashMap<>();

                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        String name = doc.getString(fieldName);
                        if (name != null) {
                            items.add(name);
                            docMap.put(name, doc.getId());
                        }
                    }

                    if (items.isEmpty()) {
                        Toast.makeText(this, "No custom items to delete.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    new AlertDialog.Builder(this)
                            .setTitle(title)
                            .setItems(items.toArray(new String[0]), (dialog, which) -> {
                                String selectedName = items.get(which);
                                String docId = docMap.get(selectedName);

                                new AlertDialog.Builder(this)
                                        .setTitle("Confirm Delete")
                                        .setMessage("Are you sure you want to delete '" + selectedName + "'? This cannot be undone.")
                                        .setPositiveButton("Delete", (d, w) -> {
                                            FirebaseFirestore.getInstance().collection(collection).document(docId)
                                                    .delete()
                                                    .addOnSuccessListener(aVoid -> {
                                                        Toast.makeText(this, "Deleted successfully", Toast.LENGTH_SHORT).show();
                                                        setupDynamicCategoryDropdowns(); // Refresh the dropdown list
                                                    });
                                        })
                                        .setNegativeButton("Cancel", null)
                                        .show();
                            })
                            .setNegativeButton("Close", null)
                            .show();
                });
    }

    private void attemptEdit() {
        String name = productNameET.getText().toString().trim();
        String type = productTypeET.getText().toString().trim();
        String line = productLineET.getText().toString().trim();

        if (name.isEmpty() || type.isEmpty()) {
            Toast.makeText(this, "Name and Type are required", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double cost = Double.parseDouble(costPriceET.getText().toString().isEmpty() ? "0" : costPriceET.getText().toString());
            int qty = Integer.parseInt(quantityET.getText().toString().isEmpty() ? "0" : quantityET.getText().toString());
            int ppu = Integer.parseInt(etPiecesPerUnit.getText().toString().isEmpty() ? "1" : etPiecesPerUnit.getText().toString());

            if (existingProductToEdit == null) existingProductToEdit = new Product();

            // Calculate and lock in the levels when saving
            int reorderLevel = (int) Math.ceil(qty * 0.20);
            int criticalLevel = (int) Math.ceil(qty * 0.05);
            if (criticalLevel == 0 && qty > 0) criticalLevel = 1;

            existingProductToEdit.setProductName(name);
            existingProductToEdit.setCategoryName(type);
            existingProductToEdit.setProductLine(line);
            existingProductToEdit.setCostPrice(cost);
            existingProductToEdit.setQuantity(qty);
            existingProductToEdit.setReorderLevel(reorderLevel);
            existingProductToEdit.setCriticalLevel(criticalLevel);
            existingProductToEdit.setPiecesPerUnit(ppu > 0 ? ppu : 1);

            // Added .trim() to clean the main unit
            String mainUnit = unitSpinner.getSelectedItem() != null ? unitSpinner.getSelectedItem().toString().trim() : "pcs";
            existingProductToEdit.setUnit(mainUnit);

            if (layoutPiecesPerUnit != null && layoutPiecesPerUnit.getVisibility() == View.VISIBLE && subUnitSpinner != null && subUnitSpinner.getSelectedItem() != null) {
                existingProductToEdit.setSalesUnit(subUnitSpinner.getSelectedItem().toString().trim());
            } else {
                existingProductToEdit.setSalesUnit(mainUnit);
            }

            String expiryStr = expiryDateET.getText().toString().trim();
            if (!expiryStr.isEmpty()) {
                Date d = expiryFormat.parse(expiryStr);
                if (d != null) existingProductToEdit.setExpiryDate(d.getTime());
            } else {
                existingProductToEdit.setExpiryDate(0L);
            }

            productRepository.updateProduct(existingProductToEdit, selectedImagePath, new ProductRepository.OnProductUpdatedListener() {
                @Override
                public void onProductUpdated() {
                    runOnUiThread(() -> {
                        Toast.makeText(EditProduct.this, "Updated successfully", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(EditProduct.this, "Update Error: " + error, Toast.LENGTH_SHORT).show());
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_SHORT).show();
        }
    }

    // Helper methods for Adapters and Image Picking
    private void setupImagePickers() {
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) { selectedImagePath = uri.toString(); btnEditPhoto.setImageURI(uri); }
            }
        });
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) openImagePicker();
        });
    }

    private void tryPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) openImagePicker();
        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) openImagePicker();
        else permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void showExpiryDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            expiryCalendar.set(y, m, d);
            expiryDateET.setText(expiryFormat.format(expiryCalendar.getTime()));
        }, expiryCalendar.get(Calendar.YEAR), expiryCalendar.get(Calendar.MONTH), expiryCalendar.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void loadInventoryForCalculations() {
        productRepository.getAllProducts().observe(this, products -> {
            inventoryProducts.clear();
            if (products != null) {
                for (Product p : products) if (p.isActive() && !"Menu".equals(p.getProductType())) inventoryProducts.add(p);
            }
        });
    }

    private void setupDynamicCategoryDropdowns() {
        // We only want to load this once when the activity starts
        productRepository.getAllProducts().observe(this, products -> {
            java.util.Set<String> uniqueCategories = new java.util.HashSet<>();
            java.util.Set<String> uniqueTypes = new java.util.HashSet<>();

            // 1. Add default fallbacks just in case the database is empty
            uniqueCategories.add("Food");
            uniqueCategories.add("Drinks");
            uniqueCategories.add("Add-ons");

            uniqueTypes.add("Menu");
            uniqueTypes.add("Raw");
            uniqueTypes.add("Finished");
            uniqueTypes.add("Packaging");

            // 2. Extract dynamically from database
            if (products != null) {
                for (Product p : products) {
                    // Extract existing Product Lines (Categories like "Beverages")
                    if (p.getProductLine() != null && !p.getProductLine().trim().isEmpty()) {
                        uniqueCategories.add(p.getProductLine());
                    }
                    // Extract existing Product Types
                    if (p.getCategoryName() != null && !p.getCategoryName().trim().isEmpty()) {
                        uniqueTypes.add(p.getCategoryName());
                    }
                }
            }

            // 3. Convert to Lists and Sort Alphabetically
            List<String> categoryList = new ArrayList<>(uniqueCategories);
            java.util.Collections.sort(categoryList);

            List<String> typeList = new ArrayList<>(uniqueTypes);
            java.util.Collections.sort(typeList);

            // 4. Apply to Adapters
            ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, categoryList);
            if (productLineET != null) productLineET.setAdapter(catAdapter);

            ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, typeList);
            if (productTypeET != null) productTypeET.setAdapter(typeAdapter);
        });
    }

    private ArrayAdapter<String> getAdaptiveAdapter(String[] items) {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, items);
        return new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, list);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}