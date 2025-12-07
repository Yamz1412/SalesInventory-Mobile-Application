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
import android.widget.Button;
import android.widget.EditText;
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

public class CreatePurchaseOrderActivity extends BaseActivity  {
    private Toolbar toolbar;
    private TextInputEditText etSupplierName, etSupplierPhone, etOrderDate, etExpectedDate, etNotes;
    private RecyclerView recyclerViewItems;
    private TextView tvTotalAmount;
    private Button btnAddItem, btnCreatePO;
    private POItemAdapter adapter;
    private List<POItem> poItems;
    private DatabaseReference poRef;
    private Calendar calendar;
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
        poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");
        productRepository = SalesInventoryApplication.getProductRepository();
        loadProductsForSpinner();
        setupRecyclerView();
        setupListeners();
    }

    private void initializeViews() {
        toolbar = findViewById(R.id.toolbar);
        etSupplierName = findViewById(R.id.etSupplierName);
        etSupplierPhone = findViewById(R.id.etSupplierPhone);
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
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                availableProducts.clear();
                productNames.clear();
                availableProducts.addAll(products);
                for (Product p : products) {
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
        }, this::calculateTotal);
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
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH);
        int day = now.get(Calendar.DAY_OF_MONTH);
        DatePickerDialog dialog = new DatePickerDialog(this, (view, y, m, d) -> {
            calendar.set(Calendar.YEAR, y);
            calendar.set(Calendar.MONTH, m);
            calendar.set(Calendar.DAY_OF_MONTH, d);
            updateDateLabel(editText);
        }, year, month, day);
        dialog.getDatePicker().setMinDate(System.currentTimeMillis());
        dialog.show();
    }

    private void updateDateLabel(EditText editText) {
        String myFormat = "MMM dd, yyyy";
        SimpleDateFormat sdf = new SimpleDateFormat(myFormat, Locale.getDefault());
        editText.setText(sdf.format(calendar.getTime()));
    }

    private void showAddItemDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_po_item, null);
        builder.setView(view);
        AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        TextInputEditText etProductName = view.findViewById(R.id.etProductName);
        TextInputEditText etQuantity = view.findViewById(R.id.etQuantity);
        TextInputEditText etUnitPrice = view.findViewById(R.id.etUnitPrice);
        Button btnAdd = view.findViewById(R.id.btnAdd);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        btnAdd.setOnClickListener(v -> {
            String nameStr = etProductName.getText() != null ? etProductName.getText().toString().trim() : "";
            String qtyStr = etQuantity.getText() != null ? etQuantity.getText().toString().trim() : "";
            String priceStr = etUnitPrice.getText() != null ? etUnitPrice.getText().toString().trim() : "";
            if (nameStr.isEmpty()) {
                Toast.makeText(this, "Please enter product name", Toast.LENGTH_SHORT).show();
                return;
            }
            if (qtyStr.isEmpty() || priceStr.isEmpty()) {
                Toast.makeText(this, "Please fill quantity and unit price", Toast.LENGTH_SHORT).show();
                return;
            }
            try {
                int qty = Integer.parseInt(qtyStr);
                double price = Double.parseDouble(priceStr);
                if (qty <= 0 || price < 0) {
                    Toast.makeText(this, "Invalid quantity or price", Toast.LENGTH_SHORT).show();
                    return;
                }
                String productId = findProductIdByName(nameStr);
                Product prod = findProductById(productId);
                if (prod != null) {
                    int ceiling = prod.getCeilingLevel() <= 0 ? Math.max(prod.getQuantity(), Math.max(prod.getReorderLevel() * 2, 100)) : prod.getCeilingLevel();
                    if (ceiling > 9999) ceiling = 9999;
                    int maxReceivable = ceiling - prod.getQuantity();
                    if (maxReceivable <= 0) {
                        Toast.makeText(this, "Cannot order this product: it is already at or above ceiling", Toast.LENGTH_LONG).show();
                        return;
                    }
                    if (qty > maxReceivable) {
                        Toast.makeText(this, "Quantity too large. Maximum receivable is " + maxReceivable, Toast.LENGTH_LONG).show();
                        return;
                    }
                }
                POItem existing = null;
                for (POItem it : poItems) {
                    if (it.getProductName().equalsIgnoreCase(nameStr)) {
                        existing = it;
                        break;
                    }
                }
                if (existing != null) {
                    existing.setQuantity(existing.getQuantity() + qty);
                    existing.setUnitPrice(price);
                } else {
                    poItems.add(new POItem(productId, nameStr, qty, price));
                }
                adapter.notifyDataSetChanged();
                calculateTotal();
                dialog.dismiss();
            } catch (NumberFormatException e) {
                Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            }
        });
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private Product findProductById(String id) {
        if (availableProducts == null) return null;
        for (Product p : availableProducts) {
            if (p.getProductId() != null && p.getProductId().equals(id)) return p;
        }
        return null;
    }

    private String findProductIdByName(String name) {
        if (availableProducts == null) return "";
        for (Product p : availableProducts) {
            if (p.getProductName() != null && p.getProductName().equalsIgnoreCase(name)) {
                return p.getProductId();
            }
        }
        return "";
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
        String supplierPhone = etSupplierPhone.getText() != null ? etSupplierPhone.getText().toString().trim() : "";
        if (supplier.isEmpty()) {
            etSupplierName.setError("Supplier name required");
            return;
        }
        if (poItems.isEmpty()) {
            Toast.makeText(this, "Please add at least one item", Toast.LENGTH_SHORT).show();
            return;
        }
        for (POItem item : poItems) {
            if (item.getProductId() != null && !item.getProductId().isEmpty()) {
                Product prod = findProductById(item.getProductId());
                if (prod != null) {
                    int ceiling = prod.getCeilingLevel() <= 0 ? Math.max(prod.getQuantity(), Math.max(prod.getReorderLevel() * 2, 100)) : prod.getCeilingLevel();
                    if (ceiling > 9999) ceiling = 9999;
                    int maxReceivable = ceiling - prod.getQuantity();
                    if (item.getQuantity() > maxReceivable) {
                        Toast.makeText(this, "Item " + item.getProductName() + " quantity exceeds allowed receive limit: " + maxReceivable, Toast.LENGTH_LONG).show();
                        return;
                    }
                }
            }
        }
        String id = poRef.push().getKey();
        if (id == null) {
            Toast.makeText(this, "Error generating ID", Toast.LENGTH_SHORT).show();
            return;
        }
        String poNumber = "PO-" + System.currentTimeMillis() / 1000;
        double total = 0;
        for (POItem item : poItems) total += item.getSubtotal();
        PurchaseOrder po = new PurchaseOrder(
                id,
                poNumber,
                supplier,
                supplierPhone,
                "Pending",
                System.currentTimeMillis(),
                total,
                new ArrayList<>(poItems)
        );
        poRef.child(id).setValue(po)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Purchase Order created", Toast.LENGTH_SHORT).show();
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