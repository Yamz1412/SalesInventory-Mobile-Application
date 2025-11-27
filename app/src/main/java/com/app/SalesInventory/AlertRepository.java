package com.app.SalesInventory;

import android.app.Application;
import android.util.Log;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlertRepository {
    private static final String TAG = "AlertRepository";
    private static AlertRepository instance;
    private FirestoreManager firestoreManager;
    private FirestoreSyncListener syncListener;
    private MutableLiveData<List<Alert>> allAlerts;
    private MutableLiveData<List<Alert>> unreadAlerts;
    private MutableLiveData<Integer> unreadAlertCount;

    private AlertRepository(Application application) {
        this.firestoreManager = FirestoreManager.getInstance();
        this.syncListener = FirestoreSyncListener.getInstance();
        this.allAlerts = new MutableLiveData<>();
        this.unreadAlerts = new MutableLiveData<>();
        this.unreadAlertCount = new MutableLiveData<>(0);
        startRealtimeSync();
    }

    public static synchronized AlertRepository getInstance(Application application) {
        if (instance == null) {
            instance = new AlertRepository(application);
        }
        return instance;
    }

    private void startRealtimeSync() {
        if (!firestoreManager.isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated. Cannot start sync.");
            return;
        }
        syncListener.listenToAlerts(snapshot -> {
            List<Alert> alertList = new ArrayList<>();
            List<Alert> unread = new ArrayList<>();
            int unreadCount = 0;
            if (snapshot != null) {
                for (DocumentSnapshot document : snapshot.getDocuments()) {
                    Alert alert = document.toObject(Alert.class);
                    if (alert != null) {
                        alert.setId(document.getId());
                        alertList.add(alert);
                        if (!alert.isRead()) {
                            unread.add(alert);
                            unreadCount++;
                        }
                    }
                }
            }
            allAlerts.setValue(alertList);
            unreadAlerts.setValue(unread);
            unreadAlertCount.setValue(unreadCount);
            Log.d(TAG, "Alerts synced from Firestore: " + alertList.size() + " (Unread: " + unreadCount + ")");
        });
    }

    public LiveData<List<Alert>> getAllAlerts() {
        return allAlerts;
    }

    public LiveData<List<Alert>> getUnreadAlerts() {
        return unreadAlerts;
    }

    public LiveData<Integer> getUnreadAlertCount() {
        return unreadAlertCount;
    }

    public void addAlert(Alert alert, OnAlertAddedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }
        Map<String, Object> alertMap = convertAlertToMap(alert);
        alertMap.put("createdAt", firestoreManager.getServerTimestamp());
        firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).add(alertMap).addOnSuccessListener(documentReference -> {
            String alertId = documentReference.getId();
            alert.setId(alertId);
            listener.onAlertAdded(alertId);
            Log.d(TAG, "Alert added: " + alertId);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error adding alert", e);
            listener.onError(e.getMessage());
        });
    }

    public void markAlertAsRead(String alertId, OnAlertUpdatedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("read", true);
        firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).document(alertId).update(updates).addOnSuccessListener(aVoid -> {
            listener.onAlertUpdated();
            Log.d(TAG, "Alert marked as read: " + alertId);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error marking alert as read", e);
            listener.onError(e.getMessage());
        });
    }

    public void markAllAlertsAsRead(OnBatchUpdatedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }
        firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).whereEqualTo("read", false).get().addOnSuccessListener(snapshot -> {
            if (snapshot.isEmpty()) {
                listener.onBatchUpdated(0);
                return;
            }
            int count = 0;
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).document(doc.getId()).update("read", true);
                count++;
            }
            listener.onBatchUpdated(count);
            Log.d(TAG, "Marked " + count + " alerts as read");
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error marking all alerts as read", e);
            listener.onError(e.getMessage());
        });
    }

    public void deleteAlert(String alertId, OnAlertDeletedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }
        firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).document(alertId).delete().addOnSuccessListener(aVoid -> {
            listener.onAlertDeleted();
            Log.d(TAG, "Alert deleted: " + alertId);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error deleting alert", e);
            listener.onError(e.getMessage());
        });
    }

    public void deleteAllReadAlerts(OnBatchUpdatedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }
        firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).whereEqualTo("read", true).get().addOnSuccessListener(snapshot -> {
            if (snapshot.isEmpty()) {
                listener.onBatchUpdated(0);
                return;
            }
            int count = 0;
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).document(doc.getId()).delete();
                count++;
            }
            listener.onBatchUpdated(count);
            Log.d(TAG, "Deleted " + count + " read alerts");
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error deleting read alerts", e);
            listener.onError(e.getMessage());
        });
    }

    public void getAlertsByType(String type, OnAlertsFetchedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }
        firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).whereEqualTo("type", type).get().addOnSuccessListener(snapshot -> {
            List<Alert> alerts = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Alert alert = doc.toObject(Alert.class);
                if (alert != null) {
                    alert.setId(doc.getId());
                    alerts.add(alert);
                }
            }
            listener.onAlertsFetched(alerts);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error fetching alerts by type", e);
            listener.onError(e.getMessage());
        });
    }

    public void getAlertsByProduct(String productId, OnAlertsFetchedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }
        firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).whereEqualTo("productId", productId).get().addOnSuccessListener(snapshot -> {
            List<Alert> alerts = new ArrayList<>();
            for (DocumentSnapshot doc : snapshot.getDocuments()) {
                Alert alert = doc.toObject(Alert.class);
                if (alert != null) {
                    alert.setId(doc.getId());
                    alerts.add(alert);
                }
            }
            listener.onAlertsFetched(alerts);
        }).addOnFailureListener(e -> {
            Log.e(TAG, "Error fetching alerts by product", e);
            listener.onError(e.getMessage());
        });
    }

    private Map<String, Object> convertAlertToMap(Alert alert) {
        Map<String, Object> map = new HashMap<>();
        map.put("productId", alert.getProductId());
        map.put("type", alert.getType());
        map.put("message", alert.getMessage());
        map.put("read", alert.isRead());
        return map;
    }

    public interface OnAlertsFetchedListener {
        void onAlertsFetched(List<Alert> alerts);
        void onError(String error);
    }

    public interface OnAlertAddedListener {
        void onAlertAdded(String alertId);
        void onError(String error);
    }

    public interface OnAlertUpdatedListener {
        void onAlertUpdated();
        void onError(String error);
    }

    public interface OnAlertDeletedListener {
        void onAlertDeleted();
        void onError(String error);
    }

    public interface OnBatchUpdatedListener {
        void onBatchUpdated(int count);
        void onError(String error);
    }
}