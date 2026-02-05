package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class WaitingVerificationActivity extends BaseActivity {
    private Button btnRefresh, btnResend;
    private ProgressBar progressBarWait;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;
    private Handler autoCheckHandler;
    private Runnable autoCheckRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waiting_verification);

        btnRefresh = findViewById(R.id.btnRefresh);
        btnResend = findViewById(R.id.btnResend);
        progressBarWait = findViewById(R.id.progressBarWait);

        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        btnRefresh.setOnClickListener(v -> checkVerification());
        btnResend.setOnClickListener(v -> resendVerification());

        startAutoCheck();
    }

    private void startAutoCheck() {
        autoCheckHandler = new Handler();
        autoCheckRunnable = new Runnable() {
            @Override
            public void run() {
                FirebaseUser user = auth.getCurrentUser();
                if (user != null) {
                    user.reload().addOnSuccessListener(aVoid -> {
                        if (user.isEmailVerified()) {
                            checkVerification(); // Proceed to profile creation/Main
                        } else {
                            autoCheckHandler.postDelayed(this, 3000); // Check again in 3s
                        }
                    });
                }
            }
        };
        autoCheckHandler.postDelayed(autoCheckRunnable, 3000);
    }

    private void checkVerification() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, SignInActivity.class));
            finish();
            return;
        }

        if (user.isEmailVerified()) {
            stopAutoCheck();
            String uid = user.getUid();
            firestore.collection("users").document(uid).get().addOnCompleteListener(task -> {
                if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                    goToMain();
                } else {
                    createAdminProfile(user);
                }
            });
        } else {
            Toast.makeText(this, "Please verify your email first.", Toast.LENGTH_SHORT).show();
        }
    }

    private void createAdminProfile(FirebaseUser user) {
        String name = getIntent().getStringExtra("user_name");
        String phone = getIntent().getStringExtra("user_phone");

        Map<String, Object> profile = new HashMap<>();
        profile.put("uid", user.getUid());
        profile.put("email", user.getEmail());
        profile.put("name", name != null ? name : "New Admin");
        profile.put("phone", phone != null ? phone : "");
        profile.put("ownerAdminId", user.getUid());
        profile.put("role", "Admin");
        profile.put("approved", true);
        profile.put("createdAt", System.currentTimeMillis());

        firestore.collection("users").document(user.getUid()).set(profile)
                .addOnSuccessListener(aVoid -> goToMain())
                .addOnFailureListener(e -> Toast.makeText(this, "Profile Error: " + e.getMessage(), Toast.LENGTH_LONG).show());
    }
    private void resendVerification() {
        FirebaseUser user = auth.getCurrentUser();
        if (user != null) {
            user.sendEmailVerification().addOnSuccessListener(aVoid ->
                    Toast.makeText(this, "Verification email sent", Toast.LENGTH_SHORT).show());
        }
    }

    private void goToMain() {
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void stopAutoCheck() {
        if (autoCheckHandler != null && autoCheckRunnable != null) {
            autoCheckHandler.removeCallbacks(autoCheckRunnable);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAutoCheck();
    }
}