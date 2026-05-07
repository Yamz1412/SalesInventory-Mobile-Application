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
import com.google.firebase.database.FirebaseDatabase;
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
    private MaterialButton btnCart, btnLockScreen, btnUnlock, btnRefund;
    private LinearLayout layoutLockScreen;
    private TextInputEditText etUnlockPassword;
    private TextView tvShiftStatus;

    private EditText etSearchProduct;
    private ImageButton btnFilterSort, btnReturn;
    private LinearLayout layoutCategoryChips;
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
    private View cardArchiveBadge;
    private com.google.firebase.firestore.ListenerRegistration archiveListener;

    private boolean isReadOnly = false;
    private boolean isAdminFlag = false;
    private boolean isManagerFlag = false;

    private String currentMainCategory = "All";
    private String currentSubCategory = "All";
    private String lastCategoryHash = "";
    private ActionMode actionMode;
    private Map<String, View> dynamicNoteViews = new HashMap<>();

    private com.google.android.material.switchmaterial.SwitchMaterial switchShowPromos;
    private List<Product> promoPseudoProducts = new ArrayList<>();
    private Map<String, List<String>> promoProductIdsMap = new HashMap<>();
    private Map<String, String> promoDiscountTypeMap = new HashMap<>();
    private Map<String, Double> promoDiscountValueMap = new HashMap<>();

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

        switchShowPromos = findViewById(R.id.switchShowPromos);
        if (switchShowPromos != null) {
            switchShowPromos.setOnCheckedChangeListener((buttonView, isChecked) -> applyCategoryFilter());
        }

        Button btnManagePromosShortcut = findViewById(R.id.btnManagePromosShortcut);
        if (btnManagePromosShortcut != null) {
            btnManagePromosShortcut.setOnClickListener(v -> {
                if (AuthManager.getInstance().hasManagerAccess()) {
                    startActivity(new Intent(SellList.this, ManagePromosActivity.class));
                } else {
                    Toast.makeText(SellList.this, "Access Denied: Only Managers can manage promos.", Toast.LENGTH_SHORT).show();
                }
            });
        }

        loadActivePromos(); // Fetch promos from Firebase

        // Lock Screen & Top Button Bindings
        tvShiftStatus = findViewById(R.id.tvShiftStatus);
        btnCart = findViewById(R.id.btnCart);
        btnReturn = findViewById(R.id.btnReturn);
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
        btnReturn.setOnClickListener(view -> finish());

        // Shift is logged at SignInActivity, just update the UI here
        if (tvShiftStatus != null) {
            tvShiftStatus.setText("🟢 Shift Active (Automated)");
        }

        if (btnLockScreen != null) {
            btnLockScreen.setOnClickListener(v -> {
                // FIXED: Route to Global Break Time Logger and turn dot red
                SalesInventoryApplication.logAttendance("BREAK_START");
                updateOnlineStatus(false);
                if (layoutLockScreen != null) layoutLockScreen.setVisibility(View.VISIBLE);
                Toast.makeText(this, "Register Locked (On Break)", Toast.LENGTH_SHORT).show();
            });
        }

        if (btnUnlock != null) {
            btnUnlock.setOnClickListener(v -> {
                if (etUnlockPassword != null) {
                    String pass = etUnlockPassword.getText().toString().trim();
                    if (pass.isEmpty()) {
                        etUnlockPassword.setError("Password required to Unlock");
                        return;
                    }

                    String email = com.google.firebase.auth.FirebaseAuth.getInstance().getCurrentUser().getEmail();
                    if (email != null) {
                        com.google.firebase.auth.FirebaseAuth.getInstance()
                                .signInWithEmailAndPassword(email, pass)
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful()) {
                                        etUnlockPassword.setText("");
                                        // End break and turn dot green
                                        SalesInventoryApplication.logAttendance("BREAK_END");
                                        updateOnlineStatus(true);
                                        if (layoutLockScreen != null) layoutLockScreen.setVisibility(View.GONE);
                                        Toast.makeText(this, "Register Unlocked", Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(SellList.this, "Incorrect Password", Toast.LENGTH_SHORT).show();
                                    }
                                });
                    }
                }
            });
        }

        sellAdapter = new SellAdapter(this, filteredProducts, masterInventory, new SellAdapter.OnProductClickListener() {
            @Override
            public void onProductClick(Product product, int maxServings) {
                if (product.isPromo()) {
                    showPromoProductsDialog(product);
                    return;
                }
                showProductOptionsDialog(product, maxServings, null, null, 0.0);
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

        layoutArchiveContainer = findViewById(R.id.layout_archive_container);
        btnArchive = findViewById(R.id.btn_archive);
        tvArchiveBadge = findViewById(R.id.tvArchiveBadge);
        cardArchiveBadge = findViewById(R.id.cardArchiveBadge);

        if (btnArchive != null) {
            btnArchive.setOnClickListener(v -> startActivity(new Intent(this, DeleteProductActivity.class)));
        }

        // Admin & Sub-Admin Role Checks
        AuthManager.getInstance().refreshCurrentUserStatus(success -> runOnUiThread(() -> {
            isAdminFlag = AuthManager.getInstance().isCurrentUserAdmin();
            isManagerFlag = AuthManager.getInstance().hasManagerAccess();
            isReadOnly = !isManagerFlag; // Staff are read-only

            if (sellAdapter != null) {
                sellAdapter.setAdminStatus(isManagerFlag);
            }

            applyRoleVisibility();
        }));

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

    private void loadActivePromos() {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();
        if (ownerId == null) return;

        // --- 1. LOAD FROM OFFLINE CACHE FIRST ---
        android.content.SharedPreferences prefs = getSharedPreferences("OfflinePromoCache_" + ownerId, MODE_PRIVATE);
        String cachedPromos = prefs.getString("promos_json", "[]");
        String cachedMapping = prefs.getString("promo_mapping", "{}");

        try {
            org.json.JSONArray promosArr = new org.json.JSONArray(cachedPromos);
            org.json.JSONObject mappingObj = new org.json.JSONObject(cachedMapping);

            promoPseudoProducts.clear();
            promoProductIdsMap.clear();

            for (int i = 0; i < promosArr.length(); i++) {
                org.json.JSONObject pObj = promosArr.getJSONObject(i);
                Product p = new Product();
                p.setProductId(pObj.getString("productId"));
                p.setProductName(pObj.getString("promoName"));
                p.setPromoName(pObj.getString("promoName"));
                p.setPromo(true);
                p.setTemporaryPromo(pObj.getBoolean("isTemp"));
                p.setPromoEndDate(pObj.getLong("endDateTimestamp"));
                p.setActive(true);
                p.setSellable(true);
                p.setQuantity(9999);
                promoPseudoProducts.add(p);

                String dType = pObj.optString("discountType", "Percentage");
                double dVal = pObj.optDouble("discountValue", 0.0);
                promoDiscountTypeMap.put(p.getProductId(), dType);
                promoDiscountValueMap.put(p.getProductId(), dVal);

                List<String> ids = new ArrayList<>();
                if (mappingObj.has(p.getProductId())) {
                    org.json.JSONArray idsArr = mappingObj.getJSONArray(p.getProductId());
                    for (int j = 0; j < idsArr.length(); j++) ids.add(idsArr.getString(j));
                }
                promoProductIdsMap.put(p.getProductId(), ids);
            }
        } catch (Exception e) { e.printStackTrace(); }

        // --- 2. LISTEN TO FIRESTORE AND UPDATE CACHE IN BACKGROUND ---
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(ownerId).collection("promos")
                .whereEqualTo("isActive", true)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    promoPseudoProducts.clear();
                    promoProductIdsMap.clear();

                    org.json.JSONArray savePromosArr = new org.json.JSONArray();
                    org.json.JSONObject saveMappingObj = new org.json.JSONObject();

                    if (snapshot != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                            String promoId = doc.getId();
                            String promoName = doc.getString("promoName");
                            String endDate = doc.getString("endDate");
                            Boolean isTemp = doc.getBoolean("isTemporary");

                            String discountType = doc.getString("discountType");
                            Double discountValue = doc.getDouble("discountValue");
                            if (discountValue == null) discountValue = 0.0;

                            long endDateTimestamp = 0;
                            if (endDate != null && !endDate.isEmpty()) {
                                String[] formats = {"yyyy-MM-dd", "MMM dd, yyyy", "MM/dd/yyyy"};
                                for (String format : formats) {
                                    try {
                                        SimpleDateFormat sdf = new SimpleDateFormat(format, Locale.US);
                                        Date date = sdf.parse(endDate);
                                        if (date != null) {
                                            endDateTimestamp = date.getTime();
                                            break; // Successfully parsed!
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }

                            List<String> productIds = new ArrayList<>();
                            Object appIdsObj = doc.get("applicableProductIds");
                            if (appIdsObj instanceof List) {
                                for (Object obj : (List<?>) appIdsObj) productIds.add(String.valueOf(obj));
                            }

                            Product p = new Product();
                            p.setProductId("PROMO_" + promoId);
                            p.setProductName(promoName);
                            p.setPromoName(promoName);
                            p.setPromo(true);
                            p.setTemporaryPromo(isTemp != null && isTemp);
                            p.setPromoEndDate(endDateTimestamp);
                            p.setActive(true);
                            p.setSellable(true);
                            p.setQuantity(9999);

                            promoPseudoProducts.add(p);
                            promoProductIdsMap.put(p.getProductId(), productIds);
                            promoDiscountTypeMap.put(p.getProductId(), discountType != null ? discountType : "Percentage");
                            promoDiscountValueMap.put(p.getProductId(), discountValue);

                            // Save securely to JSON for the offline cache
                            try {
                                org.json.JSONObject pObj = new org.json.JSONObject();
                                pObj.put("productId", p.getProductId());
                                pObj.put("promoName", promoName);
                                pObj.put("isTemp", p.isTemporaryPromo());
                                pObj.put("endDateTimestamp", p.getPromoEndDate());
                                pObj.put("discountType", discountType);
                                pObj.put("discountValue", discountValue);
                                savePromosArr.put(pObj);

                                org.json.JSONArray idsArr = new org.json.JSONArray();
                                for (String id : productIds) idsArr.put(id);
                                saveMappingObj.put(p.getProductId(), idsArr);
                            } catch (Exception ex) { }
                        }
                    }

                    prefs.edit()
                            .putString("promos_json", savePromosArr.toString())
                            .putString("promo_mapping", saveMappingObj.toString())
                            .apply();

                    if (switchShowPromos != null && switchShowPromos.isChecked()) applyCategoryFilter();
                });
    }

    private void showPromoProductsDialog(Product promo) {
        List<String> applicableIds = promoProductIdsMap.get(promo.getProductId());

        if (applicableIds == null || applicableIds.isEmpty()) {
            Toast.makeText(this, "No products assigned to this promo.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<Product> specificPromoProducts = new ArrayList<>();

        for (Product p : masterInventory) {
            String pId = p.getProductId() != null ? p.getProductId() : "";
            String lId = String.valueOf(p.getLocalId());

            if (applicableIds.contains(pId) || applicableIds.contains(lId)) {
                specificPromoProducts.add(p);
            }
        }

        if (specificPromoProducts.isEmpty()) {
            Toast.makeText(this, "Assigned products are currently unavailable.", Toast.LENGTH_SHORT).show();
            return;
        }

        View view = getLayoutInflater().inflate(R.layout.dialog_promo_products, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(view)
                .setCancelable(true)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = view.findViewById(R.id.tvPromoDialogTitle);
        tvTitle.setText(promo.getPromoName() + " Products");

        androidx.recyclerview.widget.RecyclerView rv = view.findViewById(R.id.rvPromoProducts);
        rv.setLayoutManager(new androidx.recyclerview.widget.GridLayoutManager(this, 3));

        String dType = promoDiscountTypeMap.get(promo.getProductId());
        Double dVal = promoDiscountValueMap.get(promo.getProductId());

        SellAdapter promoDialogAdapter = new SellAdapter(this, specificPromoProducts, masterInventory, (p, maxServ) -> {
            dialog.dismiss();

            if (p.getQuantity() <= 0 && maxServ <= 0 && !isFlexibleHandlingEnabled) {
                Toast.makeText(SellList.this, "Out of stock! Cannot add to cart.", Toast.LENGTH_SHORT).show();
            } else {
                showProductOptionsDialog(p, maxServ, promo.getPromoName(), dType, dVal != null ? dVal : 0.0);
            }
        }, null, isFlexibleHandlingEnabled);

        promoDialogAdapter.setAdminStatus(isManagerFlag);
        rv.setAdapter(promoDialogAdapter);
        com.google.android.material.button.MaterialButton btnClose = view.findViewById(R.id.btnPromoDialogClose);
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void applyRoleVisibility() {
        if (btnLockScreen != null) {
            btnLockScreen.setVisibility(isAdminFlag ? View.GONE : View.VISIBLE);
        }

        if (isManagerFlag) {
            if (fabAddSalesProduct != null) fabAddSalesProduct.setVisibility(View.VISIBLE);
            if (layoutArchiveContainer != null) layoutArchiveContainer.setVisibility(View.VISIBLE);
            if (btnRefund != null) btnRefund.setVisibility(View.VISIBLE); // FIXED: Show for Admins
            listenToArchivedProductsCount();
        } else {
            if (fabAddSalesProduct != null) fabAddSalesProduct.setVisibility(View.GONE);
            if (layoutBulkActions != null) layoutBulkActions.setVisibility(View.GONE);
            if (layoutArchiveContainer != null) layoutArchiveContainer.setVisibility(View.GONE);
            if (btnRefund != null) btnRefund.setVisibility(View.GONE); // FIXED: Hide for Staff
        }
    }

    private void listenToArchivedProductsCount() {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = AuthManager.getInstance().getCurrentUserId();
        }
        if (ownerId == null) return;

        archiveListener = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(ownerId).collection("products")
                .whereEqualTo("isActive", false)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null) return;
                    if (snapshot != null) {
                        int archiveCount = snapshot.size();
                        runOnUiThread(() -> {
                            boolean show = (archiveCount > 0 && isManagerFlag);
                            if (layoutArchiveContainer != null) {
                                layoutArchiveContainer.setVisibility(show ? View.VISIBLE : View.GONE);
                            }
                            if (cardArchiveBadge != null) {
                                cardArchiveBadge.setVisibility(archiveCount > 0 ? View.VISIBLE : View.GONE);
                            }
                            if (tvArchiveBadge != null) {
                                tvArchiveBadge.setText(String.valueOf(archiveCount));
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
                "Recently Added",
                "Available",
                "Unavailable"
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

                    boolean isActivelyPromoted = false;
                    if (isVisibleOnMenu && product.isPromo()) {
                        isActivelyPromoted = true;

                        if (product.isTemporaryPromo()) {
                            long endTimeMillis = product.getPromoEndDate();
                            boolean hasStarted = product.getPromoStartDate() == 0 || currentTime >= product.getPromoStartDate();
                            boolean hasExpired = endTimeMillis > 0 && currentTime > (endTimeMillis + 86400000);

                            if (!hasStarted || hasExpired) {
                                isActivelyPromoted = false; // Promo expired, return to normal menu
                                product.setPromo(false); // Dynamically revert in UI
                            }
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
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this).setView(view).create();
        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        Spinner spinnerTransaction = view.findViewById(R.id.spinnerTimeFilter); // Repurposed for Transaction IDs!
        EditText etOrderId = view.findViewById(R.id.etRefundOrderId);
        TextView tvDetails = view.findViewById(R.id.tvRefundOrderDetails);
        Spinner spinnerReason = view.findViewById(R.id.spinnerRefundReason);
        EditText etSpecificReason = view.findViewById(R.id.etRefundSpecificReason);
        Button btnCancel = view.findViewById(R.id.btnCancelRefund);
        Button btnConfirm = view.findViewById(R.id.btnConfirmRefund);

        boolean isDark = false;
        try { isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark"); } catch (Exception e) {}
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        // Hide the old search box and text details
        if (etOrderId != null) etOrderId.setVisibility(View.GONE);
        if (tvDetails != null) tvDetails.setVisibility(View.GONE);
        LinearLayout cbContainer = new LinearLayout(this);
        cbContainer.setOrientation(LinearLayout.VERTICAL);

        if (tvDetails != null && tvDetails.getParent() != null) {
            ViewGroup parentLayout = (ViewGroup) tvDetails.getParent();
            parentLayout.removeView(tvDetails);
            parentLayout.addView(cbContainer);
        }

        if (etSpecificReason != null) etSpecificReason.setTextColor(textColor);

        String[] reasons = { "Spoiled / Bad Quality", "Wrong Order Prepared", "Customer Changed Mind", "Spilled / Dropped", "Other (Specify below)" };
        if (spinnerReason != null) spinnerReason.setAdapter(getAdaptiveAdapter(reasons));

        // Get Sales Synchronously (Prevents infinite reload loops when dialog is open)
        List<Sales> currentSales = SalesInventoryApplication.getSalesRepository().getAllSales().getValue();
        if (currentSales == null) currentSales = new ArrayList<>();

        Map<String, List<Sales>> groupedSales = new java.util.LinkedHashMap<>();
        List<String> transactionDisplayList = new ArrayList<>();
        List<String> transactionIds = new ArrayList<>();

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.US);
        long now = System.currentTimeMillis();

        // Group sales by their Order ID (Transaction ID)
        for (Sales s : currentSales) {
            if (s.getPaymentMethod() != null && (s.getPaymentMethod().contains("REFUNDED") || "VOIDED".equals(s.getStatus()))) continue;
            if (now - s.getTimestamp() > 7 * 24 * 60 * 60 * 1000L) continue; // Only show last 7 days

            String oId = s.getOrderId() != null && !s.getOrderId().isEmpty() ? s.getOrderId() : "UNKNOWN";
            if (!groupedSales.containsKey(oId)) {
                groupedSales.put(oId, new ArrayList<>());
            }
            groupedSales.get(oId).add(s);
        }

        for (Map.Entry<String, List<Sales>> entry : groupedSales.entrySet()) {
            double total = 0;
            long time = 0;
            for(Sales s : entry.getValue()) {
                total += s.getTotalPrice();
                time = s.getTimestamp();
            }
            String shortId = entry.getKey().length() >= 8 ? entry.getKey().substring(0, 8).toUpperCase() : entry.getKey();
            String display = "ID: " + shortId + " | ₱" + String.format(Locale.US, "%.2f", total) + " | " + sdf.format(new Date(time));
            transactionDisplayList.add(display);
            transactionIds.add(entry.getKey());
        }

        if (transactionDisplayList.isEmpty()) {
            transactionDisplayList.add("No recent refundable transactions");
            if (btnConfirm != null) btnConfirm.setEnabled(false);
        } else {
            if (btnConfirm != null) btnConfirm.setEnabled(true);
        }

        if (spinnerTransaction != null) {
            spinnerTransaction.setAdapter(getAdaptiveAdapter(transactionDisplayList.toArray(new String[0])));

            List<android.widget.CheckBox> activeCheckBoxes = new ArrayList<>();
            List<Sales> activeCheckableSales = new ArrayList<>();

            // When a Transaction ID is selected, generate checkboxes for its items
            spinnerTransaction.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    cbContainer.removeAllViews();
                    activeCheckBoxes.clear();
                    activeCheckableSales.clear();

                    if (transactionIds.isEmpty()) return;

                    String selectedOrderId = transactionIds.get(pos);
                    List<Sales> itemsInOrder = groupedSales.get(selectedOrderId);

                    for(Sales s : itemsInOrder) {
                        android.widget.CheckBox cb = new android.widget.CheckBox(SellList.this);
                        cb.setText(s.getQuantity() + "x " + s.getProductName() + " - ₱" + String.format(Locale.US, "%.2f", s.getTotalPrice()));
                        cb.setTextColor(textColor);
                        cb.setChecked(true); // Default to selected
                        cbContainer.addView(cb);

                        activeCheckBoxes.add(cb);
                        activeCheckableSales.add(s);
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });

            if (btnConfirm != null) {
                btnConfirm.setOnClickListener(v -> {
                    List<Sales> itemsToRefund = new ArrayList<>();

                    // Collect only the items the user left checked!
                    for(int i = 0; i < activeCheckBoxes.size(); i++) {
                        if (activeCheckBoxes.get(i).isChecked()) {
                            itemsToRefund.add(activeCheckableSales.get(i));
                        }
                    }

                    if (itemsToRefund.isEmpty()) {
                        Toast.makeText(SellList.this, "Please check at least one item to refund.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String selectedReason = spinnerReason != null ? spinnerReason.getSelectedItem().toString() : "";
                    String specificNotes = etSpecificReason != null ? etSpecificReason.getText().toString().trim() : "";
                    String finalReason = selectedReason + (specificNotes.isEmpty() ? "" : " - " + specificNotes);

                    String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
                    if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();

                    // Process refunds only for the selected items
                    for (Sales sale : itemsToRefund) {
                        sale.setPaymentMethod("REFUNDED: " + finalReason);
                        SalesInventoryApplication.getSalesRepository().processRefund(sale, true, new SalesRepository.OnSaleVoidedListener() {
                            @Override public void onSuccess() {}
                            @Override public void onError(String error) {}
                        });

                        Map<String, Object> refundLog = new HashMap<>();
                        refundLog.put("orderId", sale.getOrderId());
                        refundLog.put("productName", sale.getProductName());
                        refundLog.put("amount", sale.getTotalPrice());
                        refundLog.put("reason", finalReason);
                        refundLog.put("timestamp", System.currentTimeMillis());
                        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(ownerId).collection("refund_logs").add(refundLog);
                    }

                    Toast.makeText(SellList.this, "Refund Processed: " + finalReason, Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                });
            }
        }

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());
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
        if (map == null) return 0.0;
        Object val = map.get(key);

        if (val == null) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (String.valueOf(entry.getKey()).equalsIgnoreCase(key)) {
                    val = entry.getValue();
                    break;
                }
            }
        }

        if (val == null && key.equalsIgnoreCase("price")) {
            String[] fallbacks = {"priceDiff", "additionalPrice", "amount", "value"};
            for (String fb : fallbacks) {
                if (map.containsKey(fb) && map.get(fb) != null) {
                    val = map.get(fb);
                    break;
                }
            }
        }

        if (val == null) return 0.0;

        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            String strVal = ((String) val).trim();
            strVal = strVal.replaceAll("[^\\d.]", "");
            if (strVal.isEmpty() || strVal.equals(".")) return 0.0;
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
                if (!isReadOnly) {
                    chip.setOnLongClickListener(v -> {
                        showCategoryOptionsDialog(main, false);
                        return true;
                    });
                }
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
                        if (!isReadOnly) {
                            chip.setOnLongClickListener(v -> {
                                showCategoryOptionsDialog(sub, true);
                                return true;
                            });
                        }
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

            // FIX: Implement Available / Unavailable filtering logic
            boolean matchesAvailability = true;
            if (currentSortOption.equals("Available")) {
                // It is available if there are NO missing ingredients
                matchesAvailability = (getMissingIngredientReason(p) == null);
            } else if (currentSortOption.equals("Unavailable")) {
                // It is unavailable if there ARE missing ingredients
                matchesAvailability = (getMissingIngredientReason(p) != null);
            }

            if (matchesMain && matchesSub && matchesSearch && matchesAvailability) {
                filteredProducts.add(p);
            }
        }

        if (switchShowPromos != null && switchShowPromos.isChecked()) {
            filteredProducts.clear();
            filteredProducts.addAll(promoPseudoProducts);
            for (Product p : allMenuProducts) {
                if (p.isPromo()) {
                    boolean exists = false;
                    for (Product pseudo : promoPseudoProducts) {
                        if (pseudo.getProductId().equals(p.getProductId())) {
                            exists = true; break;
                        }
                    }
                    if (!exists) filteredProducts.add(p);
                }
            }

            android.os.Parcelable rvState = null;
            if (sellListView != null && sellListView.getLayoutManager() != null) {
                rvState = sellListView.getLayoutManager().onSaveInstanceState();
            }

            if (sellAdapter != null) {
                sellAdapter.filterList(filteredProducts, masterInventory);
            }

            if (sellListView != null && sellListView.getLayoutManager() != null && rvState != null) {
                sellListView.getLayoutManager().onRestoreInstanceState(rvState);
            }            return;
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
                    long time1 = p1.getDateAdded();
                    long time2 = p2.getDateAdded();
                    return Long.compare(time2, time1);
                default:
                    // If Sort Option is "Available" or "Unavailable", just sort them A-Z
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

    private void showProductOptionsDialog(Product product, int maxBaseServings, String appliedPromoName, String discountType, double discountValue) {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_product_options, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this).setView(view).create();
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
        TextView tvQty = view.findViewById(R.id.tvOptionQty);
        ImageButton btnMinus = view.findViewById(R.id.btnOptionMinus);
        ImageButton btnPlus = view.findViewById(R.id.btnOptionPlus);
        Button btnCancel = view.findViewById(R.id.btnOptionCancel);
        Button btnAddToCart = view.findViewById(R.id.btnOptionAddToCart);

        final int[] currentQuantity = {1};

        if (btnMinus != null) {
            btnMinus.setOnClickListener(v -> {
                if (currentQuantity[0] > 1) {
                    currentQuantity[0]--;
                    tvQty.setText(String.valueOf(currentQuantity[0]));
                }
            });
        }

        if (btnPlus != null) {
            btnPlus.setOnClickListener(v -> {
                currentQuantity[0]++;
                tvQty.setText(String.valueOf(currentQuantity[0]));
            });
        }

        LinearLayout layoutSizesContainer = view.findViewById(R.id.layoutSizes);
        ViewGroup parentLayout = (ViewGroup) layoutSizesContainer.getParent();

        // 1. CRITICAL SCROLL FIX: Keeps buttons on screen!
        if (parentLayout != null) {
            ViewGroup grandParent = (ViewGroup) parentLayout.getParent();
            if (grandParent instanceof LinearLayout && !(grandParent instanceof android.widget.ScrollView)) {
                int index = grandParent.indexOfChild(parentLayout);
                grandParent.removeView(parentLayout);

                android.widget.ScrollView scrollView = new android.widget.ScrollView(this);
                // Weight 1 pushes the Confirm/Cancel buttons to the bottom securely
                LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 0, 1.0f);
                scrollView.setLayoutParams(scrollParams);

                parentLayout.setLayoutParams(new android.widget.FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                scrollView.addView(parentLayout);
                grandParent.addView(scrollView, index);
            }
        }

        RadioGroup rgSizes = view.findViewById(R.id.rgSizes);
        LinearLayout layoutAddonsContainer = view.findViewById(R.id.layoutAddons);
        LinearLayout containerAddonsList = view.findViewById(R.id.containerAddons);
        LinearLayout layoutSugarContainer = view.findViewById(R.id.layoutSugar);

        // --- OUT OF STOCK WARNING ---
        if (maxBaseServings <= 0) {
            String missingReason = getMissingIngredientReason(product);
            TextView tvWarning = new TextView(this);
            tvWarning.setText("⚠️ UNAVAILABLE: Out of " + (missingReason != null ? missingReason : "Stock"));
            tvWarning.setTextColor(Color.parseColor("#D32F2F")); // Red Warning Color
            tvWarning.setTypeface(null, android.graphics.Typeface.BOLD);
            tvWarning.setTextSize(15f);
            tvWarning.setPadding(0, 0, 0, 16);

            if (parentLayout != null) {
                parentLayout.addView(tvWarning, 0); // Inserts the text at the very top of the list!
            }
        }

        if (layoutSizesContainer != null) layoutSizesContainer.setVisibility(View.GONE);
        if (rgSizes != null) rgSizes.removeAllViews();
        if (layoutAddonsContainer != null) layoutAddonsContainer.setVisibility(View.GONE);
        if (containerAddonsList != null) containerAddonsList.removeAllViews();
        if (layoutSugarContainer != null) {
            layoutSugarContainer.setVisibility(View.GONE);
            layoutSugarContainer.removeAllViews();
        }

        String safeName = product.getProductName() != null ? product.getProductName() : "Unnamed Product";
        if (tvName != null) tvName.setText(safeName);

        double activeBasePrice = product.getActiveSellingPrice();

        if (appliedPromoName != null && discountValue > 0) {
            if ("Percentage".equalsIgnoreCase(discountType) || "Percent".equalsIgnoreCase(discountType)) {
                activeBasePrice = activeBasePrice - (activeBasePrice * (discountValue / 100.0));
            } else {
                activeBasePrice = activeBasePrice - discountValue;
            }
            if (activeBasePrice < 0) activeBasePrice = 0;
            activeBasePrice = Math.round(activeBasePrice);
        }

        if (tvPrice != null) tvPrice.setText(String.format(Locale.US, "₱%.2f", activeBasePrice));
        final double[] basePrice = {activeBasePrice};
        final double[] selectedSizePrice = {0.0};
        final double[] selectedAddonsPrice = {0.0};
        final String[] selectedSizeName = {""};

        dynamicNoteViews.clear();

        // 2. Render Sizes
        Object sizesObj = product.getSizesList();
        if (sizesObj instanceof List) {
            List<?> sList = (List<?>) sizesObj;
            if (!sList.isEmpty()) {
                if (layoutSizesContainer != null) layoutSizesContainer.setVisibility(View.VISIBLE);
                boolean isFirstAvailableSizeSelected = false;
                java.util.Set<String> addedSizes = new java.util.HashSet<>();

                for (int i = 0; i < sList.size(); i++) {
                    Object obj = sList.get(i);
                    if (obj instanceof Map) {
                        Map<?, ?> size = (Map<?, ?>) obj;
                        String sName = (String) size.get("name");

                        if (sName == null || addedSizes.contains(sName)) continue;
                        addedSizes.add(sName);

                        android.widget.RadioButton rb = new android.widget.RadioButton(this);
                        rb.setTextColor(dynamicTextColor);

                        String linkedMat = (String) size.get("linkedMaterial");

                        double sPrice = safeGetDouble(size, "price");
                        if (sPrice == 0 && size.containsKey("priceDiff")) {
                            double pct = safeGetDouble(size, "priceDiff");
                            sPrice = basePrice[0] * (pct / 100.0);
                        }

                        if (sPrice <= 0 && sName.contains("(+")) {
                            try {
                                String extracted = sName.substring(sName.indexOf("(+"));
                                extracted = extracted.replaceAll("[^\\d.]", "");
                                if (!extracted.isEmpty() && !extracted.equals(".")) {
                                    sPrice = Double.parseDouble(extracted);
                                }
                                sName = sName.substring(0, sName.indexOf("(+")).trim();
                            } catch (Exception e) {}
                        }

                        double deductQty = safeGetDouble(size, "deductQty");

                        String text = sName;
                        if (sPrice > 0) text += String.format(Locale.US, " (+₱%.2f)", sPrice);

                        if (linkedMat != null && !linkedMat.isEmpty() && deductQty > 0) {
                            Product sizeMat = findInventoryProduct(linkedMat);
                            double currentStock = sizeMat != null ? sizeMat.getQuantity() : 0;
                            if (currentStock < deductQty) {
                                text += " (Out of Stock)";
                                if (!isFlexibleHandlingEnabled) rb.setEnabled(false);
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

        // 3. Render Add-ons
        List<android.widget.CheckBox> addonBoxes = new ArrayList<>();
        Object addonsObj = product.getAddonsList();
        if (addonsObj instanceof List) {
            List<?> aList = (List<?>) addonsObj;
            if (!aList.isEmpty()) {
                if (layoutAddonsContainer != null) layoutAddonsContainer.setVisibility(View.VISIBLE);
                java.util.Set<String> addedAddons = new java.util.HashSet<>();

                for (Object obj : aList) {
                    if (obj instanceof Map) {
                        Map<?, ?> addon = (Map<?, ?>) obj;
                        String aName = (String) addon.get("name");

                        if (aName == null || addedAddons.contains(aName)) continue;
                        addedAddons.add(aName);

                        android.widget.CheckBox cb = new android.widget.CheckBox(this);
                        cb.setTextColor(dynamicTextColor);

                        String linkedMat = (String) addon.get("linkedMaterial");
                        double aPrice = safeGetDouble(addon, "price");

                        if (aPrice <= 0 && aName.contains("(+")) {
                            try {
                                String extracted = aName.substring(aName.indexOf("(+"));
                                extracted = extracted.replaceAll("[^\\d.]", "");
                                if (!extracted.isEmpty() && !extracted.equals(".")) {
                                    aPrice = Double.parseDouble(extracted);
                                }
                                aName = aName.substring(0, aName.indexOf("(+")).trim();
                            } catch (Exception e) {}
                        }

                        double deductQty = safeGetDouble(addon, "deductQty");

                        String text = aName;
                        if (aPrice > 0) text += String.format(Locale.US, " (+₱%.2f)", aPrice);

                        if (linkedMat != null && !linkedMat.isEmpty() && deductQty > 0) {
                            Product addonMat = findInventoryProduct(linkedMat);
                            double currentStock = addonMat != null ? addonMat.getQuantity() : 0;
                            if (currentStock < deductQty) {
                                text += " (Out of Stock)";
                                if (!isFlexibleHandlingEnabled) cb.setEnabled(false);
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

        // 4. RESTORED: Sugar rendered as Dropdown Spinners
        final String[] selectedNoteText = {""};
        Object notesObj = product.getNotesList();
        if (notesObj instanceof List) {
            List<?> nList = (List<?>) notesObj;
            if (!nList.isEmpty()) {

                boolean hasSugar = false;
                String sugarNoteType = "Sugar Level";
                for (Object obj : nList) {
                    if (obj instanceof Map) {
                        String type = String.valueOf(((Map<?, ?>) obj).get("type"));
                        if (type.toLowerCase().contains("sugar") || type.toLowerCase().contains("sweetness") || type.toLowerCase().contains("sugar_enabled")) {
                            hasSugar = true;
                            if(!type.equals("sugar_enabled")) sugarNoteType = type;
                            break;
                        }
                    }
                }

                if (hasSugar && layoutSugarContainer != null) {
                    layoutSugarContainer.setVisibility(View.VISIBLE);

                    TextView lblTitle = new TextView(this);
                    lblTitle.setText(sugarNoteType);
                    lblTitle.setTextColor(dynamicTextColor);
                    lblTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                    lblTitle.setPadding(0, 16, 0, 8);
                    layoutSugarContainer.addView(lblTitle);

                    Spinner sugarSpinner = new Spinner(this);
                    java.util.List<String> sugarNames = new java.util.ArrayList<>();
                    for (Product p : masterInventory) {
                        if (p.getProductName() != null) {
                            String n = p.getProductName().toLowerCase();
                            if (n.contains("sugar") || n.contains("sweetener")) {
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
                    layoutSugarContainer.addView(lblSugarSource);
                    layoutSugarContainer.addView(sugarSpinner);

                    Spinner levelSpinner = new Spinner(this);
                    java.util.List<String> levels = java.util.Arrays.asList(
                            "100% (Full Sugar)", "75% (Less Sugar)", "50% (Half Sugar)",
                            "25% (Quarter Sugar)", "0% (No Sugar)"
                    );
                    ArrayAdapter<String> levelAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, levels);
                    levelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    levelSpinner.setAdapter(levelAdapter);

                    TextView lblSugarLevel = new TextView(this);
                    lblSugarLevel.setText("Sugar Level:");
                    lblSugarLevel.setTextColor(dynamicTextColor);
                    lblSugarLevel.setTextSize(12);
                    layoutSugarContainer.addView(lblSugarLevel);
                    layoutSugarContainer.addView(levelSpinner);

                    dynamicNoteViews.put(sugarNoteType + "_sugarType", sugarSpinner);
                    dynamicNoteViews.put(sugarNoteType + "_level", levelSpinner);
                }
            }
        }

        // 5. Render Missing Ingredients (Uncheck to Bypass)
        List<android.widget.CheckBox> ingredientBoxes = new ArrayList<>();
        Object bomObj = product.getBomList();
        if (bomObj instanceof List) {
            List<?> bList = (List<?>) bomObj;
            if (!bList.isEmpty()) {
                boolean hasMissingIngredients = false;
                List<View> missingIngredientViews = new ArrayList<>();

                TextView tvIngredientsTitle = new TextView(this);
                tvIngredientsTitle.setText("Missing Ingredients (Uncheck to bypass)");
                tvIngredientsTitle.setTextColor(dynamicTextColor);
                tvIngredientsTitle.setTypeface(null, android.graphics.Typeface.BOLD);
                tvIngredientsTitle.setPadding(0, 24, 0, 8);
                missingIngredientViews.add(tvIngredientsTitle);

                java.util.Set<String> addedIngredients = new java.util.HashSet<>();

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

                            String invUnit = inventoryItem.getUnit() != null ? inventoryItem.getUnit() : "pcs";
                            String reqUnit = (String) bomItem.get("unit");
                            int ppu = inventoryItem.getPiecesPerUnit() > 0 ? inventoryItem.getPiecesPerUnit() : 1;

                            double deductedBaseAmount = UnitConverterUtil.calculateDeductionAmount(requiredQty, invUnit, reqUnit, ppu);

                            if (inventoryItem.getQuantity() < deductedBaseAmount) {
                                hasMissingIngredients = true;
                                addedIngredients.add(uniqueKey);

                                android.widget.CheckBox cb = new android.widget.CheckBox(this);
                                cb.setText(rawName + " (OUT OF STOCK)");
                                cb.setTextColor(Color.RED);
                                cb.setChecked(true);
                                cb.setTag(uniqueKey);

                                if (!isFlexibleHandlingEnabled) cb.setEnabled(false);

                                ingredientBoxes.add(cb);
                                missingIngredientViews.add(cb);
                            }
                        } else {
                            hasMissingIngredients = true;
                            addedIngredients.add(uniqueKey);

                            android.widget.CheckBox cb = new android.widget.CheckBox(this);
                            cb.setText(rawName + " (NOT FOUND IN INVENTORY)");
                            cb.setTextColor(Color.RED);
                            cb.setChecked(true);
                            cb.setTag(uniqueKey);

                            if (!isFlexibleHandlingEnabled) cb.setEnabled(false);

                            ingredientBoxes.add(cb);
                            missingIngredientViews.add(cb);
                        }
                    }
                }

                if (hasMissingIngredients && parentLayout != null) {
                    for (View v : missingIngredientViews) {
                        parentLayout.addView(v);
                    }
                }
            }
        }

        if (btnCancel != null) btnCancel.setOnClickListener(v -> dialog.dismiss());
        if (btnAddToCart != null) btnAddToCart.setOnClickListener(v -> {
            int qty = currentQuantity[0];
            if (qty <= 0) return;

            StringBuilder excludedBuilder = new StringBuilder();
            StringBuilder excludedNamesBuilder = new StringBuilder();

            for (android.widget.CheckBox cb : ingredientBoxes) {
                if (!cb.isChecked()) {
                    excludedBuilder.append(cb.getTag() != null ? cb.getTag().toString() : "").append(",");
                    String ingName = cb.getText().toString().replaceAll(" \\(OUT OF STOCK.*\\)", "").replaceAll(" \\(NOT FOUND.*\\)", "");
                    excludedNamesBuilder.append("No ").append(ingName).append(", ");
                }
            }
            String excludedIngredients = excludedBuilder.toString();

            if (qty > maxBaseServings && excludedIngredients.isEmpty() && !isFlexibleHandlingEnabled) {
                Object checkBom = product.getBomList();
                if (checkBom instanceof List && !((List<?>) checkBom).isEmpty()) {
                    Toast.makeText(this, "Not enough raw ingredients! Uncheck missing ingredients to override.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Out of stock! Cannot add to cart.", Toast.LENGTH_LONG).show();
                }
                return;
            }

            double finalPrice = basePrice[0] + selectedSizePrice[0] + selectedAddonsPrice[0];

            StringBuilder extrasBuilder = new StringBuilder();
            for (android.widget.CheckBox cb : addonBoxes) {
                if (cb.isChecked())
                    extrasBuilder.append(cb.getText().toString().replaceAll(" \\(\\+₱.*\\)", "")).append(", ");
            }

            // Restore the Spinner data reader logic
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

            if (appliedPromoName != null && !appliedPromoName.isEmpty()) {
                if (extrasBuilder.length() > 0 && !extrasBuilder.toString().endsWith(" ")) extrasBuilder.append(" ");
                extrasBuilder.append("[").append(appliedPromoName).append(" Applied]");
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

        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();
        if (ownerId == null) return;

        // FIXED: Use WriteBatch to bundle all cloud updates into a single guaranteed upload
        com.google.firebase.firestore.WriteBatch batch = com.google.firebase.firestore.FirebaseFirestore.getInstance().batch();
        List<Product> productsToUpdateLocally = new java.util.ArrayList<>();
        int count = 0;

        for (Product target : targets) {
            boolean changed = false;
            java.util.Map<String, Object> updates = new java.util.HashMap<>();

            if (copyPricing) {
                target.setSellingPrice(source.getSellingPrice());
                target.setCostPrice(source.getCostPrice());

                updates.put("sellingPrice", source.getSellingPrice());
                updates.put("costPrice", source.getCostPrice());
                changed = true;
            }

            if (copyBom) {
                List<java.util.Map<String, Object>> newBom = source.getBomList() != null ? new java.util.ArrayList<>(source.getBomList()) : new java.util.ArrayList<>();
                target.setBomList(newBom);

                updates.put("bomList", newBom);
                changed = true;
            }

            if (copyVariants) {
                List<java.util.Map<String, Object>> newSizes = source.getSizesList() != null ? new java.util.ArrayList<>(source.getSizesList()) : new java.util.ArrayList<>();
                List<java.util.Map<String, Object>> newAddons = source.getAddonsList() != null ? new java.util.ArrayList<>(source.getAddonsList()) : new java.util.ArrayList<>();
                List<java.util.Map<String, String>> newNotes = source.getNotesList() != null ? new java.util.ArrayList<>(source.getNotesList()) : new java.util.ArrayList<>();

                target.setSizesList(newSizes);
                target.setAddonsList(newAddons);
                target.setNotesList(newNotes);

                updates.put("sizesList", newSizes);
                updates.put("addonsList", newAddons);
                updates.put("notesList", newNotes);
                changed = true;
            }

            if (changed) {
                // Trigger a timestamp update so other devices know to download the changes
                updates.put("lastUpdated", System.currentTimeMillis());
                productsToUpdateLocally.add(target);

                if (target.getProductId() != null && !target.getProductId().isEmpty() && !target.getProductId().startsWith("local:")) {
                    com.google.firebase.firestore.DocumentReference ref = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("users").document(ownerId)
                            .collection("products").document(target.getProductId());

                    batch.set(ref, updates, com.google.firebase.firestore.SetOptions.merge());
                }
                count++;
            }
        }

        if (count > 0) {
            final int finalCount = count;

            // 1. Instantly save all updates to the Local Database for Offline mode!
            SalesInventoryApplication.getProductRepository().upsertFromRemoteBulk(productsToUpdateLocally);

            // 2. Safely push the entire batch to Firebase in ONE guaranteed network call!
            batch.commit().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Successfully copied to " + finalCount + " product(s)!", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Failed to sync to cloud: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        } else {
            Toast.makeText(this, "No changes were made.", Toast.LENGTH_SHORT).show();
        }

        if (sellAdapter != null) sellAdapter.clearSelection();
        hideBulkActionBar();
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

    private void updateOnlineStatus(boolean isOnline) {
        String currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (currentUid != null) {
            // Updates Firestore (Green/Red Dot)
            FirebaseFirestore.getInstance().collection("users")
                    .document(currentUid).update("isOnline", isOnline);
            // Updates Realtime DB
            FirebaseDatabase.getInstance().getReference("UsersStatus")
                    .child(currentUid).setValue(isOnline ? "online" : "offline");
        }
    }

    @Override
    protected void onDestroy() {
        try {
            SalesInventoryApplication.logAttendance("BREAK_START");
            updateOnlineStatus(false);
        } catch (Exception ignored) {}

        super.onDestroy();
        if (archiveListener != null) archiveListener.remove();
    }

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

    private String getMissingIngredientReason(Product menuProduct) {
        List<Map<String, Object>> bomList = menuProduct.getBomList();
        if (bomList == null || bomList.isEmpty()) {
            if (menuProduct.getQuantity() > 0) return null;
            return menuProduct.getProductName();
        }
        for (Map<String, Object> bomItem : bomList) {
            boolean isEssential = true;
            if (bomItem.containsKey("isEssential")) {
                Object essObj = bomItem.get("isEssential");
                if (essObj instanceof Boolean) isEssential = (Boolean) essObj;
                else if (essObj instanceof String) isEssential = Boolean.parseBoolean((String) essObj);
            }
            if (!isEssential) continue;

            String matName = (String) bomItem.get("materialName");
            if (matName == null) matName = (String) bomItem.get("rawMaterialName");

            double requiredQty = 0;
            try { requiredQty = Double.parseDouble(String.valueOf(bomItem.get("quantity"))); } catch (Exception ignored) {}
            if (requiredQty == 0) {
                try { requiredQty = Double.parseDouble(String.valueOf(bomItem.get("quantityRequired"))); } catch (Exception ignored) {}
            }

            String reqUnit = (String) bomItem.get("unit");

            Product inventoryItem = findInventoryProduct(matName);
            if (inventoryItem == null) return matName;

            double availableQty = inventoryItem.getQuantity();
            if (availableQty <= 0) return matName;

            String invUnit = inventoryItem.getUnit() != null ? inventoryItem.getUnit() : "pcs";
            int ppu = inventoryItem.getPiecesPerUnit() > 0 ? inventoryItem.getPiecesPerUnit() : 1;

            double deductedBaseAmount = UnitConverterUtil.calculateDeductionAmount(requiredQty, invUnit, reqUnit, ppu);
            if (deductedBaseAmount <= 0) continue;

            if (availableQty < deductedBaseAmount) return matName;
        }
        return null;
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