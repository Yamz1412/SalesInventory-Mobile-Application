package com.app.SalesInventory;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;

public abstract class BaseActivity extends AppCompatActivity {

    // Add this variable to track what theme was active when the screen was built
    private String appliedThemeName;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // 1. Record the current theme and apply it FIRST
        appliedThemeName = ThemeManager.getInstance(this).getCurrentTheme().name;
        ThemeManager.getInstance(this).applyTheme(this);

        // 2. Check for Colorblind override and apply it SECOND
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isColorblind = prefs.getBoolean("ColorblindMode", false);
        if (isColorblind) {
            setTheme(R.style.AppTheme_Colorblind);
        }

        // 3. Call super.onCreate() ONLY AFTER themes are set!
        super.onCreate(savedInstanceState);

        // 4. Apply status bar colors
        ThemeManager.getInstance(this).applySystemColorsToWindow(this);
        enforceAuthentication();
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeManager.getInstance(this).applySystemColorsToWindow(this);

        // FIX: Check if the theme was changed in Settings while this screen was sleeping
        String currentThemeName = ThemeManager.getInstance(this).getCurrentTheme().name;
        if (appliedThemeName != null && !appliedThemeName.equals(currentThemeName)) {
            // The theme changed! Force the activity to instantly redraw itself
            recreate();
        }
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