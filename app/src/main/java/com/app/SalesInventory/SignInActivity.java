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
        boolean remembered = prefs.getBoolean(KEY_REMEMBER, false);
        FirebaseUser currentUser = fAuth.getCurrentUser();
        if (remembered && currentUser != null) {
            progressBar.setVisibility(View.VISIBLE);
            currentUser.reload().addOnCompleteListener(new OnCompleteListener<Void>() {
                @Override
                public void onComplete(@NonNull Task<Void> task) {
                    FirebaseUser reloaded = fAuth.getCurrentUser();
                    progressBar.setVisibility(View.INVISIBLE);
                    if (reloaded == null) {
                        prefs.edit().putBoolean(KEY_REMEMBER, false).apply();
                        return;
                    }
                    checkUserRoleAndProceedAfterReload(reloaded, new RoleCheckCallback() {
                        @Override
                        public void onProceed(boolean allowed) {
                            if (!allowed) {
                                prefs.edit().putBoolean(KEY_REMEMBER, false).apply();
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
    }

    private interface RoleCheckCallback {
        void onProceed(boolean allowed);
    }

    private void checkUserRoleAndProceedAfterReload(@NonNull final FirebaseUser reloaded, @NonNull final RoleCheckCallback cb) {
        final String uid = reloaded.getUid();
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
                        if (!approved) {
                            cb.onProceed(false);
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
                                FirebaseMessaging.getInstance().subscribeToTopic("owner_" + uid);
                                ProductRemoteSyncer syncer = new ProductRemoteSyncer((Application) getApplicationContext());
                                syncer.startRealtimeSync(uid);
                                Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(intent);
                                finish();
                                cb.onProceed(true);
                                return;
                            } else {
                                Toast.makeText(SignInActivity.this, "Please verify your email before logging in. Check your inbox.", Toast.LENGTH_LONG).show();
                                FirebaseAuth.getInstance().signOut();
                                cb.onProceed(false);
                                return;
                            }
                        } else {
                            applyRemoteTheme(userSnap);
                            updateUserProfileFromAuth(reloaded, userSnap);
                            FirestoreManager.getInstance().updateCurrentUserId(uid);
                            String owner = FirestoreManager.getInstance().getBusinessOwnerId();
                            if (owner != null && !owner.isEmpty()) {
                                FirebaseMessaging.getInstance().subscribeToTopic("owner_" + owner);
                                ProductRemoteSyncer syncer = new ProductRemoteSyncer((Application) getApplicationContext());
                                syncer.startRealtimeSync(owner);
                            }
                            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
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
        progressBar.setVisibility(View.VISIBLE);
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
                                        if (!success) {
                                        } else {
                                            if (rememberCheck != null && rememberCheck.isChecked()) {
                                                prefs.edit().putBoolean(KEY_REMEMBER, true).apply();
                                            } else {
                                                prefs.edit().putBoolean(KEY_REMEMBER, false).apply();
                                            }
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

    public void resetPW(View view) {
        Intent i = new Intent(this, resetPassWord.class);
        startActivity(i);
    }

    public void GoTo(View view) {
        startActivity(new Intent(getApplicationContext(), SignUpActivity.class));
    }
}