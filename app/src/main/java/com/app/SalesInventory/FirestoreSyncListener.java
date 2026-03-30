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
    private FirestoreManager firestoreManager;

    public MutableLiveData<SyncStatus> productsSyncStatus = new MutableLiveData<>();
    public MutableLiveData<SyncStatus> salesSyncStatus = new MutableLiveData<>();
    public MutableLiveData<SyncStatus> adjustmentsSyncStatus = new MutableLiveData<>();
    public MutableLiveData<SyncStatus> alertsSyncStatus = new MutableLiveData<>();
    public MutableLiveData<SyncStatus> categoriesSyncStatus = new MutableLiveData<>();

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
        productsSyncStatus.postValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Initializing"));
        salesSyncStatus.postValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Initializing"));
        adjustmentsSyncStatus.postValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Initializing"));
        alertsSyncStatus.postValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Initializing"));
        categoriesSyncStatus.postValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Initializing"));
    }

    // Replace this method inside FirestoreSyncListener.java
    public void listenToProducts(OnProductsChangedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated for products listener");
            productsSyncStatus.postValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Not authenticated"));
            return;
        }
        android.util.Log.d("SalesInventory_SYNC", "Attempting to listen to Products at path: " + firestoreManager.getUserProductsPath());
        productsSyncStatus.postValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to products..."));

        ListenerRegistration registration = firestoreManager.getDb().collection(firestoreManager.getUserProductsPath()).addSnapshotListener((value, error) -> {
            if (error != null) {
                // *** THE MOST IMPORTANT LOG ***
                // If you see this error in Logcat, your Firebase config is still broken.
                android.util.Log.e("SalesInventory_SYNC", "*** ERROR Connecting to Products Firestore Path! ***", error);
                productsSyncStatus.postValue(new SyncStatus(SyncStatus.Status.ERROR, error.getMessage()));
                return;
            }
            if (value != null) {
                // *** SYNC SUCCESS LOG ***
                // This proves the cloud has data and it's being downloaded!
                android.util.Log.d("SalesInventory_SYNC", "SYNC SUCCESS: Products downloaded from Firebase! Count: " + value.size());

                productsSyncStatus.postValue(new SyncStatus(SyncStatus.Status.SYNCED, "Products synced: " + value.size()));
                if (listener != null) {
                    try { listener.onProductsChanged(value); } catch (Exception e) {}
                }

                try {
                    if (SalesInventoryApplication.getInstance() != null) {
                        ProductRemoteSyncer syncer = new ProductRemoteSyncer(SalesInventoryApplication.getInstance());
                        syncer.syncAllProducts(null);
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Error processing products via Syncer", e);
                }
            }
        });
        activeListeners.add(registration);
    }

    public void listenToSales(OnSalesChangedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) return;

        salesSyncStatus.postValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to sales"));
        ListenerRegistration registration = firestoreManager.getDb().collection(firestoreManager.getUserSalesPath()).addSnapshotListener((value, error) -> {
            if (error != null) {
                salesSyncStatus.postValue(new SyncStatus(SyncStatus.Status.ERROR, error.getMessage()));
                return;
            }
            if (value != null) {
                salesSyncStatus.postValue(new SyncStatus(SyncStatus.Status.SYNCED, "Sales synced: " + value.size()));
                if (listener != null) {
                    try { listener.onSalesChanged(value); } catch (Exception e) { Log.e(TAG, "Error", e); }
                }
            }
        });
        activeListeners.add(registration);
    }

    public void listenToAdjustments(OnAdjustmentsChangedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) return;

        adjustmentsSyncStatus.postValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to adjustments"));
        ListenerRegistration registration = firestoreManager.getDb().collection(firestoreManager.getUserAdjustmentsPath()).addSnapshotListener((value, error) -> {
            if (error != null) {
                adjustmentsSyncStatus.postValue(new SyncStatus(SyncStatus.Status.ERROR, error.getMessage()));
                return;
            }
            if (value != null) {
                adjustmentsSyncStatus.postValue(new SyncStatus(SyncStatus.Status.SYNCED, "Adjustments synced: " + value.size()));
                if (listener != null) {
                    try { listener.onAdjustmentsChanged(value); } catch (Exception e) { Log.e(TAG, "Error", e); }
                }
            }
        });
        activeListeners.add(registration);
    }

    public void listenToAlerts(OnAlertsChangedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) return;

        alertsSyncStatus.postValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to alerts"));
        ListenerRegistration registration = firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).addSnapshotListener((value, error) -> {
            if (error != null) {
                alertsSyncStatus.postValue(new SyncStatus(SyncStatus.Status.ERROR, error.getMessage()));
                return;
            }
            if (value != null) {
                alertsSyncStatus.postValue(new SyncStatus(SyncStatus.Status.SYNCED, "Alerts synced: " + value.size()));
                if (listener != null) {
                    try { listener.onAlertsChanged(value); } catch (Exception e) { Log.e(TAG, "Error", e); }
                }
            }
        });
        activeListeners.add(registration);
    }

    public void listenToCategories(OnCategoriesChangedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) return;

        categoriesSyncStatus.postValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to categories"));
        ListenerRegistration registration = firestoreManager.getDb().collection(firestoreManager.getUserCategoriesPath()).addSnapshotListener((value, error) -> {
            if (error != null) {
                categoriesSyncStatus.postValue(new SyncStatus(SyncStatus.Status.ERROR, error.getMessage()));
                return;
            }
            if (value != null) {
                categoriesSyncStatus.postValue(new SyncStatus(SyncStatus.Status.SYNCED, "Categories synced: " + value.size()));
                if (listener != null) {
                    try { listener.onCategoriesChanged(value); } catch (Exception e) { Log.e(TAG, "Error", e); }
                }
            }
        });
        activeListeners.add(registration);
    }

    public void stopAllListeners() {
        for (ListenerRegistration registration : activeListeners) {
            if (registration != null) registration.remove();
        }
        activeListeners.clear();
        Log.d(TAG, "All listeners stopped");
    }

    public void stopListener(ListenerRegistration registration) {
        if (registration != null) {
            registration.remove();
            activeListeners.remove(registration);
        }
    }

    public void reset() {
        stopAllListeners();
        instance = null;
    }

    public void restartAllListeners() {
        stopAllListeners();
        listenToProducts(null);
        listenToSales(null);
        listenToCategories(null);
        listenToAdjustments(null);
        listenToAlerts(null);
    }

    public interface OnProductsChangedListener { void onProductsChanged(QuerySnapshot snapshot); }
    public interface OnSalesChangedListener { void onSalesChanged(QuerySnapshot snapshot); }
    public interface OnAdjustmentsChangedListener { void onAdjustmentsChanged(QuerySnapshot snapshot); }
    public interface OnAlertsChangedListener { void onAlertsChanged(QuerySnapshot snapshot); }
    public interface OnCategoriesChangedListener { void onCategoriesChanged(QuerySnapshot snapshot); }
}