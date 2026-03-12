package com.app.SalesInventory;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

public class Profile extends BaseActivity {

    private TextView tvName, tvEmail, tvPhone, tvRole;
    private ImageView imgAvatar;
    private Button btnEditProfile, btnLogout, btnEditBusiness, btnResetData, btnDeleteAccount;

    private FirebaseAuth fAuth;
    private FirebaseFirestore fStore;
    private FirebaseStorage fStorage;

    private String currentUserId;
    private ListenerRegistration profileListener;

    private ActivityResultLauncher<Intent> avatarPickerLauncher;
    private Uri newAvatarUri;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profil);

        tvName = findViewById(R.id.ProfilNameInfo);
        tvEmail = findViewById(R.id.ProfilEmailInfo);
        tvPhone = findViewById(R.id.ProfilPhoneInfo);
        tvRole = findViewById(R.id.ProfilRoleInfo);
        imgAvatar = findViewById(R.id.imgAvatar);

        btnEditProfile = findViewById(R.id.EditProfil);
        btnEditBusiness = findViewById(R.id.btnEditBusiness);
        btnResetData = findViewById(R.id.btnResetData);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);
        btnLogout = findViewById(R.id.btnLogout);

        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        fStorage = FirebaseStorage.getInstance();

        if (fAuth.getCurrentUser() != null) {
            currentUserId = fAuth.getCurrentUser().getUid();
            loadUserProfile();
        } else {
            navigateToLogin();
            return;
        }

        setupAvatarPicker();

        btnEditProfile.setOnClickListener(v -> {
            Intent intent = new Intent(Profile.this, EditProfil.class);
            // Pass the current data to the Edit Profile screen so it pre-fills the boxes
            intent.putExtra("name", tvName.getText().toString());
            intent.putExtra("email", tvEmail.getText().toString().replace("Email: ", ""));
            intent.putExtra("phone", tvPhone.getText().toString().replace("Phone: ", ""));
            startActivity(intent);
        });

        imgAvatar.setOnClickListener(v -> chooseNewAvatar());
        btnLogout.setOnClickListener(v -> performLogout());

        btnEditBusiness.setOnClickListener(v -> {
            startActivity(new Intent(Profile.this, BusinessSetupActivity.class));
        });

        btnResetData.setOnClickListener(v -> showResetDataDialog());
        btnDeleteAccount.setOnClickListener(v -> showDeleteAccountDialog());
    }

    private void loadUserProfile() {
        DocumentReference docRef = fStore.collection("users").document(currentUserId);

        profileListener = docRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.e("Profile", "Listen failed.", e);
                    return;
                }

                if (documentSnapshot != null && documentSnapshot.exists()) {
                    String name = documentSnapshot.getString("name");
                    String email = documentSnapshot.getString("email");
                    String phone = documentSnapshot.getString("phone");
                    String role = documentSnapshot.getString("role");
                    String photoUrl = documentSnapshot.getString("photoUrl");

                    tvName.setText(name != null ? name : "User Name");
                    tvEmail.setText("Email: " + (email != null ? email : "Not Set"));
                    tvPhone.setText("Phone: " + (phone != null ? phone : "Not Set"));
                    tvRole.setText("Role: " + (role != null ? role : "Unknown"));

                    if ("Admin".equalsIgnoreCase(role)) {
                        btnEditBusiness.setVisibility(View.VISIBLE);
                        btnResetData.setVisibility(View.VISIBLE);
                    } else {
                        btnEditBusiness.setVisibility(View.GONE);
                        btnResetData.setVisibility(View.GONE);
                    }

                    loadAvatar(photoUrl);
                }
            }
        });
    }

    private void showResetDataDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Reset Data")
                .setMessage("Delete ALL inventory, sales, and transactions? This is permanent.")
                .setPositiveButton("Reset", (dialog, which) -> performDataReset(false))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Permanently delete your account and data?")
                .setPositiveButton("Delete", (dialog, which) -> performDataReset(true))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performDataReset(boolean deleteAccountAfter) {
        FirebaseUser user = fAuth.getCurrentUser();
        if (user == null) return;
        String targetUid = user.getUid();

        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage(deleteAccountAfter ? "Deleting Account..." : "Wiping Data...");
        pd.setCancelable(false);
        pd.show();

        DatabaseReference rtdb = FirebaseDatabase.getInstance().getReference();
        String[] nodes = {"Products", "Categories", "PurchaseOrders", "StockAdjustments", "History"};

        for (String node : nodes) {
            rtdb.child(node).orderByChild("ownerAdminId").equalTo(targetUid)
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            for (DataSnapshot ds : snapshot.getChildren()) ds.getRef().removeValue();
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                    });
        }

        if (deleteAccountAfter) {
            fStore.collection("users").document(targetUid).delete().addOnCompleteListener(task -> {
                user.delete().addOnCompleteListener(authTask -> {
                    pd.dismiss();
                    navigateToLogin();
                });
            });
        } else {
            pd.dismiss();
            Toast.makeText(this, "Data wiped.", Toast.LENGTH_SHORT).show();
        }
    }

    private void performLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure?")
                .setPositiveButton("Logout", (dialog, which) -> {
                    fAuth.signOut();
                    navigateToLogin();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void setupAvatarPicker() {
        avatarPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        newAvatarUri = result.getData().getData();
                        if (newAvatarUri != null) {
                            imgAvatar.setImageURI(newAvatarUri);
                            uploadAvatarToFirebase();
                        }
                    }
                }
        );
    }

    private void chooseNewAvatar() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        avatarPickerLauncher.launch(Intent.createChooser(intent, "Select Avatar"));
    }

    private void uploadAvatarToFirebase() {
        if (newAvatarUri == null) return;
        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Uploading...");
        pd.show();

        StorageReference ref = fStorage.getReference().child("avatars/" + currentUserId + ".jpg");
        ref.putFile(newAvatarUri).addOnSuccessListener(task -> ref.getDownloadUrl().addOnSuccessListener(uri -> {
            fStore.collection("users").document(currentUserId).update("photoUrl", uri.toString());
            pd.dismiss();
        }));
    }

    private void navigateToLogin() {
        Intent intent = new Intent(this, SignInActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loadAvatar(String url) {
        if (url != null && !url.isEmpty()) {
            Glide.with(this).load(url).diskCacheStrategy(DiskCacheStrategy.ALL).circleCrop().into(imgAvatar);
        } else {
            imgAvatar.setImageResource(R.drawable.avatarprofil);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (profileListener != null) profileListener.remove();
    }
}