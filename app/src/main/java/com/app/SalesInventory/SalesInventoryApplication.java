package com.app.SalesInventory;

import android.app.Application;
import android.util.Log;

import com.google.firebase.FirebaseApp;

/**
 * Application class - Initializes Firebase and repositories
 */
public class SalesInventoryApplication extends Application {
    private static final String TAG = "SalesInventoryApplication";

    // Repository instances
    private static ProductRepository productRepository;
    private static SalesRepository salesRepository;
    private static AdjustmentRepository adjustmentRepository;
    private static AlertRepository alertRepository;
    private static CategoryRepository categoryRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Application starting...");

        // Initialize Firebase
        initializeFirebase();

        Log.d(TAG, "Application initialized successfully");
    }

    /**
     * Initialize Firebase services
     */
    private void initializeFirebase() {
        try {
            // Initialize Firebase
            FirebaseApp.initializeApp(this);
            Log.d(TAG, "Firebase initialized");

            // Initialize Firestore Manager
            FirestoreManager firestoreManager = FirestoreManager.getInstance();
            firestoreManager.enableOfflinePersistence();
            Log.d(TAG, "Firestore offline persistence enabled");

            // Initialize Sync Listener
            FirestoreSyncListener syncListener = FirestoreSyncListener.getInstance();
            Log.d(TAG, "Firestore sync listener initialized");

        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase", e);
        }
    }

    /**
     * Get Product Repository
     */
    public static ProductRepository getProductRepository() {
        if (productRepository == null) {
            productRepository = ProductRepository.getInstance(getInstance());
        }
        return productRepository;
    }

    /**
     * Get Sales Repository
     */
    public static SalesRepository getSalesRepository() {
        if (salesRepository == null) {
            salesRepository = SalesRepository.getInstance(getInstance());
        }
        return salesRepository;
    }

    /**
     * Get Adjustment Repository
     */
    public static AdjustmentRepository getAdjustmentRepository() {
        if (adjustmentRepository == null) {
            adjustmentRepository = AdjustmentRepository.getInstance(getInstance());
        }
        return adjustmentRepository;
    }

    /**
     * Get Alert Repository
     */
    public static AlertRepository getAlertRepository() {
        if (alertRepository == null) {
            alertRepository = AlertRepository.getInstance(getInstance());
        }
        return alertRepository;
    }

    /**
     * Get Category Repository
     */
    public static CategoryRepository getCategoryRepository() {
        if (categoryRepository == null) {
            categoryRepository = CategoryRepository.getInstance(getInstance());
        }
        return categoryRepository;
    }

    /**
     * Reset all repositories on logout
     */
    public static void resetRepositories() {
        FirestoreManager.getInstance().reset();
        FirestoreSyncListener.getInstance().reset();
        productRepository = null;
        salesRepository = null;
        adjustmentRepository = null;
        alertRepository = null;
        categoryRepository = null;
        Log.d(TAG, "All repositories reset for logout");
    }

    /**
     * Get application instance
     */
    private static SalesInventoryApplication getInstance() {
        return (SalesInventoryApplication) BaseContext.getContext();
    }

    /**
     * Helper class to maintain application context
     */
    public static class BaseContext {
        private static android.content.Context context;

        public static void setContext(android.content.Context ctx) {
            context = ctx;
        }

        public static android.content.Context getContext() {
            return context;
        }
    }
}