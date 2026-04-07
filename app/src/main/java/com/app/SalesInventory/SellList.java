package com.app.SalesInventory;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
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
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    // UI Navigation & Shift Elements
    private MaterialButton btnCart, btnReturn, btnLockScreen, btnUnlock, btnRefund;
    private LinearLayout layoutLockScreen;
    private TextInputEditText etUnlockPassword;
    private TextView tvShiftStatus;

    private EditText etSearchProduct;
    private ImageButton btnFilterSort;
    private LinearLayout layoutCategoryChips; // Mapped to categoryContainer in XML
    private FloatingActionButton fabAddSalesProduct;

    private String currentCategoryFilter = "All";
    private String currentSortOption = "A-Z";
    private List<Product> cachedInventoryList = new ArrayList<>();
    private Map<String, Product> fastInventoryLookup = new HashMap<>();

    // Bulk action bar
    private LinearLayout layoutBulkActions;
    private TextView tvSelectedCount;
    private ImageButton btnBulkEdit, btnBulkDelete, btnBulkOptions, btnBulkClose;
    private Set<String> currentSelectedIds = new HashSet<>();

    // Archive UI Elements
    private View layoutArchiveContainer;
    private Button btnArchive;
    private TextView tvArchiveBadge;
    private com.google.firebase.firestore.ListenerRegistration archiveListener;
    private boolean isReadOnly = false;

    private String currentMainCategory = "All";
    private String currentSubCategory = "All";
    private String lastCategoryHash = "";
    private ActionMode actionMode;
    private Map<String, View> dynamicNoteViews = new HashMap<>();

    // Flexible Handling toggle
    private boolean isFlexibleHandlingEnabled = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sell_list);

        productRepository = SalesInventoryApplication.getProductRepository();
        cartManager = CartManager.getInstance();

        btnRefund = findViewById(R.id.btnRefund);
        if (btnRefund != null) btnRefund.setOnClickListener(v -> showRefundDialog());

        etSearchProduct = findViewById(R.id.etSearchProduct);
        layoutCategoryChips = findViewById(R.id.categoryContainer);

        sellListView = findViewById(R.id.SellListD);
        if (sellListView != null) {
            sellListView.setLayoutManager(new GridLayoutManager(this, getResponsiveSpanCount()));
        }

        // Lock Screen & Top Button Bindings
        tvShiftStatus = findViewById(R.id.tvShiftStatus);
        btnCart = findViewById(R.id.btnCart);
        btnLockScreen = findViewById(R.id.btn_lock_screen);
        layoutLockScreen = findViewById(R.id.layout_lock_screen);
        etUnlockPassword = findViewById(R.id.et_unlock_password);
        btnUnlock = findViewById(R.id.btn_unlock);


        btnFilterSort = findViewById(R.id.btnFilterSort);
        if (btnFilterSort != null) btnFilterSort.setOnClickListener(v -> showSortFilterDialog());

        if (btnCart != null) {
            btnCart.setOnClickListener(v -> {
                if (cartManager.getItems().isEmpty()) {
                    Toast.makeText(SellList.this, "Cart is empty", Toast.LENGTH_SHORT).show();
                    return;
                }
                startActivity(new Intent(SellList.this, sellProduct.class));
            });
        }

        // Automated Shift Logging
        logAttendance("Shift Started / Logged In");
        if (tvShiftStatus != null) {
            tvShiftStatus.setText("🟢 Shift Active (Automated)");
        }

        if (btnLockScreen != null) {
            btnLockScreen.setOnClickListener(v -> {
                logAttendance("Screen Locked");
                if (layoutLockScreen != null) layoutLockScreen.setVisibility(View.VISIBLE);
            });
        }

        if (btnUnlock != null) {
            btnUnlock.setOnClickListener(v -> {
                if (etUnlockPassword != null) {
                    String pin = etUnlockPassword.getText().toString().trim();
                    if (pin.isEmpty()) {
                        etUnlockPassword.setError("PIN Required to Unlock");
                        return;
                    }
                    etUnlockPassword.setText("");
                }
                logAttendance("Screen Unlocked");
                if (layoutLockScreen != null) layoutLockScreen.setVisibility(View.GONE);
                Toast.makeText(this, "Register Unlocked", Toast.LENGTH_SHORT).show();
            });
        }

        sellAdapter = new SellAdapter(this, filteredProducts, masterInventory, new SellAdapter.OnProductClickListener() {
            @Override
            public void onProductClick(Product product, int maxServings) {
                showProductOptionsDialog(product, maxServings);
            }
        }, selectedIds -> {

            if (isReadOnly) {
                if (!selectedIds.isEmpty() && sellAdapter != null) {
                    sellAdapter.clearSelection();
                    Toast.makeText(SellList.this, "Admin access required to modify items.", Toast.LENGTH_SHORT).show();
                }
                return;
            }

            currentSelectedIds = selectedIds;
            updateBulkActionBar(selectedIds);
        }, isFlexibleHandlingEnabled);

        if (sellListView != null) {
            sellListView.setAdapter(sellAdapter);
        }

        fabAddSalesProduct = findViewById(R.id.fabAddSalesProduct);
        if (fabAddSalesProduct != null) {
            fabAddSalesProduct.setVisibility(View.GONE);
            fabAddSalesProduct.setOnClickListener(v -> {
                if (isReadOnly) {
                    Toast.makeText(SellList.this, "Admin access required", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(SellList.this, AddProductActivity.class);
                intent.putExtra("MODE_MENU_ONLY", true);
                startActivity(intent);
            });
        }

        // Admin checks
        AuthManager.getInstance().isUserAdmin(isAdmin -> {
            runOnUiThread(() -> {
                isReadOnly = !isAdmin;
                if (fabAddSalesProduct != null) {
                    fabAddSalesProduct.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
                }

                if (btnLockScreen != null) {
                    btnLockScreen.setVisibility(isAdmin ? View.GONE : View.VISIBLE);
                }

                if (layoutBulkActions != null && !isAdmin) {
                    layoutBulkActions.setVisibility(View.GONE);
                }

                if (sellAdapter != null) {
                    sellAdapter.setAdminStatus(isAdmin);
                }

                if (isAdmin) {
                    listenToArchivedProductsCount();
                } else {
                    if (layoutArchiveContainer != null) layoutArchiveContainer.setVisibility(View.GONE);
                    if (btnArchive != null) btnArchive.setVisibility(View.GONE);
                }
            });
        });

        // Initialize Bulk Actions safely
        layoutBulkActions = findViewById(R.id.layoutBulkActions);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        btnBulkEdit = findViewById(R.id.btnBulkEdit);
        btnBulkDelete = findViewById(R.id.btnBulkDelete);
        btnBulkOptions = findViewById(R.id.btnBulkOptions);
        btnBulkClose = findViewById(R.id.btnBulkClose);

        if (btnBulkClose != null) {
            btnBulkClose.setOnClickListener(v -> {
                if (sellAdapter != null) sellAdapter.clearSelection();
                hideBulkActionBar();
            });
        }

        if (btnBulkEdit != null) {
            btnBulkEdit.setOnClickListener(v -> {
                if (currentSelectedIds.size() == 1) {
                    String selectedId = currentSelectedIds.iterator().next();
                    Product selected = findProductById(selectedId);
                    if (selected != null) {
                        Intent intent = new Intent(SellList.this, EditSalesProductActivity.class);
                        intent.putExtra("PRODUCT_ID", selected.getProductId());
                        startActivity(intent);
                        if (sellAdapter != null) sellAdapter.clearSelection();
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
                                if (p != null)
                                    productRepository.deleteProduct(p.getProductId(), null);
                            }
                            if (sellAdapter != null) sellAdapter.clearSelection();
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

        if (etSearchProduct != null) {
            etSearchProduct.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    applyCategoryFilter();
                }
            });
        }

        adjustButtonsForMobile();
        loadProducts();
    }

    private void logAttendance(String action) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();
        if (ownerId == null) return;

        Map<String, Object> log = new HashMap<>();
        log.put("action", action);
        log.put("timestamp", System.currentTimeMillis());
        log.put("userId", AuthManager.getInstance().getCurrentUserId());

        FirebaseFirestore.getInstance().collection("users").document(ownerId)
                .collection("attendance_logs").add(log);
    }

    private void listenToArchivedProductsCount() {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = AuthManager.getInstance().getCurrentUserId();
        }
        if (ownerId == null) return;

        archiveListener = FirebaseFirestore.getInstance()
                .collection("users").document(ownerId).collection("products")
                .whereEqualTo("isActive", false)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    if (snapshot != null) {
                        int archiveCount = snapshot.size();
                        runOnUiThread(() -> {
                            boolean show = (archiveCount > 0 && !isReadOnly);
                            if (layoutArchiveContainer != null) {
                                layoutArchiveContainer.setVisibility(show ? View.VISIBLE : View.GONE);
                            }
                            if (btnArchive != null) {
                                btnArchive.setVisibility(show ? View.VISIBLE : View.GONE);
                            }
                            if (tvArchiveBadge != null) {
                                tvArchiveBadge.setText(String.valueOf(archiveCount));
                                tvArchiveBadge.setVisibility(archiveCount > 0 ? View.VISIBLE : View.GONE);
                            }
                        });
                    }
                });
    }

    private void showSortFilterDialog() {
        String[] options = {
                "A-Z",
                "Z-A",
                "Price: Low to High",
                "Price: High to Low",
                "Recently Added"
        };

        int checkedItem = 0;
        for (int i = 0; i < options.length; i++) {
            if (options[i].equals(currentSortOption)) {
                checkedItem = i;
                break;
            }
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Sort Products")
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    currentSortOption = options[which];
                    applyCategoryFilter();
                    dialog.dismiss();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private ArrayAdapter<String> getAdaptiveAdapter(String[] items) {
        boolean isDark = false;
        try {
            isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        } catch (Exception e) {
        }
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
        if (config.screenWidthDp < 450) {
            if (btnCart != null) btnCart.setText("Cart");
        }
    }

    private void loadProducts() {
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                allMenuProducts.clear();
                masterInventory.clear();
                cachedInventoryList.clear();

                long currentTime = System.currentTimeMillis();

                for (Product product : products) {
                    if (product == null || !product.isActive()) continue;

                    masterInventory.add(product);
                    cachedInventoryList.add(product);

                    if (product.getProductName() != null) {
                        fastInventoryLookup.put(product.getProductName().toLowerCase().trim(), product);
                    }

                    boolean isVisibleOnMenu = product.isSellable() ||
                            "finished".equalsIgnoreCase(product.getProductType()) ||
                            "Menu".equalsIgnoreCase(product.getProductType());

                    // PROMO OVERRIDE LOGIC
                    if (isVisibleOnMenu && product.isPromo()) {

                        if (product.isTemporaryPromo()) {
                            boolean hasStarted = product.getPromoStartDate() == 0 || currentTime >= product.getPromoStartDate();
                            boolean hasExpired = product.getPromoEndDate() > 0 && currentTime > (product.getPromoEndDate() + 86400000);
                            if (!hasStarted || hasExpired) {
                                isVisibleOnMenu = false;
                            }
                        }

                        if (isVisibleOnMenu) {
                            String seriesName = product.getPromoName() != null && !product.getPromoName().isEmpty()
                                    ? product.getPromoName() : "Special Promos";
                            product.setProductLine(seriesName);
                            product.setCategoryName("");
                        }
                    }

                    if (isVisibleOnMenu) {
                        allMenuProducts.add(product);
                    }
                }
                extractAndSetupCategories();
                applyCategoryFilter();
            }
        });
    }

    private void showRefundDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_refund, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        Spinner spinnerTime = view.findViewById(R.id.spinnerTimeFilter);
        EditText etOrderId = view.findViewById(R.id.etRefundOrderId);
        TextView tvDetails = view.findViewById(R.id.tvRefundOrderDetails);
        Spinner spinnerReason = view.findViewById(R.id.spinnerRefundReason);
        EditText etSpecificReason = view.findViewById(R.id.etRefundSpecificReason);
        Button btnCancel = view.findViewById(R.id.btnCancelRefund);
        Button btnConfirm = view.findViewById(R.id.btnConfirmRefund);

        boolean isDark = false;
        try {
            isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        } catch (Exception e) {
        }
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        if (etOrderId != null) etOrderId.setTextColor(textColor);
        if (tvDetails != null) tvDetails.setTextColor(textColor);
        if (etSpecificReason != null) etSpecificReason.setTextColor(textColor);

        String[] times = {"Today", "Yesterday", "Last 7 Days", "All Time"};
        ArrayAdapter<String> timeAdapter = getAdaptiveAdapter(times);
        if (spinnerTime != null) spinnerTime.setAdapter(timeAdapter);

        String[] reasons = {
                "Spoiled / Bad Quality",
                "Wrong Order Prepared",
                "Customer Changed Mind",
                "Spilled / Dropped",
                "Other (Specify below)"
        };
        ArrayAdapter<String> reasonAdapter = getAdaptiveAdapter(reasons);
        if (spinnerReason != null) spinnerReason.setAdapter(reasonAdapter);

        final List<Sales> foundSalesList = new ArrayList<>();
        final double[] totalRefundAmount = {0.0};

        Runnable fetchAndFilterSales = () -> {
            if (etOrderId == null || spinnerTime == null) return;
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
                    if (s.getPaymentMethod() != null && s.getPaymentMethod().contains("REFUNDED"))
                        continue;

                    boolean matchesTime = (selectedTime.equals("All Time")) ||
                            (selectedTime.equals("Yesterday") && s.getTimestamp() >= finalTimeThreshold && s.getTimestamp() < (now - oneDay)) ||
                            (!selectedTime.equals("Yesterday") && s.getTimestamp() >= finalTimeThreshold);

                    boolean matchesSearch = searchTxt.isEmpty() ||
                            (s.getOrderId() != null && s.getOrderId().toLowerCase().contains(searchTxt)) ||
                            (s.getProductName() != null && s.getProductName().toLowerCase().contains(searchTxt));

                    if (matchesTime && matchesSearch) {
                        foundSalesList.add(s);
                        totalRefundAmount[0] += s.getTotalPrice();

                        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd hh:mm a", Locale.getDefault());
                        details.append("[").append(sdf.format(new Date(s.getTimestamp()))).append("] ")
                                .append(s.getQuantity()).append("x ").append(s.getProductName())
                                .append("\n   ID: ").append(s.getOrderId().substring(0, 8).toUpperCase())
                                .append(" | ₱").append(s.getTotalPrice()).append("\n\n");
                    }
                }

                if (foundSalesList.isEmpty()) {
                    if (tvDetails != null) tvDetails.setText("No transactions found for the selected filters.");
                    if (btnConfirm != null) btnConfirm.setEnabled(false);
                } else {
                    details.append("========================\nTOTAL ELIGIBLE REFUND: ₱").append(totalRefundAmount[0]);
                    if (tvDetails != null) tvDetails.setText(details.toString());
                    if (btnConfirm != null) btnConfirm.setEnabled(true);
                }
            });
        };

        if (spinnerTime != null) {
            spinnerTime.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    fetchAndFilterSales.run();
                }

                @Override
                public void onNothingSelected(AdapterView<?> p) {
                }
            });
        }

        if (etOrderId != null) {
            etOrderId.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    fetchAndFilterSales.run();
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
        }

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());

        if (btnConfirm != null) {
            btnConfirm.setOnClickListener(v -> {
                String selectedReason = spinnerReason != null ? spinnerReason.getSelectedItem().toString() : "";
                String specificNotes = etSpecificReason != null ? etSpecificReason.getText().toString().trim() : "";
                String finalReason = selectedReason + (specificNotes.isEmpty() ? "" : " - " + specificNotes);

                String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
                if (ownerId == null || ownerId.isEmpty())
                    ownerId = AuthManager.getInstance().getCurrentUserId();

                for (Sales sale : foundSalesList) {
                    sale.setPaymentMethod("REFUNDED: " + finalReason);

                    FirebaseFirestore.getInstance().collection("users").document(ownerId)
                            .collection("sales").document(sale.getId())
                            .update("paymentMethod", "REFUNDED: " + finalReason);

                    Product p = findInventoryProduct(sale.getProductName());

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
        }

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

        List<String> sortedNorms = new ArrayList<>(mainCatsMap.keySet());
        Collections.sort(sortedNorms);
        String currentHash = sortedNorms.toString() + "_" + currentMainCategory + "_" + currentSubCategory;

        if (!currentHash.equals(lastCategoryHash)) {
            lastCategoryHash = currentHash;
            setupHierarchicalCategoryChips(new ArrayList<>(mainCatsMap.values()));
        }
    }

    private double safeGetDouble(Map<?, ?> map, String key) {
        if (map == null || !map.containsKey(key) || map.get(key) == null) return 0.0;
        Object val = map.get(key);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            String strVal = ((String) val).trim();
            if (strVal.isEmpty() || strVal.equalsIgnoreCase("null")) return 0.0;
            try { return Double.parseDouble(strVal); } catch (Exception e) { return 0.0; }
        }
        return 0.0;
    }

    private void setupHierarchicalCategoryChips(List<String> mainCategories) {
        if (layoutCategoryChips == null) return;
        layoutCategoryChips.removeAllViews();
        layoutCategoryChips.setOrientation(LinearLayout.VERTICAL);

        LinearLayout mainRow = new LinearLayout(this);
        mainRow.setOrientation(LinearLayout.HORIZONTAL);
        for (String main : mainCategories) {
            boolean isSelected = normalizeCategoryName(main).equals(normalizeCategoryName(currentMainCategory));
            MaterialButton chip = createChip(main, isSelected, false);
            chip.setOnClickListener(v -> {
                currentMainCategory = main;
                currentSubCategory = "All";
                extractAndSetupCategories();
                applyCategoryFilter();
            });
            if (!main.equalsIgnoreCase("All")) {
                chip.setOnLongClickListener(v -> {
                    if (isReadOnly) {
                        Toast.makeText(SellList.this, "Admin access required", Toast.LENGTH_SHORT).show();
                        return true;
                    }
                    showCategoryOptionsDialog(main, false);
                    return true;
                });
            }
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
                    if (!sub.equalsIgnoreCase("All")) {
                        chip.setOnLongClickListener(v -> {
                            if (isReadOnly) {
                                Toast.makeText(SellList.this, "Admin access required", Toast.LENGTH_SHORT).show();
                                return true;
                            }
                            showCategoryOptionsDialog(sub, true);
                            return true;
                        });
                    }
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
            chip.setTextSize(12f);
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
            chip.setTextColor(getResources().getColor(R.color.backgroundColor));
        } else {
            chip.setChecked(false);
            chip.setTextColor(getResources().getColor(R.color.textColorSecondary));
        }
        return chip;
    }

    private void applyCategoryFilter() {
        filteredProducts.clear();
        String query = "";

        if (etSearchProduct != null && etSearchProduct.getText() != null) {
            query = etSearchProduct.getText().toString().toLowerCase().trim();
        }

        for (Product p : allMenuProducts) {
            if (p == null) continue;

            String pMain = p.getProductLine() != null ? p.getProductLine().trim() : "Uncategorized";
            String pSub = p.getCategoryName() != null ? p.getCategoryName().trim() : "Uncategorized";
            String pName = p.getProductName() != null ? p.getProductName().toLowerCase() : "";

            boolean matchesSearch = query.isEmpty() || pName.contains(query);

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

        Collections.sort(filteredProducts, (p1, p2) -> {
            if (p1 == null && p2 == null) return 0;
            if (p1 == null) return 1;
            if (p2 == null) return -1;

            String name1 = p1.getProductName() != null ? p1.getProductName() : "Unnamed Product";
            String name2 = p2.getProductName() != null ? p2.getProductName() : "Unnamed Product";

            switch (currentSortOption) {
                case "A-Z":
                    return name1.compareToIgnoreCase(name2);
                case "Z-A":
                    return name2.compareToIgnoreCase(name1);
                case "Price: Low to High":
                    return Double.compare(p1.getSellingPrice(), p2.getSellingPrice());
                case "Price: High to Low":
                    return Double.compare(p2.getSellingPrice(), p1.getSellingPrice());
                case "Recently Added":
                    return Long.compare(p2.getDateAdded(), p1.getDateAdded());
                default:
                    return name1.compareToIgnoreCase(name2);
            }
        });

        if (sellAdapter != null) {
            sellAdapter.filterList(filteredProducts, masterInventory);
        }
    }

    private String normalizeCategoryName(String name) {
        if (name == null || name.trim().isEmpty()) return "uncategorized";
        return name.toLowerCase().replaceAll("\\s+", "");
    }

    private Product findInventoryProduct(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        return fastInventoryLookup.get(name.toLowerCase().trim());
    }

    private void updateDialogTotalPrice(TextView tvPrice, double base, double size, double addons) {
        double total = base + size + addons;
        if (tvPrice != null) tvPrice.setText(String.format(Locale.US, "₱%.2f", total));
    }

    private void showProductOptionsDialog(Product product, int maxBaseServings) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_product_options, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        boolean isDark = false;
        try {
            isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        } catch (Exception e) {
            int currentNightMode = getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK;
            isDark = currentNightMode == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        }
        int dynamicTextColor = isDark ? Color.WHITE : Color.BLACK;

        TextView tvName = view.findViewById(R.id.tvOptionProductName);
        TextView tvPrice = view.findViewById(R.id.tvOptionProductPrice);
        EditText etQty = view.findViewById(R.id.tvOptionQty);
        Button btnCancel = view.findViewById(R.id.btnOptionCancel);
        Button btnAddToCart = view.findViewById(R.id.btnOptionAddToCart);

        // Replace the old layout bindings with these exact matching IDs:
        LinearLayout layoutSizesContainer = view.findViewById(R.id.layoutSizes);
        RadioGroup rgSizes = view.findViewById(R.id.rgSizes);

        LinearLayout layoutAddonsContainer = view.findViewById(R.id.layoutAddons);
        LinearLayout containerAddonsList = view.findViewById(R.id.containerAddons);

        // Mapped to your existing layoutSugar to prevent crashes
        LinearLayout layoutNotesContainer = view.findViewById(R.id.layoutSugar);
        LinearLayout containerNotesList = view.findViewById(R.id.layoutSugar);

        String safeName = product.getProductName() != null ? product.getProductName() : "Unnamed Product";
        if (tvName != null) tvName.setText(safeName);
        if (tvPrice != null) tvPrice.setText(String.format(Locale.US, "₱%.2f", product.getSellingPrice()));

        final double[] basePrice = {product.getSellingPrice()};
        final double[] selectedSizePrice = {0.0};
        final double[] selectedAddonsPrice = {0.0};
        final String[] selectedSizeName = {""};

        dynamicNoteViews.clear();

        Object sizesObj = product.getSizesList();
        if (sizesObj instanceof List) {
            List<?> sList = (List<?>) sizesObj;
            if (!sList.isEmpty()) {
                if (layoutSizesContainer != null) layoutSizesContainer.setVisibility(View.VISIBLE);
                boolean isFirstAvailableSizeSelected = false;
                Set<String> addedSizes = new HashSet<>();

                for (int i = 0; i < sList.size(); i++) {
                    Object obj = sList.get(i);
                    if (obj instanceof Map) {
                        Map<?, ?> size = (Map<?, ?>) obj;
                        String sName = (String) size.get("name");

                        if (sName == null || addedSizes.contains(sName)) continue;
                        addedSizes.add(sName);

                        RadioButton rb = new RadioButton(this);
                        rb.setTextColor(dynamicTextColor);

                        String linkedMat = (String) size.get("linkedMaterial");

                        // PERCENTAGE LOGIC FOR SIZES
                        double sPrice = 0;
                        if (size.containsKey("priceDiff")) {
                            double pct = safeGetDouble(size, "priceDiff");
                            sPrice = basePrice[0] * (pct / 100.0);
                        } else {
                            sPrice = safeGetDouble(size, "price");
                        }

                        double deductQty = safeGetDouble(size, "deductQty");

                        String text = sName;
                        if (sPrice > 0) text += String.format(Locale.US, " (+₱%.2f)", sPrice);

                        if (linkedMat != null && !linkedMat.isEmpty() && deductQty > 0) {
                            Product sizeMat = findInventoryProduct(linkedMat);
                            double currentStock = sizeMat != null ? sizeMat.getQuantity() : 0;
                            if (currentStock < deductQty) {
                                text += " (Out of Stock)";
                                if (!isFlexibleHandlingEnabled) {
                                    rb.setEnabled(false);
                                }
                            }
                        }
                        rb.setText(text);
                        rb.setId(View.generateViewId());
                        rb.setTag(size);

                        final double finalSPrice = sPrice;
                        final String finalSName = sName;

                        rb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                            if (isChecked) {
                                selectedSizePrice[0] = finalSPrice;
                                selectedSizeName[0] = finalSName;
                                updateDialogTotalPrice(tvPrice, basePrice[0], selectedSizePrice[0], selectedAddonsPrice[0]);
                            }
                        });
                        if (rgSizes != null) rgSizes.addView(rb);

                        if (rb.isEnabled() && !isFirstAvailableSizeSelected) {
                            rb.setChecked(true);
                            isFirstAvailableSizeSelected = true;
                        }
                    }
                }
            }
        }

        List<CheckBox> addonBoxes = new ArrayList<>();
        Object addonsObj = product.getAddonsList();
        if (addonsObj instanceof List) {
            List<?> aList = (List<?>) addonsObj;
            if (!aList.isEmpty()) {
                if (layoutAddonsContainer != null) layoutAddonsContainer.setVisibility(View.VISIBLE);
                Set<String> addedAddons = new HashSet<>();

                for (Object obj : aList) {
                    if (obj instanceof Map) {
                        Map<?, ?> addon = (Map<?, ?>) obj;
                        String aName = (String) addon.get("name");

                        if (aName == null || addedAddons.contains(aName)) continue;
                        addedAddons.add(aName);

                        CheckBox cb = new CheckBox(this);
                        cb.setTextColor(dynamicTextColor);

                        String linkedMat = (String) addon.get("linkedMaterial");
                        double aPrice = safeGetDouble(addon, "price");
                        double deductQty = safeGetDouble(addon, "deductQty");

                        String text = aName;
                        if (aPrice > 0) text += String.format(Locale.US, " (+₱%.2f)", aPrice);

                        if (linkedMat != null && !linkedMat.isEmpty() && deductQty > 0) {
                            Product addonMat = findInventoryProduct(linkedMat);
                            double currentStock = addonMat != null ? addonMat.getQuantity() : 0;
                            if (currentStock < deductQty) {
                                text += " (Out of Stock)";
                                if (!isFlexibleHandlingEnabled) {
                                    cb.setEnabled(false);
                                }
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
                        if (containerAddonsList != null) containerAddonsList.addView(cb);
                    }
                }
            }
        }

        final String[] selectedNoteText = {""};
        Object notesObj = product.getNotesList();
        if (notesObj instanceof List) {
            List<?> nList = (List<?>) notesObj;
            if (!nList.isEmpty()) {
                if (layoutNotesContainer != null) layoutNotesContainer.setVisibility(View.VISIBLE);

                Map<String, RadioGroup> noteGroups = new HashMap<>();
                Set<String> addedNotes = new HashSet<>();

                for (Object obj : nList) {
                    if (obj instanceof Map) {
                        Map<?, ?> noteMap = (Map<?, ?>) obj;
                        String noteType = noteMap.containsKey("type") ? String.valueOf(noteMap.get("type")) : "Note";
                        String value = noteMap.containsKey("value") ? String.valueOf(noteMap.get("value")) : "";

                        String uniqueNoteKey = noteType + "_" + value;
                        if (addedNotes.contains(uniqueNoteKey)) continue;
                        addedNotes.add(uniqueNoteKey);

                        if (noteType.toLowerCase().contains("sugar") || noteType.toLowerCase().contains("sweetness")) {
                            // Handled dynamically below
                        } else {
                            if (!noteGroups.containsKey(noteType)) {
                                TextView tvTitle = new TextView(this);
                                tvTitle.setText(noteType);
                                tvTitle.setTextColor(dynamicTextColor);
                                tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                                tvTitle.setPadding(0, 16, 0, 8);
                                if (containerNotesList != null) containerNotesList.addView(tvTitle);

                                RadioGroup rg = new RadioGroup(this);
                                rg.setOrientation(LinearLayout.VERTICAL);
                                if (containerNotesList != null) containerNotesList.addView(rg);
                                noteGroups.put(noteType, rg);
                            }

                            RadioButton rb = new RadioButton(this);
                            rb.setText(value);
                            rb.setTextColor(dynamicTextColor);
                            rb.setId(View.generateViewId());
                            noteGroups.get(noteType).addView(rb);
                        }
                    }
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

                boolean hasSugar = false;
                String sugarNoteType = "Sugar Level";
                for (Object obj : nList) {
                    if (obj instanceof Map) {
                        String type = String.valueOf(((Map<?, ?>) obj).get("type"));
                        if (type.toLowerCase().contains("sugar") || type.toLowerCase().contains("sweetness")) {
                            hasSugar = true;
                            sugarNoteType = type;
                            break;
                        }
                    }
                }

                if (hasSugar) {
                    TextView lblTitle = new TextView(this);
                    lblTitle.setText(sugarNoteType);
                    lblTitle.setTextColor(dynamicTextColor);
                    lblTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                    lblTitle.setPadding(0, 16, 0, 8);
                    if (containerNotesList != null) containerNotesList.addView(lblTitle);

                    Spinner sugarSpinner = new Spinner(this);
                    List<String> sugarNames = new ArrayList<>();
                    for (Product p : masterInventory) {
                        if (p.getProductName() != null) {
                            String n = p.getProductName().toLowerCase();
                            if (n.contains("sugar") || n.contains("syrup") || n.contains("sweetener")) {
                                sugarNames.add(p.getProductName());
                            }
                        }
                    }
                    if (sugarNames.isEmpty()) sugarNames.add("Default Sugar");

                    ArrayAdapter<String> sugarAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, sugarNames);
                    sugarAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    sugarSpinner.setAdapter(sugarAdapter);

                    TextView lblSugarSource = new TextView(this);
                    lblSugarSource.setText("Sugar Type:");
                    lblSugarSource.setTextColor(dynamicTextColor);
                    lblSugarSource.setTextSize(12);
                    if (containerNotesList != null) containerNotesList.addView(lblSugarSource);
                    if (containerNotesList != null) containerNotesList.addView(sugarSpinner);

                    Spinner levelSpinner = new Spinner(this);
                    List<String> levels = Arrays.asList(
                            "100% (Full Sugar)",
                            "75% (Less Sugar)",
                            "50% (Half Sugar)",
                            "25% (Quarter Sugar)",
                            "0% (No Sugar)"
                    );
                    ArrayAdapter<String> levelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, levels);
                    levelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    levelSpinner.setAdapter(levelAdapter);

                    TextView lblSugarLevel = new TextView(this);
                    lblSugarLevel.setText("Sugar Level:");
                    lblSugarLevel.setTextColor(dynamicTextColor);
                    lblSugarLevel.setTextSize(12);
                    if (containerNotesList != null) containerNotesList.addView(lblSugarLevel);
                    if (containerNotesList != null) containerNotesList.addView(levelSpinner);

                    dynamicNoteViews.put(sugarNoteType + "_sugarType", sugarSpinner);
                    dynamicNoteViews.put(sugarNoteType + "_level", levelSpinner);
                }
            }
        }

        List<CheckBox> ingredientBoxes = new ArrayList<>();
        Object bomObj = product.getBomList();
        if (bomObj instanceof List) {
            List<?> bList = (List<?>) bomObj;
            if (!bList.isEmpty()) {
                List<View> missingIngredientViews = new ArrayList<>();
                boolean hasMissingIngredients = false;

                TextView tvIngredientsTitle = new TextView(this);
                tvIngredientsTitle.setText("Missing Ingredients (Uncheck to bypass)");
                tvIngredientsTitle.setTextColor(dynamicTextColor);
                tvIngredientsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                tvIngredientsTitle.setPadding(0, 24, 0, 8);
                missingIngredientViews.add(tvIngredientsTitle);

                Set<String> addedIngredients = new HashSet<>();

                for (Object obj : bList) {
                    if (obj instanceof Map) {
                        Map<?, ?> bomItem = (Map<?, ?>) obj;

                        String rawName = (String) bomItem.get("rawMaterialName");
                        if (rawName == null) rawName = (String) bomItem.get("materialName");
                        String rawId = (String) bomItem.get("rawMaterialId");

                        String uniqueKey = rawId != null ? rawId : rawName;
                        if (uniqueKey == null || addedIngredients.contains(uniqueKey)) continue;

                        Product inventoryItem = findInventoryProduct(rawName);
                        if (inventoryItem != null) {

                            double requiredQty = safeGetDouble(bomItem, "quantityRequired");
                            if (requiredQty == 0) requiredQty = safeGetDouble(bomItem, "quantity");

                            if (inventoryItem.getQuantity() < requiredQty) {
                                hasMissingIngredients = true;
                                addedIngredients.add(uniqueKey);

                                CheckBox cb = new CheckBox(this);
                                cb.setText(rawName + " (OUT OF STOCK - Uncheck to bypass)");
                                cb.setTextColor(Color.RED);
                                cb.setChecked(true);
                                cb.setTag(uniqueKey);

                                if (!isFlexibleHandlingEnabled) {
                                    cb.setEnabled(false);
                                }

                                ingredientBoxes.add(cb);
                                missingIngredientViews.add(cb);
                            }
                        }
                    }
                }

                if (hasMissingIngredients && containerNotesList != null) {
                    for (View v : missingIngredientViews) {
                        containerNotesList.addView(v);
                    }
                }
            }
        }

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());
        if (btnAddToCart != null) btnAddToCart.setOnClickListener(v -> {
            String qtyStr = etQty != null ? etQty.getText().toString().trim() : "1";
            int qty = qtyStr.isEmpty() ? 1 : Integer.parseInt(qtyStr);
            if (qty <= 0) return;

            StringBuilder excludedBuilder = new StringBuilder();
            StringBuilder excludedNamesBuilder = new StringBuilder();

            for (CheckBox cb : ingredientBoxes) {
                if (!cb.isChecked()) {
                    excludedBuilder.append(cb.getTag() != null ? cb.getTag().toString() : "").append(",");
                    String ingName = cb.getText().toString().replaceAll(" \\(OUT OF STOCK.*\\)", "");
                    excludedNamesBuilder.append("No ").append(ingName).append(", ");
                }
            }
            String excludedIngredients = excludedBuilder.toString();

            if (qty > maxBaseServings && excludedIngredients.isEmpty() && !isFlexibleHandlingEnabled) {
                Toast.makeText(this, "Not enough raw ingredients! Uncheck missing ingredients to override.", Toast.LENGTH_LONG).show();
                return;
            }

            double finalPrice = basePrice[0] + selectedSizePrice[0] + selectedAddonsPrice[0];

            StringBuilder extrasBuilder = new StringBuilder();
            for (CheckBox cb : addonBoxes) {
                if (cb.isChecked())
                    extrasBuilder.append(cb.getText().toString().replaceAll(" \\(\\+₱.*\\)", "")).append(", ");
            }

            if (!selectedNoteText[0].isEmpty()) extrasBuilder.append(selectedNoteText[0]);

            for (String key : dynamicNoteViews.keySet()) {
                if (key.endsWith("_sugarType")) continue;

                if (key.endsWith("_level")) {
                    String baseType = key.replace("_level", "");
                    Spinner sType = (Spinner) dynamicNoteViews.get(baseType + "_sugarType");
                    Spinner sLevel = (Spinner) dynamicNoteViews.get(key);

                    String selectedSugar = sType != null && sType.getSelectedItem() != null ? sType.getSelectedItem().toString() : "Sugar";
                    String selectedLevel = sLevel != null && sLevel.getSelectedItem() != null ? sLevel.getSelectedItem().toString() : "100%";

                    extrasBuilder.append(selectedLevel).append(" ").append(selectedSugar).append(", ");
                } else if (dynamicNoteViews.get(key) instanceof EditText) {
                    String val = ((EditText) dynamicNoteViews.get(key)).getText().toString().trim();
                    if (!val.isEmpty()) {
                        extrasBuilder.append(key).append(": ").append(val).append(", ");
                    }
                }
            }

            if (excludedNamesBuilder.length() > 0) {
                extrasBuilder.append("(").append(excludedNamesBuilder.toString().trim());
                if (extrasBuilder.toString().endsWith(",")) {
                    extrasBuilder.setLength(extrasBuilder.length() - 1);
                }
                extrasBuilder.append(") ");
            }

            String extraDetails = extrasBuilder.toString().trim();
            if (extraDetails.endsWith(","))
                extraDetails = extraDetails.substring(0, extraDetails.length() - 1).trim();

            String safeId = product.getProductId() != null ? product.getProductId() : "local:" + product.getLocalId();
            String saveName = product.getProductName() != null ? product.getProductName() : "Unnamed Product";

            boolean added = cartManager.addItem(safeId, saveName, finalPrice, qty, maxBaseServings, selectedSizeName[0], extraDetails, excludedIngredients);

            if (added) {
                Toast.makeText(this, "Added to cart", Toast.LENGTH_SHORT).show();
                updateCheckoutButton();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Error adding to cart", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
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
        String[] options = {
                "Copy Pricing",
                "Copy Recipe (BOM)",
                "Copy Sizes, Add-ons & Notes"
        };
        boolean[] checked = {false, false, false};

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Copy from \"" + source.getProductName() + "\"")
                .setMultiChoiceItems(options, checked, (dialog, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton("Next", (dialog, w) -> {
                    boolean anyChecked = false;
                    for (boolean b : checked)
                        if (b) {
                            anyChecked = true;
                            break;
                        }

                    if (!anyChecked) {
                        Toast.makeText(this, "Select at least one property to copy", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    showTargetScopeDialog(source, checked[0], checked[1], checked[2]);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showTargetScopeDialog(Product source, boolean copyPricing, boolean copyBom, boolean copyVariants) {
        String[] scopes = {"Entire Menu (All Products)", "Specific Category", "Specific Products"};

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Apply changes to...")
                .setSingleChoiceItems(scopes, -1, (dialog, which) -> {
                    dialog.dismiss();

                    if (which == 0) {
                        List<Product> targets = new ArrayList<>();
                        for (Product p : allMenuProducts) {
                            if (!p.getProductId().equals(source.getProductId())) targets.add(p);
                        }
                        executeAdvancedCopy(source, targets, copyPricing, copyBom, copyVariants);
                    } else if (which == 1) {
                        showCategorySelectionDialog(source, copyPricing, copyBom, copyVariants);
                    } else if (which == 2) {
                        showProductMultiSelectDialog(source, copyPricing, copyBom, copyVariants);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCategorySelectionDialog(Product source, boolean copyPricing, boolean copyBom, boolean copyVariants) {
        Set<String> categorySet = new HashSet<>();
        for (Product p : allMenuProducts) {
            if (p.getCategoryName() != null && !p.getCategoryName().isEmpty()) {
                categorySet.add(p.getCategoryName());
            }
        }
        String[] categories = categorySet.toArray(new String[0]);

        if (categories.length == 0) {
            Toast.makeText(this, "No categories found", Toast.LENGTH_SHORT).show();
            return;
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Target Category")
                .setSingleChoiceItems(categories, -1, (dialog, which) -> {
                    dialog.dismiss();
                    String selectedCat = categories[which];

                    List<Product> targets = new ArrayList<>();
                    for (Product p : allMenuProducts) {
                        if (!p.getProductId().equals(source.getProductId()) && selectedCat.equals(p.getCategoryName())) {
                            targets.add(p);
                        }
                    }
                    executeAdvancedCopy(source, targets, copyPricing, copyBom, copyVariants);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showProductMultiSelectDialog(Product source, boolean copyPricing, boolean copyBom, boolean copyVariants) {
        List<Product> availableProducts = new ArrayList<>();
        for (Product p : allMenuProducts) {
            if (!p.getProductId().equals(source.getProductId())) {
                availableProducts.add(p);
            }
        }

        String[] productNames = new String[availableProducts.size()];
        boolean[] checkedItems = new boolean[availableProducts.size()];
        for (int i = 0; i < availableProducts.size(); i++) {
            productNames[i] = availableProducts.get(i).getProductName();
        }

        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Select Target Products")
                .setMultiChoiceItems(productNames, checkedItems, (dialog, which, isChecked) -> {
                    checkedItems[which] = isChecked;
                })
                .setPositiveButton("Apply", (dialog, which) -> {
                    List<Product> targets = new ArrayList<>();
                    for (int i = 0; i < checkedItems.length; i++) {
                        if (checkedItems[i]) {
                            targets.add(availableProducts.get(i));
                        }
                    }
                    if (targets.isEmpty()) {
                        Toast.makeText(this, "No products selected", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    executeAdvancedCopy(source, targets, copyPricing, copyBom, copyVariants);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeAdvancedCopy(Product source, List<Product> targets, boolean copyPricing, boolean copyBom, boolean copyVariants) {
        if (targets.isEmpty()) {
            Toast.makeText(this, "No target products found.", Toast.LENGTH_SHORT).show();
            return;
        }

        int count = 0;
        for (Product target : targets) {
            boolean changed = false;

            if (copyPricing) {
                target.setSellingPrice(source.getSellingPrice());
                target.setCostPrice(source.getCostPrice());
                changed = true;
            }

            if (copyBom) {
                target.setBomList(source.getBomList() != null ? new ArrayList<>(source.getBomList()) : new ArrayList<>());
                changed = true;
            }

            if (copyVariants) {
                target.setSizesList(source.getSizesList() != null ? new ArrayList<>(source.getSizesList()) : new ArrayList<>());
                target.setAddonsList(source.getAddonsList() != null ? new ArrayList<>(source.getAddonsList()) : new ArrayList<>());
                target.setNotesList(source.getNotesList() != null ? new ArrayList<>(source.getNotesList()) : new ArrayList<>());
                changed = true;
            }

            if (changed) {
                productRepository.updateProduct(target, null, new ProductRepository.OnProductUpdatedListener() {
                    @Override
                    public void onProductUpdated() {
                    }

                    @Override
                    public void onError(String error) {
                    }
                });
                count++;
            }
        }

        if (sellAdapter != null) sellAdapter.clearSelection();
        hideBulkActionBar();
        Toast.makeText(this, "Successfully copied to " + count + " product(s)!", Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cartManager != null && btnCart != null) {
            updateCheckoutButton();
        }
    }

    private void updateCheckoutButton() {
        if (cartManager == null || btnCart == null) return;
        int count = cartManager.getItems().size();
        if (count > 0) {
            btnCart.setVisibility(View.VISIBLE);
            btnCart.setText("Cart (" + count + ")");
            btnCart.setEnabled(true);
        } else {
            btnCart.setVisibility(View.VISIBLE);
            btnCart.setText("Cart");
            btnCart.setEnabled(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (archiveListener != null) archiveListener.remove();
    }

    // =======================================================================================
    // CATEGORY MANAGEMENT SYSTEM
    // =======================================================================================
    private void showCategoryOptionsDialog(String categoryName, boolean isSubCategory) {
        String type = isSubCategory ? "Sub-Category" : "Main Category";
        String[] options = {"Rename " + type, "Delete " + type};

        new AlertDialog.Builder(this)
                .setTitle("Manage: " + categoryName)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        showRenameCategoryDialog(categoryName, isSubCategory);
                    } else if (which == 1) {
                        showDeleteCategoryDialog(categoryName, isSubCategory);
                    }
                })
                .show();
    }

    private void showRenameCategoryDialog(String oldName, boolean isSubCategory) {
        EditText input = new EditText(this);
        input.setText(oldName);
        input.setPadding(40, 40, 40, 40);

        new AlertDialog.Builder(this)
                .setTitle("Rename Category")
                .setView(input)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (!newName.isEmpty() && !newName.equalsIgnoreCase(oldName)) {
                        updateCategoryInDatabase(oldName, newName, isSubCategory);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteCategoryDialog(String catName, boolean isSubCategory) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Category")
                .setMessage("This will move all products in '" + catName + "' to 'Uncategorized'. It will NOT delete the products themselves. Continue?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    updateCategoryInDatabase(catName, "Uncategorized", isSubCategory);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateCategoryInDatabase(String oldName, String newName, boolean isSubCategory) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();
        if (ownerId == null) return;

        int count = 0;
        com.google.firebase.firestore.WriteBatch batch = com.google.firebase.firestore.FirebaseFirestore.getInstance().batch();
        List<Product> productsToUpdateLocally = new java.util.ArrayList<>();

        for (Product p : masterInventory) {
            boolean changed = false;

            String savedProductLine = p.getProductLine() != null ? p.getProductLine().trim() : "";
            String savedCategoryName = p.getCategoryName() != null ? p.getCategoryName().trim() : "";

            boolean isTargetingUncategorized = oldName.equalsIgnoreCase("Uncategorized");

            boolean matchesMain = !isSubCategory && (
                    oldName.equalsIgnoreCase(savedProductLine) ||
                            (isTargetingUncategorized && (savedProductLine.isEmpty() || savedProductLine.equalsIgnoreCase("null") || savedProductLine.equalsIgnoreCase("Uncategorized")))
            );

            boolean matchesSub = isSubCategory && (
                    oldName.equalsIgnoreCase(savedCategoryName) ||
                            (isTargetingUncategorized && (savedCategoryName.isEmpty() || savedCategoryName.equalsIgnoreCase("null") || savedCategoryName.equalsIgnoreCase("Uncategorized")))
            );

            if (matchesMain) {
                p.setProductLine(newName);
                changed = true;
            } else if (matchesSub) {
                p.setCategoryName(newName);
                changed = true;
            }

            if (changed) {
                productsToUpdateLocally.add(p);

                if (p.getProductId() != null && !p.getProductId().isEmpty() && !p.getProductId().startsWith("local:")) {
                    com.google.firebase.firestore.DocumentReference ref = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("users").document(ownerId)
                            .collection("products").document(p.getProductId());

                    java.util.Map<String, Object> updates = new java.util.HashMap<>();
                    if (!isSubCategory) {
                        updates.put("productLine", newName);
                    } else {
                        updates.put("categoryName", newName);
                    }
                    batch.set(ref, updates, com.google.firebase.firestore.SetOptions.merge());
                }
                count++;
            }
        }

        if (count > 0) {
            final int finalCount = count;

            SalesInventoryApplication.getProductRepository().upsertFromRemoteBulk(productsToUpdateLocally);

            batch.commit().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Updated " + finalCount + " products to '" + newName + "'!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to update cloud: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                }
            });

            if (!isSubCategory && currentMainCategory.equalsIgnoreCase(oldName)) currentMainCategory = newName;
            if (isSubCategory && currentSubCategory.equalsIgnoreCase(oldName)) currentSubCategory = newName;

            extractAndSetupCategories();
            applyCategoryFilter();
        } else {
            Toast.makeText(this, "No products found to update.", Toast.LENGTH_SHORT).show();
        }
    }
}