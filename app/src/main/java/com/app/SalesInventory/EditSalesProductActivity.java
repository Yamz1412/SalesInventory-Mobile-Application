package com.app.SalesInventory;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EditSalesProductActivity extends BaseActivity {

    private ImageButton btnEditPhoto;
    private TextInputEditText editProductNameET, editSellingPriceET;
    private Button btnSaveEdit, btnCancelEdit;

    private MaterialButton btnEditVariants, btnEditSizes, btnEditAddons, btnEditNotes, btnEditBOM;

    private ProductRepository productRepository;
    private Product currentProduct;
    private String productId;

    private String selectedImagePath;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    private List<Product> inventoryProducts = new ArrayList<>();

    private List<Map<String, Object>> savedSizes = new ArrayList<>();
    private List<Map<String, Object>> savedAddons = new ArrayList<>();
    private List<Map<String, String>> savedNotes = new ArrayList<>();
    private List<Map<String, Object>> savedBOM = new ArrayList<>();
    private List<Map<String, Object>> savedVariants = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_sales_product);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        productRepository = SalesInventoryApplication.getProductRepository();

        btnEditPhoto = findViewById(R.id.btnEditPhoto);
        editProductNameET = findViewById(R.id.editProductNameET);
        editSellingPriceET = findViewById(R.id.editSellingPriceET);

        btnEditVariants = findViewById(R.id.btnEditVariants);
        btnEditSizes = findViewById(R.id.btnEditSizes);
        btnEditAddons = findViewById(R.id.btnEditAddons);
        btnEditNotes = findViewById(R.id.btnEditNotes);
        btnEditBOM = findViewById(R.id.btnEditBOM);

        btnSaveEdit = findViewById(R.id.btnSaveEdit);
        btnCancelEdit = findViewById(R.id.btnCancelEdit);

        setupImagePickers();
        loadInventoryForConfigs();

        productId = getIntent().getStringExtra("PRODUCT_ID");
        if (productId != null) {
            loadProductData();
        } else {
            Toast.makeText(this, "Error loading product", Toast.LENGTH_SHORT).show();
            finish();
        }

        btnEditVariants.setOnClickListener(v -> showVariantsDialog());
        btnEditSizes.setOnClickListener(v -> showSizesDialog());
        btnEditAddons.setOnClickListener(v -> showAddonsDialog());
        btnEditNotes.setOnClickListener(v -> showNotesDialog());
        btnEditBOM.setOnClickListener(v -> showBOMDialog());

        btnEditPhoto.setOnClickListener(v -> tryPickImage());
        btnCancelEdit.setOnClickListener(v -> finish());
        btnSaveEdit.setOnClickListener(v -> saveProductChanges());
    }

    private void loadProductData() {
        productRepository.getProductById(productId, new ProductRepository.OnProductFetchedListener() {
            @Override
            public void onProductFetched(Product product) {
                runOnUiThread(() -> {
                    currentProduct = product;
                    editProductNameET.setText(product.getProductName());
                    editSellingPriceET.setText(String.valueOf(product.getSellingPrice()));

                    if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
                        Glide.with(EditSalesProductActivity.this).load(product.getImageUrl()).into(btnEditPhoto);
                    } else if (product.getImagePath() != null) {
                        Glide.with(EditSalesProductActivity.this).load(product.getImagePath()).into(btnEditPhoto);
                    }

                    // Load Lists into memory immediately
                    if (product.getVariantsList() != null) savedVariants = new ArrayList<>(product.getVariantsList());
                    if (product.getSizesList() != null) savedSizes = new ArrayList<>(product.getSizesList());
                    if (product.getAddonsList() != null) savedAddons = new ArrayList<>(product.getAddonsList());
                    if (product.getNotesList() != null) savedNotes = new ArrayList<>(product.getNotesList());
                    if (product.getBomList() != null) savedBOM = new ArrayList<>(product.getBomList());

                    updateButtonLabels();
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(EditSalesProductActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
        });
    }

    private void updateButtonLabels() {
        btnEditVariants.setText("Edit Variants (" + savedVariants.size() + ")");
        btnEditSizes.setText("Edit Sizes (" + savedSizes.size() + ")");
        btnEditAddons.setText("Edit Add-ons (" + savedAddons.size() + ")");
        btnEditNotes.setText("Edit Custom Notes (" + savedNotes.size() + ")");
        btnEditBOM.setText("Edit Recipe / Ingredients (" + savedBOM.size() + ")");
    }

    private void saveProductChanges() {
        if (currentProduct == null) return;

        String newName = editProductNameET.getText().toString().trim();
        String newPriceStr = editSellingPriceET.getText().toString().trim();

        if (newName.isEmpty()) {
            Toast.makeText(this, "Name is required", Toast.LENGTH_SHORT).show();
            return;
        }

        currentProduct.setProductName(newName);
        try { currentProduct.setSellingPrice(Double.parseDouble(newPriceStr)); } catch (Exception e) {}

        // Save exactly what is currently loaded in memory from the dialogs
        currentProduct.setVariantsList(savedVariants);
        currentProduct.setSizesList(savedSizes);
        currentProduct.setAddonsList(savedAddons);
        currentProduct.setNotesList(savedNotes);
        currentProduct.setBomList(savedBOM);

        if (selectedImagePath != null) {
            currentProduct.setImagePath(selectedImagePath);
        }

        productRepository.updateProduct(currentProduct, selectedImagePath, new ProductRepository.OnProductUpdatedListener() {
            @Override
            public void onProductUpdated() {
                runOnUiThread(() -> {
                    Toast.makeText(EditSalesProductActivity.this, "Product Updated Successfully!", Toast.LENGTH_SHORT).show();
                    finish(); // Go back to SellList, it will automatically update because of LiveData
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(EditSalesProductActivity.this, "Update Failed: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadInventoryForConfigs() {
        productRepository.getAllProducts().observe(this, products -> {
            inventoryProducts.clear();
            if (products != null) {
                for (Product p : products) {
                    if (p.isActive() && !"Menu".equals(p.getProductType())) inventoryProducts.add(p);
                }
            }
        });
    }

    // =========================================================================
    // DIALOG METHODS (Buttons now just edit/add items without switches)
    // =========================================================================

    private void showVariantsDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_config_variants, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).setCancelable(false).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        LinearLayout containerRows = view.findViewById(R.id.containerRows);
        ImageButton btnAddRow = view.findViewById(R.id.btnAddRow);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSave = view.findViewById(R.id.btnSave);

        List<String> inventoryNames = new ArrayList<>();
        for (Product p : inventoryProducts) inventoryNames.add(p.getProductName());
        ArrayAdapter<String> autoCompleteAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, inventoryNames);

        String[] units = {"pcs", "ml", "L", "oz", "g", "kg", "box", "pack"};
        ArrayAdapter<String> rowUnitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
        rowUnitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_variant, null);
            AutoCompleteTextView actvItem = row.findViewById(R.id.actvVariantItem);
            actvItem.setAdapter(autoCompleteAdapter);
            actvItem.setOnClickListener(v -> actvItem.showDropDown());
            actvItem.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) actvItem.showDropDown(); });
            Spinner spinnerUnit = row.findViewById(R.id.spinnerVariantUnit);
            spinnerUnit.setAdapter(rowUnitAdapter);
            row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedVariants.isEmpty()) addRow.run();
        else {
            for (Map<String, Object> variant : savedVariants) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_variant, null);
                AutoCompleteTextView actvItem = row.findViewById(R.id.actvVariantItem);
                actvItem.setAdapter(autoCompleteAdapter);
                actvItem.setOnClickListener(v -> actvItem.showDropDown());
                actvItem.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) actvItem.showDropDown(); });
                actvItem.setText((String) variant.get("variantName"), false);
                EditText etQty = row.findViewById(R.id.etVariantQty);
                etQty.setText(String.valueOf(variant.get("deductQty")));
                Spinner spinnerUnit = row.findViewById(R.id.spinnerVariantUnit);
                spinnerUnit.setAdapter(rowUnitAdapter);
                String savedUnit = (String) variant.get("unit");
                if (savedUnit != null) {
                    for (int i = 0; i < units.length; i++) {
                        if (units[i].equalsIgnoreCase(savedUnit)) { spinnerUnit.setSelection(i); break; }
                    }
                }
                row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            }
        }

        btnAddRow.setOnClickListener(v -> addRow.run());
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            savedVariants.clear();
            for (int i = 0; i < containerRows.getChildCount(); i++) {
                View row = containerRows.getChildAt(i);
                AutoCompleteTextView actvItem = row.findViewById(R.id.actvVariantItem);
                EditText etQty = row.findViewById(R.id.etVariantQty);
                Spinner spinnerUnit = row.findViewById(R.id.spinnerVariantUnit);
                String itemName = actvItem.getText().toString().trim();
                String qtyStr = etQty.getText().toString().trim();
                String unitStr = spinnerUnit.getSelectedItem() != null ? spinnerUnit.getSelectedItem().toString() : "pcs";
                if (!itemName.isEmpty()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("variantName", itemName);
                    map.put("linkedMaterial", itemName);
                    map.put("deductQty", qtyStr.isEmpty() ? 0.0 : Double.parseDouble(qtyStr));
                    map.put("unit", unitStr);
                    savedVariants.add(map);
                }
            }
            updateButtonLabels();
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

        List<String> inventoryNames = new ArrayList<>();
        for (Product p : inventoryProducts) inventoryNames.add(p.getProductName());
        ArrayAdapter<String> autoCompleteAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, inventoryNames);

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_size, null);
            AutoCompleteTextView actvLinked = row.findViewById(R.id.actvLinkedInventory);
            if (actvLinked != null) {
                actvLinked.setAdapter(autoCompleteAdapter);
                actvLinked.setOnClickListener(v -> actvLinked.showDropDown());
                actvLinked.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) actvLinked.showDropDown(); });
            }
            row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedSizes.isEmpty()) addRow.run();
        else {
            for (Map<String, Object> size : savedSizes) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_size, null);
                EditText etName = row.findViewById(R.id.etSizeName);
                EditText etPrice = row.findViewById(R.id.etSizePrice);
                AutoCompleteTextView actvLinked = row.findViewById(R.id.actvLinkedInventory);
                if (actvLinked != null) {
                    actvLinked.setAdapter(autoCompleteAdapter);
                    actvLinked.setOnClickListener(v -> actvLinked.showDropDown());
                    actvLinked.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) actvLinked.showDropDown(); });
                    String linked = (String) size.get("linkedMaterial");
                    if (linked != null) actvLinked.setText(linked, false);
                }
                if (etName != null) etName.setText((String) size.get("name"));
                if (etPrice != null) etPrice.setText(String.valueOf(size.get("price")));
                row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            }
        }

        btnAddRow.setOnClickListener(v -> addRow.run());
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnSave.setOnClickListener(v -> {
            savedSizes.clear();
            for (int i = 0; i < containerRows.getChildCount(); i++) {
                View row = containerRows.getChildAt(i);
                EditText etName = row.findViewById(R.id.etSizeName);
                EditText etPrice = row.findViewById(R.id.etSizePrice);
                AutoCompleteTextView actvLinked = row.findViewById(R.id.actvLinkedInventory);
                if (etName != null && etPrice != null) {
                    String name = etName.getText().toString().trim();
                    String priceStr = etPrice.getText().toString().trim();
                    String linkedMaterial = actvLinked != null ? actvLinked.getText().toString().trim() : "";
                    if (!name.isEmpty()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", name);
                        map.put("price", priceStr.isEmpty() ? 0.0 : Double.parseDouble(priceStr));
                        if (!linkedMaterial.isEmpty()) {
                            map.put("linkedMaterial", linkedMaterial);
                            map.put("deductQty", 1.0);
                            map.put("unit", "pcs");
                        }
                        savedSizes.add(map);
                    }
                }
            }
            updateButtonLabels();
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
            row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedAddons.isEmpty()) addRow.run();
        else {
            for (Map<String, Object> addon : savedAddons) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_add_ons, null);
                EditText etName = row.findViewById(R.id.etAddonName);
                EditText etPrice = row.findViewById(R.id.etAddonPrice);
                if (etName != null) etName.setText((String) addon.get("name"));
                if (etPrice != null) etPrice.setText(String.valueOf(addon.get("price")));
                row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            }
        }

        btnAddRow.setOnClickListener(v -> addRow.run());
        btnCancel.setOnClickListener(v -> dialog.dismiss());
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
            updateButtonLabels();
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
            row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
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
                row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            }
        }

        btnAddRow.setOnClickListener(v -> addRow.run());
        btnCancel.setOnClickListener(v -> dialog.dismiss());
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
            updateButtonLabels();
            dialog.dismiss();
        });
        dialog.show();
    }

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
            @NonNull @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView tv = v.findViewById(android.R.id.text1);
                Product p = getItem(position);
                if (p != null) tv.setText(p.getProductName() + "  (" + p.getQuantity() + " " + p.getUnit() + " left)");
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
                if (matchesSearch && matchesCat) filteredList.add(p);
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
            row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedBOM.isEmpty()) addRow.run();
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
                            if (units[i].equalsIgnoreCase(savedUnit)) { spinnerUnit.setSelection(i); break; }
                        }
                    }
                }
                row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            }
        }

        btnAddRow.setOnClickListener(v -> addRow.run());
        btnCancel.setOnClickListener(v -> dialog.dismiss());
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
            updateButtonLabels();
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

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}