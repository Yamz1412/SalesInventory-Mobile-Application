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

public class AddProductActivity extends BaseActivity {
    private ImageButton btnAddPhoto;
    private TextInputEditText productNameET, productGroupET, sellingPriceET, quantityET, costPriceET, lowStockLevelET, expiryDateET;
    private Button addBtn, cancelBtn;
    private Spinner unitSpinner, existingGroupSpinner;

    // Configurations
    private SwitchMaterial switchSizes, switchAddons, switchNotes, switchBOM, switchForSaleOnly;
    private LinearLayout layoutBuyingUnitQtyCritical;

    private ProductRepository productRepository;
    private AuthManager authManager;
    private SimpleDateFormat expiryFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
    private String selectedImagePath;
    private Calendar expiryCalendar = Calendar.getInstance();
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    // Full objects for Search and Filter logic
    private List<Product> inventoryProducts = new ArrayList<>();

    // Data Storage for the Dynamic Dialogs
    private List<Map<String, Object>> savedSizes = new ArrayList<>();
    private List<Map<String, Object>> savedAddons = new ArrayList<>();
    private List<Map<String, String>> savedNotes = new ArrayList<>();
    private List<Map<String, Object>> savedBOM = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_product);
        productRepository = SalesInventoryApplication.getProductRepository();

        btnAddPhoto = findViewById(R.id.btnAddPhoto);
        productNameET = findViewById(R.id.productNameET);
        productGroupET = findViewById(R.id.productGroupET);
        sellingPriceET = findViewById(R.id.sellingPriceET);
        expiryDateET = findViewById(R.id.expiryDateET);

        costPriceET = findViewById(R.id.costPriceET);
        quantityET = findViewById(R.id.quantityET);
        lowStockLevelET = findViewById(R.id.lowStockLevelET);
        unitSpinner = findViewById(R.id.unitSpinner);
        layoutBuyingUnitQtyCritical = findViewById(R.id.layout_buying_unit_qty_critical);

        addBtn = findViewById(R.id.addBtn);
        cancelBtn = findViewById(R.id.cancelBtn);
        switchSizes = findViewById(R.id.switchSizes);
        switchAddons = findViewById(R.id.switchAddons);
        switchNotes = findViewById(R.id.switchNotes);
        switchBOM = findViewById(R.id.switchBOM);
        switchForSaleOnly = findViewById(R.id.switchForSaleOnly);

        setupCategorySpinner();
        setupImagePickers();
        loadInventoryForBOM();

        String[] units = {"pcs", "ml", "L", "oz", "g", "kg", "box", "pack"};
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        unitSpinner.setAdapter(unitAdapter);

        authManager = AuthManager.getInstance();
        authManager.refreshCurrentUserStatus(success -> {
            if (!authManager.isCurrentUserAdmin()) {
                Toast.makeText(AddProductActivity.this, "Error: User not approved", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        switchForSaleOnly.setOnCheckedChangeListener((btn, isChecked) -> updateLayoutForSelectedType());

        switchSizes.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showSizesDialog(); else savedSizes.clear(); });
        switchAddons.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showAddonsDialog(); else savedAddons.clear(); });
        switchNotes.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showNotesDialog(); else savedNotes.clear(); });
        switchBOM.setOnCheckedChangeListener((btn, isChecked) -> { if (isChecked) showBOMDialog(); else savedBOM.clear(); });

        addBtn.setOnClickListener(v -> attemptAdd());
        cancelBtn.setOnClickListener(v -> finish());
        btnAddPhoto.setOnClickListener(v -> tryPickImage());
        expiryDateET.setOnClickListener(v -> showExpiryDatePicker());

        Intent intent = getIntent();
        if (intent != null && intent.hasExtra("PREFILL_NAME")) {
            // Automatically switch it to an Inventory Item
            switchForSaleOnly.setChecked(false);

            // Pre-fill the details from the supplier delivery
            productNameET.setText(intent.getStringExtra("PREFILL_NAME"));

            double prefillCost = intent.getDoubleExtra("PREFILL_COST", 0.0);
            costPriceET.setText(String.valueOf(prefillCost));

            int prefillQty = intent.getIntExtra("PREFILL_QTY", 0);
            quantityET.setText(String.valueOf(prefillQty));

            String prefillUnit = intent.getStringExtra("PREFILL_UNIT");
            if (prefillUnit != null && !prefillUnit.isEmpty()) {
                String[] standardUnits = {"pcs", "ml", "L","oz", "g", "kg", "box", "pack"};
                for (int i = 0; i < standardUnits.length; i++) {
                    if (standardUnits[i].equalsIgnoreCase(prefillUnit)) {
                        unitSpinner.setSelection(i);
                        break;
                    }
                }
            }

            Toast.makeText(this, "Form pre-filled from Purchase Order!", Toast.LENGTH_LONG).show();
        }
        updateLayoutForSelectedType();
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

    // ==========================================
    // INVENTORY SELECTION DIALOG (WITH SEARCH & FILTER)
    // ==========================================
    private void showInventorySelectionDialog(TextView targetTextView) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_inventory_selection, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        EditText etSearch = view.findViewById(R.id.etSearchInventory);
        Spinner spinnerFilter = view.findViewById(R.id.spinnerFilterCategory);
        ListView lvItems = view.findViewById(R.id.lvInventoryItems);
        Button btnClose = view.findViewById(R.id.btnCloseSelection);

        // 1. Build Unique Category Filter
        List<String> categories = new ArrayList<>();
        categories.add("All Categories");
        for (Product p : inventoryProducts) {
            String cat = p.getCategoryName() != null && !p.getCategoryName().isEmpty() ? p.getCategoryName() : "Uncategorized";
            if (!categories.contains(cat)) categories.add(cat);
        }
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(catAdapter);

        // 2. Setup ListView Adapter
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

        // 3. Create the filtering logic
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

        // 4. Attach Listeners
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
            targetTextView.setText(selected.getProductName()); // Return name to original box
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }


    // ==========================================
    // BILL OF MATERIALS (BOM) LOGIC
    // ==========================================
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

            // Connect the Text View to open the Inventory Search Dialog
            TextView tvMaterial = row.findViewById(R.id.tvRawMaterial);
            tvMaterial.setOnClickListener(v -> showInventorySelectionDialog(tvMaterial));

            Spinner spinnerUnit = row.findViewById(R.id.spinnerUnit);
            spinnerUnit.setAdapter(rowUnitAdapter);

            ImageButton btnDelete = row.findViewById(R.id.btnDelete);
            btnDelete.setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedBOM.isEmpty()) {
            addRow.run();
        } else {
            for (Map<String, Object> bom : savedBOM) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_bom, null);

                TextView tvMaterial = row.findViewById(R.id.tvRawMaterial);
                tvMaterial.setText((String) bom.get("materialName"));
                tvMaterial.setOnClickListener(v -> showInventorySelectionDialog(tvMaterial));

                ((EditText) row.findViewById(R.id.etDeductQty)).setText(String.valueOf(bom.get("quantity")));

                Spinner spinnerUnit = row.findViewById(R.id.spinnerUnit);
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

                row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
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
                String materialName = ((TextView) row.findViewById(R.id.tvRawMaterial)).getText().toString().trim();
                String qtyStr = ((EditText) row.findViewById(R.id.etDeductQty)).getText().toString().trim();

                Spinner spinUnit = row.findViewById(R.id.spinnerUnit);
                String unitStr = spinUnit.getSelectedItem() != null ? spinUnit.getSelectedItem().toString() : "pcs";

                // Don't save if it says "Select Item" or is blank
                if (!materialName.isEmpty() && !materialName.contains("Select Item")) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("materialName", materialName);
                    map.put("quantity", qtyStr.isEmpty() ? 0.0 : Double.parseDouble(qtyStr));
                    map.put("unit", unitStr);
                    savedBOM.add(map);
                }
            }
            if (savedBOM.isEmpty()) switchBOM.setChecked(false);
            else Toast.makeText(this, "Recipe Saved!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }

    // ==========================================
    // SIZES, ADD-ONS, NOTES LOGIC (UNCHANGED)
    // ==========================================
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
            row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedSizes.isEmpty()) addRow.run();
        else {
            for (Map<String, Object> size : savedSizes) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_size, null);
                ((EditText) row.findViewById(R.id.etSizeName)).setText((String) size.get("name"));
                ((EditText) row.findViewById(R.id.etSizePrice)).setText(String.valueOf(size.get("price")));
                row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            }
        }
        btnAddRow.setOnClickListener(v -> addRow.run());
        btnCancel.setOnClickListener(v -> { if (savedSizes.isEmpty()) switchSizes.setChecked(false); dialog.dismiss(); });
        btnSave.setOnClickListener(v -> {
            savedSizes.clear();
            for (int i = 0; i < containerRows.getChildCount(); i++) {
                View row = containerRows.getChildAt(i);
                String name = ((EditText) row.findViewById(R.id.etSizeName)).getText().toString().trim();
                String priceStr = ((EditText) row.findViewById(R.id.etSizePrice)).getText().toString().trim();
                if (!name.isEmpty()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", name);
                    map.put("price", priceStr.isEmpty() ? 0.0 : Double.parseDouble(priceStr));
                    savedSizes.add(map);
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
            View row = LayoutInflater.from(this).inflate(R.layout.item_config_addon, null);
            row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedAddons.isEmpty()) addRow.run();
        else {
            for (Map<String, Object> addon : savedAddons) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_addon, null);
                ((EditText) row.findViewById(R.id.etAddonName)).setText((String) addon.get("name"));
                ((EditText) row.findViewById(R.id.etAddonPrice)).setText(String.valueOf(addon.get("price")));
                ((EditText) row.findViewById(R.id.etAddonCost)).setText(String.valueOf(addon.get("cost")));
                row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            }
        }
        btnAddRow.setOnClickListener(v -> addRow.run());
        btnCancel.setOnClickListener(v -> { if (savedAddons.isEmpty()) switchAddons.setChecked(false); dialog.dismiss(); });
        btnSave.setOnClickListener(v -> {
            savedAddons.clear();
            for (int i = 0; i < containerRows.getChildCount(); i++) {
                View row = containerRows.getChildAt(i);
                String name = ((EditText) row.findViewById(R.id.etAddonName)).getText().toString().trim();
                String priceStr = ((EditText) row.findViewById(R.id.etAddonPrice)).getText().toString().trim();
                String costStr = ((EditText) row.findViewById(R.id.etAddonCost)).getText().toString().trim();
                if (!name.isEmpty()) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("name", name);
                    map.put("price", priceStr.isEmpty() ? 0.0 : Double.parseDouble(priceStr));
                    map.put("cost", costStr.isEmpty() ? 0.0 : Double.parseDouble(costStr));
                    savedAddons.add(map);
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
            row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
            containerRows.addView(row);
        };

        if (savedNotes.isEmpty()) addRow.run();
        else {
            for (Map<String, String> note : savedNotes) {
                View row = LayoutInflater.from(this).inflate(R.layout.item_config_note, null);
                ((EditText) row.findViewById(R.id.etNoteType)).setText(note.get("type"));
                ((EditText) row.findViewById(R.id.etNoteValue)).setText(note.get("value"));
                row.findViewById(R.id.btnDelete).setOnClickListener(v -> containerRows.removeView(row));
                containerRows.addView(row);
            }
        }
        btnAddRow.setOnClickListener(v -> addRow.run());
        btnCancel.setOnClickListener(v -> { if (savedNotes.isEmpty()) switchNotes.setChecked(false); dialog.dismiss(); });
        btnSave.setOnClickListener(v -> {
            savedNotes.clear();
            for (int i = 0; i < containerRows.getChildCount(); i++) {
                View row = containerRows.getChildAt(i);
                String type = ((EditText) row.findViewById(R.id.etNoteType)).getText().toString().trim();
                String value = ((EditText) row.findViewById(R.id.etNoteValue)).getText().toString().trim();
                if (!type.isEmpty() || !value.isEmpty()) {
                    Map<String, String> map = new HashMap<>();
                    map.put("type", type);
                    map.put("value", value);
                    savedNotes.add(map);
                }
            }
            if (savedNotes.isEmpty()) switchNotes.setChecked(false);
            else Toast.makeText(this, "Notes saved!", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        dialog.show();
    }

    private void setupCategorySpinner() {
        existingGroupSpinner = findViewById(R.id.existingGroupSpinner);
        DatabaseReference categoryRef = FirebaseDatabase.getInstance().getReference("Categories");
        categoryRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<String> groups = new ArrayList<>();
                groups.add("Select Group");
                for (DataSnapshot child : snapshot.getChildren()) {
                    Category c = child.getValue(Category.class);
                    if (c != null) groups.add(c.getCategoryName());
                }
                ArrayAdapter<String> groupAdapter = new ArrayAdapter<>(AddProductActivity.this, android.R.layout.simple_spinner_item, groups);
                existingGroupSpinner.setAdapter(groupAdapter);
            }
            @Override public void onCancelled(DatabaseError error) {}
        });

        existingGroupSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position > 0) productGroupET.setText(parent.getItemAtPosition(position).toString());
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

    private void updateLayoutForSelectedType() {
        if (switchForSaleOnly.isChecked()) {
            layoutBuyingUnitQtyCritical.setVisibility(View.GONE);
            quantityET.setText(""); costPriceET.setText(""); lowStockLevelET.setText("");
        } else {
            layoutBuyingUnitQtyCritical.setVisibility(View.VISIBLE);
        }
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

        String categoryName = productGroupET.getText() != null ? productGroupET.getText().toString().trim() : "";
        if (categoryName.isEmpty()) { Toast.makeText(this, "Product Group is required", Toast.LENGTH_SHORT).show(); return; }

        String categoryId = categoryName.toLowerCase(Locale.ROOT).replace(" ", "_");
        boolean wantMenu = switchForSaleOnly.isChecked();
        String productType = wantMenu ? "Menu" : "Inventory";

        double sellingPrice = 0, costPrice = 0;
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
            try { reorderLevel = Integer.parseInt(lowStockLevelET.getText() != null ? lowStockLevelET.getText().toString() : "0"); } catch (Exception ignored) {}
            criticalLevel = 1;
            if (qty < 0) qty = 0;
            if (reorderLevel < 0) reorderLevel = 0;
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
        p.setReorderLevel(reorderLevel);
        p.setCeilingLevel(ceilingLevel);
        p.setUnit(unitSpinner.getSelectedItem() != null ? unitSpinner.getSelectedItem().toString() : "");
        p.setExpiryDate(expiryDate);
        long now = System.currentTimeMillis();
        p.setDateAdded(now);
        p.setActive(true);
        p.setImagePath(selectedImagePath);

        // ATTACH CONFIGURATIONS
        if (!savedSizes.isEmpty()) p.setSizesList(savedSizes);
        if (!savedAddons.isEmpty()) p.setAddonsList(savedAddons);
        if (!savedNotes.isEmpty()) p.setNotesList(savedNotes);
        if (!savedBOM.isEmpty()) p.setBomList(savedBOM);

        productRepository.addProduct(p, selectedImagePath, new ProductRepository.OnProductAddedListener() {
            @Override
            public void onProductAdded(String productId) {
                runOnUiThread(() -> {
                    Toast.makeText(AddProductActivity.this, "Product added successfully", Toast.LENGTH_SHORT).show();
                    finish();
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(AddProductActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }
}