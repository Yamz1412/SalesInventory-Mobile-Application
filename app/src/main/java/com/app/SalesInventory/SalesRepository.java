package com.app.SalesInventory;

import androidx.lifecycle.MutableLiveData;
import com.google.firebase.firestore.DocumentReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import android.app.Application;

public class SalesRepository {
    private static SalesRepository instance;
    private FirestoreManager firestoreManager;
    private MutableLiveData<List<Sales>> allSales;

    private SalesRepository() {
        firestoreManager = FirestoreManager.getInstance();
        allSales = new MutableLiveData<>();
        FirestoreSyncListener.getInstance().listenToSales(snapshot -> {
            List<Sales> salesList = new ArrayList<>();
            if (snapshot != null) {
                for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                    Sales sale = document.toObject(Sales.class);
                    if (sale != null) {
                        sale.setId(document.getId());
                        salesList.add(sale);
                    }
                }
            }
            allSales.postValue(salesList);
        });
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

    public MutableLiveData<List<Sales>> getAllSales() {
        return allSales;
    }

    public interface OnSaleAddedListener {
        void onSaleAdded(String saleId);
        void onError(String error);
    }

    public void addSale(Sales sale, OnSaleAddedListener listener) {
        if (!AuthManager.getInstance().isCurrentUserApproved()) {
            listener.onError("User not approved");
            return;
        }
        Map<String, Object> map = new HashMap<>();
        map.put("productId", sale.getProductId());
        map.put("productName", sale.getProductName());
        map.put("quantity", sale.getQuantity());
        map.put("price", sale.getPrice());
        map.put("totalPrice", sale.getTotalPrice());
        map.put("paymentMethod", sale.getPaymentMethod() != null ? sale.getPaymentMethod() : "");
        map.put("date", sale.getDate() > 0 ? sale.getDate() : "");
        map.put("timestamp", firestoreManager.getServerTimestamp());
        firestoreManager.getDb().collection(firestoreManager.getUserSalesPath()).add(map).addOnSuccessListener((DocumentReference documentReference) -> {
            String saleId = documentReference.getId();
            sale.setId(saleId);
            listener.onSaleAdded(saleId);
        }).addOnFailureListener(e -> {
            listener.onError(e.getMessage() != null ? e.getMessage() : "Failed to add sale");
        });
    }
}