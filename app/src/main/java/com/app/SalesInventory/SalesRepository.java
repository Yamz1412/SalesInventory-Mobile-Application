package com.app.SalesInventory;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.MutableLiveData;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.MetadataChanges;
import com.google.firebase.firestore.Transaction;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;

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

    private ListenerRegistration allSalesListener;
    private ListenerRegistration todaySalesListener;
    private ListenerRegistration monthlyRevenueListener;
    private ListenerRegistration recentSalesListener;

    private Application application;

    private SalesRepository() {
        firestoreManager = FirestoreManager.getInstance();
        allSales = new MutableLiveData<>();
        totalSalesToday = new MutableLiveData<>(0.0);
        totalMonthlyRevenue = new MutableLiveData<>(0.0);
        recentSales = new MutableLiveData<>();

        loadAllSales();
        loadTodaySales();
        loadOverallRevenue();
        loadRecentSales();
    }

    private SalesRepository(Application application) {
        this();
        this.application = application;
    }

    public static synchronized SalesRepository getInstance() {
        if (instance == null) instance = new SalesRepository();
        return instance;
    }

    public static synchronized SalesRepository getInstance(Application application) {
        if (instance == null) instance = new SalesRepository(application);
        return instance;
    }

    public void clearData() {
        if (allSalesListener != null) { allSalesListener.remove(); allSalesListener = null; }
        if (todaySalesListener != null) { todaySalesListener.remove(); todaySalesListener = null; }
        if (monthlyRevenueListener != null) { monthlyRevenueListener.remove(); monthlyRevenueListener = null; }
        if (recentSalesListener != null) { recentSalesListener.remove(); recentSalesListener = null; }

        allSales.postValue(new ArrayList<>());
        totalSalesToday.postValue(0.0);
        totalMonthlyRevenue.postValue(0.0);
        recentSales.postValue(new ArrayList<>());
    }

    private void loadOverallRevenue() {
        if (monthlyRevenueListener != null) monthlyRevenueListener.remove();
        if (!firestoreManager.hasValidUser()) return;

        monthlyRevenueListener = firestoreManager.getDb().collection(firestoreManager.getUserSalesPath())
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (error != null) return;
                    double total = 0.0;
                    if (snapshot != null) {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            Sales sale = createSalesFromSnapshot(document);
                            if (sale != null) {
                                // CRITICAL FIX: Ignore Refunds and Voids
                                boolean isRefunded = "REFUNDED".equalsIgnoreCase(sale.getStatus()) ||
                                        "VOIDED".equalsIgnoreCase(sale.getStatus()) ||
                                        (sale.getPaymentMethod() != null && sale.getPaymentMethod().toUpperCase().contains("REFUNDED"));
                                if (!isRefunded) {
                                    total += sale.getTotalPrice();
                                }
                            }
                        }
                    }
                    totalMonthlyRevenue.postValue(total);
                });
    }

    public void getSalesByDateRange(long startTime, long endTime, MutableLiveData<List<Sales>> targetLiveData) {
        if (!firestoreManager.hasValidUser()) return; // FIX: Prevent "unknown" crash

        firestoreManager.getDb().collection(firestoreManager.getUserSalesPath())
                .whereGreaterThanOrEqualTo("timestamp", startTime)
                .whereLessThanOrEqualTo("timestamp", endTime)
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Sales> list = new ArrayList<>();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Sales s = createSalesFromSnapshot(doc);
                        if (s != null) list.add(s);
                    }
                    targetLiveData.postValue(list);
                });
    }

    private void loadAllSales() {
        if (allSalesListener != null) allSalesListener.remove();
        if (!firestoreManager.hasValidUser()) return;

        allSalesListener = firestoreManager.getDb().collection(firestoreManager.getUserSalesPath())
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
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
                                Log.e(TAG, "Error parsing sale document", e);
                            }
                        }
                        allSales.postValue(salesList);
                    }
                });
    }

    private void loadTodaySales() {
        long startOfDay = getStartOfDay();
        long endOfDay = getEndOfDay();
        if (todaySalesListener != null) todaySalesListener.remove();
        if (!firestoreManager.hasValidUser()) return;

        todaySalesListener = firestoreManager.getDb().collection(firestoreManager.getUserSalesPath())
                .whereGreaterThanOrEqualTo("timestamp", startOfDay)
                .whereLessThanOrEqualTo("timestamp", endOfDay)
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (error != null) return;
                    double total = 0.0;
                    if (snapshot != null) {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            try {
                                Sales sale = createSalesFromSnapshot(document);
                                if (sale != null) {
                                    // CRITICAL FIX: Ignore Refunds and Voids
                                    boolean isRefunded = "REFUNDED".equalsIgnoreCase(sale.getStatus()) ||
                                            "VOIDED".equalsIgnoreCase(sale.getStatus()) ||
                                            (sale.getPaymentMethod() != null && sale.getPaymentMethod().toUpperCase().contains("REFUNDED"));
                                    if (!isRefunded) {
                                        total += sale.getTotalPrice();
                                    }
                                }
                            } catch (Exception e) {}
                        }
                    }
                    totalSalesToday.postValue(total);
                });
    }

    private void loadMonthlySales() {}

    private void loadRecentSales() {
        if (recentSalesListener != null) recentSalesListener.remove();
        if (!firestoreManager.hasValidUser()) return; // FIX: Prevent "unknown" crash

        recentSalesListener = firestoreManager.getDb().collection(firestoreManager.getUserSalesPath())
                .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(10)
                .addSnapshotListener(MetadataChanges.INCLUDE, (snapshot, error) -> {
                    if (error != null) return;
                    if (snapshot != null) {
                        List<Sales> salesList = new ArrayList<>();
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            Sales sale = createSalesFromSnapshot(document);
                            if (sale != null) {
                                sale.setId(document.getId());
                                salesList.add(sale);
                            }
                        }
                        recentSales.postValue(salesList);
                    }
                });
    }

    public void reloadAllSales() { loadAllSales(); }
    public void reloadTodaySales() { loadTodaySales(); }
    public void reloadMonthlySales() { loadOverallRevenue(); }
    public void reloadRecentSales() { loadRecentSales(); }

    public MutableLiveData<List<Sales>> getAllSales() { return allSales; }
    public MutableLiveData<Double> getTotalSalesToday() { return totalSalesToday; }
    public MutableLiveData<Double> getTotalMonthlyRevenue() { return totalMonthlyRevenue; }
    public MutableLiveData<List<Sales>> getRecentSales() { return recentSales; }

    public interface OnSaleVoidedListener {
        void onSuccess();
        void onError(String error);
    }

    public void voidSale(Sales sale, OnSaleVoidedListener listener) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null) return;
        FirebaseFirestore db = FirestoreManager.getInstance().getDb();

        // 1. Update status (Firebase handles offline caching automatically)
        db.collection("users").document(ownerId).collection("sales").document(sale.getId()).update("status", "VOIDED");

        // 2. Restore Inventory Locally IMMEDIATELY
        ProductRepository pr = SalesInventoryApplication.getProductRepository();
        pr.getProductById(sale.getProductId(), new ProductRepository.OnProductFetchedListener() {
            @Override
            public void onProductFetched(Product p) {
                if (p != null) {
                    double newQty = p.getQuantity() + sale.getQuantity();
                    pr.updateProductQuantity(p.getProductId(), newQty, null);
                }
            }
            @Override public void onError(String error) {}
        });

        // 3. Refund Wallet Balance safely
        String walletId = (sale.getPaymentMethod() != null && sale.getPaymentMethod().toLowerCase().contains("gcash")) ? "GCASH" : "CASH";
        DocumentReference walletRef = db.collection("users").document(ownerId).collection("wallets").document(walletId);

        Map<String, Object> walletUpdates = new HashMap<>();
        walletUpdates.put("balance", com.google.firebase.firestore.FieldValue.increment(-sale.getTotalPrice()));
        walletRef.set(walletUpdates, com.google.firebase.firestore.SetOptions.merge());

        // 4. INSTANT UI UPDATE
        if (listener != null) listener.onSuccess();
    }

    public void processRefund(Sales sale, boolean restockItems, OnSaleVoidedListener listener) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null) return;
        FirebaseFirestore db = FirestoreManager.getInstance().getDb();

        // 1. Mark Sale as REFUNDED
        db.collection("users").document(ownerId).collection("sales").document(sale.getId()).update("status", "REFUNDED");

        // 2. Restore Inventory Locally IMMEDIATELY
        if (restockItems) {
            ProductRepository pr = SalesInventoryApplication.getProductRepository();
            pr.getProductById(sale.getProductId(), new ProductRepository.OnProductFetchedListener() {
                @Override
                public void onProductFetched(Product p) {
                    if (p != null) {
                        double newQty = p.getQuantity() + sale.getQuantity();
                        pr.updateProductQuantity(p.getProductId(), newQty, null);
                    }
                }
                @Override public void onError(String error) {}
            });
        }

        // 3. Deduct from Wallet
        String walletId = (sale.getPaymentMethod() != null && sale.getPaymentMethod().toLowerCase().contains("gcash")) ? "GCASH" : "CASH";
        DocumentReference walletRef = db.collection("users").document(ownerId).collection("wallets").document(walletId);

        Map<String, Object> walletUpdates = new HashMap<>();
        walletUpdates.put("balance", com.google.firebase.firestore.FieldValue.increment(-sale.getTotalPrice()));
        walletRef.set(walletUpdates, com.google.firebase.firestore.SetOptions.merge());

        // 4. Deduct from Active Shift
        String currentUserId = AuthManager.getInstance().getCurrentUserId();
        db.collection("users").document(ownerId).collection("shifts")
                .whereEqualTo("status", "ACTIVE")
                .whereEqualTo("cashierId", currentUserId)
                .get()
                .addOnSuccessListener(shiftSnapshot -> {
                    if (!shiftSnapshot.isEmpty()) {
                        DocumentReference shiftRef = shiftSnapshot.getDocuments().get(0).getReference();
                        Map<String, Object> shiftUpdates = new HashMap<>();
                        if (walletId.equals("CASH")) {
                            shiftUpdates.put("cashSales", com.google.firebase.firestore.FieldValue.increment(-sale.getTotalPrice()));
                        } else {
                            shiftUpdates.put("ePaymentSales", com.google.firebase.firestore.FieldValue.increment(-sale.getTotalPrice()));
                        }
                        shiftRef.set(shiftUpdates, com.google.firebase.firestore.SetOptions.merge());
                    }
                });

        // 5. INSTANT UI UPDATE
        if (listener != null) listener.onSuccess();
    }

    public interface OnSaleAddedListener {
        void onSaleAdded(String saleId);
        void onError(String error);
    }

    public interface OnSalesDeletedListener {
        void onSaleDeleted();
        void onError(String error);
    }

    private Map<String, Object> createSaleDataMap(Sales sale) {
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
        map.put("totalCost", sale.getTotalCost());
        map.put("discountAmount", sale.getDiscountAmount());
        map.put("extraDetails", sale.getExtraDetails() != null ? sale.getExtraDetails() : "");
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

        return map;
    }

    public void addSale(Sales sale, OnSaleAddedListener listener) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = AuthManager.getInstance().getCurrentUserId();
        }

        FirebaseFirestore db = FirestoreManager.getInstance().getDb();

        // 1. Create the document reference FIRST so we can get a valid ID
        DocumentReference saleRef = db.collection("users").document(ownerId).collection("sales").document();
        sale.setId(saleRef.getId()); // CRITICAL: This fixes the Refund bug!

        // 2. Fire and Forget to Firebase (If offline, Firebase safely caches this automatically!)
        saleRef.set(sale);

        // 3. Deduct stock instantly in the UI so the cashier doesn't have to wait
        ProductRepository pr = SalesInventoryApplication.getProductRepository();
        pr.getProductById(sale.getProductId(), new ProductRepository.OnProductFetchedListener() {
            @Override
            public void onProductFetched(Product p) {
                if (p != null) {
                    pr.updateProductQuantity(p.getProductId(), p.getQuantity() - sale.getQuantity(), null);
                }
            }
            @Override public void onError(String error) {}
        });

        // 4. Instantly unblock the POS
        if (listener != null) listener.onSaleAdded(saleRef.getId());
    }

    public void processSaleWithInventoryDeduction(Sales sale, List<RecipeItem> ingredientsUsed, OnSaleAddedListener listener) {
        if (!AuthManager.getInstance().isCurrentUserApproved()) {
            listener.onError("User not approved");
            return;
        }

        // 1. Create Offline Entities
        SalesOrderEntity orderEntity = new SalesOrderEntity();
        orderEntity.orderNumber = sale.getOrderId() != null ? sale.getOrderId() : "ORD-" + System.currentTimeMillis();
        orderEntity.orderDate = sale.getDate() > 0 ? sale.getDate() : System.currentTimeMillis();
        orderEntity.totalAmount = sale.getTotalPrice();
        orderEntity.paymentMethod = sale.getPaymentMethod() != null ? sale.getPaymentMethod() : "CASH";
        orderEntity.deliveryStatus = sale.getDeliveryStatus() != null ? sale.getDeliveryStatus() : "";
        orderEntity.remoteId = ""; // Will be processed by SyncWorker

        SalesOrderItemEntity itemEntity = new SalesOrderItemEntity();
        itemEntity.productId = sale.getProductId();
        itemEntity.productName = sale.getProductName();
        itemEntity.quantity = sale.getQuantity();
        itemEntity.unitPrice = sale.getPrice();
        itemEntity.lineTotal = sale.getTotalPrice();
        itemEntity.totalCost = sale.getTotalCost();
        itemEntity.discountAmount = sale.getDiscountAmount();
        itemEntity.extraDetails = sale.getExtraDetails();

        List<SalesOrderItemEntity> items = new ArrayList<>();
        items.add(itemEntity);

        // 2. Save directly to Room Local Database first! (Instantly unblocks the POS)
        saveOrderOfflineFirst(orderEntity, items, new OnSaleAddedListener() {
            @Override
            public void onSaleAdded(String saleId) {
                // 3. Deduct stock instantly in UI
                if (ingredientsUsed != null && !ingredientsUsed.isEmpty()) {
                    ProductRepository pr = SalesInventoryApplication.getProductRepository();
                    for (RecipeItem ingredient : ingredientsUsed) {
                        pr.getProductById(ingredient.getRawMaterialId(), new ProductRepository.OnProductFetchedListener() {
                            @Override
                            public void onProductFetched(Product p) {
                                if (p != null) {
                                    double totalDeduction = ingredient.getQuantityRequired() * sale.getQuantity();
                                    pr.updateProductQuantity(p.getProductId(), p.getQuantity() - totalDeduction, null);
                                }
                            }
                            @Override public void onError(String error) {}
                        });
                    }
                }
                if (listener != null) listener.onSaleAdded(saleId);
            }
            @Override
            public void onError(String error) {
                if (listener != null) listener.onError(error);
            }
        });
    }

    public void deleteSale(String saleId, OnSalesDeletedListener listener) {
        firestoreManager.getDb().collection(firestoreManager.getUserSalesPath())
                .document(saleId)
                .delete()
                .addOnSuccessListener(aVoid -> listener.onSaleDeleted())
                .addOnFailureListener(e -> listener.onError(e.getMessage()));
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
                .update(updates)
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update delivery status", e));
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

    private Sales createSalesFromSnapshot(DocumentSnapshot document) {
        Sales sale = createSalesFromMap(document.getData());
        if (sale != null) sale.setId(document.getId());
        return sale;
    }

    private Sales createSalesFromMap(Map<String, Object> data) {
        if (data == null) return null;
        Sales sale = new Sales();

        if (data.containsKey("id")) sale.setId((String) data.get("id"));
        if (data.containsKey("orderId")) sale.setOrderId((String) data.get("orderId"));
        if (data.containsKey("productId")) sale.setProductId((String) data.get("productId"));
        if (data.containsKey("productName")) sale.setProductName((String) data.get("productName"));
        if (data.containsKey("quantity")) { Object qty = data.get("quantity"); if (qty instanceof Number) sale.setQuantity(((Number) qty).intValue()); }
        if (data.containsKey("price")) { Object price = data.get("price"); if (price instanceof Number) sale.setPrice(((Number) price).doubleValue()); }
        if (data.containsKey("totalPrice")) { Object totalPrice = data.get("totalPrice"); if (totalPrice instanceof Number) sale.setTotalPrice(((Number) totalPrice).doubleValue()); }
        if (data.containsKey("paymentMethod")) sale.setPaymentMethod((String) data.get("paymentMethod"));

        if (data.containsKey("date")) {
            Object dateObj = data.get("date");
            long dateTime = 0L;
            if (dateObj instanceof com.google.firebase.Timestamp) dateTime = ((com.google.firebase.Timestamp) dateObj).toDate().getTime();
            else if (dateObj instanceof java.util.Date) dateTime = ((java.util.Date) dateObj).getTime();
            else if (dateObj instanceof Number) dateTime = ((Number) dateObj).longValue();
            sale.setDate(dateTime);
        }

        if (data.containsKey("timestamp")) {
            Object tsObj = data.get("timestamp");
            long timestamp = 0L;
            if (tsObj instanceof com.google.firebase.Timestamp) timestamp = ((com.google.firebase.Timestamp) tsObj).toDate().getTime();
            else if (tsObj instanceof java.util.Date) timestamp = ((java.util.Date) tsObj).getTime();
            else if (tsObj instanceof Number) timestamp = ((Number) tsObj).longValue();
            sale.setTimestamp(timestamp);
        }

        if (data.containsKey("deliveryType")) sale.setDeliveryType((String) data.get("deliveryType"));
        if (data.containsKey("deliveryStatus")) sale.setDeliveryStatus((String) data.get("deliveryStatus"));

        if (data.containsKey("deliveryDate")) {
            Object deliveryDateObj = data.get("deliveryDate");
            long deliveryDateTime = 0L;
            if (deliveryDateObj instanceof com.google.firebase.Timestamp) deliveryDateTime = ((com.google.firebase.Timestamp) deliveryDateObj).toDate().getTime();
            else if (deliveryDateObj instanceof java.util.Date) deliveryDateTime = ((java.util.Date) deliveryDateObj).getTime();
            else if (deliveryDateObj instanceof Number) deliveryDateTime = ((Number) deliveryDateObj).longValue();
            sale.setDeliveryDate(deliveryDateTime);
        }

        if (data.containsKey("deliveryName")) sale.setDeliveryName((String) data.get("deliveryName"));
        if (data.containsKey("deliveryPhone")) sale.setDeliveryPhone((String) data.get("deliveryPhone"));
        if (data.containsKey("deliveryAddress")) sale.setDeliveryAddress((String) data.get("deliveryAddress"));
        if (data.containsKey("deliveryPaymentMethod")) sale.setDeliveryPaymentMethod((String) data.get("deliveryPaymentMethod"));

        return sale;
    }

    public void saveOrderOfflineFirst(SalesOrderEntity order, List<SalesOrderItemEntity> items, OnSaleAddedListener listener) {
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(application);

                long localOrderId = db.salesDao().insertOrder(order);
                for(SalesOrderItemEntity item : items) {
                    item.orderLocalId = localOrderId;
                }
                db.salesDao().insertOrderItems(items);

                SyncScheduler.enqueueImmediateSync(application);

                if(listener != null) listener.onSaleAdded(String.valueOf(localOrderId));

            } catch (Exception e) {
                if(listener != null) listener.onError(e.getMessage());
            }
        });
    }

    public void reloadAllData() {
        loadAllSales();
        loadTodaySales();
        loadOverallRevenue();
        loadRecentSales();
    }

    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.clearData();
            instance = null;
        }
    }
}