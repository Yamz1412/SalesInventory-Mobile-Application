package com.app.SalesInventory;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.SearchView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.snackbar.Snackbar;
import java.util.ArrayList;
import java.util.List;

public class DeleteProductActivity extends AppCompatActivity {
    private ProductRepository productRepository;
    private View rootView;
    private RecyclerView recyclerView;
    private ArchivedProductAdapter adapter;
    private Button btnRefresh;
    private SearchView searchView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_product);
        rootView = findViewById(R.id.root_layout);
        recyclerView = findViewById(R.id.productsRecyclerView);
        btnRefresh = findViewById(R.id.btn_refresh_archives);
        searchView = findViewById(R.id.searchView);
        productRepository = ProductRepository.getInstance(getApplication());
        adapter = new ArchivedProductAdapter(this, new ArchivedProductAdapter.Listener() {
            @Override
            public void onRestore(String filename) {
                performRestore(filename);
            }
            @Override
            public void onPermanentDelete(String filename) {
                confirmPermanentDelete(filename);
            }
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
        btnRefresh.setOnClickListener(v -> loadArchives());
        findViewById(R.id.clearSearchBtn).setOnClickListener(v -> {
            searchView.setQuery("", false);
            loadArchives();
        });
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { filter(query); return true; }
            @Override public boolean onQueryTextChange(String newText) { filter(newText); return true; }
        });
        loadArchives();
    }
    private void loadArchives() {
        List<String> files = productRepository.listLocalArchives();
        adapter.setFiles(files);
        findViewById(R.id.emptyStateTV).setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
    }
    private void filter(String q) {
        if (q == null || q.trim().isEmpty()) {
            loadArchives();
            return;
        }
        String ql = q.toLowerCase();
        List<String> filtered = new ArrayList<>();
        for (String f : productRepository.listLocalArchives()) {
            if (f.toLowerCase().contains(ql) || f.toLowerCase().contains(ql.replaceAll("\\s+","_"))) filtered.add(f);
        }
        adapter.setFiles(filtered);
        findViewById(R.id.emptyStateTV).setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }
    private void performRestore(String filename) {
        productRepository.restoreArchived(filename, new ProductRepository.OnProductRestoreListener() {
            @Override
            public void onProductRestored() {
                runOnUiThread(() -> {
                    Snackbar.make(rootView, "Product restored", Snackbar.LENGTH_SHORT).show();
                    loadArchives();
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(DeleteProductActivity.this, "Restore failed: " + error, Toast.LENGTH_LONG).show());
            }
        });
    }
    private void confirmPermanentDelete(String filename) {
        new AlertDialog.Builder(this)
                .setTitle("Permanently delete")
                .setMessage("This will permanently remove the archived copy and any local record. This cannot be undone. Continue?")
                .setPositiveButton("Delete", (d, w) -> performPermanentDelete(filename))
                .setNegativeButton("Cancel", null)
                .show();
    }
    private void performPermanentDelete(String filename) {
        productRepository.permanentlyDeleteArchive(filename, new ProductRepository.OnPermanentDeleteListener() {
            @Override
            public void onPermanentDeleted() {
                runOnUiThread(() -> {
                    Snackbar.make(rootView, "Archive permanently removed", Snackbar.LENGTH_SHORT).show();
                    loadArchives();
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(DeleteProductActivity.this, "Delete failed: " + error, Toast.LENGTH_LONG).show());
            }
        });
    }
}