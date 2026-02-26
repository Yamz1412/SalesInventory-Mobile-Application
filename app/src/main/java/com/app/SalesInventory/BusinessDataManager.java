package com.app.SalesInventory;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BusinessDataManager {

    private static BusinessDataManager instance;
    private final FirebaseFirestore fStore;
    private ListenerRegistration productsReg;
    private ListenerRegistration salesReg;
    private ListenerRegistration adjustmentsReg;

    public interface ProductsCallback {
        void onUpdate(List<Map<String, Object>> products);
        void onError(Exception e);
    }

    public interface SalesCallback {
        void onUpdate(List<Map<String, Object>> sales, double totalRevenue);
        void onError(Exception e);
    }

    public interface AdjustmentsCallback {
        void onUpdate(List<Map<String, Object>> adjustments);
        void onError(Exception e);
    }

    private BusinessDataManager() {
        fStore = FirebaseFirestore.getInstance();
    }

    public static BusinessDataManager getInstance() {
        if (instance == null) instance = new BusinessDataManager();
        return instance;
    }

    public void listenToProducts(String ownerAdminUid, ProductsCallback cb) {
        stopProductsListener();
        CollectionReference ref = fStore.collection("products").document(ownerAdminUid).collection("items");
        productsReg = ref.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable com.google.firebase.firestore.FirebaseFirestoreException error) {
                if (error != null) {
                    cb.onError(error);
                    return;
                }
                List<Map<String, Object>> list = new ArrayList<>();
                if (snapshots != null) {
                    for (DocumentSnapshot d : snapshots.getDocuments()) {
                        Map<String, Object> m = d.getData() != null ? new HashMap<>(d.getData()) : new HashMap<>();
                        m.put("id", d.getId());
                        list.add(m);
                    }
                }
                cb.onUpdate(list);
            }
        });
    }

    public void listenToSales(String ownerAdminUid, SalesCallback cb) {
        stopSalesListener();
        CollectionReference ref = fStore.collection("sales").document(ownerAdminUid).collection("items");
        salesReg = ref.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable com.google.firebase.firestore.FirebaseFirestoreException error) {
                if (error != null) {
                    cb.onError(error);
                    return;
                }
                List<Map<String, Object>> list = new ArrayList<>();
                double total = 0.0;
                if (snapshots != null) {
                    for (DocumentSnapshot d : snapshots.getDocuments()) {
                        Map<String, Object> m = d.getData() != null ? new HashMap<>(d.getData()) : new HashMap<>();
                        m.put("id", d.getId());
                        list.add(m);
                        total += extractNumber(m, "total", "totalAmount", "amount", "grandTotal", "price");
                    }
                }
                cb.onUpdate(list, total);
            }
        });
    }

    public void listenToAdjustments(String ownerAdminUid, AdjustmentsCallback cb) {
        stopAdjustmentsListener();
        CollectionReference ref = fStore.collection("adjustments").document(ownerAdminUid).collection("items");
        adjustmentsReg = ref.addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(@Nullable QuerySnapshot snapshots, @Nullable com.google.firebase.firestore.FirebaseFirestoreException error) {
                if (error != null) {
                    cb.onError(error);
                    return;
                }
                List<Map<String, Object>> list = new ArrayList<>();
                if (snapshots != null) {
                    for (DocumentSnapshot d : snapshots.getDocuments()) {
                        Map<String, Object> m = d.getData() != null ? new HashMap<>(d.getData()) : new HashMap<>();
                        m.put("id", d.getId());
                        list.add(m);
                    }
                }
                cb.onUpdate(list);
            }
        });
    }

    public void stopProductsListener() {
        if (productsReg != null) {
            productsReg.remove();
            productsReg = null;
        }
    }

    public void stopSalesListener() {
        if (salesReg != null) {
            salesReg.remove();
            salesReg = null;
        }
    }

    public void stopAdjustmentsListener() {
        if (adjustmentsReg != null) {
            adjustmentsReg.remove();
            adjustmentsReg = null;
        }
    }

    public void stopAllListeners() {
        stopProductsListener();
        stopSalesListener();
        stopAdjustmentsListener();
    }

    private double extractNumber(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            if (m.containsKey(k) && m.get(k) != null) {
                Object o = m.get(k);
                if (o instanceof Number) return ((Number) o).doubleValue();
                if (o instanceof String) {
                    try {
                        return Double.parseDouble(((String) o).replaceAll("[^\\d.-]", ""));
                    } catch (Exception ignored) {}
                }
            }
        }
        return 0.0;
    }
}