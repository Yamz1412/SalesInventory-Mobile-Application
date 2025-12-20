package com.app.SalesInventory;

import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class FirestoreRealtimeSyncManager {
    private static final String TAG = "RealtimeSyncManager";
    private static FirestoreRealtimeSyncManager instance;
    private final FirebaseFirestore firestore;
    private final ProductRepository productRepository;
    private final Map<String, ListenerRegistration> registrations = new HashMap<>();
    private boolean listening = false;

    private FirestoreRealtimeSyncManager() {
        firestore = FirestoreManager.getInstance().getDb();
        productRepository = ProductRepository.getInstance(SalesInventoryApplication.getInstance());
    }

    public static synchronized FirestoreRealtimeSyncManager getInstance() {
        if (instance == null) instance = new FirestoreRealtimeSyncManager();
        return instance;
    }

    public synchronized void startListening() {
        if (listening) return;
        listening = true;
        attachProductsListener();
    }

    public synchronized void stopListening() {
        for (Map.Entry<String, ListenerRegistration> entry : registrations.entrySet()) {
            try {
                if (entry.getValue() != null) entry.getValue().remove();
            } catch (Exception ignored) {}
        }
        registrations.clear();
        listening = false;
    }

    private void attachProductsListener() {
        String path = FirestoreManager.getInstance().getUserProductsPath();
        if (path == null || path.isEmpty()) return;
        CollectionReference col = firestore.collection(path);
        ListenerRegistration reg = col.addSnapshotListener(new EventListener<com.google.firebase.firestore.QuerySnapshot>() {
            @Override
            public void onEvent(com.google.firebase.firestore.QuerySnapshot snapshots, FirebaseFirestoreException e) {
                if (e != null) {
                    Log.w(TAG, "Products listener error: " + e.getMessage(), e);
                    return;
                }
                if (snapshots == null) return;
                for (DocumentChange dc : snapshots.getDocumentChanges()) {
                    DocumentSnapshot doc = dc.getDocument();
                    switch (dc.getType()) {
                        case ADDED:
                        case MODIFIED:
                            Product p = mapDocToProduct(doc);
                            if (p != null) {
                                Executors.newSingleThreadExecutor().execute(() -> productRepository.upsertFromRemote(p));
                            }
                            break;
                        case REMOVED:
                            Product removed = new Product();
                            removed.setProductId(doc.getId());
                            removed.setActive(false);
                            Executors.newSingleThreadExecutor().execute(() -> productRepository.upsertFromRemote(removed));
                            break;
                    }
                }
            }
        });
        registrations.put("products", reg);
    }

    private Product mapDocToProduct(@NonNull DocumentSnapshot doc) {
        try {
            Product p = new Product();
            p.setProductId(doc.getId());
            p.setProductName(doc.getString("productName"));
            p.setCategoryId(doc.getString("categoryId"));
            p.setCategoryName(doc.getString("categoryName"));
            p.setDescription(doc.getString("description"));
            Double cp = doc.getDouble("costPrice");
            if (cp != null) p.setCostPrice(cp);
            Double sp = doc.getDouble("sellingPrice");
            if (sp != null) p.setSellingPrice(sp);
            Long qty = doc.getLong("quantity");
            if (qty != null) p.setQuantity(qty.intValue());
            Long reorder = doc.getLong("reorderLevel");
            if (reorder != null) p.setReorderLevel(reorder.intValue());
            Long critical = doc.getLong("criticalLevel");
            if (critical != null) p.setCriticalLevel(critical.intValue());
            Long ceiling = doc.getLong("ceilingLevel");
            if (ceiling != null) p.setCeilingLevel(ceiling.intValue());
            Long floor = doc.getLong("floorLevel");
            if (floor != null) p.setFloorLevel(floor.intValue());
            p.setUnit(doc.getString("unit"));
            p.setBarcode(doc.getString("barcode"));
            p.setSupplier(doc.getString("supplier"));
            p.setImageUrl(doc.getString("imageUrl"));
            p.setImagePath(doc.getString("imagePath"));
            Object ts = doc.get("lastUpdated");
            if (ts instanceof com.google.firebase.Timestamp) {
                p.setLastUpdated(((com.google.firebase.Timestamp) ts).toDate().getTime());
            } else if (doc.getLong("lastUpdated") != null) {
                p.setLastUpdated(doc.getLong("lastUpdated"));
            } else {
                p.setLastUpdated(System.currentTimeMillis());
            }
            Boolean active = doc.getBoolean("isActive");
            p.setActive(active == null ? true : active);
            Long expiry = doc.getLong("expiryDate");
            if (expiry != null) p.setExpiryDate(expiry);
            p.setProductType(doc.getString("productType"));
            return p;
        } catch (Exception e) {
            Log.w(TAG, "mapDocToProduct failed: " + e.getMessage(), e);
            return null;
        }
    }
}