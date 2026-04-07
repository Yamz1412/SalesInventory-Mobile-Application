package com.app.SalesInventory;

import android.app.Application;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentChange;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductRemoteSyncer {
    private static final String TAG = "ProductRemoteSyncer";

    private final ProductRepository productRepository;
    private final FirebaseFirestore db;
    private ListenerRegistration productsListener;
    private boolean isInitialSnapshot = true; // NEW: Track the initial burst

    public ProductRemoteSyncer(Application application) {
        this.productRepository = ProductRepository.getInstance(application);
        this.db = FirestoreManager.getInstance().getDb();
    }

    public void syncAllProducts(@Nullable Runnable onFinished) {
        if (onFinished != null) {
            onFinished.run();
        }
    }

    public void startListening() {
        if (!FirestoreManager.getInstance().hasValidUser()) return;
        if (productsListener != null) return;

        String path = FirestoreManager.getInstance().getUserProductsPath();
        productsListener = db.collection(path).addSnapshotListener((snapshots, e) -> {
            if (e != null) {
                Log.e(TAG, "Listen failed.", e);
                return;
            }

            // CRITICAL FIX: Ignore the initial massive burst of 166 items!
            // SyncWorker already downloaded them efficiently in the background.
            if (isInitialSnapshot) {
                isInitialSnapshot = false;
                return;
            }

            handleSnapshot(snapshots);
        });
    }

    public void stopListening() {
        if (productsListener != null) {
            productsListener.remove();
            productsListener = null;
        }
    }

    private void handleSnapshot(QuerySnapshot snapshot) {
        if (snapshot == null) return;
        for (DocumentSnapshot doc : snapshot.getDocuments()) {
            Product p = documentToProduct(doc);
            productRepository.upsertFromRemote(p);
        }
    }

    private Product documentToProduct(DocumentSnapshot doc) {
        Product p = new Product();
        p.setProductId(doc.getId());
        p.setProductName(getString(doc, "productName"));
        p.setCategoryId(getString(doc, "categoryId"));
        p.setCategoryName(getString(doc, "categoryName"));
        p.setProductLine(getString(doc, "productLine"));
        p.setDescription(getString(doc, "description"));
        p.setCostPrice(getDouble(doc, "costPrice"));
        p.setSellingPrice(getDouble(doc, "sellingPrice"));
        p.setQuantity(getDouble(doc, "quantity"));
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
        p.setImagePath(getString(doc, "imagePath"));

        p.setBomList(getListObj(doc, "bomList", "bomListJson"));
        p.setUnifiedVariations(getListObj(doc, "variantsList", "variantsListJson"));

        return p;
    }

    private List<Map<String, Object>> getListObj(DocumentSnapshot doc, String field, String jsonField) {
        Object val = doc.get(field);
        if (val instanceof List) {
            try {
                return (List<Map<String, Object>>) val;
            } catch (Exception e) { e.printStackTrace(); }
        }
        String json = doc.getString(jsonField);
        if (json != null && !json.isEmpty()) {
            try {
                org.json.JSONArray array = new org.json.JSONArray(json);
                List<Map<String, Object>> list = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    org.json.JSONObject obj = array.getJSONObject(i);
                    Map<String, Object> map = new HashMap<>();
                    java.util.Iterator<String> keys = obj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        map.put(key, obj.get(key));
                    }
                    list.add(map);
                }
                return list;
            } catch (Exception e) { e.printStackTrace(); }
        }
        return new ArrayList<>();
    }

    private List<String> getListStr(DocumentSnapshot doc, String field, String jsonField) {
        Object val = doc.get(field);
        if (val instanceof List) {
            try {
                return (List<String>) val;
            } catch (Exception e) { e.printStackTrace(); }
        }
        String json = doc.getString(jsonField);
        if (json != null && !json.isEmpty()) {
            try {
                org.json.JSONArray array = new org.json.JSONArray(json);
                List<String> list = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) {
                    list.add(array.getString(i));
                }
                return list;
            } catch (Exception e) { e.printStackTrace(); }
        }
        return new ArrayList<>();
    }

    private String getString(DocumentSnapshot doc, String field) {
        String v = doc.getString(field);
        return v == null ? "" : v;
    }

    private double getDouble(DocumentSnapshot doc, String field) {
        Object val = doc.get(field);
        if (val instanceof Number) return ((Number) val).doubleValue();
        if (val instanceof String) {
            try { return Double.parseDouble((String) val); } catch (Exception ignored) {}
        }
        return 0.0;
    }

    private int getInt(DocumentSnapshot doc, String field) {
        Object val = doc.get(field);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (Exception ignored) {}
        }
        return 0;
    }

    private long getLong(DocumentSnapshot doc, String field) {
        Object val = doc.get(field);
        // 1. Handle standard Number (Long/Integer)
        if (val instanceof Number) return ((Number) val).longValue();

        // 2. FIX: Handle Firebase Timestamp safely
        if (val instanceof com.google.firebase.Timestamp) {
            return ((com.google.firebase.Timestamp) val).toDate().getTime();
        }

        // 3. Handle standard Java Date
        if (val instanceof java.util.Date) {
            return ((java.util.Date) val).getTime();
        }

        // 4. Handle String fallback
        if (val instanceof String) {
            try { return Long.parseLong((String) val); } catch (Exception ignored) {}
        }

        return 0L;
    }

    private boolean getBoolean(DocumentSnapshot doc, String field, boolean def) {
        Boolean b = doc.getBoolean(field);
        return b == null ? def : b;
    }
}