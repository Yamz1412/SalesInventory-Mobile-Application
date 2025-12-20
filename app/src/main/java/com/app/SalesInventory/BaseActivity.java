package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.getInstance(this).applyTheme(this);
        super.onCreate(savedInstanceState);
        ThemeManager.getInstance(this).applySystemColorsToWindow(this);
        enforceAuthentication();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeManager.getInstance(this).applySystemColorsToWindow(this);
    }
    @Override
    protected void onPostCreate(@Nullable Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        try {
            ThemeManager tm = ThemeManager.getInstance(this);
            if (tm != null) {
                tm.applyTheme(this);
            }
        } catch (Exception ignored) {}
    }

    private void enforceAuthentication() {
        if (this instanceof FirstActivity
                || this instanceof SignInActivity
                || this instanceof SignUpActivity
                || this instanceof WaitingVerificationActivity) {
            return;
        }

        AuthManager authManager = AuthManager.getInstance();
        if (authManager.getCurrentUser() == null) {
            Intent intent = new Intent(this, SignInActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        } else {
            String uid = authManager.getCurrentUserId();
            FirebaseFirestore db = authManager.getFirestore();
            db.collection("users").document(uid).get().addOnSuccessListener((DocumentSnapshot doc) -> {
                String role = null;
                if (doc.exists()) {
                    if (doc.contains("role")) {
                        role = doc.getString("role");
                    } else if (doc.contains("Role")) {
                        role = doc.getString("Role");
                    }
                }
                String ownerAdminId = null;
                if (doc.exists()) {
                    if (doc.contains("ownerAdminId")) {
                        ownerAdminId = doc.getString("ownerAdminId");
                    }
                }
                String businessOwnerId;
                if (role != null && role.equalsIgnoreCase("Admin")) {
                    businessOwnerId = uid;
                } else if (ownerAdminId != null && !ownerAdminId.isEmpty()) {
                    businessOwnerId = ownerAdminId;
                } else {
                    businessOwnerId = uid;
                }
                FirestoreManager.getInstance().updateCurrentUserId(uid);
                FirestoreManager.getInstance().setBusinessOwnerId(businessOwnerId);
            });
            ThemeManager.getInstance(this).loadUserThemeFromRemote(null);
        }
    }
}