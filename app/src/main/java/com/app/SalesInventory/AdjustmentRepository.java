package com.app.SalesInventory;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.app.SalesInventory.FirestoreManager;
import com.app.SalesInventory.FirestoreSyncListener;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdjustmentRepository {
    private static final String TAG = "AdjustmentRepository";
    private static AdjustmentRepository instance;

    private FirestoreManager firestoreManager;
    private FirestoreSyncListener syncListener;
    private MutableLiveData<List<Adjustment>> allAdjustments;

    private AdjustmentRepository(Application application) {
        this.firestoreManager = FirestoreManager.getInstance();
        this.syncListener = FirestoreSyncListener.getInstance();
        this.allAdjustments = new MutableLiveData<>();

        // Start listening to adjustments from Firestore
        startRealtimeSync();
    }

    public static synchronized AdjustmentRepository getInstance(Application application) {
        if (instance == null) {
            instance = new AdjustmentRepository(application);
        }
        return instance;
    }

    /**
     * Start real-time sync with Firestore
     */
    private void startRealtimeSync() {
        if (!firestoreManager.isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated. Cannot start sync.");
            return;
        }

        syncListener.listenToAdjustments(new FirestoreSyncListener.OnAdjustmentsChangedListener() {
            @Override
            public void onAdjustmentsChanged(QuerySnapshot snapshot) {
                List<Adjustment> adjustmentList = new ArrayList<>();

                if (snapshot != null) {
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        Adjustment adjustment = document.toObject(Adjustment.class);
                        if (adjustment != null) {
                            adjustment.setId(document.getId());
                            adjustmentList.add(adjustment);
                        }
                    }
                }

                allAdjustments.setValue(adjustmentList);
                Log.d(TAG, "Adjustments synced from Firestore: " + adjustmentList.size());
            }
        });
    }

    /**
     * Get all adjustments (LiveData)
     */
    public LiveData<List<Adjustment>> getAllAdjustments() {
        return allAdjustments;
    }

    /**
     * Add new adjustment
     */
    public void addAdjustment(Adjustment adjustment, OnAdjustmentAddedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        Map<String, Object> adjustmentMap = convertAdjustmentToMap(adjustment);
        adjustmentMap.put("timestamp", firestoreManager.getServerTimestamp());

        firestoreManager.getDb()
                .collection(firestoreManager.getUserAdjustmentsPath())
                .add(adjustmentMap)
                .addOnSuccessListener(documentReference -> {
                    String adjustmentId = documentReference.getId();
                    adjustment.setId(adjustmentId);
                    listener.onAdjustmentAdded(adjustmentId);
                    Log.d(TAG, "Adjustment added: " + adjustmentId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding adjustment", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Update adjustment
     */
    public void updateAdjustment(Adjustment adjustment, OnAdjustmentUpdatedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        if (adjustment.getId() == null || adjustment.getId().isEmpty()) {
            listener.onError("Adjustment ID is empty");
            return;
        }

        Map<String, Object> adjustmentMap = convertAdjustmentToMap(adjustment);

        firestoreManager.getDb()
                .collection(firestoreManager.getUserAdjustmentsPath())
                .document(adjustment.getId())
                .update(adjustmentMap)
                .addOnSuccessListener(aVoid -> {
                    listener.onAdjustmentUpdated();
                    Log.d(TAG, "Adjustment updated: " + adjustment.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating adjustment", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Delete adjustment
     */
    public void deleteAdjustment(String adjustmentId, OnAdjustmentDeletedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        firestoreManager.getDb()
                .collection(firestoreManager.getUserAdjustmentsPath())
                .document(adjustmentId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    listener.onAdjustmentDeleted();
                    Log.d(TAG, "Adjustment deleted: " + adjustmentId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting adjustment", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Get adjustments by product
     */
    public void getAdjustmentsByProduct(String productId, OnAdjustmentsFetchedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        firestoreManager.getDb()
                .collection(firestoreManager.getUserAdjustmentsPath())
                .whereEqualTo("productId", productId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Adjustment> adjustments = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Adjustment adjustment = doc.toObject(Adjustment.class);
                        if (adjustment != null) {
                            adjustment.setId(doc.getId());
                            adjustments.add(adjustment);
                        }
                    }
                    listener.onAdjustmentsFetched(adjustments);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching adjustments by product", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Get adjustments by type
     */
    public void getAdjustmentsByType(String type, OnAdjustmentsFetchedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        firestoreManager.getDb()
                .collection(firestoreManager.getUserAdjustmentsPath())
                .whereEqualTo("type", type)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Adjustment> adjustments = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Adjustment adjustment = doc.toObject(Adjustment.class);
                        if (adjustment != null) {
                            adjustment.setId(doc.getId());
                            adjustments.add(adjustment);
                        }
                    }
                    listener.onAdjustmentsFetched(adjustments);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching adjustments by type", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Convert Adjustment to Map
     */
    private Map<String, Object> convertAdjustmentToMap(Adjustment adjustment) {
        Map<String, Object> map = new HashMap<>();
        map.put("productId", adjustment.getProductId());
        map.put("quantity", adjustment.getQuantity());
        map.put("type", adjustment.getType());
        map.put("reason", adjustment.getReason());
        map.put("date", adjustment.getDate());
        return map;
    }

    // Callback interfaces
    public interface OnAdjustmentsFetchedListener {
        void onAdjustmentsFetched(List<Adjustment> adjustments);
        void onError(String error);
    }

    public interface OnAdjustmentAddedListener {
        void onAdjustmentAdded(String adjustmentId);
        void onError(String error);
    }

    public interface OnAdjustmentUpdatedListener {
        void onAdjustmentUpdated();
        void onError(String error);
    }

    public interface OnAdjustmentDeletedListener {
        void onAdjustmentDeleted();
        void onError(String error);
    }
}