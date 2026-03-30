package com.app.SalesInventory;

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

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EditProduct extends BaseActivity {
    private ImageButton btnEditPhoto;
    private TextInputEditText productNameET, sellingPriceET, quantityET, costPriceET, expiryDateET;
    private TextView tvStockLevel;
    private AutoCompleteTextView productLineET, productTypeET;
    private Button updateBtn, cancelBtn;
    private Spinner unitSpinner;
    private TextInputLayout layoutPiecesPerUnit;
    private TextInputEditText etPiecesPerUnit;

    private SwitchMaterial switchSellOnPOS;
    private SwitchMaterial switchSizes, switchAddons, switchNotes, switchBOM;

    private LinearLayout layoutConfigurations;
    private TextInputLayout layoutSellingPrice;

    private List<Map<String, Object>> savedSizes = new ArrayList<>();
    private List<Map<String, Object>> savedAddons = new ArrayList<>();
    private List<Map<String, String>> savedNotes = new ArrayList<>();
    private List<Map<String, Object>> savedBOM = new ArrayList<>();

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_product);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Edit Product");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

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
        if (currentUserId == null || currentUserId.isEmpty()) {
            currentUserId = authManager.getCurrentUserId();
        }

        btnEditPhoto = findViewById(R.id.btnEditPhoto);
        productNameET = findViewById(R.id.productNameET);
        productLineET = findViewById(R.id.productLineET);
        productTypeET = findViewById(R.id.productTypeET);

        switchSellOnPOS = findViewById(R.id.switchSellOnPOS);
        switchSizes = findViewById(R.id.switchSizes);
        switchAddons = findViewById(R.id.switchAddons);
        switchNotes = findViewById(R.id.switchNotes);
        switchBOM = findViewById(R.id.switchBOM);

        costPriceET = findViewById(R.id.costPriceET);
        sellingPriceET = findViewById(R.id.sellingPriceET);
        layoutSellingPrice = findViewById(R.id.layoutSellingPrice);

        quantityET = findViewById(R.id.quantityET);
        unitSpinner = findViewById(R.id.unitSpinner);
        layoutPiecesPerUnit = findViewById(R.id.layoutPiecesPerUnit);
        etPiecesPerUnit = findViewById(R.id.etPiecesPerUnit);
        tvStockLevel = findViewById(R.id.tvStockLevel);
        expiryDateET = findViewById(R.id.expiryDateET);

        layoutConfigurations = findViewById(R.id.layoutConfigurations);

        updateBtn = findViewById(R.id.updateBtn);
        cancelBtn = findViewById(R.id.cancelBtn);

        setupImagePickers();
        loadInventoryForCalculations();

        String[] units = {"pcs", "ml", "L", "oz", "g", "kg", "box", "pack"};
        ArrayAdapter<String> unitAdapter = getAdaptiveAdapter(units);
        unitSpinner.setAdapter(unitAdapter);

        unitSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selected = unitSpinner.getSelectedItem() != null ? unitSpinner.getSelectedItem().toString() : "";
                boolean isPackaging = selected.equalsIgnoreCase("box") || selected.equalsIgnoreCase("pack");
                if (layoutPiecesPerUnit != null) {
                    layoutPiecesPerUnit.setVisibility(isPackaging ? View.VISIBLE : View.GONE);
                    if (!isPackaging && etPiecesPerUnit != null) etPiecesPerUnit.setText("");
                }
            }
            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        switchSellOnPOS.setOnCheckedChangeListener((btn, isChecked) -> updateLayoutForSelectedType());
        updateBtn.setOnClickListener(v -> attemptEdit());
        cancelBtn.setOnClickListener(v -> finish());
        btnEditPhoto.setOnClickListener(v -> tryPickImage());
        expiryDateET.setOnClickListener(v -> showExpiryDatePicker());

        quantityET.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateAutomatedLowStock(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadProductData();
    }

    // FIX: Added the List<String> version of the adapter method
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

    // Preserved the String[] version for the other spinners
    private ArrayAdapter<String> getAdaptiveAdapter(String[] items) {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, items);
        return getAdaptiveAdapter(list);
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

    private void updateLayoutForSelectedType() {
        boolean isMenu = switchSellOnPOS.isChecked();

        if (isMenu) {
            if (layoutConfigurations != null) layoutConfigurations.setVisibility(View.VISIBLE);
            if (layoutSellingPrice != null) layoutSellingPrice.setVisibility(View.VISIBLE);

            quantityET.setVisibility(View.GONE);
            unitSpinner.setVisibility(View.GONE);
            if (tvStockLevel != null && tvStockLevel.getParent() != null) {
                ((View) tvStockLevel.getParent()).setVisibility(View.GONE);
            }

            costPriceET.setEnabled(false);
            costPriceET.setHint("Auto-calculated");
            costPriceET.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.dirtyWhite));
            updateMainCostFromBOM();
        } else {
            if (layoutConfigurations != null) layoutConfigurations.setVisibility(View.GONE);
            if (layoutSellingPrice != null) layoutSellingPrice.setVisibility(View.INVISIBLE);

            quantityET.setVisibility(View.VISIBLE);
            unitSpinner.setVisibility(View.VISIBLE);
            if (tvStockLevel != null && tvStockLevel.getParent() != null) {
                ((View) tvStockLevel.getParent()).setVisibility(View.VISIBLE);
            }

            costPriceET.setEnabled(true);
            costPriceET.setHint("Total Cost (₱)");
            costPriceET.setBackgroundTintList(null);
        }
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
        if (!switchSellOnPOS.isChecked() || savedBOM == null) return;
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
        ArrayAdapter<String> rowUnitAdapter = getAdaptiveAdapter(units);

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
        ArrayAdapter<String> autoCompleteAdapter = getAdaptiveDropdownAdapter(inventoryNames);

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
        ArrayAdapter<String> autoCompleteAdapter = getAdaptiveDropdownAdapter(inventoryNames);

        String[] units = {"pcs", "ml", "L", "oz", "g", "kg", "box", "pack"};
        ArrayAdapter<String> rowUnitAdapter = getAdaptiveAdapter(units);

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
        // FIX: The missing dot was here!
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

    private void showInventorySelectionDialog(TextView targetTextView, Runnable onSelected) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_inventory_selection, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        EditText etSearch = view.findViewById(R.id.etSearchInventory);
        Spinner spinnerFilter = view.findViewById(R.id.spinnerFilterCategory);
        ListView lvItems = view.findViewById(R.id.lvInventoryItems);
        Button btnClose = view.findViewById(R.id.btnCloseSelection);

        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;
        etSearch.setTextColor(textColor);
        etSearch.setHintTextColor(Color.GRAY);

        List<String> categories = new ArrayList<>();
        categories.add("All Categories");
        for (Product p : inventoryProducts) {
            String cat = p.getCategoryName() != null ? p.getCategoryName() : "Uncategorized";
            if (!categories.contains(cat)) categories.add(cat);
        }
        ArrayAdapter<String> catAdapter = getAdaptiveAdapter(categories);
        spinnerFilter.setAdapter(catAdapter);

        List<Product> filteredList = new ArrayList<>(inventoryProducts);
        ArrayAdapter<Product> listAdapter = new ArrayAdapter<Product>(this, android.R.layout.simple_list_item_1, filteredList) {
            @NonNull @Override
            public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView tv = v.findViewById(android.R.id.text1);
                tv.setTextColor(textColor);
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

    private void updateAutomatedLowStock() {
        try {
            String qtyStr = quantityET.getText() != null ? quantityET.getText().toString() : "0";
            double qty = qtyStr.isEmpty() ? 0 : Double.parseDouble(qtyStr);
            String unit = unitSpinner.getSelectedItem() != null ? unitSpinner.getSelectedItem().toString() : "";
            int lowStockThreshold = (int) Math.ceil(qty * 0.20);
            tvStockLevel.setText(qty <= 0 ? "0 " + unit : lowStockThreshold + " " + unit);
        } catch (Exception e) { tvStockLevel.setText("0"); }
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

    private void showExpiryDatePicker() {
        Calendar now = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            expiryCalendar.set(Calendar.YEAR, y);
            expiryCalendar.set(Calendar.MONTH, m);
            expiryCalendar.set(Calendar.DAY_OF_MONTH, d);
            expiryDateET.setText(expiryFormat.format(expiryCalendar.getTime()));
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        dialog.show();
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
                        sellingPriceET.setText(String.valueOf(p.getSellingPrice()));
                        costPriceET.setText(String.valueOf(p.getCostPrice()));
                        quantityET.setText(String.valueOf(p.getQuantity()));

                        if (p.getExpiryDate() > 0) {
                            expiryCalendar.setTimeInMillis(p.getExpiryDate());
                            expiryDateET.setText(expiryFormat.format(expiryCalendar.getTime()));
                        }

                        if (p.isSellable() || "finished".equalsIgnoreCase(p.getProductType()) || "Menu".equalsIgnoreCase(p.getProductType())) {
                            switchSellOnPOS.setChecked(true);
                        } else {
                            switchSellOnPOS.setChecked(false);
                        }

                        if (p.getUnit() != null) {
                            for (int i = 0; i < unitSpinner.getAdapter().getCount(); i++) {
                                if (unitSpinner.getAdapter().getItem(i).toString().equalsIgnoreCase(p.getUnit())) {
                                    unitSpinner.setSelection(i);
                                    break;
                                }
                            }
                        }

                        int savedPpu = p.getPiecesPerUnit();
                        if (savedPpu > 0 && etPiecesPerUnit != null) {
                            etPiecesPerUnit.setText(String.valueOf(savedPpu));
                        }
                        if (layoutPiecesPerUnit != null) {
                            String loadedUnit = p.getUnit() != null ? p.getUnit() : "";
                            boolean isPackaging = loadedUnit.equalsIgnoreCase("box") || loadedUnit.equalsIgnoreCase("pack");
                            layoutPiecesPerUnit.setVisibility(isPackaging ? View.VISIBLE : View.GONE);
                        }

                        if (p.getImagePath() != null && !p.getImagePath().isEmpty()) {
                            selectedImagePath = p.getImagePath();
                        } else if (p.getImageUrl() != null && !p.getImageUrl().isEmpty()) {
                            selectedImagePath = p.getImageUrl();
                        }

                        if (isDestroyed() || isFinishing()) return; // FIX: Prevent Glide from crashing if theme changed

                        if (selectedImagePath != null) {
                            Glide.with(EditProduct.this).load(selectedImagePath)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .into(btnEditPhoto);
                        }

                        savedSizes = p.getSizesList() != null ? p.getSizesList() : new ArrayList<>();
                        savedAddons = p.getAddonsList() != null ? p.getAddonsList() : new ArrayList<>();
                        savedNotes = p.getNotesList() != null ? p.getNotesList() : new ArrayList<>();
                        savedBOM = p.getBomList() != null ? p.getBomList() : new ArrayList<>();

                        if (switchSizes != null) switchSizes.setOnCheckedChangeListener(null);
                        if (switchAddons != null) switchAddons.setOnCheckedChangeListener(null);
                        if (switchNotes != null) switchNotes.setOnCheckedChangeListener(null);
                        if (switchBOM != null) switchBOM.setOnCheckedChangeListener(null);

                        if (switchSizes != null) switchSizes.setChecked(!savedSizes.isEmpty());
                        if (switchAddons != null) switchAddons.setChecked(!savedAddons.isEmpty());
                        if (switchNotes != null) switchNotes.setChecked(!savedNotes.isEmpty());
                        if (switchBOM != null) switchBOM.setChecked(!savedBOM.isEmpty());

                        if (switchSizes != null) switchSizes.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showSizesDialog(); else savedSizes.clear(); });
                        if (switchAddons != null) switchAddons.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showAddonsDialog(); else savedAddons.clear(); });
                        if (switchNotes != null) switchNotes.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showNotesDialog(); else savedNotes.clear(); });
                        if (switchBOM != null) switchBOM.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showBOMDialog(); else savedBOM.clear(); });

                        updateLayoutForSelectedType();
                        updateAutomatedLowStock();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(EditProduct.this, "Failed to load product data: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void attemptEdit() {
        String line = productLineET.getText() != null ? productLineET.getText().toString().trim() : "";
        String type = productTypeET.getText() != null ? productTypeET.getText().toString().trim() : "";
        String name = productNameET.getText() != null ? productNameET.getText().toString().trim() : "";

        if (name.isEmpty() || type.isEmpty()) {
            Toast.makeText(this, "Product name and type are required", Toast.LENGTH_SHORT).show();
            return;
        }

        String categoryId = type.toLowerCase(Locale.ROOT).replace(" ", "_");
        String productTypeStr = switchSellOnPOS.isChecked() ? "Menu" : "Raw";

        double sellingPrice = 0, costPrice = 0, qty = 0;
        int criticalLevel = 1, reorderLevel = 0;
        long expiryDate = 0L;

        String expiryStr = expiryDateET.getText() != null ? expiryDateET.getText().toString().trim() : "";
        if (!expiryStr.isEmpty()) {
            try { Date d = expiryFormat.parse(expiryStr); if (d != null) expiryDate = d.getTime(); } catch (ParseException ignored) {}
        }

        if (switchSellOnPOS.isChecked()) {
            try { sellingPrice = Double.parseDouble(sellingPriceET.getText() != null ? sellingPriceET.getText().toString() : "0"); } catch (Exception ignored) {}
            try { costPrice = Double.parseDouble(costPriceET.getText() != null ? costPriceET.getText().toString() : "0"); } catch (Exception ignored) {}
        } else {
            try { costPrice = Double.parseDouble(costPriceET.getText() != null ? costPriceET.getText().toString() : "0"); } catch (Exception ignored) {}
            try { qty = Double.parseDouble(quantityET.getText() != null ? quantityET.getText().toString() : "0"); } catch (Exception ignored) {}
            reorderLevel = (int) Math.ceil(qty * 0.20);
            if (qty < 0) qty = 0;
            if (reorderLevel < 0) reorderLevel = 0;
        }

        if (existingProductToEdit == null) existingProductToEdit = new Product();
        existingProductToEdit.setProductName(name);
        existingProductToEdit.setCategoryName(type);
        existingProductToEdit.setProductLine(line);
        existingProductToEdit.setCategoryId(categoryId);
        existingProductToEdit.setProductType(productTypeStr);
        existingProductToEdit.setSellingPrice(sellingPrice);
        existingProductToEdit.setCostPrice(costPrice);
        existingProductToEdit.setQuantity(qty);
        existingProductToEdit.setCriticalLevel(criticalLevel);
        existingProductToEdit.setReorderLevel(reorderLevel);
        existingProductToEdit.setUnit(unitSpinner.getSelectedItem() != null ? unitSpinner.getSelectedItem().toString() : "pcs");
        existingProductToEdit.setSalesUnit(unitSpinner.getSelectedItem() != null ? unitSpinner.getSelectedItem().toString() : "pcs");
        existingProductToEdit.setSellable(switchSellOnPOS.isChecked());

        int piecesPerUnit = 1;
        if (layoutPiecesPerUnit.getVisibility() == View.VISIBLE) {
            try { piecesPerUnit = Integer.parseInt(etPiecesPerUnit.getText() != null ? etPiecesPerUnit.getText().toString().trim() : "1"); } catch (Exception ignored) {}
        }
        existingProductToEdit.setPiecesPerUnit(piecesPerUnit > 0 ? piecesPerUnit : 1);

        existingProductToEdit.setExpiryDate(expiryDate);

        if (switchSellOnPOS.isChecked()) {
            existingProductToEdit.setSizesList(savedSizes);
            existingProductToEdit.setAddonsList(savedAddons);
            existingProductToEdit.setNotesList(savedNotes);
            existingProductToEdit.setBomList(savedBOM);
        } else {
            existingProductToEdit.setSizesList(new ArrayList<>());
            existingProductToEdit.setAddonsList(new ArrayList<>());
            existingProductToEdit.setNotesList(new ArrayList<>());
            existingProductToEdit.setBomList(new ArrayList<>());
        }

        productRepository.updateProduct(existingProductToEdit, selectedImagePath, new ProductRepository.OnProductUpdatedListener() {
            @Override public void onProductUpdated() { runOnUiThread(() -> { Toast.makeText(EditProduct.this, "Updated successfully", Toast.LENGTH_SHORT).show(); finish(); }); }
            @Override public void onError(String error) { runOnUiThread(() -> Toast.makeText(EditProduct.this, "Update Error: " + error, Toast.LENGTH_SHORT).show()); }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}