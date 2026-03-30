package com.app.SalesInventory;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

import com.bumptech.glide.Glide;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class BusinessSetupActivity extends BaseActivity {

    private ImageView ivBusinessLogo;
    private TextInputLayout tilBusinessName, tilAddress, tilLandmark;
    private TextInputEditText etBusinessName, etAddress, etLandmark;
    private Button btnSaveBusiness;
    private ProgressBar progressBar;

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private StorageReference storageReference;

    private Uri imageUri = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_business_setup);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
        storageReference = FirebaseStorage.getInstance().getReference("business_logos");

        ivBusinessLogo = findViewById(R.id.ivBusinessLogo);
        tilBusinessName = findViewById(R.id.tilBusinessName);
        etBusinessName = findViewById(R.id.etBusinessName);
        tilAddress = findViewById(R.id.tilAddress);
        etAddress = findViewById(R.id.etAddress);
        tilLandmark = findViewById(R.id.tilLandmark);
        etLandmark = findViewById(R.id.etLandmark);

        btnSaveBusiness = findViewById(R.id.btnSaveBusiness);
        progressBar = findViewById(R.id.progressBar);

        ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        if (isDestroyed() || isFinishing()) return; // SAFEGUARD
                        imageUri = result.getData().getData();
                        Glide.with(this).load(imageUri).circleCrop().into(ivBusinessLogo);
                    }
                }
        );

        ivBusinessLogo.setOnClickListener(v -> {
            Intent intent = new Intent();
            intent.setType("image/*");
            intent.setAction(Intent.ACTION_GET_CONTENT);
            imagePickerLauncher.launch(Intent.createChooser(intent, "Select Logo"));
        });

        btnSaveBusiness.setOnClickListener(v -> saveBusinessProfile());
    }

    private void saveBusinessProfile() {
        String businessName = etBusinessName.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String landmark = etLandmark.getText().toString().trim();

        boolean hasError = false;

        if (businessName.isEmpty()) {
            tilBusinessName.setError("Business name is required");
            hasError = true;
        } else {
            tilBusinessName.setError(null);
        }

        if (address.isEmpty()) {
            tilAddress.setError("Complete address is required");
            hasError = true;
        } else {
            tilAddress.setError(null);
        }

        if (hasError) return;

        FirebaseUser user = firebaseAuth.getCurrentUser();
        if (user == null) return;

        setLoading(true);

        if (imageUri != null) {
            String fileName = user.getUid() + ".jpg";
            StorageReference fileRef = storageReference.child(fileName);

            fileRef.putFile(imageUri)
                    .addOnSuccessListener(taskSnapshot -> {
                        fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            String imageUrl = uri.toString();
                            saveToFirestore(user.getUid(), businessName, address, landmark, imageUrl);
                        });
                    })
                    .addOnFailureListener(e -> {
                        setLoading(false);
                        Toast.makeText(BusinessSetupActivity.this, "Failed to upload logo: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            saveToFirestore(user.getUid(), businessName, address, landmark, null);
        }
    }

    private void saveToFirestore(String userId, String name, String address, String landmark, String imageUrl) {
        Map<String, Object> businessData = new HashMap<>();
        businessData.put("businessName", name);
        businessData.put("address", address);
        businessData.put("landmark", landmark);
        businessData.put("businessType", "Coffee Shop");

        if (imageUrl != null) {
            businessData.put("businessLogoUrl", imageUrl);
        }

        firestore.collection("users").document(userId)
                .set(businessData, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    setLoading(false);
                    Toast.makeText(BusinessSetupActivity.this, "Setup Complete!", Toast.LENGTH_SHORT).show();
                    navigateToDashboard();
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(BusinessSetupActivity.this, "Error saving profile: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void navigateToDashboard() {
        Intent intent = new Intent(BusinessSetupActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void setLoading(boolean isLoading) {
        progressBar.setVisibility(isLoading ? View.VISIBLE : View.INVISIBLE);
        btnSaveBusiness.setEnabled(!isLoading);
        ivBusinessLogo.setEnabled(!isLoading);
        etBusinessName.setEnabled(!isLoading);
        etAddress.setEnabled(!isLoading);
        etLandmark.setEnabled(!isLoading);
    }
}