package com.app.SalesInventory;

import android.app.Application;
import android.content.Context;
import com.google.firebase.FirebaseApp;

public class SalesInventoryApplication extends Application {
    private static SalesInventoryApplication instance;
    private static ProductRepository productRepository;
    private static AlertRepository alertRepository;
    private static SalesRepository salesRepository;

    public static class BaseContext {
        private static Context context;
        public static void setContext(Context ctx) {
            context = ctx == null ? null : ctx.getApplicationContext();
        }
        public static Context getContext() {
            return context;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        FirebaseApp.initializeApp(this);
        productRepository = ProductRepository.getInstance(this);
        alertRepository = AlertRepository.getInstance(this);
        salesRepository = SalesRepository .getInstance(this);
    }

    public static SalesInventoryApplication getInstance() {
        return instance;
    }

    public static ProductRepository getProductRepository() {
        if (productRepository == null && instance != null) {
            productRepository = ProductRepository.getInstance(instance);
        }
        return productRepository;
    }

    public static AlertRepository getAlertRepository() {
        if (alertRepository == null && instance != null) {
            alertRepository = AlertRepository.getInstance(instance);
        }
        return alertRepository;
    }

    public static SalesRepository getSalesRepository() {
        if (salesRepository == null && instance != null) {
            salesRepository = SalesRepository.getInstance(instance);
        }
        return salesRepository;
    }

    public static void resetRepositories() {
        productRepository = null;
        alertRepository = null;
        salesRepository = null;
    }

    public static Context getAppContext() {
        return instance == null ? null : instance.getApplicationContext();
    }
}