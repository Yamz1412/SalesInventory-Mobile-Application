package com.app.SalesInventory;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.MutableLiveData;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SalesRepository {
    private static final String TAG = "SalesRepo";
    private static SalesRepository instance;
    private FirestoreManager firestoreManager;
    private MutableLiveData<List<Sales>> allSales;
    private MutableLiveData<Double> totalSalesToday;
    private MutableLiveData<Double> totalMonthlyRevenue;
    private MutableLiveData<List<Sales>> recentSales;
    private Application application;
    private boolean listenersStarted = false;
    private int startAttempts = 0;
    private static final int MAX_START_ATTEMPTS = 10;
    private static final long RETRY_MS = 1000L;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private ListenerRegistration allSalesListener;
    private ListenerRegistration recentSalesListener;

    private SalesRepository() {
        firestoreManager = FirestoreManager.getInstance();
        allSales = new MutableLiveData<>(new ArrayList<>());
        totalSalesToday = new MutableLiveData<>(0.0);
        totalMonthlyRevenue = new MutableLiveData<>(0.0);
        recentSales = new MutableLiveData<>(new ArrayList<>());
        startIfNeeded();
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

    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.stopListeners();
            instance = null;
        }
    }

    private void stopListeners() {
        listenersStarted = false;
        startAttempts = 0;
        if (allSalesListener != null) {
            allSalesListener.remove();
            allSalesListener = null;
        }
        if (recentSalesListener != null) {
            recentSalesListener.remove();
            recentSalesListener = null;
        }
        allSales.postValue(new ArrayList<>());
        totalSalesToday.postValue(0.0);
        totalMonthlyRevenue.postValue(0.0);
        recentSales.postValue(new ArrayList<>());
    }

    public synchronized void startIfNeeded() {
        if (listenersStarted) return;
        String path = firestoreManager.getUserSalesPath();
        if (path == null) {
            startAttempts++;
            if (startAttempts <= MAX_START_ATTEMPTS) {
                Log.w(TAG, "startIfNeeded: businessOwnerId not set yet, will retry attempt " + startAttempts);
                mainHandler.postDelayed(this::startIfNeeded, RETRY_MS);
            } else {
                Log.w(TAG, "startIfNeeded: giving up after " + startAttempts + " attempts");
            }
            return;
        }
        listenersStarted = true;
        loadAllSales();
        loadRecentSales();
    }

    private long extractTimestampMs(DocumentSnapshot document) {
        Object tsObj = document.get("timestamp");
        if (tsObj instanceof Timestamp) {
            return ((Timestamp) tsObj).toDate().getTime();
        }
        if (tsObj instanceof Number) {
            return ((Number) tsObj).longValue();
        }
        Object dateObj = document.get("date");
        if (dateObj instanceof Number) return ((Number) dateObj).longValue();
        Long d = document.getLong("date");
        if (d != null) return d;
        return System.currentTimeMillis();
    }

    private double extractDouble(DocumentSnapshot document, String key) {
        Object o = document.get(key);
        if (o instanceof Number) return ((Number) o).doubleValue();
        if (o instanceof String) {
            try { return Double.parseDouble((String) o); } catch (Exception ignored) {}
        }
        Double d = document.getDouble(key);
        if (d != null) return d;
        return 0.0;
    }

    private int extractInt(DocumentSnapshot document, String key) {
        Object o = document.get(key);
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) {
            try { return Integer.parseInt((String) o); } catch (Exception ignored) {}
        }
        Long l = document.getLong(key);
        if (l != null) return l.intValue();
        return 0;
    }

    private String extractString(DocumentSnapshot document, String key) {
        String s = document.getString(key);
        if (s != null) return s;
        Object o = document.get(key);
        return o == null ? "" : String.valueOf(o);
    }

    private void loadAllSales() {
        String path = firestoreManager.getUserSalesPath();
        if (path == null) {
            Log.w(TAG, "loadAllSales skipped: user sales path is null");
            return;
        }
        Log.d(TAG, "loadAllSales: starting listener on path=" + path);
        allSalesListener = firestoreManager.getDb().collection(path)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "loadAllSales listener error", error);
                        return;
                    }
                    if (snapshot == null) {
                        Log.w(TAG, "loadAllSales: snapshot is null");
                        return;
                    }

                    List<Sales> salesList = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        try {
                            Sales sale = new Sales();
                            sale.setId(document.getId());
                            sale.setOrderId(extractString(document, "orderId"));
                            sale.setProductId(extractString(document, "productId"));
                            sale.setProductName(extractString(document, "productName"));
                            sale.setQuantity(extractInt(document, "quantity"));
                            sale.setPrice(extractDouble(document, "price"));
                            sale.setTotalPrice(extractDouble(document, "totalPrice"));
                            sale.setPaymentMethod(extractString(document, "paymentMethod"));
                            long ts = extractTimestampMs(document);
                            sale.setTimestamp(ts);
                            Long dateLong = document.getLong("date");
                            sale.setDate(dateLong == null ? ts : dateLong);
                            sale.setDeliveryType(extractString(document, "deliveryType"));
                            sale.setDeliveryStatus(extractString(document, "deliveryStatus"));
                            sale.setDeliveryDate(document.getLong("deliveryDate") == null ? 0L : document.getLong("deliveryDate"));
                            sale.setDeliveryName(extractString(document, "deliveryName"));
                            sale.setDeliveryPhone(extractString(document, "deliveryPhone"));
                            sale.setDeliveryAddress(extractString(document, "deliveryAddress"));
                            sale.setDeliveryPaymentMethod(extractString(document, "deliveryPaymentMethod"));
                            salesList.add(sale);
                        } catch (Exception e) {
                            Log.w(TAG, "skip malformed sale doc " + document.getId(), e);
                        }
                    }

                    allSales.postValue(salesList);

                    long startDay = getStartOfDay();
                    long endDay = getEndOfDay();
                    long startMonth = getStartOfMonth();
                    long endMonth = getEndOfMonth();

                    Log.d(TAG, "Today range: " + startDay + " to " + endDay);
                    Log.d(TAG, "Month range: " + startMonth + " to " + endMonth);

                    double totalToday = 0.0;
                    double totalMonth = 0.0;
                    int todayCount = 0;
                    int monthCount = 0;

                    for (Sales s : salesList) {
                        if (s == null) continue;
                        long t = s.getTimestamp();
                        double price = s.getTotalPrice();

                        if (t >= startDay && t <= endDay) {
                            totalToday += price;
                            todayCount++;
                            Log.d(TAG, "Today sale: ts=" + t + " price=" + price + " product=" + s.getProductName());
                        }
                        if (t >= startMonth && t <= endMonth) {
                            totalMonth += price;
                            monthCount++;
                        }
                    }

                    totalSalesToday.postValue(totalToday);
                    totalMonthlyRevenue.postValue(totalMonth);
                    Log.d(TAG, "loadAllSales: total=" + salesList.size() + " today=" + todayCount + " (₱" + totalToday + ") month=" + monthCount + " (₱" + totalMonth + ")");
                });
    }

    private void loadRecentSales() {
        String path = firestoreManager.getUserSalesPath();
        if (path == null) {
            Log.w(TAG, "loadRecentSales skipped: user sales path is null");
            return;
        }
        recentSalesListener = firestoreManager.getDb().collection(path)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(10)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.e(TAG, "loadRecentSales listener error", error);
                        return;
                    }
                    if (snapshot == null) return;
                    List<Sales> salesList = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        try {
                            Sales sale = new Sales();
                            sale.setId(document.getId());
                            sale.setOrderId(extractString(document, "orderId"));
                            sale.setProductId(extractString(document, "productId"));
                            sale.setProductName(extractString(document, "productName"));
                            sale.setQuantity(extractInt(document, "quantity"));
                            sale.setPrice(extractDouble(document, "price"));
                            sale.setTotalPrice(extractDouble(document, "totalPrice"));
                            long ts = extractTimestampMs(document);
                            sale.setTimestamp(ts);
                            Long dateLong = document.getLong("date");
                            sale.setDate(dateLong == null ? ts : dateLong);
                            salesList.add(sale);
                        } catch (Exception e) {
                            Log.w(TAG, "skip malformed recent sale doc " + document.getId(), e);
                        }
                    }
                    recentSales.postValue(salesList);
                    Log.d(TAG, "loadRecentSales: docs=" + salesList.size());
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
        AuthManager auth = AuthManager.getInstance();
        Boolean cachedApproved = auth.getCachedIsApproved();
        if (cachedApproved != null) {
            if (!cachedApproved) {
                if (listener != null) listener.onError("User not approved");
                return;
            }
            performSaleWrite(sale, listener);
            return;
        }
        auth.refreshCurrentUserStatus(new AuthManager.SimpleCallback() {
            @Override
            public void onComplete(boolean success) {
                if (!auth.isCurrentUserApproved()) {
                    if (listener != null) listener.onError("User not approved");
                    return;
                }
                performSaleWrite(sale, listener);
            }
        });
    }

    private void performSaleWrite(Sales sale, OnSaleAddedListener listener) {
        long now = System.currentTimeMillis();
        long date = sale.getDate() > 0 ? sale.getDate() : now;
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
        map.put("timestamp", FieldValue.serverTimestamp());
        try {
            String path = firestoreManager.getUserSalesPath();
            if (path == null) {
                if (listener != null) listener.onError("Business owner ID not set");
                Log.e(TAG, "addSale failed: sales path not set");
                return;
            }
            Log.d(TAG, "addSale writing to path=" + path);
            firestoreManager.getDb().collection(path).add(map)
                    .addOnSuccessListener((DocumentReference documentReference) -> {
                        String saleId = documentReference.getId();
                        sale.setId(saleId);
                        if (listener != null) listener.onSaleAdded(saleId);
                        try {
                            ProductRepository pr = SalesInventoryApplication.getProductRepository();
                            if (pr != null) {
                                pr.getProductById(sale.getProductId(), new ProductRepository.OnProductFetchedListener() {
                                    @Override
                                    public void onProductFetched(Product product) {
                                        try {
                                            int before = product == null ? 0 : product.getQuantity();
                                            int after = Math.max(0, before - sale.getQuantity());
                                            String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
                                            if (ownerId == null) ownerId = AuthManager.getInstance().getCurrentUserId();
                                            if (ownerId != null) {
                                                String movId = FirebaseDatabase.getInstance().getReference().child("InventoryMovements").child(ownerId).child("items").push().getKey();
                                                Map<String, Object> movement = new HashMap<>();
                                                movement.put("movementId", movId);
                                                movement.put("productId", sale.getProductId());
                                                movement.put("productName", sale.getProductName());
                                                movement.put("change", -sale.getQuantity());
                                                movement.put("quantityBefore", before);
                                                movement.put("quantityAfter", after);
                                                movement.put("reason", "Sale");
                                                movement.put("type", "SALE");
                                                movement.put("timestamp", System.currentTimeMillis());
                                                FirebaseUser fu = FirebaseAuth.getInstance().getCurrentUser();
                                                if (fu != null) movement.put("performedBy", fu.getUid());
                                                FirebaseDatabase.getInstance().getReference().child("InventoryMovements").child(ownerId).child("items").child(movId).setValue(movement);
                                            }
                                        } catch (Exception ignored) {}
                                    }
                                    @Override
                                    public void onError(String error) {}
                                });
                            }
                        } catch (Exception ignored) {}
                        Log.d(TAG, "addSale success id=" + saleId + " path=" + path);
                    }).addOnFailureListener(e -> {
                        String msg = e.getMessage() != null ? e.getMessage() : "Failed to add sale";
                        if (listener != null) listener.onError(msg);
                        Log.e(TAG, "addSale failed path=" + path + " error=" + msg, e);
                    });
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Failed to add sale";
            if (listener != null) listener.onError(msg);
            Log.e(TAG, "addSale exception", e);
        }
    }

    public void deleteSale(String saleId, OnSalesDeletedListener listener) {
        String path = firestoreManager.getUserSalesPath();
        if (path == null) {
            listener.onError("Business owner ID not set");
            return;
        }
        firestoreManager.getDb().collection(path)
                .document(saleId)
                .delete()
                .addOnSuccessListener(aVoid -> listener.onSaleDeleted())
                .addOnFailureListener(e -> listener.onError(e.getMessage() != null ? e.getMessage() : "Failed to delete sale"));
    }

    public void updateSaleDeliveryStatus(Sales sale) {
        if (sale == null || sale.getId() == null) return;
        String path = firestoreManager.getUserSalesPath();
        if (path == null) {
            Log.w(TAG, "updateSaleDeliveryStatus skipped: businessOwnerId not set");
            return;
        }
        Map<String, Object> updates = new HashMap<>();
        updates.put("deliveryStatus", sale.getDeliveryStatus());
        updates.put("deliveryDate", sale.getDeliveryDate());
        updates.put("deliveryType", sale.getDeliveryType());
        updates.put("deliveryName", sale.getDeliveryName());
        updates.put("deliveryPhone", sale.getDeliveryPhone());
        updates.put("deliveryAddress", sale.getDeliveryAddress());
        updates.put("deliveryPaymentMethod", sale.getDeliveryPaymentMethod());
        firestoreManager.getDb().collection(path)
                .document(sale.getId())
                .update(updates);
    }

    private long getStartOfDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getEndOfDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    private long getStartOfMonth() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getEndOfMonth() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }
}