package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class SignInActivity extends BaseActivity {

    EditText Email, Password;
    ProgressBar progressBar;
    FirebaseAuth fAuth;
    TextView forgotpassword;
    Button BtnSingIn;
    CheckBox rememberCheck;
    FirebaseFirestore fStore;
    private SharedPreferences prefs;
    private static final String PREFS_NAME = "app_prefs";
    private static final String KEY_REMEMBER = "remember";
    private static final String KEY_LAST_USER_ID = "last_user_id";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);
        BtnSingIn = findViewById(R.id.BtnSignIn);
        Email = findViewById(R.id.email1);
        Password = findViewById(R.id.password1);
        progressBar = findViewById(R.id.progressBar2);
        forgotpassword = findViewById(R.id.forgotpassword);
        rememberCheck = findViewById(R.id.rememberCheck);
        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        FirebaseApp app = FirebaseApp.getInstance();
        Log.i("FirebaseConfig", "projectId=" + app.getOptions().getProjectId());
        Log.i("FirebaseConfig", "apiKey=" + app.getOptions().getApiKey());
        Log.i("FirebaseConfig", "applicationId=" + app.getOptions().getApplicationId());

        AuthManager.getInstance().clearCache();
        FirestoreManager.getInstance().setBusinessOwnerId(null);
        SalesRepository.resetInstance();
        clearLocalDatabaseIfNeeded();

        boolean remembered = prefs.getBoolean(KEY_REMEMBER, false);
        FirebaseUser currentUser = fAuth.getCurrentUser();
        if (remembered && currentUser != null) {
            progressBar.setVisibility(View.VISIBLE);
            currentUser.reload().addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    progressBar.setVisibility(View.INVISIBLE);
                    FirebaseUser reloaded = fAuth.getCurrentUser();
                    if (reloaded == null) {
                        prefs.edit().putBoolean(KEY_REMEMBER, false).apply();
                        AuthManager.getInstance().clearCache();
                        FirestoreManager.getInstance().setBusinessOwnerId(null);
                        SalesRepository.resetInstance();
                        clearLocalDatabaseIfNeeded();
                        return;
                    }
                    checkUserRoleAndProceedAfterReload(reloaded, new RoleCheckCallback() {
                        @Override
                        public void onProceed(boolean allowed) {
                            if (!allowed) {
                                prefs.edit().putBoolean(KEY_REMEMBER, false).apply();
                                AuthManager.getInstance().clearCache();
                                FirestoreManager.getInstance().setBusinessOwnerId(null);
                                SalesRepository.resetInstance();
                                clearLocalDatabaseIfNeeded();
                            } else {
                                String owner = FirestoreManager.getInstance().getBusinessOwnerId();
                                if (owner != null && !owner.isEmpty()) {
                                    ProductRemoteSyncer syncer = new ProductRemoteSyncer((Application) getApplicationContext());
                                    syncer.startRealtimeSync(owner);
                                    FirebaseMessaging.getInstance().subscribeToTopic("owner_" + owner);
                                }
                            }
                        }
                    });
                }
            });
        }
        BtnSingIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                SingInB(view);
            }
        });
    }

    private void clearLocalDatabaseIfNeeded() {
        FirebaseUser currentUser = fAuth.getCurrentUser();
        String currentUserId = currentUser != null ? currentUser.getUid() : null;
        String lastUserId = prefs.getString(KEY_LAST_USER_ID, null);

        if (currentUserId != null && lastUserId != null && !currentUserId.equals(lastUserId)) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    AppDatabase db = AppDatabase.getInstance(getApplicationContext());
                    db.clearAllTables();
                    Log.d("SignInActivity", "Local database cleared for new user");
                } catch (Exception e) {
                    Log.e("SignInActivity", "Error clearing local database", e);
                }
            });
        }

        if (currentUserId != null) {
            prefs.edit().putString(KEY_LAST_USER_ID, currentUserId).apply();
        }
    }

    private interface RoleCheckCallback {
        void onProceed(boolean allowed);
    }

    private void checkUserRoleAndProceedAfterReload(@NonNull final FirebaseUser reloaded, @NonNull final RoleCheckCallback cb) {
        final String uid = reloaded.getUid();

        AuthManager.getInstance().clearCache();
        FirestoreManager.getInstance().setBusinessOwnerId(null);
        SalesRepository.resetInstance();
        clearLocalDatabaseIfNeeded();

        fStore.collection("admin").document(uid).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> adminTask) {
                DocumentSnapshot adminSnap;
                if (adminTask.isSuccessful() && adminTask.getResult() != null && adminTask.getResult().exists()) {
                    adminSnap = adminTask.getResult();
                } else {
                    adminSnap = null;
                }
                fStore.collection("users").document(uid).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> userTask) {
                        DocumentSnapshot userSnap;
                        if (userTask.isSuccessful() && userTask.getResult() != null && userTask.getResult().exists()) {
                            userSnap = userTask.getResult();
                        } else {
                            userSnap = null;
                        }
                        boolean approved = false;
                        String role = "Unknown";
                        String ownerAdminId = null;

                        if (adminSnap != null && adminSnap.exists()) {
                            Boolean adminApproved = adminSnap.getBoolean("approved");
                            if (adminApproved == null) adminApproved = false;
                            approved = adminApproved;
                            String adminRole = adminSnap.getString("role");
                            if (adminRole != null) role = adminRole;
                        } else if (userSnap != null && userSnap.exists()) {
                            Boolean userApproved = userSnap.getBoolean("approved");
                            if (userApproved == null) userApproved = false;
                            approved = userApproved;
                            String userRole = userSnap.getString("role");
                            if (userRole == null) userRole = userSnap.getString("Role");
                            if (userRole != null) role = userRole;
                            ownerAdminId = userSnap.getString("ownerAdminId");
                        } else {
                            approved = false;
                        }

                        if (!approved) {
                            cb.onProceed(false);
                            AuthManager.getInstance().clearCache();
                            FirestoreManager.getInstance().setBusinessOwnerId(null);
                            SalesRepository.resetInstance();
                            clearLocalDatabaseIfNeeded();
                            Toast.makeText(SignInActivity.this, "Please wait for admin approval", Toast.LENGTH_LONG).show();
                            Intent intent = new Intent(getApplicationContext(), WaitingVerificationActivity.class);
                            startActivity(intent);
                            finish();
                            return;
                        }

                        if ("Admin".equalsIgnoreCase(role)) {
                            if (reloaded.isEmailVerified()) {
                                applyRemoteTheme(userSnap);
                                updateUserProfileFromAuth(reloaded, userSnap);
                                FirestoreManager.getInstance().updateCurrentUserId(uid);
                                FirestoreManager.getInstance().setBusinessOwnerId(uid);
                                prefs.edit().putString(KEY_LAST_USER_ID, uid).apply();
                                FirebaseMessaging.getInstance().subscribeToTopic("owner_" + uid);
                                AuthManager.getInstance().refreshCurrentUserStatus(new AuthManager.SimpleCallback() {
                                    @Override
                                    public void onComplete(boolean success) {
                                        SalesRepository salesRepo = SalesRepository.getInstance((Application) getApplicationContext());
                                        salesRepo.startIfNeeded();
                                        ProductRemoteSyncer syncer = new ProductRemoteSyncer((Application) getApplicationContext());
                                        syncer.startRealtimeSync(uid);
                                        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                        finish();
                                        cb.onProceed(true);
                                    }
                                });
                                return;
                            } else {
                                Toast.makeText(SignInActivity.this, "Please verify your email before logging in. Check your inbox.", Toast.LENGTH_LONG).show();
                                FirebaseAuth.getInstance().signOut();
                                AuthManager.getInstance().clearCache();
                                FirestoreManager.getInstance().setBusinessOwnerId(null);
                                SalesRepository.resetInstance();
                                clearLocalDatabaseIfNeeded();
                                cb.onProceed(false);
                                return;
                            }
                        } else {
                            if (ownerAdminId != null && !ownerAdminId.isEmpty()) {
                                FirestoreManager.getInstance().setBusinessOwnerId(ownerAdminId);
                                FirebaseMessaging.getInstance().subscribeToTopic("owner_" + ownerAdminId);
                            } else {
                                FirestoreManager.getInstance().setBusinessOwnerId(uid);
                                FirebaseMessaging.getInstance().subscribeToTopic("owner_" + uid);
                            }
                            prefs.edit().putString(KEY_LAST_USER_ID, uid).apply();
                            applyRemoteTheme(userSnap);
                            updateUserProfileFromAuth(reloaded, userSnap);
                            FirestoreManager.getInstance().updateCurrentUserId(uid);
                            AuthManager.getInstance().refreshCurrentUserStatus(new AuthManager.SimpleCallback() {
                                @Override
                                public void onComplete(boolean success) {
                                    String owner = FirestoreManager.getInstance().getBusinessOwnerId();
                                    if (owner != null && !owner.isEmpty()) {
                                        SalesRepository salesRepo = SalesRepository.getInstance((Application) getApplicationContext());
                                        salesRepo.startIfNeeded();
                                        ProductRemoteSyncer syncer = new ProductRemoteSyncer((Application) getApplicationContext());
                                        syncer.startRealtimeSync(owner);
                                    }
                                    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                    cb.onProceed(true);
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    private void applyRemoteTheme(DocumentSnapshot doc) {
        if (doc == null) return;
        String themeName = doc.getString("themeName");
        Long primary = doc.getLong("primaryColor");
        Long secondary = doc.getLong("secondaryColor");
        Long accent = doc.getLong("accentColor");
        ThemeManager tm = ThemeManager.getInstance(this);
        if (themeName != null && !themeName.isEmpty()) {
            tm.setCurrentThemeLocalOnly(themeName);
        }
        if (primary != null && secondary != null && accent != null) {
            tm.setCustomColors(primary.intValue(), secondary.intValue(), accent.intValue());
        }
    }

    private void updateUserProfileFromAuth(com.google.firebase.auth.FirebaseUser user, DocumentSnapshot existingDoc) {
        if (user == null) return;
        String uid = user.getUid();
        String email = user.getEmail() != null ? user.getEmail() : "";
        String name = user.getDisplayName() != null ? user.getDisplayName() : "";
        String phone = user.getPhoneNumber() != null ? user.getPhoneNumber() : "";
        String photoUrl = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "";
        Map<String, Object> data = new HashMap<>();
        if (!email.isEmpty()) {
            data.put("email", email);
            data.put("Email", email);
        }
        if (!name.isEmpty()) {
            data.put("name", name);
            data.put("Name", name);
        }
        if (!phone.isEmpty()) {
            data.put("phone", phone);
            data.put("Phone", phone);
        }
        if (!photoUrl.isEmpty()) {
            data.put("photoUrl", photoUrl);
        } else if (existingDoc != null) {
            String existingPhoto = existingDoc.getString("photoUrl");
            if (existingPhoto != null && !existingPhoto.isEmpty()) {
                data.put("photoUrl", existingPhoto);
            }
        }
        if (!data.isEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(uid).set(data, SetOptions.merge());
        }
    }

    public void SingInB(View view) {
        String email = Email.getText().toString().trim();
        String password = Password.getText().toString().trim();
        if (TextUtils.isEmpty(email)) {
            Email.setError("Email is Required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            Password.setError("Password is Required");
            return;
        }
        if (password.length() < 6) {
            Password.setError("Password Must be 6 Characters or More");
            return;
        }

        AuthManager.getInstance().clearCache();
        FirestoreManager.getInstance().setBusinessOwnerId(null);
        SalesRepository.resetInstance();
        clearLocalDatabaseIfNeeded();

        progressBar.setVisibility(View.VISIBLE);
        fAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener(new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        progressBar.setVisibility(View.INVISIBLE);
                        if (task.isSuccessful()) {
                            FirebaseUser user = fAuth.getCurrentUser();
                            if (user != null) {
                                user.reload().addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> t) {
                                        FirebaseUser reloaded = fAuth.getCurrentUser();
                                        if (reloaded == null) {
                                            showErrorDialog("Sign in failed");
                                            AuthManager.getInstance().clearCache();
                                            FirestoreManager.getInstance().setBusinessOwnerId(null);
                                            SalesRepository.resetInstance();
                                            clearLocalDatabaseIfNeeded();
                                            return;
                                        }
                                        checkUserRoleAndProceedAfterReload(reloaded, new RoleCheckCallback() {
                                            @Override
                                            public void onProceed(boolean success) {
                                                if (success) {
                                                    if (rememberCheck != null && rememberCheck.isChecked()) {
                                                        prefs.edit().putBoolean(KEY_REMEMBER, true).apply();
                                                    } else {
                                                        prefs.edit().putBoolean(KEY_REMEMBER, false).apply();
                                                    }
                                                    Email.getText().clear();
                                                    Password.getText().clear();
                                                } else {
                                                    AuthManager.getInstance().clearCache();
                                                    FirestoreManager.getInstance().setBusinessOwnerId(null);
                                                    SalesRepository.resetInstance();
                                                    clearLocalDatabaseIfNeeded();
                                                }
                                            }
                                        });
                                    }
                                });
                            } else {
                                showErrorDialog("Sign in failed");
                                AuthManager.getInstance().clearCache();
                                FirestoreManager.getInstance().setBusinessOwnerId(null);
                                SalesRepository.resetInstance();
                                clearLocalDatabaseIfNeeded();
                            }
                        } else {
                            Exception e = task.getException();
                            Log.e("AuthSignIn", "Sign in failed", e);
                            String display = "Login failed";
                            if (e instanceof FirebaseAuthException) {
                                String code = ((FirebaseAuthException) e).getErrorCode();
                                display = mapAuthErrorCodeToMessage(code, e.getMessage());
                            } else if (e != null && e.getMessage() != null) {
                                display = e.getMessage();
                            }
                            showErrorDialog(display);
                            AuthManager.getInstance().clearCache();
                            FirestoreManager.getInstance().setBusinessOwnerId(null);
                            SalesRepository.resetInstance();
                            clearLocalDatabaseIfNeeded();
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("AuthSignIn", "Failure listener", e);
                    String msg = e.getMessage() != null ? e.getMessage() : "Sign-in failed";
                    showErrorDialog(msg);
                    AuthManager.getInstance().clearCache();
                    FirestoreManager.getInstance().setBusinessOwnerId(null);
                    SalesRepository.resetInstance();
                    clearLocalDatabaseIfNeeded();
                });
    }

    private String mapAuthErrorCodeToMessage(String code, String fallback) {
        if (code == null) return fallback != null ? fallback : "Authentication failed";
        switch (code) {
            case "ERROR_INVALID_EMAIL":
            case "INVALID_EMAIL":
                return "The email address is badly formatted.";
            case "ERROR_USER_DISABLED":
            case "USER_DISABLED":
                return "This account has been disabled.";
            case "ERROR_USER_NOT_FOUND":
            case "EMAIL_NOT_FOUND":
                return "No account found with this email.";
            case "ERROR_WRONG_PASSWORD":
            case "INVALID_PASSWORD":
                return "Incorrect password. Try again or reset your password.";
            case "TOO_MANY_ATTEMPTS_TRY_LATER":
                return "Too many attempts. Try again later.";
            default:
                return fallback != null ? fallback : "Authentication failed";
        }
    }

    public void resetPW(View view) {
        Intent i = new Intent(this, resetPassWord.class);
        startActivity(i);
    }

    public void GoTo(View view) {
        startActivity(new Intent(getApplicationContext(), SignUpActivity.class));
    }

    private void showErrorDialog(String message) {
        if (isFinishing() || isDestroyed()) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }
        try {
            new AlertDialog.Builder(SignInActivity.this)
                    .setTitle("Sign-in error")
                    .setMessage(message)
                    .setPositiveButton("OK", null)
                    .show();
        } catch (Exception ex) {
            Log.e("AuthSignIn", "Error showing dialog", ex);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        }
    }
}