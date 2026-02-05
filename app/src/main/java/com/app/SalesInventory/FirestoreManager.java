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

    public String getUserProductsPath() {
        return "products/" + getBusinessOwnerId() + "/items";
    }

    public String getUserSalesPath() {
        return "sales/" + getBusinessOwnerId() + "/items";
    }

    public String getUserAdjustmentsPath() {
        return "adjustments/" + getBusinessOwnerId() + "/items";
    }

    public String getUserAlertsPath() {
        return "alerts/" + getBusinessOwnerId() + "/items";
    }

    public String getUserCategoriesPath() {
        return "categories/" + getBusinessOwnerId() + "/items";
    }

    public String getUserPurchaseOrdersPath() {
        return "purchaseOrders/" + getBusinessOwnerId() + "/items";
    }

    public Object getServerTimestamp() {
        return FieldValue.serverTimestamp();
    }
}