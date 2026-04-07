package com.app.SalesInventory;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class FirestoreManager {
    private static FirestoreManager instance;
    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private String currentUserId;
    private String businessOwnerId;

    private FirestoreManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        FirebaseUser u = auth.getCurrentUser();
        if (u != null) {
            currentUserId = u.getUid();
        }
    }

    public static synchronized FirestoreManager getInstance() {
        if (instance == null) {
            instance = new FirestoreManager();
        }
        return instance;
    }

    public FirebaseFirestore getDb() {
        return db;
    }

    public boolean isUserAuthenticated() {
        return auth.getCurrentUser() != null;
    }

    // --- NEW METHOD TO PREVENT "UNKNOWN" PERMISSION DENIED CRASHES ---
    public boolean hasValidUser() {
        String id = getBusinessOwnerId();
        return id != null && !id.trim().isEmpty() && !id.equals("unknown");
    }
    // -----------------------------------------------------------------

    public void updateCurrentUserId(String uid) {
        this.currentUserId = uid;
    }

    private String ensureCurrentUserId() {
        if (currentUserId == null) {
            FirebaseUser u = auth.getCurrentUser();
            if (u != null) {
                currentUserId = u.getUid();
            }
        }
        return currentUserId == null ? "unknown" : currentUserId;
    }

    public String getCurrentUserId() {
        return ensureCurrentUserId();
    }

    public void setBusinessOwnerId(String ownerId) {
        this.businessOwnerId = ownerId;
    }

    public String getBusinessOwnerId() {
        if (businessOwnerId != null && !businessOwnerId.isEmpty()) {
            return businessOwnerId;
        }
        return ensureCurrentUserId();
    }

    public void clearCachedIds() {
        this.currentUserId = null;
        this.businessOwnerId = null;
    }

    public com.google.firebase.firestore.DocumentReference getResetSignalRef() {
        return getDb().collection("users").document(getBusinessOwnerId()).collection("system").document("reset_signal");
    }

    public String getUserProductsPath() {
        String ownerId = getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();
        return "users/" + ownerId + "/products";
    }

    public String getUserSalesPath() {
        String ownerId = getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();
        return "users/" + ownerId + "/sales";
    }

    public String getUserAdjustmentsPath() {
        String ownerId = getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();
        return "users/" + ownerId + "/adjustments";
    }

    public String getUserAlertsPath() {
        String ownerId = getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();
        return "users/" + ownerId + "/alerts";
    }

    public String getUserCategoriesPath() {
        String ownerId = getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();
        return "users/" + ownerId + "/categories";
    }

    public String getUserPurchaseOrdersPath() {
        return "purchaseOrders/" + getBusinessOwnerId() + "/items";
    }

    public Object getServerTimestamp() {
        return FieldValue.serverTimestamp();
    }
}