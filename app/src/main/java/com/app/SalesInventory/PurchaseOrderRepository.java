package com.app.SalesInventory;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class PurchaseOrderRepository {
    private static PurchaseOrderRepository instance;
    private final DatabaseReference rootRef;
    private final MutableLiveData<List<PurchaseOrder>> allPurchaseOrders;
    private final MutableLiveData<Integer> pendingCount;
    private static final String ARCHIVE_DIR = "archives";
    private com.google.firebase.database.ValueEventListener listListener;
    private static final String TAG = "PORepo";

    private PurchaseOrderRepository() {
        rootRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");
        allPurchaseOrders = new MutableLiveData<>(new ArrayList<>());
        pendingCount = new MutableLiveData<>(0);
        startListening();
    }

    public static synchronized PurchaseOrderRepository getInstance() {
        if (instance == null) instance = new PurchaseOrderRepository();
        return instance;
    }

    public LiveData<List<PurchaseOrder>> getAllPurchaseOrders() {
        return allPurchaseOrders;
    }

    public LiveData<Integer> getPendingCount() {
        return pendingCount;
    }

    private void startListening() {
        if (listListener != null) return;
        listListener = new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<PurchaseOrder> list = new ArrayList<>();
                int pending = 0;
                for (DataSnapshot ch : snapshot.getChildren()) {
                    PurchaseOrder po = ch.getValue(PurchaseOrder.class);
                    if (po == null) continue;
                    if (po.getPoId() == null || po.getPoId().isEmpty()) {
                        po.setPoId(ch.getKey());
                    }
                    list.add(po);
                    String status = po.getStatus();
                    if (status != null && (status.equalsIgnoreCase("Pending") || status.equalsIgnoreCase(PurchaseOrder.STATUS_PENDING))) {
                        pending++;
                    }
                }
                allPurchaseOrders.postValue(list);
                pendingCount.postValue(pending);
            }
            @Override
            public void onCancelled(DatabaseError error) {
            }
        };
        rootRef.addValueEventListener(listListener);
    }

    public interface OnArchiveListener {
        void onArchived(String filename);
        void onError(String error);
    }

    public interface OnRestorePOListener {
        void onRestored();
        void onError(String error);
    }

    public interface OnListListener {
        void onList(List<String> files);
    }

    public void archivePurchaseOrder(Context ctx, String poId, OnArchiveListener listener) {
        rootRef.child(poId).get().addOnCompleteListener(task -> {
            if (!task.isSuccessful() || task.getResult() == null || !task.getResult().exists()) {
                listener.onError("PO not found");
                return;
            }
            PurchaseOrder po = task.getResult().getValue(PurchaseOrder.class);
            if (po == null) {
                listener.onError("PO data invalid");
                return;
            }
            try {
                if (po.isReceived()) {
                    List<POItem> items = po.getItems();
                    if (items != null && !items.isEmpty()) {
                        ProductRepository productRepository = ProductRepository.getInstance((Application) ctx.getApplicationContext());
                        for (POItem item : items) {
                            if (item == null) continue;
                            String productId = item.getProductId();
                            int qty = item.getQuantity();
                            if (productId == null || productId.isEmpty() || qty <= 0) continue;
                            productRepository.getProductById(productId, new ProductRepository.OnProductFetchedListener() {
                                @Override
                                public void onProductFetched(Product product) {
                                    if (product == null) return;
                                    int current = product.getQuantity();
                                    int newQty = current - qty;
                                    if (newQty < 0) newQty = 0;
                                    productRepository.updateProductQuantity(productId, newQty, new ProductRepository.OnProductUpdatedListener() {
                                        @Override public void onProductUpdated() {}
                                        @Override public void onError(String error) {}
                                    });
                                }
                                @Override public void onError(String error) {}
                            });
                        }
                    }
                }
                File dir = new File(ctx.getFilesDir(), ARCHIVE_DIR);
                if (!dir.exists()) dir.mkdirs();
                String safeId = poId.replaceAll("[^a-zA-Z0-9_-]", "_");
                String fname = "po_" + safeId + "_" + System.currentTimeMillis() + ".json";
                File out = new File(dir, fname);
                JSONObject o = new JSONObject(po.toMap());
                try (FileWriter fw = new FileWriter(out)) {
                    fw.write(o.toString());
                    fw.flush();
                }
                String abs = out.getName();
                Log.d(TAG, "Archived file written: " + abs);
                rootRef.child(poId).removeValue().addOnCompleteListener(rTask -> {
                    if (rTask.isSuccessful()) {
                        listener.onArchived(abs);
                    } else {
                        listener.onError("Failed to remove PO from remote");
                    }
                });
            } catch (Exception ex) {
                listener.onError(ex.getMessage() == null ? "Error archiving" : ex.getMessage());
            }
        });
    }

    public void listLocalPOArchives(Context ctx, OnListListener listener) {
        List<String> result = new ArrayList<>();
        File dir = new File(ctx.getFilesDir(), ARCHIVE_DIR);
        if (!dir.exists()) {
            listener.onList(result);
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            listener.onList(result);
            return;
        }
        for (File f : files) {
            if (f.isFile() && f.getName().startsWith("po_") && f.getName().endsWith(".json")) {
                result.add(f.getName());
            }
        }
        listener.onList(result);
    }

    private File locateArchiveFile(Context ctx, String filename) {
        File dir = new File(ctx.getFilesDir(), ARCHIVE_DIR);
        if (!dir.exists()) {
            Log.d(TAG, "Archive dir does not exist");
            return null;
        }

        File exactFile = new File(dir, filename);
        if (exactFile.exists()) {
            Log.d(TAG, "Found exact match: " + exactFile.getAbsolutePath());
            return exactFile;
        }

        File asPath = new File(filename);
        String baseName = asPath.getName();
        File baseFile = new File(dir, baseName);
        if (baseFile.exists()) {
            Log.d(TAG, "Found by basename: " + baseFile.getAbsolutePath());
            return baseFile;
        }

        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().equals(filename) || f.getName().equals(baseName)) {
                    Log.d(TAG, "Found by iteration: " + f.getAbsolutePath());
                    return f;
                }
            }
        }

        Log.d(TAG, "File not found: " + filename);
        return null;
    }

    public void restorePOArchive(Context ctx, String filename, OnRestorePOListener listener) {
        try {
            Log.d(TAG, "restorePOArchive called with: " + filename);
            File archiveFile = locateArchiveFile(ctx, filename);

            if (archiveFile == null || !archiveFile.exists()) {
                Log.d(TAG, "Archive file not found");
                listener.onError("Archive not found");
                return;
            }

            Log.d(TAG, "Reading file: " + archiveFile.getAbsolutePath());
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(archiveFile))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            String finalFilename = archiveFile.getName();
            restorePOArchiveFromJson(ctx, sb.toString(), finalFilename, listener);
        } catch (Exception ex) {
            Log.e(TAG, "Error in restorePOArchive", ex);
            listener.onError(ex.getMessage() == null ? "Restore failed" : ex.getMessage());
        }
    }

    public void restorePOArchiveFromJson(Context ctx, String json, String displayName, OnRestorePOListener listener) {
        try {
            JSONObject o = new JSONObject(json);
            PurchaseOrder po = new PurchaseOrder();
            po.setPoId(o.optString("poId", null));
            po.setPoNumber(o.optString("poNumber", null));
            po.setSupplierName(o.optString("supplierName", o.optString("supplier", o.optString("supplier_name", null))));
            po.setSupplierPhone(o.optString("supplierPhone", null));
            po.setStatus(o.optString("status", PurchaseOrder.STATUS_PENDING));
            po.setOrderDate(o.optLong("orderDate", System.currentTimeMillis()));
            po.setTotalAmount(o.optDouble("totalAmount", o.optDouble("total", 0.0)));

            List<POItem> items = new ArrayList<>();
            Object itemsObj = o.opt("items");
            if (itemsObj instanceof JSONArray) {
                JSONArray arr = (JSONArray) itemsObj;
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject it = arr.optJSONObject(i);
                    if (it == null) continue;
                    POItem pi = new POItem();
                    pi.setProductId(it.optString("productId", it.optString("product_id", null)));
                    pi.setProductName(it.optString("productName", it.optString("product_name", null)));
                    int qty = it.has("quantity") ? it.optInt("quantity", 0) : it.optInt("qty", 0);
                    pi.setQuantity(qty);
                    double up = it.has("unitPrice") ? it.optDouble("unitPrice", 0.0) : it.optDouble("unit_price", it.optDouble("costPrice", it.optDouble("cost_price", 0.0)));
                    pi.setUnitPrice(up);
                    pi.setCostPrice(it.optDouble("costPrice", it.optDouble("cost_price", 0.0)));
                    items.add(pi);
                }
            } else if (itemsObj instanceof JSONObject) {
                JSONObject map = (JSONObject) itemsObj;
                Iterator<String> keys = map.keys();
                while (keys.hasNext()) {
                    String k = keys.next();
                    JSONObject it = map.optJSONObject(k);
                    if (it == null) continue;
                    POItem pi = new POItem();
                    pi.setProductId(it.optString("productId", it.optString("product_id", null)));
                    pi.setProductName(it.optString("productName", it.optString("product_name", null)));
                    int qty = it.has("quantity") ? it.optInt("quantity", 0) : it.optInt("qty", 0);
                    pi.setQuantity(qty);
                    double up = it.has("unitPrice") ? it.optDouble("unitPrice", 0.0) : it.optDouble("unit_price", it.optDouble("costPrice", it.optDouble("cost_price", 0.0)));
                    pi.setUnitPrice(up);
                    pi.setCostPrice(it.optDouble("costPrice", it.optDouble("cost_price", 0.0)));
                    items.add(pi);
                }
            }
            po.setItems(items);

            String poId = po.getPoId();
            if (poId == null || poId.isEmpty()) poId = rootRef.push().getKey();
            if (poId == null) {
                listener.onError("Failed to allocate PO id");
                return;
            }

            final String allocatedId = poId;
            rootRef.child(allocatedId).setValue(po).addOnCompleteListener(t -> {
                if (t.isSuccessful()) {
                    if (po.isReceived()) {
                        ProductRepository productRepository = ProductRepository.getInstance((Application) ctx.getApplicationContext());
                        for (POItem item : items) {
                            if (item == null) continue;
                            String productId = item.getProductId();
                            int qty = item.getQuantity();
                            if (productId == null || productId.isEmpty() || qty <= 0) continue;
                            productRepository.getProductById(productId, new ProductRepository.OnProductFetchedListener() {
                                @Override
                                public void onProductFetched(Product product) {
                                    if (product == null) return;
                                    int current = product.getQuantity();
                                    int newQty = current + qty;
                                    productRepository.updateProductQuantity(productId, newQty, new ProductRepository.OnProductUpdatedListener() {
                                        @Override public void onProductUpdated() {}
                                        @Override public void onError(String error) {}
                                    });
                                }
                                @Override public void onError(String error) {}
                            });
                        }
                    }
                    File dir = new File(ctx.getFilesDir(), ARCHIVE_DIR);
                    File f = new File(dir, displayName);
                    if (f.exists()) {
                        boolean deleted = f.delete();
                        Log.d(TAG, "Deleted archive after restore: " + deleted);
                    }
                    listener.onRestored();
                } else {
                    Log.e(TAG, "Failed to restore PO", t.getException());
                    listener.onError("Failed to restore PO to remote");
                }
            });
        } catch (Exception ex) {
            Log.e(TAG, "Error parsing JSON for restore", ex);
            listener.onError(ex.getMessage() == null ? "Restore failed" : ex.getMessage());
        }
    }

    public void permanentlyDeletePOArchive(Context ctx, String filename, OnArchiveListener listener) {
        try {
            Log.d(TAG, "permanentlyDeletePOArchive called with: " + filename);
            File f = locateArchiveFile(ctx, filename);

            if (f == null || !f.exists()) {
                Log.d(TAG, "Archive file not found for deletion");
                listener.onError("Archive not found");
                return;
            }

            Log.d(TAG, "Deleting file: " + f.getAbsolutePath());
            boolean deleted = f.delete();
            if (deleted) {
                Log.d(TAG, "File deleted successfully");
                listener.onArchived(f.getName());
            } else {
                Log.d(TAG, "Failed to delete file");
                listener.onError("Failed to remove archive");
            }
        } catch (Exception ex) {
            Log.e(TAG, "Error deleting archive", ex);
            listener.onError(ex.getMessage() == null ? "Error deleting archive" : ex.getMessage());
        }
    }

    public void stopListening() {
        if (listListener != null) {
            rootRef.removeEventListener(listListener);
            listListener = null;
        }
    }
}