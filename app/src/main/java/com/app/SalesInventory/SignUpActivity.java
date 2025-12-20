package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.functions.FirebaseFunctions;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.Patterns;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class SignUpActivity extends BaseActivity {
    private static final int TERMS_REQUEST_CODE = 101;
    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z. ]{1,80}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+63\\d{10}$");
    private TextInputEditText mName;
    private TextInputEditText mEmail;
    private TextInputEditText mPhone;
    private TextInputEditText mPassword;
    private TextInputEditText mCPassword;
    private CheckBox mCheck;
    private Button btnSignUp;
    private Button btnSignInPage;
    private ProgressBar progressBar;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private FirebaseFunctions functions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);
        mName = findViewById(R.id.mName);
        mEmail = findViewById(R.id.mEmail);
        mPhone = findViewById(R.id.mPhone);
        mPassword = findViewById(R.id.mPassword);
        mCPassword = findViewById(R.id.mCPassword);
        mCheck = findViewById(R.id.mCheck);
        btnSignUp = findViewById(R.id.BtnSignUp);
        btnSignInPage = findViewById(R.id.SignInPage);
        progressBar = findViewById(R.id.progressBar);
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        functions = FirebaseFunctions.getInstance();
        btnSignInPage.setOnClickListener(v -> {
            Intent i = new Intent(SignUpActivity.this, SignInActivity.class);
            startActivity(i);
            finish();
        });
        if (android.os.Build.VERSION.SDK_INT >= 21) {
            Window window = this.getWindow();
            window.setStatusBarColor(this.getResources().getColor(R.color.statusBarColor));
        }
        applyInputFilters();
        setupTermsLink();
        btnSignUp.setOnClickListener(v -> attemptSignUp());
    }

    private void applyInputFilters() {
        InputFilter nameFilter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence src, int start, int end, Spanned dest, int dstart, int dend) {
                for (int i = start; i < end; i++) {
                    char c = src.charAt(i);
                    if (!(Character.isLetter(c) || c == '.' || Character.isSpaceChar(c))) {
                        return "";
                    }
                }
                int newLen = dest.length() - (dend - dstart) + (end - start);
                if (newLen > 80) return "";
                return null;
            }
        };
        mName.setFilters(new InputFilter[] { nameFilter });
        InputFilter phoneFilter = new InputFilter.LengthFilter(13);
        mPhone.setFilters(new InputFilter[] { phoneFilter });
        if (mPhone.getText() == null || mPhone.getText().toString().trim().isEmpty()) {
            mPhone.setText("+63");
            mPhone.setSelection(mPhone.getText().length());
        }
        mPhone.addTextChangedListener(new android.text.TextWatcher() {
            boolean self = false;
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                if (self) return;
                self = true;
                String val = s.toString();
                String cleaned = val.replaceAll("[^0-9+]", "");
                if (!cleaned.startsWith("+")) {
                    cleaned = cleaned.replaceFirst("^0+", "");
                    if (cleaned.startsWith("63")) cleaned = "+" + cleaned;
                    else cleaned = "+63" + cleaned.replaceFirst("^\\+", "");
                }
                if (!cleaned.startsWith("+63")) {
                    String digitsOnly = cleaned.replaceAll("[^0-9]", "");
                    cleaned = "+63" + digitsOnly;
                }
                String afterPrefix = cleaned.length() > 3 ? cleaned.substring(3).replaceAll("[^0-9]", "") : "";
                if (afterPrefix.length() > 10) afterPrefix = afterPrefix.substring(0, 10);
                String result = "+63" + afterPrefix;
                mPhone.setText(result);
                mPhone.setSelection(result.length());
                self = false;
            }
        });
        InputFilter passwordFilter = new InputFilter() {
            @Override
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                String result = dest.subSequence(0, dstart) + source.toString() + dest.subSequence(dend, dest.length());
                if (result.length() > 64) return "";
                int nonAlnum = 0;
                for (int i = 0; i < result.length(); i++) {
                    char c = result.charAt(i);
                    if (!Character.isLetterOrDigit(c)) nonAlnum++;
                    if (nonAlnum > 1) return "";
                }
                return null;
            }
        };
        mPassword.setFilters(new InputFilter[] { passwordFilter });
        mCPassword.setFilters(new InputFilter[] { passwordFilter });
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
        if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || !mCheck.isChecked()) {
            Toast.makeText(this, "Please complete the form", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            Toast.makeText(this, "Name can only contain letters, spaces and dot (max 80 chars)", Toast.LENGTH_LONG).show();
            return;
        }
        if (TextUtils.isEmpty(email) || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(this, "Please enter a valid email address", Toast.LENGTH_LONG).show();
            return;
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            Toast.makeText(this, "Phone must start with +63 followed by 10 digits", Toast.LENGTH_LONG).show();
            return;
        }
        boolean usingPassword = password != null && !password.isEmpty();
        if (usingPassword) {
            if (!password.equals(cpassword)) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show();
                return;
            }
            if (password.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                return;
            }
            int nonAlnum = 0;
            for (int i = 0; i < password.length(); i++) {
                char c = password.charAt(i);
                if (!Character.isLetterOrDigit(c)) nonAlnum++;
                if (nonAlnum > 1) {
                    Toast.makeText(this, "Password may contain at most one special symbol", Toast.LENGTH_SHORT).show();
                    return;
                }
            }
        }
        progressBar.setVisibility(android.view.View.VISIBLE);
        String passwordToUse = usingPassword ? password : generateRandomPassword();
        firebaseAuth.createUserWithEmailAndPassword(email, passwordToUse).addOnCompleteListener(createTask -> {
            if (createTask.isSuccessful()) {
                FirebaseUser createdUser = firebaseAuth.getCurrentUser();
                if (createdUser == null) {
                    runOnUiThread(() -> progressBar.setVisibility(android.view.View.GONE));
                    return;
                }
                String uid = createdUser.getUid();
                boolean autoApprove = true;
                Map<String, Object> profile = new HashMap<>();
                profile.put("photoUrl", createdUser.getPhotoUrl() != null ? createdUser.getPhotoUrl().toString() : "");
                profile.put("uid", uid);
                profile.put("email", createdUser.getEmail() != null ? createdUser.getEmail() : "");
                profile.put("name", name);
                profile.put("phone", phone);
                profile.put("role", "Admin");
                profile.put("approved", autoApprove);
                profile.put("createdAt", System.currentTimeMillis());
                firestore.collection("users").document(uid).set(profile).addOnCompleteListener(setTask -> {
                    Map<String, Object> adminDoc = new HashMap<>();
                    adminDoc.put("uid", uid);
                    adminDoc.put("email", createdUser.getEmail() != null ? createdUser.getEmail() : "");
                    adminDoc.put("name", name);
                    adminDoc.put("phone", phone);
                    adminDoc.put("role", "Admin");
                    adminDoc.put("approved", autoApprove);
                    adminDoc.put("createdAt", System.currentTimeMillis());
                    firestore.collection("admin").document(uid).set(adminDoc).addOnCompleteListener(adminSetTask -> {
                        Map<String, Object> data = new HashMap<>();
                        data.put("uid", uid);
                        data.put("name", name);
                        data.put("email", createdUser.getEmail() != null ? createdUser.getEmail() : "");
                        data.put("phone", phone);
                        functions.getHttpsCallable("createAdminOwner").call(data).addOnCompleteListener(fnTask -> {
                            createdUser.sendEmailVerification().addOnCompleteListener(sendTask -> {
                                runOnUiThread(() -> progressBar.setVisibility(android.view.View.GONE));
                                Intent i = new Intent(SignUpActivity.this, WaitingVerificationActivity.class);
                                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                startActivity(i);
                                finish();
                            }).addOnFailureListener(e -> {
                                runOnUiThread(() -> {
                                    progressBar.setVisibility(android.view.View.GONE);
                                    Intent i = new Intent(SignUpActivity.this, WaitingVerificationActivity.class);
                                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(i);
                                    finish();
                                });
                            });
                        }).addOnFailureListener(e -> {
                            runOnUiThread(() -> {
                                progressBar.setVisibility(android.view.View.GONE);
                                Toast.makeText(SignUpActivity.this, "Failed to register admin: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
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

    private String generateRandomPassword() {
        SecureRandom rnd = new SecureRandom();
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            sb.append(chars.charAt(rnd.nextInt(chars.length())));
        }
        return sb.toString();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == TERMS_REQUEST_CODE && resultCode == RESULT_OK) {
            mCheck.setChecked(true);
        }
    }
}