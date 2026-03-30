package com.app.SalesInventory;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class SyncWorker extends Worker {
    private static final String TAG = "SyncWorker";
    private final AppDatabase db;
    private final ProductDao productDao;
    private final FirestoreManager firestoreManager;
    private final FirebaseFirestore firestore;
    private final FirebaseStorage storage;

    public SyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
        db = AppDatabase.getInstance(context);
        productDao = db.productDao();
        firestoreManager = FirestoreManager.getInstance();
        firestore = firestoreManager.getDb();
        storage = FirebaseStorage.getInstance();
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            // === Sync Products ===
            List<ProductEntity> pending = productDao.getPendingProductsSync();
            if (pending != null) {
                for (ProductEntity pe : pending) {
                    if ("DELETE_PENDING".equals(pe.syncState)) deleteEntity(pe);
                    else syncEntity(pe);
                }
            }
            pullFromFirestore();

            // === NEW: Sync Offline Sales ===
            syncPendingSales();

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "SyncWorker failed", e);
            return Result.retry();
        }
    }

    private void syncPendingSales() {
        List<SalesOrderWithItems> pendingOrders = db.salesDao().getPendingOrdersSync();
        if (pendingOrders == null || pendingOrders.isEmpty()) return;

        String ownerId = firestoreManager.getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();
        if (ownerId == null) return;

        for (SalesOrderWithItems orderWithItems : pendingOrders) {
            SalesOrderEntity order = orderWithItems.order;
            List<SalesOrderItemEntity> items = orderWithItems.items;

            // 1. Upload Items as Sales Documents
            for (SalesOrderItemEntity item : items) {
                Sales sale = new Sales();
                sale.setOrderId(order.orderNumber);
                sale.setProductId(item.productId);
                sale.setProductName(item.productName);
                sale.setQuantity((int)item.quantity);
                sale.setPrice(item.unitPrice);
                sale.setTotalPrice(item.lineTotal);
                sale.setPaymentMethod(order.paymentMethod);
                sale.setDate(order.orderDate);
                sale.setTimestamp(order.orderDate);
                sale.setDeliveryStatus(order.deliveryStatus);

                Task<DocumentReference> t = firestore.collection("users").document(ownerId).collection("sales").add(sale);
                try { Tasks.await(t); } catch (Exception ignored) {}
            }

            // 2. Update Cash Management & Shifts in the Background
            updateRemoteWalletAndShift(order, ownerId);

            // 3. Mark as Synced Locally
            db.salesDao().updateOrderRemoteId(order.localId, "SYNCED");
        }
    }

    private void updateRemoteWalletAndShift(SalesOrderEntity order, String ownerId) {
        String walletDocId = order.paymentMethod.toLowerCase().contains("gcash") ? "GCASH" : "CASH";
        DocumentReference walletRef = firestore.collection("users").document(ownerId).collection("wallets").document(walletDocId);

        // Update Wallet Balance
        firestore.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(walletRef);
            double newBal = order.totalAmount;
            if(snapshot.exists() && snapshot.getDouble("balance") != null) {
                newBal += snapshot.getDouble("balance");
            }
            Map<String, Object> w = new HashMap<>();
            w.put("balance", newBal);
            w.put("name", walletDocId.equals("CASH") ? "Cash on Hand" : "GCash");
            transaction.set(walletRef, w, com.google.firebase.firestore.SetOptions.merge());
            return null;
        });

        // Update Active Shift Totals
        Task<com.google.firebase.firestore.QuerySnapshot> shiftTask = firestore.collection("users").document(ownerId).collection("shifts")
                .whereEqualTo("status", "ACTIVE").get();
        try {
            com.google.firebase.firestore.QuerySnapshot shiftSnap = Tasks.await(shiftTask);
            if(!shiftSnap.isEmpty()) {
                DocumentSnapshot shiftDoc = shiftSnap.getDocuments().get(0);
                double currentCash = shiftDoc.getDouble("cashSales") != null ? shiftDoc.getDouble("cashSales") : 0.0;
                double currentEPay = shiftDoc.getDouble("ePaymentSales") != null ? shiftDoc.getDouble("ePaymentSales") : 0.0;
                if(order.paymentMethod.equalsIgnoreCase("Cash")) {
                    shiftDoc.getReference().update("cashSales", currentCash + order.totalAmount);
                } else {
                    shiftDoc.getReference().update("ePaymentSales", currentEPay + order.totalAmount);
                }
            }
        } catch (Exception ignored) {}
    }
    private void pullFromFirestore() {
        try {
            com.google.firebase.firestore.QuerySnapshot snapshot =
                    Tasks.await(firestore.collection(firestoreManager.getUserProductsPath()).get());

            ProductRepository repo = ProductRepository.getInstance(
                    (android.app.Application) getApplicationContext().getApplicationContext()
            );

            for (com.google.firebase.firestore.DocumentSnapshot doc : snapshot.getDocuments()) {
                Product p = doc.toObject(Product.class);
                if (p != null) {
                    p.setProductId(doc.getId());
                    repo.upsertFromRemote(p);
                }
            }
            Log.d(TAG, "Pulled " + snapshot.size() + " products from Firestore");
        } catch (Exception e) {
            Log.e(TAG, "pullFromFirestore failed", e);
            // Non-fatal: don't fail the whole job
        }
    }

    private void deleteEntity(ProductEntity pe) {
        if (pe.productId != null && !pe.productId.isEmpty()) {
            Task<Void> t = firestore.collection(firestoreManager.getUserProductsPath())
                    .document(pe.productId)
                    .delete();
            try {
                Tasks.await(t);
                productDao.deleteByLocalId(pe.localId);
            } catch (ExecutionException | InterruptedException e) {
                // Retry later
            }
        } else {
            productDao.deleteByLocalId(pe.localId);
        }
    }

    private void syncEntity(ProductEntity pe) {
        Map<String, Object> doc = new HashMap<>();
        doc.put("productName", pe.productName);
        doc.put("categoryId", pe.categoryId);
        doc.put("categoryName", pe.categoryName);
        doc.put("description", pe.description);
        doc.put("costPrice", pe.costPrice);
        doc.put("sellingPrice", pe.sellingPrice);
        doc.put("quantity", pe.quantity);
        doc.put("reorderLevel", pe.reorderLevel);
        doc.put("criticalLevel", pe.criticalLevel);
        doc.put("ceilingLevel", pe.ceilingLevel);
        doc.put("floorLevel", pe.floorLevel);
        doc.put("unit", pe.unit);
        doc.put("barcode", pe.barcode);
        doc.put("supplier", pe.supplier);
        doc.put("dateAdded", pe.dateAdded);
        doc.put("addedBy", pe.addedBy);
        doc.put("isActive", pe.isActive);
        doc.put("expiryDate", pe.expiryDate);
        doc.put("productType", pe.productType);

        // ==========================================
        // NEW: MAP THE JSON CONFIGURATION LISTS!
        // ==========================================
        doc.put("sizesList", parseJsonToListObj(pe.sizesListJson));
        doc.put("addonsList", parseJsonToListObj(pe.addonsListJson));
        doc.put("bomList", parseJsonToListObj(pe.bomListJson));
        doc.put("notesList", parseJsonToListStr(pe.notesListJson));

        try {
            String newUrl = uploadImage(pe);
            if (newUrl != null) {
                pe.imageUrl = newUrl;
                pe.imagePath = null;
            }
        } catch (Exception e) {
            // Log image error, but continue syncing data
        }

        doc.put("imageUrl", pe.imageUrl != null ? pe.imageUrl : "");

        if (pe.productId != null && !pe.productId.isEmpty()) {
            Task<Void> t = firestore.collection(firestoreManager.getUserProductsPath())
                    .document(pe.productId)
                    .set(doc);
            try {
                Tasks.await(t);
                productDao.setSyncInfo(pe.localId, pe.productId, "SYNCED");
            } catch (ExecutionException | InterruptedException e) {
                productDao.setSyncInfo(pe.localId, pe.productId, "ERROR");
            }
        } else {
            Task<com.google.firebase.firestore.DocumentReference> t = firestore
                    .collection(firestoreManager.getUserProductsPath())
                    .add(doc);
            try {
                com.google.firebase.firestore.DocumentReference dr = Tasks.await(t);
                String newId = dr.getId();
                productDao.setSyncInfo(pe.localId, newId, "SYNCED");
            } catch (ExecutionException | InterruptedException e) {
                productDao.setSyncInfo(pe.localId, null, "ERROR");
            }
        }
    }

    private List<Map<String, Object>> parseJsonToListObj(String json) {
        List<Map<String, Object>> list = new java.util.ArrayList<>();
        if (json == null || json.isEmpty() || json.equals("[]")) return list;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                Map<String, Object> map = new HashMap<>();
                java.util.Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    map.put(key, obj.get(key));
                }
                list.add(map);
            }
        } catch (Exception ignored) {}
        return list;
    }

    private List<Map<String, String>> parseJsonToListStr(String json) {
        List<Map<String, String>> list = new java.util.ArrayList<>();
        if (json == null || json.isEmpty() || json.equals("[]")) return list;
        try {
            org.json.JSONArray arr = new org.json.JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject obj = arr.getJSONObject(i);
                Map<String, String> map = new HashMap<>();
                java.util.Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    map.put(key, obj.getString(key));
                }
                list.add(map);
            }
        } catch (Exception ignored) {}
        return list;
    }

    private String uploadImage(ProductEntity pe) throws ExecutionException, InterruptedException {
        if (pe.imagePath == null || pe.imagePath.isEmpty()) {
            return null;
        }

        if (pe.imagePath.startsWith("http")) {
            return pe.imagePath;
        }

        Uri uri = Uri.parse(pe.imagePath);

        String id = pe.productId;
        if (id == null || id.isEmpty()) {
            id = "local_" + pe.localId;
        }
        StorageReference ref = storage
                .getReference()
                .child("product_images/" + id + ".jpg");
        Task<?> uploadTask = ref.putFile(uri);
        Tasks.await(uploadTask);
        Task<Uri> urlTask = ref.getDownloadUrl();
        Uri download = Tasks.await(urlTask);
        return download.toString();
    }
}