package com.app.SalesInventory;

import android.app.Application;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

public class ProductRepository {
    private static ProductRepository instance;
    private AppDatabase db;
    private ProductDao productDao;
    private MediatorLiveData<List<Product>> allProducts;
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
        LiveData<List<ProductEntity>> source = productDao.getAllProductsLive();
        allProducts.addSource(source, entities -> {
            List<Product> list = new ArrayList<>();
            if (entities != null) {
                for (ProductEntity e : entities) {
                    Product p = mapEntityToProduct(e);
                    list.add(p);
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
            if (entities != null) {
                for (ProductEntity e : entities) {
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
                if (entities != null) {
                    for (ProductEntity e : entities) {
                        Product p = mapEntityToProduct(e);
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
    public void addProduct(Product product, OnProductAddedListener listener) {
        addProduct(product, null, listener);
    }
    public void addProduct(Product product, String imagePath, OnProductAddedListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (!AuthManager.getInstance().isCurrentUserApproved()) {
                listener.onError("User not approved");
                return;
            }
            long now = System.currentTimeMillis();
            ProductEntity e = mapProductToEntity(product);
            if (e.floorLevel < 1) e.floorLevel = 1;
            if (e.criticalLevel < 1) e.criticalLevel = 1;
            if (e.ceilingLevel <= 0) e.ceilingLevel = computeDefaultCeiling(e.quantity, e.reorderLevel);
            if (e.ceilingLevel > 9999) e.ceilingLevel = 9999;
            if (e.quantity > e.ceilingLevel) e.quantity = e.ceilingLevel;
            if (e.quantity < e.floorLevel) e.quantity = e.floorLevel;
            e.dateAdded = now;
            e.lastUpdated = now;
            e.syncState = "PENDING";
            if (imagePath != null && !imagePath.isEmpty()) {
                e.imagePath = imagePath;
            }
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
            ProductEntity existing = null;
            if (product.getProductId() != null && !product.getProductId().isEmpty()) {
                existing = productDao.getByProductIdSync(product.getProductId());
            }
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
                if (existing.floorLevel < 1) existing.floorLevel = 1;
                if (existing.criticalLevel < 1) existing.criticalLevel = 1;
                if (existing.ceilingLevel <= 0) existing.ceilingLevel = computeDefaultCeiling(existing.quantity, existing.reorderLevel);
                if (existing.ceilingLevel > 9999) existing.ceilingLevel = 9999;
                if (existing.quantity > existing.ceilingLevel) existing.quantity = existing.ceilingLevel;
                if (existing.quantity < existing.floorLevel) existing.quantity = existing.floorLevel;
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
                if (e.quantity > e.ceilingLevel) e.quantity = e.ceilingLevel;
                if (e.quantity < e.floorLevel) e.quantity = e.floorLevel;
                e.lastUpdated = now;
                e.syncState = "PENDING";
                if (imagePath != null && !imagePath.isEmpty()) {
                    e.imagePath = imagePath;
                }
                productDao.insert(e);
                checkExpiryForEntity(e);
                checkFloorForEntity(e);
            }
            SyncScheduler.enqueueImmediateSync(application.getApplicationContext());
            listener.onProductUpdated();
        });
    }
    public void deleteProduct(String productId, OnProductDeletedListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            if (!AuthManager.getInstance().isCurrentUserAdmin()) {
                listener.onError("Unauthorized");
                return;
            }
            ProductEntity existing = productDao.getByProductIdSync(productId);
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
                if (e.floorLevel < 1) e.floorLevel = 1;
                if (e.criticalLevel < 1) e.criticalLevel = 1;
                if (e.ceilingLevel <= 0) e.ceilingLevel = computeDefaultCeiling(e.quantity, e.reorderLevel);
                if (e.ceilingLevel > 9999) e.ceilingLevel = 9999;
                ProductEntity existing = null;
                if (e.productId != null && !e.productId.isEmpty()) existing = productDao.getByProductIdSync(e.productId);
                if (existing != null) {
                    existing.productName = e.productName;
                    existing.categoryId = e.categoryId;
                    existing.categoryName = e.categoryName;
                    existing.description = e.description;
                    existing.costPrice = e.costPrice;
                    existing.sellingPrice = e.sellingPrice;
                    existing.quantity = Math.min(e.quantity, e.ceilingLevel);
                    if (existing.quantity < existing.floorLevel) existing.quantity = existing.floorLevel;
                    existing.reorderLevel = e.reorderLevel;
                    existing.criticalLevel = e.criticalLevel;
                    existing.ceilingLevel = e.ceilingLevel;
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
                    ProductEntity existing = productDao.getByProductIdSync(productId);
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
    private String sanitizeFilename(String s) {
        if (s == null) return "x";
        return s.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
    public void updateProductQuantity(String productId, int newQuantity, OnProductUpdatedListener listener) {
        Executors.newSingleThreadExecutor().execute(() -> {
            ProductEntity existing = productDao.getByProductIdSync(productId);
            if (existing != null) {
                int oldQuantity = existing.quantity;
                if (existing.floorLevel < 1) existing.floorLevel = 1;
                if (existing.criticalLevel < 1) existing.criticalLevel = 1;
                if (existing.ceilingLevel <= 0) existing.ceilingLevel = computeDefaultCeiling(existing.quantity, existing.reorderLevel);
                if (existing.ceilingLevel > 9999) existing.ceilingLevel = 9999;
                int clamped = Math.max(existing.floorLevel, Math.min(existing.ceilingLevel, newQuantity));
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
                checkExpiryForEntity(existing);
                listener.onProductUpdated();
            } else {
                listener.onError("Product not found locally");
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
            public void onAlertAdded(String alertId) {
            }
            @Override
            public void onError(String error) {
            }
        });
    }
    private void createCriticalStockAlert(ProductEntity e) {
        if (alertRepository == null) return;
        String name = e.productName == null ? "" : e.productName;
        String message = "Critical stock for " + name + " (Qty: " + e.quantity + ")";
        alertRepository.addAlertIfNotExists(e.productId, "CRITICAL_STOCK", message, System.currentTimeMillis(), new AlertRepository.OnAlertAddedListener() {
            @Override
            public void onAlertAdded(String alertId) {
            }
            @Override
            public void onError(String error) {
            }
        });
    }
    private void createFloorLevelAlert(ProductEntity e) {
        if (alertRepository == null) return;
        String name = e.productName == null ? "" : e.productName;
        String message = "Stock at or below floor level for " + name + " (Qty: " + e.quantity + ", Floor: " + e.floorLevel + ")";
        alertRepository.addAlertIfNotExists(e.productId, "FLOOR_STOCK", message, System.currentTimeMillis(), new AlertRepository.OnAlertAddedListener() {
            @Override
            public void onAlertAdded(String alertId) {
            }
            @Override
            public void onError(String error) {
            }
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
            public void onAlertAdded(String alertId) {
            }
            @Override
            public void onError(String error) {
            }
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
            ProductEntity e = productDao.getByProductIdSync(productId);
            if (e != null) {
                listener.onProductFetched(mapEntityToProduct(e));
            } else {
                listener.onError("Product not found");
            }
        });
    }
    public void upsertFromRemote(Product p) {
        if (p == null || p.getProductId() == null) return;
        Executors.newSingleThreadExecutor().execute(() -> {
            ProductEntity existing = productDao.getByProductIdSync(p.getProductId());
            long now = System.currentTimeMillis();
            if (existing != null) {
                existing.productName = p.getProductName();
                existing.categoryId = p.getCategoryId();
                existing.categoryName = p.getCategoryName();
                existing.description = p.getDescription();
                existing.costPrice = p.getCostPrice();
                existing.sellingPrice = p.getSellingPrice();
                existing.quantity = p.getQuantity();
                existing.reorderLevel = p.getReorderLevel();
                existing.criticalLevel = p.getCriticalLevel();
                existing.ceilingLevel = p.getCeilingLevel();
                existing.floorLevel = p.getFloorLevel();
                existing.unit = p.getUnit();
                existing.barcode = p.getBarcode();
                existing.supplier = p.getSupplier();
                existing.dateAdded = p.getDateAdded();
                existing.addedBy = p.getAddedBy();
                existing.isActive = p.isActive();
                existing.imageUrl = p.getImageUrl();
                if (p.getImagePath() != null && !p.getImagePath().isEmpty()) {
                    existing.imagePath = p.getImagePath();
                }
                existing.expiryDate = p.getExpiryDate();
                existing.productType = p.getProductType();
                existing.lastUpdated = now;
                existing.syncState = "SYNCED";
                if (existing.floorLevel < 1) existing.floorLevel = 1;
                if (existing.criticalLevel < 1) existing.criticalLevel = 1;
                if (existing.ceilingLevel <= 0) existing.ceilingLevel = computeDefaultCeiling(existing.quantity, existing.reorderLevel);
                if (existing.ceilingLevel > 9999) existing.ceilingLevel = 9999;
                if (existing.quantity > existing.ceilingLevel) existing.quantity = existing.ceilingLevel;
                productDao.update(existing);
                checkExpiryForEntity(existing);
                checkFloorForEntity(existing);
            } else {
                ProductEntity e = new ProductEntity();
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
                e.imageUrl = p.getImageUrl();
                e.imagePath = p.getImagePath();
                e.expiryDate = p.getExpiryDate();
                e.productType = p.getProductType();
                e.lastUpdated = now;
                e.syncState = "SYNCED";
                if (e.floorLevel < 1) e.floorLevel = 1;
                if (e.criticalLevel < 1) e.criticalLevel = 1;
                if (e.ceilingLevel <= 0) e.ceilingLevel = computeDefaultCeiling(e.quantity, e.reorderLevel);
                if (e.ceilingLevel > 9999) e.ceilingLevel = 9999;
                if (e.quantity > e.ceilingLevel) e.quantity = e.ceilingLevel;
                productDao.insert(e);
                checkExpiryForEntity(e);
                checkFloorForEntity(e);
            }
        });
    }
    private ProductEntity mapProductToEntity(Product p) {
        ProductEntity e = new ProductEntity();
        if (p == null) return e;
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
        e.imagePath = p.getImagePath();
        e.imageUrl = p.getImageUrl();
        e.expiryDate = p.getExpiryDate();
        e.productType = p.getProductType();
        return e;
    }
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
        p.setExpiryDate(e.expiryDate);
        p.setProductType(e.productType);
        return p;
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
}