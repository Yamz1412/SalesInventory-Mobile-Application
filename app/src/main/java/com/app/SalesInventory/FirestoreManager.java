package com.app.SalesInventory;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

public class FirestoreManager {
    private static FirestoreManager instance;
    private FirebaseFirestore db;
    private FirebaseAuth auth;
    private String currentUserId;

    private FirestoreManager() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
        updateCurrentUserId();
    }

    public static synchronized FirestoreManager getInstance() {
        if (instance == null) {
            instance = new FirestoreManager();
        }
        return instance;
    }

    /**
     * Update current user ID from Firebase Authentication
     */
    public void updateCurrentUserId() {
        if (auth.getCurrentUser() != null) {
            this.currentUserId = auth.getCurrentUser().getUid();
        }
    }

    /**
     * Get current user ID
     */
    public String getCurrentUserId() {
        return currentUserId;
    }

    /**
     * Check if user is authenticated
     */
    public boolean isUserAuthenticated() {
        return auth.getCurrentUser() != null;
    }

    /**
     * Get Firestore instance
     */
    public FirebaseFirestore getDb() {
        return db;
    }

    /**
     * Get user's collection path
     */
    public String getUserProductsPath() {
        return "products/" + currentUserId;
    }

    public String getUserSalesPath() {
        return "sales/" + currentUserId;
    }

    public String getUserAdjustmentsPath() {
        return "adjustments/" + currentUserId;
    }

    public String getUserAlertsPath() {
        return "alerts/" + currentUserId;
    }

    public String getUserCategoriesPath() {
        return "categories/" + currentUserId;
    }

    /**
     * Start batch write for multiple operations
     */
    public WriteBatch startBatch() {
        return db.batch();
    }

    public void enableOfflinePersistence() {
        try {
            db.enableNetwork().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    android.util.Log.d("Firestore", "Offline persistence enabled");
                }
            });
        } catch (Exception e) {
            android.util.Log.e("Firestore", "Error enabling offline persistence", e);
        }
    }

    /**
     * Get Firestore server timestamp
     */
    public Object getServerTimestamp() {
        return com.google.firebase.firestore.FieldValue.serverTimestamp();
    }

    /**
     * Disconnect from Firestore
     */
    public void disconnect() {
        db.terminate().addOnCompleteListener(task -> {
            android.util.Log.d("Firestore", "Disconnected from Firestore");
        });
    }

    /**
     * Reset manager (for logout)
     */
    public void reset() {
        currentUserId = null;
        instance = null;
    }
}