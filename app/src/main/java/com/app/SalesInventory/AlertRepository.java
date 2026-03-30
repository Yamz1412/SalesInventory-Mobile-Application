package com.app.SalesInventory;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AlertRepository {
    private static final String TAG = "AlertRepository";
    private static AlertRepository instance;
    private final FirestoreManager firestoreManager;
    private final FirestoreSyncListener syncListener;
    private final MutableLiveData<List<Alert>> allAlerts;
    private final MutableLiveData<List<Alert>> unreadAlerts;
    private final MutableLiveData<Integer> unreadAlertCount;
    private final Application application;

    private AlertRepository(Application application) {
        this.application = application;
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
                    // FIXED: Directly use map conversion to avoid "Timestamp to Long" warnings
                    Alert alert = createAlertFromMap(document.getData());
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
            allAlerts.postValue(alertList);
            unreadAlerts.postValue(unread);
            unreadAlertCount.postValue(unreadCount);
            Log.d(TAG, "Alerts synced from Firestore: " + alertList.size() + " (Unread: " + unreadCount + ")");
        });
    }

//    public void createOrUpdateStockAlert(String productId, String productName, String type, double currentQty) {
//        CollectionReference alertsRef = firestore.collection("alerts");
//        String businessId = getBusinessId(); // Ensure user is logged in
//
//        // Query for existing alert: Same business, product, and alert type (e.g., 'LOW_STOCK')
//        alertsRef.whereEqualTo("businessId", businessId)
//                .whereEqualTo("productId", productId)
//                .whereEqualTo("type", type)
//                .get()
//                .addOnSuccessListener(querySnapshot -> {
//                    String message = "Stock Alert: " + productName + " (" + currentQty + " remaining)";
//                    long timestamp = System.currentTimeMillis();
//
//                    if (!querySnapshot.isEmpty()) {
//                        // 1. UPDATE EXISTING ALERT
//                        String docId = querySnapshot.getDocuments().get(0).getId();
//                        Map<String, Object> updates = new HashMap<>();
//                        updates.put("message", message);
//                        updates.put("timestamp", timestamp);
//                        updates.put("isRead", false); // Optional: reset to unread on updates
//
//                        alertsRef.document(docId).update(updates);
//                    } else {
//                        // 2. CREATE NEW ALERT
//                        Map<String, Object> newAlert = new HashMap<>();
//                        newAlert.put("businessId", businessId);
//                        newAlert.put("productId", productId);
//                        newAlert.put("productName", productName);
//                        newAlert.put("type", type);
//                        newAlert.put("message", message);
//                        newAlert.put("timestamp", timestamp);
//                        newAlert.put("isRead", false);
//
//                        alertsRef.add(newAlert);
//                    }
//                });
//    }

    private Alert createAlertFromSnapshot(DocumentSnapshot document) {
        // We skip toObject() to prevent log spam about Timestamp conversion
        Alert alert = createAlertFromMap(document.getData());
        if (alert != null) {
            alert.setId(document.getId());
        }
        return alert;
    }

    private Alert createAlertFromMap(Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        Alert alert = new Alert();

        if (data.containsKey("id")) {
            alert.setId((String) data.get("id"));
        }

        if (data.containsKey("productId")) {
            alert.setProductId((String) data.get("productId"));
        }

        if (data.containsKey("type")) {
            alert.setType((String) data.get("type"));
        }

        if (data.containsKey("message")) {
            alert.setMessage((String) data.get("message"));
        }

        if (data.containsKey("read")) {
            Object readObj = data.get("read");
            alert.setRead(readObj instanceof Boolean ? (Boolean) readObj : false);
        }

        if (data.containsKey("timestamp")) {
            Object timestampObj = data.get("timestamp");
            long timestamp = 0L;

            if (timestampObj instanceof com.google.firebase.Timestamp) {
                com.google.firebase.Timestamp ts = (com.google.firebase.Timestamp) timestampObj;
                timestamp = ts.toDate().getTime();
            } else if (timestampObj instanceof java.util.Date) {
                timestamp = ((java.util.Date) timestampObj).getTime();
            } else if (timestampObj instanceof Number) {
                timestamp = ((Number) timestampObj).longValue();
            }

            alert.setTimestamp(timestamp);
        }

        if (data.containsKey("source")) {
            alert.setSource((String) data.get("source"));
        }

        if (data.containsKey("createdBy")) {
            alert.setCreatedBy((String) data.get("createdBy"));
        }

        return alert;
    }

    public LiveData<List<Alert>> getAllAlerts() { return allAlerts; }
    public LiveData<List<Alert>> getUnreadAlerts() { return unreadAlerts; }
    public LiveData<Integer> getUnreadAlertCount() { return unreadAlertCount; }

    public void addAlert(Alert alert, OnAlertAddedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }
        Map<String, Object> alertMap = convertAlertToMap(alert);
        String currentUserId = AuthManager.getInstance().getCurrentUserId();
        alertMap.put("createdBy", currentUserId != null ? currentUserId : "client");
        alertMap.put("source", "client");
        alertMap.put("createdAt", firestoreManager.getServerTimestamp());

        firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath())
                .add(alertMap)
                .addOnSuccessListener(documentReference -> {
                    String alertId = documentReference.getId();
                    alert.setId(alertId);
                    listener.onAlertAdded(alertId);
                    Log.d(TAG, "Alert added: " + alertId);

                    try {
                        String title = getTitleForType(alert.getType());
                        String body = alert.getMessage() != null ? alert.getMessage() : "";
                        NotificationHelper.showNotification(application, title, body, alertId);
                    } catch (Exception ex) {
                        Log.e(TAG, "Failed to show local notification", ex);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding alert", e);
                    listener.onError(e.getMessage());
                });
    }

    public void addAlertIfNotExists(String productId, String type, String message, long timestamp, OnAlertAddedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        // This query finds ANY existing alert for this product & type to UPDATE it instead of duplicating
        firestoreManager.getDb()
                .collection(firestoreManager.getUserAlertsPath())
                .whereEqualTo("productId", productId)
                .whereEqualTo("type", type)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && !snapshot.isEmpty()) {
                        // UPDATE EXISTING (Prevents Duplication!)
                        String docId = snapshot.getDocuments().get(0).getId();
                        java.util.Map<String, Object> updates = new java.util.HashMap<>();
                        updates.put("message", message);
                        updates.put("timestamp", timestamp);
                        updates.put("read", false); // Bring it back to unread status so it alerts the user again

                        firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath())
                                .document(docId).update(updates)
                                .addOnSuccessListener(aVoid -> listener.onAlertAdded(docId));
                    } else {
                        // CREATE NEW ONLY IF IT DOESN'T EXIST YET
                        Alert alert = new Alert();
                        alert.setProductId(productId);
                        alert.setType(type);
                        alert.setMessage(message);
                        alert.setRead(false);
                        alert.setTimestamp(timestamp);
                        addAlert(alert, listener);
                    }
                })
                .addOnFailureListener(e -> listener.onError(e.getMessage()));
    }

    public void markAlertAsRead(String alertId, OnAlertUpdatedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("read", true);
        firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).document(alertId).update(updates)
                .addOnSuccessListener(aVoid -> {
                    if (listener != null) listener.onAlertUpdated();
                    Log.d(TAG, "Alert marked as read: " + alertId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error marking alert as read", e);
                    if (listener != null) listener.onError(e.getMessage());
                });
    }

    public void markAllAlertsAsRead(OnBatchUpdatedListener listener) {
        if (!firestoremanagerIsReady()) {
            listener.onError("User not authenticated");
            return;
        }
        firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).whereEqualTo("read", false).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.isEmpty()) {
                        listener.onBatchUpdated(0);
                        return;
                    }
                    WriteBatch batch = firestoreManager.getDb().batch();
                    int count = 0;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        batch.update(doc.getReference(), "read", true);
                        count++;
                    }
                    batch.commit().addOnSuccessListener(aVoid -> {
                        listener.onBatchUpdated(snapshot.size());
                        Log.d(TAG, "Marked all alerts as read");
                    }).addOnFailureListener(e -> listener.onError(e.getMessage()));
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error marking all alerts as read", e);
                    listener.onError(e.getMessage());
                });
    }

    public void deleteAlert(String alertId) {
        if (!firestoremanagerIsReady() || alertId == null) return;
        firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).document(alertId)
                .delete()
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Alert deleted"))
                .addOnFailureListener(e -> Log.e(TAG, "Error deleting alert", e));
    }

    public void clearAllAlerts() {
        if (!firestoremanagerIsReady()) return;

        // FIXED: Use the exact same path helper that is used for fetching/adding alerts
        // This fixes the bug where alerts wouldn't clear because we were deleting from the wrong path
        firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        WriteBatch batch = firestoreManager.getDb().batch();
                        int count = 0;
                        for (DocumentSnapshot doc : task.getResult()) {
                            batch.delete(doc.getReference());
                            count++;
                            // Firestore batch limit is 500
                            if (count >= 400) {
                                batch.commit();
                                batch = firestoreManager.getDb().batch();
                                count = 0;
                            }
                        }
                        if (count > 0) {
                            batch.commit();
                        }
                        Log.d(TAG, "All alerts cleared from path: " + firestoreManager.getUserAlertsPath());
                    } else {
                        Log.e(TAG, "Failed to fetch alerts for deletion: " + task.getException());
                    }
                });
    }

    public void getAlertsByType(String type, OnAlertsFetchedListener listener) {
        if (!firestoremanagerIsReady()) {
            listener.onError("User not authenticated");
            return;
        }
        firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).whereEqualTo("type", type).get()
                .addOnSuccessListener(snapshot -> {
                    List<Alert> alerts = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Alert alert = createAlertFromSnapshot(doc);
                        if (alert != null) {
                            alert.setId(doc.getId());
                            alerts.add(alert);
                        }
                    }
                    listener.onAlertsFetched(alerts);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching alerts by type", e);
                    listener.onError(e.getMessage());
                });
    }

    public void getAlertsByProduct(String productId, OnAlertsFetchedListener listener) {
        if (!firestoremanagerIsReady()) {
            listener.onError("User not authenticated");
            return;
        }
        firestoreManager.getDb().collection(firestoreManager.getUserAlertsPath()).whereEqualTo("productId", productId).get()
                .addOnSuccessListener(snapshot -> {
                    List<Alert> alerts = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Alert alert = createAlertFromSnapshot(doc);
                        if (alert != null) {
                            alert.setId(doc.getId());
                            alerts.add(alert);
                        }
                    }
                    listener.onAlertsFetched(alerts);
                })
                .addOnFailureListener(e -> {
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
        map.put("timestamp", alert.getTimestamp());
        return map;
    }

    private String getTitleForType(String type) {
        if (type == null) return "Alert";
        switch (type) {
            case "LOW_STOCK": return "Low Stock";
            case "CRITICAL_STOCK": return "Critical Stock";
            case "FLOOR_STOCK": return "Floor Level";
            case "EXPIRY_7_DAYS":
            case "EXPIRY_3_DAYS":
            case "EXPIRED": return "Expiry Alert";
            case "PO_RECEIVED": return "Purchase Order Received";
            case "PO_PENDING": return "Purchase Order Pending";
            case "PO_CANCELLED": return "Purchase Order Cancelled";
            default: return "Alert";
        }
    }

    private boolean firestoremanagerIsReady() {
        return firestoreManager != null && firestoreManager.isUserAuthenticated();
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