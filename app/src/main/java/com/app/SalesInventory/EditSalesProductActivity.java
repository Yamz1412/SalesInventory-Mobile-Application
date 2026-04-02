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
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditSalesProductActivity extends BaseActivity {
    private ImageButton btnEditPhoto;
    private TextInputEditText productNameET, sellingPriceET, costPriceET;
    private AutoCompleteTextView productTypeET;
    private Button btnSaveEdit, btnCancelEdit;

    private SwitchMaterial switchSizes, switchAddons, switchNotes, switchBOM;

    private List<Map<String, Object>> savedSizes = new ArrayList<>();
    private List<Map<String, Object>> savedAddons = new ArrayList<>();
    private List<Map<String, String>> savedNotes = new ArrayList<>();
    private List<Map<String, Object>> savedBOM = new ArrayList<>();

    private ProductRepository productRepository;
    private String selectedImagePath;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    private List<Product> inventoryProducts = new ArrayList<>();
    private String editProductId;
    private Product existingProductToEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_sales_product);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        // Safe ID retrieval logic
        editProductId = getIntent().getStringExtra("PRODUCT_ID");
        if (editProductId == null) editProductId = getIntent().getStringExtra("productId");
        if (editProductId == null) editProductId = getIntent().getStringExtra("product_id");

        if (editProductId == null) {
            Toast.makeText(this, "Product ID missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        productRepository = SalesInventoryApplication.getProductRepository();

        btnEditPhoto = findViewById(R.id.btnEditPhoto);
        productNameET = findViewById(R.id.productNameET);
        productTypeET = findViewById(R.id.productTypeET);
        costPriceET = findViewById(R.id.costPriceET);
        sellingPriceET = findViewById(R.id.sellingPriceET);

        switchSizes = findViewById(R.id.switchSizes);
        switchAddons = findViewById(R.id.switchAddons);
        switchNotes = findViewById(R.id.switchNotes);
        switchBOM = findViewById(R.id.switchBOM);

        btnSaveEdit = findViewById(R.id.btnSaveEdit);
        btnCancelEdit = findViewById(R.id.btnCancelEdit);

        setupImagePickers();
        loadInventoryForCalculations();

        if (switchSizes != null) switchSizes.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showSizesDialog(); else savedSizes.clear(); });
        if (switchAddons != null) switchAddons.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showAddonsDialog(); else savedAddons.clear(); });
        if (switchNotes != null) switchNotes.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showNotesDialog(); else savedNotes.clear(); });
        if (switchBOM != null) switchBOM.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showBOMDialog(); else savedBOM.clear(); });

        btnEditPhoto.setOnClickListener(v -> tryPickImage());
        btnCancelEdit.setOnClickListener(v -> finish());
        btnSaveEdit.setOnClickListener(v -> attemptEdit());

        loadProductData();
    }

    private void loadInventoryForCalculations() {
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

    private Product findInventoryProduct(String name) {
        for (Product p : inventoryProducts) {
            if (p.getProductName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private void updateMainCostFromBOM() {
        if (savedBOM == null) return;
        double totalCost = 0.0;
        for (Map<String, Object> bom : savedBOM) {
            String matName = (String) bom.get("materialName");
            double bQty = 0;
            try { bQty = Double.parseDouble(bom.get("quantity").toString()); } catch (Exception ignored) {}
            String bUnit = (String) bom.get("unit");

            Product mat = findInventoryProduct(matName);
            if (mat != null && mat.getQuantity() > 0) {
                int ppu = mat.getPiecesPerUnit() > 0 ? mat.getPiecesPerUnit() : 1;
                String invUnit = mat.getUnit() != null ? mat.getUnit() : "pcs";

                Object[] conversion = UnitConverterUtil.convertBaseInventoryUnit(mat.getQuantity(), invUnit, bUnit, ppu);
                double convertedInvQty = (double) conversion[0];
                String newInvUnit = (String) conversion[1];

                double deductionAmount = UnitConverterUtil.calculateDeductionAmount(bQty, newInvUnit, bUnit, ppu);

                double unitCost = mat.getCostPrice() / convertedInvQty;
                totalCost += (deductionAmount * unitCost);
            }
        }
        costPriceET.setText(String.format(Locale.US, "%.2f", totalCost));
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

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_bom, null);
            TextView tvMaterial = row.findViewById(R.id.tvRawMaterial);
            Spinner spinnerUnit = row.findViewById(R.id.spinnerUnit);
            if (spinnerUnit != null) spinnerUnit.setAdapter(rowUnitAdapter);

            if (tvMaterial != null) {
                tvMaterial.setOnClickListener(v -> showInventorySelectionDialog(tvMaterial, () -> {}));
            }

            View btnDelete = row.findViewById(R.id.btnDelete);
            if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedBOM.isEmpty()) addRow.run();
        else {
            for (Map<String, Object> bom : savedBOM) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_bom, null);
                TextView tvMaterial = row.findViewById(R.id.tvRawMaterial);
                EditText etQty = row.findViewById(R.id.etDeductQty);
                Spinner spinnerUnit = row.findViewById(R.id.spinnerUnit);

                if (tvMaterial != null) {
                    tvMaterial.setText((String) bom.get("materialName"));
                    tvMaterial.setOnClickListener(v -> showInventorySelectionDialog(tvMaterial, () -> {}));
                }
                if (etQty != null) etQty.setText(String.valueOf(bom.get("quantity")));
                if (spinnerUnit != null) {
                    spinnerUnit.setAdapter(rowUnitAdapter);
                    String savedUnit = (String) bom.get("unit");
                    if (savedUnit != null) {
                        for (int i = 0; i < units.length; i++) {
                            if (units[i].equalsIgnoreCase(savedUnit)) { spinnerUnit.setSelection(i); break; }
                        }
                    }
                }
                View btnDelete = row.findViewById(R.id.btnDelete);
                if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            }
        }

        btnAddRow.setOnClickListener(v -> addRow.run());
        btnCancel.setOnClickListener(v -> { if (savedBOM.isEmpty() && switchBOM != null) switchBOM.setChecked(false); dialog.dismiss(); });

        btnSave.setOnClickListener(v -> {
            savedBOM.clear();
            for (int i = 0; i < containerRows.getChildCount(); i++) {
                View row = containerRows.getChildAt(i);
                TextView tvMat = row.findViewById(R.id.tvRawMaterial);
                EditText etQty = row.findViewById(R.id.etDeductQty);
                Spinner spinUnit = row.findViewById(R.id.spinnerUnit);

                String materialName = tvMat != null ? tvMat.getText().toString().trim() : "";
                String qtyStr = etQty != null ? etQty.getText().toString().trim() : "";
                String unitStr = spinUnit != null && spinUnit.getSelectedItem() != null ? spinUnit.getSelectedItem().toString() : "pcs";

                if (!materialName.isEmpty() && !materialName.contains("Select Item")) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("materialName", materialName);
                    map.put("quantity", qtyStr.isEmpty() ? 0.0 : Double.parseDouble(qtyStr));
                    map.put("unit", unitStr);
                    savedBOM.add(map);
                }
            }
            if (savedBOM.isEmpty() && switchBOM != null) switchBOM.setChecked(false);
            else {
                Toast.makeText(this, "Recipe Saved!", Toast.LENGTH_SHORT).show();
                updateMainCostFromBOM();
            }
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

        List<String> inventoryNames = new ArrayList<>();
        for (Product p : inventoryProducts) inventoryNames.add(p.getProductName());
        ArrayAdapter<String> autoCompleteAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, inventoryNames);

        String[] units = {"pcs", "ml", "L", "oz", "g", "kg", "box", "pack"};
        ArrayAdapter<String> rowUnitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_addon, null);
            AutoCompleteTextView actvItem = row.findViewById(R.id.actvAddonItem);
            Spinner spinnerUnit = row.findViewById(R.id.spinnerAddonUnit);

            if (actvItem != null) actvItem.setAdapter(autoCompleteAdapter);
            if (spinnerUnit != null) spinnerUnit.setAdapter(rowUnitAdapter);

            View btnDelete = row.findViewById(R.id.btnDelete);
            if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedAddons.isEmpty()) addRow.run();
        else {
            for (Map<String, Object> addon : savedAddons) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_addon, null);
                AutoCompleteTextView actvItem = row.findViewById(R.id.actvAddonItem);
                EditText etPrice = row.findViewById(R.id.etAddonPrice);
                EditText etQty = row.findViewById(R.id.etAddonQty);
                Spinner spinnerUnit = row.findViewById(R.id.spinnerAddonUnit);

                if (actvItem != null) {
                    actvItem.setAdapter(autoCompleteAdapter);
                    actvItem.setText((String) addon.get("name"));
                }
                if (spinnerUnit != null) spinnerUnit.setAdapter(rowUnitAdapter);
                if (etPrice != null) etPrice.setText(String.valueOf(addon.get("price")));
                if (etQty != null && addon.containsKey("deductQty")) etQty.setText(String.valueOf(addon.get("deductQty")));

                if (addon.containsKey("unit") && spinnerUnit != null) {
                    String savedUnit = (String) addon.get("unit");
                    for (int i = 0; i < units.length; i++) {
                        if (units[i].equalsIgnoreCase(savedUnit)) { spinnerUnit.setSelection(i); break; }
                    }
                }

                View btnDelete = row.findViewById(R.id.btnDelete);
                if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            }
        }

        btnAddRow.setOnClickListener(v -> addRow.run());
        btnCancel.setOnClickListener(v -> { if (savedAddons.isEmpty() && switchAddons != null) switchAddons.setChecked(false); dialog.dismiss(); });

        btnSave.setOnClickListener(v -> {
            savedAddons.clear();
            for (int i = 0; i < containerRows.getChildCount(); i++) {
                View row = containerRows.getChildAt(i);
                AutoCompleteTextView actvItem = row.findViewById(R.id.actvAddonItem);
                EditText etPrice = row.findViewById(R.id.etAddonPrice);
                EditText etQty = row.findViewById(R.id.etAddonQty);
                Spinner spinnerUnit = row.findViewById(R.id.spinnerAddonUnit);

                String name = actvItem != null ? actvItem.getText().toString().trim() : "";
                String priceStr = etPrice != null ? etPrice.getText().toString().trim() : "";
                String qtyStr = etQty != null ? etQty.getText().toString().trim() : "";
                String unitStr = spinnerUnit != null && spinnerUnit.getSelectedItem() != null ? spinnerUnit.getSelectedItem().toString() : "pcs";

                if (!name.isEmpty()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", name);
                    map.put("price", priceStr.isEmpty() ? 0.0 : Double.parseDouble(priceStr));
                    map.put("deductQty", qtyStr.isEmpty() ? 0.0 : Double.parseDouble(qtyStr));
                    map.put("unit", unitStr);
                    map.put("linkedMaterial", name);
                    savedAddons.add(map);
                }
            }
            if (savedAddons.isEmpty() && switchAddons != null) switchAddons.setChecked(false);
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

                TextView tvType = row.findViewById(R.id.tvNoteType);
                EditText etVal = row.findViewById(R.id.etNoteValue);

                // FIX: Actually restore the Type (e.g., "Sugar Level")!
                if (tvType != null && note.get("type") != null) {
                    tvType.setText(note.get("type"));
                }

                if (etVal != null) {
                    String cleanValue = note.get("value");
                    if (cleanValue != null && cleanValue.endsWith("%")) cleanValue = cleanValue.replace("%", "");
                    etVal.setText(cleanValue);
                }
                View btnDelete = row.findViewById(R.id.btnDelete);
                if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            }
        }

        btnAddRow.setOnClickListener(v -> addRow.run());
        btnCancel.setOnClickListener(v -> { if (savedNotes.isEmpty() && switchNotes != null) switchNotes.setChecked(false); dialog.dismiss(); });

        btnSave.setOnClickListener(v -> {
            savedNotes.clear();
            for (int i = 0; i < containerRows.getChildCount(); i++) {
                View row = containerRows.getChildAt(i);
                TextView tvType = row.findViewById(R.id.tvNoteType);
                EditText etVal = row.findViewById(R.id.etNoteValue);

                if (tvType != null && etVal != null) {
                    String type = tvType.getText().toString().trim();
                    String value = etVal.getText().toString().trim();
                    if (!value.isEmpty()) {
                        Map<String, String> map = new HashMap<>();
                        map.put("type", type);
                        map.put("value", value + "%");
                        savedNotes.add(map);
                    }
                }
            }
            if (savedNotes.isEmpty() && switchNotes != null) switchNotes.setChecked(false);
            else Toast.makeText(this, "Notes saved!", Toast.LENGTH_SHORT).show();
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
            if (actvLinked != null) actvLinked.setAdapter(autoCompleteAdapter);

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
                AutoCompleteTextView actvLinked = row.findViewById(R.id.actvLinkedInventory);

                if (actvLinked != null) { actvLinked.setAdapter(autoCompleteAdapter); actvLinked.setText((String) size.get("linkedMaterial")); }
                if (etName != null) etName.setText((String) size.get("name"));
                if (etPrice != null) etPrice.setText(String.valueOf(size.get("price")));

                View btnDelete = row.findViewById(R.id.btnDelete);
                if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            }
        }

        btnAddRow.setOnClickListener(v -> addRow.run());
        btnCancel.setOnClickListener(v -> { if (savedSizes.isEmpty() && switchSizes != null) switchSizes.setChecked(false); dialog.dismiss(); });
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
            if (savedSizes.isEmpty() && switchSizes != null) switchSizes.setChecked(false);
            else Toast.makeText(this, "Sizes saved!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showInventorySelectionDialog(TextView targetTextView, Runnable onSelected) {
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
            String cat = p.getCategoryName() != null ? p.getCategoryName() : "Uncategorized";
            if (!categories.contains(cat)) categories.add(cat);
        }
        spinnerFilter.setAdapter(getAdaptiveDropdownAdapter(categories));

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
            if (onSelected != null) onSelected.run();
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void setupImagePickers() {
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) { selectedImagePath = uri.toString(); btnEditPhoto.setImageURI(uri); }
            }
        });
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) openImagePicker(); else Toast.makeText(this, "Permission required", Toast.LENGTH_SHORT).show();
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

    private void loadProductData() {
        productRepository.getProductById(editProductId, new ProductRepository.OnProductFetchedListener() {
            @Override
            public void onProductFetched(Product p) {
                runOnUiThread(() -> {
                    if (p != null) {
                        existingProductToEdit = p;
                        productNameET.setText(p.getProductName());
                        productTypeET.setText(p.getCategoryName());
                        sellingPriceET.setText(String.valueOf(p.getSellingPrice()));
                        costPriceET.setText(String.valueOf(p.getCostPrice()));

                        if (p.getImagePath() != null && !p.getImagePath().isEmpty()) {
                            selectedImagePath = p.getImagePath();
                        } else if (p.getImageUrl() != null && !p.getImageUrl().isEmpty()) {
                            selectedImagePath = p.getImageUrl();
                        }

                        if (selectedImagePath != null) {
                            Glide.with(EditSalesProductActivity.this).load(selectedImagePath)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .into(btnEditPhoto);
                        }

                        savedSizes = p.getSizesList() != null ? p.getSizesList() : new ArrayList<>();
                        savedAddons = p.getAddonsList() != null ? p.getAddonsList() : new ArrayList<>();
                        savedNotes = p.getNotesList() != null ? p.getNotesList() : new ArrayList<>();
                        savedBOM = p.getBomList() != null ? p.getBomList() : new ArrayList<>();

                        // DETACH LISTENERS FIRST to prevent auto-popups from clearing your data!
                        if (switchSizes != null) switchSizes.setOnCheckedChangeListener(null);
                        if (switchAddons != null) switchAddons.setOnCheckedChangeListener(null);
                        if (switchNotes != null) switchNotes.setOnCheckedChangeListener(null);
                        if (switchBOM != null) switchBOM.setOnCheckedChangeListener(null);

                        if (switchSizes != null) switchSizes.setChecked(!savedSizes.isEmpty());
                        if (switchAddons != null) switchAddons.setChecked(!savedAddons.isEmpty());
                        if (switchNotes != null) switchNotes.setChecked(!savedNotes.isEmpty());
                        if (switchBOM != null) switchBOM.setChecked(!savedBOM.isEmpty());

                        // REATTACH LISTENERS NOW that data is safely loaded
                        if (switchSizes != null) switchSizes.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showSizesDialog(); else savedSizes.clear(); });
                        if (switchAddons != null) switchAddons.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showAddonsDialog(); else savedAddons.clear(); });
                        if (switchNotes != null) switchNotes.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showNotesDialog(); else savedNotes.clear(); });
                        if (switchBOM != null) switchBOM.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showBOMDialog(); else savedBOM.clear(); });

                        updateMainCostFromBOM();
                        updateMainCostFromBOM();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(EditSalesProductActivity.this, "Failed to load product data: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private ArrayAdapter<String> getAdaptiveDropdownAdapter(List<String> items) {
        boolean isDark = false;
        try {
            isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        } catch (Exception e) {
            int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            isDark = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
        int textColor = isDark ? android.graphics.Color.WHITE : android.graphics.Color.BLACK;

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

    private void attemptEdit() {
        String type = productTypeET.getText() != null ? productTypeET.getText().toString().trim() : "";
        String name = productNameET.getText() != null ? productNameET.getText().toString().trim() : "";

        if (name.isEmpty() || type.isEmpty()) {
            Toast.makeText(this, "Product name and category are required", Toast.LENGTH_SHORT).show();
            return;
        }

        String categoryId = type.toLowerCase(Locale.ROOT).replace(" ", "_");

        double sellingPrice = 0, costPrice = 0;

        try { sellingPrice = Double.parseDouble(sellingPriceET.getText() != null ? sellingPriceET.getText().toString() : "0"); } catch (Exception ignored) {}
        try { costPrice = Double.parseDouble(costPriceET.getText() != null ? costPriceET.getText().toString() : "0"); } catch (Exception ignored) {}

        if (existingProductToEdit == null) existingProductToEdit = new Product();
        existingProductToEdit.setProductName(name);
        existingProductToEdit.setCategoryName(type);
        existingProductToEdit.setCategoryId(categoryId);
        existingProductToEdit.setProductType("Menu");
        existingProductToEdit.setSellable(true);
        existingProductToEdit.setActive(true);
        existingProductToEdit.setSellingPrice(sellingPrice);
        existingProductToEdit.setCostPrice(costPrice);

        existingProductToEdit.setSizesList(savedSizes);
        existingProductToEdit.setAddonsList(savedAddons);
        existingProductToEdit.setNotesList(savedNotes);
        existingProductToEdit.setBomList(savedBOM);

        productRepository.updateProduct(existingProductToEdit, selectedImagePath, new ProductRepository.OnProductUpdatedListener() {
            @Override public void onProductUpdated() { runOnUiThread(() -> { Toast.makeText(EditSalesProductActivity.this, "Updated successfully", Toast.LENGTH_SHORT).show(); finish(); }); }
            @Override public void onError(String error) { runOnUiThread(() -> Toast.makeText(EditSalesProductActivity.this, "Update Error: " + error, Toast.LENGTH_SHORT).show()); }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}