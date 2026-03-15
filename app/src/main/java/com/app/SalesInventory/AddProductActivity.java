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
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
import com.google.android.material.textfield.TextInputLayout;
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

public class AddProductActivity extends BaseActivity {
    private ImageButton btnAddPhoto;
    private TextInputEditText productNameET, productGroupET, sellingPriceET, quantityET, costPriceET, expiryDateET;
    private TextView tvStockLevel;
    private Button addBtn, cancelBtn;
    private Spinner unitSpinner, existingGroupSpinner, salesUnitSpinner;

    private SwitchMaterial switchSizes, switchAddons, switchNotes, switchBOM;

    // Type Selector (Tabs)
    private RadioGroup rgProductType;
    private RadioButton rbTypeInventory, rbTypeSales, rbTypeBoth;

    // Variants and Layout Configurations
    private SwitchMaterial switchVariants;
    private LinearLayout layoutConfigurations;
    private List<Map<String, Object>> savedVariants = new ArrayList<>();

    // Dual Measurement Setup UI
    private View layoutDeduction;
    private TextInputEditText deductionAmountET;
    private View layoutSellingPrice;
    private LinearLayout layoutBuyingUnitQtyCritical;

    private ProductRepository productRepository;
    private AuthManager authManager;
    private SimpleDateFormat expiryFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private String selectedImagePath;
    private Calendar expiryCalendar = Calendar.getInstance();
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    private List<Product> inventoryProducts = new ArrayList<>();

    private List<Map<String, Object>> savedSizes = new ArrayList<>();
    private List<Map<String, Object>> savedAddons = new ArrayList<>();
    private List<Map<String, String>> savedNotes = new ArrayList<>();
    private List<Map<String, Object>> savedBOM = new ArrayList<>();

    private String currentUserId;
    private TextInputLayout layoutPiecesPerUnit;
    private TextInputEditText piecesPerUnitET;

