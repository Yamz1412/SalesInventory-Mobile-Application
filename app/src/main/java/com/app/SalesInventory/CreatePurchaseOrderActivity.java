package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreatePurchaseOrderActivity extends BaseActivity {
    private Toolbar toolbar;

    private TextInputEditText etSupplierName, etSupplierPhone, etExpectedDate, etExpectedTime;
    private RecyclerView recyclerViewItems;
    private TextView tvTotalAmount;
    private Button btnAddItem, btnCreatePO, btnCancelForm;

    private POItemAdapter adapter;
    private List<POItem> poItems;
    private DatabaseReference poRef;

    private Calendar expectedCalendar;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    private ProductRepository productRepository;
    private List<Product> availableProducts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_purchase_order);

        toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");
        productRepository = SalesInventoryApplication.getProductRepository();

        etSupplierName = findViewById(R.id.etSupplierName);
        etSupplierPhone = findViewById(R.id.etSupplierPhone);
        etExpectedDate = findViewById(R.id.etExpectedDate);
        etExpectedTime = findViewById(R.id.etExpectedTime);

        recyclerViewItems = findViewById(R.id.recyclerViewItems);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        btnAddItem = findViewById(R.id.btnAddItem);
        btnCreatePO = findViewById(R.id.btnCreatePO);
        btnCancelForm = findViewById(R.id.btnCancel); // Bottom horizontal cancel button

        poItems = new ArrayList<>();
        adapter = new POItemAdapter(this, poItems, position -> {
            poItems.remove(position);
            adapter.notifyItemRemoved(position);
            updateTotal();
        }, this::updateTotal);

        recyclerViewItems.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewItems.setAdapter(adapter);

        expectedCalendar = Calendar.getInstance();

        loadProductsForSearch();

        etExpectedDate.setOnClickListener(v -> showDatePicker());
        etExpectedTime.setOnClickListener(v -> showTimePicker());
        btnAddItem.setOnClickListener(v -> showAddItemDialog());
        btnCreatePO.setOnClickListener(v -> createPurchaseOrder());
        btnCancelForm.setOnClickListener(v -> finish());
    }

    private void loadProductsForSearch() {
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                availableProducts.clear();
                for (Product p : products) {
                    if (p.isActive()) availableProducts.add(p);
                }
            }
        });
    }

    private void showDatePicker() {
        DatePickerDialog dialog = new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            expectedCalendar.set(Calendar.YEAR, year);
            expectedCalendar.set(Calendar.MONTH, month);
            expectedCalendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            etExpectedDate.setText(dateFormat.format(expectedCalendar.getTime()));
        }, expectedCalendar.get(Calendar.YEAR), expectedCalendar.get(Calendar.MONTH), expectedCalendar.get(Calendar.DAY_OF_MONTH));
        dialog.getDatePicker().setMinDate(System.currentTimeMillis());
        dialog.show();
    }

    private void showTimePicker() {
        new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            expectedCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay);
            expectedCalendar.set(Calendar.MINUTE, minute);
            etExpectedTime.setText(timeFormat.format(expectedCalendar.getTime()));
        }, expectedCalendar.get(Calendar.HOUR_OF_DAY), expectedCalendar.get(Calendar.MINUTE), false).show();
    }

    private void showAddItemDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_add_po_item, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextInputEditText etProduct = dialogView.findViewById(R.id.etProduct);
        ImageButton btnSearchInventory = dialogView.findViewById(R.id.btnSearchInventory);
        TextInputEditText etQuantity = dialogView.findViewById(R.id.etQuantity);
        TextInputEditText etUnitPrice = dialogView.findViewById(R.id.etUnitPrice);
        Spinner spinnerUnit = dialogView.findViewById(R.id.spinnerUnit);
        Button btnCancel = dialogView.findViewById(R.id.btnCancel);
        Button btnAdd = dialogView.findViewById(R.id.btnAdd);

        String[] units = {"pcs", "ml", "L", "oz", "g", "kg", "box", "pack"};
        ArrayAdapter<String> unitAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, units);
        unitAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerUnit.setAdapter(unitAdapter);

        final Product[] selectedProduct = new Product[1];

        // OPEN REUSABLE INVENTORY DIALOG
        btnSearchInventory.setOnClickListener(v -> showInventorySelectionDialog(etProduct, etUnitPrice, spinnerUnit, selectedProduct, units));

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnAdd.setOnClickListener(v -> {
            String nameInput = etProduct.getText() != null ? etProduct.getText().toString().trim() : "";
            String qtyStr = etQuantity.getText() != null ? etQuantity.getText().toString().trim() : "";
            String priceStr = etUnitPrice.getText() != null ? etUnitPrice.getText().toString().trim() : "";
            String selectedUnit = spinnerUnit.getSelectedItem() != null ? spinnerUnit.getSelectedItem().toString() : "pcs";

            if (nameInput.isEmpty() || qtyStr.isEmpty() || priceStr.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            int qty = Integer.parseInt(qtyStr);
            double price = Double.parseDouble(priceStr);

            String productId = "";
            if (selectedProduct[0] != null && selectedProduct[0].getProductName().equalsIgnoreCase(nameInput)) {
                productId = selectedProduct[0].getProductId();
                selectedUnit = selectedProduct[0].getUnit() != null ? selectedProduct[0].getUnit() : selectedUnit;
            } else {
                Toast.makeText(this, "Note: New Unlinked Item. Will prompt registration on delivery.", Toast.LENGTH_LONG).show();
                productId = "CUSTOM_" + System.currentTimeMillis();
            }

            poItems.add(new POItem(productId, nameInput, qty, price, selectedUnit));
            adapter.notifyDataSetChanged();
            updateTotal();
            dialog.dismiss();
        });

        dialog.show();
    }

    // ============================================================================
    // REUSING INVENTORY SELECTION DIALOG (Search & Filter)
    // ============================================================================
    private void showInventorySelectionDialog(TextInputEditText targetNameBox, TextInputEditText targetCostBox, Spinner targetUnitSpinner, Product[] selectedProductRef, String[] unitArray) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_inventory_selection, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        EditText etSearch = view.findViewById(R.id.etSearchInventory);
        Spinner spinnerFilter = view.findViewById(R.id.spinnerFilterCategory);
        ListView lvItems = view.findViewById(R.id.lvInventoryItems);
        Button btnClose = view.findViewById(R.id.btnCloseSelection);

        List<String> categories = new ArrayList<>();
        categories.add("All Categories");
        for (Product p : availableProducts) {
            String cat = p.getCategoryName() != null && !p.getCategoryName().isEmpty() ? p.getCategoryName() : "Uncategorized";
            if (!categories.contains(cat)) categories.add(cat);
        }
        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, categories);
        catAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerFilter.setAdapter(catAdapter);

        List<Product> filteredList = new ArrayList<>(availableProducts);
        ArrayAdapter<Product> listAdapter = new ArrayAdapter<Product>(this, android.R.layout.simple_list_item_1, filteredList) {
            @NonNull
            @Override
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
            for (Product p : availableProducts) {
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
            selectedProductRef[0] = selected;

            // Auto-fill the form
            targetNameBox.setText(selected.getProductName());
            targetCostBox.setText(String.valueOf(selected.getCostPrice()));

            if (selected.getUnit() != null) {
                for (int i = 0; i < unitArray.length; i++) {
                    if (unitArray[i].equalsIgnoreCase(selected.getUnit())) {
                        targetUnitSpinner.setSelection(i);
                        break;
                    }
                }
            }
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
    // ============================================================================

    private void updateTotal() {
        double total = 0;
        for (POItem item : poItems) total += item.getSubtotal();
        tvTotalAmount.setText(String.format(Locale.getDefault(), "₱%.2f", total));
    }

    private void createPurchaseOrder() {
        String supplier = etSupplierName.getText() != null ? etSupplierName.getText().toString().trim() : "";
        String supplierPhone = etSupplierPhone.getText() != null ? etSupplierPhone.getText().toString().trim() : "";
        String dateStr = etExpectedDate.getText() != null ? etExpectedDate.getText().toString().trim() : "";

        if (supplier.isEmpty()) { Toast.makeText(this, "Please enter supplier name", Toast.LENGTH_SHORT).show(); return; }
        if (dateStr.isEmpty()) { Toast.makeText(this, "Please select expected delivery date", Toast.LENGTH_SHORT).show(); return; }
        if (poItems.isEmpty()) { Toast.makeText(this, "Please add at least one item", Toast.LENGTH_SHORT).show(); return; }

        String id = poRef.push().getKey();
        if (id == null) return;

        String poNumber = "PO-" + System.currentTimeMillis() / 1000;
        double total = 0;
        for (POItem item : poItems) total += item.getSubtotal();

        PurchaseOrder po = new PurchaseOrder(
                id, poNumber, supplier, supplierPhone, PurchaseOrder.STATUS_PENDING,
                System.currentTimeMillis(), total, new ArrayList<>(poItems)
        );
        po.setOwnerAdminId(AuthManager.getInstance().getCurrentUserId());

        Map<String, Object> poData = po.toMap();
        poData.put("expectedDeliveryDate", expectedCalendar.getTimeInMillis());

        poRef.child(id).setValue(poData)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Purchase Order Created Successfully", Toast.LENGTH_LONG).show();
                    finish();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}