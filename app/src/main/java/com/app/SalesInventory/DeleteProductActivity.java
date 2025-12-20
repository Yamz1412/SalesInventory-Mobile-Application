package com.app.SalesInventory;

import android.app.Application;
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
    private PurchaseOrderRepository poRepository;
    private View rootView;
    private RecyclerView recyclerView;
    private ArchivedProductAdapter adapter;
    private Button btnRefresh;
    private SearchView searchView;
    private String archiveType = "product";
    private Button clearSearchBtn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delete_product);
        rootView = findViewById(R.id.root_layout);
        recyclerView = findViewById(R.id.productsRecyclerView);
        btnRefresh = findViewById(R.id.btn_refresh_archives);
        searchView = findViewById(R.id.searchView);
        clearSearchBtn = findViewById(R.id.clearSearchBtn);
        productRepository = ProductRepository.getInstance(getApplication());
        poRepository = PurchaseOrderRepository.getInstance();
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
        clearSearchBtn.setOnClickListener(v -> confirmClearAllArchives());
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { filter(query); return true; }
            @Override public boolean onQueryTextChange(String newText) { filter(newText); return true; }
        });
        if (getIntent() != null && getIntent().hasExtra("archiveType")) {
            archiveType = getIntent().getStringExtra("archiveType");
            if (archiveType == null) archiveType = "product";
        }
        loadArchives();
    }

    private void loadArchives() {
        if ("po".equalsIgnoreCase(archiveType)) {
            poRepository.listLocalPOArchives(this, files -> {
                runOnUiThread(() -> {
                    adapter.setFiles(files);
                    findViewById(R.id.emptyStateTV).setVisibility(files.isEmpty() ? View.VISIBLE : View.GONE);
                });
            });
            return;
        }
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
        if ("po".equalsIgnoreCase(archiveType)) {
            poRepository.listLocalPOArchives(this, files -> {
                List<String> filtered = new ArrayList<>();
                for (String f : files) {
                    if (f.toLowerCase().contains(ql) || f.toLowerCase().contains(ql.replaceAll("\\s+","_"))) filtered.add(f);
                }
                runOnUiThread(() -> {
                    adapter.setFiles(filtered);
                    findViewById(R.id.emptyStateTV).setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
                });
            });
            return;
        }
        List<String> filtered = new ArrayList<>();
        for (String f : productRepository.listLocalArchives()) {
            if (f.toLowerCase().contains(ql) || f.toLowerCase().contains(ql.replaceAll("\\s+","_"))) filtered.add(f);
        }
        adapter.setFiles(filtered);
        findViewById(R.id.emptyStateTV).setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void performRestore(String filename) {
        if ("po".equalsIgnoreCase(archiveType)) {
            poRepository.restorePOArchive(this, filename, new PurchaseOrderRepository.OnRestorePOListener() {
                @Override public void onRestored() {
                    runOnUiThread(() -> {
                        Snackbar.make(rootView, "PO restored", Snackbar.LENGTH_SHORT).show();
                        loadArchives();
                    });
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(DeleteProductActivity.this, "Restore failed: " + error, Toast.LENGTH_LONG).show());
                }
            });
            return;
        }
        productRepository.restoreArchived(filename, new ProductRepository.OnProductRestoreListener() {
            @Override public void onProductRestored() {
                runOnUiThread(() -> {
                    Snackbar.make(rootView, "Product restored", Snackbar.LENGTH_SHORT).show();
                    loadArchives();
                });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(DeleteProductActivity.this, "Restore failed: " + error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void confirmPermanentDelete(String filename) {
        new AlertDialog.Builder(this)
                .setTitle("Permanently delete")
                .setMessage("This will permanently remove the archived copy. This cannot be undone. Continue?")
                .setPositiveButton("Delete", (d, w) -> performPermanentDelete(filename))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performPermanentDelete(String filename) {
        if ("po".equalsIgnoreCase(archiveType)) {
            poRepository.permanentlyDeletePOArchive(this, filename, new PurchaseOrderRepository.OnArchiveListener() {
                @Override public void onArchived(String fname) {
                    runOnUiThread(() -> {
                        Snackbar.make(rootView, "Archive permanently removed", Snackbar.LENGTH_SHORT).show();
                        loadArchives();
                    });
                }
                @Override public void onError(String error) {
                    runOnUiThread(() -> Toast.makeText(DeleteProductActivity.this, "Delete failed: " + error, Toast.LENGTH_LONG).show());
                }
            });
            return;
        }
        productRepository.permanentlyDeleteArchive(filename, new ProductRepository.OnPermanentDeleteListener() {
            @Override public void onPermanentDeleted() {
                runOnUiThread(() -> {
                    Snackbar.make(rootView, "Archive permanently removed", Snackbar.LENGTH_SHORT).show();
                    loadArchives();
                });
            }
            @Override public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(DeleteProductActivity.this, "Delete failed: " + error, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void confirmClearAllArchives() {
        new AlertDialog.Builder(this)
                .setTitle("Delete all archives")
                .setMessage("This will permanently remove all archived items. This cannot be undone. Continue?")
                .setPositiveButton("Delete All", (d, w) -> deleteAllArchives())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteAllArchives() {
        if ("po".equalsIgnoreCase(archiveType)) {
            poRepository.listLocalPOArchives(this, files -> {
                if (files == null || files.isEmpty()) {
                    runOnUiThread(() -> Toast.makeText(this, "No PO archives to delete", Toast.LENGTH_SHORT).show());
                    return;
                }
                final int total = files.size();
                final int[] processed = {0};
                for (String f : files) {
                    poRepository.permanentlyDeletePOArchive(this, f, new PurchaseOrderRepository.OnArchiveListener() {
                        @Override public void onArchived(String fname) {
                            synchronized (processed) {
                                processed[0]++;
                                if (processed[0] >= total) {
                                    runOnUiThread(() -> {
                                        Snackbar.make(rootView, "All PO archives removed", Snackbar.LENGTH_SHORT).show();
                                        loadArchives();
                                    });
                                }
                            }
                        }
                        @Override public void onError(String error) {
                            synchronized (processed) {
                                processed[0]++;
                                if (processed[0] >= total) {
                                    runOnUiThread(() -> {
                                        Snackbar.make(rootView, "Completed with some errors", Snackbar.LENGTH_SHORT).show();
                                        loadArchives();
                                    });
                                }
                            }
                        }
                    });
                }
            });
            return;
        }
        List<String> files = productRepository.listLocalArchives();
        if (files == null || files.isEmpty()) {
            Toast.makeText(this, "No archives to delete", Toast.LENGTH_SHORT).show();
            return;
        }
        final int total = files.size();
        final int[] processed = {0};
        for (String f : files) {
            productRepository.permanentlyDeleteArchive(f, new ProductRepository.OnPermanentDeleteListener() {
                @Override public void onPermanentDeleted() {
                    synchronized (processed) {
                        processed[0]++;
                        if (processed[0] >= total) {
                            runOnUiThread(() -> {
                                Snackbar.make(rootView, "All archives removed", Snackbar.LENGTH_SHORT).show();
                                loadArchives();
                            });
                        }
                    }
                }
                @Override public void onError(String error) {
                    synchronized (processed) {
                        processed[0]++;
                        if (processed[0] >= total) {
                            runOnUiThread(() -> {
                                Snackbar.make(rootView, "Completed with some errors", Snackbar.LENGTH_SHORT).show();
                                loadArchives();
                            });
                        }
                    }
                }
            });
        }
    }
}