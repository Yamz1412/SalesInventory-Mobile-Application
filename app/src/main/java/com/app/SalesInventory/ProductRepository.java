package com.app.SalesInventory;

import android.app.Application;
import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;

import com.google.firebase.firestore.FirebaseFirestore;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public class ProductRepository {
    private static ProductRepository instance;
    private AppDatabase db;
    private ProductDao productDao;
    private MediatorLiveData<List<Product>> allProducts;
    private LiveData<List<ProductEntity>> currentSource;
    private Application application;
    private AlertRepository alertRepository;
    private List<OnCriticalStockListener> criticalStockListeners = new CopyOnWriteArrayList<>();
    private final java.util.concurrent.ExecutorService syncExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();

    public interface OnCriticalStockListener {
        void onProductCritical(Product product);
    }

    private ProductRepository(Application application) {
        this.application = application;
        db = AppDatabase.getInstance(application);
        productDao = db.productDao();
        allProducts = new MediatorLiveData<>();
        alertRepository = AlertRepository.getInstance(application);

        refreshProducts();
        SyncScheduler.schedulePeriodicSync(application.getApplicationContext());
    }

    public static synchronized ProductRepository getInstance(Application application) {
        if (instance == null) {
            instance = new ProductRepository(application);
        }
        return instance;
    }

    private ProductEntity findEntityByIdSafe(String id) {
        if (id == null || id.isEmpty()) return null;
        if (id.startsWith("local:")) {
            try {
                long localId = Long.parseLong(id.replace("local:", ""));
                return productDao.getByLocalId(localId);
            } catch (Exception e) { return null; }
        }
        ProductEntity e = productDao.getByProductIdSync(id);
        if (e == null) {
            try {
                long localId = Long.parseLong(id);
                e = productDao.getByLocalId(localId);
            } catch (Exception ex) { }
        }
        return e;
    }

    public void syncProductImageFromFirestore(String productId, String imageUrl) {
        if (productId == null || imageUrl == null || imageUrl.isEmpty()) return;
        syncExecutor.execute(() -> {
            try {
                ProductEntity entity = findEntityByIdSafe(productId);
                if (entity != null) {
                    entity.imageUrl = imageUrl;
                    entity.lastUpdated = System.currentTimeMillis();
                    productDao.update(entity);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void clearLocalData() {
        syncExecutor.execute(() -> {
            db.clearAllTables();
            allProducts.postValue(new ArrayList<>());
        });
    }

    public void deductStockFIFO(String productId, double quantityToDeduct, Context context) {
        new Thread(() -> {
            AppDatabase db = AppDatabase.getInstance(context);
            BatchDao batchDao = db.batchDao();
            ProductDao productDao = db.productDao();

            List<BatchEntity> batches = batchDao.getAvailableBatchesFIFO(productId);
            double remainingToDeduct = quantityToDeduct;

            for (BatchEntity batch : batches) {
                if (remainingToDeduct <= 0) break;

                if (batch.remainingQuantity <= remainingToDeduct) {
                    remainingToDeduct -= batch.remainingQuantity;
                    batch.remainingQuantity = 0;
                } else {
                    batch.remainingQuantity -= remainingToDeduct;
                    remainingToDeduct = 0;
                }
                batchDao.updateBatch(batch);
            }

            double newTotalQuantity = batchDao.getTotalBatchQuantity(productId);
            updateProductQuantity(productId, newTotalQuantity, null);
        }).start();
    }

    public void updateProductImage(String productId, String imagePath, String imageUrl, OnProductUpdatedListener listener) {
        syncExecutor.execute(() -> {
            try {
                ProductEntity entity = findEntityByIdSafe(productId);
                if (entity != null) {
                    if (imagePath != null && !imagePath.isEmpty()) entity.imagePath = imagePath;
                    if (imageUrl != null && !imageUrl.isEmpty()) entity.imageUrl = imageUrl;
                    entity.lastUpdated = System.currentTimeMillis();
                    productDao.update(entity);
                    if (listener != null) listener.onProductUpdated();
                }
            } catch (Exception e) {
                if (listener != null) listener.onError(e.getMessage());
            }
        });
    }

    public void registerCriticalStockListener(OnCriticalStockListener listener) {
        if (listener != null && !criticalStockListeners.contains(listener)) {
            criticalStockListeners.add(listener);
        }
    }

    public void unregisterCriticalStockListener(OnCriticalStockListener listener) {
        criticalStockListeners.remove(listener);
    }

    private void notifyCriticalStock(Product p) {
        if (p.isCriticalStock()) {
            for (OnCriticalStockListener listener : criticalStockListeners) {
                listener.onProductCritical(p);
            }
        }
    }

    public LiveData<List<Product>> getAllProducts() {
        return allProducts;
    }

    public void fetchAllProductsAsync(OnProductsFetchedListener listener) {
        syncExecutor.execute(() -> {
            List<ProductEntity> entities = productDao.getPendingProductsSync();
            List<Product> products = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();
            if (entities != null) {
                for (ProductEntity e : entities) {
                    if (e.productId != null && seenIds.contains(e.productId)) continue;
                    if (e.productId != null) seenIds.add(e.productId);
                    products.add(mapEntityToProduct(e));
                }
            }
            listener.onProductsFetched(products);
        });
    }

    public void getProductsByCategory(String category, OnProductsFetchedListener listener) {
        LiveData<List<ProductEntity>> source = productDao.getAllProductsLive();
        Observer<List<ProductEntity>> obs = new Observer<List<ProductEntity>>() {
            @Override
            public void onChanged(List<ProductEntity> entities) {
                source.removeObserver(this);
                List<Product> results = new ArrayList<>();
                Set<String> seenIds = new HashSet<>();
                if (entities != null) {
                    for (ProductEntity e : entities) {
                        Product p = mapEntityToProduct(e);
                        if (p.getProductId() != null && seenIds.contains(p.getProductId())) continue;
                        if (p.getProductId() != null) seenIds.add(p.getProductId());

                        if (category == null || category.isEmpty()) {
                            results.add(p);
                        } else {
                            String catName = p.getCategoryName() == null ? "" : p.getCategoryName();
                            if (catName.equalsIgnoreCase(category)) results.add(p);
                        }
                    }
                }
                listener.onProductsFetched(results);
            }
        };
        source.observeForever(obs);
    }

    public void addProduct(Product product, Uri imageUri, OnProductAddedListener listener) {
        String path = (imageUri != null) ? imageUri.toString() : null;
        addProduct(product, path, listener);
    }

    public void addProduct(Product product, String imagePath, OnProductAddedListener listener) {
        syncExecutor.execute(() -> {
            if (!AuthManager.getInstance().isCurrentUserApproved()) {
                if (listener != null) listener.onError("User not approved");
                return;
            }
            if (product.getProductId() == null || product.getProductId().isEmpty()) {
                product.setProductId(java.util.UUID.randomUUID().toString());
            }
            ProductEntity existing = findEntityByIdSafe(product.getProductId());
            if (existing != null) {
                if (listener != null) listener.onError("Product already exists.");
                return;
            }

            long now = System.currentTimeMillis();
            ProductEntity e = mapProductToEntity(product);
            if (e.floorLevel < 1) e.floorLevel = 1;
            if (e.criticalLevel < 1) e.criticalLevel = 1;
            if (e.ceilingLevel <= 0) e.ceilingLevel = computeDefaultCeiling(e.quantity, e.reorderLevel);
            if (e.ceilingLevel > 9999) e.ceilingLevel = 9999;
            if (e.quantity > e.ceilingLevel) e.ceilingLevel = (int) Math.ceil(e.quantity);

            e.dateAdded = now;
            e.lastUpdated = now;
            e.syncState = "PENDING";
            if (imagePath != null && !imagePath.isEmpty()) e.imagePath = imagePath;

            long localId = productDao.insert(e);
            checkExpiryForEntity(e);
            checkFloorForEntity(e);
            SyncScheduler.enqueueImmediateSync(application.getApplicationContext());
            if (listener != null) listener.onProductAdded("local:" + localId);
        });
    }

    public void insert(Product product, OnProductInsertedListener listener) {
        syncExecutor.execute(() -> {
            if (!AuthManager.getInstance().isCurrentUserApproved()) {
                if (listener != null) listener.onError("User not approved");
                return;
            }
            try {
                long now = System.currentTimeMillis();
                ProductEntity e = mapProductToEntity(product);

                if (e.floorLevel < 1) e.floorLevel = 1;
                if (e.criticalLevel < 1) e.criticalLevel = 1;
                if (e.ceilingLevel <= 0) e.ceilingLevel = computeDefaultCeiling(e.quantity, e.reorderLevel);
                if (e.ceilingLevel > 9999) e.ceilingLevel = 9999;
                if (e.quantity > e.ceilingLevel) e.ceilingLevel = (int) Math.ceil(e.quantity);

                e.dateAdded = now;
                e.lastUpdated = now;
                e.syncState = "PENDING";

                long localId = productDao.insert(e);

                String resultId = product.getProductId();
                if (resultId == null || resultId.isEmpty()) {
                    resultId = "local:" + localId;
                }

                checkExpiryForEntity(e);
                checkFloorForEntity(e);
                SyncScheduler.enqueueImmediateSync(application.getApplicationContext());

                if (listener != null) listener.onProductInserted(resultId);
            } catch (Exception e) {
                if (listener != null) listener.onError("Insert failed: " + e.getMessage());
            }
        });
    }

    public void updateProduct(Product product, OnProductUpdatedListener listener) {
        updateProduct(product, null, listener);
    }

    public void updateProduct(Product product, String imagePath, OnProductUpdatedListener listener) {
        syncExecutor.execute(() -> {
            if (!AuthManager.getInstance().isCurrentUserApproved()) {
                if (listener != null) listener.onError("User not approved");
                return;
            }
            String searchId = product.getProductId() != null && !product.getProductId().isEmpty() ?
                    product.getProductId() : "local:" + product.getLocalId();
            ProductEntity existing = findEntityByIdSafe(searchId);

            long now = System.currentTimeMillis();
            if (existing != null) {
                existing.productName = product.getProductName();
                existing.categoryId = product.getCategoryId();
                existing.categoryName = product.getCategoryName();
                existing.productLine = product.getProductLine();
                existing.description = product.getDescription();
                existing.costPrice = product.getCostPrice();
                existing.sellingPrice = product.getSellingPrice();
                existing.quantity = product.getQuantity();
                existing.reorderLevel = product.getReorderLevel();
                existing.criticalLevel = product.getCriticalLevel();
                existing.ceilingLevel = product.getCeilingLevel();
                existing.floorLevel = product.getFloorLevel();
                existing.unit = product.getUnit();
                existing.dateAdded = product.getDateAdded();
                existing.expiryDate = product.getExpiryDate();
                existing.productType = product.getProductType();

                // FIX: Ensure subunit fields aren't dropped during updates
                existing.salesUnit = product.getSalesUnit();
                existing.piecesPerUnit = product.getPiecesPerUnit();

                existing.bomListJson = serializeListObj(product.getBomList());
                existing.sizesListJson = serializeListObj(product.getSizesList());
                existing.addonsListJson = serializeListObj(product.getAddonsList());
                existing.notesListJson = serializeListStr(product.getNotesList());

                existing.isPromo = product.isPromo();
                existing.isTemporaryPromo = product.isTemporaryPromo();
                existing.promoName = product.getPromoName();
                existing.promoStartDate = product.getPromoStartDate();
                existing.promoEndDate = product.getPromoEndDate();

                if (existing.floorLevel < 1) existing.floorLevel = 1;
                if (existing.criticalLevel < 1) existing.criticalLevel = 1;
                if (existing.ceilingLevel <= 0) existing.ceilingLevel = computeDefaultCeiling(existing.quantity, existing.reorderLevel);
                if (existing.ceilingLevel > 9999) existing.ceilingLevel = 9999;
                if (existing.quantity > existing.ceilingLevel) existing.ceilingLevel = (int) Math.ceil(existing.quantity);

                existing.lastUpdated = now;
                existing.syncState = "PENDING";
                if (imagePath != null && !imagePath.isEmpty()) {
                    existing.imagePath = imagePath;
                } else if (product.getImagePath() != null) {
                    existing.imagePath = product.getImagePath();
                }
                if (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) {
                    existing.imageUrl = product.getImageUrl();
                }
                productDao.update(existing);
                checkExpiryForEntity(existing);
                checkFloorForEntity(existing);
            } else {
                ProductEntity e = mapProductToEntity(product);
                if (e.floorLevel < 1) e.floorLevel = 1;
                if (e.criticalLevel < 1) e.criticalLevel = 1;
                if (e.ceilingLevel <= 0) e.ceilingLevel = computeDefaultCeiling(e.quantity, e.reorderLevel);
                if (e.ceilingLevel > 9999) e.ceilingLevel = 9999;
                if (e.quantity > e.ceilingLevel) e.ceilingLevel = (int) Math.ceil(e.quantity);

                e.lastUpdated = now;
                e.syncState = "PENDING";
                if (imagePath != null && !imagePath.isEmpty()) e.imagePath = imagePath;
                productDao.insert(e);
                checkExpiryForEntity(e);
                checkFloorForEntity(e);
            }
            SyncScheduler.enqueueImmediateSync(application.getApplicationContext());
            if (listener != null) listener.onProductUpdated();
        });
    }

    public void cleanupDuplicates(OnCleanupListener listener) {
        syncExecutor.execute(() -> {
            List<ProductEntity> all = productDao.getAllProductsSync();
            if (all == null) {
                if (listener != null) listener.onCleanupComplete(0);
                return;
            }

            Map<String, List<ProductEntity>> map = new HashMap<>();
            for (ProductEntity p : all) {
                if (!p.isActive) continue;
                String name = p.productName != null ? p.productName.trim().toLowerCase() : "";
                if (name.isEmpty()) continue;

                if (!map.containsKey(name)) map.put(name, new ArrayList<>());
                map.get(name).add(p);
            }

            int deletedCount = 0;
            long now = System.currentTimeMillis();

            for (List<ProductEntity> duplicates : map.values()) {
                if (duplicates.size() > 1) {
                    Collections.sort(duplicates, (p1, p2) -> {
                        boolean p1HasId = p1.productId != null && !p1.productId.isEmpty();
                        boolean p2HasId = p2.productId != null && !p2.productId.isEmpty();
                        if (p1HasId && !p2HasId) return -1;
                        if (!p1HasId && p2HasId) return 1;

                        if (p1.quantity > p2.quantity) return -1;
                        if (p2.quantity > p1.quantity) return 1;

                        return Long.compare(p2.lastUpdated, p1.lastUpdated);
                    });

                    for (int i = 1; i < duplicates.size(); i++) {
                        ProductEntity toDelete = duplicates.get(i);
                        archiveEntityLocally(toDelete);
                        toDelete.isActive = false;
                        toDelete.lastUpdated = now;
                        toDelete.syncState = "DELETE_PENDING";
                        productDao.update(toDelete);
                        deletedCount++;
                    }
                }
            }

            if (deletedCount > 0) SyncScheduler.enqueueImmediateSync(application.getApplicationContext());
            if (listener != null) listener.onCleanupComplete(deletedCount);
        });
    }

    public void deleteProduct(String productId, OnProductDeletedListener listener) {
        syncExecutor.execute(() -> {
            if (!AuthManager.getInstance().isCurrentUserAdmin()) {
                if (listener != null) listener.onError("Unauthorized");
                return;
            }
            ProductEntity existing = findEntityByIdSafe(productId);
            String archiveFilename = null;
            if (existing != null) {
                archiveFilename = archiveEntityLocally(existing);
                long now = System.currentTimeMillis();

                existing.isActive = false;
                existing.lastUpdated = now;
                existing.syncState = "PENDING";
                productDao.update(existing);

                String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
                if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();

                if (ownerId != null && productId != null && !productId.startsWith("local:")) {
                    FirebaseFirestore.getInstance()
                            .collection("users").document(ownerId)
                            .collection("products").document(productId)
                            .update("isActive", false)
                            .addOnSuccessListener(aVoid -> { });
                }
            }
            SyncScheduler.enqueueImmediateSync(application.getApplicationContext());
            if (listener != null) {
                listener.onProductDeleted(archiveFilename);
            }
        });
    }

    private String archiveEntityLocally(ProductEntity e) {
        try {
            File dir = new File(application.getFilesDir(), "archives");
            if (!dir.exists()) dir.mkdirs();
            String idPart = e.productId != null && !e.productId.isEmpty() ? e.productId : String.valueOf(e.localId);
            String fname = "product_" + sanitizeFilename(idPart) + "_" + System.currentTimeMillis() + ".json";
            File out = new File(dir, fname);
            JSONObject o = new JSONObject();
            o.put("localId", e.localId);
            o.put("productId", e.productId);
            o.put("productName", e.productName);
            o.put("categoryId", e.categoryId);
            o.put("categoryName", e.categoryName);
            o.put("description", e.description);
            o.put("costPrice", e.costPrice);
            o.put("sellingPrice", e.sellingPrice);
            o.put("quantity", e.quantity);
            o.put("reorderLevel", e.reorderLevel);
            o.put("criticalLevel", e.criticalLevel);
            o.put("ceilingLevel", e.ceilingLevel);
            o.put("floorLevel", e.floorLevel);
            o.put("unit", e.unit);
            o.put("barcode", e.barcode);
            o.put("supplier", e.supplier);
            o.put("dateAdded", e.dateAdded);
            o.put("addedBy", e.addedBy);
            o.put("isActive", e.isActive);
            o.put("imagePath", e.imagePath);
            o.put("imageUrl", e.imageUrl);
            o.put("expiryDate", e.expiryDate);
            o.put("productType", e.productType);
            o.put("lastUpdated", e.lastUpdated);
            o.put("syncState", e.syncState);

            // FIX: Subunit fields properly backed up
            o.put("salesUnit", e.salesUnit);
            o.put("piecesPerUnit", e.piecesPerUnit);

            o.put("sizesListJson", e.sizesListJson);
            o.put("addonsListJson", e.addonsListJson);
            o.put("notesListJson", e.notesListJson);
            o.put("bomListJson", e.bomListJson);

            o.put("isPromo", e.isPromo);
            o.put("isTemporaryPromo", e.isTemporaryPromo);
            o.put("promoName", e.promoName);
            o.put("promoStartDate", e.promoStartDate);
            o.put("promoEndDate", e.promoEndDate);

            FileWriter fw = new FileWriter(out);
            fw.write(o.toString());
            fw.flush();
            fw.close();
            return fname;
        } catch (Exception ex) {
            return null;
        }
    }

    public List<String> listLocalArchives() {
        List<String> result = new ArrayList<>();
        File dir = new File(application.getFilesDir(), "archives");
        if (!dir.exists()) return result;
        File[] files = dir.listFiles();
        if (files == null) return result;
        for (File f : files) {
            if (f.isFile() && f.getName().endsWith(".json")) result.add(f.getName());
        }
        return result;
    }

    private String sanitizeFilename(String s) {
        if (s == null) return "x";
        return s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    public void updateProductQuantity(String productId, double newQuantity, OnProductUpdatedListener listener) {
        updateProductQuantityAndCost(productId, newQuantity, -1, listener);
    }

    public void updateProductQuantityAndCost(String productId, double newQuantity, double newCostPrice, OnProductUpdatedListener listener) {
        syncExecutor.execute(() -> {
            try {
                ProductEntity existing = findEntityByIdSafe(productId);
                if (existing != null) {
                    double oldQuantity = existing.quantity;
                    double clamped = Math.max(0.0, newQuantity);
                    if (clamped > 99999) clamped = 99999.0;

                    // FIX: ONLY override threshold levels if they haven't been manually set!
                    if (!"Menu".equalsIgnoreCase(existing.productType)) {
                        if (existing.ceilingLevel <= 0) existing.ceilingLevel = (int) Math.max(10, Math.ceil(clamped * 2.0));
                        if (existing.reorderLevel <= 0) existing.reorderLevel = (int) Math.max(1, Math.ceil(clamped * 0.20));
                        if (existing.criticalLevel <= 0) existing.criticalLevel = (int) Math.max(1, Math.ceil(clamped * 0.05));
                        if (existing.floorLevel <= 0) existing.floorLevel = 1;
                    }

                    existing.quantity = clamped;
                    if (newCostPrice >= 0) {
                        existing.costPrice = newCostPrice;
                    }

                    existing.lastUpdated = System.currentTimeMillis();
                    existing.syncState = "PENDING";
                    productDao.update(existing);

                    if (productId != null && !productId.startsWith("local:")) {
                        FirebaseFirestore.getInstance()
                                .collection(FirestoreManager.getInstance().getUserProductsPath())
                                .document(productId)
                                .update(
                                        "quantity", existing.quantity,
                                        "costPrice", existing.costPrice,
                                        "reorderLevel", existing.reorderLevel,
                                        "criticalLevel", existing.criticalLevel
                                );
                    } else {
                        SyncScheduler.enqueueImmediateSync(application.getApplicationContext());
                    }

                    boolean wasCritical = existing.criticalLevel > 0 && oldQuantity <= existing.criticalLevel;
                    boolean isNowCritical = existing.criticalLevel > 0 && clamped <= existing.criticalLevel;
                    boolean isNowLowOnly = !isNowCritical && existing.reorderLevel > 0 && clamped <= existing.reorderLevel;
                    boolean recoveredFromCritical = wasCritical && clamped > existing.criticalLevel;

                    if (recoveredFromCritical) {
                        CriticalStockNotifier.getInstance().clearForProduct(existing.productId);
                    }
                    if (isNowCritical) {
                        createCriticalStockAlert(existing);
                        notifyCriticalStockListeners(existing);
                    } else if (isNowLowOnly) {
                        createLowStockAlert(existing);
                    }
                    if (existing.floorLevel > 0 && clamped <= existing.floorLevel) {
                        createFloorLevelAlert(existing);
                    }
                    if (listener != null) {
                        new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> listener.onProductUpdated());
                    }
                    checkExpiryForEntity(existing);
                } else {
                    if (listener != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> listener.onError("Product not found locally"));
                }
            } catch (Exception e) {
                if (listener != null) new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> listener.onError("Update failed: " + e.getMessage()));
            }
        });
    }

    private void notifyCriticalStockListeners(ProductEntity e) {
        Product p = mapEntityToProduct(e);
        for (OnCriticalStockListener l : criticalStockListeners) {
            l.onProductCritical(p);
        }
    }

    private void createLowStockAlert(ProductEntity e) {
        if (alertRepository == null) return;
        String name = e.productName == null ? "" : e.productName;
        String message = "Reorder level reached: " + name + " (" + e.quantity + " left)";
        alertRepository.addAlertIfNotExists(e.productId, "LOW_STOCK", message, System.currentTimeMillis(), new AlertRepository.OnAlertAddedListener() {
            @Override public void onAlertAdded(String alertId) {}
            @Override public void onError(String error) {}
        });
    }

    private void createCriticalStockAlert(ProductEntity e) {
        if (alertRepository == null) return;
        String name = e.productName == null ? "" : e.productName;
        String message = "Critical stock: " + name + " (" + e.quantity + " left)";
        alertRepository.addAlertIfNotExists(e.productId, "CRITICAL_STOCK", message, System.currentTimeMillis(), new AlertRepository.OnAlertAddedListener() {
            @Override public void onAlertAdded(String alertId) {}
            @Override public void onError(String error) {}
        });
    }

    private void createFloorLevelAlert(ProductEntity e) {
        if (alertRepository == null) return;
        String name = e.productName == null ? "" : e.productName;
        String message = "Floor level reached: " + name + " (" + e.quantity + " left)";
        alertRepository.addAlertIfNotExists(e.productId, "FLOOR_STOCK", message, System.currentTimeMillis(), new AlertRepository.OnAlertAddedListener() {
            @Override public void onAlertAdded(String alertId) {}
            @Override public void onError(String error) {}
        });
    }

    private void createExpiryAlert(ProductEntity e, String type) {
        if (alertRepository == null) return;
        String name = e.productName == null ? "" : e.productName;
        String message;
        if ("EXPIRY_7_DAYS".equals(type)) message = "Product \"" + name + "\" will expire in 7 days or less.";
        else if ("EXPIRY_3_DAYS".equals(type)) message = "Product \"" + name + "\" will expire in 3 days or less.";
        else if ("EXPIRED".equals(type)) message = "Product \"" + name + "\" has expired.";
        else message = "Expiry alert for " + name + ".";
        alertRepository.addAlertIfNotExists(e.productId, type, message, System.currentTimeMillis(), new AlertRepository.OnAlertAddedListener() {
            @Override public void onAlertAdded(String alertId) {}
            @Override public void onError(String error) {}
        });
    }

    private void checkFloorForEntity(ProductEntity e) {
        if (e == null || e.floorLevel <= 0) return;
        if (e.quantity <= e.floorLevel) createFloorLevelAlert(e);
    }

    private void checkExpiryForEntity(ProductEntity e) {
        if (e == null || e.expiryDate <= 0) return;
        long diffMillis = e.expiryDate - System.currentTimeMillis();
        long days = diffMillis / (24L * 60L * 60L * 1000L);
        if (diffMillis <= 0) createExpiryAlert(e, "EXPIRED");
        else if (days <= 3) createExpiryAlert(e, "EXPIRY_3_DAYS");
        else if (days <= 7) createExpiryAlert(e, "EXPIRY_7_DAYS");
    }

    public void runExpirySweep() {
        syncExecutor.execute(() -> {
            List<ProductEntity> entities = productDao.getAllProductsSync();
            if (entities == null) return;
            for (ProductEntity e : entities) {
                checkExpiryForEntity(e);
                checkFloorForEntity(e);
            }
        });
    }

    public void getProductById(String productId, OnProductFetchedListener listener) {
        syncExecutor.execute(() -> {
            ProductEntity e = findEntityByIdSafe(productId);
            if (e != null) listener.onProductFetched(mapEntityToProduct(e));
            else listener.onError("Product not found");
        });
    }

    private String serializeListObj(List<Map<String, Object>> list) {
        if (list == null || list.isEmpty()) return null;
        JSONArray array = new JSONArray();
        for (Map<String, Object> map : list) {
            JSONObject obj = new JSONObject(map);
            array.put(obj);
        }
        return array.toString();
    }

    private List<Map<String, Object>> deserializeListObj(String json) {
        if (json == null || json.isEmpty() || json.equals("null")) return new ArrayList<>();
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                try {
                    // CRITICAL FIX: Inner Try-Catch ensures one bad item doesn't destroy the whole list!
                    JSONObject obj = array.getJSONObject(i);
                    Map<String, Object> map = new HashMap<>();
                    Iterator<String> keys = obj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        Object val = obj.get(key);
                        if (val == JSONObject.NULL) {
                            map.put(key, null);
                        } else {
                            map.put(key, val);
                        }
                    }
                    list.add(map);
                } catch (Exception inner) { Log.e("ProductRepo", "Inner JSON Error: " + inner.getMessage()); }
            }
        } catch (Exception e) { Log.e("ProductRepo", "JSON Error: " + e.getMessage()); }
        return list;
    }

    private String serializeListStr(List<Map<String, String>> list) {
        if (list == null || list.isEmpty()) return null;
        JSONArray array = new JSONArray();
        for (Map<String, String> map : list) {
            JSONObject obj = new JSONObject(map);
            array.put(obj);
        }
        return array.toString();
    }

    private List<Map<String, String>> deserializeListStr(String json) {
        if (json == null || json.isEmpty() || json.equals("null")) return new ArrayList<>();
        List<Map<String, String>> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                try {
                    // CRITICAL FIX: Safe string conversion prevents number-format crashes
                    JSONObject obj = array.getJSONObject(i);
                    Map<String, String> map = new HashMap<>();
                    Iterator<String> keys = obj.keys();
                    while (keys.hasNext()) {
                        String key = keys.next();
                        Object val = obj.get(key);
                        if (val != JSONObject.NULL) {
                            map.put(key, String.valueOf(val));
                        }
                    }
                    list.add(map);
                } catch (Exception inner) { Log.e("ProductRepo", "Inner JSON Error: " + inner.getMessage()); }
            }
        } catch (Exception e) { Log.e("ProductRepo", "JSON Error: " + e.getMessage()); }
        return list;
    }

    private Product mapEntityToProduct(ProductEntity e) {
        Product p = new Product();
        p.setLocalId(e.localId);
        p.setProductId(e.productId);
        p.setProductName(e.productName);
        p.setCategoryId(e.categoryId);
        p.setCategoryName(e.categoryName);
        p.setProductLine(e.productLine);
        p.setDescription(e.description);
        p.setCostPrice(e.costPrice);
        p.setSellingPrice(e.sellingPrice);
        p.setQuantity(e.quantity);
        p.setReorderLevel(e.reorderLevel);
        p.setCriticalLevel(e.criticalLevel);
        p.setCeilingLevel(e.ceilingLevel);
        p.setFloorLevel(e.floorLevel);
        p.setUnit(e.unit);
        p.setBarcode(e.barcode);
        p.setSupplier(e.supplier);
        p.setDateAdded(e.dateAdded);
        p.setAddedBy(e.addedBy);
        p.setActive(e.isActive);

        p.setImagePath(e.imagePath);
        p.setImageUrl(e.imageUrl);

        p.setProductType(e.productType);
        p.setOwnerAdminId(e.ownerAdminId);
        p.setExpiryDate(e.expiryDate);

        // FIX: Map Sub-units reliably
        p.setSalesUnit(e.salesUnit);
        p.setPiecesPerUnit(e.piecesPerUnit);

        p.setBomList(safeJsonToList(e.bomListJson));
        p.setSizesList(safeJsonToList(e.sizesListJson));
        p.setAddonsList(safeJsonToList(e.addonsListJson));
        p.setNotesList(safeJsonToNotesList(e.notesListJson));

        p.setPromo(e.isPromo);
        p.setTemporaryPromo(e.isTemporaryPromo);
        p.setPromoName(e.promoName);
        p.setPromoStartDate(e.promoStartDate);
        p.setPromoEndDate(e.promoEndDate);

        return p;
    }

    private ProductEntity mapProductToEntity(Product p) {
        ProductEntity e = new ProductEntity();
        e.localId = p.getLocalId();
        e.productId = p.getProductId();
        e.productName = p.getProductName();
        e.categoryId = p.getCategoryId();
        e.categoryName = p.getCategoryName();
        e.productLine = p.getProductLine();
        e.description = p.getDescription();
        e.costPrice = p.getCostPrice();
        e.sellingPrice = p.getSellingPrice();
        e.quantity = p.getQuantity();
        e.reorderLevel = p.getReorderLevel();
        e.criticalLevel = p.getCriticalLevel();
        e.ceilingLevel = p.getCeilingLevel();
        e.floorLevel = p.getFloorLevel();
        e.unit = p.getUnit();
        e.barcode = p.getBarcode();
        e.supplier = p.getSupplier();
        e.dateAdded = p.getDateAdded();
        e.addedBy = p.getAddedBy();
        e.isActive = p.isActive();
        e.lastUpdated = System.currentTimeMillis();

        e.imagePath = p.getImagePath();
        e.imageUrl = p.getImageUrl();

        e.productType = p.getProductType();
        e.ownerAdminId = p.getOwnerAdminId();
        e.expiryDate = p.getExpiryDate();

        // FIX: Map Sub-units reliably
        e.salesUnit = p.getSalesUnit();
        e.piecesPerUnit = p.getPiecesPerUnit();

        e.bomListJson = safeListToJson(p.getBomList());
        e.sizesListJson = safeListToJson(p.getSizesList());
        e.addonsListJson = safeListToJson(p.getAddonsList());
        e.notesListJson = safeListToJson(p.getNotesList());

        e.isPromo = p.isPromo();
        e.isTemporaryPromo = p.isTemporaryPromo();
        e.promoName = p.getPromoName();
        e.promoStartDate = p.getPromoStartDate();
        e.promoEndDate = p.getPromoEndDate();

        return e;
    }

    public void retrySync(long localId) {
        syncExecutor.execute(() -> {            ProductEntity e = productDao.getByLocalId(localId);
            if (e != null) {
                productDao.setSyncInfo(localId, e.productId, "PENDING");
                SyncScheduler.enqueueImmediateSync(application.getApplicationContext());
            }
        });
    }

    public void refreshProducts() {
        if (currentSource != null) {
            allProducts.removeSource(currentSource);
        }
        currentSource = productDao.getAllProductsLive();
        allProducts.addSource(currentSource, entities -> {
            List<Product> list = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();
            Set<String> seenNames = new HashSet<>();

            if (entities != null) {
                for (ProductEntity e : entities) {
                    Product p = mapEntityToProduct(e);
                    String nameKey = p.getProductName() != null ? p.getProductName().trim().toLowerCase() : "";

                    boolean isIdUnique = p.getProductId() == null || !seenIds.contains(p.getProductId());
                    boolean isNameUnique = nameKey.isEmpty() || !seenNames.contains(nameKey);

                    if (isIdUnique && isNameUnique) {
                        list.add(p);
                        if (p.getProductId() != null) seenIds.add(p.getProductId());
                        if (!nameKey.isEmpty()) seenNames.add(nameKey);
                    }
                }
            }
            allProducts.setValue(list);
        });
    }

    public void upsertFromRemote(Product p) {
        if (p == null || p.getProductId() == null) return;
        syncExecutor.execute(() -> {
            try {
                ProductEntity existing = productDao.getByProductIdSync(p.getProductId());
                long now = System.currentTimeMillis();

                if (existing == null) {
                    List<ProductEntity> all = productDao.getAllProductsSync();
                    if (all != null) {
                        for (ProductEntity e : all) {
                            if (e.productId == null && e.productName != null &&
                                    e.productName.trim().equalsIgnoreCase(p.getProductName().trim())) {
                                existing = e;
                                break;
                            }
                        }
                    }
                }

                if (existing != null) {
                    if ("PENDING".equalsIgnoreCase(existing.syncState) || "DELETE_PENDING".equalsIgnoreCase(existing.syncState)) {
                        return;
                    }

                    ProductEntity updated = mapProductToEntity(p);
                    updated.localId = existing.localId;
                    updated.syncState = "SYNCED";
                    updated.lastUpdated = now;
                    productDao.update(updated);
                } else {
                    ProductEntity newEntity = mapProductToEntity(p);
                    newEntity.syncState = "SYNCED";
                    newEntity.lastUpdated = now;
                    productDao.insert(newEntity);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void restoreArchived(String filename, OnProductRestoreListener listener) {
        syncExecutor.execute(() -> {
            try {
                File dir = new File(application.getFilesDir(), "archives");
                File file = new File(dir, filename);
                if (!file.exists()) {
                    if (listener != null) listener.onError("Archive file not found");
                    return;
                }

                BufferedReader reader = new BufferedReader(new FileReader(file));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                JSONObject obj = new JSONObject(sb.toString());
                Product p = parseProductFromJson(obj);

                String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
                if (ownerId == null || ownerId.isEmpty()) ownerId = AuthManager.getInstance().getCurrentUserId();

                if (ownerId != null) {
                    p.setOwnerAdminId(ownerId);
                    FirebaseFirestore.getInstance()
                            .collection("users").document(ownerId)
                            .collection("products").document(p.getProductId())
                            .set(p)
                            .addOnSuccessListener(aVoid -> {
                                syncExecutor.execute(() -> {
                                    ProductEntity entity = mapProductToEntity(p);
                                    productDao.insertProduct(entity);
                                    file.delete();
                                    refreshProducts();
                                    if (listener != null) listener.onProductRestored();
                                });
                            });
                } else {
                    ProductEntity entity = mapProductToEntity(p);
                    productDao.insertProduct(entity);
                    file.delete();
                    refreshProducts();
                    if (listener != null) listener.onProductRestored();
                }

            } catch (Exception e) {
                if (listener != null) listener.onError(e.getMessage());
            }
        });
    }

    public void permanentlyDeleteArchive(String filename, OnPermanentDeleteListener listener) {
        syncExecutor.execute(() -> {
            try {
                File dir = new File(application.getFilesDir(), "archives");
                if (!dir.exists()) {
                    if (listener != null) listener.onError("Archive folder not found");
                    return;
                }
                File f = new File(dir, filename);
                if (!f.exists()) {
                    if (listener != null) listener.onError("Archive file not found");
                    return;
                }

                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new FileReader(f));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();

                JSONObject o = new JSONObject(sb.toString());
                String productId = o.optString("productId", null);
                long localId = o.optLong("localId", 0);

                if (productId != null && !productId.isEmpty()) {
                    ProductEntity existing = findEntityByIdSafe(productId);
                    if (existing != null) productDao.deleteByLocalId(existing.localId);
                } else if (localId > 0) {
                    productDao.deleteByLocalId(localId);
                }

                boolean deleted = f.delete();
                if (!deleted) {
                    if (listener != null) listener.onError("Failed to remove archive file");
                    return;
                }
                if (listener != null) listener.onPermanentDeleted();
            } catch (Exception ex) {
                if (listener != null) listener.onError(ex.getMessage());
            }
        });
    }

    public void clearAllProductsLocal(OnCleanupListener listener) {
        syncExecutor.execute(() -> {
            try {
                int count = productDao.deleteAllProducts();
                refreshProducts();
                if (listener != null) listener.onCleanupComplete(count);
            } catch (Exception e) {
                if (listener != null) listener.onError(e.getMessage());
            }
        });
    }

    private Product parseProductFromJson(JSONObject m) throws JSONException {
        Product p = new Product();
        if (m.has("productId")) p.setProductId(m.getString("productId"));
        if (m.has("productName")) p.setProductName(m.getString("productName"));
        if (m.has("categoryId")) p.setCategoryId(m.getString("categoryId"));
        if (m.has("categoryName")) p.setCategoryName(m.getString("categoryName"));
        if (m.has("description")) p.setDescription(m.getString("description"));
        if (m.has("costPrice")) p.setCostPrice(m.getDouble("costPrice"));
        if (m.has("sellingPrice")) p.setSellingPrice(m.getDouble("sellingPrice"));
        if (m.has("quantity")) p.setQuantity(m.getDouble("quantity"));
        if (m.has("reorderLevel")) p.setReorderLevel(m.getInt("reorderLevel"));
        if (m.has("criticalLevel")) p.setCriticalLevel(m.getInt("criticalLevel"));
        if (m.has("ceilingLevel")) p.setCeilingLevel(m.getInt("ceilingLevel"));
        if (m.has("floorLevel")) p.setFloorLevel(m.getInt("floorLevel"));
        if (m.has("unit")) p.setUnit(m.getString("unit"));
        if (m.has("barcode")) p.setBarcode(m.getString("barcode"));
        if (m.has("supplier")) p.setSupplier(m.getString("supplier"));
        if (m.has("productLine")) p.setProductLine(m.getString("productLine"));
        if (m.has("dateAdded")) p.setDateAdded(m.getLong("dateAdded"));
        if (m.has("addedBy")) p.setAddedBy(m.getString("addedBy"));
        if (m.has("isActive")) p.setActive(m.getBoolean("isActive"));
        if (m.has("isSellable")) p.setSellable(m.getBoolean("isSellable"));
        if (m.has("productType")) p.setProductType(m.getString("productType"));
        if (m.has("expiryDate")) p.setExpiryDate(m.getLong("expiryDate"));
        if (m.has("imagePath")) p.setImagePath(m.getString("imagePath"));
        if (m.has("imageUrl")) p.setImageUrl(m.getString("imageUrl"));
        if (m.has("salesUnit")) p.setSalesUnit(m.getString("salesUnit"));
        if (m.has("piecesPerUnit")) p.setPiecesPerUnit(m.getInt("piecesPerUnit"));

        try {
            if (m.has("sizesList")) {
                JSONArray arr = m.getJSONArray("sizesList");
                List<Map<String,Object>> list = new ArrayList<>();
                for(int i=0; i<arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    Map<String,Object> map = new HashMap<>();
                    Iterator<String> keys = obj.keys();
                    while(keys.hasNext()) { String key = keys.next(); map.put(key, obj.get(key)); }
                    list.add(map);
                }
                p.setSizesList(list);
            }
        } catch(Exception e){}

        try {
            if (m.has("addonsList")) {
                JSONArray arr = m.getJSONArray("addonsList");
                List<Map<String,Object>> list = new ArrayList<>();
                for(int i=0; i<arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    Map<String,Object> map = new HashMap<>();
                    Iterator<String> keys = obj.keys();
                    while(keys.hasNext()) { String key = keys.next(); map.put(key, obj.get(key)); }
                    list.add(map);
                }
                p.setAddonsList(list);
            }
        } catch(Exception e){}

        try {
            if (m.has("notesList")) {
                JSONArray arr = m.getJSONArray("notesList");
                List<Map<String,String>> list = new ArrayList<>();
                for(int i=0; i<arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    Map<String,String> map = new HashMap<>();
                    Iterator<String> keys = obj.keys();
                    while(keys.hasNext()) { String key = keys.next(); map.put(key, obj.getString(key)); }
                    list.add(map);
                }
                p.setNotesList(list);
            }
        } catch(Exception e){}

        try {
            if (m.has("bomList")) {
                JSONArray arr = m.getJSONArray("bomList");
                List<Map<String,Object>> list = new ArrayList<>();
                for(int i=0; i<arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    Map<String,Object> map = new HashMap<>();
                    Iterator<String> keys = obj.keys();
                    while(keys.hasNext()) { String key = keys.next(); map.put(key, obj.get(key)); }
                    list.add(map);
                }
                p.setBomList(list);
            }
        } catch(Exception e){}

        return p;
    }

    private int computeDefaultCeiling(double quantity, int reorderLevel) {
        int result;
        if (reorderLevel > 0) result = Math.max((int) quantity, reorderLevel * 2);
        else result = Math.max((int) quantity, 100);
        if (result > 9999) result = 9999;
        return result;
    }

    public interface OnProductsFetchedListener {
        void onProductsFetched(List<Product> products);
        void onError(String error);
    }

    public interface OnProductFetchedListener {
        void onProductFetched(Product product);
        void onError(String error);
    }

    public interface OnProductAddedListener {
        void onProductAdded(String productId);
        void onError(String error);
    }

    public interface OnProductUpdatedListener {
        void onProductUpdated();
        void onError(String error);
    }

    public interface OnProductDeletedListener {
        void onProductDeleted(String archiveFilename);
        void onError(String error);
    }

    public interface OnProductRestoreListener {
        void onProductRestored();
        void onError(String error);
    }

    public interface OnProductInsertedListener {
        void onProductInserted(String productId);
        void onError(String error);
    }

    public interface OnPermanentDeleteListener {
        void onPermanentDeleted();
        void onError(String error);
    }

    public interface OnCleanupListener {
        void onCleanupComplete(int count);
        void onError(String error);
    }

    public static synchronized void resetInstance() {
        if (instance != null) {
            if (instance.currentSource != null) {
                instance.allProducts.removeSource(instance.currentSource);
            }
            instance = null;
        }
    }

    // ==============================================================================
    // CRITICAL FIX: Saves hundreds of products in a single database transaction!
    // This stops the UI from freezing and redrawing itself repeatedly.
    // ==============================================================================
    public void upsertFromRemoteBulk(List<Product> products) {
        syncExecutor.execute(() -> {
            // Run inside a single Transaction so LiveData only triggers ONE time at the very end
            db.runInTransaction(() -> {
                long now = System.currentTimeMillis();
                List<ProductEntity> allLocal = productDao.getAllProductsSync();

                for (Product p : products) {
                    if (p == null || p.getProductId() == null) continue;

                    ProductEntity existing = productDao.getByProductIdSync(p.getProductId());

                    // Fallback: Check if it exists by name if ID is missing locally
                    if (existing == null && allLocal != null) {
                        for (ProductEntity e : allLocal) {
                            if (e.productId == null && e.productName != null &&
                                    e.productName.trim().equalsIgnoreCase(p.getProductName().trim())) {
                                existing = e;
                                break;
                            }
                        }
                    }

                    if (existing != null) {
                        if ("PENDING".equalsIgnoreCase(existing.syncState) || "DELETE_PENDING".equalsIgnoreCase(existing.syncState)) {
                            continue; // Don't overwrite local changes that haven't uploaded yet
                        }
                        ProductEntity updated = mapProductToEntity(p);
                        updated.localId = existing.localId;
                        updated.syncState = "SYNCED";
                        updated.lastUpdated = now;
                        productDao.update(updated);
                    } else {
                        ProductEntity newEntity = mapProductToEntity(p);
                        newEntity.syncState = "SYNCED";
                        newEntity.lastUpdated = now;
                        productDao.insert(newEntity);
                    }
                }
            });
        });
    }

    // =======================================================================================
    // CRITICAL FIX: Safe JSON Converters to prevent local database crashes!
    // =======================================================================================
    private String safeListToJson(List<?> list) {
        if (list == null || list.isEmpty()) return "[]";
        try {
            org.json.JSONArray jsonArray = new org.json.JSONArray();
            for (Object item : list) {
                if (item instanceof Map) {
                    jsonArray.put(new org.json.JSONObject((Map<?, ?>) item));
                } else if (item instanceof String) {
                    jsonArray.put(item);
                }
            }
            return jsonArray.toString();
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<Map<String, Object>> safeJsonToList(String json) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (json == null || json.trim().isEmpty() || json.equals("[]")) return result;
        try {
            org.json.JSONArray jsonArray = new org.json.JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                org.json.JSONObject obj = jsonArray.getJSONObject(i);
                Map<String, Object> map = new HashMap<>();
                java.util.Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    map.put(key, obj.get(key));
                }
                result.add(map);
            }
        } catch (Exception e) {}
        return result;
    }

    private List<Map<String, String>> safeJsonToNotesList(String json) {
        List<Map<String, String>> result = new ArrayList<>();
        if (json == null || json.trim().isEmpty() || json.equals("[]")) return result;
        try {
            org.json.JSONArray jsonArray = new org.json.JSONArray(json);
            for (int i = 0; i < jsonArray.length(); i++) {
                org.json.JSONObject obj = jsonArray.getJSONObject(i);
                Map<String, String> map = new HashMap<>();
                java.util.Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    map.put(key, String.valueOf(obj.get(key)));
                }
                result.add(map);
            }
        } catch (Exception e) {}
        return result;
    }
}