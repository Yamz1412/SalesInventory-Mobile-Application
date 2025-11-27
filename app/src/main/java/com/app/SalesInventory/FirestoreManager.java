package com.app.SalesInventory;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

public class FirestoreManager {
    private static FirestoreManager instance;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String currentUserId;

    private FirestoreManager() {
        db = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() != null) {
            currentUserId = auth.getCurrentUser().getUid();
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
        if (currentUserId == null && auth.getCurrentUser() != null) {
            currentUserId = auth.getCurrentUser().getUid();
        }
        return currentUserId == null ? "unknown" : currentUserId;
    }

    public String getUserProductsPath() {
        return "products/" + ensureCurrentUserId() + "/items";
    }

    public String getUserSalesPath() {
        return "sales/" + ensureCurrentUserId() + "/items";
    }

    public String getUserAdjustmentsPath() {
        return "adjustments/" + ensureCurrentUserId() + "/items";
    }

    public String getUserAlertsPath() {
        return "alerts/" + ensureCurrentUserId() + "/items";
    }

    public String getUserCategoriesPath() {
        return "categories/" + ensureCurrentUserId() + "/items";
    }

    public Object getServerTimestamp() {
        return FieldValue.serverTimestamp();
    }
}