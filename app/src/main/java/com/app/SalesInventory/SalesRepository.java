package com.app.SalesInventory;

import androidx.lifecycle.MutableLiveData;
import com.google.firebase.firestore. DocumentReference;
import com.google. firebase.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.app.Application;

public class SalesRepository {
    private static SalesRepository instance;
    private FirestoreManager firestoreManager;
    private MutableLiveData<List<Sales>> allSales;
    private MutableLiveData<Double> totalSalesToday;
    private MutableLiveData<Double> totalMonthlyRevenue;
    private MutableLiveData<List<Sales>> recentSales;

    private SalesRepository() {
        firestoreManager = FirestoreManager.getInstance();
        allSales = new MutableLiveData<>();
        totalSalesToday = new MutableLiveData<>(0.0);
        totalMonthlyRevenue = new MutableLiveData<>(0.0);
        recentSales = new MutableLiveData<>();
        loadAllSales();
        loadTodaySales();
        loadMonthlySales();
        loadRecentSales();
    }

    private SalesRepository(Application application) {
        this();
    }

    public static synchronized SalesRepository getInstance() {
        if (instance == null) {
            instance = new SalesRepository();
        }
        return instance;
    }

    public static synchronized SalesRepository getInstance(Application application) {
        if (instance == null) {
            instance = new SalesRepository(application);
        }
        return instance;
    }

    private void loadAllSales() {
        firestoreManager.getDb().collection(firestoreManager.getUserSalesPath()). orderBy("timestamp", com.google.firebase.firestore. Query.Direction.DESCENDING). addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                return;
            }
            if (snapshot != null) {
                List<Sales> salesList = new ArrayList<>();
                for (com.google.firebase.firestore. DocumentSnapshot document : snapshot.getDocuments()) {
                    Sales sale = document. toObject(Sales.class);
                    if (sale != null) {
                        sale.setId(document.getId());
                        salesList.add(sale);
                    }
                }
                allSales.postValue(salesList);
            }
        });
    }

    private void loadTodaySales() {
        long startOfDay = getStartOfDay();
        long endOfDay = getEndOfDay();
        firestoreManager.getDb().collection(firestoreManager.getUserSalesPath()).whereGreaterThanOrEqualTo("timestamp", startOfDay).whereLessThanOrEqualTo("timestamp", endOfDay).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                return;
            }
            double total = 0.0;
            if (snapshot != null) {
                for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                    Sales sale = document.toObject(Sales.class);
                    if (sale != null) {
                        total += sale.getTotalPrice();
                    }
                }
            }
            totalSalesToday.postValue(total);
        });
    }

    private void loadMonthlySales() {
        long startOfMonth = getStartOfMonth();
        long endOfMonth = getEndOfMonth();
        firestoreManager.getDb().collection(firestoreManager.getUserSalesPath()).whereGreaterThanOrEqualTo("timestamp", startOfMonth).whereLessThanOrEqualTo("timestamp", endOfMonth).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                return;
            }
            double total = 0.0;
            if (snapshot != null) {
                for (com.google.firebase.firestore.DocumentSnapshot document : snapshot. getDocuments()) {
                    Sales sale = document.toObject(Sales.class);
                    if (sale != null) {
                        total += sale.getTotalPrice();
                    }
                }
            }
            totalMonthlyRevenue.postValue(total);
        });
    }

    private void loadRecentSales() {
        firestoreManager.getDb().collection(firestoreManager.getUserSalesPath()).orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING).limit(10).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                return;
            }
            if (snapshot != null) {
                List<Sales> salesList = new ArrayList<>();
                for (com.google.firebase. firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                    Sales sale = document.toObject(Sales.class);
                    if (sale != null) {
                        sale.setId(document.getId());
                        salesList.add(sale);
                    }
                }
                recentSales.postValue(salesList);
            }
        });
    }

    public MutableLiveData<List<Sales>> getAllSales() {
        return allSales;
    }

    public MutableLiveData<Double> getTotalSalesToday() {
        return totalSalesToday;
    }

    public MutableLiveData<Double> getTotalMonthlyRevenue() {
        return totalMonthlyRevenue;
    }

    public MutableLiveData<List<Sales>> getRecentSales() {
        return recentSales;
    }

    public interface OnSaleAddedListener {
        void onSaleAdded(String saleId);
        void onError(String error);
    }

    public interface OnSalesDeletedListener {
        void onSaleDeleted();
        void onError(String error);
    }

    public void addSale(Sales sale, OnSaleAddedListener listener) {
        if (! AuthManager.getInstance().isCurrentUserApproved()) {
            listener. onError("User not approved");
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("productId", sale.getProductId());
        map.put("productName", sale.getProductName());
        map.put("quantity", sale.getQuantity());
        map.put("price", sale.getPrice());
        map. put("totalPrice", sale.getTotalPrice());
        map.put("paymentMethod", sale.getPaymentMethod() != null ? sale.getPaymentMethod() : "");
        map.put("paymentReference", sale.getPaymentReference() != null ? sale.getPaymentReference() : "");
        map.put("date", sale.getDate() > 0 ? sale.getDate() : System.currentTimeMillis());
        map.put("timestamp", firestoreManager.getServerTimestamp());
        firestoreManager.getDb().collection(firestoreManager.getUserSalesPath()).add(map). addOnSuccessListener((DocumentReference documentReference) -> {
            String saleId = documentReference.getId();
            sale.setId(saleId);
            listener.onSaleAdded(saleId);
        }).addOnFailureListener(e -> {
            listener.onError(e.getMessage() != null ? e.getMessage() : "Failed to add sale");
        });
    }

    public void deleteSale(String saleId, OnSalesDeletedListener listener) {
        firestoreManager.getDb().collection(firestoreManager.getUserSalesPath()).document(saleId).delete().addOnSuccessListener(aVoid -> {
            listener. onSaleDeleted();
        }).addOnFailureListener(e -> {
            listener.onError(e.getMessage() != null ?  e.getMessage() : "Failed to delete sale");
        });
    }

    private long getStartOfDay() {
        java.util.Calendar calendar = java.util.Calendar. getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar. SECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getEndOfDay() {
        java.util.Calendar calendar = java. util.Calendar.getInstance();
        calendar.set(java.util. Calendar.HOUR_OF_DAY, 23);
        calendar.set(java.util.Calendar.MINUTE, 59);
        calendar.set(java.util.Calendar.SECOND, 59);
        return calendar.getTimeInMillis();
    }

    private long getStartOfMonth() {
        java.util.Calendar calendar = java.util.Calendar. getInstance();
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar. SECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getEndOfMonth() {
        java.util.Calendar calendar = java. util.Calendar.getInstance();
        calendar.set(java.util. Calendar.DAY_OF_MONTH, calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH));
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23);
        calendar.set(java. util.Calendar.MINUTE, 59);
        calendar.set(java.util.Calendar.SECOND, 59);
        return calendar.getTimeInMillis();
    }
}