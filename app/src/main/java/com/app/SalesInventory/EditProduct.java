package com.app.SalesInventory;

import android.Manifest;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
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
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditProduct extends BaseActivity {
    private TextInputEditText productNameET, productGroupET, sellingPriceET, quantityET, costPriceET, minStockET, floorLevelET, expiryDateET;
    private Spinner unitSpinner, existingGroupSpinner;
    private Button updateBtn, cancelBtn;
    private ImageButton btnEditPhoto;

    private SwitchMaterial switchSizes, switchAddons, switchNotes, switchBOM, switchForSaleOnly;
    private LinearLayout layoutBuyingUnitQtyCritical;

    private ProductRepository productRepository;
    private String productId;
    private Product currentProduct;
    private String selectedImagePath;
    private SimpleDateFormat expiryFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private Calendar calendar = Calendar.getInstance();

    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    private String currentUserId;
    private List<Product> inventoryProducts = new ArrayList<>();

    // Configuration Lists
    private List<Map<String, Object>> savedSizes = new ArrayList<>();
    private List<Map<String, Object>> savedAddons = new ArrayList<>();
    private List<Map<String, String>> savedNotes = new ArrayList<>();
    private List<Map<String, Object>> savedBOM = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_product);

        productRepository = SalesInventoryApplication.getProductRepository();
        currentUserId = AuthManager.getInstance().getCurrentUserId();

        // Binding Views
        btnEditPhoto = findViewById(R.id.btnEditPhoto);
        productNameET = findViewById(R.id.productNameET);
        productGroupET = findViewById(R.id.productGroupET);
        existingGroupSpinner = findViewById(R.id.existingGroupSpinner);
        sellingPriceET = findViewById(R.id.sellingPriceET);
        costPriceET = findViewById(R.id.costPriceET);
        quantityET = findViewById(R.id.quantityET);
        minStockET = findViewById(R.id.minStockET);
        floorLevelET = findViewById(R.id.floorLevelET);
        expiryDateET = findViewById(R.id.expiryDateET);
        unitSpinner = findViewById(R.id.unitSpinner);

        layoutBuyingUnitQtyCritical = findViewById(R.id.layout_buying_unit_qty_critical);
        updateBtn = findViewById(R.id.updateBtn);
        cancelBtn = findViewById(R.id.cancelBtn);

        switchSizes = findViewById(R.id.switchSizes);
        switchAddons = findViewById(R.id.switchAddons);
        switchNotes = findViewById(R.id.switchNotes);
        switchBOM = findViewById(R.id.switchBOM);
        switchForSaleOnly = findViewById(R.id.switchForSaleOnly);

        String[] units = {"pcs", "ml", "L", "oz", "g", "kg", "box", "pack"};
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(unitAdapter);

        setupImagePickers();
        setupCategorySpinner();
        loadInventoryForBOM();

        switchForSaleOnly.setOnCheckedChangeListener((btn, isChecked) -> {
            updateLayoutForSelectedType();
            setupCategorySpinner();
        });

        switchSizes.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showSizesDialog(); else savedSizes.clear(); });
        switchAddons.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showAddonsDialog(); else savedAddons.clear(); });
        switchNotes.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showNotesDialog(); else savedNotes.clear(); });
        switchBOM.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showBOMDialog(); else savedBOM.clear(); });

        updateBtn.setOnClickListener(v -> updateProduct());
        cancelBtn.setOnClickListener(v -> finish());
        btnEditPhoto.setOnClickListener(v -> tryPickImage());
        expiryDateET.setOnClickListener(v -> showDatePicker());

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            productId = extras.getString("productId");
            if (productId == null) productId = extras.getString("PRODUCT_ID");
            if (productId != null) {
                loadProductData();
            }
        }
    }

    private void loadInventoryForBOM() {
        productRepository.getAllProducts().observe(this, products -> {
            inventoryProducts.clear();
            if (products != null) {
                for (Product p : products) {
                    if (p.isActive() && !"Menu".equals(p.getProductType())) {
                        inventoryProducts.add(p);
                    }
                }
            }
        });
    }

    private void setupCategorySpinner() {
        DatabaseReference categoryRef = FirebaseDatabase.getInstance().getReference("Categories");
        categoryRef.orderByChild("ownerAdminId").equalTo(currentUserId)
                .addValueEventListener(new ValueEventListener(){
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<String> groups = new ArrayList<>();
                        groups.add("Select Group");
                        boolean wantMenu = switchForSaleOnly.isChecked();

                        for (DataSnapshot child : snapshot.getChildren()) {
                            Category c = child.getValue(Category.class);
                            if (c != null && c.isActive()) {
                                if (wantMenu && "Menu".equalsIgnoreCase(c.getType())) {
                                    groups.add(c.getCategoryName());
                                } else if (!wantMenu && !"Menu".equalsIgnoreCase(c.getType())) {
                                    groups.add(c.getCategoryName());
                                }
                            }
                        }
                        ArrayAdapter<String> groupAdapter = new ArrayAdapter<>(EditProduct.this, android.R.layout.simple_spinner_item, groups);
                        existingGroupSpinner.setAdapter(groupAdapter);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });

        existingGroupSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) {
                    productGroupET.setText(parent.getItemAtPosition(position).toString());
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void loadProductData() {
        productRepository.getProductById(productId, new ProductRepository.OnProductFetchedListener() {
            @Override
            public void onProductFetched(Product product) {
                currentProduct = product;
                runOnUiThread(() -> populateFields());
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(EditProduct.this, "Error loading product", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void populateFields() {
        if (currentProduct != null) {
            productNameET.setText(currentProduct.getProductName());
            productGroupET.setText(currentProduct.getCategoryName());
            costPriceET.setText(String.valueOf(currentProduct.getCostPrice()));
            sellingPriceET.setText(String.valueOf(currentProduct.getSellingPrice()));
            quantityET.setText(String.valueOf(currentProduct.getQuantity()));
            minStockET.setText(String.valueOf(currentProduct.getReorderLevel()));
            floorLevelET.setText(String.valueOf(Math.max(1, currentProduct.getFloorLevel())));

            // Set Unit Spinner
            String currentUnit = currentProduct.getUnit();
            if (currentUnit != null) {
                String[] units = {"pcs", "ml", "L", "oz", "g", "kg", "box", "pack"};
                for (int i = 0; i < units.length; i++) {
                    if (units[i].equalsIgnoreCase(currentUnit)) {
                        unitSpinner.setSelection(i);
                        break;
                    }
                }
            }

            if (currentProduct.getExpiryDate() > 0 && currentProduct.getExpiryDate() >= System.currentTimeMillis()) {
                expiryDateET.setText(expiryFormat.format(new Date(currentProduct.getExpiryDate())));
            } else {
                expiryDateET.setText("");
            }

            // Load Configurations
            if (currentProduct.getSizesList() != null) savedSizes.addAll(currentProduct.getSizesList());
            if (currentProduct.getAddonsList() != null) savedAddons.addAll(currentProduct.getAddonsList());
            if (currentProduct.getNotesList() != null) savedNotes.addAll(currentProduct.getNotesList());
            if (currentProduct.getBomList() != null) savedBOM.addAll(currentProduct.getBomList());

            switchSizes.setChecked(!savedSizes.isEmpty());
            switchAddons.setChecked(!savedAddons.isEmpty());
            switchNotes.setChecked(!savedNotes.isEmpty());
            switchBOM.setChecked(!savedBOM.isEmpty());

            String type = currentProduct.getProductType() == null ? "" : currentProduct.getProductType();
            switchForSaleOnly.setChecked("Menu".equalsIgnoreCase(type));
            updateLayoutForSelectedType();

            // Load Image
            String imagePath = currentProduct.getImagePath();
            String imageUrl = currentProduct.getImageUrl();
            String toLoad = (imageUrl != null && !imageUrl.isEmpty()) ? imageUrl : imagePath;
            if (toLoad != null && !toLoad.isEmpty()) {
                selectedImagePath = toLoad;
                try {
                    Uri uri = Uri.parse(toLoad);
                    btnEditPhoto.setImageURI(uri);
                } catch (Exception ignored) {}
            }
        }
    }

    private void updateLayoutForSelectedType() {
        if (switchForSaleOnly.isChecked()) {
            layoutBuyingUnitQtyCritical.setVisibility(View.GONE);
            quantityET.setText(""); costPriceET.setText(""); minStockET.setText(""); floorLevelET.setText("");
        } else {
            layoutBuyingUnitQtyCritical.setVisibility(View.VISIBLE);
        }
    }

    private void updateProduct() {
        if (currentProduct == null) return;

        String name = productNameET.getText() != null ? productNameET.getText().toString().trim() : "";
        if (name.isEmpty()) { Toast.makeText(this, "Product name is required", Toast.LENGTH_SHORT).show(); return; }

        String categoryName = productGroupET.getText().toString().trim();
        if (categoryName.isEmpty()) { Toast.makeText(this, "Product group is required", Toast.LENGTH_SHORT).show(); return; }

        updateBtn.setEnabled(false);
        updateBtn.setText("Updating...");

        boolean isMenu = switchForSaleOnly.isChecked();
        checkAndCreateCategory(categoryName, isMenu);

        try {
            double sellingPrice = Double.parseDouble(sellingPriceET.getText().toString().isEmpty() ? "0" : sellingPriceET.getText().toString());

            currentProduct.setProductName(name);
            currentProduct.setCategoryName(categoryName);
            currentProduct.setCategoryId(categoryName.toLowerCase(Locale.ROOT).replace(" ", "_"));
            currentProduct.setProductType(isMenu ? "Menu" : "Inventory");
            currentProduct.setSellingPrice(sellingPrice);

            if (isMenu) {
                currentProduct.setCostPrice(0);
                currentProduct.setQuantity(0);
                currentProduct.setReorderLevel(0);
                currentProduct.setFloorLevel(1);
            } else {
                currentProduct.setCostPrice(Double.parseDouble(costPriceET.getText().toString().isEmpty() ? "0" : costPriceET.getText().toString()));
                currentProduct.setQuantity(Integer.parseInt(quantityET.getText().toString().isEmpty() ? "0" : quantityET.getText().toString()));
                currentProduct.setReorderLevel(Integer.parseInt(minStockET.getText().toString().isEmpty() ? "0" : minStockET.getText().toString()));
                currentProduct.setFloorLevel(Integer.parseInt(floorLevelET.getText().toString().isEmpty() ? "1" : floorLevelET.getText().toString()));
                currentProduct.setUnit(unitSpinner.getSelectedItem() != null ? unitSpinner.getSelectedItem().toString() : "");
            }

            String expiryStr = expiryDateET.getText().toString().trim();
            long expiry = 0L;
            if (!expiryStr.isEmpty()) {
                try {
                    Date d = expiryFormat.parse(expiryStr);
                    if (d != null) expiry = d.getTime();
                } catch (ParseException ignored) {}
            }
            currentProduct.setExpiryDate(expiry);

            if (selectedImagePath != null && !selectedImagePath.isEmpty()) {
                currentProduct.setImagePath(selectedImagePath);
                currentProduct.setImageUrl(null);
            }

            currentProduct.setSizesList(savedSizes);
            currentProduct.setAddonsList(savedAddons);
            currentProduct.setNotesList(savedNotes);
            currentProduct.setBomList(savedBOM);

            productRepository.updateProduct(currentProduct, new ProductRepository.OnProductUpdatedListener() {
                @Override
                public void onProductUpdated() {
                    runOnUiThread(() -> {
                        Toast.makeText(EditProduct.this, "Product updated successfully", Toast.LENGTH_SHORT).show();
                        navigateBackAfterUpdate();
                    });
                }
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Toast.makeText(EditProduct.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                        updateBtn.setEnabled(true);
                        updateBtn.setText("Update Product");
                    });
                }
            });

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            updateBtn.setEnabled(true);
            updateBtn.setText("Update Product");
        }
    }

    private void checkAndCreateCategory(String name, boolean isMenu) {
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Categories");
        ref.orderByChild("ownerAdminId").equalTo(currentUserId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean exists = false;
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Category c = ds.getValue(Category.class);
                    if (c != null && name.equalsIgnoreCase(c.getCategoryName())) {
                        exists = true; break;
                    }
                }
                if (!exists) {
                    String id = ref.push().getKey();
                    Category newCat = new Category();
                    newCat.setCategoryId(id);
                    newCat.setCategoryName(name);
                    newCat.setOwnerAdminId(currentUserId);
                    newCat.setType(isMenu ? "Menu" : "Inventory");
                    newCat.setActive(true);
                    if (id != null) ref.child(id).setValue(newCat);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void navigateBackAfterUpdate() {
        String type = currentProduct.getProductType() == null ? "" : currentProduct.getProductType();
        Intent intent;
        if ("Menu".equalsIgnoreCase(type)) {
            intent = new Intent(EditProduct.this, SellList.class);
        } else {
            intent = new Intent(EditProduct.this, Inventory.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    // ========================================================================
    // DIALOG BUILDERS (Identical to AddProductActivity)
    // ========================================================================
    private void showInventorySelectionDialog(TextView targetTextView) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_inventory_selection, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        EditText etSearch = view.findViewById(R.id.etSearchInventory);
        Spinner spinnerFilter = view.findViewById(R.id.spinnerFilterCategory);
        ListView lvItems = view.findViewById(R.id.lvInventoryItems);
        Button btnClose = view.findViewById(R.id.btnCloseSelection);

        List<String> categories = new ArrayList<>();
        categories.add("All Categories");
        for (Product p : inventoryProducts) {
            String cat = p.getCategoryName() != null && !p.getCategoryName().isEmpty() ? p.getCategoryName() : "Uncategorized";
            if (!categories.contains(cat)) categories.add(cat);
        }
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(catAdapter);

        List<Product> filteredList = new ArrayList<>(inventoryProducts);
        ArrayAdapter<Product> listAdapter = new ArrayAdapter<Product>(this, android.R.layout.simple_list_item_1, filteredList) {
            @NonNull
            @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView tv = v.findViewById(android.R.id.text1);
                Product p = getItem(position);
                if (p != null) {
                    tv.setText(p.getProductName() + "  (" + p.getQuantity() + " " + p.getUnit() + " left)");
                }
                return v;
            }
        };
        lvItems.setAdapter(listAdapter);

        Runnable applyFilter = () -> {
            String query = etSearch.getText().toString().toLowerCase().trim();
            String cat = spinnerFilter.getSelectedItem() != null ? spinnerFilter.getSelectedItem().toString() : "All Categories";
            filteredList.clear();
            for (Product p : inventoryProducts) {
                boolean matchesSearch = p.getProductName().toLowerCase().contains(query);
                boolean matchesCat = cat.equals("All Categories") || cat.equals(p.getCategoryName());
                if (matchesSearch && matchesCat) {
                    filteredList.add(p);
                }
            }
            listAdapter.notifyDataSetChanged();
        };

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { applyFilter.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        spinnerFilter.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) { applyFilter.run(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        lvItems.setOnItemClickListener((parent, view1, position, id) -> {
            Product selected = filteredList.get(position);
            targetTextView.setText(selected.getProductName());
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void showBOMDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_config_bom, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).setCancelable(false).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        LinearLayout containerRows = view.findViewById(R.id.containerRows);
        ImageButton btnAddRow = view.findViewById(R.id.btnAddRow);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSave = view.findViewById(R.id.btnSave);

        String[] units = {"pcs", "ml", "L", "oz", "g", "kg", "box", "pack"};
        ArrayAdapter<String> rowUnitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
        rowUnitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_bom, null);
            TextView tvMaterial = row.findViewById(R.id.tvRawMaterial);
            if (tvMaterial != null) tvMaterial.setOnClickListener(v -> showInventorySelectionDialog(tvMaterial));
            Spinner spinnerUnit = row.findViewById(R.id.spinnerUnit);
            if (spinnerUnit != null) spinnerUnit.setAdapter(rowUnitAdapter);
            View btnDelete = row.findViewById(R.id.btnDelete);
            if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedBOM.isEmpty()) { addRow.run(); }
        else {
            for (Map<String, Object> bom : savedBOM) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_bom, null);
                TextView tvMaterial = row.findViewById(R.id.tvRawMaterial);
                if (tvMaterial != null) {
                    tvMaterial.setText((String) bom.get("materialName"));
                    tvMaterial.setOnClickListener(v -> showInventorySelectionDialog(tvMaterial));
                }
                EditText etQty = row.findViewById(R.id.etDeductQty);
                if (etQty != null) etQty.setText(String.valueOf(bom.get("quantity")));
                Spinner spinnerUnit = row.findViewById(R.id.spinnerUnit);
                if (spinnerUnit != null) {
                    spinnerUnit.setAdapter(rowUnitAdapter);
                    String savedUnit = (String) bom.get("unit");
                    if (savedUnit != null) {
                        for (int i = 0; i < units.length; i++) {
                            if (units[i].equalsIgnoreCase(savedUnit)) {
                                spinnerUnit.setSelection(i);
                                break;
                            }
                        }
                    }
                }
                View btnDelete = row.findViewById(R.id.btnDelete);
                if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            }
        }

        btnAddRow.setOnClickListener(v -> addRow.run());
        btnCancel.setOnClickListener(v -> { if (savedBOM.isEmpty()) switchBOM.setChecked(false); dialog.dismiss(); });
        btnSave.setOnClickListener(v -> {
            savedBOM.clear();
            for (int i = 0; i < containerRows.getChildCount(); i++) {
                View row = containerRows.getChildAt(i);
                TextView tvMat = row.findViewById(R.id.tvRawMaterial);
                EditText etQty = row.findViewById(R.id.etDeductQty);
                Spinner spinUnit = row.findViewById(R.id.spinnerUnit);

                if (tvMat != null && etQty != null && spinUnit != null) {
                    String materialName = tvMat.getText().toString().trim();
                    String qtyStr = etQty.getText().toString().trim();
                    String unitStr = spinUnit.getSelectedItem() != null ? spinUnit.getSelectedItem().toString() : "pcs";

                    if (!materialName.isEmpty() && !materialName.contains("Select Item")) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("materialName", materialName);
                        map.put("quantity", qtyStr.isEmpty() ? 0.0 : Double.parseDouble(qtyStr));
                        map.put("unit", unitStr);
                        savedBOM.add(map);
                    }
                }
            }
            if (savedBOM.isEmpty()) switchBOM.setChecked(false);
            else Toast.makeText(this, "Recipe Saved!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showSizesDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_config_sizes, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).setCancelable(false).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        LinearLayout containerRows = view.findViewById(R.id.containerRows);
        ImageButton btnAddRow = view.findViewById(R.id.btnAddRow);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSave = view.findViewById(R.id.btnSave);

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_size, null);
            View btnDelete = row.findViewById(R.id.btnDelete);
            if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedSizes.isEmpty()) addRow.run();
        else {
            for (Map<String, Object> size : savedSizes) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_size, null);
                EditText etName = row.findViewById(R.id.etSizeName);
                EditText etPrice = row.findViewById(R.id.etSizePrice);
                if (etName != null) etName.setText((String) size.get("name"));
                if (etPrice != null) etPrice.setText(String.valueOf(size.get("price")));
                View btnDelete = row.findViewById(R.id.btnDelete);
                if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            }
        }

        btnAddRow.setOnClickListener(v -> addRow.run());
        btnCancel.setOnClickListener(v -> { if (savedSizes.isEmpty()) switchSizes.setChecked(false); dialog.dismiss(); });
        btnSave.setOnClickListener(v -> {
            savedSizes.clear();
            for (int i = 0; i < containerRows.getChildCount(); i++) {
                View row = containerRows.getChildAt(i);
                EditText etName = row.findViewById(R.id.etSizeName);
                EditText etPrice = row.findViewById(R.id.etSizePrice);

                if (etName != null && etPrice != null) {
                    String name = etName.getText().toString().trim();
                    String priceStr = etPrice.getText().toString().trim();
                    if (!name.isEmpty()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", name);
                        map.put("price", priceStr.isEmpty() ? 0.0 : Double.parseDouble(priceStr));
                        savedSizes.add(map);
                    }
                }
            }
            if (savedSizes.isEmpty()) switchSizes.setChecked(false);
            else Toast.makeText(this, "Sizes saved!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showAddonsDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_config_addons, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).setCancelable(false).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        LinearLayout containerRows = view.findViewById(R.id.containerRows);
        ImageButton btnAddRow = view.findViewById(R.id.btnAddRow);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSave = view.findViewById(R.id.btnSave);

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_add_ons, null);
            View btnDelete = row.findViewById(R.id.btnDelete);
            if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedAddons.isEmpty()) { addRow.run(); }
        else {
            for (Map<String, Object> addon : savedAddons) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_add_ons, null);
                EditText etName = row.findViewById(R.id.etAddonName);
                EditText etPrice = row.findViewById(R.id.etAddonPrice);
                if (etName != null) etName.setText((String) addon.get("name"));
                if (etPrice != null) etPrice.setText(String.valueOf(addon.get("price")));
                View btnDelete = row.findViewById(R.id.btnDelete);
                if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            }
        }

        btnAddRow.setOnClickListener(v -> addRow.run());
        btnCancel.setOnClickListener(v -> { if (savedAddons.isEmpty()) switchAddons.setChecked(false); dialog.dismiss(); });
        btnSave.setOnClickListener(v -> {
            savedAddons.clear();
            for (int i = 0; i < containerRows.getChildCount(); i++) {
                View row = containerRows.getChildAt(i);
                EditText etName = row.findViewById(R.id.etAddonName);
                EditText etPrice = row.findViewById(R.id.etAddonPrice);

                if (etName != null && etPrice != null) {
                    String name = etName.getText().toString().trim();
                    String priceStr = etPrice.getText().toString().trim();
                    if (!name.isEmpty()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", name);
                        map.put("price", priceStr.isEmpty() ? 0.0 : Double.parseDouble(priceStr));
                        savedAddons.add(map);
                    }
                }
            }
            if (savedAddons.isEmpty()) switchAddons.setChecked(false);
            else Toast.makeText(this, "Add-ons saved!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showNotesDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_config_notes, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).setCancelable(false).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        LinearLayout containerRows = view.findViewById(R.id.containerRows);
        ImageButton btnAddRow = view.findViewById(R.id.btnAddRow);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSave = view.findViewById(R.id.btnSave);

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_note, null);
            View btnDelete = row.findViewById(R.id.btnDelete);
            if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedNotes.isEmpty()) addRow.run();
        else {
            for (Map<String, String> note : savedNotes) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_note, null);
                EditText etType = row.findViewById(R.id.etNoteType);
                EditText etVal = row.findViewById(R.id.etNoteValue);
                if (etType != null) etType.setText(note.get("type"));
                if (etVal != null) etVal.setText(note.get("value"));
                View btnDelete = row.findViewById(R.id.btnDelete);
                if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            }
        }

        btnAddRow.setOnClickListener(v -> addRow.run());
        btnCancel.setOnClickListener(v -> { if (savedNotes.isEmpty()) switchNotes.setChecked(false); dialog.dismiss(); });
        btnSave.setOnClickListener(v -> {
            savedNotes.clear();
            for (int i = 0; i < containerRows.getChildCount(); i++) {
                View row = containerRows.getChildAt(i);
                EditText etType = row.findViewById(R.id.etNoteType);
                EditText etVal = row.findViewById(R.id.etNoteValue);

                if (etType != null && etVal != null) {
                    String type = etType.getText().toString().trim();
                    String value = etVal.getText().toString().trim();
                    if (!type.isEmpty() || !value.isEmpty()) {
                        Map<String, String> map = new HashMap<>();
                        map.put("type", type);
                        map.put("value", value);
                        savedNotes.add(map);
                    }
                }
            }
            if (savedNotes.isEmpty()) switchNotes.setChecked(false);
            else Toast.makeText(this, "Notes saved!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void setupImagePickers() {
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    selectedImagePath = uri.toString();
                    btnEditPhoto.setImageURI(uri);
                }
            }
        });
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) openImagePicker();
            else Toast.makeText(this, "Permission required", Toast.LENGTH_SHORT).show();
        });
    }

    private void tryPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) openImagePicker();
        else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) openImagePicker();
            else permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void showDatePicker() {
        long initial = currentProduct != null ? currentProduct.getExpiryDate() : 0L;
        if (initial > 0 && initial >= System.currentTimeMillis()) {
            calendar.setTimeInMillis(initial);
        } else {
            calendar.setTimeInMillis(System.currentTimeMillis());
        }
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            calendar.set(Calendar.YEAR, y);
            calendar.set(Calendar.MONTH, m);
            calendar.set(Calendar.DAY_OF_MONTH, d);
            expiryDateET.setText(expiryFormat.format(calendar.getTime()));
        }, year, month, day);
        dialog.getDatePicker().setMinDate(System.currentTimeMillis());
        dialog.show();
    }
}