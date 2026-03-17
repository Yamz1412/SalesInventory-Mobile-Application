package com.app.SalesInventory;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
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

        if (btnResetData != null) {
            btnResetData.setOnClickListener(v -> wipeAllBusinessData());
        }
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

                    if ("Admin".equalsIgnoreCase(role) || "Owner".equalsIgnoreCase(role)) {
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

    // =====================================================================
    // TRUE FACTORY RESET LOGIC (TARGETS FIRESTORE + NOTIFICATIONS)
    // =====================================================================
    private void wipeAllBusinessData() {
        new AlertDialog.Builder(this)
                .setTitle("⚠️ FULL FACTORY RESET")
                .setMessage("This will permanently scrub EVERYTHING (Products, Sales, POs, Notifications, etc.) from the cloud database and your device. \n\nAre you absolutely sure you want to start from zero?")
                .setPositiveButton("SCRUB EVERYTHING", (dialog, which) -> {

                    String wipeId = FirestoreManager.getInstance().getBusinessOwnerId();
                    if (wipeId == null || wipeId.isEmpty()) wipeId = currentUserId;
                    if (wipeId == null) return;
                    final String finalWipeId = wipeId;

                    ProgressDialog pd = new ProgressDialog(this);
                    pd.setMessage("Deleting Firestore Collections & Device Memory... Please wait.");
                    pd.setCancelable(false);
                    pd.show();

                    // 1. FORCE CLEAR ANDROID SYSTEM NOTIFICATIONS IMMEDIATELY
                    NotificationHelper.clearAllNotifications(Profile.this);

                    // 2. WIPE FIRESTORE SUBCOLLECTIONS (The Core Data!)
                    String[] firestoreCollections = {
                            "products", "sales", "adjustments", "categories",
                            "suppliers", "purchaseOrders", "deliveries", "alerts"
                    };

                    for (String collection : firestoreCollections) {
                        // Clear under Business Owner ID
                        fStore.collection(collection).document(finalWipeId).collection("items").get()
                                .addOnCompleteListener(task -> {
                                    if (task.isSuccessful() && task.getResult() != null) {
                                        for (DocumentSnapshot doc : task.getResult()) {
                                            doc.getReference().delete();
                                        }
                                    }
                                });

                        // Clear under Specific Admin ID just in case data crossed over
                        if (!finalWipeId.equals(currentUserId)) {
                            fStore.collection(collection).document(currentUserId).collection("items").get()
                                    .addOnCompleteListener(task -> {
                                        if (task.isSuccessful() && task.getResult() != null) {
                                            for (DocumentSnapshot doc : task.getResult()) {
                                                doc.getReference().delete();
                                            }
                                        }
                                    });
                        }
                    }

                    // 3. Wipe Cash Transactions and Reset Wallet
                    try {
                        fStore.collection("users").document(finalWipeId)
                                .collection("cash_transactions").get().addOnCompleteListener(task -> {
                                    if (task.isSuccessful() && task.getResult() != null) {
                                        for (DocumentSnapshot doc : task.getResult()) {
                                            doc.getReference().delete();
                                        }
                                    }
                                });

                        Map<String, Object> resetWallet = new HashMap<>();
                        resetWallet.put("balance", 0.0);
                        fStore.collection("users").document(finalWipeId)
                                .collection("wallets").document("CASH").set(resetWallet);

                    } catch (Exception e) {
                        Log.e("Profile", "Firestore user data wipe error safely ignored.");
                    }

                    // 4. WIPE REALTIME DATABASE (Just in case legacy data is there)
                    DatabaseReference db = FirebaseDatabase.getInstance().getReference();
                    db.child("OperatingExpenses").child(finalWipeId).removeValue();
                    db.child("Cart").child(finalWipeId).removeValue();
                    String[] legacyNodes = {"Products", "Sales", "PurchaseOrders", "Returns", "StockAdjustments", "Alerts"};
                    for (String node : legacyNodes) {
                        deleteRTDBNodes(db.child(node), "ownerAdminId", finalWipeId);
                        deleteRTDBNodes(db.child(node), "adminId", finalWipeId);
                        deleteRTDBNodes(db.child(node), "ownerAdminId", currentUserId);
                    }

                    // 5. SEND THE KILL SWITCH SIGNAL TO ALL STAFF
                    Map<String, Object> signalData = new HashMap<>();
                    signalData.put("lastResetTime", com.google.firebase.firestore.FieldValue.serverTimestamp());
                    fStore.collection("users").document(finalWipeId).collection("system").document("reset_signal").set(signalData);

                    // 6. Force Local Room Database clear on BACKGROUND THREAD
                    new Thread(() -> {
                        try {
                            if (SalesInventoryApplication.getProductRepository() != null) {
                                SalesInventoryApplication.getProductRepository().clearLocalData();
                            }
                            if (SalesInventoryApplication.getSalesRepository() != null) {
                                SalesInventoryApplication.getSalesRepository().clearData();
                            }
                            try {
                                AlertRepository.getInstance(getApplication()).clearAllAlerts();
                            } catch (Exception ignored) {}
                        } catch (Exception e) {
                            Log.e("Profile", "Local wipe error: " + e.getMessage());
                        }

                        // Return to Main Thread and Restart UI
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            pd.dismiss();
                            Toast.makeText(Profile.this, "✅ SYSTEM FACTORY RESET COMPLETE!", Toast.LENGTH_LONG).show();

                            Intent intent = new Intent(Profile.this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(intent);
                            finish();
                        }, 3000); // 3-second delay to let Firestore batches finish deleting
                    }).start();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteRTDBNodes(DatabaseReference ref, String orderBy, String ownerId) {
        ref.orderByChild(orderBy).equalTo(ownerId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) ds.getRef().removeValue();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // =====================================================================
    // ACCOUNT DELETION LOGIC (NOW TARGETS FIRESTORE & ALERTS)
    // =====================================================================
    private void showDeleteAccountDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Account")
                .setMessage("Permanently delete your account, business profile, and all associated data?")
                .setPositiveButton("Delete", (dialog, which) -> performAccountDeletion())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performAccountDeletion() {
        FirebaseUser user = fAuth.getCurrentUser();
        if (user == null) return;

        String targetUid = FirestoreManager.getInstance().getBusinessOwnerId();
        if (targetUid == null || targetUid.isEmpty()) targetUid = user.getUid();

        ProgressDialog pd = new ProgressDialog(this);
        pd.setMessage("Deleting Account & Data...");
        pd.setCancelable(false);
        pd.show();

        NotificationHelper.clearAllNotifications(Profile.this);

        // Wipe Firestore Subcollections First
        String[] firestoreCollections = {
                "products", "sales", "adjustments", "categories",
                "suppliers", "purchaseOrders", "deliveries", "alerts"
        };
        for (String collection : firestoreCollections) {
            fStore.collection(collection).document(targetUid).collection("items").get()
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful() && task.getResult() != null) {
                            for (DocumentSnapshot doc : task.getResult()) doc.getReference().delete();
                        }
                    });
        }

        // Delete Profile Picture from Storage
        StorageReference avatarRef = fStorage.getReference().child("avatars/" + user.getUid() + ".jpg");
        avatarRef.delete().addOnCompleteListener(task -> {
            // Delete Firestore user document
            fStore.collection("users").document(user.getUid()).delete().addOnCompleteListener(task2 -> {
                // Finally, delete Auth Account
                user.delete().addOnCompleteListener(authTask -> {
                    pd.dismiss();
                    if (authTask.isSuccessful()) {
                        Toast.makeText(Profile.this, "Account Permanently Deleted.", Toast.LENGTH_LONG).show();
                        navigateToLogin();
                    } else {
                        Toast.makeText(Profile.this, "Data wiped. Please log in again to finalize account deletion.", Toast.LENGTH_LONG).show();
                        fAuth.signOut();
                        navigateToLogin();
                    }
                });
            });
        });
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