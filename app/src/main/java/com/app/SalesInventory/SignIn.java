package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class SignIn extends AppCompatActivity {

    EditText Email, Password;
    ProgressBar progressBar;
    FirebaseAuth fAuth;
    TextView forgotpassword;
    Button BtnSingIn;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sign_in);

        BtnSingIn = findViewById(R.id.BtnSignIn);
        Email = findViewById(R.id.email1);
        Password = findViewById(R.id.password1);
        progressBar = findViewById(R.id.progressBar2);
        forgotpassword = findViewById(R.id.forgotpassword);

        fAuth = FirebaseAuth.getInstance();

        FirebaseUser currentUser = fAuth.getCurrentUser();
        if (currentUser != null && currentUser.isEmailVerified()) {
            startActivity(new Intent(getApplicationContext(), Dashboard.class));
            finish();
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
                    FirebaseUser user = fAuth.getCurrentUser();
                    if (user != null) {
                        if (user.isEmailVerified()) {
                            Toast.makeText(SignIn.this, "Welcome!", Toast.LENGTH_SHORT).show();
                            startActivity(new Intent(getApplicationContext(), Dashboard.class));
                            finish();
                        } else {
                            Toast.makeText(SignIn.this, "Please verify your email before logging in. Check your inbox.", Toast.LENGTH_LONG).show();
                            fAuth.signOut();
                        }
                    }
                } else {
                    String errorMessage = task.getException() != null ? task.getException().getMessage() : "Login failed";
                    Toast.makeText(SignIn.this, "Error: " + errorMessage, Toast.LENGTH_LONG).show();
                }

                Email.getText().clear();
                Password.getText().clear();
            }
        });
    }

    public void resetPW(View view) {
        startActivity(new Intent(getApplicationContext(), resetPassWord.class));
    }

    public void GoTo(View view) {
        startActivity(new Intent(getApplicationContext(), Signup.class));
    }
}