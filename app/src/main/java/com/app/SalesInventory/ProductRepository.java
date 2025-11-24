package com.app.SalesInventory;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.app.SalesInventory.FirestoreManager;
import com.app.SalesInventory.FirestoreSyncListener;
import com.app.SalesInventory.Product;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ProductRepository {
    private static final String TAG = "ProductRepository";
    private static ProductRepository instance;

    private FirestoreManager firestoreManager;
    private FirestoreSyncListener syncListener;
    private MutableLiveData<List<Product>> allProducts;

    private ProductRepository(Application application) {
        this.firestoreManager = FirestoreManager.getInstance();
        this.syncListener = FirestoreSyncListener.getInstance();
        this.allProducts = new MutableLiveData<>();

        startRealtimeSync();
    }

    public static synchronized ProductRepository getInstance(Application application) {
        if (instance == null) {
            instance = new ProductRepository(application);
        }
        return instance;
    }

    private void startRealtimeSync() {
        if (!firestoreManager.isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated. Cannot start sync.");
            return;
        }

        syncListener.listenToProducts(new FirestoreSyncListener.OnProductsChangedListener() {
            @Override
            public void onProductsChanged(QuerySnapshot snapshot) {
                List<Product> productList = new ArrayList<>();

                if (snapshot != null) {
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        Product product = document.toObject(Product.class);
                        if (product != null) {
                            product.setProductId(document.getId());
                            productList.add(product);
                        }
                    }
                }

                allProducts.setValue(productList);
                Log.d(TAG, "Products synced from Firestore: " + productList.size());
            }
        });
    }

    /**
     * Get all products (LiveData)
     */
    public LiveData<List<Product>> getAllProducts() {
        return allProducts;
    }

    /**
     * Get all products (async)
     */
    public void fetchAllProductsAsync(OnProductsFetchedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        firestoreManager.getDb()
                .collection(firestoreManager.getUserProductsPath())
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Product> products = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Product product = doc.toObject(Product.class);
                        if (product != null) {
                            product.setProductId(doc.getId());
                            products.add(product);
                        }
                    }
                    listener.onProductsFetched(products);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching products", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Add new product to Firestore
     */
    public void addProduct(Product product, OnProductAddedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        // Convert product to map
        Map<String, Object> productMap = convertProductToMap(product);
        productMap.put("createdAt", firestoreManager.getServerTimestamp());
        productMap.put("lastUpdated", firestoreManager.getServerTimestamp());

        firestoreManager.getDb()
                .collection(firestoreManager.getUserProductsPath())
                .add(productMap)
                .addOnSuccessListener(documentReference -> {
                    String productId = documentReference.getId();
                    product.setProductId(productId);
                    listener.onProductAdded(productId);
                    Log.d(TAG, "Product added: " + productId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding product", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Update existing product
     */
    public void updateProduct(Product product, OnProductUpdatedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        if (product.getProductId() == null || product.getProductId().isEmpty()) {
            listener.onError("Product ID is empty");
            return;
        }

        Map<String, Object> productMap = convertProductToMap(product);
        productMap.put("lastUpdated", firestoreManager.getServerTimestamp());

        firestoreManager.getDb()
                .collection(firestoreManager.getUserProductsPath())
                .document(product.getProductId())
                .update(productMap)
                .addOnSuccessListener(aVoid -> {
                    listener.onProductUpdated();
                    Log.d(TAG, "Product updated: " + product.getProductId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating product", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Delete product
     */
    public void deleteProduct(String productId, OnProductDeletedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        if (productId == null || productId.isEmpty()) {
            listener.onError("Product ID is empty");
            return;
        }

        firestoreManager.getDb()
                .collection(firestoreManager.getUserProductsPath())
                .document(productId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    listener.onProductDeleted();
                    Log.d(TAG, "Product deleted: " + productId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting product", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Update product quantity
     */
    public void updateProductQuantity(String productId, int newQuantity, OnProductUpdatedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("quantity", newQuantity);
        updates.put("lastUpdated", firestoreManager.getServerTimestamp());

        firestoreManager.getDb()
                .collection(firestoreManager.getUserProductsPath())
                .document(productId)
                .update(updates)
                .addOnSuccessListener(aVoid -> {
                    listener.onProductUpdated();
                    Log.d(TAG, "Product quantity updated: " + productId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating quantity", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Get product by ID
     */
    public void getProductById(String productId, OnProductFetchedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        firestoreManager.getDb()
                .collection(firestoreManager.getUserProductsPath())
                .document(productId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Product product = documentSnapshot.toObject(Product.class);
                        if (product != null) {
                            product.setProductId(documentSnapshot.getId());
                            listener.onProductFetched(product);
                        }
                    } else {
                        listener.onError("Product not found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching product", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Get products by category
     */
    public void getProductsByCategory(String category, OnProductsFetchedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        firestoreManager.getDb()
                .collection(firestoreManager.getUserProductsPath())
                .whereEqualTo("category", category)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Product> products = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        Product product = doc.toObject(Product.class);
                        if (product != null) {
                            product.setProductId(doc.getId());
                            products.add(product);
                        }
                    }
                    listener.onProductsFetched(products);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching products by category", e);
                    listener.onError(e.getMessage());
                });
    }

    /**
     * Convert Product object to Map for Firestore
     */
    private Map<String, Object> convertProductToMap(Product product) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", product.getProductName());
        map.put("category", product.getCategoryName());
        map.put("quantity", product.getQuantity());
        map.put("costPrice", product.getCostPrice());
        map.put("sellingPrice", product.getSellingPrice());
        map.put("unit", product.getUnit());
        map.put("reorderLevel", product.getReorderLevel());
        return map;
    }

    // Callback interfaces
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
        void onProductDeleted();
        void onError(String error);
    }
}