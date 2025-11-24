package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
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
import java.util.UUID;

public class CreatePurchaseOrderActivity extends AppCompatActivity {

    // UI Components
    private Toolbar toolbar;
    private TextInputEditText etSupplierName, etOrderDate, etExpectedDate, etNotes;
    private RecyclerView recyclerViewItems;
    private TextView tvTotalAmount;
    private Button btnAddItem, btnCreatePO;

    // Adapters & Data
    private POItemAdapter adapter;
    private List<POItem> poItems;
    private DatabaseReference poRef;
    private Calendar calendar;

    // For Product Spinner
    private ProductRepository productRepository;
    private List<Product> availableProducts = new ArrayList<>();
    private List<String> productNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_purchase_order);

        initializeViews();

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Create Purchase Order");
        }

        // Initialize Firebase & Repository
        poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");
        productRepository = SalesInventoryApplication.getProductRepository();

        // Load Products for the Spinner
        loadProductsForSpinner();

        setupRecyclerView();
        setupListeners();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        etSupplierName = findViewById(R.id.etSupplierName);
        etOrderDate = findViewById(R.id.etOrderDate);
        etExpectedDate = findViewById(R.id.etExpectedDate);
        etNotes = findViewById(R.id.etNotes);
        recyclerViewItems = findViewById(R.id.recyclerViewItems);
        tvTotalAmount = findViewById(R.id.tvTotalAmount);
        btnAddItem = findViewById(R.id.btnAddItem);
        btnCreatePO = findViewById(R.id.btnCreatePO);

        calendar = Calendar.getInstance();
        updateDateLabel(etOrderDate);
    }

    private void loadProductsForSpinner() {
        // Observe existing products to populate the dropdown
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                availableProducts.clear();
                productNames.clear();
                for (Product p : products) {
                    availableProducts.add(p);
                    productNames.add(p.getProductName());
                }
            }
        });
    }

    private void setupRecyclerView() {
        poItems = new ArrayList<>();
        adapter = new POItemAdapter(this, poItems, position -> {
            poItems.remove(position);
            adapter.notifyItemRemoved(position);
            calculateTotal();
        }, () -> calculateTotal());

        recyclerViewItems.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewItems.setAdapter(adapter);
    }

    private void setupListeners() {
        etOrderDate.setOnClickListener(v -> showDatePicker(etOrderDate));
        etExpectedDate.setOnClickListener(v -> showDatePicker(etExpectedDate));
        btnAddItem.setOnClickListener(v -> showAddItemDialog());
        btnCreatePO.setOnClickListener(v -> savePurchaseOrder());
    }

    private void showDatePicker(final EditText editText) {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(Calendar.YEAR, year);
            calendar.set(Calendar.MONTH, month);
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
            updateDateLabel(editText);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void updateDateLabel(EditText editText) {
        String myFormat = "MMM dd, yyyy";
        SimpleDateFormat sdf = new SimpleDateFormat(myFormat, Locale.getDefault());
        editText.setText(sdf.format(calendar.getTime()));
    }

    /**
     * Updated to match your dialog_add_po_item.xml
     */
    private void showAddItemDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_po_item, null);
        builder.setView(view);

        // Create the dialog instance so we can dismiss it later
        final AlertDialog dialog = builder.create();
        // Make background transparent for rounded corners if needed
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Initialize Dialog Views
        Spinner spinnerProduct = view.findViewById(R.id.spinnerProduct);
        TextInputEditText etQuantity = view.findViewById(R.id.etQuantity);
        TextInputEditText etUnitPrice = view.findViewById(R.id.etUnitPrice);
        Button btnAdd = view.findViewById(R.id.btnAdd);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        // Setup Spinner
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, productNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerProduct.setAdapter(spinnerAdapter);

        // Handle Add Button Click
        btnAdd.setOnClickListener(v -> {
            String qtyStr = etQuantity.getText().toString();
            String priceStr = etUnitPrice.getText().toString();

            if (spinnerProduct.getSelectedItem() == null) {
                Toast.makeText(this, "Please select a product", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!qtyStr.isEmpty() && !priceStr.isEmpty()) {
                try {
                    int qty = Integer.parseInt(qtyStr);
                    double price = Double.parseDouble(priceStr);

                    // Get selected product name
                    int selectedPosition = spinnerProduct.getSelectedItemPosition();
                    String productName = productNames.get(selectedPosition);
                    // Use product ID from the list if needed, or generate random ID for the PO item
                    String itemId = UUID.randomUUID().toString();

                    poItems.add(new POItem(itemId, productName, qty, price));
                    adapter.notifyDataSetChanged();
                    calculateTotal();

                    dialog.dismiss(); // Close dialog

                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show();
            }
        });

        // Handle Cancel Button Click
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void calculateTotal() {
        double total = 0;
        for (POItem item : poItems) {
            total += item.getSubtotal();
        }
        tvTotalAmount.setText(String.format(Locale.getDefault(), "₱%.2f", total));
    }

    private void savePurchaseOrder() {
        String supplier = etSupplierName.getText().toString().trim();

        if (supplier.isEmpty()) {
            etSupplierName.setError("Supplier name required");
            return;
        }

        if (poItems.isEmpty()) {
            Toast.makeText(this, "Please add at least one item", Toast.LENGTH_SHORT).show();
            return;
        }

        String id = poRef.push().getKey();
        String poNumber = "PO-" + System.currentTimeMillis() / 1000;

        double total = 0;
        for (POItem item : poItems) total += item.getSubtotal();

        PurchaseOrder po = new PurchaseOrder(id, poNumber, supplier, "Pending", System.currentTimeMillis(), total);

        if (id != null) {
            poRef.child(id).setValue(po).addOnSuccessListener(aVoid -> {
                Toast.makeText(this, "Purchase Order Created Successfully", Toast.LENGTH_SHORT).show();
                finish();
            }).addOnFailureListener(e -> {
                Toast.makeText(this, "Error saving order: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
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