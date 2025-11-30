package com.app.SalesInventory;

import androidx.annotation.NonNull;
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
import java.util.HashMap;
import java.util.Map;

public class SignInActivity extends BaseActivity  {

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
                    if (reloaded != null && reloaded.isEmailVerified()) {
                        String uid = reloaded.getUid();
                        fStore.collection("users").document(uid).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                progressBar.setVisibility(View.INVISIBLE);
                                if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                                    DocumentSnapshot doc = task.getResult();
                                    Boolean approved = doc.getBoolean("approved");
                                    if (approved == null) approved = false;
                                    if (approved) {
                                        applyRemoteTheme(doc);
                                        Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                        startActivity(intent);
                                        finish();
                                    }
                                } else {
                                    Toast.makeText(SignInActivity.this, "Unable to verify user profile", Toast.LENGTH_LONG).show();
                                }
                            }
                        });
                    } else {
                        progressBar.setVisibility(View.INVISIBLE);
                        prefs.edit().putBoolean(KEY_REMEMBER, false).apply();
                    }
                }
            });
        }
    }

    private void applyRemoteTheme(DocumentSnapshot doc) {
        if (doc == null) return;
        String themeName = doc.getString("themeName");
        Long primary = doc.getLong("primaryColor");
        Long secondary = doc.getLong("secondaryColor");
        Long accent = doc.getLong("accentColor");
        ThemeManager tm = ThemeManager.getInstance(this);
        if (themeName != null && !themeName.isEmpty()) {
            tm.setCurrentTheme(themeName);
        }
        if (primary != null && secondary != null && accent != null) {
            tm.setCustomColors(primary.intValue(), secondary.intValue(), accent.intValue());
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
                if (task.isSuccessful()) {
                    FirebaseUser user = fAuth.getCurrentUser();
                    if (user != null) {
                        user.reload().addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> t) {
                                progressBar.setVisibility(View.INVISIBLE);
                                FirebaseUser reloaded = fAuth.getCurrentUser();
                                if (reloaded != null && reloaded.isEmailVerified()) {
                                    String uid = reloaded.getUid();
                                    fStore.collection("users").document(uid).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                                        @Override
                                        public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                                            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                                                DocumentSnapshot doc = task.getResult();
                                                Boolean approved = doc.getBoolean("approved");
                                                if (approved == null) approved = false;
                                                if (approved) {
                                                    if (rememberCheck != null && rememberCheck.isChecked()) {
                                                        prefs.edit().putBoolean(KEY_REMEMBER, true).apply();
                                                    } else {
                                                        prefs.edit().putBoolean(KEY_REMEMBER, false).apply();
                                                    }
                                                    applyRemoteTheme(doc);
                                                    Intent intent = new Intent(getApplicationContext(), MainActivity.class);
                                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                                    startActivity(intent);
                                                    finish();
                                                } else {
                                                    Toast.makeText(SignInActivity.this, "Please wait for admin approval", Toast.LENGTH_LONG).show();
                                                    Intent intent = new Intent(getApplicationContext(), WaitingVerificationActivity.class);
                                                    startActivity(intent);
                                                    finish();
                                                }
                                            } else {
                                                Toast.makeText(SignInActivity.this, "User profile missing", Toast.LENGTH_LONG).show();
                                            }
                                        }
                                    });
                                } else {
                                    Toast.makeText(SignInActivity.this, "Please verify your email before logging in. Check your inbox.", Toast.LENGTH_LONG).show();
                                    fAuth.signOut();
                                }
                            }
                        });
                    } else {
                        progressBar.setVisibility(View.INVISIBLE);
                        Toast.makeText(SignInActivity.this, "Sign in failed", Toast.LENGTH_LONG).show();
                    }
                } else {
                    progressBar.setVisibility(View.INVISIBLE);
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