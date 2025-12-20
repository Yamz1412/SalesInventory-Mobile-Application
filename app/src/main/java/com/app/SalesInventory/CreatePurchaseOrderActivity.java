package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
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
import java.util.regex.Pattern;

public class CreatePurchaseOrderActivity extends BaseActivity  {
    private Toolbar toolbar;
    private TextInputEditText etSupplierName, etSupplierPhone, etOrderDate, etExpectedDate, etNotes;
    private RecyclerView recyclerViewItems;
    private TextView tvTotalAmount;
    private Button btnAddItem, btnCreatePO, btnCancelPO;
    private POItemAdapter adapter;
    private List<POItem> poItems;
    private DatabaseReference poRef;
    private Calendar calendar;
    private ProductRepository productRepository;
    private List<Product> availableProducts = new ArrayList<>();
    private List<String> productNames = new ArrayList<>();

    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+63\\d{10}$");
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z. ]+$");
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("^\\d{1,5}$");
    private static final Pattern PRICE_PATTERN = Pattern.compile("^\\d{1,7}(\\.\\d{0,2})?$");

    private TextWatcher phoneWatcher;
    private InputFilter nameFilter;

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
        btnCancelPO = findViewById(R.id.btnCancelPO);
        calendar = Calendar.getInstance();
        updateDateLabel(etOrderDate);

        nameFilter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence src, int start, int end, Spanned dest, int dstart, int dend) {
                for (int i = start; i < end; i++) {
                    char c = src.charAt(i);
                    if (!(Character.isLetter(c) || c == '.' || Character.isSpaceChar(c))) {
                        return "";
                    }
                }
                return null;
            }
        };
        etSupplierName.setFilters(new InputFilter[] { nameFilter, new InputFilter.LengthFilter(80) });

        etSupplierPhone.setFilters(new InputFilter[] { new InputFilter.LengthFilter(13) });
        if (etSupplierPhone.getText() == null || etSupplierPhone.getText().toString().trim().isEmpty()) {
            etSupplierPhone.setText("+63");
            etSupplierPhone.setSelection(etSupplierPhone.getText().length());
        }

        phoneWatcher = new TextWatcher() {
            boolean selfChange = false;
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override
            public void afterTextChanged(Editable s) {
                if (selfChange) return;
                selfChange = true;
                String val = s.toString();
                String cleaned = val.replaceAll("[^+0-9]", "");
                if (!cleaned.startsWith("+")) {
                    if (cleaned.startsWith("0")) {
                        cleaned = cleaned.replaceFirst("^0+", "");
                        cleaned = "+63" + cleaned;
                    } else if (cleaned.startsWith("63")) {
                        cleaned = "+" + cleaned;
                    } else {
                        cleaned = "+63" + cleaned.replaceFirst("^\\+", "");
                    }
                }
                if (!cleaned.startsWith("+63")) {
                    String digitsOnly = cleaned.replaceAll("[^0-9]", "");
                    cleaned = "+63" + digitsOnly;
                }
                String afterPrefix = cleaned.substring(3).replaceAll("[^0-9]", "");
                if (afterPrefix.length() > 10) afterPrefix = afterPrefix.substring(0, 10);
                String result = "+63" + afterPrefix;
                etSupplierPhone.setText(result);
                etSupplierPhone.setSelection(result.length());
                selfChange = false;
            }
        };
        etSupplierPhone.addTextChangedListener(phoneWatcher);
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
        btnCreatePO.setOnClickListener(v -> showConfirmCreateDialog());
        btnCancelPO.setOnClickListener(v -> showConfirmCancelDialog());
    }

    private void showConfirmCreateDialog() {
        String supplier = etSupplierName.getText() == null ? "" : etSupplierName.getText().toString().trim();
        String phone = etSupplierPhone.getText() == null ? "" : etSupplierPhone.getText().toString().trim();

        if (supplier.isEmpty()) {
            etSupplierName.setError("Supplier name required");
            return;
        }
        if (!NAME_PATTERN.matcher(supplier).matches()) {
            etSupplierName.setError("Supplier name can only contain letters, spaces and dot");
            return;
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            etSupplierPhone.setError("Phone must be in format +63########## (10 digits after +63)");
            return;
        }
        if (poItems.isEmpty()) {
            Toast.makeText(this, "Please add at least one item", Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Create Purchase Order")
                .setMessage("Are you sure you want to create this purchase order?")
                .setPositiveButton("Create", (dialog, which) -> savePurchaseOrder())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showConfirmCancelDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Cancel Purchase Order")
                .setMessage("Discard this purchase order?")
                .setPositiveButton("Discard", (dialog, which) -> finish())
                .setNegativeButton("Keep Editing", null)
                .show();
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

        InputFilter digitsOnlyFilter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence src, int start, int end, Spanned dest, int dstart, int dend) {
                for (int i = start; i < end; i++) {
                    char c = src.charAt(i);
                    if (!Character.isDigit(c)) return "";
                }
                return null;
            }
        };

        etQuantity.setFilters(new InputFilter[] { digitsOnlyFilter, new InputFilter.LengthFilter(5) });

        InputFilter priceFilter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                String newText = dest.subSequence(0, dstart) + source.toString() + dest.subSequence(dend, dest.length());
                if (newText.isEmpty()) return null;
                if (!newText.matches("^\\d{0,7}(\\.\\d{0,2})?$")) return "";
                return null;
            }
        };
        etUnitPrice.setFilters(new InputFilter[] { priceFilter, new InputFilter.LengthFilter(10) });

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
            if (!QUANTITY_PATTERN.matcher(qtyStr).matches()) {
                Toast.makeText(this, "Quantity must be up to 5 digits", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!PRICE_PATTERN.matcher(priceStr).matches()) {
                Toast.makeText(this, "Unit price must be up to 7 digits (optionally 2 decimals)", Toast.LENGTH_SHORT).show();
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
        if (!NAME_PATTERN.matcher(supplier).matches()) {
            etSupplierName.setError("Supplier name can only contain letters, spaces and dot");
            return;
        }
        if (!PHONE_PATTERN.matcher(supplierPhone).matches()) {
            etSupplierPhone.setError("Phone must be in format +63##########");
            return;
        }
        if (poItems.isEmpty()) {
            Toast.makeText(this, "Please add at least one item", Toast.LENGTH_SHORT).show();
            return;
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
            showConfirmCancelDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}