    // AUTOMATED QUEUE VARIABLES
    private ArrayList<Bundle> registrationQueue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);
        productRepository = SalesInventoryApplication.getProductRepository();

        layoutPiecesPerUnit = findViewById(R.id.layoutPiecesPerUnit);
        piecesPerUnitET = findViewById(R.id.piecesPerUnitET);

        btnAddPhoto = findViewById(R.id.btnAddPhoto);
        productNameET = findViewById(R.id.productNameET);
        productGroupET = findViewById(R.id.productGroupET);
        sellingPriceET = findViewById(R.id.sellingPriceET);
        expiryDateET = findViewById(R.id.expiryDateET);

        authManager = AuthManager.getInstance();
        currentUserId = authManager.getCurrentUserId();

        costPriceET = findViewById(R.id.costPriceET);
        quantityET = findViewById(R.id.quantityET);
        tvStockLevel = findViewById(R.id.tvStockLevel);
        unitSpinner = findViewById(R.id.unitSpinner);

        layoutBuyingUnitQtyCritical = findViewById(R.id.layout_buying_unit_qty_critical);
        layoutSellingPrice = findViewById(R.id.layoutSellingPrice);

        addBtn = findViewById(R.id.addBtn);
        cancelBtn = findViewById(R.id.cancelBtn);
        switchSizes = findViewById(R.id.switchSizes);
        switchAddons = findViewById(R.id.switchAddons);
        switchNotes = findViewById(R.id.switchNotes);
        switchBOM = findViewById(R.id.switchBOM);

        // MAP NEW TABS & DUAL MEASUREMENT UI
        rgProductType = findViewById(R.id.rgProductType);
        rbTypeInventory = findViewById(R.id.rbTypeInventory);
        rbTypeSales = findViewById(R.id.rbTypeSales);
        rbTypeBoth = findViewById(R.id.rbTypeBoth);

        layoutDeduction = findViewById(R.id.layoutDeduction);
        deductionAmountET = findViewById(R.id.deductionAmountET);
        salesUnitSpinner = findViewById(R.id.salesUnitSpinner);

        // MAP Configurations Layout and Variants
        layoutConfigurations = findViewById(R.id.layoutConfigurations);
        switchVariants = findViewById(R.id.switchVariants);

        setupImagePickers();
        loadInventoryForBOM();

        String[] units = {"pcs", "ml", "L", "oz", "g", "kg", "box", "pack"};
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(unitAdapter);
        if(salesUnitSpinner != null) salesUnitSpinner.setAdapter(unitAdapter);

        // Enforce logic based on intent
        if (getIntent().getBooleanExtra("FORCE_SALE_ONLY", false)) {
            rgProductType.check(R.id.rbTypeSales);
            rbTypeInventory.setEnabled(false);
            rbTypeBoth.setEnabled(false);
        }

        rgProductType.setOnCheckedChangeListener((group, checkedId) -> updateLayoutForSelectedType());
        updateLayoutForSelectedType();

        authManager.refreshCurrentUserStatus(success -> {
            if (!authManager.isCurrentUserAdmin()) {
                Toast.makeText(AddProductActivity.this, "Error: User not approved", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        switchVariants.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showVariantsDialog(); else savedVariants.clear(); });
        switchSizes.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showSizesDialog(); else savedSizes.clear(); });
        switchAddons.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showAddonsDialog(); else savedAddons.clear(); });
        switchNotes.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showNotesDialog(); else savedNotes.clear(); });
        switchBOM.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showBOMDialog(); else savedBOM.clear(); });

        addBtn.setOnClickListener(v -> attemptAdd());
        cancelBtn.setOnClickListener(v -> finish());
        btnAddPhoto.setOnClickListener(v -> tryPickImage());
        expiryDateET.setOnClickListener(v -> showExpiryDatePicker());

        checkAndApplyPrefillData();

        // ----------------------------------------------------
        // NEW: AUTOMATED REAL-TIME LOW STOCK CALCULATION LOGIC
        // ----------------------------------------------------
        quantityET.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateAutomatedLowStock();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        unitSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selected = parent.getItemAtPosition(position).toString().toLowerCase(Locale.ROOT);
                if (selected.equals("box") || selected.equals("pack")) {
                    layoutPiecesPerUnit.setVisibility(View.VISIBLE);
                } else {
                    layoutPiecesPerUnit.setVisibility(View.GONE);
                }
                updateAutomatedLowStock();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    /**
     * Calculates 20% of the input stock and dynamically updates the UI threshold
     */
    private void updateAutomatedLowStock() {
        if (tvStockLevel == null) return;
        try {
            String qtyStr = quantityET.getText() != null ? quantityET.getText().toString() : "0";
            double qty = qtyStr.isEmpty() ? 0 : Double.parseDouble(qtyStr);
            String unit = unitSpinner.getSelectedItem() != null ? unitSpinner.getSelectedItem().toString() : "";

            // Standard rule: Alert triggers when stock drops to 20% or below
            int lowStockThreshold = (int) Math.ceil(qty * 0.20);

            if (qty <= 0) {
                tvStockLevel.setText("0 " + unit);
            } else {
                tvStockLevel.setText(lowStockThreshold + " " + unit);
            }
        } catch (Exception e) {
            tvStockLevel.setText("0");
        }
    }

    private void updateLayoutForSelectedType() {
        int checkedId = rgProductType.getCheckedRadioButtonId();

        if (checkedId == R.id.rbTypeSales) {
            layoutBuyingUnitQtyCritical.setVisibility(View.GONE);
            if (layoutSellingPrice != null) layoutSellingPrice.setVisibility(View.VISIBLE);
            layoutConfigurations.setVisibility(View.VISIBLE);
            layoutDeduction.setVisibility(View.GONE);
            setupCategorySpinner(true);
        } else if (checkedId == R.id.rbTypeBoth) {
            layoutBuyingUnitQtyCritical.setVisibility(View.VISIBLE);
            if (layoutSellingPrice != null) layoutSellingPrice.setVisibility(View.VISIBLE);
            layoutConfigurations.setVisibility(View.GONE);
            layoutDeduction.setVisibility(View.VISIBLE);
            setupCategorySpinner(true);
        } else {
            layoutBuyingUnitQtyCritical.setVisibility(View.VISIBLE);
            if (layoutSellingPrice != null) layoutSellingPrice.setVisibility(View.GONE);
            layoutConfigurations.setVisibility(View.GONE);
            layoutDeduction.setVisibility(View.GONE);
            setupCategorySpinner(false);
        }
    }

    private void checkAndApplyPrefillData() {
        registrationQueue = getIntent().getParcelableArrayListExtra("REGISTRATION_QUEUE");
        Bundle currentItemBundle = null;

        if (registrationQueue != null && !registrationQueue.isEmpty()) {
            currentItemBundle = registrationQueue.remove(0);

            if (!registrationQueue.isEmpty()) {
                Toast.makeText(this, "Please save this item. " + registrationQueue.size() + " more new items waiting.", Toast.LENGTH_LONG).show();
                if (getSupportActionBar() != null) getSupportActionBar().setTitle("Add Product (Queue: " + (registrationQueue.size() + 1) + ")");
            } else {
                if (getSupportActionBar() != null) getSupportActionBar().setTitle("Add Product (Last Item)");
            }
        } else if (getIntent().getExtras() != null && getIntent().hasExtra("PREFILL_NAME")) {
            currentItemBundle = getIntent().getExtras();
        }

        if (currentItemBundle != null) {
            rgProductType.check(R.id.rbTypeInventory);
            rbTypeSales.setEnabled(false);
            rbTypeBoth.setEnabled(false);

            if (currentItemBundle.containsKey("PREFILL_NAME")) productNameET.setText(currentItemBundle.getString("PREFILL_NAME"));
            if (currentItemBundle.containsKey("PREFILL_COST")) costPriceET.setText(String.valueOf(currentItemBundle.getDouble("PREFILL_COST")));
            if (currentItemBundle.containsKey("PREFILL_QTY")) quantityET.setText(String.valueOf(currentItemBundle.getInt("PREFILL_QTY")));

            if (currentItemBundle.containsKey("PREFILL_UNIT")) {
                String unit = currentItemBundle.getString("PREFILL_UNIT");
                for (int i = 0; i < unitSpinner.getAdapter().getCount(); i++) {
                    if (unitSpinner.getAdapter().getItem(i).toString().equalsIgnoreCase(unit)) {
                        unitSpinner.setSelection(i);
                        break;
                    }
                }
            }
        }
    }

    private void proceedToNextInQueue() {
        if (registrationQueue != null && !registrationQueue.isEmpty()) {
            Intent intent = new Intent(this, AddProductActivity.class);
            intent.putParcelableArrayListExtra("REGISTRATION_QUEUE", registrationQueue);
            startActivity(intent);
        }
        finish();
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

    private void clearForm() {
        productNameET.setText("");
        productGroupET.setText("");
        sellingPriceET.setText("");
        costPriceET.setText("");
        quantityET.setText("");
        tvStockLevel.setText("0");
        expiryDateET.setText("");
        selectedImagePath = null;
        btnAddPhoto.setImageResource(android.R.drawable.ic_menu_report_image);

        if (switchVariants != null) switchVariants.setChecked(false);
        switchSizes.setChecked(false);
        switchAddons.setChecked(false);
        switchNotes.setChecked(false);
        switchBOM.setChecked(false);
        deductionAmountET.setText("");

        savedVariants.clear();
        savedSizes.clear();
        savedAddons.clear();
        savedNotes.clear();
        savedBOM.clear();
        productNameET.requestFocus();
    }

    private void showVariantsDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_config_variants, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).setCancelable(false).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        LinearLayout containerRows = view.findViewById(R.id.containerRows);
        ImageButton btnAddRow = view.findViewById(R.id.btnAddRow);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSave = view.findViewById(R.id.btnSave);

        List<String> inventoryNames = new ArrayList<>();
        for (Product p : inventoryProducts) {
            inventoryNames.add(p.getProductName());
        }
        ArrayAdapter<String> autoCompleteAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, inventoryNames);

        String[] units = {"pcs", "ml", "L", "oz", "g", "kg", "box", "pack"};
        ArrayAdapter<String> rowUnitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
        rowUnitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_variant, null);

            AutoCompleteTextView actvItem = row.findViewById(R.id.actvVariantItem);
            actvItem.setAdapter(autoCompleteAdapter);

            Spinner spinnerUnit = row.findViewById(R.id.spinnerVariantUnit);
            spinnerUnit.setAdapter(rowUnitAdapter);

            View btnDelete = row.findViewById(R.id.btnDelete);
            btnDelete.setOnClickListener(v -> containerRows.removeView(row));

            containerRows.addView(row);
        };

        if (savedVariants.isEmpty()) {
            addRow.run();
        } else {
            for (Map<String, Object> variant : savedVariants) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_variant, null);

                AutoCompleteTextView actvItem = row.findViewById(R.id.actvVariantItem);
                actvItem.setAdapter(autoCompleteAdapter);
                actvItem.setText((String) variant.get("variantName"));

                EditText etQty = row.findViewById(R.id.etVariantQty);
                etQty.setText(String.valueOf(variant.get("deductQty")));

                Spinner spinnerUnit = row.findViewById(R.id.spinnerVariantUnit);
                spinnerUnit.setAdapter(rowUnitAdapter);

                String savedUnit = (String) variant.get("unit");
                if (savedUnit != null) {
                    for (int i = 0; i < units.length; i++) {
                        if (units[i].equalsIgnoreCase(savedUnit)) {
                            spinnerUnit.setSelection(i);
                            break;
                        }
                    }
                }

                View btnDelete = row.findViewById(R.id.btnDelete);
                btnDelete.setOnClickListener(v -> containerRows.removeView(row));

                containerRows.addView(row);
            }
        }

        btnAddRow.setOnClickListener(v -> addRow.run());

        btnCancel.setOnClickListener(v -> {
            if (savedVariants.isEmpty()) switchVariants.setChecked(false);
            dialog.dismiss();
        });

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
                    map.put("extraPrice", 0.0);
                    savedVariants.add(map);
                }
            }
            if (savedVariants.isEmpty()) switchVariants.setChecked(false);
            else Toast.makeText(this, "Variants Saved!", Toast.LENGTH_SHORT).show();
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
            try {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_bom, null);
                TextView tvMaterial = row.findViewById(R.id.tvRawMaterial);
                if (tvMaterial != null) tvMaterial.setOnClickListener(v -> showInventorySelectionDialog(tvMaterial));

                Spinner spinnerUnit = row.findViewById(R.id.spinnerUnit);
                if (spinnerUnit != null) spinnerUnit.setAdapter(rowUnitAdapter);

                View btnDelete = row.findViewById(R.id.btnDelete);
                if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));

                containerRows.addView(row);
            } catch (Exception e) {
                Toast.makeText(this, "Layout error. Check item_config_bom.xml IDs.", Toast.LENGTH_SHORT).show();
            }
        };

        if (savedBOM.isEmpty()) {
            addRow.run();
        } else {
            for (Map<String, Object> bom : savedBOM) {
                try {
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
                } catch (Exception e) { }
            }
        }

        btnAddRow.setOnClickListener(v -> addRow.run());

        btnCancel.setOnClickListener(v -> {
            if (savedBOM.isEmpty()) switchBOM.setChecked(false);
            dialog.dismiss();
        });

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

        List<String> inventoryNames = new ArrayList<>();
        for (Product p : inventoryProducts) {
            inventoryNames.add(p.getProductName());
        }
        ArrayAdapter<String> autoCompleteAdapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, inventoryNames);

        Runnable addRow = () -> {
            try {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_size, null);

                AutoCompleteTextView actvLinked = row.findViewById(R.id.actvLinkedInventory);
                if (actvLinked != null) actvLinked.setAdapter(autoCompleteAdapter);

                View btnDelete = row.findViewById(R.id.btnDelete);
                if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            } catch (Exception e) {
                Toast.makeText(this, "Layout error. Check item_config_size.xml IDs.", Toast.LENGTH_SHORT).show();
            }
        };

        if (savedSizes.isEmpty()) addRow.run();
        else {
            for (Map<String, Object> size : savedSizes) {
                try {
                    View row = LayoutInflater.from(this).inflate(R.layout.item_config_size, null);
                    EditText etName = row.findViewById(R.id.etSizeName);
                    EditText etPrice = row.findViewById(R.id.etSizePrice);
                    AutoCompleteTextView actvLinked = row.findViewById(R.id.actvLinkedInventory);

                    if (actvLinked != null) {
                        actvLinked.setAdapter(autoCompleteAdapter);
                        String linked = (String) size.get("linkedMaterial");
                        if (linked != null) actvLinked.setText(linked);
                    }

                    if (etName != null) etName.setText((String) size.get("name"));
                    if (etPrice != null) etPrice.setText(String.valueOf(size.get("price")));

                    View btnDelete = row.findViewById(R.id.btnDelete);
                    if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
                    containerRows.addView(row);
                } catch (Exception e) { }
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
            if (savedSizes.isEmpty()) switchSizes.setChecked(false);
            else Toast.makeText(this, "Sizes saved!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void showAddonsDialog() {
        try {
            View view = LayoutInflater.from(this).inflate(R.layout.dialog_config_addons, null);
            AlertDialog dialog = new AlertDialog.Builder(this).setView(view).setCancelable(false).create();
            if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

            LinearLayout containerRows = view.findViewById(R.id.containerRows);
            ImageButton btnAddRow = view.findViewById(R.id.btnAddRow);
            Button btnCancel = view.findViewById(R.id.btnCancel);
            Button btnSave = view.findViewById(R.id.btnSave);

            Runnable addRow = () -> {
                try {
                    View row = LayoutInflater.from(this).inflate(R.layout.item_add_ons, null);
                    View btnDelete = row.findViewById(R.id.btnDelete);
                    if (btnDelete != null) {
                        btnDelete.setOnClickListener(v -> containerRows.removeView(row));
                    }
                    containerRows.addView(row);
                } catch (Exception e) {
                    Toast.makeText(this, "Error inflating Add-on row: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            };

            if (savedAddons.isEmpty()) {
                addRow.run();
            } else {
                for (Map<String, Object> addon : savedAddons) {
                    try {
                        View row = LayoutInflater.from(this).inflate(R.layout.item_add_ons, null);
                        EditText etName = row.findViewById(R.id.etAddonName);
                        EditText etPrice = row.findViewById(R.id.etAddonPrice);

                        if (etName != null) etName.setText((String) addon.get("name"));
                        if (etPrice != null) etPrice.setText(String.valueOf(addon.get("price")));

                        View btnDelete = row.findViewById(R.id.btnDelete);
                        if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));

                        containerRows.addView(row);
                    } catch (Exception e) {
                        Toast.makeText(this, "Error restoring saved add-on row", Toast.LENGTH_SHORT).show();
                    }
                }
            }

            btnAddRow.setOnClickListener(v -> addRow.run());
            btnCancel.setOnClickListener(v -> {
                if (savedAddons.isEmpty()) switchAddons.setChecked(false);
                dialog.dismiss();
            });

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

        } catch (Exception e) {
            Toast.makeText(this, "Fatal dialog error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            switchAddons.setChecked(false);
        }
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
            try {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_note, null);
                View btnDelete = row.findViewById(R.id.btnDelete);
                if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            } catch (Exception e) {
                Toast.makeText(this, "Layout error. Check item_config_note.xml IDs.", Toast.LENGTH_SHORT).show();
            }
        };

        if (savedNotes.isEmpty()) addRow.run();
        else {
            for (Map<String, String> note : savedNotes) {
                try {
                    View row = LayoutInflater.from(this).inflate(R.layout.item_config_note, null);
                    EditText etType = row.findViewById(R.id.etNoteType);
                    EditText etVal = row.findViewById(R.id.etNoteValue);

                    if (etType != null) etType.setText(note.get("type"));
                    if (etVal != null) etVal.setText(note.get("value"));

                    View btnDelete = row.findViewById(R.id.btnDelete);
                    if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
                    containerRows.addView(row);
                } catch (Exception e) { }
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

    private void setupCategorySpinner(boolean wantMenu) {
        existingGroupSpinner = findViewById(R.id.existingGroupSpinner);
        productGroupET = findViewById(R.id.productGroupET);

        DatabaseReference categoryRef = FirebaseDatabase.getInstance().getReference("Categories");

        categoryRef.orderByChild("ownerAdminId").equalTo(currentUserId)
                .addListenerForSingleValueEvent(new ValueEventListener(){
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        List<String> groups = new ArrayList<>();
                        groups.add("Select Group");

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
                        ArrayAdapter<String> groupAdapter = new ArrayAdapter<>(AddProductActivity.this, android.R.layout.simple_spinner_item, groups);
                        existingGroupSpinner.setAdapter(groupAdapter);
                    }
                    @Override public void onCancelled(DatabaseError error) {}
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

    private void setupImagePickers() {
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) {
                    selectedImagePath = uri.toString();
                    btnAddPhoto.setImageURI(uri);
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

    private void showExpiryDatePicker() {
        Calendar now = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            expiryCalendar.set(Calendar.YEAR, y);
            expiryCalendar.set(Calendar.MONTH, m);
            expiryCalendar.set(Calendar.DAY_OF_MONTH, d);
            expiryDateET.setText(expiryFormat.format(expiryCalendar.getTime()));
        }, now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis());
        dialog.show();
    }

    private void attemptAdd() {
        String name = productNameET.getText() != null ? productNameET.getText().toString().trim() : "";
        if (name.isEmpty()) { Toast.makeText(this, "Product name is required", Toast.LENGTH_SHORT).show(); return; }

        String categoryName = productGroupET.getText().toString().trim();
        if (categoryName.isEmpty()) { Toast.makeText(this, "Product group is required", Toast.LENGTH_SHORT).show(); return; }

        int checkedTabId = rgProductType.getCheckedRadioButtonId();
        boolean isMenu = (checkedTabId == R.id.rbTypeSales || checkedTabId == R.id.rbTypeBoth);
        checkAndCreateCategory(categoryName, isMenu);

        String categoryId = categoryName.toLowerCase(Locale.ROOT).replace(" ", "_");

        String productType = "Inventory";
        if (checkedTabId == R.id.rbTypeSales) productType = "Menu";
        else if (checkedTabId == R.id.rbTypeBoth) productType = "Both";

        double sellingPrice = 0, costPrice = 0, deductionAmount = 1.0;
        int qty = 0, criticalLevel = 1, reorderLevel = 0, ceilingLevel = 0;
        long expiryDate = 0L;

        try { sellingPrice = Double.parseDouble(sellingPriceET.getText() != null ? sellingPriceET.getText().toString() : "0"); } catch (Exception ignored) {}

        String expiryStr = expiryDateET.getText() != null ? expiryDateET.getText().toString().trim() : "";
        if (!expiryStr.isEmpty()) {
            try {
                Date d = expiryFormat.parse(expiryStr);
                if (d != null) expiryDate = d.getTime();
            } catch (ParseException ignored) {}
        }

        if ("Menu".equalsIgnoreCase(productType)) {
            costPrice = 0; qty = 0; criticalLevel = 1; reorderLevel = 0;
        } else {
            try { costPrice = Double.parseDouble(costPriceET.getText() != null ? costPriceET.getText().toString() : "0"); } catch (Exception ignored) {}
            try { qty = Integer.parseInt(quantityET.getText() != null ? quantityET.getText().toString() : "0"); } catch (Exception ignored) {}

            // ----------------------------------------------------
            // NEW: AUTOMATICALLY ASSIGN 20% AS LOW STOCK LEVEL
            // ----------------------------------------------------
            reorderLevel = (int) Math.ceil(qty * 0.20);

            criticalLevel = 1;
            if (qty < 0) qty = 0;
            if (reorderLevel < 0) reorderLevel = 0;

            if ("Both".equalsIgnoreCase(productType)) {
                try {
                    deductionAmount = Double.parseDouble(deductionAmountET.getText().toString().trim());
                } catch (Exception ignored) {}
            }
        }

        Product p = new Product();
        p.setProductName(name);
        p.setCategoryName(categoryName);
        p.setCategoryId(categoryId);
        p.setProductType(productType);
        p.setSellingPrice(sellingPrice);
        p.setCostPrice(costPrice);
        p.setQuantity(qty);
        p.setCriticalLevel(criticalLevel);
        p.setReorderLevel(reorderLevel); // Automated threshold saved here
        p.setCeilingLevel(ceilingLevel);
        p.setUnit(unitSpinner.getSelectedItem() != null ? unitSpinner.getSelectedItem().toString() : "");
        p.setExpiryDate(expiryDate);
        p.setDeductionAmount(deductionAmount);

        p.setSalesUnit(salesUnitSpinner != null && salesUnitSpinner.getSelectedItem() != null ? salesUnitSpinner.getSelectedItem().toString() : "");
        int ppu = 1;
        if (layoutPiecesPerUnit.getVisibility() == View.VISIBLE) {
            try { ppu = Integer.parseInt(piecesPerUnitET.getText().toString().trim()); } catch (Exception ignored) {}
        }
        p.setPiecesPerUnit(ppu);

        long now = System.currentTimeMillis();
        p.setDateAdded(now);
        p.setActive(true);
        p.setImagePath(selectedImagePath);
        p.setOwnerAdminId(currentUserId);

        if (checkedTabId == R.id.rbTypeSales) {
            if (!savedVariants.isEmpty()) p.setVariantsList(savedVariants);
            if (!savedSizes.isEmpty()) p.setSizesList(savedSizes);
            if (!savedAddons.isEmpty()) p.setAddonsList(savedAddons);
            if (!savedNotes.isEmpty()) p.setNotesList(savedNotes);
            if (!savedBOM.isEmpty()) p.setBomList(savedBOM);
        }

        productRepository.addProduct(p, selectedImagePath, new ProductRepository.OnProductAddedListener() {
            @Override
            public void onProductAdded(String productId) {
                runOnUiThread(() -> {
                    Toast.makeText(AddProductActivity.this, "Product added successfully", Toast.LENGTH_SHORT).show();
                    if (registrationQueue != null && !registrationQueue.isEmpty()) {
                        proceedToNextInQueue();
                    } else if (getIntent().hasExtra("PREFILL_NAME")) {
                        finish();
                    } else {
                        clearForm();
                    }
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(AddProductActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show());
            }
        });
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

    @Override
    public void onBackPressed() {
        if (registrationQueue != null && !registrationQueue.isEmpty()) proceedToNextInQueue();
        else super.onBackPressed();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            if (registrationQueue != null && !registrationQueue.isEmpty()) proceedToNextInQueue();
            else finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}