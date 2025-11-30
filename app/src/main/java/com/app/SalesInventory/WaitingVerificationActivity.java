package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.HashMap;
import java.util.Map;

public class WaitingVerificationActivity extends BaseActivity  {
    private Button btnRefresh;
    private Button btnResend;
    private FirebaseAuth auth;
    private FirebaseFirestore firestore;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waiting_verification);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnResend = findViewById(R.id.btnResend);
        auth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        btnRefresh.setOnClickListener(v -> checkVerification());
        btnResend.setOnClickListener(v -> resendVerification());
    }

    private void resendVerification() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "No signed-in user", Toast.LENGTH_SHORT).show();
            return;
        }
        user.sendEmailVerification().addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Verification email sent", Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Failed to send verification: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    private void checkVerification() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Intent i = new Intent(this, SignInActivity.class);
            startActivity(i);
            finish();
            return;
        }
        user.reload().addOnSuccessListener(aVoid -> {
            FirebaseUser reloaded = auth.getCurrentUser();
            if (reloaded != null && reloaded.isEmailVerified()) {
                String uid = reloaded.getUid();
                firestore.collection("users").document(uid).get().addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                        DocumentSnapshot doc = task.getResult();
                        Object approvedObj = doc.get("approved");
                        boolean approved = false;
                        if (approvedObj instanceof Boolean) {
                            approved = (Boolean) approvedObj;
                        }
                        if (approved) {
                            Intent i = new Intent(WaitingVerificationActivity.this, MainActivity.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                            finish();
                        } else {
                            Toast.makeText(WaitingVerificationActivity.this, "Email verified. Waiting for admin approval.", Toast.LENGTH_LONG).show();
                        }
                    } else {
                        Map<String, Object> profile = new HashMap<>();
                        profile.put("uid", uid);
                        profile.put("email", reloaded.getEmail() != null ? reloaded.getEmail() : "");
                        profile.put("name", reloaded.getDisplayName() != null ? reloaded.getDisplayName() : "");
                        profile.put("phone", "");
                        profile.put("role", "Staff");
                        profile.put("approved", true);
                        profile.put("createdAt", System.currentTimeMillis());
                        firestore.collection("users").document(uid).set(profile).addOnSuccessListener(aVoid2 -> {
                            Intent i = new Intent(WaitingVerificationActivity.this, MainActivity.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(i);
                            finish();
                        }).addOnFailureListener(e -> {
                            Toast.makeText(WaitingVerificationActivity.this, "Failed to create profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                }).addOnFailureListener(e -> {
                    Toast.makeText(WaitingVerificationActivity.this, "Failed to fetch profile: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } else {
                Toast.makeText(WaitingVerificationActivity.this, "Email not verified yet.", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(e -> {
            Toast.makeText(WaitingVerificationActivity.this, "Failed to check verification: " + e.getMessage(), Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}