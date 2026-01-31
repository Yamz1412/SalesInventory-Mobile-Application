package com.app.SalesInventory;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SalesRepository {
    private static final String TAG = "SalesRepository";
    private static SalesRepository instance;
    private FirestoreManager firestoreManager;
    private MutableLiveData<List<Sales>> allSales;
    private MutableLiveData<Double> totalSalesToday;
    private MutableLiveData<Double> totalMonthlyRevenue;
    private MutableLiveData<List<Sales>> recentSales;
    private Application application;

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
        this.application = application;
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

    private Sales createSalesFromSnapshot(DocumentSnapshot document) {
        try {
            Sales sale = document.toObject(Sales.class);
            return sale;
        } catch (Exception e) {
            Log.w(TAG, "Failed to deserialize sales with toObject(), trying manual mapping: " + e.getMessage());
            return createSalesFromMap(document.getData());
        }
    }

    private Sales createSalesFromMap(Map<String, Object> data) {
        if (data == null) {
            return null;
        }

        Sales sale = new Sales();

        if (data.containsKey("id")) {
            sale.setId((String) data.get("id"));
        }

        if (data.containsKey("orderId")) {
            sale.setOrderId((String) data.get("orderId"));
        }

        if (data.containsKey("productId")) {
            sale.setProductId((String) data.get("productId"));
        }

        if (data.containsKey("productName")) {
            sale.setProductName((String) data.get("productName"));
        }

        if (data.containsKey("quantity")) {
            Object qty = data.get("quantity");
            if (qty instanceof Number) {
                sale.setQuantity(((Number) qty).intValue());
            }
        }

        if (data.containsKey("price")) {
            Object price = data.get("price");
            if (price instanceof Number) {
                sale.setPrice(((Number) price).doubleValue());
            }
        }

        if (data.containsKey("totalPrice")) {
            Object totalPrice = data.get("totalPrice");
            if (totalPrice instanceof Number) {
                sale.setTotalPrice(((Number) totalPrice).doubleValue());
            }
        }

        if (data.containsKey("paymentMethod")) {
            sale.setPaymentMethod((String) data.get("paymentMethod"));
        }

        if (data.containsKey("date")) {
            Object dateObj = data.get("date");
            long dateTime = 0L;
            if (dateObj instanceof com.google.firebase.Timestamp) {
                dateTime = ((com.google.firebase.Timestamp) dateObj).toDate().getTime();
            } else if (dateObj instanceof java.util.Date) {
                dateTime = ((java.util.Date) dateObj).getTime();
            } else if (dateObj instanceof Number) {
                dateTime = ((Number) dateObj).longValue();
            }
            sale.setDate(dateTime);
        }

        if (data.containsKey("timestamp")) {
            Object tsObj = data.get("timestamp");
            long timestamp = 0L;
            if (tsObj instanceof com.google.firebase.Timestamp) {
                timestamp = ((com.google.firebase.Timestamp) tsObj).toDate().getTime();
            } else if (tsObj instanceof java.util.Date) {
                timestamp = ((java.util.Date) tsObj).getTime();
            } else if (tsObj instanceof Number) {
                timestamp = ((Number) tsObj).longValue();
            }
            sale.setTimestamp(timestamp);
        }

        if (data.containsKey("deliveryType")) {
            sale.setDeliveryType((String) data.get("deliveryType"));
        }

        if (data.containsKey("deliveryStatus")) {
            sale.setDeliveryStatus((String) data.get("deliveryStatus"));
        }

        if (data.containsKey("deliveryDate")) {
            Object deliveryDateObj = data.get("deliveryDate");
            long deliveryDateTime = 0L;
            if (deliveryDateObj instanceof com.google.firebase.Timestamp) {
                deliveryDateTime = ((com.google.firebase.Timestamp) deliveryDateObj).toDate().getTime();
            } else if (deliveryDateObj instanceof java.util.Date) {
                deliveryDateTime = ((java.util.Date) deliveryDateObj).getTime();
            } else if (deliveryDateObj instanceof Number) {
                deliveryDateTime = ((Number) deliveryDateObj).longValue();
            }
            sale.setDeliveryDate(deliveryDateTime);
        }

        if (data.containsKey("deliveryName")) {
            sale.setDeliveryName((String) data.get("deliveryName"));
        }

        if (data.containsKey("deliveryPhone")) {
            sale.setDeliveryPhone((String) data.get("deliveryPhone"));
        }

        if (data.containsKey("deliveryAddress")) {
            sale.setDeliveryAddress((String) data.get("deliveryAddress"));
        }

        if (data.containsKey("deliveryPaymentMethod")) {
            sale.setDeliveryPaymentMethod((String) data.get("deliveryPaymentMethod"));
        }

        return sale;
    }

    private void loadAllSales() {
        firestoreManager.getDb().collection(firestoreManager.getUserSalesPath())
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error loading all sales", error);
                        return;
                    }
                    if (snapshot != null) {
                        List<Sales> salesList = new ArrayList<>();
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            try {
                                Sales sale = createSalesFromSnapshot(document);
                                if (sale != null) {
                                    sale.setId(document.getId());
                                    salesList.add(sale);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error deserializing sales document: " + document.getId(), e);
                            }
                        }
                        allSales.postValue(salesList);
                    }
                });
    }

    private void loadTodaySales() {
        long startOfDay = getStartOfDay();
        long endOfDay = getEndOfDay();
        firestoreManager.getDb().collection(firestoreManager.getUserSalesPath())
                .whereGreaterThanOrEqualTo("timestamp", startOfDay)
                .whereLessThanOrEqualTo("timestamp", endOfDay)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error loading today sales", error);
                        return;
                    }
                    double total = 0.0;
                    if (snapshot != null) {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            try {
                                Sales sale = createSalesFromSnapshot(document);
                                if (sale != null) {
                                    total += sale.getTotalPrice();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error deserializing sales document: " + document.getId(), e);
                            }
                        }
                    }
                    totalSalesToday.postValue(total);
                });
    }

    private void loadMonthlySales() {
        long startOfMonth = getStartOfMonth();
        long endOfMonth = getEndOfMonth();
        firestoreManager.getDb().collection(firestoreManager.getUserSalesPath())
                .whereGreaterThanOrEqualTo("timestamp", startOfMonth)
                .whereLessThanOrEqualTo("timestamp", endOfMonth)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error loading monthly sales", error);
                        return;
                    }
                    double total = 0.0;
                    if (snapshot != null) {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            try {
                                Sales sale = createSalesFromSnapshot(document);
                                if (sale != null) {
                                    total += sale.getTotalPrice();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error deserializing sales document: " + document.getId(), e);
                            }
                        }
                    }
                    totalMonthlyRevenue.postValue(total);
                });
    }

    private void loadRecentSales() {
        firestoreManager.getDb().collection(firestoreManager.getUserSalesPath())
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(10)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "Error loading recent sales", error);
                        return;
                    }
                    if (snapshot != null) {
                        List<Sales> salesList = new ArrayList<>();
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            try {
                                Sales sale = createSalesFromSnapshot(document);
                                if (sale != null) {
                                    sale.setId(document.getId());
                                    salesList.add(sale);
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "Error deserializing sales document: " + document.getId(), e);
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
        if (!AuthManager.getInstance().isCurrentUserApproved()) {
            listener.onError("User not approved");
            return;
        }
        long now = System.currentTimeMillis();
        long date = sale.getDate() > 0 ? sale.getDate() : now;
        long ts = sale.getTimestamp() > 0 ? sale.getTimestamp() : date;

        Map<String, Object> map = new HashMap<>();
        map.put("orderId", sale.getOrderId() != null ? sale.getOrderId() : "");
        map.put("productId", sale.getProductId());
        map.put("productName", sale.getProductName());
        map.put("quantity", sale.getQuantity());
        map.put("price", sale.getPrice());
        map.put("totalPrice", sale.getTotalPrice());
        map.put("paymentMethod", sale.getPaymentMethod() != null ? sale.getPaymentMethod() : "");
        map.put("deliveryType", sale.getDeliveryType() != null ? sale.getDeliveryType() : "");
        map.put("deliveryStatus", sale.getDeliveryStatus() != null ? sale.getDeliveryStatus() : "");
        map.put("deliveryDate", sale.getDeliveryDate() > 0 ? sale.getDeliveryDate() : 0L);
        map.put("deliveryName", sale.getDeliveryName() != null ? sale.getDeliveryName() : "");
        map.put("deliveryPhone", sale.getDeliveryPhone() != null ? sale.getDeliveryPhone() : "");
        map.put("deliveryAddress", sale.getDeliveryAddress() != null ? sale.getDeliveryAddress() : "");
        map.put("deliveryPaymentMethod", sale.getDeliveryPaymentMethod() != null ? sale.getDeliveryPaymentMethod() : "");
        map.put("date", date);
        map.put("timestamp", ts);

        firestoreManager.getDb().collection(firestoreManager.getUserSalesPath()).add(map)
                .addOnSuccessListener((DocumentReference documentReference) -> {
                    String saleId = documentReference.getId();
                    sale.setId(saleId);
                    listener.onSaleAdded(saleId);
                }).addOnFailureListener(e -> {
                    listener.onError(e.getMessage() != null ? e.getMessage() : "Failed to add sale");
                });
    }

    public void deleteSale(String saleId, OnSalesDeletedListener listener) {
        firestoreManager.getDb().collection(firestoreManager.getUserSalesPath())
                .document(saleId)
                .delete()
                .addOnSuccessListener(aVoid -> listener.onSaleDeleted())
                .addOnFailureListener(e -> listener.onError(e.getMessage() != null ? e.getMessage() : "Failed to delete sale"));
    }

    public void updateSaleDeliveryStatus(Sales sale) {
        if (sale == null || sale.getId() == null) return;
        Map<String, Object> updates = new HashMap<>();
        updates.put("deliveryStatus", sale.getDeliveryStatus());
        updates.put("deliveryDate", sale.getDeliveryDate());
        updates.put("deliveryType", sale.getDeliveryType());
        updates.put("deliveryName", sale.getDeliveryName());
        updates.put("deliveryPhone", sale.getDeliveryPhone());
        updates.put("deliveryAddress", sale.getDeliveryAddress());
        updates.put("deliveryPaymentMethod", sale.getDeliveryPaymentMethod());
        firestoreManager.getDb().collection(firestoreManager.getUserSalesPath())
                .document(sale.getId())
                .update(updates);
    }

    private long getStartOfDay() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getEndOfDay() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23);
        calendar.set(java.util.Calendar.MINUTE, 59);
        calendar.set(java.util.Calendar.SECOND, 59);
        return calendar.getTimeInMillis();
    }

    private long getStartOfMonth() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.DAY_OF_MONTH, 1);
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0);
        calendar.set(java.util.Calendar.MINUTE, 0);
        calendar.set(java.util.Calendar.SECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getEndOfMonth() {
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        calendar.set(java.util.Calendar.DAY_OF_MONTH, calendar.getActualMaximum(java.util.Calendar.DAY_OF_MONTH));
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 23);
        calendar.set(java.util.Calendar.MINUTE, 59);
        calendar.set(java.util.Calendar.SECOND, 59);
        return calendar.getTimeInMillis();
    }
}