package com.app.SalesInventory;

import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class FirestoreSyncListener {
    private static final String TAG = "FirestoreSyncListener";
    private static FirestoreSyncListener instance;
    private com.app.SalesInventory.FirestoreManager firestoreManager;

    // LiveData for sync status
    public MutableLiveData<SyncStatus> productsSyncStatus = new MutableLiveData<>();
    public MutableLiveData<SyncStatus> salesSyncStatus = new MutableLiveData<>();
    public MutableLiveData<SyncStatus> adjustmentsSyncStatus = new MutableLiveData<>();
    public MutableLiveData<SyncStatus> alertsSyncStatus = new MutableLiveData<>();
    public MutableLiveData<SyncStatus> categoriesSyncStatus = new MutableLiveData<>();

    // Listener registrations
    private List<ListenerRegistration> activeListeners = new ArrayList<>();

    private FirestoreSyncListener() {
        this.firestoreManager = FirestoreManager.getInstance();
        initializeSyncStatus();
    }

    public static synchronized FirestoreSyncListener getInstance() {
        if (instance == null) {
            instance = new FirestoreSyncListener();
        }
        return instance;
    }

    private void initializeSyncStatus() {
        productsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Initializing products listener"));
        salesSyncStatus.setValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Initializing sales listener"));
        adjustmentsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Initializing adjustments listener"));
        alertsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Initializing alerts listener"));
        categoriesSyncStatus.setValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Initializing categories listener"));
    }

    /**
     * Start listening to products collection
     */
    public void listenToProducts(OnProductsChangedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated for products listener");
            return;
        }

        productsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to products..."));

        ListenerRegistration registration = firestoreManager.getDb()
                .collection(firestoreManager.getUserProductsPath())
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to products", error);
                        productsSyncStatus.setValue(new SyncStatus(com.app.SalesInventory.SyncStatus.Status.ERROR, error.getMessage()));
                        return;
                    }

                    if (value != null) {
                        productsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCED, "Products synced: " + value.size()));
                        if (listener != null) {
                            listener.onProductsChanged(value);
                        }
                    }
                });

        activeListeners.add(registration);
    }

    /**
     * Start listening to sales collection
     */
    public void listenToSales(OnSalesChangedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated for sales listener");
            return;
        }

        salesSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to sales..."));

        ListenerRegistration registration = firestoreManager.getDb()
                .collection(firestoreManager.getUserSalesPath())
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to sales", error);
                        salesSyncStatus.setValue(new SyncStatus(SyncStatus.Status.ERROR, error.getMessage()));
                        return;
                    }

                    if (value != null) {
                        salesSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCED, "Sales synced: " + value.size()));
                        if (listener != null) {
                            listener.onSalesChanged(value);
                        }
                    }
                });

        activeListeners.add(registration);
    }

    /**
     * Start listening to adjustments collection
     */
    public void listenToAdjustments(OnAdjustmentsChangedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated for adjustments listener");
            return;
        }

        adjustmentsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to adjustments..."));

        ListenerRegistration registration = firestoreManager.getDb()
                .collection(firestoreManager.getUserAdjustmentsPath())
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to adjustments", error);
                        adjustmentsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.ERROR, error.getMessage()));
                        return;
                    }

                    if (value != null) {
                        adjustmentsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCED, "Adjustments synced: " + value.size()));
                        if (listener != null) {
                            listener.onAdjustmentsChanged(value);
                        }
                    }
                });

        activeListeners.add(registration);
    }

    /**
     * Start listening to alerts collection
     */
    public void listenToAlerts(OnAlertsChangedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated for alerts listener");
            return;
        }

        alertsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to alerts..."));

        ListenerRegistration registration = firestoreManager.getDb()
                .collection(firestoreManager.getUserAlertsPath())
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to alerts", error);
                        alertsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.ERROR, error.getMessage()));
                        return;
                    }

                    if (value != null) {
                        alertsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCED, "Alerts synced: " + value.size()));
                        if (listener != null) {
                            listener.onAlertsChanged(value);
                        }
                    }
                });

        activeListeners.add(registration);
    }

    /**
     * Start listening to categories collection
     */
    public void listenToCategories(OnCategoriesChangedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated for categories listener");
            return;
        }

        categoriesSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to categories..."));

        ListenerRegistration registration = firestoreManager.getDb()
                .collection(firestoreManager.getUserCategoriesPath())
                .addSnapshotListener((value, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error listening to categories", error);
                        categoriesSyncStatus.setValue(new SyncStatus(SyncStatus.Status.ERROR, error.getMessage()));
                        return;
                    }

                    if (value != null) {
                        categoriesSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCED, "Categories synced: " + value.size()));
                        if (listener != null) {
                            listener.onCategoriesChanged(value);
                        }
                    }
                });

        activeListeners.add(registration);
    }

    /**
     * Stop all listeners
     */
    public void stopAllListeners() {
        for (ListenerRegistration registration : activeListeners) {
            registration.remove();
        }
        activeListeners.clear();
        Log.d(TAG, "All listeners stopped");
    }

    /**
     * Stop specific listener
     */
    public void stopListener(ListenerRegistration registration) {
        if (registration != null) {
            registration.remove();
            activeListeners.remove(registration);
        }
    }

    /**
     * Reset listener (for logout)
     */
    public void reset() {
        stopAllListeners();
        instance = null;
    }

    // Callback interfaces
    public interface OnProductsChangedListener {
        void onProductsChanged(QuerySnapshot snapshot);
    }

    public interface OnSalesChangedListener {
        void onSalesChanged(QuerySnapshot snapshot);
    }

    public interface OnAdjustmentsChangedListener {
        void onAdjustmentsChanged(QuerySnapshot snapshot);
    }

    public interface OnAlertsChangedListener {
        void onAlertsChanged(QuerySnapshot snapshot);
    }

    public interface OnCategoriesChangedListener {
        void onCategoriesChanged(QuerySnapshot snapshot);
    }
}