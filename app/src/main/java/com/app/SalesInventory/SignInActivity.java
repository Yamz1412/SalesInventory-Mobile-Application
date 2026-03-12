package com.app.SalesInventory;

import androidx.annotation.NonNull;
import android.app.Application;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.HashMap;
import java.util.Map;

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
    private static final String KEY_USER_EMAIL = "user_email";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_ROLE = "user_role";
    private static final String KEY_BUSINESS_OWNER = "business_owner";
    private static final String KEY_LAST_LOGIN = "last_login";

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

        checkRememberedUser();
    }

    private void checkRememberedUser() {
        boolean remembered = prefs.getBoolean(KEY_REMEMBER, false);
        String cachedUserId = prefs.getString(KEY_USER_ID, null);
        String cachedBusinessOwner = prefs.getString(KEY_BUSINESS_OWNER, null);

        if (remembered && cachedUserId != null) {
            FirebaseUser currentUser = fAuth.getCurrentUser();

            if (currentUser != null && currentUser.getUid().equals(cachedUserId)) {
                proceedToMainActivityOffline(cachedUserId, cachedBusinessOwner);
            } else if (currentUser != null) {
                progressBar.setVisibility(View.VISIBLE);
                currentUser.reload().addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        FirebaseUser reloaded = fAuth.getCurrentUser();
                        progressBar.setVisibility(View.INVISIBLE);
                        if (reloaded != null) {
                            checkUserRoleAndProceedAfterReload(reloaded, new RoleCheckCallback() {
                                @Override
                                public void onProceed(boolean allowed) {
                                    if (allowed) {
                                        cacheUserData(reloaded.getUid(),
                                                FirestoreManager.getInstance().getBusinessOwnerId());
                                    } else {
                                        clearRememberedUser();
                                    }
                                }
                            });
                        } else {
                            clearRememberedUser();
                        }
                    }
                });
            } else {
                clearRememberedUser();
            }
        }
    }

    private void proceedToMainActivityOffline(String userId, String businessOwner) {
        FirestoreManager.getInstance().updateCurrentUserId(userId);
        if (businessOwner != null && !businessOwner.isEmpty()) {
            FirestoreManager.getInstance().setBusinessOwnerId(businessOwner);
        }

        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtra("offline_mode", true);
        startActivity(intent);
        finish();
    }

    private interface RoleCheckCallback {
        void onProceed(boolean allowed);
    }

    private void checkUserRoleAndProceedAfterReload(@NonNull final FirebaseUser reloaded, @NonNull final RoleCheckCallback cb) {
        final String uid = reloaded.getUid();
        fStore.collection("admin").document(uid).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> adminTask) {
                DocumentSnapshot adminSnap = null;
                if (adminTask.isSuccessful() && adminTask.getResult() != null && adminTask.getResult().exists()) {
                    adminSnap = adminTask.getResult();
                }

                final DocumentSnapshot finalAdminSnap = adminSnap;
                fStore.collection("users").document(uid).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> userTask) {
                        DocumentSnapshot userSnap = null;
                        if (userTask.isSuccessful() && userTask.getResult() != null && userTask.getResult().exists()) {
                            userSnap = userTask.getResult();
                        }

                        boolean approved = false;
                        String role = "Unknown";
                        if (finalAdminSnap != null && finalAdminSnap.exists()) {
                            Boolean adminApproved = finalAdminSnap.getBoolean("approved");
                            approved = adminApproved != null ? adminApproved : false;
                            String adminRole = finalAdminSnap.getString("role");
                            if (adminRole != null) role = adminRole;
                        } else if (userSnap != null && userSnap.exists()) {
                            Boolean userApproved = userSnap.getBoolean("approved");
                            approved = userApproved != null ? userApproved : false;
                            String userRole = userSnap.getString("role");
                            if (userRole == null) userRole = userSnap.getString("Role");
                            if (userRole != null) role = userRole;
                            String ownerAdminId = userSnap.getString("ownerAdminId");
                            if (ownerAdminId != null && !ownerAdminId.isEmpty()) {
                                FirestoreManager.getInstance().setBusinessOwnerId(ownerAdminId);
                                FirebaseMessaging.getInstance().subscribeToTopic("owner_" + ownerAdminId);
                            } else {
                                FirestoreManager.getInstance().setBusinessOwnerId(uid);
                                FirebaseMessaging.getInstance().subscribeToTopic("owner_" + uid);
                            }
                        } else {
                            approved = false;
                            FirestoreManager.getInstance().setBusinessOwnerId(uid);
                            FirebaseMessaging.getInstance().subscribeToTopic("owner_" + uid);
                        }

                        ProductRepository.getInstance(getApplication()).clearLocalData();
                        SalesRepository.getInstance(getApplication()).clearData();

                        if ("Admin".equalsIgnoreCase(role)) {
                            // =========================================================
                            // ADMIN LOGIN LOGIC
                            // =========================================================
                            if (reloaded.isEmailVerified()) {
                                // If Admin has verified email, auto-approve them in the database!
                                if (!approved) {
                                    fStore.collection("users").document(uid).update("approved", true);
                                }

                                applyRemoteTheme(userSnap);
                                updateUserProfileFromAuth(reloaded, userSnap);
                                FirestoreManager.getInstance().updateCurrentUserId(uid);
                                FirestoreManager.getInstance().setBusinessOwnerId(uid);
                                FirebaseMessaging.getInstance().subscribeToTopic("owner_" + uid);
                                ProductRemoteSyncer syncer = new ProductRemoteSyncer((Application) getApplicationContext());
                                syncer.startRealtimeSync(uid);

                                if (rememberCheck != null && rememberCheck.isChecked()) {
                                    cacheUserData(uid, uid);
                                    prefs.edit().putBoolean(KEY_REMEMBER, true).apply();
                                } else {
                                    clearRememberedUser();
                                }

                                // FIX: Removed the BusinessSetupActivity redirection completely.
                                // Users will now go straight to the MainActivity Dashboard.
                                proceedToMainActivityOffline(uid, uid);
                                cb.onProceed(true);
                            } else {
                                Toast.makeText(SignInActivity.this, "Please verify your email before logging in.", Toast.LENGTH_LONG).show();
                                FirebaseAuth.getInstance().signOut();
                                cb.onProceed(false);
                            }
                        } else {
                            // =========================================================
                            // STAFF LOGIN LOGIC
                            // =========================================================
                            if (!approved) {
                                // Staff must wait for explicit Admin approval
                                cb.onProceed(false);
                                Toast.makeText(SignInActivity.this, "Please wait for admin approval", Toast.LENGTH_LONG).show();
                                Intent intent = new Intent(getApplicationContext(), WaitingVerificationActivity.class);
                                startActivity(intent);
                                finish();
                                return;
                            }

                            applyRemoteTheme(userSnap);
                            updateUserProfileFromAuth(reloaded, userSnap);
                            FirestoreManager.getInstance().updateCurrentUserId(uid);
                            String owner = FirestoreManager.getInstance().getBusinessOwnerId();
                            if (owner != null && !owner.isEmpty()) {
                                FirebaseMessaging.getInstance().subscribeToTopic("owner_" + owner);
                                ProductRemoteSyncer syncer = new ProductRemoteSyncer((Application) getApplicationContext());
                                syncer.startRealtimeSync(owner);
                            }

                            if (rememberCheck != null && rememberCheck.isChecked()) {
                                cacheUserData(uid, owner);
                                prefs.edit().putBoolean(KEY_REMEMBER, true).apply();
                            } else {
                                clearRememberedUser();
                            }

                            proceedToMainActivityOffline(uid, owner);
                            cb.onProceed(true);
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
        if (themeName != null && !themeName.isEmpty()) tm.setCurrentThemeLocalOnly(themeName);
        if (primary != null && secondary != null && accent != null) tm.setCustomColors(primary.intValue(), secondary.intValue(), accent.intValue());
    }

    private void updateUserProfileFromAuth(com.google.firebase.auth.FirebaseUser user, DocumentSnapshot existingDoc) {
        if (user == null) return;
        String uid = user.getUid();
        String email = user.getEmail() != null ? user.getEmail() : "";
        String name = user.getDisplayName() != null ? user.getDisplayName() : "";
        String phone = user.getPhoneNumber() != null ? user.getPhoneNumber() : "";
        String photoUrl = user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "";
        Map<String, Object> data = new HashMap<>();
        if (!email.isEmpty()) { data.put("email", email); data.put("Email", email); }
        if (!name.isEmpty()) { data.put("name", name); data.put("Name", name); }
        if (!phone.isEmpty()) { data.put("phone", phone); data.put("Phone", phone); }
        if (!photoUrl.isEmpty()) {
            data.put("photoUrl", photoUrl);
        } else if (existingDoc != null) {
            String existingPhoto = existingDoc.getString("photoUrl");
            if (existingPhoto != null && !existingPhoto.isEmpty()) data.put("photoUrl", existingPhoto);
        }
        if (!data.isEmpty()) {
            FirebaseFirestore.getInstance().collection("users").document(uid).set(data, SetOptions.merge());
        }
    }

    private void cacheUserData(String userId, String businessOwner) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_USER_ID, userId);
        editor.putString(KEY_USER_EMAIL, Email.getText().toString().trim());
        editor.putString(KEY_BUSINESS_OWNER, businessOwner != null ? businessOwner : userId);
        editor.putLong(KEY_LAST_LOGIN, System.currentTimeMillis());
        editor.apply();
    }

    private void clearRememberedUser() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_REMEMBER, false);
        editor.remove(KEY_USER_ID);
        editor.remove(KEY_USER_EMAIL);
        editor.remove(KEY_BUSINESS_OWNER);
        editor.remove(KEY_LAST_LOGIN);
        editor.apply();
    }

    // =========================================================================================
    // SMART LOGIN (Recognizes Email vs Username)
    // =========================================================================================
    public void SingInB(View view) {
        String input = Email.getText().toString().trim();
        String password = Password.getText().toString().trim();

        if (TextUtils.isEmpty(input)) { Email.setError("Email or Username is Required"); return; }
        if (TextUtils.isEmpty(password)) { Password.setError("Password is Required"); return; }
        if (password.length() < 6) { Password.setError("Password Must be 6 Characters or More"); return; }

        progressBar.setVisibility(View.VISIBLE);

        if (!input.contains("@")) {
            // USER TYPED A USERNAME - Let's find the matching email in Firestore
            String usernameLower = input.toLowerCase();
            fStore.collection("users").whereEqualTo("username", usernameLower).get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null && !task.getResult().isEmpty()) {
                    // Found the username! Extract the attached email
                    String fetchedEmail = task.getResult().getDocuments().get(0).getString("email");
                    if (fetchedEmail != null) {
                        performFirebaseAuthLogin(fetchedEmail, password);
                    } else {
                        progressBar.setVisibility(View.INVISIBLE);
                        Toast.makeText(SignInActivity.this, "Error: Account has no email linked", Toast.LENGTH_LONG).show();
                    }
                } else {
                    progressBar.setVisibility(View.INVISIBLE);
                    Toast.makeText(SignInActivity.this, "Username not found", Toast.LENGTH_LONG).show();
                }
            });
        } else {
            // USER TYPED AN EMAIL
            performFirebaseAuthLogin(input, password);
        }
    }

    private void performFirebaseAuthLogin(String email, String password) {
        fAuth.signInWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                progressBar.setVisibility(View.INVISIBLE);
                if (task.isSuccessful()) {
                    com.google.firebase.auth.FirebaseUser user = fAuth.getCurrentUser();
                    if (user != null) {
                        user.reload().addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> t) {
                                com.google.firebase.auth.FirebaseUser reloaded = fAuth.getCurrentUser();
                                if (reloaded == null) {
                                    Toast.makeText(SignInActivity.this, "Sign in failed", Toast.LENGTH_LONG).show();
                                    return;
                                }
                                checkUserRoleAndProceedAfterReload(reloaded, new RoleCheckCallback() {
                                    @Override
                                    public void onProceed(boolean success) {
                                        if (success) {
                                            if (rememberCheck != null && rememberCheck.isChecked()) prefs.edit().putBoolean(KEY_REMEMBER, true).apply();
                                            else clearRememberedUser();
                                        }
                                    }
                                });
                            }
                        });
                    } else {
                        Toast.makeText(SignInActivity.this, "Sign in failed", Toast.LENGTH_LONG).show();
                    }
                } else {
                    String errorMessage = task.getException() != null ? task.getException().getMessage() : "Login failed";
                    Toast.makeText(SignInActivity.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                }
                Email.getText().clear();
                Password.getText().clear();
            }
        });
    }

    public void resetPW(View view) { startActivity(new Intent(this, resetPassWord.class)); }
    public void GoTo(View view) { startActivity(new Intent(getApplicationContext(), SignUpActivity.class)); }
}