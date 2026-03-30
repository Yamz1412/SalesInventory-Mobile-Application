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
import android.widget.AdapterView;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

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

public class AddProductActivity extends BaseActivity {
    private ImageButton btnAddPhoto;
    private TextInputEditText productNameET, sellingPriceET, quantityET, costPriceET, expiryDateET;
    private TextView tvStockLevel;
    private AutoCompleteTextView productLineET, productTypeET;
    private Button addBtn, cancelBtn;
    private Spinner unitSpinner;

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
    private String editProductId = null;
    private Product existingProductToEdit = null;

    private TextInputLayout layoutPiecesPerUnit;
    private TextInputEditText etPiecesPerUnit;
    private List<String> unitList = new ArrayList<>();
    private ArrayAdapter<String> unitAdapter;
    private List<String> dialogUnits = new ArrayList<>();

    // UPGRADED: Added Custom Measurements natively
    private List<String> baseUnitList = new ArrayList<>(java.util.Arrays.asList(
            "pcs", "ml", "L", "oz", "g", "kg", "box", "pack", "bottle", "scoop"
    ));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);

        productRepository = SalesInventoryApplication.getProductRepository();
        authManager = AuthManager.getInstance();

        currentUserId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (currentUserId == null || currentUserId.isEmpty()) {
            currentUserId = authManager.getCurrentUserId();
        }

        btnAddPhoto = findViewById(R.id.btnAddPhoto);
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
        tvStockLevel = findViewById(R.id.tvStockLevel);
        expiryDateET = findViewById(R.id.expiryDateET);

        layoutConfigurations = findViewById(R.id.layoutConfigurations);

        addBtn = findViewById(R.id.addBtn);
        cancelBtn = findViewById(R.id.cancelBtn);

        setupImagePickers();
        loadInventoryForCalculations();

        layoutPiecesPerUnit = findViewById(R.id.layoutPiecesPerUnit);
        etPiecesPerUnit = findViewById(R.id.etPiecesPerUnit);

        unitList.addAll(baseUnitList);
        unitList.add("+ Add Custom Unit...");
        unitAdapter = getAdaptiveAdapter(unitList);
        unitSpinner.setAdapter(unitAdapter);

        unitSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String selected = unitList.get(position);
                if (selected.equals("+ Add Custom Unit...")) {
                    showCustomUnitDialog();
                } else {
                    boolean isBulkContainer = selected.equalsIgnoreCase("box") ||
                            selected.equalsIgnoreCase("pack") ||
                            selected.equalsIgnoreCase("bottle") ||
                            selected.equalsIgnoreCase("L") ||
                            selected.equalsIgnoreCase("kg");

                    layoutPiecesPerUnit.setVisibility(isBulkContainer ? View.VISIBLE : View.GONE);

                    if (isBulkContainer) {
                        layoutPiecesPerUnit.setHint("Total sub-units in 1 " + selected);
                    } else {
                        etPiecesPerUnit.setText("1"); // Reset to 1 for simple units like pcs/scoop
                    }
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        productTypeET.setOnClickListener(v -> productTypeET.showDropDown());
        productLineET.setOnClickListener(v -> productLineET.showDropDown());

        listenToProductLinesAndTypes();

        switchSellOnPOS.setOnCheckedChangeListener((btn, isChecked) -> updateLayoutForSelectedType());
        updateLayoutForSelectedType();

        if (switchSizes != null) switchSizes.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showSizesDialog(); else savedSizes.clear(); });
        if (switchAddons != null) switchAddons.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showAddonsDialog(); else savedAddons.clear(); });
        if (switchNotes != null) switchNotes.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showNotesDialog(); else savedNotes.clear(); });
        if (switchBOM != null) switchBOM.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showBOMDialog(); else savedBOM.clear(); });

        addBtn.setOnClickListener(v -> attemptAdd());
        cancelBtn.setOnClickListener(v -> finish());
        btnAddPhoto.setOnClickListener(v -> tryPickImage());
        expiryDateET.setOnClickListener(v -> showExpiryDatePicker());

        quantityET.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateAutomatedLowStock(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        if (getIntent().hasExtra("EDIT_PRODUCT_ID")) {
            editProductId = getIntent().getStringExtra("EDIT_PRODUCT_ID");
            addBtn.setText("Update Product");
            if (getSupportActionBar() != null) getSupportActionBar().setTitle("Edit Product");
            loadProductDataForEdit();
        }

        // CATCH UNREGISTERED PRODUCTS FROM PURCHASE ORDERS
        if (getIntent().hasExtra("REGISTRATION_QUEUE")) {
            ArrayList<Bundle> queue = getIntent().getParcelableArrayListExtra("REGISTRATION_QUEUE");
            if (queue != null && !queue.isEmpty()) {
                Bundle pendingItem = queue.get(0);
                productNameET.setText(pendingItem.getString("productName", ""));

                double cost = pendingItem.getDouble("costPrice", 0.0);
                double qty = pendingItem.getDouble("quantity", 0.0);

                costPriceET.setText(String.format(java.util.Locale.US, "%.2f", cost));
                quantityET.setText(String.format(java.util.Locale.US, "%.2f", qty));

                if (queue.size() > 1) {
                    queue.remove(0);
                    final ArrayList<Bundle> remainingQueue = new ArrayList<>(queue);

                    addBtn.setOnClickListener(v -> {
                        attemptAdd();
                        Intent nextIntent = new Intent(AddProductActivity.this, AddProductActivity.class);
                        nextIntent.putParcelableArrayListExtra("REGISTRATION_QUEUE", remainingQueue);
                        startActivity(nextIntent);
                    });
                }
            }
        }
        if (getIntent().getBooleanExtra("MODE_MENU_ONLY", false)) {
            if (switchSellOnPOS != null) {
                switchSellOnPOS.setChecked(true);
                updateLayoutForSelectedType();
            }
        }
    }

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

    private void showCustomUnitDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Custom Unit");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 40, 60, 20);

        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        // 1. Input for the Main Unit (e.g., "Tub")
        final EditText etNewUnit = new EditText(this);
        etNewUnit.setHint("Main Unit (e.g., Tub, Gallon)");
        etNewUnit.setTextColor(textColor);
        layout.addView(etNewUnit);

        // 2. Question Text
        TextView tvInfo = new TextView(this);
        tvInfo.setText("\nDoes this unit contain sub-amounts?\n(e.g., 1 Tub = 50 Scoops)");
        tvInfo.setTextSize(12);
        tvInfo.setTextColor(textColor);
        layout.addView(tvInfo);

        // 3. Input for the Amount (e.g., "50")
        final EditText etSubAmount = new EditText(this);
        etSubAmount.setHint("Amount (e.g., 50)");
        etSubAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etSubAmount.setTextColor(textColor);
        layout.addView(etSubAmount);

        // 4. Input for the Sub-Unit Name (e.g., "scoops")
        final EditText etSubUnitName = new EditText(this);
        etSubUnitName.setHint("Sub-unit name (e.g., scoops, ml)");
        etSubUnitName.setTextColor(textColor);
        layout.addView(etSubUnitName);

        builder.setView(layout);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String mainUnit = etNewUnit.getText().toString().trim();
            String subAmount = etSubAmount.getText().toString().trim();
            String subName = etSubUnitName.getText().toString().trim();

            if (!mainUnit.isEmpty()) {
                // Add to list and select it
                unitList.add(unitList.size() - 1, mainUnit);
                unitAdapter.notifyDataSetChanged();
                unitSpinner.setSelection(unitList.size() - 2);

                // Automatically populate the "Pieces Per Unit" logic
                if (!subAmount.isEmpty()) {
                    layoutPiecesPerUnit.setVisibility(View.VISIBLE);
                    etPiecesPerUnit.setText(subAmount);
                    // Tip: You can even update the label to say "Pieces per [Main Unit]"
                    layoutPiecesPerUnit.setHint(subName + " per " + mainUnit);
                }
            } else {
                unitSpinner.setSelection(0);
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> unitSpinner.setSelection(0));
        builder.show();
    }

    private void updateLayoutForSelectedType() {
        boolean isMenu = switchSellOnPOS.isChecked();

        if (isMenu) {
            layoutConfigurations.setVisibility(View.VISIBLE);
            if (layoutSellingPrice != null) layoutSellingPrice.setVisibility(View.VISIBLE);

            quantityET.setVisibility(View.GONE);
            unitSpinner.setVisibility(View.GONE);
            ((View) tvStockLevel.getParent()).setVisibility(View.GONE);

            costPriceET.setEnabled(false);
            costPriceET.setHint("Auto-calculated");
            costPriceET.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.dirtyWhite));
            updateMainCostFromBOM();
        } else {
            layoutConfigurations.setVisibility(View.GONE);
            if (layoutSellingPrice != null) layoutSellingPrice.setVisibility(View.INVISIBLE);

            quantityET.setVisibility(View.VISIBLE);
            unitSpinner.setVisibility(View.VISIBLE);
            ((View) tvStockLevel.getParent()).setVisibility(View.VISIBLE);

            costPriceET.setEnabled(true);
            costPriceET.setHint("Total Cost (₱)");
            costPriceET.setBackgroundTintList(null);
        }
    }

    private void loadInventoryForCalculations() {
        productRepository.getAllProducts().observe(this, products -> {
            inventoryProducts.clear();
            java.util.Set<String> dynamicUnits = new java.util.HashSet<>(baseUnitList); // UPGRADED
            if (products != null) {
                for (Product p : products) {
                    if (p.getUnit() != null && !p.getUnit().isEmpty()) dynamicUnits.add(p.getUnit());
                    if (p.isActive() && !"Menu".equalsIgnoreCase(p.getProductType()) && !"finished".equalsIgnoreCase(p.getProductType())) {
                        inventoryProducts.add(p);
                    }
                }
            }
            dialogUnits.clear();
            dialogUnits.addAll(dynamicUnits);
            unitList.clear();
            unitList.addAll(dynamicUnits);
            unitList.add("+ Add Custom Unit...");
            if (unitAdapter != null) unitAdapter.notifyDataSetChanged();
        });
    }

    private Product findInventoryProduct(String name) {
        for (Product p : inventoryProducts) {
            if (p.getProductName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private void updateMainCostFromBOM() {
        if (!switchSellOnPOS.isChecked()) return;
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

        dialogUnits.remove("+ Add Custom Unit...");
        ArrayAdapter<String> rowUnitAdapter = getAdaptiveAdapter(dialogUnits);

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_bom, null);
            TextView tvMaterial = row.findViewById(R.id.tvRawMaterial);
            Spinner spinnerUnit = row.findViewById(R.id.spinnerUnit);

            // FIX: Create a unique, final list for this specific row's logic
            final List<String> rowUnits = new ArrayList<>(unitList);
            // FIX: Create a unique adapter specifically for this row
            ArrayAdapter<String> adapterForThisRow = getAdaptiveAdapter(rowUnits);

            if (spinnerUnit != null) {
                spinnerUnit.setAdapter(adapterForThisRow);
                spinnerUnit.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        if (rowUnits.get(pos).equals("+ Add Custom Unit...")) {
                            showCustomUnitDialog();
                        }
                    }
                    @Override public void onNothingSelected(AdapterView<?> p) {}
                });
            }

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
                        for (int i = 0; i < dialogUnits.size(); i++) {
                            if (dialogUnits.get(i).equalsIgnoreCase(savedUnit)) { spinnerUnit.setSelection(i); break; }
                        }
                    }
                }

                android.widget.CheckBox cbIsEssential = row.findViewById(R.id.cbIsEssential);
                if (cbIsEssential != null && bom.containsKey("isEssential")) {
                    Object isEssObj = bom.get("isEssential");
                    if (isEssObj instanceof Boolean) cbIsEssential.setChecked((Boolean) isEssObj);
                    else if (isEssObj instanceof String) cbIsEssential.setChecked(Boolean.parseBoolean((String) isEssObj));
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
                android.widget.CheckBox cbIsEssential = row.findViewById(R.id.cbIsEssential);
                boolean isEssential = cbIsEssential == null || cbIsEssential.isChecked();

                if (!materialName.isEmpty() && !materialName.contains("Select Item")) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("materialName", materialName);
                    map.put("quantity", qtyStr.isEmpty() ? 0.0 : Double.parseDouble(qtyStr));
                    map.put("unit", unitStr);
                    map.put("isEssential", isEssential);
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
        ArrayAdapter<String> autoCompleteAdapter = getAdaptiveDropdownAdapter(inventoryNames);

        List<String> currentDialogUnits = new ArrayList<>(dialogUnits);
        currentDialogUnits.remove("+ Add Custom Unit...");
        ArrayAdapter<String> rowUnitAdapter = getAdaptiveAdapter(currentDialogUnits);

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_addon, null);
            AutoCompleteTextView actvItem = row.findViewById(R.id.actvAddonItem);
            Spinner spinnerUnit = row.findViewById(R.id.spinnerAddonUnit);

            // FIX: Create a unique, final list for this specific row's logic
            final List<String> rowUnits = new ArrayList<>(unitList);
            // FIX: Create a unique adapter specifically for this row
            ArrayAdapter<String> adapterForThisRow = getAdaptiveAdapter(rowUnits);

            if (actvItem != null) actvItem.setAdapter(autoCompleteAdapter);
            if (spinnerUnit != null) {
                spinnerUnit.setAdapter(adapterForThisRow);
                spinnerUnit.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        if (rowUnits.get(pos).equals("+ Add Custom Unit...")) {
                            showCustomUnitDialog();
                        }
                    }
                    @Override public void onNothingSelected(AdapterView<?> p) {}
                });
            }

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
                    for (int i = 0; i < currentDialogUnits.size(); i++) {
                        if (currentDialogUnits.get(i).equalsIgnoreCase(savedUnit)) { spinnerUnit.setSelection(i); break; }
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
                EditText etVal = row.findViewById(R.id.etNoteValue);
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

    private void checkAndCreateProductLine(String lineName) {
        if (lineName == null || lineName.trim().isEmpty()) return;
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("ProductLines");
        ref.orderByChild("ownerAdminId").equalTo(currentUserId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean exists = false;
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String existingName = ds.child("lineName").getValue(String.class);
                    if (existingName != null && existingName.equalsIgnoreCase(lineName.trim())) { exists = true; break; }
                }
                if (!exists) {
                    String id = ref.push().getKey();
                    if (id != null) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", id);
                        map.put("lineName", lineName.trim());
                        map.put("ownerAdminId", currentUserId);
                        ref.child(id).setValue(map);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void checkAndCreateCategory(String name, boolean isMenu) {
        if (name == null || name.isEmpty()) return;
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("Categories");
        ref.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean exists = false;
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Category c = ds.getValue(Category.class);
                    if (c != null && name.equalsIgnoreCase(c.getCategoryName()) && currentUserId.equals(c.getOwnerAdminId())) { exists = true; break; }
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

    private void listenToProductLinesAndTypes() {
        DatabaseReference catRef = FirebaseDatabase.getInstance().getReference("Categories");
        catRef.addValueEventListener(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                List<String> options = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    Category c = child.getValue(Category.class);
                    if (c != null && currentUserId.equals(c.getOwnerAdminId())) options.add(c.getCategoryName());
                }
                productTypeET.setAdapter(getAdaptiveDropdownAdapter(options));
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
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
                if (uri != null) { selectedImagePath = uri.toString(); btnAddPhoto.setImageURI(uri); }
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
        dialog.getDatePicker().setMinDate(System.currentTimeMillis());
        dialog.show();
    }

    private void loadProductDataForEdit() {
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

                        if ("finished".equalsIgnoreCase(p.getProductType()) || "Menu".equalsIgnoreCase(p.getProductType())) {
                            switchSellOnPOS.setChecked(true);
                        } else {
                            switchSellOnPOS.setChecked(false);
                        }

                        String loadedUnit = p.getUnit() != null ? p.getUnit() : "";
                        boolean isBulk = loadedUnit.equalsIgnoreCase("box") ||
                                loadedUnit.equalsIgnoreCase("pack") ||
                                loadedUnit.equalsIgnoreCase("bottle") ||
                                loadedUnit.equalsIgnoreCase("tub") ||
                                loadedUnit.equalsIgnoreCase("can");

                        if (p.getPiecesPerUnit() > 1 || isBulk) {
                            layoutPiecesPerUnit.setVisibility(View.VISIBLE);
                            etPiecesPerUnit.setText(String.valueOf(p.getPiecesPerUnit()));
                        } else {
                            layoutPiecesPerUnit.setVisibility(View.GONE);
                        }

                        if (p.getUnit() != null) {
                            for (int i = 0; i < unitSpinner.getAdapter().getCount(); i++) {
                                if (unitSpinner.getAdapter().getItem(i).toString().equalsIgnoreCase(p.getUnit())) {
                                    unitSpinner.setSelection(i);
                                    break;
                                }
                            }
                        }

                        if (isDestroyed() || isFinishing()) return;

                        if (p.getImageUrl() != null && !p.getImageUrl().isEmpty()) {
                            selectedImagePath = p.getImageUrl();
                            Glide.with(AddProductActivity.this).load(p.getImageUrl())
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .into(btnAddPhoto);
                        } else if (p.getImagePath() != null && !p.getImagePath().isEmpty()) {
                            selectedImagePath = p.getImagePath();
                            Glide.with(AddProductActivity.this).load(p.getImagePath())
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .into(btnAddPhoto);
                        }

                        savedSizes = p.getSizesList() != null ? p.getSizesList() : new ArrayList<>();
                        savedAddons = p.getAddonsList() != null ? p.getAddonsList() : new ArrayList<>();
                        savedNotes = p.getNotesList() != null ? p.getNotesList() : new ArrayList<>();
                        savedBOM = p.getBomList() != null ? p.getBomList() : new ArrayList<>();

                        if (switchSizes != null) switchSizes.setChecked(!savedSizes.isEmpty());
                        if (switchAddons != null) switchAddons.setChecked(!savedAddons.isEmpty());
                        if (switchNotes != null) switchNotes.setChecked(!savedNotes.isEmpty());
                        if (switchBOM != null) switchBOM.setChecked(!savedBOM.isEmpty());

                        updateLayoutForSelectedType();
                        updateAutomatedLowStock();
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(AddProductActivity.this, "Failed to load product data: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void attemptAdd() {
        String line = productLineET.getText() != null ? productLineET.getText().toString().trim() : "";
        String type = productTypeET.getText() != null ? productTypeET.getText().toString().trim() : "";
        String name = productNameET.getText() != null ? productNameET.getText().toString().trim() : "";

        if (name.isEmpty()) { Toast.makeText(this, "Product name is required", Toast.LENGTH_SHORT).show(); return; }
        if (type.isEmpty()) { Toast.makeText(this, "Product Type is required", Toast.LENGTH_SHORT).show(); return; }

        checkAndCreateProductLine(line);
        checkAndCreateCategory(type, switchSellOnPOS.isChecked());

        String categoryId = type.toLowerCase(Locale.ROOT).replace(" ", "_");
        String productTypeStr = switchSellOnPOS.isChecked() ? "finished" : "raw";

        double sellingPrice = 0, costPrice = 0;
        int qty = 0, criticalLevel = 1, reorderLevel = 0;
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
            try { qty = Integer.parseInt(quantityET.getText() != null ? quantityET.getText().toString() : "0"); } catch (Exception ignored) {}
            reorderLevel = (int) Math.ceil(qty * 0.20);
            if (qty < 0) qty = 0;
            if (reorderLevel < 0) reorderLevel = 0;
        }

        Product p = existingProductToEdit != null ? existingProductToEdit : new Product();
        p.setProductName(name);
        p.setCategoryName(type);
        p.setProductLine(line);
        p.setCategoryId(categoryId);
        p.setProductType(productTypeStr);
        p.setSellable(switchSellOnPOS.isChecked());
        p.setSellingPrice(sellingPrice);
        p.setCostPrice(costPrice);
        p.setQuantity(qty);
        p.setCriticalLevel(criticalLevel);
        p.setReorderLevel(reorderLevel);
        p.setUnit(unitSpinner.getSelectedItem() != null ? unitSpinner.getSelectedItem().toString() : "pcs");
        p.setSalesUnit(unitSpinner.getSelectedItem() != null ? unitSpinner.getSelectedItem().toString() : "pcs");
        p.setExpiryDate(expiryDate);

        int piecesPerUnit = 1;
        if (layoutPiecesPerUnit.getVisibility() == View.VISIBLE) {
            try { piecesPerUnit = Integer.parseInt(etPiecesPerUnit.getText() != null ? etPiecesPerUnit.getText().toString().trim() : "1"); } catch (Exception ignored) {}
        }
        if (piecesPerUnit <= 0) piecesPerUnit = 1;
        p.setPiecesPerUnit(piecesPerUnit);

        if (existingProductToEdit == null) {
            long now = System.currentTimeMillis();
            p.setProductId("PROD_" + java.util.UUID.randomUUID().toString().substring(0, 8));
            p.setDateAdded(now);
            p.setActive(true);
            p.setOwnerAdminId(currentUserId);
        }

        if (selectedImagePath != null && !selectedImagePath.isEmpty()) p.setImagePath(selectedImagePath);

        if (switchSellOnPOS.isChecked()) {
            p.setSizesList(savedSizes);
            p.setAddonsList(savedAddons);
            p.setNotesList(savedNotes);
            p.setBomList(savedBOM);
        } else {
            p.setSizesList(new ArrayList<>());
            p.setAddonsList(new ArrayList<>());
            p.setNotesList(new ArrayList<>());
            p.setBomList(new ArrayList<>());
        }

        if (editProductId != null) {
            productRepository.updateProduct(p, selectedImagePath, new ProductRepository.OnProductUpdatedListener() {
                @Override public void onProductUpdated() { runOnUiThread(() -> { Toast.makeText(AddProductActivity.this, "Updated successfully", Toast.LENGTH_SHORT).show(); finish(); }); }
                @Override public void onError(String error) { runOnUiThread(() -> Toast.makeText(AddProductActivity.this, "Update Error: " + error, Toast.LENGTH_SHORT).show()); }
            });

        } else {
            productRepository.addProduct(p, selectedImagePath, new ProductRepository.OnProductAddedListener() {
                @Override public void onProductAdded(String productId) { runOnUiThread(() -> { Toast.makeText(AddProductActivity.this, "Added successfully", Toast.LENGTH_SHORT).show(); finish(); }); }
                @Override public void onError(String error) { runOnUiThread(() -> Toast.makeText(AddProductActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show()); }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}