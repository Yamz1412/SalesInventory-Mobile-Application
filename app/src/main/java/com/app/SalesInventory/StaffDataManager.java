package com.app.SalesInventory;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Map;

public class StaffDataManager {

    private static StaffDataManager instance;
    private final FirebaseAuth auth;
    private final FirebaseFirestore fStore;
    private final BusinessDataManager businessDataManager;

    public interface ProductListener {
        void onProducts(List<Map<String, Object>> products);
        void onError(Exception e);
    }

    public interface SalesListener {
        void onSales(List<Map<String, Object>> sales, double totalRevenue);
        void onError(Exception e);
    }

    public interface AdjustmentsListener {
        void onAdjustments(List<Map<String, Object>> adjustments);
        void onError(Exception e);
    }

    public interface OwnerCallback {
        void onOwnerResolved(String ownerAdminId);
    }

    private StaffDataManager() {
        auth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        businessDataManager = BusinessDataManager.getInstance();
    }

    public static StaffDataManager getInstance() {
        if (instance == null) instance = new StaffDataManager();
        return instance;
    }

    public void startForCurrentUser(final ProductListener p, final SalesListener s, final AdjustmentsListener a) {
        startForCurrentUser(p, s, a, null);
    }

    public void startForCurrentUser(final ProductListener p, final SalesListener s, final AdjustmentsListener a, final OwnerCallback ownerCb) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            if (p != null) p.onError(new IllegalStateException("No signed-in user"));
            if (s != null) s.onError(new IllegalStateException("No signed-in user"));
            if (a != null) a.onError(new IllegalStateException("No signed-in user"));
            if (ownerCb != null) ownerCb.onOwnerResolved(null);
            return;
        }
        final String uid = u.getUid();
        fStore.collection("users").document(uid).get().addOnCompleteListener(task -> {
            String ownerAdminId = uid;
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                DocumentSnapshot doc = task.getResult();
                String owner = doc.getString("ownerAdminId");
                if (owner != null && !owner.isEmpty()) ownerAdminId = owner;
            }
            FirestoreManager.getInstance().setBusinessOwnerId(ownerAdminId);
            if (ownerCb != null) ownerCb.onOwnerResolved(ownerAdminId);
            if (p != null) {
                businessDataManager.listenToProducts(ownerAdminId, new BusinessDataManager.ProductsCallback() {
                    @Override
                    public void onUpdate(List<Map<String, Object>> products) {
                        p.onProducts(products);
                    }
                    @Override
                    public void onError(Exception e) {
                        p.onError(e);
                    }
                });
            }
            if (s != null) {
                businessDataManager.listenToSales(ownerAdminId, new BusinessDataManager.SalesCallback() {
                    @Override
                    public void onUpdate(List<Map<String, Object>> sales, double totalRevenue) {
                        s.onSales(sales, totalRevenue);
                    }
                    @Override
                    public void onError(Exception e) {
                        s.onError(e);
                    }
                });
            }
            if (a != null) {
                businessDataManager.listenToAdjustments(ownerAdminId, new BusinessDataManager.AdjustmentsCallback() {
                    @Override
                    public void onUpdate(List<Map<String, Object>> adjustments) {
                        a.onAdjustments(adjustments);
                    }
                    @Override
                    public void onError(Exception e) {
                        a.onError(e);
                    }
                });
            }
        }).addOnFailureListener(e -> {
            if (p != null) p.onError(e);
            if (s != null) s.onError(e);
            if (a != null) a.onError(e);
            if (ownerCb != null) ownerCb.onOwnerResolved(null);
        });
    }

    public void stopAll() {
        businessDataManager.stopAllListeners();
    }
}