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
        if (!firestoreManager.isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated for products listener");
            return;
        }
        productsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to products"));
        ListenerRegistration registration = firestoreManager.getDb().collection(firestoreManager.getUserProductsPath()).addSnapshotListener((value, error) -> {
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
                }
            }
        });
        activeListeners.add(registration);
    }

    public void listenToSales(OnSalesChangedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            return;
        }
        salesSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to sales"));
        ListenerRegistration registration = firestoreManager.getDb().collection(firestoreManager.getUserSalesPath()).addSnapshotListener((value, error) -> {
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
        if (!firestoreManager.isUserAuthenticated()) {
            return;
        }
        adjustmentsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to adjustments"));
        ListenerRegistration registration = firestoreManager.getDb().collection(firestoreManager.getUserAdjustmentsPath()).addSnapshotListener((value, error) -> {
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
        if (!firestoreManager.isUserAuthenticated()) {
            return;
        }
        alertsSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to alerts"));
        ListenerRegistration registration = firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).addSnapshotListener((value, error) -> {
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
        if (!firestoreManager.isUserAuthenticated()) {
            return;
        }
        categoriesSyncStatus.setValue(new SyncStatus(SyncStatus.Status.SYNCING, "Connecting to categories"));
        ListenerRegistration registration = firestoreManager.getDb().collection(firestoreManager.getUserCategoriesPath()).addSnapshotListener((value, error) -> {
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