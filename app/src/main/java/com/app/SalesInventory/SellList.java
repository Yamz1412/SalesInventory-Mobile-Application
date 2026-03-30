package com.app.SalesInventory;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SellList extends BaseActivity {
    private RecyclerView sellListView;
    private SellAdapter sellAdapter;
    private List<Product> allMenuProducts = new ArrayList<>();
    private List<Product> masterInventory = new ArrayList<>();
    private List<Product> filteredProducts = new ArrayList<>();
    private ProductRepository productRepository;
    private CartManager cartManager;

    private Button btnCheckout;
    private ImageButton btnRefundList;
    private EditText etSearchProduct;
    private LinearLayout layoutCategoryChips;
    private FloatingActionButton fabAddSalesProduct;
    private String currentCategoryFilter = "All";
    private List<Product> cachedInventoryList = new ArrayList<>();

    // Bulk action bar
    private LinearLayout layoutBulkActions;
    private TextView tvSelectedCount;
    private ImageButton btnBulkEdit, btnBulkDelete, btnBulkOptions, btnBulkClose;
    private Set<String> currentSelectedIds = new HashSet<>();
    private com.google.android.material.button.MaterialButton btnShiftAction;
    private boolean isShiftActive = false;
    private com.google.firebase.firestore.ListenerRegistration shiftListener;
    private String currentMainCategory = "All";
    private String currentSubCategory = "All";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sell_list);

        productRepository = SalesInventoryApplication.getProductRepository();
        cartManager = CartManager.getInstance();

        btnRefundList = findViewById(R.id.btnRefundList);
        if (btnRefundList != null) btnRefundList.setOnClickListener(v -> showRefundDialog());

        etSearchProduct = findViewById(R.id.etSearchProduct);
        layoutCategoryChips = findViewById(R.id.layoutCategoryChips);

        sellListView = findViewById(R.id.SellListD);
        sellListView.setLayoutManager(new GridLayoutManager(this, getResponsiveSpanCount()));

        btnShiftAction = findViewById(R.id.btnShiftAction);
        observeShiftStatus();

        btnShiftAction.setOnClickListener(v -> {
            if (!isShiftActive) {
                showStartShiftDialog();
            } else {
                showEndShiftDialog();
            }
        });

        sellAdapter = new SellAdapter(this, filteredProducts, masterInventory, new SellAdapter.OnProductClickListener() {
            @Override
            public void onProductClick(Product product, int maxServings) {
                showProductOptionsDialog(product, maxServings);
            }
        }, selectedIds -> {
            currentSelectedIds = selectedIds;
            updateBulkActionBar(selectedIds);
        });

        sellListView.setAdapter(sellAdapter);

        fabAddSalesProduct = findViewById(R.id.fabAddSalesProduct);
        if (fabAddSalesProduct != null) {
            fabAddSalesProduct.setOnClickListener(v -> {
                Intent intent = new Intent(SellList.this, AddProductActivity.class);
                intent.putExtra("MODE_MENU_ONLY", true);
                startActivity(intent);
            });
        }

        // Inside onCreate in SellList.java
        AuthManager.getInstance().isUserAdmin(isAdmin -> {
            runOnUiThread(() -> {
                if (fabAddSalesProduct != null) {
                    fabAddSalesProduct.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
                }

                View btnAddProduct = findViewById(R.id.btn_add_product);
                if (btnAddProduct != null) {
                    btnAddProduct.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
                }

                if (layoutBulkActions != null && !isAdmin) {
                    layoutBulkActions.setVisibility(View.GONE);
                }

                if (sellAdapter != null) {
                    sellAdapter.setAdminStatus(isAdmin);
                }
            });
        });

        layoutBulkActions = findViewById(R.id.layoutBulkActions);
        tvSelectedCount   = findViewById(R.id.tvSelectedCount);
        btnBulkEdit       = findViewById(R.id.btnBulkEdit);
        btnBulkDelete     = findViewById(R.id.btnBulkDelete);
        btnBulkOptions    = findViewById(R.id.btnBulkOptions);
        btnBulkClose      = findViewById(R.id.btnBulkClose);

        if (btnBulkClose != null) {
            btnBulkClose.setOnClickListener(v -> {
                sellAdapter.clearSelection();
                hideBulkActionBar();
            });
        }

        if (btnBulkEdit != null) {
            btnBulkEdit.setOnClickListener(v -> {
                if (currentSelectedIds.size() == 1) {
                    String selectedId = currentSelectedIds.iterator().next();
                    Product selected = findProductById(selectedId);
                    if (selected != null) {
                        Intent intent = new Intent(SellList.this, EditProduct.class);
                        intent.putExtra("PRODUCT_ID", selected.getProductId());
                        startActivity(intent);
                        sellAdapter.clearSelection();
                        hideBulkActionBar();
                    }
                } else {
                    Toast.makeText(this, "Select only one product to edit", Toast.LENGTH_SHORT).show();
                }
            });
        }

        if (btnBulkDelete != null) {
            btnBulkDelete.setOnClickListener(v -> {
                if (currentSelectedIds.isEmpty()) return;
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setTitle("Delete " + currentSelectedIds.size() + " product(s)?")
                        .setMessage("This action cannot be undone.")
                        .setPositiveButton("Delete", (d, w) -> {
                            for (String id : currentSelectedIds) {
                                Product p = findProductById(id);
                                if (p != null) productRepository.deleteProduct(p.getProductId(), null);
                            }
                            sellAdapter.clearSelection();
                            hideBulkActionBar();
                            Toast.makeText(this, "Deleted", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        if (btnBulkOptions != null) {
            btnBulkOptions.setOnClickListener(v -> {
                if (currentSelectedIds.size() != 1) {
                    Toast.makeText(this, "Select one source product to copy from", Toast.LENGTH_SHORT).show();
                    return;
                }
                String sourceId = currentSelectedIds.iterator().next();
                Product source = findProductById(sourceId);
                if (source == null) return;
                showCopyOptionsDialog(source);
            });
        }

        btnCheckout = findViewById(R.id.btnCheckout);
        if (btnCheckout != null) {
            btnCheckout.setOnClickListener(v -> {
                if (cartManager.getItems().isEmpty()) {
                    Toast.makeText(SellList.this, "Cart is empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(SellList.this, sellProduct.class));
            });
        }

        if (etSearchProduct != null) {
            etSearchProduct.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) { applyCategoryFilter(); }
            });
        }

        adjustButtonsForMobile();
        loadProducts();
    }

    private void observeShiftStatus() {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        // Real-time listener ensures Admin and Staff are synced
        shiftListener = FirebaseFirestore.getInstance()
                .collection("users").document(ownerId)
                .collection("system").document("current_shift")
                .addSnapshotListener((snapshot, e) -> {
                    if (snapshot != null && snapshot.exists()) {
                        isShiftActive = Boolean.TRUE.equals(snapshot.getBoolean("active"));
                        updateShiftUI(isShiftActive);
                    } else {
                        isShiftActive = false;
                        updateShiftUI(false);
                    }
                });
    }

    private void updateShiftUI(boolean active) {
        runOnUiThread(() -> {
            if (active) {
                btnShiftAction.setText("End Shift");
                btnShiftAction.setIconTintResource(R.color.errorRed);
                btnShiftAction.setTextColor(getResources().getColor(R.color.errorRed));
            } else {
                btnShiftAction.setText("Start Shift");
                btnShiftAction.setIconTintResource(R.color.colorPrimary);
                btnShiftAction.setTextColor(getResources().getColor(R.color.textColorSecondary));
            }
        });
    }

    private void showStartShiftDialog() {
        android.widget.EditText etStartingCash = new android.widget.EditText(this);
        etStartingCash.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        etStartingCash.setHint("0.00");

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Start Shift")
                .setMessage("Enter starting cash amount in drawer:")
                .setView(etStartingCash)
                .setPositiveButton("Start", (d, w) -> {
                    String amount = etStartingCash.getText().toString();
                    if (!amount.isEmpty()) {
                        saveShiftStatus(true, Double.parseDouble(amount));
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void saveShiftStatus(boolean active, double amount) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        java.util.Map<String, Object> shiftData = new java.util.HashMap<>();
        shiftData.put("active", active);
        shiftData.put("amount", amount);
        shiftData.put("timestamp", System.currentTimeMillis());
        shiftData.put("updatedBy", AuthManager.getInstance().getCurrentUserId());

        FirebaseFirestore.getInstance()
                .collection("users").document(ownerId)
                .collection("system").document("current_shift")
                .set(shiftData, com.google.firebase.firestore.SetOptions.merge());
    }

    private void showEndShiftDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("End Shift")
                .setMessage("Are you sure you want to end the current shift?")
                .setPositiveButton("End Shift", (d, w) -> saveShiftStatus(false, 0.0))
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (shiftListener != null) shiftListener.remove(); // Clean up listener
    }

    private ArrayAdapter<String> getAdaptiveAdapter(String[] items) {
        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
            @androidx.annotation.NonNull
            @Override
            public View getView(int position, View convertView, @androidx.annotation.NonNull ViewGroup parent) {
                View view = super.getView(position, convertView, parent);
                ((TextView) view).setTextColor(textColor);
                return view;
            }

            @Override
            public View getDropDownView(int position, View convertView, @androidx.annotation.NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                ((TextView) view).setTextColor(textColor);
                return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        return adapter;
    }

    private int getResponsiveSpanCount() {
        android.content.res.Configuration config = getResources().getConfiguration();
        int screenWidthDp = config.screenWidthDp;
        boolean isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;

        boolean isTablet = screenWidthDp >= 600;

        if (isTablet) {
            if (screenWidthDp >= 900 && isLandscape) return 4;
            return isLandscape ? 3 : 2;
        } else {
            return isLandscape ? 2 : 1;
        }
    }

    private void adjustButtonsForMobile() {
        android.content.res.Configuration config = getResources().getConfiguration();
        if (config.screenWidthDp < 450) { // If it's a small mobile device
            btnShiftAction.setText(isShiftActive ? "End" : "Start");
            if (btnCheckout != null) btnCheckout.setText("Cart");
        }
    }

    private void loadProducts() {
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                allMenuProducts.clear();
                masterInventory.clear();

                for (Product product : products) {
                    if (product.isActive()) {
                        masterInventory.add(product);
                        if (product.isSellable() || "finished".equalsIgnoreCase(product.getProductType()) || "Menu".equalsIgnoreCase(product.getProductType())) {
                            allMenuProducts.add(product);
                        }
                    }
                }
                extractAndSetupCategories(); // Triggers the new dual-row hierarchy
                applyCategoryFilter();
            }
        });
    }

    private void showRefundDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_refund, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        Spinner spinnerTime = view.findViewById(R.id.spinnerTimeFilter);
        EditText etOrderId = view.findViewById(R.id.etRefundOrderId);
        TextView tvDetails = view.findViewById(R.id.tvRefundOrderDetails);
        Spinner spinnerReason = view.findViewById(R.id.spinnerRefundReason);
        EditText etSpecificReason = view.findViewById(R.id.etRefundSpecificReason);
        Button btnCancel = view.findViewById(R.id.btnCancelRefund);
        Button btnConfirm = view.findViewById(R.id.btnConfirmRefund);

        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        if (etOrderId != null) etOrderId.setTextColor(textColor);
        if (tvDetails != null) tvDetails.setTextColor(textColor);
        if (etSpecificReason != null) etSpecificReason.setTextColor(textColor);

        String[] times = {"Today", "Yesterday", "Last 7 Days", "All Time"};
        ArrayAdapter<String> timeAdapter = getAdaptiveAdapter(times);
        spinnerTime.setAdapter(timeAdapter);

        String[] reasons = {
                "Spoiled / Bad Quality",
                "Wrong Order Prepared",
                "Customer Changed Mind",
                "Spilled / Dropped",
                "Other (Specify below)"
        };
        ArrayAdapter<String> reasonAdapter = getAdaptiveAdapter(reasons);
        spinnerReason.setAdapter(reasonAdapter);

        final List<Sales> foundSalesList = new ArrayList<>();
        final double[] totalRefundAmount = {0.0};

        Runnable fetchAndFilterSales = () -> {
            String searchTxt = etOrderId.getText().toString().trim().toLowerCase();
            String selectedTime = spinnerTime.getSelectedItem().toString();

            long now = System.currentTimeMillis();
            long oneDay = 24L * 60 * 60 * 1000L;
            long timeThreshold = 0;

            if (selectedTime.equals("Today")) timeThreshold = now - oneDay;
            else if (selectedTime.equals("Yesterday")) timeThreshold = now - (2 * oneDay);
            else if (selectedTime.equals("Last 7 Days")) timeThreshold = now - (7 * oneDay);

            long finalTimeThreshold = timeThreshold;

            SalesInventoryApplication.getSalesRepository().getAllSales().observe(this, salesList -> {
                foundSalesList.clear();
                totalRefundAmount[0] = 0;
                StringBuilder details = new StringBuilder();

                for (Sales s : salesList) {
                    if (s.getPaymentMethod() != null && s.getPaymentMethod().contains("REFUNDED")) continue;

                    boolean matchesTime = (selectedTime.equals("All Time")) ||
                            (selectedTime.equals("Yesterday") && s.getTimestamp() >= finalTimeThreshold && s.getTimestamp() < (now - oneDay)) ||
                            (!selectedTime.equals("Yesterday") && s.getTimestamp() >= finalTimeThreshold);

                    boolean matchesSearch = searchTxt.isEmpty() ||
                            s.getOrderId().toLowerCase().contains(searchTxt) ||
                            s.getProductName().toLowerCase().contains(searchTxt);

                    if (matchesTime && matchesSearch) {
                        foundSalesList.add(s);
                        totalRefundAmount[0] += s.getTotalPrice();

                        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd hh:mm a", Locale.getDefault());
                        details.append("[").append(sdf.format(new Date(s.getTimestamp()))).append("] ")
                                .append(s.getQuantity()).append("x ").append(s.getProductName())
                                .append("\n   ID: ").append(s.getOrderId().substring(0,8).toUpperCase())
                                .append(" | ₱").append(s.getTotalPrice()).append("\n\n");
                    }
                }

                if (foundSalesList.isEmpty()) {
                    tvDetails.setText("No transactions found for the selected filters.");
                    btnConfirm.setEnabled(false);
                } else {
                    details.append("========================\nTOTAL ELIGIBLE REFUND: ₱").append(totalRefundAmount[0]);
                    tvDetails.setText(details.toString());
                    btnConfirm.setEnabled(true);
                }
            });
        };

        spinnerTime.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { fetchAndFilterSales.run(); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        etOrderId.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { fetchAndFilterSales.run(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnConfirm.setOnClickListener(v -> {
            String selectedReason = spinnerReason.getSelectedItem().toString();
            String specificNotes = etSpecificReason.getText().toString().trim();
            String finalReason = selectedReason + (specificNotes.isEmpty() ? "" : " - " + specificNotes);

            String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
            if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();

            for (Sales sale : foundSalesList) {
                sale.setPaymentMethod("REFUNDED: " + finalReason);

                FirebaseFirestore.getInstance().collection("users").document(ownerId)
                        .collection("sales").document(sale.getId())
                        .update("paymentMethod", "REFUNDED: " + finalReason);

                Product p = null;
                for(Product invP : cachedInventoryList) {
                    if (invP.getProductName().equalsIgnoreCase(sale.getProductName())) { p = invP; break; }
                }

                if (p != null) {
                    double newQty = p.getQuantity() + sale.getQuantity();
                    productRepository.updateProductQuantity(p.getProductId(), newQty, null);
                }

                Map<String, Object> refundLog = new HashMap<>();
                refundLog.put("orderId", sale.getOrderId());
                refundLog.put("productName", sale.getProductName());
                refundLog.put("amount", sale.getTotalPrice());
                refundLog.put("reason", finalReason);
                refundLog.put("timestamp", System.currentTimeMillis());
                FirebaseFirestore.getInstance().collection("users").document(ownerId).collection("refund_logs").add(refundLog);
            }

            Toast.makeText(this, "Refund Processed: " + finalReason, Toast.LENGTH_LONG).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void extractAndSetupCategories() {
        java.util.Map<String, String> mainCatsMap = new java.util.HashMap<>();
        mainCatsMap.put("all", "All");
        for (Product p : allMenuProducts) {
            String main = p.getProductLine() != null && !p.getProductLine().trim().isEmpty() ? p.getProductLine().trim() : "Uncategorized";
            String norm = normalizeCategoryName(main);
            if (!mainCatsMap.containsKey(norm)) mainCatsMap.put(norm, main);
        }
        setupHierarchicalCategoryChips(new ArrayList<>(mainCatsMap.values()));
    }

    private void setupHierarchicalCategoryChips(List<String> mainCategories) {
        if (layoutCategoryChips == null) return;
        layoutCategoryChips.removeAllViews();
        layoutCategoryChips.setOrientation(LinearLayout.VERTICAL);

        LinearLayout mainRow = new LinearLayout(this);
        mainRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String main : mainCategories) {
            // Compare normalized names so the UI knows which one is selected
            boolean isSelected = normalizeCategoryName(main).equals(normalizeCategoryName(currentMainCategory));
            MaterialButton chip = createChip(main, isSelected, false);
            chip.setOnClickListener(v -> {
                currentMainCategory = main;
                currentSubCategory = "All";
                extractAndSetupCategories();
                applyCategoryFilter();
            });
            mainRow.addView(chip);
        }
        layoutCategoryChips.addView(mainRow);

        if (!normalizeCategoryName(currentMainCategory).equals("all")) {
            java.util.Map<String, String> subCatsMap = new java.util.HashMap<>();
            subCatsMap.put("all", "All");

            for (Product p : allMenuProducts) {
                String pMain = p.getProductLine() != null && !p.getProductLine().trim().isEmpty() ? p.getProductLine().trim() : "Uncategorized";
                if (normalizeCategoryName(pMain).equals(normalizeCategoryName(currentMainCategory))) {
                    String sub = p.getCategoryName() != null && !p.getCategoryName().trim().isEmpty() ? p.getCategoryName().trim() : "Uncategorized";
                    String norm = normalizeCategoryName(sub);
                    if (!subCatsMap.containsKey(norm)) subCatsMap.put(norm, sub);
                }
            }

            if (subCatsMap.size() > 1) {
                LinearLayout subRow = new LinearLayout(this);
                subRow.setOrientation(LinearLayout.HORIZONTAL);
                subRow.setPadding(32, 8, 0, 0);

                for (String sub : subCatsMap.values()) {
                    boolean isSelected = normalizeCategoryName(sub).equals(normalizeCategoryName(currentSubCategory));
                    MaterialButton chip = createChip(sub, isSelected, true);
                    chip.setOnClickListener(v -> {
                        currentSubCategory = sub;
                        extractAndSetupCategories();
                        applyCategoryFilter();
                    });
                    subRow.addView(chip);
                }
                layoutCategoryChips.addView(subRow);
            }
        }
    }

    private MaterialButton createChip(String text, boolean isSelected, boolean isSubCategory) {
        MaterialButton chip = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        chip.setText(text);
        chip.setCheckable(true);
        chip.setCornerRadius(50);

        if (isSubCategory) {
            chip.setTextSize(12f); // Make subcategories slightly smaller
            chip.setMinimumHeight(0);
            chip.setPadding(24, 0, 24, 0);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 0, 16, 0);
        chip.setLayoutParams(params);

        if (isSelected) {
            chip.setChecked(true);
            chip.setBackgroundColor(getResources().getColor(R.color.colorPrimary));
            chip.setTextColor(getResources().getColor(android.R.color.white));
        } else {
            chip.setChecked(false);
            chip.setTextColor(getResources().getColor(R.color.colorPrimary));
        }
        return chip;
    }

    private void applyCategoryFilter() {
        filteredProducts.clear();
        String query = etSearchProduct != null ? etSearchProduct.getText().toString().toLowerCase().trim() : "";

        for (Product p : allMenuProducts) {
            // 1. Extract the raw strings from the product object
            String pMain = p.getProductLine() != null ? p.getProductLine().trim() : "Uncategorized";
            String pSub = p.getCategoryName() != null ? p.getCategoryName().trim() : "Uncategorized";
            String pName = p.getProductName() != null ? p.getProductName().toLowerCase() : "";

            // 2. Restore the Search Function logic
            boolean matchesSearch = query.isEmpty() || pName.contains(query);

            // 3. Normalize for case/space-insensitive matching
            String pMainNorm = normalizeCategoryName(pMain);
            String pSubNorm = normalizeCategoryName(pSub);
            String currentMainNorm = normalizeCategoryName(currentMainCategory);
            String currentSubNorm = normalizeCategoryName(currentSubCategory);

            boolean matchesMain = currentMainNorm.equals("all") || currentMainNorm.equals(pMainNorm);
            boolean matchesSub = currentSubNorm.equals("all") || currentSubNorm.equals(pSubNorm);

            if (matchesMain && matchesSub && matchesSearch) {
                filteredProducts.add(p);
            }
        }
        sellAdapter.filterList(filteredProducts, masterInventory);
    }

    private String normalizeCategoryName(String name) {
        if (name == null || name.trim().isEmpty()) return "uncategorized";
        return name.toLowerCase().replaceAll("\\s+", "");
    }

    private Product findInventoryProduct(String name) {
        for (Product p : masterInventory) {
            if (p.getProductName() != null && p.getProductName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private void showProductOptionsDialog(Product product, int maxBaseServings) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_product_options, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView tvName = view.findViewById(R.id.tvOptionProductName);
        TextView tvPrice = view.findViewById(R.id.tvOptionProductPrice);
        EditText etQty = view.findViewById(R.id.etOptionQty);
        Button btnCancel = view.findViewById(R.id.btnOptionCancel);
        Button btnAddToCart = view.findViewById(R.id.btnOptionAddToCart);

        LinearLayout layoutSizesContainer = view.findViewById(R.id.layoutSizesContainer);
        RadioGroup rgSizes = view.findViewById(R.id.rgSizes);

        LinearLayout layoutAddonsContainer = view.findViewById(R.id.layoutAddonsContainer);
        LinearLayout containerAddonsList = view.findViewById(R.id.containerAddonsList);

        LinearLayout layoutNotesContainer = view.findViewById(R.id.layoutNotesContainer);
        LinearLayout containerNotesList = view.findViewById(R.id.containerNotesList);

        tvName.setText(product.getProductName());
        tvPrice.setText(String.format(Locale.US, "₱%.2f", product.getSellingPrice()));

        final double[] basePrice = {product.getSellingPrice()};
        final double[] selectedSizePrice = {0.0};
        final double[] selectedAddonsPrice = {0.0};
        final String[] selectedSizeName = {""};

        List<Map<String, Object>> sizes = product.getSizesList();
        if (sizes != null && !sizes.isEmpty()) {
            layoutSizesContainer.setVisibility(View.VISIBLE);
            boolean isFirstAvailableSizeSelected = false;
            for (int i = 0; i < sizes.size(); i++) {
                Map<String, Object> size = sizes.get(i);
                RadioButton rb = new RadioButton(this);
                String sName = (String) size.get("name");
                String linkedMat = (String) size.get("linkedMaterial");

                double sPrice = 0.0;
                double deductQty = 0.0;
                try { sPrice = Double.parseDouble(String.valueOf(size.get("price"))); } catch (Exception ignored){}
                try { deductQty = Double.parseDouble(String.valueOf(size.get("deductQty"))); } catch (Exception ignored){}

                String text = sName;
                if (sPrice > 0) text += String.format(" (+₱%.2f)", sPrice);

                if (linkedMat != null && !linkedMat.isEmpty() && deductQty > 0) {
                    Product sizeMat = findInventoryProduct(linkedMat);
                    double currentStock = sizeMat != null ? sizeMat.getQuantity() : 0;
                    if (currentStock < deductQty) {
                        rb.setEnabled(false);
                        text += " (Out of Stock)";
                    }
                }

                rb.setText(text);
                rb.setId(View.generateViewId());

                final double finalSPrice = sPrice;
                final String finalSName = sName;

                rb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedSizePrice[0] = finalSPrice;
                        selectedSizeName[0] = finalSName;
                        updateDialogTotalPrice(tvPrice, basePrice[0], selectedSizePrice[0], selectedAddonsPrice[0]);
                    }
                });
                rgSizes.addView(rb);

                if (rb.isEnabled() && !isFirstAvailableSizeSelected) {
                    rb.setChecked(true);
                    isFirstAvailableSizeSelected = true;
                }
            }
        }

        List<CheckBox> addonBoxes = new ArrayList<>();
        List<Map<String, Object>> addons = product.getAddonsList();
        if (addons != null && !addons.isEmpty()) {
            layoutAddonsContainer.setVisibility(View.VISIBLE);
            for (Map<String, Object> addon : addons) {
                CheckBox cb = new CheckBox(this);
                String aName = (String) addon.get("name");
                String linkedMat = (String) addon.get("linkedMaterial");

                double aPrice = 0.0;
                double deductQty = 0.0;
                try { aPrice = Double.parseDouble(String.valueOf(addon.get("price"))); } catch (Exception ignored){}
                try { deductQty = Double.parseDouble(String.valueOf(addon.get("deductQty"))); } catch (Exception ignored){}

                String text = aName;
                if (aPrice > 0) text += String.format(" (+₱%.2f)", aPrice);

                if (linkedMat != null && !linkedMat.isEmpty() && deductQty > 0) {
                    Product addonMat = findInventoryProduct(linkedMat);
                    double currentStock = addonMat != null ? addonMat.getQuantity() : 0;
                    if (currentStock < deductQty) {
                        cb.setEnabled(false);
                        text += " (Out of Stock)";
                    }
                }

                cb.setText(text);

                final double finalAPrice = aPrice;
                cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) selectedAddonsPrice[0] += finalAPrice;
                    else selectedAddonsPrice[0] -= finalAPrice;
                    updateDialogTotalPrice(tvPrice, basePrice[0], selectedSizePrice[0], selectedAddonsPrice[0]);
                });
                addonBoxes.add(cb);
                containerAddonsList.addView(cb);
            }
        }

        List<Map<String, String>> notes = product.getNotesList();
        final String[] selectedNoteText = {""};
        if (notes != null && !notes.isEmpty()) {
            layoutNotesContainer.setVisibility(View.VISIBLE);

            Map<String, RadioGroup> noteGroups = new HashMap<>();
            for (Map<String, String> note : notes) {
                String type = note.get("type");
                String value = note.get("value");

                if (!noteGroups.containsKey(type)) {
                    TextView tvTitle = new TextView(this);
                    tvTitle.setText(type);
                    tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);

                    tvTitle.setPadding(0, 16, 0, 8);
                    containerNotesList.addView(tvTitle);

                    RadioGroup rg = new RadioGroup(this);
                    rg.setOrientation(LinearLayout.VERTICAL);
                    containerNotesList.addView(rg);
                    noteGroups.put(type, rg);
                }

                RadioButton rb = new RadioButton(this);
                rb.setText(value);
                rb.setId(View.generateViewId());
                noteGroups.get(type).addView(rb);
            }

            for (RadioGroup rg : noteGroups.values()) {
                rg.setOnCheckedChangeListener((group, checkedId) -> {
                    StringBuilder sb = new StringBuilder();
                    for (RadioGroup g : noteGroups.values()) {
                        int id = g.getCheckedRadioButtonId();
                        if (id != -1) {
                            RadioButton selectedRb = g.findViewById(id);
                            sb.append(selectedRb.getText().toString()).append(", ");
                        }
                    }
                    selectedNoteText[0] = sb.toString();
                });
            }
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnAddToCart.setOnClickListener(v -> {
            String qtyStr = etQty.getText().toString().trim();
            int qty = qtyStr.isEmpty() ? 1 : Integer.parseInt(qtyStr);
            if (qty <= 0) return;

            if (qty > maxBaseServings) {
                Toast.makeText(this, "Only " + maxBaseServings + " servings available based on raw ingredients!", Toast.LENGTH_LONG).show();
                return;
            }

            double finalPrice = basePrice[0] + selectedSizePrice[0] + selectedAddonsPrice[0];

            StringBuilder extrasBuilder = new StringBuilder();
            for (CheckBox cb : addonBoxes) {
                if (cb.isChecked()) extrasBuilder.append(cb.getText().toString().replaceAll(" \\(\\+₱.*\\)", "")).append(", ");
            }
            if (!selectedNoteText[0].isEmpty()) extrasBuilder.append(selectedNoteText[0]);

            String extraDetails = extrasBuilder.toString().trim();
            if (extraDetails.endsWith(",")) extraDetails = extraDetails.substring(0, extraDetails.length() - 1).trim();

            String safeId = product.getProductId() != null ? product.getProductId() : "local:" + product.getLocalId();

            boolean added = cartManager.addItem(safeId, product.getProductName(), finalPrice, qty, maxBaseServings, selectedSizeName[0], extraDetails);

            if (added) {
                Toast.makeText(this, "Added to cart", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Not enough ingredients to fulfill order!", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    private void updateDialogTotalPrice(TextView tvPrice, double base, double size, double addons) {
        double total = base + size + addons;
        tvPrice.setText(String.format(Locale.US, "₱%.2f", total));
    }

    private void updateBulkActionBar(Set<String> selectedIds) {
        if (layoutBulkActions == null) return;
        if (selectedIds == null || selectedIds.isEmpty()) {
            hideBulkActionBar();
        } else {
            layoutBulkActions.setVisibility(View.VISIBLE);
            if (tvSelectedCount != null) {
                tvSelectedCount.setText(selectedIds.size() + " Selected");
            }
            if (btnBulkOptions != null) {
                btnBulkOptions.setAlpha(selectedIds.size() == 1 ? 1.0f : 0.4f);
            }
            if (btnBulkEdit != null) {
                btnBulkEdit.setAlpha(selectedIds.size() == 1 ? 1.0f : 0.4f);
            }
        }
    }

    private void hideBulkActionBar() {
        if (layoutBulkActions != null) layoutBulkActions.setVisibility(View.GONE);
        currentSelectedIds = new HashSet<>();
    }

    private Product findProductById(String id) {
        for (Product p : allMenuProducts) {
            String pId = p.getProductId() != null ? p.getProductId() : "local:" + p.getLocalId();
            if (pId.equals(id)) return p;
        }
        return null;
    }

    private void showCopyOptionsDialog(Product source) {
        String[] options = {"Copy Pricing to All Products", "Copy Recipe (BOM) to All Products", "Copy Add-ons to All Products"};
        boolean[] checked = {false, false, false};

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Copy from \"" + source.getProductName() + "\" to all products")
                .setMultiChoiceItems(options, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Apply", (dialog, w) -> {
                    boolean anyChecked = false;
                    for (boolean b : checked) if (b) { anyChecked = true; break; }
                    if (!anyChecked) {
                        Toast.makeText(this, "Select at least one option to copy", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int count = 0;
                    for (Product target : allMenuProducts) {
                        if (target.getProductId().equals(source.getProductId())) continue;
                        boolean changed = false;
                        if (checked[0]) { target.setSellingPrice(source.getSellingPrice()); target.setCostPrice(source.getCostPrice()); changed = true; }
                        if (checked[1]) { target.setBomList(source.getBomList()); changed = true; }
                        if (checked[2]) { target.setAddonsList(source.getAddonsList()); changed = true; }
                        if (changed) {
                            productRepository.updateProduct(target, null, null);
                            count++;
                        }
                    }
                    sellAdapter.clearSelection();
                    hideBulkActionBar();
                    Toast.makeText(this, "Copied to " + count + " product(s)", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cartManager != null && btnCheckout != null) {
            updateCheckoutButton();
        }
    }

    private void updateCheckoutButton() {
        if (cartManager == null || btnCheckout == null) return;

        int count = cartManager.getItems().size();
        if (count > 0) {
            btnCheckout.setVisibility(View.VISIBLE);
            btnCheckout.setText("Checkout (" + count + ")");
            btnCheckout.setEnabled(true);
        } else {
            btnCheckout.setVisibility(View.GONE);
            btnCheckout.setEnabled(false);
        }
    }
}