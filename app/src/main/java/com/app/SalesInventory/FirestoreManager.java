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
        return currentUserId;
    }

    public void setBusinessOwnerId(String ownerId) {
        this.businessOwnerId = ownerId;
    }

    public String getBusinessOwnerId() {
        if (businessOwnerId != null && !businessOwnerId.isEmpty()) {
            return businessOwnerId;
        }
        return null;
    }

    public String getUserProductsPath() {
        String owner = getBusinessOwnerId();
        if (owner == null) return null;
        return "products/" + owner + "/items";
    }

    public String getUserSalesPath() {
        String owner = getBusinessOwnerId();
        if (owner == null) return null;
        return "sales/" + owner + "/items";
    }

    public String getUserAdjustmentsPath() {
        String owner = getBusinessOwnerId();
        if (owner == null) return null;
        return "adjustments/" + owner + "/items";
    }

    public String getUserAlertsPath() {
        String owner = getBusinessOwnerId();
        if (owner == null) return null;
        return "alerts/" + owner + "/items";
    }

    public String getUserCategoriesPath() {
        String owner = getBusinessOwnerId();
        if (owner == null) return null;
        return "categories/" + owner + "/items";
    }

    public String getUserPurchaseOrdersPath() {
        String owner = getBusinessOwnerId();
        if (owner == null) return null;
        return "purchaseOrders/" + owner + "/items";
    }

    public String getUserDeliveriesPath() {
        String owner = getBusinessOwnerId();
        if (owner == null) return null;
        return "deliveries/" + owner + "/items";
    }

    public Object getServerTimestamp() {
        return FieldValue.serverTimestamp();
    }
}