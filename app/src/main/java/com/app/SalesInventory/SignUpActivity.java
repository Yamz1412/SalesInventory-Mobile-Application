package com.app.SalesInventory;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends AppCompatActivity {
    private static final int TERMS_REQUEST_CODE = 101;
    private TextInputEditText mName;
    private TextInputEditText mEmail;
    private TextInputEditText mPhone;
    private Spinner roleSpinner;
    private TextInputEditText mPassword;
    private TextInputEditText mCPassword;
    private CheckBox mCheck;
    private Button btnSignUp;
    private Button btnSignInPage;
    private ProgressBar progressBar;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private boolean anyAdminExists = false;
    private boolean adminCheckCompleted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);
        mName = findViewById(R.id.mName);
        mEmail = findViewById(R.id.mEmail);
        mPhone = findViewById(R.id.mPhone);
        roleSpinner = findViewById(R.id.RoleSpinner);
        mPassword = findViewById(R.id.mPassword);
        mCPassword = findViewById(R.id.mCPassword);
        mCheck = findViewById(R.id.mCheck);
        btnSignUp = findViewById(R.id.BtnSignUp);
        btnSignInPage = findViewById(R.id.SignInPage);
        progressBar = findViewById(R.id.progressBar);
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        ArrayAdapter<String> staffOnlyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, new String[] {"Staff"});
        staffOnlyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        roleSpinner.setAdapter(staffOnlyAdapter);
        roleSpinner.setPopupBackgroundResource(android.R.color.white);

        btnSignInPage.setOnClickListener(v -> {
            Intent i = new Intent(SignUpActivity.this, SignInActivity.class);
            startActivity(i);
            finish();
        });

        firestore.collection("users").whereEqualTo("role", "Admin").limit(1).get().addOnCompleteListener(task -> {
            adminCheckCompleted = true;
            if (task.isSuccessful()) {
                QuerySnapshot snap = task.getResult();
                if (snap != null && !snap.isEmpty()) {
                    anyAdminExists = true;
                    ArrayAdapter<String> staffOnly = new ArrayAdapter<>(SignUpActivity.this, android.R.layout.simple_spinner_item, new String[] {"Staff"});
                    staffOnly.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    roleSpinner.setAdapter(staffOnly);
                    roleSpinner.setPopupBackgroundResource(android.R.color.white);
                } else {
                    anyAdminExists = false;
                    ArrayAdapter<String> adminFirst = new ArrayAdapter<>(SignUpActivity.this, android.R.layout.simple_spinner_item, new String[] {"Admin", "Staff"});
                    adminFirst.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                    roleSpinner.setAdapter(adminFirst);
                    roleSpinner.setPopupBackgroundResource(android.R.color.white);
                }
            } else {
                anyAdminExists = true;
                ArrayAdapter<String> staffOnlyFallback = new ArrayAdapter<>(SignUpActivity.this, android.R.layout.simple_spinner_item, new String[] {"Staff"});
                staffOnlyFallback.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                roleSpinner.setAdapter(staffOnlyFallback);
                roleSpinner.setPopupBackgroundResource(android.R.color.white);
            }
        });

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            Window window = this.getWindow();
            window.setStatusBarColor(this.getResources().getColor(R.color.statusBarColor));
        }

        setupTermsLink();
        btnSignUp.setOnClickListener(v -> attemptSignUp());
    }

    private void setupTermsLink() {
        String text = "I Accept Terms and Conditions";
        android.text.SpannableString ss = new android.text.SpannableString(text);
        android.text.style.ClickableSpan clickableSpan = new android.text.style.ClickableSpan() {
            @Override
            public void onClick(@NonNull android.view.View widget) {
                Intent intent = new Intent(SignUpActivity.this, Conditions.class);
                startActivityForResult(intent, TERMS_REQUEST_CODE);
            }
            @Override
            public void updateDrawState(@NonNull android.text.TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(true);
                ds.setColor(ContextCompat.getColor(SignUpActivity.this, R.color.colorPrimary));
            }
        };
        ss.setSpan(clickableSpan, 9, text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mCheck.setText(ss);
        mCheck.setMovementMethod(android.text.method.LinkMovementMethod.getInstance());
    }

    private void attemptSignUp() {
        String name = mName.getText() != null ? mName.getText().toString().trim() : "";
        String email = mEmail.getText() != null ? mEmail.getText().toString().trim() : "";
        String phone = mPhone.getText() != null ? mPhone.getText().toString().trim() : "";
        String password = mPassword.getText() != null ? mPassword.getText().toString() : "";
        String cpassword = mCPassword.getText() != null ? mCPassword.getText().toString() : "";
        String role = roleSpinner.getSelectedItem() != null ? roleSpinner.getSelectedItem().toString() : "Staff";
        if (name.isEmpty() || email.isEmpty() || password.isEmpty() || cpassword.isEmpty() || !mCheck.isChecked()) {
            Toast.makeText(this, "Please complete the form", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(cpassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!adminCheckCompleted) {
            Toast.makeText(this, "Please wait...", Toast.LENGTH_SHORT).show();
            return;
        }
        boolean creatingAdmin = "Admin".equalsIgnoreCase(role);
        if (creatingAdmin && anyAdminExists) {
            Toast.makeText(this, "Admin account already exists. Only Staff registrations are allowed.", Toast.LENGTH_LONG).show();
            return;
        }
        progressBar.setVisibility(android.view.View.VISIBLE);
        firebaseAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(createTask -> {
            if (createTask.isSuccessful()) {
                FirebaseUser createdUser = firebaseAuth.getCurrentUser();
                if (createdUser == null) {
                    runOnUiThread(() -> progressBar.setVisibility(android.view.View.GONE));
                    return;
                }
                String uid = createdUser.getUid();
                boolean autoApprove = creatingAdmin && !anyAdminExists;
                Map<String, Object> profile = new HashMap<>();
                profile.put("uid", uid);
                profile.put("email", createdUser.getEmail() != null ? createdUser.getEmail() : "");
                profile.put("name", name);
                profile.put("phone", phone);
                profile.put("role", creatingAdmin ? "Admin" : "Staff");
                profile.put("approved", autoApprove);
                profile.put("createdAt", System.currentTimeMillis());
                firestore.collection("users").document(uid).set(profile).addOnCompleteListener(setTask -> {
                    createdUser.sendEmailVerification().addOnCompleteListener(sendTask -> {
                        runOnUiThread(() -> progressBar.setVisibility(android.view.View.GONE));
                        if (sendTask.isSuccessful()) {
                            Toast.makeText(SignUpActivity.this, "Account created! Check your email to verify.", Toast.LENGTH_LONG).show();
                            Intent i = new Intent(SignUpActivity.this, WaitingVerificationActivity.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                            finish();
                        } else {
                            Toast.makeText(SignUpActivity.this, "Account created but failed to send verification: " + (sendTask.getException() != null ? sendTask.getException().getMessage() : ""), Toast.LENGTH_LONG).show();
                            Intent i = new Intent(SignUpActivity.this, WaitingVerificationActivity.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                            finish();
                        }
                    }).addOnFailureListener(e -> {
                        runOnUiThread(() -> {
                            progressBar.setVisibility(android.view.View.GONE);
                            Toast.makeText(SignUpActivity.this, "Account created but failed to send verification: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            Intent i = new Intent(SignUpActivity.this, WaitingVerificationActivity.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                            finish();
                        });
                    });
                }).addOnFailureListener(e -> runOnUiThread(() -> {
                    progressBar.setVisibility(android.view.View.GONE);
                    Toast.makeText(SignUpActivity.this, "Failed to save profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }));
            } else {
                runOnUiThread(() -> {
                    progressBar.setVisibility(android.view.View.GONE);
                    String msg = createTask.getException() != null ? createTask.getException().getMessage() : "Sign up failed";
                    Toast.makeText(SignUpActivity.this, msg, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == TERMS_REQUEST_CODE && resultCode == RESULT_OK) {
            mCheck.setChecked(true);
        }
    }
}