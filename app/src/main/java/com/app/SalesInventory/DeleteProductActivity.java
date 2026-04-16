package com.app.SalesInventory;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class DeleteProductActivity extends BaseActivity {
    private ProductRepository productRepository;
    private View rootView;
    private RecyclerView recyclerView;
    private ArchiveAdapter adapter;
    private Button btnRefresh;
    private SearchView searchView;

    private List<Product> allArchivedProducts = new ArrayList<>();
    private List<Product> filteredArchivedProducts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_product);

        // FIX 1: Safely grab the root view so Snackbar never crashes
        rootView = findViewById(android.R.id.content);
        recyclerView = findViewById(R.id.productsRecyclerView);

        // These might be null depending on the XML, which is fine
        btnRefresh = findViewById(R.id.btn_refresh_archives);
        searchView = findViewById(R.id.searchView);

        AuthManager authManager = AuthManager.getInstance();
        if (!authManager.isCurrentUserAdmin()) {
            Toast.makeText(this, "Access denied", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        productRepository = SalesInventoryApplication.getProductRepository();

        if (recyclerView != null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(this));
            adapter = new ArchiveAdapter(new ArrayList<>());
            recyclerView.setAdapter(adapter);
        }

        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> loadArchivedProducts());
        }

        if (searchView != null) {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    filterArchives(query);
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    filterArchives(newText);
                    return true;
                }
            });
        }

        loadArchivedProducts();
    }

    private void loadArchivedProducts() {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = AuthManager.getInstance().getCurrentUserId();
        }
        if (ownerId == null) {
            Toast.makeText(this, "Authentication error", Toast.LENGTH_SHORT).show();
            return;
        }

        FirebaseFirestore.getInstance().collection("users").document(ownerId).collection("products")
                .whereEqualTo("isActive", false)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allArchivedProducts.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots) {
                        try {
                            // FIX: Manually map only the fields we need.
                            // This completely bypasses the Date/Long crash from Firebase!
                            Product p = new Product();
                            p.setProductId(doc.getId());

                            String name = doc.getString("productName");
                            p.setProductName(name != null ? name : "Unknown Product");

                            String cat = doc.getString("categoryName");
                            p.setCategoryName(cat != null ? cat : "Uncategorized");

                            allArchivedProducts.add(p);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    filterArchives(searchView != null && searchView.getQuery() != null ? searchView.getQuery().toString() : "");
                })
                .addOnFailureListener(e -> Toast.makeText(DeleteProductActivity.this, "Failed to load archives", Toast.LENGTH_SHORT).show());
    }

    private void performRestore(Product p) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = AuthManager.getInstance().getCurrentUserId();
        }
        if (ownerId == null) return;

        // 1. Restore the product in the Cloud
        FirebaseFirestore.getInstance().collection("users").document(ownerId)
                .collection("products").document(p.getProductId())
                .update("isActive", true)
                .addOnSuccessListener(aVoid -> {

                    // 2. FIXED: Immediately restore the product in the Local Database too!
                    new Thread(() -> {
                        try {
                            AppDatabase db = AppDatabase.getInstance(DeleteProductActivity.this);
                            ProductEntity entity = db.productDao().getByProductIdSync(p.getProductId());
                            if (entity != null) {
                                entity.isActive = true;
                                db.productDao().update(entity);
                            }

                            runOnUiThread(() -> {
                                if (rootView != null) Snackbar.make(rootView, "Restored: " + p.getProductName(), Snackbar.LENGTH_SHORT).show();
                                loadArchivedProducts(); // Refresh the archive list
                            });
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }).start();

                })
                .addOnFailureListener(e -> {
                    runOnUiThread(() -> Toast.makeText(DeleteProductActivity.this, "Error restoring", Toast.LENGTH_SHORT).show());
                });
    }

    private void confirmPermanentDelete(Product p) {
        new AlertDialog.Builder(this)
                .setTitle("Permanent Delete")
                .setMessage("Are you sure you want to completely erase " + p.getProductName() + "? This cannot be undone.")
                .setPositiveButton("Erase", (d, w) -> {
                    String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
                    if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();
                    if (ownerId == null) return;

                    // 1. FIXED: Physically delete the document from the Cloud (No more soft deletes)
                    FirebaseFirestore.getInstance().collection("users").document(ownerId)
                            .collection("products").document(p.getProductId())
                            .delete()
                            .addOnSuccessListener(aVoid -> {

                                // 2. FIXED: Wipe it from the Local Database
                                new Thread(() -> {
                                    try {
                                        AppDatabase db = AppDatabase.getInstance(DeleteProductActivity.this);
                                        ProductEntity entity = db.productDao().getByProductIdSync(p.getProductId());
                                        if (entity != null) {
                                            db.productDao().deleteByLocalId(entity.localId);
                                        }
                                        runOnUiThread(() -> {
                                            if (rootView != null) Snackbar.make(rootView, "Permanently Deleted", Snackbar.LENGTH_SHORT).show();
                                            loadArchivedProducts(); // Refresh the archive list
                                        });
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                }).start();

                            })
                            .addOnFailureListener(e -> Toast.makeText(DeleteProductActivity.this, "Delete failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void filterArchives(String query) {
        filteredArchivedProducts.clear();
        if (query == null || query.trim().isEmpty()) {
            filteredArchivedProducts.addAll(allArchivedProducts);
        } else {
            String lowerQ = query.toLowerCase();
            for (Product p : allArchivedProducts) {
                if (p.getProductName() != null && p.getProductName().toLowerCase().contains(lowerQ)) {
                    filteredArchivedProducts.add(p);
                }
            }
        }
        if (adapter != null) adapter.updateItems(filteredArchivedProducts);
    }

    class ArchiveAdapter extends RecyclerView.Adapter<ArchiveAdapter.VH> {
        private List<Product> items;

        ArchiveAdapter(List<Product> items) {
            this.items = items;
        }

        public void updateItems(List<Product> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout container = new LinearLayout(parent.getContext());
            container.setOrientation(LinearLayout.HORIZONTAL);
            container.setPadding(16, 16, 16, 16);
            container.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            TextView tvName = new TextView(parent.getContext());
            tvName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            tvName.setTextSize(16f);
            container.addView(tvName);

            Button btnRestore = new Button(parent.getContext());
            btnRestore.setText("Restore");
            btnRestore.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            btnRestore.setTextColor(Color.WHITE);
            container.addView(btnRestore);

            Button btnDelete = new Button(parent.getContext());
            btnDelete.setText("Delete");
            btnDelete.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#F44336")));
            btnDelete.setTextColor(Color.WHITE);
            container.addView(btnDelete);

            return new VH(container, tvName, btnRestore, btnDelete);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            Product p = items.get(position);
            String cat = p.getCategoryName() != null ? p.getCategoryName() : "Uncategorized";
            holder.tvName.setText(p.getProductName() + "\n(" + cat + ")");

            boolean isDark = false;
            try {
                isDark = ThemeManager.getInstance(DeleteProductActivity.this).getCurrentTheme().name.equals("dark");
            } catch (Exception e) {
            }
            holder.tvName.setTextColor(isDark ? Color.WHITE : Color.BLACK);

            holder.btnRestore.setOnClickListener(v -> performRestore(p));
            holder.btnDelete.setOnClickListener(v -> confirmPermanentDelete(p));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName;
            Button btnRestore, btnDelete;

            VH(View v, TextView tv, Button br, Button bd) {
                super(v);
                tvName = tv;
                btnRestore = br;
                btnDelete = bd;
            }
        }
    }
}