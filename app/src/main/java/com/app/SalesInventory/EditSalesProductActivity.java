package com.app.SalesInventory;

import android.Manifest;
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
import androidx.appcompat.widget.Toolbar;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class EditSalesProductActivity extends BaseActivity {

    private ImageButton btnEditPhoto;
    private TextInputEditText productNameET, sellingPriceET, costPriceET, lowStockET;
    private TextView tvRecipeSummary, tvSizesSummary, tvAddonsSummary, tvSugarSummary;
    private AutoCompleteTextView productLineET;
    private AutoCompleteTextView productTypeET;
    private Button btnSaveEdit, btnCancelEdit;

    private SwitchMaterial switchSizes, switchAddons, switchEnableSugar, switchBOM;
    private boolean usePercentageMarkup = false;
    private double defaultMarkupPercent = 0.0;
    private String currentUserId;

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

    private List<String> dialogUnits = new ArrayList<>();
    private List<String> unitList = new ArrayList<>();
    private List<String> baseUnitList = new ArrayList<>(Arrays.asList(
            "pcs", "ml", "L", "oz", "g", "kg", "box", "pack", "scoop", "pump", "shot"
    ));

    public interface OnInventorySelectedListener {
        void onSelected(String itemName);
    }

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

        editProductId = getIntent().getStringExtra("PRODUCT_ID");
        if (editProductId == null) editProductId = getIntent().getStringExtra("productId");
        if (editProductId == null) editProductId = getIntent().getStringExtra("product_id");

        if (editProductId == null) {
            Toast.makeText(this, "Product ID missing", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        productRepository = SalesInventoryApplication.getProductRepository();

        currentUserId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (currentUserId == null || currentUserId.isEmpty()) {
            currentUserId = AuthManager.getInstance().getCurrentUserId();
        }

        btnEditPhoto    = findViewById(R.id.btnEditPhoto);
        productNameET   = findViewById(R.id.productNameET);
        productLineET   = findViewById(R.id.productLineET);
        productTypeET   = findViewById(R.id.productTypeET);
        costPriceET     = findViewById(R.id.costPriceET);
        sellingPriceET  = findViewById(R.id.sellingPriceET);
        lowStockET      = findViewById(R.id.lowStockET);

        switchBOM    = findViewById(R.id.switchBOM);
        switchSizes  = findViewById(R.id.switchSizes);
        switchAddons = findViewById(R.id.switchAddons);
        switchEnableSugar = findViewById(R.id.switchEnableSugar);

        tvRecipeSummary = findViewById(R.id.tvRecipeSummary);
        tvSizesSummary = findViewById(R.id.tvSizesSummary);
        tvAddonsSummary = findViewById(R.id.tvAddonsSummary);
        tvSugarSummary = findViewById(R.id.tvSugarSummary);

        btnSaveEdit   = findViewById(R.id.btnSaveEdit);
        btnCancelEdit = findViewById(R.id.btnCancelEdit);

        // Fetch pricing rules from Settings
        loadPricingRules();
        updateConfigUI();

        setupImagePickers();
        loadInventoryForCalculations();

        // Listen for cost price changes to auto-calculate markup
        costPriceET.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (usePercentageMarkup && savedBOM.isEmpty()) {
                    calculateSellingPrice();
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnEditPhoto.setOnClickListener(v -> tryPickImage());
        btnCancelEdit.setOnClickListener(v -> finish());
        btnSaveEdit.setOnClickListener(v -> attemptEdit());

        loadProductData();

        if (switchSizes != null) {
            handleSwitchLogic(switchSizes, savedSizes, "Sizes", this::showSizesDialog);
            tvSizesSummary.setOnClickListener(v -> showSizesDialog());
        }
        if (switchAddons != null) {
            handleSwitchLogic(switchAddons, savedAddons, "Add-ons", this::showAddonsDialog);
            tvAddonsSummary.setOnClickListener(v -> showAddonsDialog());
        }
        if (switchBOM != null) {
            handleSwitchLogic(switchBOM, savedBOM, "Recipe", this::showBOMDialog);
            tvRecipeSummary.setOnClickListener(v -> showBOMDialog());
        }
        if (switchEnableSugar != null) {
            switchEnableSugar.setOnCheckedChangeListener((btn, isChecked) -> updateConfigUI());
        }
    }

    // =========================================================
    // PRICING LOGIC
    // =========================================================
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

                    updatePricingUI();
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updatePricingUI() {
        if (usePercentageMarkup) {
            sellingPriceET.setEnabled(true);
            sellingPriceET.setFocusable(true);
            sellingPriceET.setFocusableInTouchMode(true);
            sellingPriceET.setLongClickable(true);
            sellingPriceET.setCursorVisible(true);
            sellingPriceET.setHint("Selling (Auto Markup " + defaultMarkupPercent + "%)");
            sellingPriceET.setBackgroundTintList(null); // Removes the gray locked background
            calculateSellingPrice();
        } else {
            sellingPriceET.setEnabled(true);
            sellingPriceET.setFocusable(true);
            sellingPriceET.setFocusableInTouchMode(true);
            sellingPriceET.setLongClickable(true);
            sellingPriceET.setCursorVisible(true);
            sellingPriceET.setHint("Selling Price");
            sellingPriceET.setBackgroundTintList(null);
        }
    }

    private void calculateSellingPrice() {
        if (!usePercentageMarkup) return;
        try {
            String costStr = costPriceET.getText().toString();
            double cost = costStr.isEmpty() ? 0 : Double.parseDouble(costStr);
            double sellingPrice = cost + (cost * (defaultMarkupPercent / 100));
            double roundedPrice = Math.round(sellingPrice);

            sellingPriceET.setText(String.format(Locale.US, "%.2f", roundedPrice));
        } catch (Exception e) {
            sellingPriceET.setText("0.00");
        }
    }

    private int getIndexIgnoreCase(List<String> list, String target) {
        if (target == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).equalsIgnoreCase(target)) return i;
        }
        return -1;
    }

    private void loadInventoryForCalculations() {
        productRepository.getAllProducts().observe(this, products -> {
            inventoryProducts.clear();
            Set<String> dynamicUnits = new java.util.LinkedHashSet<>(baseUnitList);

            if (products != null) {
                for (Product p : products) {
                    if (p.getUnit() != null && !p.getUnit().isEmpty()) dynamicUnits.add(p.getUnit());
                    if (p.isActive() && !"Menu".equals(p.getProductType())) {
                        inventoryProducts.add(p);
                    }
                }
            }
            dialogUnits.clear();
            dialogUnits.addAll(dynamicUnits);
            unitList.clear();
            unitList.addAll(dynamicUnits);
            unitList.add("+ Add Custom Unit...");
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
            if (mat != null) {
                int ppu = mat.getPiecesPerUnit() > 0 ? mat.getPiecesPerUnit() : 1;
                String invUnit = mat.getUnit() != null ? mat.getUnit().toLowerCase() : "pcs";

                double costPrice = mat.getCostPrice();

                if ((invUnit.equals("ml") || invUnit.equals("g")) && ppu == 1 && costPrice > 50) {
                    double estimatedBulkVolume = Math.max(1000.0, Math.ceil(mat.getQuantity() / 1000.0) * 1000.0);
                    costPrice = costPrice / estimatedBulkVolume;
                }

                Object[] conversion = UnitConverterUtil.convertBaseInventoryUnit(mat.getQuantity(), invUnit, bUnit, ppu);
                String newInvUnit = (String) conversion[1];
                double deductionAmount = UnitConverterUtil.calculateDeductionAmount(bQty, newInvUnit, bUnit, ppu);
                double unitCost = UnitConverterUtil.calculateTrueUnitCost(costPrice, newInvUnit, ppu);
                totalCost += (deductionAmount * unitCost);
            }
        }

        if (costPriceET != null) {
            costPriceET.setText(String.format(Locale.US, "%.2f", totalCost));
        }
        try { calculateSellingPrice(); } catch (Exception ignored) {}
    }

    private void autoSelectSubUnit(String materialName, Spinner spinnerUnit) {
        if (materialName == null || spinnerUnit == null) return;
        Product mat = findInventoryProduct(materialName);
        if (mat == null) return;

        String baseUnit = mat.getUnit() != null ? mat.getUnit().toLowerCase() : "pcs";
        String targetUnit = baseUnit; // Default to the same unit

        // The Auto-Switch Logic
        if (baseUnit.equals("kg")) targetUnit = "g";
        else if (baseUnit.equals("l")) targetUnit = "ml";
        else if (baseUnit.equals("box") || baseUnit.equals("pack") || baseUnit.equals("tub") || baseUnit.equals("can")) {
            targetUnit = mat.getSalesUnit() != null ? mat.getSalesUnit().toLowerCase() : "pcs";
        }

        // Apply it safely to the spinner ignoring case
        for (int i = 0; i < spinnerUnit.getAdapter().getCount(); i++) {
            if (spinnerUnit.getAdapter().getItem(i).toString().equalsIgnoreCase(targetUnit)) {
                spinnerUnit.setSelection(i);
                break;
            }
        }
    }

    private void showBOMDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_config_bom, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).setCancelable(false).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        LinearLayout containerRows = view.findViewById(R.id.containerRows);
        ImageButton btnAddRow = view.findViewById(R.id.btnAddRow);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        Button btnSave   = view.findViewById(R.id.btnSave);

        List<String> currentDialogUnits = new ArrayList<>(dialogUnits);
        currentDialogUnits.remove("+ Add Custom Unit...");
        ArrayAdapter<String> rowUnitAdapter = getAdaptiveAdapter(currentDialogUnits);

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_bom, null);
            Spinner spinnerRawMaterial = row.findViewById(R.id.spinnerRawMaterial);
            Spinner spinnerUnit = row.findViewById(R.id.spinnerUnit);

            if (spinnerUnit != null) spinnerUnit.setAdapter(rowUnitAdapter);

            if (spinnerRawMaterial != null) {
                spinnerRawMaterial.setOnTouchListener((v, event) -> {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        showInventorySelectionDialog(itemName -> {
                            ArrayAdapter<String> tempAdapter = getAdaptiveAdapter(Collections.singletonList(itemName));
                            spinnerRawMaterial.setAdapter(tempAdapter);
                            autoSelectSubUnit(itemName, spinnerUnit);
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
                EditText etQty      = row.findViewById(R.id.etDeductQty);
                Spinner spinnerUnit = row.findViewById(R.id.spinnerUnit);
                CheckBox cbIsEssential = row.findViewById(R.id.cbIsEssential);

                if (spinnerRawMaterial != null) {
                    String savedMat = (String) bom.get("materialName");
                    spinnerRawMaterial.setAdapter(getAdaptiveAdapter(Collections.singletonList(savedMat)));
                    spinnerRawMaterial.setOnTouchListener((v, event) -> {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            showInventorySelectionDialog(itemName -> {
                                ArrayAdapter<String> tempAdapter = getAdaptiveAdapter(Collections.singletonList(itemName));
                                spinnerRawMaterial.setAdapter(tempAdapter);
                                autoSelectSubUnit(itemName, spinnerUnit);
                            });
                        }
                        return true;
                    });
                }

                if (etQty != null) etQty.setText(String.valueOf(bom.get("quantity")));

                if (spinnerUnit != null) {
                    spinnerUnit.setAdapter(rowUnitAdapter);
                    String savedUnit = bom.containsKey("unit") && bom.get("unit") != null ? String.valueOf(bom.get("unit")).trim() : "";
                    int unitIndex = getIndexIgnoreCase(currentDialogUnits, savedUnit);
                    spinnerUnit.post(() -> spinnerUnit.setSelection(Math.max(0, unitIndex)));
                }

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
        btnCancel.setOnClickListener(v -> {
            if (savedBOM.isEmpty() && switchBOM != null) switchBOM.setChecked(false);
            dialog.dismiss();
        });
        btnSave.setOnClickListener(v -> {
            savedBOM.clear();
            for (int i = 0; i < containerRows.getChildCount(); i++) {
                View row = containerRows.getChildAt(i);
                Spinner spinMat = row.findViewById(R.id.spinnerRawMaterial);
                EditText etQty = row.findViewById(R.id.etDeductQty);
                Spinner spinUnit = row.findViewById(R.id.spinnerUnit);
                CheckBox cbIsEssential = row.findViewById(R.id.cbIsEssential);

                String materialName = spinMat != null && spinMat.getSelectedItem() != null ? spinMat.getSelectedItem().toString().trim() : "";
                String qtyStr  = etQty != null ? etQty.getText().toString().trim() : "";
                String unitStr = spinUnit != null && spinUnit.getSelectedItem() != null ? spinUnit.getSelectedItem().toString().trim() : "pcs";
                boolean isEssential = cbIsEssential == null || cbIsEssential.isChecked();

                if (!materialName.isEmpty() && !materialName.contains("Select Item")) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("materialName", materialName);
                    map.put("quantity", qtyStr.isEmpty() ? 0.0 : Double.parseDouble(qtyStr));
                    map.put("unit", unitStr); // Clean string saved to database
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
            TextView helper = new TextView(this);
            helper.setText("Note: Input size pricing as Percentage Markup (e.g., 20 for +20%). Leave blank to use Base Price.");
            helper.setTextSize(12);
            helper.setTextColor(Color.GRAY);
            helper.setPadding(32, 8, 32, 16);
            ((LinearLayout) view).addView(helper, 1);
        }

        String[] sizeOptions = {"Small", "Medium", "Large", "8oz", "12oz", "16oz", "Custom"};
        ArrayAdapter<String> sizeAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sizeOptions);

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_size, null);
            Spinner spinnerLinked = row.findViewById(R.id.spinnerLinkedInventory);
            Spinner spinnerSizeName = row.findViewById(R.id.spinnerSizeName);
            EditText etCustomSizeName = row.findViewById(R.id.etCustomSizeName);
            SwitchMaterial switchHotCold = row.findViewById(R.id.switchHotCold);
            TextView tvHotColdLabel = row.findViewById(R.id.tvHotColdLabel);
            EditText etPrice = row.findViewById(R.id.etSizePrice);

            if(etPrice != null) etPrice.setHint("+ Markup %");
            spinnerSizeName.setAdapter(sizeAdapter);

            spinnerSizeName.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    if ("Custom".equals(sizeOptions[position])) {
                        etCustomSizeName.setVisibility(View.VISIBLE);
                    } else {
                        etCustomSizeName.setVisibility(View.GONE);
                        etCustomSizeName.setText("");
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> parent) {}
            });

            Runnable updateCups = () -> {
                boolean isHot = switchHotCold.isChecked();
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

            if (switchHotCold != null) {
                switchHotCold.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        tvHotColdLabel.setText("Hot");
                        tvHotColdLabel.setTextColor(Color.parseColor("#FF5722"));
                        spinnerSizeName.setSelection(0); // Force to Small
                    } else {
                        tvHotColdLabel.setText("Cold");
                        tvHotColdLabel.setTextColor(Color.parseColor("#2196F3"));
                    }
                    updateCups.run();
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
                Spinner spinnerLinked = row.findViewById(R.id.spinnerLinkedInventory);
                Spinner spinnerSizeName = row.findViewById(R.id.spinnerSizeName);
                EditText etCustomSizeName = row.findViewById(R.id.etCustomSizeName);
                SwitchMaterial switchHotCold = row.findViewById(R.id.switchHotCold);
                TextView tvHotColdLabel = row.findViewById(R.id.tvHotColdLabel);
                EditText etPrice = row.findViewById(R.id.etSizePrice);

                spinnerSizeName.setAdapter(sizeAdapter);

                if (etPrice != null) {
                    etPrice.setHint("+ Markup %");
                    Object savedPrice = size.get("priceDiff");
                    if (savedPrice == null) savedPrice = size.get("price"); // Fallback to old format
                    if(savedPrice != null && !savedPrice.toString().equals("0") && !savedPrice.toString().equals("0.0")) {
                        etPrice.setText(String.valueOf(savedPrice));
                    }
                }

                // Restore Size Name
                String savedName = (String) size.get("name");
                int nameIndex = -1;
                for (int i = 0; i < sizeOptions.length; i++) {
                    if (sizeOptions[i].equalsIgnoreCase(savedName)) {
                        nameIndex = i; break;
                    }
                }
                if (nameIndex != -1) {
                    spinnerSizeName.setSelection(nameIndex);
                } else if (savedName != null && !savedName.isEmpty()) {
                    spinnerSizeName.setSelection(sizeOptions.length - 1); // "Custom"
                    etCustomSizeName.setVisibility(View.VISIBLE);
                    etCustomSizeName.setText(savedName);
                }

                spinnerSizeName.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                    @Override
                    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                        if ("Custom".equals(sizeOptions[position])) etCustomSizeName.setVisibility(View.VISIBLE);
                        else { etCustomSizeName.setVisibility(View.GONE); etCustomSizeName.setText(""); }
                    }
                    @Override public void onNothingSelected(AdapterView<?> parent) {}
                });

                String savedLinked = (String) size.get("linkedMaterial");
                String savedType = (String) size.get("type");

                Runnable updateCups = () -> {
                    boolean isHot = switchHotCold.isChecked();
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

                if (switchHotCold != null) {
                    switchHotCold.setOnCheckedChangeListener((buttonView, isChecked) -> {
                        if (isChecked) {
                            tvHotColdLabel.setText("Hot");
                            tvHotColdLabel.setTextColor(Color.parseColor("#FF5722"));
                        } else {
                            tvHotColdLabel.setText("Cold");
                            tvHotColdLabel.setTextColor(Color.parseColor("#2196F3"));
                        }
                        updateCups.run();
                    });

                    if ("Hot".equalsIgnoreCase(savedType)) {
                        switchHotCold.setChecked(true);
                    } else {
                        switchHotCold.setChecked(false);
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
                Spinner spinnerSizeName = row.findViewById(R.id.spinnerSizeName);
                EditText etCustomSizeName = row.findViewById(R.id.etCustomSizeName);
                EditText etPrice = row.findViewById(R.id.etSizePrice);
                SwitchMaterial switchHotCold = row.findViewById(R.id.switchHotCold);
                Spinner spinnerLinked = row.findViewById(R.id.spinnerLinkedInventory);

                String selectedSize = spinnerSizeName.getSelectedItem().toString();
                String finalSizeName = "Custom".equals(selectedSize) ? etCustomSizeName.getText().toString().trim() : selectedSize;

                if (finalSizeName.isEmpty()) continue; // Skip blank rows

                String priceStr = etPrice.getText().toString().trim();
                String linkedMaterial = spinnerLinked.getSelectedItem() != null && !spinnerLinked.getSelectedItem().toString().equals("Select Cup...") ? spinnerLinked.getSelectedItem().toString() : "";
                String type = switchHotCold.isChecked() ? "Hot" : "Cold";

                Map<String, Object> map = new HashMap<>();
                map.put("name", finalSizeName);
                map.put("type", type);
                map.put("priceDiff", priceStr.isEmpty() ? 0.0 : Double.parseDouble(priceStr));
                map.put("linkedMaterial", linkedMaterial);
                map.put("deductQty", 1.0);
                map.put("unit", "pcs");
                savedSizes.add(map);
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

        List<String> currentDialogUnits = new ArrayList<>(dialogUnits);
        currentDialogUnits.remove("+ Add Custom Unit...");
        ArrayAdapter<String> rowUnitAdapter = getAdaptiveAdapter(currentDialogUnits);

        Runnable addRow = () -> {
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_addon, null);
            Spinner spinnerAddonItem = row.findViewById(R.id.spinnerAddonItem);
            Spinner spinnerUnit = row.findViewById(R.id.spinnerAddonUnit);

            if (spinnerUnit != null) spinnerUnit.setAdapter(rowUnitAdapter);

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

                if (spinnerUnit != null) {
                    spinnerUnit.setAdapter(rowUnitAdapter);
                    String savedUnit = addon.containsKey("unit") && addon.get("unit") != null ? String.valueOf(addon.get("unit")).trim() : "";
                    int unitIndex = getIndexIgnoreCase(currentDialogUnits, savedUnit);
                    spinnerUnit.post(() -> spinnerUnit.setSelection(Math.max(0, unitIndex)));
                }

                if (etPrice != null) etPrice.setText(String.valueOf(addon.get("price")));
                if (etQty != null && addon.containsKey("deductQty")) etQty.setText(String.valueOf(addon.get("deductQty")));

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
                String unitStr = spinnerUnit != null && spinnerUnit.getSelectedItem() != null ? spinnerUnit.getSelectedItem().toString().trim() : "pcs";

                if (!name.isEmpty()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", name);
                    map.put("price", priceStr.isEmpty() ? 0.0 : Double.parseDouble(priceStr));
                    map.put("deductQty", qtyStr.isEmpty() ? 0.0 : Double.parseDouble(qtyStr));
                    map.put("unit", unitStr); // Clean string saved to database
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

    private void showInventorySelectionDialog(OnInventorySelectedListener listener) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_inventory_selection, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        EditText etSearch       = view.findViewById(R.id.etSearchInventory);
        Spinner spinnerFilter   = view.findViewById(R.id.spinnerFilterCategory);
        ListView lvItems        = view.findViewById(R.id.lvInventoryItems);
        Button btnClose         = view.findViewById(R.id.btnCloseSelection);

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
                View v  = super.getView(position, convertView, parent);
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

                if (matchesSearch && matchesCat && p.getQuantity() > 0) {
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
            if (listener != null) listener.onSelected(selected.getProductName());
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private ArrayAdapter<String> getAdaptiveAdapter(List<String> items) {
        boolean isDark = false;
        try { isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark"); } catch (Exception e) {}
        final int textColor = isDark ? Color.WHITE : Color.BLACK;
        final int bgColor = isDark ? Color.parseColor("#2C2C2C") : Color.WHITE;

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
                view.setBackgroundColor(bgColor);
                ((TextView) view).setTextColor(textColor);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private ArrayAdapter<String> getAdaptiveDropdownAdapter(List<String> items) {
        boolean isDark = false;
        try { isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark"); } catch (Exception ignored) {}
        int textColor = isDark ? android.graphics.Color.WHITE : android.graphics.Color.BLACK;

        return new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, items) {
            @NonNull @Override
            public View getView(int position, View convertView, @NonNull ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                ((TextView) v).setTextColor(textColor);
                return v;
            }
        };
    }

    // =========================================================
    // IMAGE PICKER
    // =========================================================
    private void setupImagePickers() {
        imagePickerLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                Uri uri = result.getData().getData();
                if (uri != null) { selectedImagePath = uri.toString(); btnEditPhoto.setImageURI(uri); }
            }
        });
        permissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) openImagePicker();
            else Toast.makeText(this, "Permission required", Toast.LENGTH_SHORT).show();
        });
    }

    private void tryPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) openImagePicker();
        else if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) openImagePicker();
        else permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE);
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    // =========================================================
    // LOAD & SAVE DATA
    // =========================================================
    private void loadProductData() {
        productRepository.getProductById(editProductId, new ProductRepository.OnProductFetchedListener() {
            @Override
            public void onProductFetched(Product p) {
                runOnUiThread(() -> {
                    if (p != null) {
                        existingProductToEdit = p;

                        productNameET.setText(p.getProductName());
                        if (productLineET != null) productLineET.setText(p.getProductLine());
                        if (productTypeET != null) productTypeET.setText(p.getCategoryName());
                        if (costPriceET   != null) costPriceET.setText(String.valueOf(p.getCostPrice()));
                        if (lowStockET    != null) lowStockET.setText(String.valueOf(p.getReorderLevel()));

                        if (usePercentageMarkup) {
                            calculateSellingPrice();
                        } else if (sellingPriceET != null) {
                            sellingPriceET.setText(String.valueOf(p.getSellingPrice()));
                        }

                        if (p.getImagePath() != null && !p.getImagePath().isEmpty()) selectedImagePath = p.getImagePath();
                        else if (p.getImageUrl() != null && !p.getImageUrl().isEmpty()) selectedImagePath = p.getImageUrl();

                        if (selectedImagePath != null && !isFinishing()) {
                            Glide.with(EditSalesProductActivity.this).load(selectedImagePath).diskCacheStrategy(DiskCacheStrategy.ALL).into(btnEditPhoto);
                        }

                        savedSizes  = p.getSizesList()  != null ? p.getSizesList()  : new ArrayList<>();
                        savedAddons = p.getAddonsList() != null ? p.getAddonsList() : new ArrayList<>();
                        savedNotes  = p.getNotesList()  != null ? p.getNotesList()  : new ArrayList<>();
                        savedBOM    = p.getBomList()    != null ? p.getBomList()    : new ArrayList<>();

                        if (switchSizes  != null) switchSizes.setOnCheckedChangeListener(null);
                        if (switchAddons != null) switchAddons.setOnCheckedChangeListener(null);
                        if (switchBOM    != null) switchBOM.setOnCheckedChangeListener(null);

                        if (switchSizes  != null) switchSizes.setChecked(!savedSizes.isEmpty());
                        if (switchAddons != null) switchAddons.setChecked(!savedAddons.isEmpty());
                        boolean hasSugar = false;
                        if (savedNotes != null) {
                            for (Map<String, String> note : savedNotes) {
                                if ("sugar_enabled".equals(note.get("type"))) {
                                    hasSugar = true;
                                    break;
                                }
                            }
                        }
                        updateMainCostFromBOM();
                    }
                });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(EditSalesProductActivity.this, "Failed to load product data: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void attemptEdit() {
        String line = productLineET != null && productLineET.getText() != null ? productLineET.getText().toString().trim() : "";
        String type = productTypeET != null && productTypeET.getText() != null ? productTypeET.getText().toString().trim() : "";
        String name = productNameET.getText() != null ? productNameET.getText().toString().trim() : "";

        if (name.isEmpty() || type.isEmpty()) {
            Toast.makeText(this, "Product name and category are required", Toast.LENGTH_SHORT).show();
            return;
        }

        String categoryId = type.toLowerCase(Locale.ROOT).replace(" ", "_");

        double sellingPrice = 0, costPrice = 0;
        int reorderLevel = 0;

        try { sellingPrice  = Double.parseDouble(sellingPriceET != null && sellingPriceET.getText() != null ? sellingPriceET.getText().toString() : "0"); } catch (Exception ignored) {}
        try { costPrice     = Double.parseDouble(costPriceET    != null && costPriceET.getText()    != null ? costPriceET.getText().toString()    : "0"); } catch (Exception ignored) {}
        try { reorderLevel  = Integer.parseInt(  lowStockET     != null && lowStockET.getText()     != null ? lowStockET.getText().toString()     : "0"); } catch (Exception ignored) {}

        if (existingProductToEdit == null) existingProductToEdit = new Product();
        existingProductToEdit.setProductName(name);
        existingProductToEdit.setProductLine(line);
        existingProductToEdit.setCategoryName(type);
        existingProductToEdit.setCategoryId(categoryId);
        existingProductToEdit.setProductType("Menu");
        existingProductToEdit.setSellable(true);
        existingProductToEdit.setActive(true);
        existingProductToEdit.setSellingPrice(sellingPrice);
        existingProductToEdit.setCostPrice(costPrice);
        existingProductToEdit.setReorderLevel(reorderLevel);

        existingProductToEdit.setSizesList(savedSizes);
        existingProductToEdit.setAddonsList(savedAddons);
        existingProductToEdit.setBomList(savedBOM);

        // Process the simplified Sugar Switch
        List<Map<String, String>> finalNotes = new ArrayList<>();
        if (switchEnableSugar != null && switchEnableSugar.isChecked()) {
            Map<String, String> sugarNote = new HashMap<>();
            sugarNote.put("type", "sugar_enabled");
            sugarNote.put("value", "true");
            finalNotes.add(sugarNote);
        }
        existingProductToEdit.setNotesList(finalNotes);

        productRepository.updateProduct(existingProductToEdit, selectedImagePath, new ProductRepository.OnProductUpdatedListener() {
            @Override public void onProductUpdated() {
                runOnUiThread(() -> {
                    Toast.makeText(EditSalesProductActivity.this, "Updated successfully", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(EditSalesProductActivity.this,
                        "Update Error: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    private void handleSwitchLogic(SwitchMaterial sw, List<?> list, String title, Runnable showDialogAction) {
        sw.setOnClickListener(v -> {
            boolean isChecked = sw.isChecked();
            if (isChecked) {
                if (list.isEmpty()) {
                    showDialogAction.run();
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle(title + " Active")
                            .setMessage("You have saved details. Do you want to edit them or just enable?")
                            .setPositiveButton("Edit Items", (d, w) -> showDialogAction.run())
                            .setNegativeButton("Just Enable", (d, w) -> updateConfigUI())
                            .setNeutralButton("Cancel", (d, w) -> {
                                sw.setChecked(false);
                                updateConfigUI();
                            })
                            .show();
                }
            } else {
                new AlertDialog.Builder(this)
                        .setTitle("Disable " + title + "?")
                        .setMessage("Your details will be hidden but NOT deleted. Continue?")
                        .setPositiveButton("Disable", (d, w) -> updateConfigUI())
                        .setNegativeButton("Keep On", (d, w) -> {
                            sw.setChecked(true);
                            updateConfigUI();
                        })
                        .show();
            }
        });
    }

    private void updateConfigUI() {
        if (tvSizesSummary != null) {
            if (savedSizes.isEmpty()) tvSizesSummary.setText("Click to Add");
            else {
                StringBuilder sb = new StringBuilder();
                for (Map<String, Object> size : savedSizes) sb.append(size.get("name")).append(", ");
                String text = sb.toString();
                if (text.length() > 20) text = text.substring(0, 17) + "...";
                tvSizesSummary.setText(text.endsWith(", ") ? text.substring(0, text.length() - 2) : text);
            }
            tvSizesSummary.setAlpha(switchSizes.isChecked() ? 1.0f : 0.4f);
        }

        if (tvAddonsSummary != null) {
            if (savedAddons.isEmpty()) tvAddonsSummary.setText("Click to Add");
            else {
                StringBuilder sb = new StringBuilder();
                for (Map<String, Object> addon : savedAddons) sb.append(addon.get("name")).append(", ");
                String text = sb.toString();
                if (text.length() > 20) text = text.substring(0, 17) + "...";
                tvAddonsSummary.setText(text.endsWith(", ") ? text.substring(0, text.length() - 2) : text);
            }
            tvAddonsSummary.setAlpha(switchAddons.isChecked() ? 1.0f : 0.4f);
        }

        if (tvRecipeSummary != null) {
            if (savedBOM.isEmpty()) tvRecipeSummary.setText("Click to Add");
            else tvRecipeSummary.setText(savedBOM.size() + " Items Added");
            tvRecipeSummary.setAlpha(switchBOM.isChecked() ? 1.0f : 0.4f);
        }

        if (tvSugarSummary != null) {
            tvSugarSummary.setText(switchEnableSugar.isChecked() ? "Enabled" : "Off");
        }
    }
}