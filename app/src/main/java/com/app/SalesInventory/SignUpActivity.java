package com.app.SalesInventory;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SignUpActivity extends BaseActivity {
    private static final int TERMS_REQUEST_CODE = 101;

    private TextInputLayout tilPhone, tilCPassword;
    private TextInputEditText mName, mUserName, mEmail, mPhone, mPassword, mCPassword;
    private TextView reqLength, reqUpper, reqLower, reqNumber, reqSpecial;
    private CheckBox mCheck;
    private Button btnSignUp, btnSignInPage;
    private ProgressBar progressBar;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;

    private boolean isPasswordValid = false;
    private boolean isPhoneValid = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        tilPhone = findViewById(R.id.tilPhone);
        tilCPassword = findViewById(R.id.tilCPassword);

        mName = findViewById(R.id.mName);
        mUserName = findViewById(R.id.mUserName); // Bound Username
        mEmail = findViewById(R.id.mEmail);
        mPhone = findViewById(R.id.mPhone);
        mPassword = findViewById(R.id.mPassword);
        mCPassword = findViewById(R.id.mCPassword);
        mCheck = findViewById(R.id.mCheck);
        btnSignUp = findViewById(R.id.BtnSignUp);
        btnSignInPage = findViewById(R.id.SignInPage);
        progressBar = findViewById(R.id.progressBar);

        reqLength = findViewById(R.id.reqLength);
        reqUpper = findViewById(R.id.reqUpper);
        reqLower = findViewById(R.id.reqLower);
        reqNumber = findViewById(R.id.reqNumber);
        reqSpecial = findViewById(R.id.reqSpecial);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        setupRealtimeValidation();

        btnSignInPage.setOnClickListener(v -> {
            Intent i = new Intent(SignUpActivity.this, SignInActivity.class);
            startActivity(i);
            finish();
        });

        if (android.os.Build.VERSION.SDK_INT >= 21) {
            Window window = this.getWindow();
            window.setStatusBarColor(this.getResources().getColor(R.color.statusBarColor));
        }

        setupTermsLink();
        btnSignUp.setOnClickListener(v -> attemptSignUp());
    }

    private void setupRealtimeValidation() {
        mPhone.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String phone = s.toString();
                if (phone.length() > 0 && phone.length() < 10) {
                    tilPhone.setError("Invalid phone number. Must be 10 digits.");
                    isPhoneValid = false;
                } else {
                    tilPhone.setError(null);
                    isPhoneValid = phone.length() == 10;
                }
            }
        });

        mPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String pwd = s.toString();
                boolean hasLength = pwd.length() >= 8;
                boolean hasUpper = pwd.matches(".*[A-Z].*");
                boolean hasLower = pwd.matches(".*[a-z].*");
                boolean hasNumber = pwd.matches(".*[0-9].*");
                boolean hasSpecial = pwd.matches(".*[@#$%^&+=!].*");

                updateRequirementUI(reqLength, hasLength, "At least 8 characters");
                updateRequirementUI(reqUpper, hasUpper, "At least one uppercase letter");
                updateRequirementUI(reqLower, hasLower, "At least one lowercase letter");
                updateRequirementUI(reqNumber, hasNumber, "At least one number");
                updateRequirementUI(reqSpecial, hasSpecial, "At least one special character (@#$%^&+=!)");

                isPasswordValid = hasLength && hasUpper && hasLower && hasNumber && hasSpecial;
            }
        });

        mCPassword.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                String pwd = mPassword.getText().toString();
                String cpwd = s.toString();
                if (!cpwd.isEmpty() && !pwd.equals(cpwd)) tilCPassword.setError("Passwords do not match");
                else tilCPassword.setError(null);
            }
        });
    }

    private void updateRequirementUI(TextView tv, boolean isValid, String text) {
        if (isValid) {
            tv.setText("✓ " + text);
            tv.setTextColor(Color.parseColor("#4CAF50"));
        } else {
            tv.setText("✗ " + text);
            tv.setTextColor(Color.parseColor("#757575"));
        }
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
        String username = mUserName.getText() != null ? mUserName.getText().toString().trim(): "";
        String email = mEmail.getText() != null ? mEmail.getText().toString().trim() : "";
        String rawPhone = mPhone.getText() != null ? mPhone.getText().toString().trim() : "";
        String fullPhone = "+63" + rawPhone;
        String password = mPassword.getText() != null ? mPassword.getText().toString() : "";
        String cpassword = mCPassword.getText() != null ? mCPassword.getText().toString() : "";

        if (name.isEmpty() || username.isEmpty() || email.isEmpty() || password.isEmpty() || cpassword.isEmpty() || !mCheck.isChecked()) {
            Toast.makeText(this, "Please complete the form and accept terms", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isPhoneValid) {
            Toast.makeText(this, "Please enter a valid 10-digit phone number", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isPasswordValid) {
            Toast.makeText(this, "Password does not meet the minimum requirements", Toast.LENGTH_LONG).show();
            return;
        }

        if (!password.equals(cpassword)) {
            Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(android.view.View.VISIBLE);

        // 1. Check if Username is already taken
        firestore.collection("users").whereEqualTo("username", username).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && !task.getResult().isEmpty()) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(this, "Username is already taken, please choose another.", Toast.LENGTH_LONG).show();
                mUserName.setError("Taken");
            } else {
                // 2. If unique, proceed to Firebase Auth
                createAccountAndSave(name, username, email, fullPhone, password);
            }
        });
    }

    private void createAccountAndSave(String name, String username, String email, String fullPhone, String password) {
        firebaseAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(createTask -> {
            if (createTask.isSuccessful()) {
                FirebaseUser createdUser = firebaseAuth.getCurrentUser();
                if (createdUser == null) return;

                // 3. Save ALL data (including Username) to Firestore so Login-by-Username works later
                Map<String, Object> userMap = new HashMap<>();
                userMap.put("name", name);
                userMap.put("Name", name);
                userMap.put("username", username);
                userMap.put("email", email);
                userMap.put("Email", email);
                userMap.put("phone", fullPhone);
                userMap.put("Phone", fullPhone);
                userMap.put("role", "Admin");
                userMap.put("approved", false);

                firestore.collection("users").document(createdUser.getUid()).set(userMap).addOnCompleteListener(dbTask -> {
                    createdUser.sendEmailVerification().addOnCompleteListener(sendTask -> {
                        runOnUiThread(() -> progressBar.setVisibility(android.view.View.GONE));
                        Intent i = new Intent(SignUpActivity.this, WaitingVerificationActivity.class);
                        i.putExtra("user_name", name);
                        i.putExtra("user_phone", fullPhone);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(i);
                        finish();
                    });
                });
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
        if (requestCode == TERMS_REQUEST_CODE && resultCode == RESULT_OK) mCheck.setChecked(true);
    }
}