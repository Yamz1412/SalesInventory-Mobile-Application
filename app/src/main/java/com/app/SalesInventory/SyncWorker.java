package com.app.SalesInventory;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
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
            List<ProductEntity> pending = productDao.getPendingProductsSync();
            if (pending == null || pending.isEmpty()) {
                return Result.success();
            }
            for (ProductEntity pe : pending) {
                if ("DELETE_PENDING".equals(pe.syncState)) {
                    handleDelete(pe);
                } else {
                    handleUpsert(pe);
                }
            }
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Sync failed", e);
            return Result.retry();
        }
    }

    private void handleDelete(ProductEntity pe) {
        if (pe.productId != null && !pe.productId.isEmpty()) {
            Task<Void> deleteTask = firestore
                    .collection(firestoreManager.getUserProductsPath())
                    .document(pe.productId)
                    .delete();
            try {
                Tasks.await(deleteTask);
                productDao.deleteByLocalId(pe.localId);
            } catch (ExecutionException | InterruptedException e) {
                productDao.setSyncInfo(pe.localId, pe.productId, "ERROR");
            }
        } else {
            productDao.deleteByLocalId(pe.localId);
        }
    }

    private void handleUpsert(ProductEntity pe) {
        try {
            if (pe.imagePath != null && !pe.imagePath.isEmpty() && (pe.imageUrl == null || pe.imageUrl.isEmpty())) {
                String url = uploadImage(pe);
                if (url != null) {
                    pe.imageUrl = url;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Image upload failed for localId=" + pe.localId, e);
        }

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
        doc.put("productType", pe.productType);
        doc.put("expiryDate", pe.expiryDate);
        doc.put("lastUpdated", firestoreManager.getServerTimestamp());
        if (pe.imageUrl != null && !pe.imageUrl.isEmpty()) {
            doc.put("imageUrl", pe.imageUrl);
        }

        if (pe.productId != null && !pe.productId.isEmpty()) {
            Task<Void> t = firestore
                    .collection(firestoreManager.getUserProductsPath())
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

    private String uploadImage(ProductEntity pe) throws ExecutionException, InterruptedException {
        if (pe.imagePath == null || pe.imagePath.isEmpty()) {
            return null;
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
        if (download == null) return null;
        return download.toString();
    }
}