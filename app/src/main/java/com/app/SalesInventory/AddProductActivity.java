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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
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
import com.google.android.material.button.MaterialButtonToggleGroup;
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
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;

public class AddProductActivity extends BaseActivity {
    private ImageButton btnAddPhoto;
    private TextInputEditText productNameET, sellingPriceET, quantityET, costPriceET, expiryDateET;
    private AutoCompleteTextView productLineET, productTypeET;
    private Button addBtn, cancelBtn;
    private Spinner unitSpinner;

    private SwitchMaterial switchSellOnPOS;
    private SwitchMaterial switchSizes, switchAddons, switchNotes, switchBOM;

    private LinearLayout layoutConfigurations;

    // GLOBAL PRICING RULES (REVISION 1)
    private boolean usePercentageMarkup = false;
    private double defaultMarkupPercent = 0.0;

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

    private LinearLayout layoutPiecesPerUnit;
    private TextInputEditText etPiecesPerUnit;
    private List<String> unitList = new ArrayList<>();
    private ArrayAdapter<String> unitAdapter;
    private List<String> dialogUnits = new ArrayList<>();

    private List<String> baseUnitList = new ArrayList<>(Arrays.asList(
            "pcs", "ml", "L", "oz", "g", "kg", "box", "pack", "scoop"
    ));
    private Spinner subUnitSpinner;
    private List<String> subUnitList = new ArrayList<>();
    private ArrayAdapter<String> subUnitAdapter;

    private boolean isFromPromoBuilder = false;
    private String autoPromoName;
    private boolean autoIsTemporary;
    private String autoStart, autoEnd;


    private String lastSelectedUnit = "";

    public interface OnInventorySelectedListener {
        void onSelected(String itemName);
    }

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

        quantityET = findViewById(R.id.quantityET);
        unitSpinner = findViewById(R.id.unitSpinner);
        expiryDateET = findViewById(R.id.expiryDateET);

        layoutConfigurations = findViewById(R.id.layoutConfigurations);
        addBtn = findViewById(R.id.addBtn);
        cancelBtn = findViewById(R.id.cancelBtn);

        if (getIntent().getBooleanExtra("FROM_PROMO_BUILDER", false)) {
            isFromPromoBuilder = true;
            autoPromoName = getIntent().getStringExtra("PROMO_NAME");
            autoIsTemporary = getIntent().getBooleanExtra("IS_TEMPORARY", false);
            autoStart = getIntent().getStringExtra("PROMO_START");
            autoEnd = getIntent().getStringExtra("PROMO_END");

            // Automatically check the switch to "Sell on POS"
            if (switchSellOnPOS != null) {
                switchSellOnPOS.setChecked(true);
                switchSellOnPOS.setEnabled(false); // Lock it to Menu mode
                updateLayoutForSelectedType();
            }
        }

        // REVISION 1: Fetch Admin Global Pricing Rules
        loadPricingRules();

        setupImagePickers();
        loadInventoryForCalculations();

        layoutPiecesPerUnit = findViewById(R.id.layoutPiecesPerUnit);
        etPiecesPerUnit = findViewById(R.id.etPiecesPerUnit);
        subUnitSpinner = findViewById(R.id.subUnitSpinner);
        subUnitAdapter = getAdaptiveAdapter(subUnitList);
        subUnitSpinner.setAdapter(subUnitAdapter);

        unitList.addAll(baseUnitList);
        unitList.add("+ Add Custom Unit...");
        unitAdapter = getAdaptiveAdapter(unitList);
        unitSpinner.setAdapter(unitAdapter);

        unitSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selected = unitList.get(position);
                if (selected.equals("+ Add Custom Unit...")) {
                    showCustomUnitDialog();
                } else {
                    boolean isBulkContainer = selected.equalsIgnoreCase("box") ||
                            selected.equalsIgnoreCase("pack") ||
                            selected.equalsIgnoreCase("L") ||
                            selected.equalsIgnoreCase("kg") ||
                            selected.equalsIgnoreCase("tub");

                    layoutPiecesPerUnit.setVisibility(isBulkContainer ? View.VISIBLE : View.GONE);

                    if (isBulkContainer) {
                        subUnitList.clear();
                        for (String u : unitList) {
                            if (!u.equals("+ Add Custom Unit...") && !u.equalsIgnoreCase(selected)) {
                                subUnitList.add(u);
                            }
                        }
                        if (!subUnitList.contains("pcs")) subUnitList.add(0, "pcs");
                        if (selected.equalsIgnoreCase("kg") && !subUnitList.contains("g")) subUnitList.add("g");
                        if (selected.equalsIgnoreCase("L") && !subUnitList.contains("ml")) subUnitList.add("ml");

                        subUnitAdapter.notifyDataSetChanged();

                        if (!selected.equals(lastSelectedUnit)) {
                            lastSelectedUnit = selected;
                            if (selected.equalsIgnoreCase("kg")) {
                                if (etPiecesPerUnit.getText().toString().isEmpty() || etPiecesPerUnit.getText().toString().equals("1")) etPiecesPerUnit.setText("1000");
                                subUnitSpinner.setSelection(subUnitList.indexOf("g"));
                            } else if (selected.equalsIgnoreCase("L")) {
                                if (etPiecesPerUnit.getText().toString().isEmpty() || etPiecesPerUnit.getText().toString().equals("1")) etPiecesPerUnit.setText("1000");
                                subUnitSpinner.setSelection(subUnitList.indexOf("ml"));
                            } else {
                                if (etPiecesPerUnit.getText().toString().isEmpty()) etPiecesPerUnit.setText("1");
                                subUnitSpinner.setSelection(subUnitList.indexOf("pcs"));
                            }
                        }
                    } else {
                        if (!selected.equals(lastSelectedUnit)) {
                            lastSelectedUnit = selected;
                            etPiecesPerUnit.setText("1");
                        }
                    }
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        productTypeET.setOnClickListener(v -> productTypeET.showDropDown());
        productLineET.setOnClickListener(v -> productLineET.showDropDown());

        listenToProductLinesAndTypes();

        switchSellOnPOS.setOnCheckedChangeListener((btn, isChecked) -> updateLayoutForSelectedType());
        updateLayoutForSelectedType();

        // REVISION 1: TextWatcher to Auto-Calculate Selling Price if manually entering Cost
        costPriceET.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (usePercentageMarkup && switchSellOnPOS.isChecked() && savedBOM.isEmpty()) {
                    calculateSellingPrice();
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        if (switchSizes != null) switchSizes.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showSizesDialog(); else savedSizes.clear(); });
        if (switchAddons != null) switchAddons.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showAddonsDialog(); else savedAddons.clear(); });
        if (switchNotes != null) switchNotes.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showNotesDialog(); else savedNotes.clear(); });
        if (switchBOM != null) switchBOM.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showBOMDialog(); else savedBOM.clear(); });

        addBtn.setOnClickListener(v -> attemptAdd());
        cancelBtn.setOnClickListener(v -> finish());
        btnAddPhoto.setOnClickListener(v -> tryPickImage());
        expiryDateET.setOnClickListener(v -> showExpiryDatePicker());

        if (getIntent().hasExtra("EDIT_PRODUCT_ID")) {
            editProductId = getIntent().getStringExtra("EDIT_PRODUCT_ID");
            addBtn.setText("Update Product");
            if (getSupportActionBar() != null) getSupportActionBar().setTitle("Edit Product");
            loadProductDataForEdit();
        }

        if (getIntent().getBooleanExtra("MODE_MENU_ONLY", false)) {
            if (switchSellOnPOS != null) {
                switchSellOnPOS.setChecked(true);
                updateLayoutForSelectedType();
            }
        }
    }

    private void loadPricingRules() {
        DatabaseReference settingsRef = FirebaseDatabase.getInstance().getReference("SystemSettings").child(currentUserId);
        settingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean isMarkupEnabled = snapshot.child("usePercentageMarkup").getValue(Boolean.class);
                    Double defaultMarkup = snapshot.child("defaultMarkupPercent").getValue(Double.class);

                    if (isMarkupEnabled != null) usePercentageMarkup = isMarkupEnabled;
                    if (defaultMarkup != null) defaultMarkupPercent = defaultMarkup;

                    updateLayoutForSelectedType(); // Refresh UI State based on rules
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void calculateSellingPrice() {
        if (!usePercentageMarkup) return;

        try {
            String costStr = costPriceET.getText().toString();
            double cost = costStr.isEmpty() ? 0 : Double.parseDouble(costStr);

            // Formula: Selling Price = Cost + (Cost * (Markup % / 100))
            double sellingPrice = cost + (cost * (defaultMarkupPercent / 100));
            sellingPriceET.setText(String.format(Locale.US, "%.2f", sellingPrice));

        } catch (Exception e) {
            sellingPriceET.setText("0.00");
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
        final EditText etNewUnit = new EditText(this);
        etNewUnit.setHint("Main Unit (e.g., Tub)");
        etNewUnit.setTextColor(textColor);
        layout.addView(etNewUnit);
        TextView tvInfo = new TextView(this);
        tvInfo.setText("\nDoes this unit contain sub-amounts?");
        tvInfo.setTextSize(12);
        tvInfo.setTextColor(textColor);
        layout.addView(tvInfo);
        final EditText etSubAmount = new EditText(this);
        etSubAmount.setHint("Amount (e.g., 50)");
        etSubAmount.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        etSubAmount.setTextColor(textColor);
        layout.addView(etSubAmount);
        final EditText etSubUnitName = new EditText(this);
        etSubUnitName.setHint("Sub-unit name (e.g., scoops)");
        etSubUnitName.setTextColor(textColor);
        layout.addView(etSubUnitName);
        builder.setView(layout);
        builder.setPositiveButton("Add", (dialog, which) -> {
            String mainUnit = etNewUnit.getText().toString().trim();
            String subAmount = etSubAmount.getText().toString().trim();
            String subName = etSubUnitName.getText().toString().trim();
            if (!mainUnit.isEmpty()) {
                unitList.add(unitList.size() - 1, mainUnit);
                unitAdapter.notifyDataSetChanged();

                lastSelectedUnit = mainUnit;

                unitSpinner.setSelection(unitList.size() - 2);
                if (!subAmount.isEmpty()) {
                    layoutPiecesPerUnit.setVisibility(View.VISIBLE);
                    etPiecesPerUnit.setText(subAmount);
                    etPiecesPerUnit.setHint(subName + " per " + mainUnit);
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
            quantityET.setVisibility(View.GONE);
            unitSpinner.setVisibility(View.GONE);

            costPriceET.setEnabled(false);
            costPriceET.setFocusable(false);
            costPriceET.setFocusableInTouchMode(false);
            costPriceET.setLongClickable(false);
            costPriceET.setCursorVisible(false);
            costPriceET.setHint("Auto-calculated Cost");
            costPriceET.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.dirtyWhite));

            // REVISION 1: Apply Markup Logic UI
            if (usePercentageMarkup) {
                sellingPriceET.setEnabled(false);
                sellingPriceET.setFocusable(false);
                sellingPriceET.setFocusableInTouchMode(false);
                sellingPriceET.setLongClickable(false);
                sellingPriceET.setCursorVisible(false);
                sellingPriceET.setHint("Selling (Auto Markup " + defaultMarkupPercent + "%)");
                sellingPriceET.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.dirtyWhite));
            } else {
                sellingPriceET.setEnabled(true);
                sellingPriceET.setFocusable(true);
                sellingPriceET.setFocusableInTouchMode(true);
                sellingPriceET.setLongClickable(true);
                sellingPriceET.setCursorVisible(true);
                sellingPriceET.setHint("Selling Price");
                sellingPriceET.setBackgroundTintList(null);
            }

            if (layoutPiecesPerUnit != null) layoutPiecesPerUnit.setVisibility(View.GONE);

            updateMainCostFromBOM();
        } else {
            layoutConfigurations.setVisibility(View.GONE);
            quantityET.setVisibility(View.VISIBLE);
            unitSpinner.setVisibility(View.VISIBLE);

            costPriceET.setEnabled(true);
            costPriceET.setFocusable(true);
            costPriceET.setFocusableInTouchMode(true);
            costPriceET.setLongClickable(true);
            costPriceET.setCursorVisible(true);
            costPriceET.setHint("Cost (₱)");
            costPriceET.setBackgroundTintList(null);

            // Hide selling price entirely for raw materials
            sellingPriceET.setText("");
            sellingPriceET.setEnabled(false);
            sellingPriceET.setHint("N/A for raw");

            String selectedUnit = unitSpinner.getSelectedItem() != null ? unitSpinner.getSelectedItem().toString() : "";
            boolean isBulkContainer = selectedUnit.equalsIgnoreCase("box") ||
                    selectedUnit.equalsIgnoreCase("pack") ||
                    selectedUnit.equalsIgnoreCase("L") ||
                    selectedUnit.equalsIgnoreCase("kg") ||
                    selectedUnit.equalsIgnoreCase("tub");

            if (layoutPiecesPerUnit != null) {
                layoutPiecesPerUnit.setVisibility(isBulkContainer ? View.VISIBLE : View.GONE);
            }
        }
    }

    private void loadInventoryForCalculations() {
        productRepository.getAllProducts().observe(this, products -> {
            inventoryProducts.clear();
            Set<String> dynamicUnits = new HashSet<>(baseUnitList);

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
        calculateSellingPrice(); // Update Selling price automatically
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
            Spinner spinnerRawMaterial = row.findViewById(R.id.spinnerRawMaterial);
            Spinner spinnerUnit = row.findViewById(R.id.spinnerUnit);
            final List<String> rowUnits = new ArrayList<>(unitList);
            ArrayAdapter<String> adapterForThisRow = getAdaptiveAdapter(rowUnits);

            if (spinnerUnit != null) {
                spinnerUnit.setAdapter(adapterForThisRow);
                spinnerUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        if (rowUnits.get(pos).equals("+ Add Custom Unit...")) showCustomUnitDialog();
                    }
                    @Override public void onNothingSelected(AdapterView<?> p) {}
                });
            }
            if (spinnerRawMaterial != null) {
                spinnerRawMaterial.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        showInventorySelectionDialog(itemName -> {
                            ArrayAdapter<String> tempAdapter = getAdaptiveAdapter(Collections.singletonList(itemName));
                            spinnerRawMaterial.setAdapter(tempAdapter);
                        });
                    }
                    return true;
                });
            }
            View btnDelete = row.findViewById(R.id.btnDelete);
            if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedBOM.isEmpty()) addRow.run();
        else {
            for (Map<String, Object> bom : savedBOM) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_bom, null);
                Spinner spinnerRawMaterial = row.findViewById(R.id.spinnerRawMaterial);
                EditText etQty = row.findViewById(R.id.etDeductQty);
                Spinner spinnerUnit = row.findViewById(R.id.spinnerUnit);

                if (spinnerRawMaterial != null) {
                    String savedMat = (String) bom.get("materialName");
                    spinnerRawMaterial.setAdapter(getAdaptiveAdapter(Collections.singletonList(savedMat)));
                    spinnerRawMaterial.setOnTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            showInventorySelectionDialog(itemName -> {
                                ArrayAdapter<String> tempAdapter = getAdaptiveAdapter(Collections.singletonList(itemName));
                                spinnerRawMaterial.setAdapter(tempAdapter);
                            });
                        }
                        return true;
                    });
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
                CheckBox cbIsEssential = row.findViewById(R.id.cbIsEssential);
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
                Spinner spinMat = row.findViewById(R.id.spinnerRawMaterial);
                EditText etQty = row.findViewById(R.id.etDeductQty);
                Spinner spinUnit = row.findViewById(R.id.spinnerUnit);

                String materialName = spinMat != null && spinMat.getSelectedItem() != null ? spinMat.getSelectedItem().toString().trim() : "";
                String qtyStr = etQty != null ? etQty.getText().toString().trim() : "";
                String unitStr = spinUnit != null && spinUnit.getSelectedItem() != null ? spinUnit.getSelectedItem().toString() : "pcs";
                CheckBox cbIsEssential = row.findViewById(R.id.cbIsEssential);
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
        List<String> currentDialogUnits = new ArrayList<>(dialogUnits);
        currentDialogUnits.remove("+ Add Custom Unit...");
        ArrayAdapter<String> rowUnitAdapter = getAdaptiveAdapter(currentDialogUnits);

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_addon, null);
            Spinner spinnerAddonItem = row.findViewById(R.id.spinnerAddonItem);
            Spinner spinnerUnit = row.findViewById(R.id.spinnerAddonUnit);
            final List<String> rowUnits = new ArrayList<>(unitList);
            ArrayAdapter<String> adapterForThisRow = getAdaptiveAdapter(rowUnits);

            if (spinnerAddonItem != null) {
                spinnerAddonItem.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        showInventorySelectionDialog(itemName -> {
                            ArrayAdapter<String> tempAdapter = getAdaptiveAdapter(Collections.singletonList(itemName));
                            spinnerAddonItem.setAdapter(tempAdapter);
                        });
                    }
                    return true;
                });
            }
            if (spinnerUnit != null) {
                spinnerUnit.setAdapter(adapterForThisRow);
                spinnerUnit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                        if (rowUnits.get(pos).equals("+ Add Custom Unit...")) showCustomUnitDialog();
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
                Spinner spinnerAddonItem = row.findViewById(R.id.spinnerAddonItem);
                EditText etPrice = row.findViewById(R.id.etAddonPrice);
                EditText etQty = row.findViewById(R.id.etAddonQty);
                Spinner spinnerUnit = row.findViewById(R.id.spinnerAddonUnit);

                if (spinnerAddonItem != null) {
                    String savedName = (String) addon.get("name");
                    spinnerAddonItem.setAdapter(getAdaptiveAdapter(Collections.singletonList(savedName)));
                    spinnerAddonItem.setOnTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            showInventorySelectionDialog(itemName -> {
                                ArrayAdapter<String> tempAdapter = getAdaptiveAdapter(Collections.singletonList(itemName));
                                spinnerAddonItem.setAdapter(tempAdapter);
                            });
                        }
                        return true;
                    });
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
                Spinner spinnerAddonItem = row.findViewById(R.id.spinnerAddonItem);
                EditText etPrice = row.findViewById(R.id.etAddonPrice);
                EditText etQty = row.findViewById(R.id.etAddonQty);
                Spinner spinnerUnit = row.findViewById(R.id.spinnerAddonUnit);

                String name = spinnerAddonItem != null && spinnerAddonItem.getSelectedItem() != null ? spinnerAddonItem.getSelectedItem().toString().trim() : "";
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

        List<String> sugarLevels = Arrays.asList("100", "75", "50", "25", "0");

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_note, null);
            Spinner spinnerNoteValue = row.findViewById(R.id.spinnerNoteValue);
            if (spinnerNoteValue != null) spinnerNoteValue.setAdapter(getAdaptiveAdapter(sugarLevels));

            View btnDelete = row.findViewById(R.id.btnDelete);
            if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedNotes.isEmpty()) addRow.run();
        else {
            for (Map<String, String> note : savedNotes) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_note, null);
                Spinner spinnerNoteValue = row.findViewById(R.id.spinnerNoteValue);
                if (spinnerNoteValue != null) {
                    spinnerNoteValue.setAdapter(getAdaptiveAdapter(sugarLevels));
                    String cleanValue = note.get("value");
                    if (cleanValue != null && cleanValue.endsWith("%")) cleanValue = cleanValue.replace("%", "");
                    int pos = sugarLevels.indexOf(cleanValue);
                    if (pos >= 0) spinnerNoteValue.setSelection(pos);
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
                Spinner spinnerNoteValue = row.findViewById(R.id.spinnerNoteValue);

                if (tvType != null && spinnerNoteValue != null) {
                    String type = tvType.getText().toString().trim();
                    String value = spinnerNoteValue.getSelectedItem() != null ? spinnerNoteValue.getSelectedItem().toString() : "";
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

        TextView tvHelperInfo = view.findViewById(R.id.tvHelperInfo);
        if(tvHelperInfo == null) {
            // Programmatically inject helper text at bottom
            TextView helper = new TextView(this);
            helper.setText("Note: Input size pricing as Percentage Markup (e.g., 20 for +20%). Leave blank to use Base Price.");
            helper.setTextSize(12);
            helper.setTextColor(Color.GRAY);
            helper.setPadding(32, 8, 32, 16);
            ((LinearLayout) view).addView(helper, 1);
        }

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_size, null);
            Spinner spinnerLinked = row.findViewById(R.id.spinnerLinkedInventory);
            MaterialButtonToggleGroup toggleHotCold = row.findViewById(R.id.toggleHotCold);
            EditText etPrice = row.findViewById(R.id.etSizePrice);

            // REVISION 1: Change label to show it's a percentage
            if(etPrice != null) etPrice.setHint("+ Markup %");

            Runnable updateCups = () -> {
                boolean isHot = toggleHotCold.getCheckedButtonId() == R.id.btnHot;
                List<String> filteredCups = new ArrayList<>();
                filteredCups.add("Select Cup...");
                for (Product p : inventoryProducts) {
                    String prodName = p.getProductName().toLowerCase();
                    if (prodName.contains("cup")) {
                        if (isHot && (prodName.contains("hot") || prodName.contains("paper"))) filteredCups.add(p.getProductName());
                        else if (!isHot && (prodName.contains("cold") || prodName.contains("plastic") || prodName.contains("pet"))) filteredCups.add(p.getProductName());
                    }
                }
                if (spinnerLinked != null) spinnerLinked.setAdapter(getAdaptiveAdapter(filteredCups));
            };

            if (toggleHotCold != null) {
                toggleHotCold.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                    if (isChecked) updateCups.run();
                });
                updateCups.run();
            }

            View btnDelete = row.findViewById(R.id.btnDelete);
            if (btnDelete != null) btnDelete.setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedSizes.isEmpty()) addRow.run();
        else {
            for (Map<String, Object> size : savedSizes) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_size, null);
                EditText etPrice = row.findViewById(R.id.etSizePrice);
                Spinner spinnerLinked = row.findViewById(R.id.spinnerLinkedInventory);
                MaterialButtonToggleGroup toggleHotCold = row.findViewById(R.id.toggleHotCold);

                if (etPrice != null) {
                    etPrice.setHint("+ Markup %");
                    Object savedPrice = size.get("price");
                    if(savedPrice != null && !savedPrice.toString().equals("0") && !savedPrice.toString().equals("0.0")) {
                        etPrice.setText(String.valueOf(savedPrice));
                    }
                }

                String savedLinked = (String) size.get("linkedMaterial");

                Runnable updateCups = () -> {
                    boolean isHot = toggleHotCold.getCheckedButtonId() == R.id.btnHot;
                    List<String> filteredCups = new ArrayList<>();
                    filteredCups.add("Select Cup...");
                    for (Product p : inventoryProducts) {
                        String prodName = p.getProductName().toLowerCase();
                        if (prodName.contains("cup")) {
                            if (isHot && (prodName.contains("hot") || prodName.contains("paper"))) filteredCups.add(p.getProductName());
                            else if (!isHot && (prodName.contains("cold") || prodName.contains("plastic") || prodName.contains("pet"))) filteredCups.add(p.getProductName());
                        }
                    }
                    if (spinnerLinked != null) {
                        ArrayAdapter<String> adapter = getAdaptiveAdapter(filteredCups);
                        spinnerLinked.setAdapter(adapter);
                        if (savedLinked != null) {
                            for (int i = 0; i < adapter.getCount(); i++) {
                                if (adapter.getItem(i).equals(savedLinked)) {
                                    spinnerLinked.setSelection(i);
                                    break;
                                }
                            }
                        }
                    }
                };

                if (toggleHotCold != null) {
                    toggleHotCold.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
                        if (isChecked) updateCups.run();
                    });
                    if (savedLinked != null) {
                        String lower = savedLinked.toLowerCase();
                        if (lower.contains("cold") || lower.contains("plastic") || lower.contains("pet")) {
                            toggleHotCold.check(R.id.btnCold);
                        } else {
                            toggleHotCold.check(R.id.btnHot);
                        }
                    } else {
                        toggleHotCold.check(R.id.btnHot);
                    }
                    updateCups.run();
                }

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
                EditText etPrice = row.findViewById(R.id.etSizePrice);
                Spinner spinnerLinked = row.findViewById(R.id.spinnerLinkedInventory);

                if (etPrice != null && spinnerLinked != null) {
                    String priceStr = etPrice.getText().toString().trim();
                    String linkedMaterial = spinnerLinked.getSelectedItem() != null && !spinnerLinked.getSelectedItem().toString().equals("Select Cup...") ? spinnerLinked.getSelectedItem().toString() : "";

                    if (!linkedMaterial.isEmpty()) {
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", linkedMaterial);
                        // Save as Percentage Markup value. Blank defaults to 0% (Base Price)
                        map.put("price", priceStr.isEmpty() ? 0.0 : Double.parseDouble(priceStr));
                        map.put("linkedMaterial", linkedMaterial);
                        map.put("deductQty", 1.0);
                        map.put("unit", "pcs");
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

    private void showInventorySelectionDialog(OnInventorySelectedListener listener) {
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

        spinnerFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { applyFilter.run(); }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        lvItems.setOnItemClickListener((parent, view1, position, id) -> {
            Product selected = filteredList.get(position);
            if (listener != null) listener.onSelected(selected.getProductName());
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

                        // Force update of cost and trigger auto markup
                        costPriceET.setText(String.valueOf(p.getCostPrice()));
                        if(usePercentageMarkup) calculateSellingPrice();
                        else sellingPriceET.setText(String.valueOf(p.getSellingPrice()));

                        if (quantityET != null) quantityET.setText(String.valueOf(p.getQuantity()));

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
                                loadedUnit.equalsIgnoreCase("tub") ||
                                loadedUnit.equalsIgnoreCase("can") ||
                                loadedUnit.equalsIgnoreCase("kg") ||
                                loadedUnit.equalsIgnoreCase("L");

                        if (p.getPiecesPerUnit() > 1 || isBulk) {
                            if (layoutPiecesPerUnit != null) layoutPiecesPerUnit.setVisibility(View.VISIBLE);
                            if (etPiecesPerUnit != null) etPiecesPerUnit.setText(String.valueOf(p.getPiecesPerUnit()));

                            subUnitList.clear();
                            for (String u : unitList) {
                                if (!u.equals("+ Add Custom Unit...") && !u.equalsIgnoreCase(loadedUnit)) {
                                    subUnitList.add(u);
                                }
                            }
                            if (!subUnitList.contains("pcs")) subUnitList.add(0, "pcs");

                            if (loadedUnit.equalsIgnoreCase("kg") && !subUnitList.contains("g")) subUnitList.add("g");
                            if (loadedUnit.equalsIgnoreCase("L") && !subUnitList.contains("ml")) subUnitList.add("ml");

                            if (subUnitAdapter != null) subUnitAdapter.notifyDataSetChanged();

                            String savedSalesUnit = p.getSalesUnit() != null ? p.getSalesUnit() : "pcs";
                            if (subUnitSpinner != null) {
                                for (int i = 0; i < subUnitSpinner.getAdapter().getCount(); i++) {
                                    if (subUnitSpinner.getAdapter().getItem(i).toString().equalsIgnoreCase(savedSalesUnit)) {
                                        subUnitSpinner.setSelection(i);
                                        break;
                                    }
                                }
                            }
                        } else {
                            if (layoutPiecesPerUnit != null) layoutPiecesPerUnit.setVisibility(View.GONE);
                        }

                        if (p.getUnit() != null && unitSpinner != null) {
                            lastSelectedUnit = p.getUnit();
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
            try { sellingPrice = Double.parseDouble(sellingPriceET.getText() != null && !sellingPriceET.getText().toString().isEmpty() ? sellingPriceET.getText().toString() : "0"); } catch (Exception ignored) {}
            try { costPrice = Double.parseDouble(costPriceET.getText() != null && !costPriceET.getText().toString().isEmpty() ? costPriceET.getText().toString() : "0"); } catch (Exception ignored) {}
        } else {
            try { costPrice = Double.parseDouble(costPriceET.getText() != null && !costPriceET.getText().toString().isEmpty() ? costPriceET.getText().toString() : "0"); } catch (Exception ignored) {}
            try { qty = Integer.parseInt(quantityET != null && quantityET.getText() != null && !quantityET.getText().toString().isEmpty() ? quantityET.getText().toString() : "0"); } catch (Exception ignored) {}
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
        String mainUnit = unitSpinner.getSelectedItem() != null ? unitSpinner.getSelectedItem().toString() : "pcs";
        p.setUnit(mainUnit);

        // REVISION 2: Promos are now completely separated. Set to false to avoid database artifacts.
        p.setPromo(false);
        p.setPromoName("");
        p.setTemporaryPromo(false);
        p.setPromoStartDate(0L);
        p.setPromoEndDate(0L);

        if (isFromPromoBuilder) {
            p.setPromo(true);
            p.setPromoName(autoPromoName);
            p.setTemporaryPromo(autoIsTemporary);

            if (autoIsTemporary && !autoStart.isEmpty() && !autoEnd.isEmpty()) {
                try {
                    // Use the existing simpleDateFormat from your class for consistency
                    p.setPromoStartDate(expiryFormat.parse(autoStart).getTime());
                    p.setPromoEndDate(expiryFormat.parse(autoEnd).getTime());
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }
        } else {
            // Regular non-promo product logic
            p.setPromo(false);
            p.setPromoName("");
        }

        if (layoutPiecesPerUnit != null && layoutPiecesPerUnit.getVisibility() == View.VISIBLE && subUnitSpinner != null && subUnitSpinner.getSelectedItem() != null) {
            p.setSalesUnit(subUnitSpinner.getSelectedItem().toString());
        } else {
            p.setSalesUnit(mainUnit);
        }

        if (expiryDate > 0) {
            p.setExpiryDate(expiryDate);
        } else if (expiryStr.isEmpty()) {
            p.setExpiryDate(0L);
        }

        int piecesPerUnit = 1;
        if (layoutPiecesPerUnit != null && layoutPiecesPerUnit.getVisibility() == View.VISIBLE) {
            try { piecesPerUnit = Integer.parseInt(etPiecesPerUnit.getText() != null ? etPiecesPerUnit.getText().toString().trim() : "1"); } catch (Exception ignored) {}
        }
        if (piecesPerUnit <= 0) piecesPerUnit = 1;
        p.setPiecesPerUnit(piecesPerUnit);

        if (existingProductToEdit == null) {
            long now = System.currentTimeMillis();
            p.setProductId("PROD_" + UUID.randomUUID().toString().substring(0, 8));
            p.setDateAdded(now);
            p.setActive(true);
            p.setOwnerAdminId(currentUserId);
        }

        if (selectedImagePath != null && !selectedImagePath.isEmpty()) p.setImagePath(selectedImagePath);

        if (switchSellOnPOS.isChecked()) {
            p.setSizesList(savedSizes != null ? savedSizes : p.getSizesList());
            p.setAddonsList(savedAddons != null ? savedAddons : p.getAddonsList());
            p.setNotesList(savedNotes != null ? savedNotes : p.getNotesList());
            p.setBomList(savedBOM != null ? savedBOM : p.getBomList());
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