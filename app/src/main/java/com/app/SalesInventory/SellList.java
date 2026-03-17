package com.app.SalesInventory;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SellList extends BaseActivity {
    private RecyclerView sellListView;
    private SellAdapter sellAdapter;
    private List<Product> allMenuProducts = new ArrayList<>();
    private List<Product> filteredProducts = new ArrayList<>();
    private ProductRepository productRepository;
    private CartManager cartManager;

    private Button btnCheckout;
    private EditText etSearchProduct;
    private LinearLayout layoutCategoryChips;
    private String currentCategoryFilter = "All";

    // BULK ACTION VIEWS
    private View layoutBulkActions;
    private TextView tvSelectedCount;
    private ImageButton btnBulkOptions, btnBulkDelete, btnBulkClose, btnBulkEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sell_list);

        productRepository = SalesInventoryApplication.getProductRepository();
        cartManager = CartManager.getInstance();

        sellListView = findViewById(R.id.SellListD);
        btnCheckout = findViewById(R.id.btnCheckout);
        etSearchProduct = findViewById(R.id.etSearchProduct);
        layoutCategoryChips = findViewById(R.id.layoutCategoryChips);

        // Bind Bulk Action views from XML
        layoutBulkActions = findViewById(R.id.layoutBulkActions);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        btnBulkOptions = findViewById(R.id.btnBulkOptions);
        btnBulkDelete = findViewById(R.id.btnBulkDelete);
        btnBulkClose = findViewById(R.id.btnBulkClose);
        btnBulkEdit = findViewById(R.id.btnBulkEdit); // <-- NEW

        setupBulkActionListeners();

        sellListView.setLayoutManager(new GridLayoutManager(this, 2));

        // Initial Adapter attachment
        sellAdapter = new SellAdapter(
                this,
                filteredProducts,
                product -> showProductOptionsDialog(product),
                selectedIds -> {
                    if (selectedIds.isEmpty()) {
                        if (layoutBulkActions != null) layoutBulkActions.setVisibility(View.GONE);
                    } else {
                        if (layoutBulkActions != null) {
                            layoutBulkActions.setVisibility(View.VISIBLE);
                            tvSelectedCount.setText(selectedIds.size() + " Selected");
                        }
                    }
                }
        );
        sellListView.setAdapter(sellAdapter);

        loadMenuProducts();

        com.google.android.material.floatingactionbutton.FloatingActionButton fabAddSalesProduct = findViewById(R.id.fabAddSalesProduct);

        // Hide Admin functions from Cashiers
        if (!AuthManager.getInstance().isCurrentUserAdmin()) {
            if (fabAddSalesProduct != null) fabAddSalesProduct.setVisibility(View.GONE);
            if (btnBulkDelete != null) btnBulkDelete.setVisibility(View.GONE);
            if (btnBulkOptions != null) btnBulkOptions.setVisibility(View.GONE);
            if (btnBulkEdit != null) btnBulkEdit.setVisibility(View.GONE); // <-- NEW
        }

        if (fabAddSalesProduct != null) {
            fabAddSalesProduct.setOnClickListener(v -> {
                Intent intent = new Intent(SellList.this, AddProductActivity.class);
                intent.putExtra("FORCE_SALE_ONLY", true);
                startActivity(intent);
            });
        }

        btnCheckout.setOnClickListener(v -> {
            if (cartManager.getItems().isEmpty()) {
                Toast.makeText(this, "Cart is empty", Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(SellList.this, sellProduct.class));
            }
        });

        etSearchProduct.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupBulkActionListeners() {
        if (btnBulkClose != null) {
            btnBulkClose.setOnClickListener(v -> {
                if (sellAdapter != null) sellAdapter.clearSelection();
            });
        }

        if (btnBulkDelete != null) {
            btnBulkDelete.setOnClickListener(v -> {
                if (sellAdapter != null) performBulkDelete(sellAdapter.getSelectedIds());
            });
        }

        if (btnBulkOptions != null) {
            btnBulkOptions.setOnClickListener(v -> {
                if (sellAdapter != null) performBulkCopyOptions(sellAdapter.getSelectedIds());
            });
        }

        // --- NEW EDIT LOGIC ---
        if (btnBulkEdit != null) {
            btnBulkEdit.setOnClickListener(v -> {
                if (sellAdapter != null) {
                    Set<String> selectedIds = sellAdapter.getSelectedIds();

                    if (selectedIds.size() == 1) {
                        // Extract the single selected ID
                        String idToEdit = selectedIds.iterator().next();

                        // Launch Edit Activity
                        Intent intent = new Intent(SellList.this, EditSalesProductActivity.class);
                        intent.putExtra("PRODUCT_ID", idToEdit);
                        startActivity(intent);

                        // Optional: Clear selection so it's clean when user comes back
                        sellAdapter.clearSelection();

                    } else if (selectedIds.size() > 1) {
                        Toast.makeText(SellList.this, "Please select only 1 item to edit", Toast.LENGTH_SHORT).show();
                    }
                }
            });
        }
    }

    private void performBulkDelete(Set<String> selectedIds) {
        if (!AuthManager.getInstance().isCurrentUserAdmin()) return;

        new AlertDialog.Builder(this)
                .setTitle("Delete Products")
                .setMessage("Are you sure you want to delete " + selectedIds.size() + " selected products?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    for (String id : selectedIds) {
                        productRepository.deleteProduct(id, new ProductRepository.OnProductDeletedListener() {
                            @Override public void onProductDeleted(String msg) {}
                            @Override public void onError(String error) {}
                        });
                    }
                    Toast.makeText(SellList.this, "Products deleted", Toast.LENGTH_SHORT).show();
                    if (sellAdapter != null) sellAdapter.clearSelection();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performBulkCopyOptions(Set<String> selectedIds) {
        if (!AuthManager.getInstance().isCurrentUserAdmin()) return;

        List<Product> templates = new ArrayList<>(allMenuProducts);
        String[] templateNames = new String[templates.size()];
        for (int i = 0; i < templates.size(); i++) {
            templateNames[i] = templates.get(i).getProductName();
        }

        new AlertDialog.Builder(this)
                .setTitle("Copy Configurations From...")
                .setItems(templateNames, (dialog, which) -> {
                    Product sourceProduct = templates.get(which);
                    applyConfigurationsToSelected(sourceProduct, selectedIds);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void applyConfigurationsToSelected(Product sourceProduct, Set<String> selectedIds) {
        int updatedCount = 0;
        for (String id : selectedIds) {
            String sourceId = sourceProduct.getProductId() != null ? sourceProduct.getProductId() : "local:" + sourceProduct.getLocalId();
            if (id.equals(sourceId)) continue;

            Product targetProduct = null;
            for (Product p : allMenuProducts) {
                String targetId = p.getProductId() != null ? p.getProductId() : "local:" + p.getLocalId();
                if (targetId.equals(id)) {
                    targetProduct = p;
                    break;
                }
            }

            if (targetProduct != null) {
                targetProduct.setSizesList(sourceProduct.getSizesList());
                targetProduct.setAddonsList(sourceProduct.getAddonsList());
                targetProduct.setNotesList(sourceProduct.getNotesList());

                productRepository.updateProduct(targetProduct, new ProductRepository.OnProductUpdatedListener() {
                    @Override public void onProductUpdated() {}
                    @Override public void onError(String error) {}
                });
                updatedCount++;
            }
        }
        Toast.makeText(this, "Copied configurations to " + updatedCount + " products!", Toast.LENGTH_SHORT).show();
        if (sellAdapter != null) sellAdapter.clearSelection();
    }

    private void loadMenuProducts() {
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null && !products.isEmpty()) {
                populateMenuList(products);
            } else {
                // Room is empty — pull directly from Firestore
                fetchMenuProductsFromFirestore();
            }
        });
    }

    // ADD this new method:
    private void fetchMenuProductsFromFirestore() {
        String ownerId = AuthManager.getInstance().getCurrentUserId();
        if (ownerId == null) return;

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(ownerId).collection("products")
                .whereEqualTo("isActive", true)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Product> products = new ArrayList<>();
                    ProductRepository repo = SalesInventoryApplication.getProductRepository();
                    for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                        Product p = doc.toObject(Product.class);
                        if (p != null) {
                            p.setProductId(doc.getId());
                            p.setActive(true);
                            products.add(p);
                            repo.upsertFromRemote(p); // saves to Room for next time
                        }
                    }
                    runOnUiThread(() -> populateMenuList(products));
                });
    }

    // ADD this helper (extracted from loadMenuProducts):
    private void populateMenuList(List<Product> products) {
        allMenuProducts.clear();
        List<String> categories = new ArrayList<>();
        categories.add("All");

        for (Product p : products) {
            if (p == null || !p.isActive()) continue;
            boolean isMenuType = "Menu".equalsIgnoreCase(p.getProductType())
                    || "Both".equalsIgnoreCase(p.getProductType());
            if (isMenuType) {
                allMenuProducts.add(p);
                String cat = p.getCategoryName();
                if (cat != null && !cat.trim().isEmpty() && !categories.contains(cat)) {
                    categories.add(cat);
                }
            }
        }
        setupCategoryChips(categories);
        applyFilters();
    }

    private void setupCategoryChips(List<String> categories) {
        layoutCategoryChips.removeAllViews();
        for (String cat : categories) {
            MaterialButton chip = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
            chip.setText(cat);
            chip.setCheckable(true);
            chip.setChecked(cat.equals(currentCategoryFilter));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            params.setMargins(0, 0, 16, 0);
            chip.setLayoutParams(params);

            chip.setOnClickListener(v -> {
                for (int i = 0; i < layoutCategoryChips.getChildCount(); i++) {
                    ((MaterialButton) layoutCategoryChips.getChildAt(i)).setChecked(false);
                }
                chip.setChecked(true);
                currentCategoryFilter = cat;
                applyFilters();
            });
            layoutCategoryChips.addView(chip);
        }
    }

    private void applyFilters() {
        String query = etSearchProduct.getText().toString().toLowerCase().trim();
        filteredProducts.clear();

        for (Product p : allMenuProducts) {
            String pCat = p.getCategoryName() != null ? p.getCategoryName() : "";
            boolean matchesCategory = currentCategoryFilter.equals("All") || currentCategoryFilter.equals(pCat);
            String pName = p.getProductName() != null ? p.getProductName().toLowerCase() : "";
            boolean matchesSearch = pName.contains(query);

            if (matchesCategory && matchesSearch) {
                filteredProducts.add(p);
            }
        }

        if (sellAdapter != null) {
            sellAdapter.updateData(filteredProducts);
        }
    }

    private void showProductOptionsDialog(Product product) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_product_options, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        TextView tvName = dialogView.findViewById(R.id.tvOptionProductName);
        TextView tvPrice = dialogView.findViewById(R.id.tvOptionProductPrice);

        LinearLayout layoutSizesContainer = dialogView.findViewById(R.id.layoutSizesContainer);
        RadioGroup rgSizes = dialogView.findViewById(R.id.rgSizes);
        LinearLayout layoutAddonsContainer = dialogView.findViewById(R.id.layoutAddonsContainer);
        LinearLayout addonsList = dialogView.findViewById(R.id.addonsList);
        LinearLayout layoutNotesContainer = dialogView.findViewById(R.id.layoutNotesContainer);
        RadioGroup rgNotes = dialogView.findViewById(R.id.rgNotes);

        TextInputEditText etQty = dialogView.findViewById(R.id.etOptionQty);
        Button btnCancel = dialogView.findViewById(R.id.btnOptionCancel);
        Button btnAddToCart = dialogView.findViewById(R.id.btnOptionAddToCart);

        tvName.setText(product.getProductName() != null ? product.getProductName() : "Product");

        final double[] basePrice = {product.getSellingPrice()};
        final double[] selectedSizePrice = {0.0};
        final double[] selectedAddonsPrice = {0.0};
        final String[] selectedSizeName = {""};

        Runnable updatePrice = () -> {
            double total = basePrice[0] + selectedSizePrice[0] + selectedAddonsPrice[0];
            tvPrice.setText(String.format(Locale.getDefault(), "₱%.2f", total));
        };

        // 1. SIZES
        if (product.getSizesList() != null && !product.getSizesList().isEmpty()) {
            layoutSizesContainer.setVisibility(View.VISIBLE);
            rgSizes.removeAllViews();
            boolean first = true;
            for (Map<String, Object> size : product.getSizesList()) {
                String name = (String) size.get("name");
                double price = size.get("price") instanceof Number ? ((Number) size.get("price")).doubleValue() : 0.0;
                RadioButton rb = new RadioButton(this);
                rb.setText(name + " (+₱" + String.format(Locale.getDefault(), "%.2f", price) + ")");
                rb.setTag(price);
                rb.setId(View.generateViewId());
                if (first) {
                    rb.setChecked(true);
                    selectedSizePrice[0] = price;
                    selectedSizeName[0] = name;
                    first = false;
                }
                rb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if (isChecked) {
                        selectedSizePrice[0] = (double) buttonView.getTag();
                        selectedSizeName[0] = name;
                        updatePrice.run();
                    }
                });
                rgSizes.addView(rb);
            }
        }

        // 2. ADD-ONS
        List<android.widget.CheckBox> addonBoxes = new ArrayList<>();
        if (product.getAddonsList() != null && !product.getAddonsList().isEmpty()) {
            layoutAddonsContainer.setVisibility(View.VISIBLE);
            addonsList.removeAllViews();
            for (Map<String, Object> addon : product.getAddonsList()) {
                String name = (String) addon.get("name");
                double price = addon.get("price") instanceof Number ? ((Number) addon.get("price")).doubleValue() : 0.0;
                CheckBox cb = new CheckBox(this);
                cb.setText(name + " (+₱" + String.format(Locale.getDefault(), "%.2f", price) + ")");
                cb.setTag(price);
                cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
                    double p = (double) buttonView.getTag();
                    if (isChecked) selectedAddonsPrice[0] += p;
                    else selectedAddonsPrice[0] -= p;
                    updatePrice.run();
                });
                addonsList.addView(cb);
                addonBoxes.add(cb);
            }
        }

        // 3. NOTES
        final String[] selectedNoteText = {""};
        if (product.getNotesList() != null && !product.getNotesList().isEmpty()) {
            layoutNotesContainer.setVisibility(View.VISIBLE);
            rgNotes.removeAllViews();
            RadioButton rbNone = new RadioButton(this);
            rbNone.setText("None");
            rbNone.setId(View.generateViewId());
            rbNone.setChecked(true);
            rgNotes.addView(rbNone);
            for (Map<String, String> note : product.getNotesList()) {
                String type = note.get("type");
                String value = note.get("value");
                RadioButton rb = new RadioButton(this);
                rb.setText((type != null && !type.isEmpty()) ? type + " - " + value : value);
                rb.setId(View.generateViewId());
                rgNotes.addView(rb);
            }
            rgNotes.setOnCheckedChangeListener((group, checkedId) -> {
                RadioButton checkedRb = group.findViewById(checkedId);
                if (checkedRb != null && !checkedRb.getText().toString().equals("None")) {
                    selectedNoteText[0] = checkedRb.getText().toString();
                } else {
                    selectedNoteText[0] = "";
                }
            });
        }

        updatePrice.run();
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnAddToCart.setOnClickListener(v -> {
            String qtyStr = etQty.getText().toString().trim();
            int qty = qtyStr.isEmpty() ? 1 : Integer.parseInt(qtyStr);
            if (qty <= 0) return;

            double finalPrice = basePrice[0] + selectedSizePrice[0] + selectedAddonsPrice[0];
            StringBuilder extrasBuilder = new StringBuilder();
            for (CheckBox cb : addonBoxes) {
                if (cb.isChecked()) extrasBuilder.append(cb.getText()).append(", ");
            }
            if (!selectedNoteText[0].isEmpty()) extrasBuilder.append(selectedNoteText[0]).append(", ");

            String extraDetails = extrasBuilder.toString();
            if (extraDetails.endsWith(", ")) extraDetails = extraDetails.substring(0, extraDetails.length() - 2);

            String safeId = product.getProductId() != null ? product.getProductId() : "local:" + product.getLocalId();
            boolean added = cartManager.addItem(safeId, product.getProductName(), finalPrice, qty, 9999, selectedSizeName[0], extraDetails);

            if (added) {
                Toast.makeText(this, "Added to cart", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            } else {
                Toast.makeText(this, "Not enough stock!", Toast.LENGTH_SHORT).show();
            }
        });

        dialog.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sellAdapter != null) applyFilters();
    }
}