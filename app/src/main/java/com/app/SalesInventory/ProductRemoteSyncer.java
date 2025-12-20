package com.app.SalesInventory;

import android.app.Application;
import android.util.Log;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

public class ProductRemoteSyncer {
    private static final String TAG = "ProductRemoteSyncer";
    private final ProductRepository productRepository;
    private final FirebaseFirestore db;
    private ListenerRegistration productsListener;

    public ProductRemoteSyncer(Application application) {
        this.productRepository = ProductRepository.getInstance(application);
        this.db = FirestoreManager.getInstance().getDb();
    }

    public void syncAllProducts(@Nullable Runnable onFinished) {
        String path = FirestoreManager.getInstance().getUserProductsPath();
        db.collection(path)
                .get()
                .addOnSuccessListener(this::handleSnapshot)
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to download products", e);
                    if (onFinished != null) onFinished.run();
                })
                .addOnCompleteListener(task -> {
                    if (onFinished != null) onFinished.run();
                });
    }

    private void handleSnapshot(QuerySnapshot snapshot) {
        if (snapshot == null) return;
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Product p = mapDocToProduct(doc);
            if (p != null) {
                productRepository.upsertFromRemote(p);
            }
        }
    }

    public void startRealtimeSync(String ownerAdminUid) {
        stopRealtimeSync();
        if (ownerAdminUid == null || ownerAdminUid.isEmpty()) return;
        String path = "products/" + ownerAdminUid + "/items";
        productsListener = db.collection(path).addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable com.google.firebase.firestore.FirebaseFirestoreException error) {
                if (error != null) {
                    Log.e(TAG, "Realtime listener error", error);
                    return;
                }
                if (snapshots == null) return;
                for (DocumentChange dc : snapshots.getDocumentChanges()) {
                    DocumentSnapshot doc = dc.getDocument();
                    Product p = mapDocToProduct(doc);
                    if (p == null) continue;
                    productRepository.upsertFromRemote(p);
                }
            }
        });
    }

    public void stopRealtimeSync() {
        if (productsListener != null) {
            productsListener.remove();
            productsListener = null;
        }
    }

    private Product mapDocToProduct(DocumentSnapshot doc) {
        if (doc == null || !doc.exists()) return null;
        Product p = new Product();
        p.setProductId(doc.getId());
        p.setProductName(getString(doc, "productName"));
        p.setCategoryId(getString(doc, "categoryId"));
        p.setCategoryName(getString(doc, "categoryName"));
        p.setDescription(getString(doc, "description"));
        p.setCostPrice(getDouble(doc, "costPrice"));
        p.setSellingPrice(getDouble(doc, "sellingPrice"));
        p.setQuantity(getInt(doc, "quantity"));
        p.setReorderLevel(getInt(doc, "reorderLevel"));
        p.setCriticalLevel(getInt(doc, "criticalLevel"));
        p.setCeilingLevel(getInt(doc, "ceilingLevel"));
        p.setFloorLevel(getInt(doc, "floorLevel"));
        p.setUnit(getString(doc, "unit"));
        p.setBarcode(getString(doc, "barcode"));
        p.setSupplier(getString(doc, "supplier"));
        p.setDateAdded(getLong(doc, "dateAdded"));
        p.setAddedBy(getString(doc, "addedBy"));
        p.setActive(getBoolean(doc, "isActive", true));
        p.setExpiryDate(getLong(doc, "expiryDate"));
        p.setProductType(getString(doc, "productType"));
        p.setImageUrl(getString(doc, "imageUrl"));
        p.setImagePath(null);
        return p;
    }

    private String getString(DocumentSnapshot doc, String field) {
        String v = doc.getString(field);
        return v == null ? "" : v;
    }

    private double getDouble(DocumentSnapshot doc, String field) {
        Double d = doc.getDouble(field);
        if (d != null) return d;
        Long l = doc.getLong(field);
        return l == null ? 0.0 : l.doubleValue();
    }

    private int getInt(DocumentSnapshot doc, String field) {
        Long l = doc.getLong(field);
        return l == null ? 0 : l.intValue();
    }

    private long getLong(DocumentSnapshot doc, String field) {
        Long l = doc.getLong(field);
        return l == null ? 0L : l;
    }

    private boolean getBoolean(DocumentSnapshot doc, String field, boolean def) {
        Boolean b = doc.getBoolean(field);
        return b == null ? def : b;
    }
}