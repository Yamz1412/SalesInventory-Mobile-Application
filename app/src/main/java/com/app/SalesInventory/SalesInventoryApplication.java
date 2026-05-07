package com.app.SalesInventory;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.FirebaseAppCheck;
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory;
import androidx.annotation.NonNull;
import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
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

        // --- 1. START GLOBAL PRESENCE TRACKER ---
        setupGlobalPresence();

        // --- 2. START BACKGROUND / FOREGROUND TRACKER ---
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStart(@NonNull LifecycleOwner owner) {
                // App is opened or unlocked -> End Break & Go Online
                if (AuthManager.getInstance().getCurrentUserId() != null) {
                    logAttendance("BREAK_END");
                    updateOnlineStatus(true);
                }
            }

            @Override
            public void onStop(@NonNull LifecycleOwner owner) {
                // App is minimized, screen locked, or swiped away -> Start Break & Go Offline
                if (AuthManager.getInstance().getCurrentUserId() != null) {
                    logAttendance("BREAK_START");
                    updateOnlineStatus(false);
                }
            }
        });

        productRepository = ProductRepository.getInstance(this);
        productRemoteSyncer = new ProductRemoteSyncer(this);
        salesRepository = SalesRepository.getInstance(this);

        String owner = FirestoreManager.getInstance().getBusinessOwnerId();
        if (owner != null && !owner.isEmpty()) {
            productRemoteSyncer.startListening();
        }
        SyncScheduler.schedulePeriodicSync(this);
    }

    public static SalesInventoryApplication getInstance() { return instance; }
    public static ProductRepository getProductRepository() { return instance.productRepository; }
    public static ProductRemoteSyncer getProductRemoteSyncer() { return instance.productRemoteSyncer; }
    public static SalesRepository getSalesRepository() { return instance.salesRepository; }

    private void setupGlobalPresence() {
        FirebaseAuth.getInstance().addAuthStateListener(firebaseAuth -> {
            FirebaseUser user = firebaseAuth.getCurrentUser();
            if (user != null) {
                String uid = user.getUid();
                DatabaseReference statusRef = FirebaseDatabase.getInstance().getReference("UsersStatus").child(uid);
                DatabaseReference connectedRef = FirebaseDatabase.getInstance().getReference(".info/connected");

                connectedRef.addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        boolean connected = Boolean.TRUE.equals(snapshot.getValue(Boolean.class));
                        if (connected) {
                            // RTDB Disconnect rules
                            statusRef.child("status").onDisconnect().setValue("offline");
                            statusRef.child("lastActive").onDisconnect().setValue(System.currentTimeMillis());

                            // Set to online immediately upon connection
                            statusRef.child("status").setValue("online");
                            statusRef.child("lastActive").setValue(System.currentTimeMillis());

                            // CRITICAL FIX: Also update Firestore so the AdminStaffList sees it!
                            FirebaseFirestore.getInstance().collection("users").document(uid)
                                    .update("isOnline", true, "lastActive", System.currentTimeMillis());
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
            }
        });
    }

    public static void logAttendance(String status) {
        String uid = AuthManager.getInstance().getCurrentUserId();
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (uid == null || ownerId == null || uid.isEmpty() || ownerId.isEmpty()) return;

        long now = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateKey = sdf.format(new Date(now));

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // CRITICAL FIX 1: Write to AttendanceLogs so AttendanceLogsActivity can read it!
        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("userId", uid);
        logEntry.put("status", status); // "BREAK_START" or "BREAK_END"
        logEntry.put("timestamp", now);

        db.collection("AttendanceLogs").document(ownerId).collection(dateKey).add(logEntry);

        // CRITICAL FIX 2: Trigger AuthManager to lock/unlock the specific shift document
        if (status.equals("BREAK_START")) {
            AuthManager.getInstance().logShiftLock(true);
        } else if (status.equals("BREAK_END")) {
            AuthManager.getInstance().logShiftLock(false);
        }
    }

    public static void updateOnlineStatus(boolean isOnline) {
        String uid = AuthManager.getInstance().getCurrentUserId();
        if (uid != null && !uid.isEmpty()) {
            long now = System.currentTimeMillis();

            // Update Realtime Database
            DatabaseReference statusRef = FirebaseDatabase.getInstance().getReference("UsersStatus").child(uid);
            statusRef.child("status").setValue(isOnline ? "online" : "offline");
            statusRef.child("lastActive").setValue(now);

            // Safely update Firestore using merge to prevent missing-field crashes
            Map<String, Object> updates = new HashMap<>();
            updates.put("isOnline", isOnline);
            updates.put("lastActive", now);
            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .set(updates, com.google.firebase.firestore.SetOptions.merge());
        }
    }
}