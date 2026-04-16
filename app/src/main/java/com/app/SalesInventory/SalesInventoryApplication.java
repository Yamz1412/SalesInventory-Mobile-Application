package com.app.SalesInventory;

import android.app.Application;

// Add these imports
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.FirebaseFirestore;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class SalesInventoryApplication extends Application {
    private static SalesInventoryApplication instance;
    private ProductRepository productRepository;
    private ProductRemoteSyncer productRemoteSyncer;
    private SalesRepository salesRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        FirebaseApp.initializeApp(this);
        FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        FirebaseAppCheck firebaseAppCheck = FirebaseAppCheck.getInstance();
        firebaseAppCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
        );

        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                // App is opened or unlocked -> End Break
                if (AuthManager.getInstance().getCurrentUserId() != null) {
                    logAttendance("BREAK_END");
                }
            }

            @Override
            public void onStop(@NonNull LifecycleOwner owner) {
                // App is minimized, screen locked, or swiped away -> Start Break
                if (AuthManager.getInstance().getCurrentUserId() != null) {
                    logAttendance("BREAK_START");
                }
            }
        });
        // -------------------------------------------------------------------------


        // 3. Initialize your local repositories
        productRepository = ProductRepository.getInstance(this);
        productRemoteSyncer = new ProductRemoteSyncer(this);
        salesRepository = SalesRepository.getInstance(this);

        String owner = FirestoreManager.getInstance().getBusinessOwnerId();
        if (owner != null && !owner.isEmpty()) {
            productRemoteSyncer.startListening();
        }
        SyncScheduler.schedulePeriodicSync(this);
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

    // --- NEW: Centralized Real-Time Attendance Logger ---
    public static void logAttendance(String action) {
        String userId = AuthManager.getInstance().getCurrentUserId();
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (userId == null || ownerId == null) return;

        com.google.firebase.firestore.CollectionReference attendanceRef = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                .collection("users").document(ownerId)
                .collection("attendance");

        // Find the open shift for this user
        attendanceRef.whereEqualTo("staffId", userId)
                .whereIn("status", java.util.Arrays.asList("ACTIVE", "ON_BREAK"))
                .get().addOnSuccessListener(querySnapshot -> {

                    if (!querySnapshot.isEmpty()) {
                        com.google.firebase.firestore.DocumentSnapshot doc = querySnapshot.getDocuments().get(0);
                        String docId = doc.getId();
                        java.util.Map<String, Object> updates = new java.util.HashMap<>();

                        long now = System.currentTimeMillis();

                        if (action.equals("TIME_OUT")) {
                            updates.put("endTime", now);
                            updates.put("status", "COMPLETED");
                        } else if (action.equals("BREAK_START")) {
                            updates.put("currentBreakStart", now);
                            updates.put("status", "ON_BREAK");
                        } else if (action.equals("BREAK_END")) {
                            long breakStart = doc.getLong("currentBreakStart") != null ? doc.getLong("currentBreakStart") : now;
                            long totalBreak = doc.getLong("totalBreakTime") != null ? doc.getLong("totalBreakTime") : 0;

                            long newBreakDurationMins = (now - breakStart) / (60 * 1000);

                            updates.put("totalBreakTime", totalBreak + newBreakDurationMins);
                            updates.put("currentBreakStart", 0);
                            updates.put("status", "ACTIVE");
                        }

                        attendanceRef.document(docId).update(updates);
                    }
                });
    }
}