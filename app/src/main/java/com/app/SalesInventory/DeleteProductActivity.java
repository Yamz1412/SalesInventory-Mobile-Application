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

    // NEW: Cloud Archive List
    private List<Product> allArchivedProducts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_product);

        rootView = findViewById(R.id.root_layout);
        recyclerView = findViewById(R.id.productsRecyclerView);
        btnRefresh = findViewById(R.id.btn_refresh_archives);
        searchView = findViewById(R.id.searchView);

        productRepository = SalesInventoryApplication.getProductRepository();

        // NEW: Replaced old file adapter with our custom Cloud Adapter
        adapter = new ArchiveAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        btnRefresh.setOnClickListener(v -> loadArchivesFromFirestore());

        View clearBtn = findViewById(R.id.clearSearchBtn);
        if (clearBtn != null) {
            clearBtn.setOnClickListener(v -> {
                searchView.setQuery("", false);
                loadArchivesFromFirestore();
            });
        }

        setupSearchView();

        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { filter(query); return true; }
            @Override public boolean onQueryTextChange(String newText) { filter(newText); return true; }
        });

        loadArchivesFromFirestore();
    }

    private void setupSearchView() {
        int id = searchView.getContext().getResources().getIdentifier("android:id/search_src_text", null, null);
        TextView searchEditText = searchView.findViewById(id);
        if (searchEditText != null) {
            boolean isDark = false;
            try { isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark"); } catch(Exception e){}
            searchEditText.setTextColor(isDark ? Color.WHITE : Color.BLACK);
            searchEditText.setHintTextColor(Color.GRAY);
        }
    }

    // =========================================================================
    // NEW FEATURE: FETCH SOFT-DELETED PRODUCTS FROM FIREBASE INSTEAD OF FILES
    // =========================================================================
    private void loadArchivesFromFirestore() {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();
        if (ownerId == null) return;

        FirebaseFirestore.getInstance().collection("users").document(ownerId)
                .collection("products")
                .whereEqualTo("isActive", false) // Looks for soft-deleted items!
                .get()
                .addOnSuccessListener(snapshot -> {
                    allArchivedProducts.clear();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Product p = doc.toObject(Product.class);
                        if (p != null) {
                            p.setProductId(doc.getId());
                            allArchivedProducts.add(p);
                        }
                    }
                    filter(searchView.getQuery() != null ? searchView.getQuery().toString() : "");
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load archives", Toast.LENGTH_SHORT).show());
    }

    private void filter(String q) {
        List<Product> filtered = new ArrayList<>();
        if (q == null || q.trim().isEmpty()) {
            filtered.addAll(allArchivedProducts);
        } else {
            String ql = q.toLowerCase();
            for (Product p : allArchivedProducts) {
                if (p.getProductName() != null && p.getProductName().toLowerCase().contains(ql)) {
                    filtered.add(p);
                }
            }
        }
        adapter.setProducts(filtered);
        View emptyState = findViewById(R.id.emptyStateTV);
        if (emptyState != null) {
            emptyState.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    // --- RESTORE LOGIC ---
    private void performRestore(Product p) {
        p.setActive(true); // Bring it back to life!
        productRepository.updateProduct(p, null, new ProductRepository.OnProductUpdatedListener() {
            @Override
            public void onProductUpdated() {
                runOnUiThread(() -> {
                    if (rootView != null) Snackbar.make(rootView, "Product restored successfully!", Snackbar.LENGTH_SHORT).show();
                    else Toast.makeText(DeleteProductActivity.this, "Product restored successfully!", Toast.LENGTH_SHORT).show();
                    loadArchivesFromFirestore();
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(DeleteProductActivity.this, "Restore failed: " + error, Toast.LENGTH_LONG).show());
            }
        });
    }

    // --- PERMANENT DELETE LOGIC ---
    private void confirmPermanentDelete(Product p) {
        new AlertDialog.Builder(this)
                .setTitle("Permanently Delete")
                .setMessage("This will permanently delete '" + p.getProductName() + "'. This cannot be undone. Continue?")
                .setPositiveButton("Delete", (d, w) -> performPermanentDelete(p))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performPermanentDelete(Product p) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();

        FirebaseFirestore.getInstance().collection("users").document(ownerId)
                .collection("products").document(p.getProductId())
                .delete()
                .addOnSuccessListener(aVoid -> {
                    if (rootView != null) Snackbar.make(rootView, "Product permanently deleted", Snackbar.LENGTH_SHORT).show();
                    loadArchivesFromFirestore();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Delete failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }

    // =========================================================================
    // DYNAMIC UI ADAPTER (Guarantees no layout crashes for the buttons!)
    // =========================================================================
    class ArchiveAdapter extends RecyclerView.Adapter<ArchiveAdapter.VH> {
        private List<Product> items = new ArrayList<>();

        public void setProducts(List<Product> products) {
            this.items.clear();
            if (products != null) this.items.addAll(products);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout container = new LinearLayout(parent.getContext());
            container.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            container.setOrientation(LinearLayout.HORIZONTAL);
            int p = (int) (16 * parent.getContext().getResources().getDisplayMetrics().density);
            container.setPadding(p, p, p, p);
            container.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView tvName = new TextView(parent.getContext());
            tvName.setTextSize(16f);
            tvName.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams tvParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            container.addView(tvName, tvParams);

            Button btnRestore = new Button(parent.getContext());
            btnRestore.setText("Restore");
            btnRestore.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#4CAF50")));
            btnRestore.setTextColor(Color.WHITE);
            LinearLayout.LayoutParams brParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            brParams.setMargins(0, 0, 16, 0);
            container.addView(btnRestore, brParams);

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
            try { isDark = ThemeManager.getInstance(DeleteProductActivity.this).getCurrentTheme().name.equals("dark"); } catch(Exception e){}
            holder.tvName.setTextColor(isDark ? Color.WHITE : Color.BLACK);

            holder.btnRestore.setOnClickListener(v -> performRestore(p));
            holder.btnDelete.setOnClickListener(v -> confirmPermanentDelete(p));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvName;
            Button btnRestore, btnDelete;
            VH(View v, TextView tv, Button br, Button bd) {
                super(v);
                tvName = tv; btnRestore = br; btnDelete = bd;
            }
        }
    }
}