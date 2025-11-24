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

public class SalesRepository {
    private static final String TAG = "SalesRepository";
    private static SalesRepository instance;

    private FirestoreManager firestoreManager;
    private FirestoreSyncListener syncListener;
    private MutableLiveData<List<Sales>> allSales;

    private SalesRepository(Application application) {
        this.firestoreManager = FirestoreManager.getInstance();
        this.syncListener = FirestoreSyncListener.getInstance();
        this.allSales = new MutableLiveData<>();

        // Start listening to sales from Firestore
        startRealtimeSync();
    }

    public static synchronized SalesRepository getInstance(Application application) {
        if (instance == null) {
            instance = new SalesRepository(application);
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

        syncListener.listenToSales(new FirestoreSyncListener.OnSalesChangedListener() {
            @Override
            public void onSalesChanged(QuerySnapshot snapshot) {
                List<Sales> salesList = new ArrayList<>();

                if (snapshot != null) {
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        Sales sale = document.toObject(Sales.class);
                        if (sale != null) {
                            sale.setId(document.getId());
                            salesList.add(sale);
                        }
                    }
                }

                allSales.setValue(salesList);
                Log.d(TAG, "Sales synced from Firestore: " + salesList.size());
            }
        });
    }

    /**
     * Get all sales (LiveData)
     */
    public LiveData<List<Sales>> getAllSales() {
        return allSales;
    }

    /**
     * Add new sale
     */
    public void addSale(Sales sale, OnSaleAddedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        Map<String, Object> saleMap = convertSaleToMap(sale);
        saleMap.put("timestamp", firestoreManager.getServerTimestamp());

        firestoreManager.getDb()
                .collection(firestoreManager.getUserSalesPath())
                .add(saleMap)
                .addOnSuccessListener(documentReference -> {
                    String saleId = documentReference.getId();
                    sale.setId(saleId);
                    listener.onSaleAdded(saleId);
                    Log.d(TAG, "Sale added: " + saleId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding sale", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Update sale
     */
    public void updateSale(Sales sale, OnSaleUpdatedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        if (sale.getId() == null || sale.getId().isEmpty()) {
            listener.onError("Sale ID is empty");
            return;
        }

        Map<String, Object> saleMap = convertSaleToMap(sale);

        firestoreManager.getDb()
                .collection(firestoreManager.getUserSalesPath())
                .document(sale.getId())
                .update(saleMap)
                .addOnSuccessListener(aVoid -> {
                    listener.onSaleUpdated();
                    Log.d(TAG, "Sale updated: " + sale.getId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating sale", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Delete sale
     */
    public void deleteSale(String saleId, OnSaleDeletedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        firestoreManager.getDb()
                .collection(firestoreManager.getUserSalesPath())
                .document(saleId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    listener.onSaleDeleted();
                    Log.d(TAG, "Sale deleted: " + saleId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting sale", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Get sales by product
     */
    public void getSalesByProduct(String productId, OnSalesFetchedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        firestoreManager.getDb()
                .collection(firestoreManager.getUserSalesPath())
                .whereEqualTo("productId", productId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Sales> sales = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Sales sale = doc.toObject(Sales.class);
                        if (sale != null) {
                            sale.setId(doc.getId());
                            sales.add(sale);
                        }
                    }
                    listener.onSalesFetched(sales);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching sales by product", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Get sales by date range
     */
    public void getSalesByDateRange(long startDate, long endDate, OnSalesFetchedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        firestoreManager.getDb()
                .collection(firestoreManager.getUserSalesPath())
                .whereGreaterThanOrEqualTo("date", startDate)
                .whereLessThanOrEqualTo("date", endDate)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Sales> sales = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Sales sale = doc.toObject(Sales.class);
                        if (sale != null) {
                            sale.setId(doc.getId());
                            sales.add(sale);
                        }
                    }
                    listener.onSalesFetched(sales);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching sales by date range", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Convert Sale object to Map
     */
    private Map<String, Object> convertSaleToMap(Sales sale) {
        Map<String, Object> map = new HashMap<>();
        map.put("productId", sale.getProductId());
        map.put("quantity", sale.getQuantity());
        map.put("price", sale.getTotalPrice());
        map.put("date", sale.getDate());
        map.put("paymentMethod", sale.getPaymentMethod());
        return map;
    }

    // Callback interfaces
    public interface OnSalesFetchedListener {
        void onSalesFetched(List<Sales> sales);
        void onError(String error);
    }

    public interface OnSaleAddedListener {
        void onSaleAdded(String saleId);
        void onError(String error);
    }

    public interface OnSaleUpdatedListener {
        void onSaleUpdated();
        void onError(String error);
    }

    public interface OnSaleDeletedListener {
        void onSaleDeleted();
        void onError(String error);
    }
}