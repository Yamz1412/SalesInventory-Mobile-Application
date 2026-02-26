package com.app.SalesInventory;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryRepository {
    private static final String TAG = "CategoryRepository";
    private static CategoryRepository instance;
    private final FirestoreManager firestoreManager;
    private final FirestoreSyncListener syncListener;
    private final MutableLiveData<List<Category>> allCategories;

    public interface CategoryListCallback {
        void onSuccess(List<Category> categories);
        void onError(String error);
    }

    private CategoryRepository(Application application) {
        this.firestoreManager = FirestoreManager.getInstance();
        this.syncListener = FirestoreSyncListener.getInstance();
        this.allCategories = new MutableLiveData<>();
        startRealtimeSync();
    }

    public static synchronized CategoryRepository getInstance(Application application) {
        if (instance == null) {
            instance = new CategoryRepository(application);
        }
        return instance;
    }

    private void startRealtimeSync() {
        if (!firestoreManager.isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated. Cannot start sync.");
            return;
        }
        syncListener.listenToCategories(snapshot -> {
            List<Category> categoryList = new ArrayList<>();
            if (snapshot != null) {
                for (DocumentSnapshot document : snapshot.getDocuments()) {
                    Category category = document.toObject(Category.class);
                    if (category != null) {
                        category.setCategoryId(document.getId());
                        if (category.getType() == null || category.getType().isEmpty()) {
                            category.setType("Inventory");
                        }
                        categoryList.add(category);
                    }
                }
            }
            allCategories.setValue(categoryList);
            Log.d(TAG, "Categories synced from Firestore: " + categoryList.size());
        });
    }

    public LiveData<List<Category>> getAllCategories() {
        return allCategories;
    }

    public void getAllCategories(@NonNull CategoryListCallback callback) {
        if (!firestoreManager.isUserAuthenticated()) {
            callback.onError("User not authenticated");
            return;
        }
        firestoreManager.getDb()
                .collection(firestoreManager.getUserCategoriesPath())
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Category> list = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            Category category = document.toObject(Category.class);
                            if (category != null) {
                                category.setCategoryId(document.getId());
                                if (category.getType() == null || category.getType().isEmpty()) {
                                    category.setType("Inventory");
                                }
                                list.add(category);
                            }
                        }
                    }
                    callback.onSuccess(list);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error loading categories", e);
                    callback.onError(e.getMessage() != null ? e.getMessage() : "Failed to load categories");
                });
    }

    public void addCategory(Category category, OnCategoryAddedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }
        if (category.getCategoryName() == null || category.getCategoryName().isEmpty()) {
            listener.onError("Category name cannot be empty");
            return;
        }
        Map<String, Object> categoryMap = new HashMap<>();
        categoryMap.put("categoryName", category.getCategoryName());
        categoryMap.put("description", category.getDescription() != null ? category.getDescription() : "");
        categoryMap.put("color", category.getColor() != null ? category.getColor() : "#000000");
        categoryMap.put("type", category.getType() != null && !category.getType().isEmpty() ? category.getType() : "Inventory");
        categoryMap.put("active", category.isActive());
        categoryMap.put("timestamp", firestoreManager.getServerTimestamp());
        firestoreManager.getDb().collection(firestoreManager.getUserCategoriesPath()).add(categoryMap)
                .addOnSuccessListener(documentReference -> {
                    String categoryId = documentReference.getId();
                    category.setCategoryId(categoryId);
                    listener.onCategoryAdded(categoryId);
                    Log.d(TAG, "Category added: " + categoryId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error adding category", e);
                    listener.onError(e.getMessage());
                });
    }

    public void updateCategory(Category category, OnCategoryUpdatedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }
        if (category.getCategoryId() == null || category.getCategoryId().isEmpty()) {
            listener.onError("Category ID is empty");
            return;
        }
        Map<String, Object> categoryMap = new HashMap<>();
        categoryMap.put("categoryName", category.getCategoryName());
        categoryMap.put("description", category.getDescription() != null ? category.getDescription() : "");
        categoryMap.put("color", category.getColor() != null ? category.getColor() : "#000000");
        categoryMap.put("type", category.getType() != null && !category.getType().isEmpty() ? category.getType() : "Inventory");
        categoryMap.put("active", category.isActive());
        firestoreManager.getDb().collection(firestoreManager.getUserCategoriesPath())
                .document(category.getCategoryId())
                .update(categoryMap)
                .addOnSuccessListener(aVoid -> {
                    listener.onCategoryUpdated();
                    Log.d(TAG, "Category updated: " + category.getCategoryId());
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error updating category", e);
                    listener.onError(e.getMessage());
                });
    }

    public void deleteCategory(String categoryId, OnCategoryDeletedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }
        firestoreManager.getDb().collection(firestoreManager.getUserCategoriesPath())
                .document(categoryId)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    listener.onCategoryDeleted();
                    Log.d(TAG, "Category deleted: " + categoryId);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error deleting category", e);
                    listener.onError(e.getMessage());
                });
    }

    public void getCategoryById(String categoryId, OnCategoryFetchedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }
        firestoreManager.getDb().collection(firestoreManager.getUserCategoriesPath())
                .document(categoryId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Category category = documentSnapshot.toObject(Category.class);
                        if (category != null) {
                            category.setCategoryId(documentSnapshot.getId());
                            if (category.getType() == null || category.getType().isEmpty()) {
                                category.setType("Inventory");
                            }
                            listener.onCategoryFetched(category);
                            return;
                        }
                    }
                    listener.onError("Category not found");
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching category", e);
                    listener.onError(e.getMessage());
                });
    }

    public interface OnCategoryAddedListener {
        void onCategoryAdded(String categoryId);
        void onError(String error);
    }

    public interface OnCategoryUpdatedListener {
        void onCategoryUpdated();
        void onError(String error);
    }

    public interface OnCategoryDeletedListener {
        void onCategoryDeleted();
        void onError(String error);
    }

    public interface OnCategoryFetchedListener {
        void onCategoryFetched(Category category);
        void onError(String error);
    }
}