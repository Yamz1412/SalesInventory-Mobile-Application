package com.app.SalesInventory;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.app.SalesInventory.FirestoreManager;
import com.app.SalesInventory.FirestoreSyncListener;
import com.app.SalesInventory.Category;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CategoryRepository {
    private static final String TAG = "CategoryRepository";
    private static CategoryRepository instance;

    private FirestoreManager firestoreManager;
    private FirestoreSyncListener syncListener;
    private MutableLiveData<List<Category>> allCategories;

    private CategoryRepository(Application application) {
        this.firestoreManager = FirestoreManager.getInstance();
        this.syncListener = FirestoreSyncListener.getInstance();
        this.allCategories = new MutableLiveData<>();

        // Start listening to categories from Firestore
        startRealtimeSync();
    }

    public static synchronized CategoryRepository getInstance(Application application) {
        if (instance == null) {
            instance = new CategoryRepository(application);
        }
        return instance;
    }

    /**
     * Start real-time sync with Firestore
     */
    private void startRealtimeSync() {
        if (!firestoreManager.isUserAuthenticated()) {
            Log.w(TAG, "User not authenticated. Cannot start sync.");
            return;
        }

        syncListener.listenToCategories(new FirestoreSyncListener.OnCategoriesChangedListener() {
            @Override
            public void onCategoriesChanged(QuerySnapshot snapshot) {
                List<Category> categoryList = new ArrayList<>();

                if (snapshot != null) {
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        Category category = document.toObject(Category.class);
                        if (category != null) {
                            category.setCategoryId(document.getId());
                            categoryList.add(category);
                        }
                    }
                }

                allCategories.setValue(categoryList);
                Log.d(TAG, "Categories synced from Firestore: " + categoryList.size());
            }
        });
    }

    /**
     * Get all categories (LiveData)
     */
    public LiveData<List<Category>> getAllCategories() {
        return allCategories;
    }

    /**
     * Add new category
     */
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
        categoryMap.put("name", category.getCategoryName());
        categoryMap.put("description", category.getDescription() != null ? category.getDescription() : "");
        categoryMap.put("color", category.getColor() != null ? category.getColor() : "#000000");
        categoryMap.put("createdAt", firestoreManager.getServerTimestamp());

        firestoreManager.getDb()
                .collection(firestoreManager.getUserCategoriesPath())
                .add(categoryMap)
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

    /**
     * Update category
     */
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
        categoryMap.put("name", category.getCategoryName());
        categoryMap.put("description", category.getDescription() != null ? category.getDescription() : "");
        categoryMap.put("color", category.getColor() != null ? category.getColor() : "#000000");

        firestoreManager.getDb()
                .collection(firestoreManager.getUserCategoriesPath())
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

    /**
     * Delete category
     */
    public void deleteCategory(String categoryId, OnCategoryDeletedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        firestoreManager.getDb()
                .collection(firestoreManager.getUserCategoriesPath())
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

    /**
     * Get category by ID
     */
    public void getCategoryById(String categoryId, OnCategoryFetchedListener listener) {
        if (!firestoreManager.isUserAuthenticated()) {
            listener.onError("User not authenticated");
            return;
        }

        firestoreManager.getDb()
                .collection(firestoreManager.getUserCategoriesPath())
                .document(categoryId)
                .get()
                .addOnSuccessListener(documentSnapshot -> {
                    if (documentSnapshot.exists()) {
                        Category category = documentSnapshot.toObject(Category.class);
                        if (category != null) {
                            category.setCategoryId(documentSnapshot.getId());
                            listener.onCategoryFetched(category);
                        }
                    } else {
                        listener.onError("Category not found");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Error fetching category", e);
                    listener.onError(e.getMessage());
                });
    }

    // Callback interfaces
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