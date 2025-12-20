package com.app.SalesInventory;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class AdminDataController {
    private static AdminDataController instance;
    private String adminUid;

    private AdminDataController(String adminUid) {
        this.adminUid = adminUid;
    }

    public static synchronized AdminDataController getInstance(String adminUid) {
        if (instance == null || (instance.adminUid != null && !instance.adminUid.equals(adminUid))) {
            instance = new AdminDataController(adminUid);
        }
        return instance;
    }

    public void enforceDataScope(AdminDataControllerCallback callback) {
        DatabaseReference productsRef = FirebaseDatabase.getInstance()
                .getReference("Product")
                .child(adminUid)
                .child("items");

        productsRef.get().addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                DataSnapshot snapshot = task.getResult();
                boolean isNewAdmin = snapshot == null || !snapshot.hasChildren();
                callback.onChecked(isNewAdmin);
            } else {
                callback.onChecked(true);
            }
        });
    }

    public interface AdminDataControllerCallback {
        void onChecked(boolean isNewAdmin);
    }
}