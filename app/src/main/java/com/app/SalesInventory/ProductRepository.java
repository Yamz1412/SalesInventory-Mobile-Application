package com.app.SalesInventory;

import android.app.Application;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;

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

    public interface OnCriticalStockListener {
        void onProductCritical(Product product);
    }

    private ProductRepository(Application application) {
        this.application = application;
        db = AppDatabase.getInstance(application);
        productDao = db.productDao();
        allProducts = new MediatorLiveData<>();

        currentSource = productDao.getAllProductsLive();
        allProducts.addSource(currentSource, entities -> {
            List<Product> list = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();

            if (entities != null) {
                for (ProductEntity e : entities) {
                    Product p = mapEntityToProduct(e);
                    if (p.getProductId() != null && !seenIds.contains(p.getProductId())) {
                        list.add(p);
                        seenIds.add(p.getProductId());
                    } else if (p.getProductId() == null) {
                        list.add(p);
                    }
                }
            }
            allProducts.setValue(list);
        });
        alertRepository = AlertRepository.getInstance(application);
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
        Executors.newSingleThreadExecutor().execute(() -> {
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
        Executors.newSingleThreadExecutor().execute(() -> {
            db.clearAllTables();
            allProducts.postValue(new ArrayList<>());
        });
    }

    public void updateProductImage(String productId, String imagePath, String imageUrl, OnProductUpdatedListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
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

    public LiveData<List<Product>> getAllProducts() {
        return allProducts;
    }

    public void fetchAllProductsAsync(OnProductsFetchedListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
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

    public void addProduct(Product product, android.net.Uri imageUri, OnProductAddedListener listener) {
        String path = (imageUri != null) ? imageUri.toString() : null;
        addProduct(product, path, listener);
    }

    public void addProduct(Product product, String imagePath, OnProductAddedListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (!AuthManager.getInstance().isCurrentUserApproved()) {
                listener.onError("User not approved");
                return;
            }
            if (product.getProductId() != null && !product.getProductId().isEmpty()) {
                ProductEntity existing = findEntityByIdSafe(product.getProductId());
                if (existing != null) {
                    listener.onError("Product already exists.");
                    return;
                }
            }

            long now = System.currentTimeMillis();
            ProductEntity e = mapProductToEntity(product);
            if (e.floorLevel < 1) e.floorLevel = 1;
            if (e.criticalLevel < 1) e.criticalLevel = 1;
            if (e.ceilingLevel <= 0) e.ceilingLevel = computeDefaultCeiling(e.quantity, e.reorderLevel);
            if (e.ceilingLevel > 9999) e.ceilingLevel = 9999;
            if (e.quantity > e.ceilingLevel) e.ceilingLevel = e.quantity;

            e.dateAdded = now;
            e.lastUpdated = now;
            e.syncState = "PENDING";
            if (imagePath != null && !imagePath.isEmpty()) e.imagePath = imagePath;

            long localId = productDao.insert(e);
            checkExpiryForEntity(e);
            checkFloorForEntity(e);
            SyncScheduler.enqueueImmediateSync(application.getApplicationContext());
            listener.onProductAdded("local:" + localId);
        });
    }

    public void updateProduct(Product product, OnProductUpdatedListener listener) {
        updateProduct(product, null, listener);
    }

    public void updateProduct(Product product, String imagePath, OnProductUpdatedListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (!AuthManager.getInstance().isCurrentUserApproved()) {
                listener.onError("User not approved");
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

                existing.sizesListJson = serializeListObj(product.getSizesList());
                existing.addonsListJson = serializeListObj(product.getAddonsList());
                existing.variantsListJson = serializeListObj(product.getVariantsList());
                existing.bomListJson = serializeListObj(product.getBomList());
                existing.notesListJson = serializeListStr(product.getNotesList());

                if (existing.floorLevel < 1) existing.floorLevel = 1;
                if (existing.criticalLevel < 1) existing.criticalLevel = 1;
                if (existing.ceilingLevel <= 0) existing.ceilingLevel = computeDefaultCeiling(existing.quantity, existing.reorderLevel);
                if (existing.ceilingLevel > 9999) existing.ceilingLevel = 9999;
                if (existing.quantity > existing.ceilingLevel) existing.ceilingLevel = existing.quantity;

                existing.lastUpdated = now;
                existing.syncState = "PENDING";
                if (imagePath != null && !imagePath.isEmpty()) {
                    existing.imagePath = imagePath;
                } else {
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
                if (e.quantity > e.ceilingLevel) e.ceilingLevel = e.quantity;

                e.lastUpdated = now;
                e.syncState = "PENDING";
                if (imagePath != null && !imagePath.isEmpty()) e.imagePath = imagePath;
                productDao.insert(e);
                checkExpiryForEntity(e);
                checkFloorForEntity(e);
            }
            SyncScheduler.enqueueImmediateSync(application.getApplicationContext());
            listener.onProductUpdated();
        });
    }

    public void cleanupDuplicates(OnCleanupListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
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
        Executors.newSingleThreadExecutor().execute(() -> {
            if (!AuthManager.getInstance().isCurrentUserAdmin()) {
                listener.onError("Unauthorized");
                return;
            }
            ProductEntity existing = findEntityByIdSafe(productId);
            String archiveFilename = null;
            if (existing != null) {
                archiveFilename = archiveEntityLocally(existing);
                long now = System.currentTimeMillis();
                existing.isActive = false;
                existing.lastUpdated = now;
                existing.syncState = "DELETE_PENDING";
                productDao.update(existing);
            }
            SyncScheduler.enqueueImmediateSync(application.getApplicationContext());
            listener.onProductDeleted(archiveFilename);
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
            o.put("sizesListJson", e.sizesListJson);
            o.put("addonsListJson", e.addonsListJson);
            o.put("notesListJson", e.notesListJson);
            o.put("variantsListJson", e.variantsListJson);
            o.put("bomListJson", e.bomListJson);
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

    public void updateProductQuantity(String productId, int newQuantity, OnProductUpdatedListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ProductEntity existing = findEntityByIdSafe(productId);
                if (existing != null) {
                    int oldQuantity = existing.quantity;
                    if (existing.floorLevel < 1) existing.floorLevel = 1;
                    if (existing.criticalLevel < 1) existing.criticalLevel = 1;
                    if (existing.ceilingLevel <= 0) existing.ceilingLevel = computeDefaultCeiling(existing.quantity, existing.reorderLevel);
                    if (existing.ceilingLevel > 9999) existing.ceilingLevel = 9999;

                    int clamped = Math.max(0, newQuantity);
                    if (clamped > 99999) clamped = 99999;
                    if (clamped > existing.ceilingLevel) {
                        existing.ceilingLevel = clamped;
                    }

                    existing.quantity = clamped;
                    existing.lastUpdated = System.currentTimeMillis();
                    existing.syncState = "PENDING";
                    productDao.update(existing);

                    SyncScheduler.enqueueImmediateSync(application.getApplicationContext());

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
                        listener.onProductUpdated();
                    }
                    checkExpiryForEntity(existing);
                } else {
                    if (listener != null) listener.onError("Product not found locally");
                }
            } catch (Exception e) {
                if (listener != null) listener.onError("Update failed: " + e.getMessage());
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
        String message = "Low stock for " + name + " (Qty: " + e.quantity + ")";
        alertRepository.addAlertIfNotExists(e.productId, "LOW_STOCK", message, System.currentTimeMillis(), new AlertRepository.OnAlertAddedListener() {
            @Override
            public void onAlertAdded(String alertId) {}
            @Override
            public void onError(String error) {}
        });
    }

    private void createCriticalStockAlert(ProductEntity e) {
        if (alertRepository == null) return;
        String name = e.productName == null ? "" : e.productName;
        String message = "Critical stock for " + name + " (Qty: " + e.quantity + ")";
        alertRepository.addAlertIfNotExists(e.productId, "CRITICAL_STOCK", message, System.currentTimeMillis(), new AlertRepository.OnAlertAddedListener() {
            @Override
            public void onAlertAdded(String alertId) {}
            @Override
            public void onError(String error) {}
        });
    }

    private void createFloorLevelAlert(ProductEntity e) {
        if (alertRepository == null) return;
        String name = e.productName == null ? "" : e.productName;
        String message = "Stock at or below floor level for " + name + " (Qty: " + e.quantity + ", Floor: " + e.floorLevel + ")";
        alertRepository.addAlertIfNotExists(e.productId, "FLOOR_STOCK", message, System.currentTimeMillis(), new AlertRepository.OnAlertAddedListener() {
            @Override
            public void onAlertAdded(String alertId) {}
            @Override
            public void onError(String error) {}
        });
    }

    private void createExpiryAlert(ProductEntity e, String type) {
        if (alertRepository == null) return;
        String name = e.productName == null ? "" : e.productName;
        String message;
        if ("EXPIRY_7_DAYS".equals(type)) {
            message = "Product \"" + name + "\" will expire in 7 days or less.";
        } else if ("EXPIRY_3_DAYS".equals(type)) {
            message = "Product \"" + name + "\" will expire in 3 days or less.";
        } else if ("EXPIRED".equals(type)) {
            message = "Product \"" + name + "\" has expired.";
        } else {
            message = "Expiry alert for " + name + ".";
        }
        alertRepository.addAlertIfNotExists(e.productId, type, message, System.currentTimeMillis(), new AlertRepository.OnAlertAddedListener() {
            @Override
            public void onAlertAdded(String alertId) {}
            @Override
            public void onError(String error) {}
        });
    }

    private void checkFloorForEntity(ProductEntity e) {
        if (e == null) return;
        if (e.floorLevel <= 0) return;
        if (e.quantity <= e.floorLevel) {
            createFloorLevelAlert(e);
        }
    }

    private void checkExpiryForEntity(ProductEntity e) {
        if (e == null) return;
        if (e.expiryDate <= 0) return;
        long now = System.currentTimeMillis();
        long diffMillis = e.expiryDate - now;
        long days = diffMillis / (24L * 60L * 60L * 1000L);
        if (diffMillis <= 0) {
            createExpiryAlert(e, "EXPIRED");
        } else if (days <= 3) {
            createExpiryAlert(e, "EXPIRY_3_DAYS");
        } else if (days <= 7) {
            createExpiryAlert(e, "EXPIRY_7_DAYS");
        }
    }

    public void runExpirySweep() {
        Executors.newSingleThreadExecutor().execute(() -> {
            List<ProductEntity> entities = productDao.getAllProductsSync();
            if (entities == null) return;
            for (ProductEntity e : entities) {
                checkExpiryForEntity(e);
                checkFloorForEntity(e);
            }
        });
    }

    public void getProductById(String productId, OnProductFetchedListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            ProductEntity e = findEntityByIdSafe(productId);
            if (e != null) {
                listener.onProductFetched(mapEntityToProduct(e));
            } else {
                listener.onError("Product not found");
            }
        });
    }

    // ===========================================================================
    // JSON SERIALIZATION HELPERS
    // ===========================================================================
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
        if (json == null || json.isEmpty()) return null;
        List<Map<String, Object>> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Map<String, Object> map = new HashMap<>();
                java.util.Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    map.put(key, obj.get(key));
                }
                list.add(map);
            }
        } catch (Exception e) { }
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
        if (json == null || json.isEmpty()) return null;
        List<Map<String, String>> list = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                Map<String, String> map = new HashMap<>();
                java.util.Iterator<String> keys = obj.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    map.put(key, obj.getString(key));
                }
                list.add(map);
            }
        } catch (Exception e) { }
        return list;
    }
    // ===========================================================================

    private Product mapEntityToProduct(ProductEntity e) {
        Product p = new Product();
        p.setLocalId(e.localId);
        p.setProductId(e.productId);
        p.setProductName(e.productName);
        p.setCategoryId(e.categoryId);
        p.setCategoryName(e.categoryName);
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

        // MAP LISTS BACK TO PRODUCT
        p.setSizesList(deserializeListObj(e.sizesListJson));
        p.setAddonsList(deserializeListObj(e.addonsListJson));
        p.setVariantsList(deserializeListObj(e.variantsListJson));
        p.setBomList(deserializeListObj(e.bomListJson));
        p.setNotesList(deserializeListStr(e.notesListJson));

        return p;
    }

    private ProductEntity mapProductToEntity(Product p) {
        ProductEntity e = new ProductEntity();
        e.localId = p.getLocalId();
        e.productId = p.getProductId();
        e.productName = p.getProductName();
        e.categoryId = p.getCategoryId();
        e.categoryName = p.getCategoryName();
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

        // MAP LISTS INTO ENTITY
        e.sizesListJson = serializeListObj(p.getSizesList());
        e.addonsListJson = serializeListObj(p.getAddonsList());
        e.variantsListJson = serializeListObj(p.getVariantsList());
        e.bomListJson = serializeListObj(p.getBomList());
        e.notesListJson = serializeListStr(p.getNotesList());

        return e;
    }

    public void retrySync(long localId) {
        Executors.newSingleThreadExecutor().execute(() -> {
            ProductEntity e = productDao.getByLocalId(localId);
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
            if (entities != null) {
                for (ProductEntity e : entities) {
                    Product p = mapEntityToProduct(e);
                    if (p.getProductId() != null && !seenIds.contains(p.getProductId())) {
                        list.add(p);
                        seenIds.add(p.getProductId());
                    } else if (p.getProductId() == null) {
                        list.add(p);
                    }
                }
            }
            allProducts.setValue(list);
        });
    }

    public void upsertFromRemote(Product p) {
        if (p == null || p.getProductId() == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                ProductEntity existing = productDao.getByProductIdSync(p.getProductId());
                long now = System.currentTimeMillis();

                if (existing != null) {
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
        Executors.newSingleThreadExecutor().execute(() -> {
            File dir = new File(application.getFilesDir(), "archives");
            if (!dir.exists()) {
                listener.onError("Archive not found");
                return;
            }
            File f = new File(dir, filename);
            if (!f.exists()) {
                listener.onError("Archive file not found");
                return;
            }
            try {
                StringBuilder sb = new StringBuilder();
                BufferedReader br = new BufferedReader(new FileReader(f));
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                JSONObject o = new JSONObject(sb.toString());
                ProductEntity e = new ProductEntity();
                e.localId = o.optLong("localId", 0);
                e.productId = o.optString("productId", null);
                e.productName = o.optString("productName", null);
                e.categoryId = o.optString("categoryId", null);
                e.categoryName = o.optString("categoryName", null);
                e.description = o.optString("description", null);
                e.costPrice = o.optDouble("costPrice", 0.0);
                e.sellingPrice = o.optDouble("sellingPrice", 0.0);
                e.quantity = o.optInt("quantity", 0);
                e.reorderLevel = o.optInt("reorderLevel", 0);
                e.criticalLevel = o.optInt("criticalLevel", 0);
                e.ceilingLevel = o.optInt("ceilingLevel", 0);
                e.floorLevel = o.optInt("floorLevel", 0);
                e.unit = o.optString("unit", null);
                e.barcode = o.optString("barcode", null);
                e.supplier = o.optString("supplier", null);
                e.dateAdded = o.optLong("dateAdded", System.currentTimeMillis());
                e.addedBy = o.optString("addedBy", null);
                e.isActive = true;
                e.imagePath = o.optString("imagePath", null);
                e.imageUrl = o.optString("imageUrl", null);
                e.expiryDate = o.optLong("expiryDate", 0);
                e.productType = o.optString("productType", null);
                e.lastUpdated = System.currentTimeMillis();
                e.syncState = "PENDING";

                e.sizesListJson = o.optString("sizesListJson", null);
                e.addonsListJson = o.optString("addonsListJson", null);
                e.notesListJson = o.optString("notesListJson", null);
                e.variantsListJson = o.optString("variantsListJson", null);
                e.bomListJson = o.optString("bomListJson", null);

                if (e.floorLevel < 1) e.floorLevel = 1;
                if (e.criticalLevel < 1) e.criticalLevel = 1;
                if (e.ceilingLevel <= 0) e.ceilingLevel = computeDefaultCeiling(e.quantity, e.reorderLevel);
                if (e.ceilingLevel > 9999) e.ceilingLevel = 9999;

                ProductEntity existing = null;
                if (e.productId != null && !e.productId.isEmpty()) existing = findEntityByIdSafe(e.productId);

                if (existing != null) {
                    existing.productName = e.productName;
                    existing.categoryId = e.categoryId;
                    existing.categoryName = e.categoryName;
                    existing.description = e.description;
                    existing.costPrice = e.costPrice;
                    existing.sellingPrice = e.sellingPrice;
                    existing.quantity = e.quantity;
                    if (existing.quantity > existing.ceilingLevel) existing.ceilingLevel = existing.quantity;
                    existing.reorderLevel = e.reorderLevel;
                    existing.criticalLevel = e.criticalLevel;
                    existing.floorLevel = e.floorLevel;
                    existing.unit = e.unit;
                    existing.barcode = e.barcode;
                    existing.supplier = e.supplier;
                    existing.dateAdded = e.dateAdded;
                    existing.addedBy = e.addedBy;
                    existing.isActive = true;
                    existing.imagePath = e.imagePath;
                    existing.imageUrl = e.imageUrl;
                    existing.expiryDate = e.expiryDate;
                    existing.productType = e.productType;
                    existing.lastUpdated = e.lastUpdated;
                    existing.syncState = "PENDING";
                    existing.sizesListJson = e.sizesListJson;
                    existing.addonsListJson = e.addonsListJson;
                    existing.notesListJson = e.notesListJson;
                    existing.variantsListJson = e.variantsListJson;
                    existing.bomListJson = e.bomListJson;
                    productDao.update(existing);
                } else {
                    productDao.insert(e);
                }
                SyncScheduler.enqueueImmediateSync(application.getApplicationContext());
                boolean deleted = f.delete();
                if (!deleted) {
                    listener.onError("Failed to remove archive file");
                    return;
                }
                listener.onProductRestored();
            } catch (JSONException je) {
                listener.onError("Invalid archive format");
            } catch (Exception ex) {
                listener.onError(ex.getMessage() == null ? "Error restoring" : ex.getMessage());
            }
        });
    }

    public void permanentlyDeleteArchive(String filename, OnPermanentDeleteListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                File dir = new File(application.getFilesDir(), "archives");
                if (!dir.exists()) {
                    listener.onError("Archive folder not found");
                    return;
                }
                File f = new File(dir, filename);
                if (!f.exists()) {
                    listener.onError("Archive file not found");
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
                    if (existing != null) {
                        productDao.deleteByLocalId(existing.localId);
                    }
                } else if (localId > 0) {
                    productDao.deleteByLocalId(localId);
                }
                boolean deleted = f.delete();
                if (!deleted) {
                    listener.onError("Failed to remove archive file");
                    return;
                }
                listener.onPermanentDeleted();
            } catch (Exception ex) {
                listener.onError(ex.getMessage() == null ? "Error deleting archive" : ex.getMessage());
            }
        });
    }

    private int computeDefaultCeiling(int quantity, int reorderLevel) {
        int result;
        if (reorderLevel > 0) {
            result = Math.max(quantity, reorderLevel * 2);
        } else {
            result = Math.max(quantity, 100);
        }
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

    public interface OnPermanentDeleteListener {
        void onPermanentDeleted();
        void onError(String error);
    }

    public interface OnCleanupListener {
        void onCleanupComplete(int count);
        void onError(String error);
    }
}