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

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

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

    // To map product IDs to actual Product names/prices
    private Map<String, Product> productLookupMap = new HashMap<>();

    private DatabaseReference promosRef;
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

        promosRef = FirebaseDatabase.getInstance().getReference("Promos").child(currentUserId);

        initViews();
        setupRecyclerView();
        loadAllProductsForLookup(); // Load menu items first, then load promos
    }

    private void initViews() {
        rvManagePromos = findViewById(R.id.rvManagePromos);
        tvNoPromos = findViewById(R.id.tvNoPromos);
        etSearchPromos = findViewById(R.id.etSearchPromos);
        fabAddPromo = findViewById(R.id.fabAddPromo);
        btnFilterPromos = findViewById(R.id.btnFilterPromos);

        fabAddPromo.setOnClickListener(v -> {
            startActivity(new Intent(ManagePromosActivity.this, CreatePromoActivity.class));
        });

        etSearchPromos.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable s) {
                filterPromos(s.toString());
            }
        });

        btnFilterPromos.setOnClickListener(v -> {
            Toast.makeText(this, "Filter options coming soon", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupRecyclerView() {
        adapter = new ManagePromosAdapter();
        rvManagePromos.setLayoutManager(new LinearLayoutManager(this));
        rvManagePromos.setAdapter(adapter);
    }

    // 1. Fetch menu products so we can show names and prices inside the promo cards
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
            loadPromosFromDatabase(); // Now fetch the promos
        });
    }

    // 2. Fetch the Promos from Firebase
    private void loadPromosFromDatabase() {
        promosRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allPromos.clear();
                for (DataSnapshot data : snapshot.getChildren()) {
                    PromoModel promo = data.getValue(PromoModel.class);
                    if (promo != null) {
                        allPromos.add(promo);
                    }
                }

                // Sort by newest first
                Collections.sort(allPromos, (p1, p2) -> Long.compare(p2.createdAt, p1.createdAt));

                filterPromos(etSearchPromos.getText().toString());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ManagePromosActivity.this, "Failed to load promos", Toast.LENGTH_SHORT).show();
            }
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

    // ==========================================
    // RECYCLERVIEW ADAPTER FOR PROMOS
    // ==========================================
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

            // Format Discount Text
            String discountStr = "Discount: ";
            if ("Percentage (%)".equals(promo.discountType)) {
                discountStr += promo.discountValue + "% Off";
            } else if ("Fixed Amount (₱)".equals(promo.discountType)) {
                discountStr += "₱" + promo.discountValue + " Off";
            } else {
                discountStr += "Set to ₱" + promo.discountValue;
            }
            holder.tvPromoDiscount.setText(discountStr);

            // Format Validity Text
            if (promo.isTemporary) {
                holder.tvPromoValidity.setText("Valid: " + promo.startDate + " to " + promo.endDate);
            } else {
                holder.tvPromoValidity.setText("Permanent Series (No Expiration)");
            }

            // Temporarily detach listener to avoid unwanted database triggers during scroll recycling
            holder.switchShowInMenu.setOnCheckedChangeListener(null);

            holder.switchShowInMenu.setChecked(promo.isActive);
            holder.switchShowInMenu.setText(promo.isActive ? "Active" : "Hidden");

            // Handle Hide/Show Toggle
            holder.switchShowInMenu.setOnCheckedChangeListener((buttonView, isChecked) -> {
                holder.switchShowInMenu.setText(isChecked ? "Active" : "Hidden");
                promosRef.child(promo.promoId).child("isActive").setValue(isChecked)
                        .addOnSuccessListener(aVoid -> Toast.makeText(ManagePromosActivity.this, isChecked ? "Promo activated" : "Promo hidden from menu", Toast.LENGTH_SHORT).show());
            });

            // Dynamically add the products inside the layout container
            holder.containerPromoProducts.removeAllViews(); // Clear old views first

            if (promo.applicableProductIds != null && !promo.applicableProductIds.isEmpty()) {
                for (String productId : promo.applicableProductIds) {

                    // Look up the actual product details using the map we built earlier
                    Product product = productLookupMap.get(productId);

                    if (product != null) {
                        View rowView = LayoutInflater.from(ManagePromosActivity.this).inflate(R.layout.item_promo_product_row, holder.containerPromoProducts, false);
                        TextView tvRowName = rowView.findViewById(R.id.tvRowProductName);
                        TextView tvRowPrice = rowView.findViewById(R.id.tvRowProductPrice);

                        tvRowName.setText("• " + product.getProductName());

                        // Calculate price if it's an override or discount
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
                // If no products found
                TextView emptyText = new TextView(ManagePromosActivity.this);
                emptyText.setText("No active products linked.");
                emptyText.setAlpha(0.6f);
                holder.containerPromoProducts.addView(emptyText);
            }

            holder.btnPromoOptions.setOnClickListener(v -> {
                // Future Implementation: Edit Promo Details
                Toast.makeText(ManagePromosActivity.this, "Edit Promo feature coming soon!", Toast.LENGTH_SHORT).show();
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
                btnPromoOptions = findViewById(R.id.btnPromoOptions);
                containerPromoProducts = itemView.findViewById(R.id.containerPromoProducts);
            }
        }
    }

    // ==========================================
    // DATA MODEL FOR PROMOS
    // ==========================================
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

        public PromoModel() {
            // Empty constructor required for Firebase DataSnapshot mapping
        }
    }
}