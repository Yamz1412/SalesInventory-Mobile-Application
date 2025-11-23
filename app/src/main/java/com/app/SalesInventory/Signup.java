package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class Signup extends AppCompatActivity {

    Button BtnSignUp, signInButton;
    EditText mName, mPassword, mEmail, mPhone, mCPassword;
    ProgressBar progressBar;
    CheckBox mCheck;
    Spinner roleSpinner;

    FirebaseAuth fAuth;
    FirebaseFirestore fStore;
    String UserID;

    private static final int TERMS_REQUEST_CODE = 101;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_up);

        BtnSignUp = findViewById(R.id.BtnSignUp);
        signInButton = findViewById(R.id.SignInPage);
        mName = findViewById(R.id.mName);
        mPassword = findViewById(R.id.mPassword);
        mEmail = findViewById(R.id.mEmail);
        mPhone = findViewById(R.id.mPhone);
        mCPassword = findViewById(R.id.mCPassword);
        mCheck = findViewById(R.id.mCheck);
        progressBar = findViewById(R.id.progressBar);
        roleSpinner = findViewById(R.id.RoleSpinner);

        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();

        String[] roles = {"Admin", "Employee"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, R.layout.spinner_item, roles);
        adapter.setDropDownViewResource(R.layout.spinner_item);
        roleSpinner.setAdapter(adapter);

        if (Build.VERSION.SDK_INT >= 21) {
            Window window = this.getWindow();
            window.setStatusBarColor(this.getResources().getColor(R.color.statusBarColor));
        }

        setupTermsLink();

        signInButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                navigateToSignIn();
            }
        });

        BtnSignUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleSignUp();
            }
        });
    }

    private void setupTermsLink() {
        String text = "I Accept Terms and Conditions";
        SpannableString ss = new SpannableString(text);

        ClickableSpan clickableSpan = new ClickableSpan() {
            @Override
            public void onClick(@NonNull View widget) {
                openConditions(widget);
            }

            @Override
            public void updateDrawState(@NonNull TextPaint ds) {
                super.updateDrawState(ds);
                ds.setUnderlineText(true);
                ds.setColor(ContextCompat.getColor(Signup.this, R.color.colorPrimary));
            }
        };

        ss.setSpan(clickableSpan, 9, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        mCheck.setText(ss);
        mCheck.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private void handleSignUp() {
        final String email = mEmail.getText().toString().trim();
        String password = mPassword.getText().toString().trim();
        String password2 = mCPassword.getText().toString().trim();
        final String name = mName.getText().toString().trim();
        final String phone = mPhone.getText().toString();

        if (TextUtils.isEmpty(name)) {
            mName.setError("Name is Required");
            return;
        }
        if (TextUtils.isEmpty(email)) {
            mEmail.setError("Email is Required");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            mPassword.setError("Password is Required");
            return;
        }
        if (password.length() < 6) {
            mPassword.setError("Password Must be 6 Characters or More");
            return;
        }
        if (TextUtils.isEmpty(password2)) {
            mCPassword.setError("Confirm Password Required");
            return;
        }
        if (!password.equals(password2)) {
            mCPassword.setError("Passwords do not match");
            return;
        }
        if (!mCheck.isChecked()) {
            Toast.makeText(Signup.this, "You Must Accept the Terms and Conditions", Toast.LENGTH_SHORT).show();
            return;
        }

        progressBar.setVisibility(View.VISIBLE);

        fAuth.createUserWithEmailAndPassword(email, password).addOnCompleteListener(new OnCompleteListener<AuthResult>() {
            @Override
            public void onComplete(@NonNull Task<AuthResult> task) {
                if (task.isSuccessful()) {
                    Log.d("DEBUG", "User Created.");
                    UserID = fAuth.getCurrentUser().getUid();

                    DocumentReference documentReference = fStore.collection("users").document(UserID);
                    Map<String, Object> user = new HashMap<>();
                    user.put("Name", name);
                    user.put("Email", email);
                    user.put("Phone", phone);
                    user.put("Role", roleSpinner.getSelectedItem().toString());

                    documentReference.set(user).addOnSuccessListener(new OnSuccessListener<Void>() {
                        @Override
                        public void onSuccess(Void aVoid) {
                            Log.d("Task", "User Profile Created");
                        }
                    });

                    FirebaseUser firebaseUser = fAuth.getCurrentUser();
                    if (firebaseUser != null) {
                        firebaseUser.sendEmailVerification().addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> emailTask) {
                                progressBar.setVisibility(View.INVISIBLE);

                                if (emailTask.isSuccessful()) {
                                    Log.d("DEBUG", "Verification email sent successfully to: " + email);
                                    Toast.makeText(Signup.this, "Account created! Please check your email (" + email + ") to verify.", Toast.LENGTH_LONG).show();

                                    Intent intent = new Intent(getApplicationContext(), WaitingVerification.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();

                                } else {
                                    Exception exception = emailTask.getException();
                                    Log.e("DEBUG", "Failed to send verification email: " + (exception != null ? exception.getMessage() : "Unknown error"));

                                    Toast.makeText(Signup.this, "Account created but failed to send verification email. Error: " + (exception != null ? exception.getMessage() : "Unknown"), Toast.LENGTH_LONG).show();

                                    Intent intent = new Intent(getApplicationContext(), WaitingVerification.class);
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                }
                            }
                        });
                    } else {
                        progressBar.setVisibility(View.INVISIBLE);
                        Toast.makeText(Signup.this, "Error: User creation failed", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(Signup.this, "Error: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.INVISIBLE);
                }
            }
        });
    }

    public void navigateToSignIn() {
        Intent intent = new Intent(this, SignIn.class);
        startActivity(intent);
    }

    public void openConditions(View view) {
        Intent intent = new Intent(getApplicationContext(), Conditions.class);
        startActivityForResult(intent, TERMS_REQUEST_CODE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == TERMS_REQUEST_CODE && resultCode == RESULT_OK) {
            mCheck.setChecked(true);
        }
    }
}