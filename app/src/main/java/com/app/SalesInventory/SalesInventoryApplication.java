package com.app.SalesInventory;

import android.app.Application;

public class SalesInventoryApplication extends Application {
    private static SalesInventoryApplication instance;
    private ProductRepository productRepository;
    private ProductRemoteSyncer productRemoteSyncer;
    private SalesRepository salesRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        productRepository = ProductRepository.getInstance(this);
        productRemoteSyncer = new ProductRemoteSyncer(this);
        salesRepository = SalesRepository.getInstance(this);
        String owner = FirestoreManager.getInstance().getBusinessOwnerId();
        if (owner != null && !owner.isEmpty()) {
            productRemoteSyncer.startRealtimeSync(owner);
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