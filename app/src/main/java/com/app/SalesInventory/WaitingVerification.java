package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import android.os.Handler; // Imported

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class WaitingVerification extends AppCompatActivity {

    FirebaseAuth fAuth;
    Button btnRefresh, btnResend;
    Handler handler = new Handler();
    Runnable checkTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waiting_verification);

        fAuth = FirebaseAuth.getInstance();
        btnRefresh = findViewById(R.id.btnRefresh);
        btnResend = findViewById(R.id.btnResend);

        // Manual Check
        btnRefresh.setOnClickListener(v -> checkVerification(true));

        // Resend Email
        btnResend.setOnClickListener(v -> {
            FirebaseUser user = fAuth.getCurrentUser();
            if(user != null){
                user.sendEmailVerification()
                        .addOnSuccessListener(a -> Toast.makeText(WaitingVerification.this, "Link Resent!", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e -> Toast.makeText(WaitingVerification.this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });

        // START THE AUTO-CHECK LOOP
        startAutoCheck();
    }

    private void startAutoCheck() {
        checkTask = new Runnable() {
            @Override
            public void run() {
                checkVerification(false);
                handler.postDelayed(this, 2000);
            }
        };
        handler.post(checkTask);
    }

    private void checkVerification(boolean showMessage) {
        FirebaseUser user = fAuth.getCurrentUser();
        if (user == null) {
            startActivity(new Intent(this, SignIn.class));
            finish();
            return;
        }

        user.reload().addOnSuccessListener(aVoid -> {
            if (user.isEmailVerified()) {
                handler.removeCallbacks(checkTask);

                Intent intent = new Intent(WaitingVerification.this, Dashboard.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            } else {
                if(showMessage) Toast.makeText(this, "Email not verified yet.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null && checkTask != null) {
            handler.removeCallbacks(checkTask);
        }
    }
}