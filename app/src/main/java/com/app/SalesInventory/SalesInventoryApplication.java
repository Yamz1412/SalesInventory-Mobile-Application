package com.app.SalesInventory;

import android.app.Application;

// Add these imports
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;

public class SalesInventoryApplication extends Application {
    private static SalesInventoryApplication instance;
    private ProductRepository productRepository;
    private ProductRemoteSyncer productRemoteSyncer;
    private SalesRepository salesRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // 1. Explicitly initialize Firebase first
        FirebaseApp.initializeApp(this);

        // 2. Initialize AppCheck with Play Integrity instead of SafetyNet
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
        );

        // 3. Initialize your local repositories
        productRepository = ProductRepository.getInstance(this);
        productRemoteSyncer = new ProductRemoteSyncer(this);
        salesRepository = SalesRepository.getInstance(this);

        String owner = FirestoreManager.getInstance().getBusinessOwnerId();
        if (owner != null && !owner.isEmpty()) {
            productRemoteSyncer.startListening();
        }
    }

    public static SalesInventoryApplication getInstance() {
        return instance;
    }

    public static ProductRepository getProductRepository() {
        return instance.productRepository;
    }

    public static ProductRemoteSyncer getProductRemoteSyncer() {
        return instance.productRemoteSyncer;
    }

    public static SalesRepository getSalesRepository() {
        return instance.salesRepository;
    }
}