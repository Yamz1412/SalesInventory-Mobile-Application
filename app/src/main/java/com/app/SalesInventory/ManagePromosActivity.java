package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.PopupMenu;
import android.view.Menu;
import androidx.appcompat.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ManagePromosActivity extends BaseActivity {

    private RecyclerView rvManagePromos;
    private TextView tvNoPromos;
    private EditText etSearchPromos;
    private FloatingActionButton fabAddPromo;
    private ImageButton btnFilterPromos;

    private ManagePromosAdapter adapter;
    private List<PromoModel> allPromos = new ArrayList<>();
    private List<PromoModel> filteredPromos = new ArrayList<>();

    private Map<String, Product> productLookupMap = new HashMap<>();

    private CollectionReference promosRef;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_promos);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentUserId = AuthManager.getInstance().getCurrentUserId();
        if (currentUserId == null) currentUserId = FirestoreManager.getInstance().getBusinessOwnerId();

        promosRef = FirebaseFirestore.getInstance().collection("users").document(currentUserId).collection("promos");

        initViews();
        setupRecyclerView();
        loadAllProductsForLookup();
    }

    private void initViews() {
        rvManagePromos = findViewById(R.id.rvManagePromos);
        tvNoPromos = findViewById(R.id.tvNoPromos);
        etSearchPromos = findViewById(R.id.etSearchPromos);
        fabAddPromo = findViewById(R.id.fabAddPromo);

        // SAFEGUARD: Only setup the filter button if you actually add it to your XML later
        btnFilterPromos = findViewById(R.id.btnFilterPromos);
        if (btnFilterPromos != null) {
            btnFilterPromos.setOnClickListener(v -> {
                Toast.makeText(this, "Filter options coming soon", Toast.LENGTH_SHORT).show();
            });
        }

        // SAFEGUARD: Prevent crashes if these IDs are missing or named differently in XML
        if (fabAddPromo != null) {
            fabAddPromo.setOnClickListener(v -> {
                startActivity(new Intent(ManagePromosActivity.this, CreatePromoActivity.class));
            });
        }

        if (etSearchPromos != null) {
            etSearchPromos.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    filterPromos(s.toString());
                }
            });
        }
    }

    private void setupRecyclerView() {
        if (rvManagePromos != null) {
            adapter = new ManagePromosAdapter();
            rvManagePromos.setLayoutManager(new LinearLayoutManager(this));
            rvManagePromos.setAdapter(adapter);
        }
    }

    private void loadAllProductsForLookup() {
        SalesInventoryApplication.getProductRepository().getAllProducts().observe(this, products -> {
            productLookupMap.clear();
            if (products != null) {
                for (Product p : products) {
                    if (p.getProductId() != null) {
                        productLookupMap.put(p.getProductId(), p);
                    }
                }
            }
            loadPromosFromDatabase();
        });
    }

    private void loadPromosFromDatabase() {
        promosRef.addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                Toast.makeText(ManagePromosActivity.this, "Failed to load promos", Toast.LENGTH_SHORT).show();
                return;
            }
            allPromos.clear();
            if (snapshot != null) {
                for (DocumentSnapshot doc : snapshot.getDocuments()) {
                    PromoModel promo = doc.toObject(PromoModel.class);
                    if (promo != null) {
                        allPromos.add(promo);
                    }
                }
            }
            Collections.sort(allPromos, (p1, p2) -> Long.compare(p2.createdAt, p1.createdAt));
            filterPromos(etSearchPromos.getText().toString());
        });
    }

    private void filterPromos(String query) {
        filteredPromos.clear();
        String lowerQuery = query.toLowerCase(Locale.getDefault());

        for (PromoModel promo : allPromos) {
            if (promo.promoName != null && promo.promoName.toLowerCase(Locale.getDefault()).contains(lowerQuery)) {
                filteredPromos.add(promo);
            }
        }

        adapter.notifyDataSetChanged();

        if (filteredPromos.isEmpty()) {
            rvManagePromos.setVisibility(View.GONE);
            tvNoPromos.setVisibility(View.VISIBLE);
        } else {
            rvManagePromos.setVisibility(View.VISIBLE);
            tvNoPromos.setVisibility(View.GONE);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class ManagePromosAdapter extends RecyclerView.Adapter<ManagePromosAdapter.PromoViewHolder> {

        @NonNull
        @Override
        public PromoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_manage_promo, parent, false);
            return new PromoViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PromoViewHolder holder, int position) {
            PromoModel promo = filteredPromos.get(position);

            holder.tvPromoName.setText(promo.promoName != null ? promo.promoName : "Unnamed Promo");

            String discountStr = "Discount: ";
            if ("Percentage (%)".equals(promo.discountType)) {
                discountStr += promo.discountValue + "% Off";
            } else if ("Fixed Amount (₱)".equals(promo.discountType)) {
                discountStr += "₱" + promo.discountValue + " Off";
            } else {
                discountStr += "Set to ₱" + promo.discountValue;
            }
            holder.tvPromoDiscount.setText(discountStr);

            if (promo.isTemporary) {
                holder.tvPromoValidity.setText("Valid: " + promo.startDate + " to " + promo.endDate);
            } else {
                holder.tvPromoValidity.setText("Permanent Series (No Expiration)");
            }

            holder.switchShowInMenu.setOnCheckedChangeListener(null);
            holder.switchShowInMenu.setChecked(promo.isActive);
            holder.switchShowInMenu.setText(promo.isActive ? "Active" : "Hidden");

            holder.switchShowInMenu.setOnCheckedChangeListener((buttonView, isChecked) -> {
                holder.switchShowInMenu.setText(isChecked ? "Active" : "Hidden");

                WriteBatch batch = FirebaseFirestore.getInstance().batch();

                batch.update(promosRef.document(promo.promoId), "isActive", isChecked);

                if (promo.applicableProductIds != null) {
                    for (String productId : promo.applicableProductIds) {
                        batch.update(FirebaseFirestore.getInstance()
                                .collection("users").document(currentUserId)
                                .collection("products").document(productId), "isPromo", isChecked);
                    }
                }

                batch.commit().addOnFailureListener(e -> {
                    Toast.makeText(ManagePromosActivity.this, "Failed to update: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    holder.switchShowInMenu.setChecked(!isChecked);
                });
            });

            holder.containerPromoProducts.removeAllViews();

            if (promo.applicableProductIds != null && !promo.applicableProductIds.isEmpty()) {
                for (String productId : promo.applicableProductIds) {
                    Product product = productLookupMap.get(productId);
                    if (product != null) {
                        View rowView = LayoutInflater.from(ManagePromosActivity.this).inflate(R.layout.item_promo_product_row, holder.containerPromoProducts, false);
                        TextView tvRowName = rowView.findViewById(R.id.tvRowProductName);
                        TextView tvRowPrice = rowView.findViewById(R.id.tvRowProductPrice);

                        tvRowName.setText("• " + product.getProductName());

                        double finalPrice = product.getSellingPrice();
                        if ("Percentage (%)".equals(promo.discountType)) {
                            finalPrice = finalPrice - (finalPrice * (promo.discountValue / 100.0));
                        } else if ("Fixed Amount (₱)".equals(promo.discountType)) {
                            finalPrice = Math.max(0, finalPrice - promo.discountValue);
                        } else if ("Override Price (₱)".equals(promo.discountType)) {
                            finalPrice = promo.discountValue;
                        }

                        tvRowPrice.setText(String.format(Locale.US, "₱%.2f", finalPrice));
                        holder.containerPromoProducts.addView(rowView);
                    }
                }
            } else {
                TextView emptyText = new TextView(ManagePromosActivity.this);
                emptyText.setText("No active products linked.");
                emptyText.setAlpha(0.6f);
                holder.containerPromoProducts.addView(emptyText);
            }

            holder.btnPromoOptions.setOnClickListener(v -> {
                PopupMenu popup = new PopupMenu(ManagePromosActivity.this, holder.btnPromoOptions);
                popup.getMenu().add(Menu.NONE, 1, 1, "Edit");
                popup.getMenu().add(Menu.NONE, 2, 2, "Delete");

                popup.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == 1) {
                        Toast.makeText(ManagePromosActivity.this, "Edit Promo feature coming soon!", Toast.LENGTH_SHORT).show();
                        return true;
                    } else if (item.getItemId() == 2) {
                        showDeleteConfirmation(promo);
                        return true;
                    }
                    return false;
                });
                popup.show();
            });
        }

        @Override
        public int getItemCount() {
            return filteredPromos.size();
        }

        class PromoViewHolder extends RecyclerView.ViewHolder {
            TextView tvPromoName, tvPromoDiscount, tvPromoValidity;
            SwitchMaterial switchShowInMenu;
            ImageButton btnPromoOptions;
            LinearLayout containerPromoProducts;

            PromoViewHolder(@NonNull View itemView) {
                super(itemView);
                tvPromoName = itemView.findViewById(R.id.tvPromoName);
                tvPromoDiscount = itemView.findViewById(R.id.tvPromoDiscount);
                tvPromoValidity = itemView.findViewById(R.id.tvPromoValidity);
                switchShowInMenu = itemView.findViewById(R.id.switchShowInMenu);
                btnPromoOptions = itemView.findViewById(R.id.btnPromoOptions);
                containerPromoProducts = itemView.findViewById(R.id.containerPromoProducts);
            }
        }
    }

    private void showDeleteConfirmation(PromoModel promo) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Promo")
                .setMessage("Are you sure you want to permanently delete '" + promo.promoName + "'?\n\nAll linked products will return to their normal prices and the promo will be removed from the Sell List.")
                .setPositiveButton("Delete", (dialog, which) -> deletePromo(promo))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deletePromo(PromoModel promo) {
        WriteBatch batch = FirebaseFirestore.getInstance().batch();

        // 1. Delete the actual promo document from Firestore
        batch.delete(promosRef.document(promo.promoId));

        // 2. Revert all associated products back to their normal state
        if (promo.applicableProductIds != null) {
            for (String productId : promo.applicableProductIds) {
                DocumentReference productRef = FirebaseFirestore.getInstance()
                        .collection("users").document(currentUserId)
                        .collection("products").document(productId);

                Map<String, Object> productUpdates = new HashMap<>();
                productUpdates.put("isPromo", false);
                productUpdates.put("promoName", "");
                productUpdates.put("isTemporaryPromo", false);
                productUpdates.put("promoPrice", 0.0);
                productUpdates.put("promoStartDate", 0);
                productUpdates.put("promoEndDate", 0);

                batch.update(productRef, productUpdates);
            }
        }

        // 3. Execute the batch
        batch.commit().addOnSuccessListener(aVoid -> {
            Toast.makeText(ManagePromosActivity.this, "Promo deleted successfully", Toast.LENGTH_SHORT).show();
            // The list will automatically refresh because of the SnapshotListener
        }).addOnFailureListener(e -> {
            Toast.makeText(ManagePromosActivity.this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        });
    }

    public static class PromoModel {
        public String promoId;
        public String promoName;
        public String discountType;
        public double discountValue;
        public boolean isTemporary;
        public String startDate;
        public String endDate;
        public boolean isActive;
        public List<String> applicableProductIds;
        public long createdAt;

        public PromoModel() {}
    }
}