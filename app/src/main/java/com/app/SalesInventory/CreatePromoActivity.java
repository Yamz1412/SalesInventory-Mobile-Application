package com.app.SalesInventory;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class CreatePromoActivity extends BaseActivity {

    // Top Action Bar
    private SwitchMaterial switchPromoStatus;
    private ImageButton btnClearForm;
    private MaterialButton btnGoToManagePromos;

    // Promo Details
    private TextInputEditText etPromoName, etPromoValue;
    private AutoCompleteTextView actvPromoType;
    private SwitchMaterial switchTemporaryPromo;

    // Validity Period
    private LinearLayout layoutValidityPeriod;
    private TextInputEditText etStartDate, etEndDate;
    private Calendar calendarStart, calendarEnd;
    private SimpleDateFormat dateFormat;

    // Products
    private MaterialButton btnCreatePromoProduct;
    private EditText etSearchProducts;
    private RecyclerView rvPromoProducts;
    private PromoProductAdapter productAdapter;

    // Actions
    private Button btnCancelPromo, btnSavePromo;

    // Data
    private ProductRepository productRepository;
    private List<Product> allMenuProducts = new ArrayList<>();
    private List<Product> filteredProducts = new ArrayList<>();
    private HashSet<String> selectedProductIds = new HashSet<>(); // Stores ticked products
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_promo);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Initialize User/Database
        currentUserId = AuthManager.getInstance().getCurrentUserId();
        if (currentUserId == null) currentUserId = FirestoreManager.getInstance().getBusinessOwnerId();
        productRepository = SalesInventoryApplication.getProductRepository();

        dateFormat = new SimpleDateFormat("MM/dd/yyyy", Locale.US);
        calendarStart = Calendar.getInstance();
        calendarEnd = Calendar.getInstance();

        initViews();
        setupDropdowns();
        setupListeners();
        setupRecyclerView();

        loadMenuProducts();
    }

    private void initViews() {
        // Top Bar
        switchPromoStatus = findViewById(R.id.switchPromoStatus);
        btnClearForm = findViewById(R.id.btnClearForm);
        btnGoToManagePromos = findViewById(R.id.btnGoToManagePromos);

        // Details
        etPromoName = findViewById(R.id.etPromoName);
        actvPromoType = findViewById(R.id.actvPromoType);
        etPromoValue = findViewById(R.id.etPromoValue);
        switchTemporaryPromo = findViewById(R.id.switchTemporaryPromo);

        // Validity
        layoutValidityPeriod = findViewById(R.id.layoutValidityPeriod);
        etStartDate = findViewById(R.id.etStartDate);
        etEndDate = findViewById(R.id.etEndDate);

        // Products
        btnCreatePromoProduct = findViewById(R.id.btnCreatePromoProduct);
        etSearchProducts = findViewById(R.id.etSearchProducts);
        rvPromoProducts = findViewById(R.id.rvPromoProducts);

        // Buttons
        btnCancelPromo = findViewById(R.id.btnCancelPromo);
        btnSavePromo = findViewById(R.id.btnSavePromo);
    }

    private void setupDropdowns() {
        String[] promoTypes = {"Percentage (%)", "Fixed Amount (₱)", "Override Price (₱)"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, promoTypes);
        actvPromoType.setAdapter(adapter);
    }

    private void setupListeners() {
        switchPromoStatus.setOnCheckedChangeListener((buttonView, isChecked) -> {
            switchPromoStatus.setText(isChecked ? "Status: Active" : "Status: Draft");
        });

        btnClearForm.setOnClickListener(v -> clearForm());

        btnGoToManagePromos.setOnClickListener(v -> {
            startActivity(new Intent(this, ManagePromosActivity.class));
        });

        switchTemporaryPromo.setOnCheckedChangeListener((buttonView, isChecked) -> {
            layoutValidityPeriod.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (!isChecked) {
                etStartDate.setText("");
                etEndDate.setText("");
            }
        });

        etStartDate.setOnClickListener(v -> showDatePicker(etStartDate, calendarStart));
        etEndDate.setOnClickListener(v -> showDatePicker(etEndDate, calendarEnd));

        // Inside setupListeners() in CreatePromoActivity.java
        btnCreatePromoProduct.setOnClickListener(v -> {
            String promoName = etPromoName.getText().toString().trim();
            boolean isTemporary = switchTemporaryPromo.isChecked();
            String start = etStartDate.getText().toString().trim();
            String end = etEndDate.getText().toString().trim();

            Intent intent = new Intent(this, AddProductActivity.class);
            // Force the "Menu/Sales" mode
            intent.putExtra("MODE_MENU_ONLY", true);

            // Pass Promo context so the new product is automatically tagged
            intent.putExtra("FROM_PROMO_BUILDER", true);
            intent.putExtra("PROMO_NAME", promoName.isEmpty() ? "Special Promo" : promoName);
            intent.putExtra("IS_TEMPORARY", isTemporary);
            intent.putExtra("PROMO_START", start);
            intent.putExtra("PROMO_END", end);

            startActivity(intent);
        });

        etSearchProducts.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterProducts(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnCancelPromo.setOnClickListener(v -> finish());
        btnSavePromo.setOnClickListener(v -> attemptSavePromo());
    }

    private void setupRecyclerView() {
        productAdapter = new PromoProductAdapter();
        rvPromoProducts.setLayoutManager(new LinearLayoutManager(this));
        rvPromoProducts.setAdapter(productAdapter);
    }

    private void loadMenuProducts() {
        productRepository.getAllProducts().observe(this, products -> {
            allMenuProducts.clear();
            if (products != null) {
                for (Product p : products) {
                    if (p.isActive() && ("Menu".equalsIgnoreCase(p.getProductType()) || p.isSellable())) {
                        allMenuProducts.add(p);
                    }
                }
            }
            filterProducts(etSearchProducts.getText().toString());
        });
    }

    private void filterProducts(String query) {
        filteredProducts.clear();
        if (query.isEmpty()) {
            filteredProducts.addAll(allMenuProducts);
        } else {
            String lowerQuery = query.toLowerCase(Locale.getDefault());
            for (Product p : allMenuProducts) {
                if (p.getProductName().toLowerCase(Locale.getDefault()).contains(lowerQuery) ||
                        (p.getCategoryName() != null && p.getCategoryName().toLowerCase(Locale.getDefault()).contains(lowerQuery))) {
                    filteredProducts.add(p);
                }
            }
        }
        productAdapter.notifyDataSetChanged();
    }

    private void showDatePicker(TextInputEditText targetField, Calendar calendar) {
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            calendar.set(year, month, dayOfMonth);
            targetField.setText(dateFormat.format(calendar.getTime()));
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void clearForm() {
        etPromoName.setText("");
        actvPromoType.setText("", false);
        etPromoValue.setText("");
        switchTemporaryPromo.setChecked(false);
        etStartDate.setText("");
        etEndDate.setText("");
        etSearchProducts.setText("");
        selectedProductIds.clear();
        productAdapter.notifyDataSetChanged();
    }

    // --- ADDED MISSING HELPER METHOD ---
    private Product getProductByIdLocally(String productId) {
        for (Product p : allMenuProducts) {
            if (p.getProductId() != null && p.getProductId().equals(productId)) {
                return p;
            }
        }
        return null;
    }

    private void attemptSavePromo() {
        String name = etPromoName.getText() != null ? etPromoName.getText().toString().trim() : "";
        String type = actvPromoType.getText().toString().trim();
        String valueStr = etPromoValue.getText() != null ? etPromoValue.getText().toString().trim() : "";
        boolean isTemporary = switchTemporaryPromo.isChecked();
        String startDate = etStartDate.getText() != null ? etStartDate.getText().toString().trim() : "";
        String endDate = etEndDate.getText() != null ? etEndDate.getText().toString().trim() : "";
        boolean isActive = switchPromoStatus.isChecked();

        // Validations
        if (name.isEmpty()) {
            etPromoName.setError("Promo Name is required");
            return;
        }
        if (type.isEmpty()) {
            Toast.makeText(this, "Please select a discount type", Toast.LENGTH_SHORT).show();
            return;
        }
        if (valueStr.isEmpty()) {
            etPromoValue.setError("Value is required");
            return;
        }
        if (isTemporary && (startDate.isEmpty() || endDate.isEmpty())) {
            Toast.makeText(this, "Start and End dates required for temporary promos", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selectedProductIds.isEmpty()) {
            Toast.makeText(this, "Please select at least one applicable product", Toast.LENGTH_SHORT).show();
            return;
        }

        double value = Double.parseDouble(valueStr);
        DatabaseReference promoRef = FirebaseDatabase.getInstance().getReference("Promos").child(currentUserId).push();
        String promoId = promoRef.getKey();

        Map<String, Object> promoData = new HashMap<>();
        promoData.put("promoId", promoId);
        promoData.put("promoName", name);
        promoData.put("discountType", type);
        promoData.put("discountValue", value);
        promoData.put("isTemporary", isTemporary);
        promoData.put("startDate", startDate);
        promoData.put("endDate", endDate);
        promoData.put("isActive", isActive);
        promoData.put("applicableProductIds", new ArrayList<>(selectedProductIds));
        promoData.put("createdAt", System.currentTimeMillis());

        // FIXED: Single Database Call that updates BOTH Promos (Realtime) and Products (Firestore)
        promoRef.setValue(promoData).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {

                // UPDATE THE ACTUAL PRODUCTS IN FIRESTORE
                WriteBatch batch = FirebaseFirestore.getInstance().batch();

                for (String productId : selectedProductIds) {
                    Product originalProduct = getProductByIdLocally(productId);
                    if (originalProduct == null) continue;

                    double originalPrice = originalProduct.getSellingPrice();
                    double newPromoPrice = originalPrice;

                    // Calculate new price
                    if (type.equals("Percentage (%)")) newPromoPrice = originalPrice - (originalPrice * (value / 100.0));
                    else if (type.equals("Fixed Amount (₱)")) newPromoPrice = Math.max(0, originalPrice - value);
                    else if (type.equals("Override Price (₱)")) newPromoPrice = value;

                    DocumentReference productRef = FirebaseFirestore.getInstance()
                            .collection("users").document(currentUserId)
                            .collection("products").document(productId);

                    Map<String, Object> productUpdates = new HashMap<>();
                    productUpdates.put("isPromo", isActive);
                    productUpdates.put("promoName", name);
                    productUpdates.put("isTemporaryPromo", isTemporary);
                    productUpdates.put("promoPrice", newPromoPrice);

                    if (isTemporary) {
                        try {
                            productUpdates.put("promoStartDate", dateFormat.parse(startDate).getTime());
                            productUpdates.put("promoEndDate", dateFormat.parse(endDate).getTime());
                        } catch (Exception e) { e.printStackTrace(); }
                    }

                    batch.update(productRef, productUpdates);
                }

                // Execute Firestore Batch
                batch.commit().addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Promo created successfully!", Toast.LENGTH_SHORT).show();
                    finish();
                }).addOnFailureListener(e -> {
                    Toast.makeText(this, "Promo created, but failed to link products: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    finish();
                });

            } else {
                Toast.makeText(this, "Failed to create promo: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // ==========================================
    // RECYCLERVIEW ADAPTER FOR PRODUCT SELECTION
    // ==========================================
    private class PromoProductAdapter extends RecyclerView.Adapter<PromoProductAdapter.ProductViewHolder> {

        @NonNull
        @Override
        public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_promo_product_select, parent, false);
            return new ProductViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
            Product product = filteredProducts.get(position);
            String productId = product.getProductId();

            holder.tvProductName.setText(product.getProductName());
            String cat = product.getCategoryName() != null ? product.getCategoryName() : "Uncategorized";
            holder.tvProductCategory.setText(cat + " | ₱" + String.format(Locale.US, "%.2f", product.getSellingPrice()));

            holder.cbSelectProduct.setOnCheckedChangeListener(null);
            holder.cbSelectProduct.setChecked(selectedProductIds.contains(productId));

            holder.cbSelectProduct.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selectedProductIds.add(productId);
                else selectedProductIds.remove(productId);
            });

            holder.itemView.setOnClickListener(v -> {
                holder.cbSelectProduct.setChecked(!holder.cbSelectProduct.isChecked());
            });
        }

        @Override
        public int getItemCount() {
            return filteredProducts.size();
        }

        class ProductViewHolder extends RecyclerView.ViewHolder {
            CheckBox cbSelectProduct;
            TextView tvProductName, tvProductCategory;

            ProductViewHolder(@NonNull View itemView) {
                super(itemView);
                cbSelectProduct = itemView.findViewById(R.id.cbSelectProduct);
                tvProductName = itemView.findViewById(R.id.tvProductName);
                tvProductCategory = itemView.findViewById(R.id.tvProductCategory);
            }
        }
    }
}