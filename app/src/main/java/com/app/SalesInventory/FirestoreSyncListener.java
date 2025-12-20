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
        productsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Initializing"));
        salesSyncStatus.setValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Initializing"));
        adjustmentsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Initializing"));
        alertsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Initializing"));
        categoriesSyncStatus.setValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Initializing"));
    }

    public void listenToProducts(OnProductsChangedListener listener) {
        String path = firestoreManager.getUserProductsPath();
        if (path == null) {
            Log.w(TAG, "listenToProducts skipped: businessOwnerId not set yet");
            productsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Owner not set"));
            return;
        }
        productsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to products"));
        ListenerRegistration registration = firestoreManager.getDb().collection(path).addSnapshotListener((value, error) -> {
            if (error != null) {
                productsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.ERROR, error.getMessage()));
                return;
            }
            if (value != null) {
                productsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCED, "Products synced: " + value.size()));
                if (listener != null) {
                    listener.onProductsChanged(value);
                }
                try {
                    ProductRepository repo = ProductRepository.getInstance(SalesInventoryApplication.getInstance());
                    for (DocumentSnapshot document : value.getDocuments()) {
                        Product p = document.toObject(Product.class);
                        if (p != null) {
                            p.setProductId(document.getId());
                            repo.upsertFromRemote(p);
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Error upserting remote products", e);
                }
            }
        });
        activeListeners.add(registration);
    }

    public void listenToSales(OnSalesChangedListener listener) {
        String path = firestoreManager.getUserSalesPath();
        if (path == null) {
            Log.w(TAG, "listenToSales skipped: businessOwnerId not set yet");
            salesSyncStatus.setValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Owner not set"));
            return;
        }
        salesSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to sales"));
        ListenerRegistration registration = firestoreManager.getDb().collection(path).addSnapshotListener((value, error) -> {
            if (error != null) {
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

    public void listenToAdjustments(OnAdjustmentsChangedListener listener) {
        String path = firestoreManager.getUserAdjustmentsPath();
        if (path == null) {
            Log.w(TAG, "listenToAdjustments skipped: businessOwnerId not set yet");
            adjustmentsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Owner not set"));
            return;
        }
        adjustmentsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to adjustments"));
        ListenerRegistration registration = firestoreManager.getDb().collection(path).addSnapshotListener((value, error) -> {
            if (error != null) {
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

    public void listenToAlerts(OnAlertsChangedListener listener) {
        String path = firestoreManager.getUserAlertsPath();
        if (path == null) {
            Log.w(TAG, "listenToAlerts skipped: businessOwnerId not set yet");
            alertsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Owner not set"));
            return;
        }
        alertsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to alerts"));
        ListenerRegistration registration = firestoreManager.getDb().collection(path).addSnapshotListener((value, error) -> {
            if (error != null) {
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

    public void listenToCategories(OnCategoriesChangedListener listener) {
        String path = firestoreManager.getUserCategoriesPath();
        if (path == null) {
            Log.w(TAG, "listenToCategories skipped: businessOwnerId not set yet");
            categoriesSyncStatus.setValue(new SyncStatus(SyncStatus.Status.OFFLINE, "Owner not set"));
            return;
        }
        categoriesSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to categories"));
        ListenerRegistration registration = firestoreManager.getDb().collection(path).addSnapshotListener((value, error) -> {
            if (error != null) {
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

    public void stopAllListeners() {
        for (ListenerRegistration registration : activeListeners) {
            registration.remove();
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