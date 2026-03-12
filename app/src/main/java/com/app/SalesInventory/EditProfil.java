package com.app.SalesInventory;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class EditProfil extends BaseActivity {

    public static final String TAG = "EditProfil";
    EditText ProfilName, ProfilUsername, ProfilEmail, ProfilPhone;
    Button savebtn, btnChangePhoto;
    ImageView imgAvatarEdit;

    FirebaseAuth fAuth;
    FirebaseFirestore fStore;
    FirebaseUser user;
    StorageReference storageRef;
    Uri selectedImageUri;
    String currentPhotoUrl;
    String currentUsernameFetched = "";

    ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profil);

        Intent data = getIntent();
        String fullName = data.getStringExtra("name");
        String email = data.getStringExtra("email");
        String phone = data.getStringExtra("phone");

        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        user = fAuth.getCurrentUser();
        storageRef = FirebaseStorage.getInstance().getReference();

        ProfilName = findViewById(R.id.ProfilNameE);
        ProfilUsername = findViewById(R.id.ProfilUsernameTE); // Bind Username Field
        ProfilEmail = findViewById(R.id.ProfilEmailTE);
        ProfilPhone = findViewById(R.id.ProfilPhoneTE);
        savebtn = findViewById(R.id.SaveProfile);
        btnChangePhoto = findViewById(R.id.btnChangePhoto);
        imgAvatarEdit = findViewById(R.id.imgAvatarEdit);

        if (fullName != null) ProfilName.setText(fullName);
        if (email != null) ProfilEmail.setText(email);
        if (phone != null) ProfilPhone.setText(phone);

        loadCurrentUserDetails();

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        Glide.with(this).load(uri).circleCrop().into(imgAvatarEdit);
                    }
                }
        );

        imgAvatarEdit.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        if (btnChangePhoto != null) {
            btnChangePhoto.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        }

        savebtn.setOnClickListener(v -> handleSave());

        Button btnChangePass = findViewById(R.id.btnChangePassword);
        if (btnChangePass != null) {
            btnChangePass.setOnClickListener(v -> showChangePasswordDialog());
        }
    }

    private void loadCurrentUserDetails() {
        if (user == null) return;
        fStore.collection("users").document(user.getUid()).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                currentUsernameFetched = doc.getString("username");
                if (currentUsernameFetched != null) ProfilUsername.setText(currentUsernameFetched);

                currentPhotoUrl = doc.getString("photoUrl");
                if (currentPhotoUrl != null && !currentPhotoUrl.isEmpty()) {
                    Glide.with(this).load(currentPhotoUrl).placeholder(R.drawable.avatarprofil).circleCrop().into(imgAvatarEdit);
                } else if (user.getPhotoUrl() != null) {
                    Glide.with(this).load(user.getPhotoUrl()).circleCrop().into(imgAvatarEdit);
                }
            }
        });
    }

    private void handleSave() {
        String name = ProfilName.getText().toString().trim();
        String newUsername = ProfilUsername.getText().toString().trim().toLowerCase(); // Force lowercase for safety
        String mail = ProfilEmail.getText().toString().trim();
        String pNum = ProfilPhone.getText().toString().trim();

        if (name.isEmpty() || newUsername.isEmpty() || mail.isEmpty() || pNum.isEmpty()) {
            Toast.makeText(EditProfil.this, "All fields are required.", Toast.LENGTH_SHORT).show();
            return;
        }

        savebtn.setEnabled(false);
        savebtn.setText("Saving...");

        // 1. Check Username Uniqueness (if changed)
        if (!newUsername.equals(currentUsernameFetched)) {
            fStore.collection("users").whereEqualTo("username", newUsername).get().addOnSuccessListener(snap -> {
                if (!snap.isEmpty()) {
                    Toast.makeText(this, "Username is already taken", Toast.LENGTH_SHORT).show();
                    savebtn.setEnabled(true);
                    savebtn.setText("Save Changes");
                } else {
                    processEmailAndDatabase(name, newUsername, mail, pNum);
                }
            });
        } else {
            processEmailAndDatabase(name, newUsername, mail, pNum);
        }
    }

    private void processEmailAndDatabase(String name, String username, String mail, String phone) {
        // 2. If email changed, prompt Auth and Send Verification Link
        if (!mail.equals(user.getEmail())) {
            promptReAuthentication(password -> {
                updateEmailAndProfile(password, name, username, mail, phone);
            });
        } else {
            // 3. Normal save (no email change)
            if (selectedImageUri != null) {
                uploadImageAndSaveProfile(name, username, mail, phone);
            } else {
                saveProfileData(name, username, mail, phone, null);
            }
        }
    }

    // --- RE-AUTHENTICATION DIALOG ---
    private interface OnReAuthSuccess {
        void onSuccess(String password);
    }

    private void promptReAuthentication(OnReAuthSuccess callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Security Check");
        builder.setMessage("You are changing your email address. Please enter your current password to verify.");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        builder.setView(input);

        builder.setPositiveButton("Verify", (dialog, which) -> {
            String password = input.getText().toString();
            if (!password.isEmpty()) {
                callback.onSuccess(password);
            } else {
                Toast.makeText(this, "Password required", Toast.LENGTH_SHORT).show();
                savebtn.setEnabled(true);
                savebtn.setText("Save Changes");
            }
        });
        builder.setNegativeButton("Cancel", (dialog, which) -> {
            dialog.cancel();
            savebtn.setEnabled(true);
            savebtn.setText("Save Changes");
        });
        builder.show();
    }

    // ADDED "final" TO ALL PARAMETERS HERE:
    private void updateEmailAndProfile(final String password, final String name, final String username, final String newEmail, final String phone) {
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), password);
        user.reauthenticate(credential).addOnSuccessListener(aVoid -> {

            // Re-auth successful, update email
            user.updateEmail(newEmail).addOnSuccessListener(unused -> {

                // Send verification link to the new email
                user.sendEmailVerification().addOnSuccessListener(sendTask -> {
                    Toast.makeText(EditProfil.this, "Email updated! Verification link sent to " + newEmail, Toast.LENGTH_LONG).show();
                });

                if (selectedImageUri != null) {
                    uploadImageAndSaveProfile(name, username, newEmail, phone);
                } else {
                    saveProfileData(name, username, newEmail, phone, null);
                }
            }).addOnFailureListener(e -> {
                savebtn.setEnabled(true);
                savebtn.setText("Save Changes");
                Toast.makeText(EditProfil.this, "Failed to update email: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }).addOnFailureListener(e -> {
            savebtn.setEnabled(true);
            savebtn.setText("Save Changes");
            Toast.makeText(EditProfil.this, "Incorrect Password", Toast.LENGTH_SHORT).show();
        });
    }

    private void uploadImageAndSaveProfile(String name, String username, String email, String phone) {
        if (user == null) return;
        StorageReference fileRef = storageRef.child("profile_images/" + user.getUid() + ".jpg");

        fileRef.putFile(selectedImageUri)
                .addOnSuccessListener(taskSnapshot -> fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String downloadUrl = uri.toString();
                    saveProfileData(name, username, email, phone, downloadUrl);
                }))
                .addOnFailureListener(e -> {
                    savebtn.setEnabled(true);
                    savebtn.setText("Save Changes");
                    Toast.makeText(EditProfil.this, "Image Upload Failed", Toast.LENGTH_SHORT).show();
                });
    }

    private void saveProfileData(String name, String username, String email, String phone, String photoUrl) {
        if (user == null) return;
        String uid = user.getUid();
        DocumentReference docRef = fStore.collection("users").document(uid);

        Map<String, Object> edited = new HashMap<>();
        edited.put("fName", name);
        edited.put("name", name);
        edited.put("Name", name);
        edited.put("username", username); // Log new username
        edited.put("email", email);
        edited.put("phone", phone);
        edited.put("Phone", phone);

        if (photoUrl != null) {
            edited.put("photoUrl", photoUrl);
        }

        docRef.set(edited, SetOptions.merge()).addOnSuccessListener(aVoid -> {
            Toast.makeText(EditProfil.this, "Profile Updated", Toast.LENGTH_SHORT).show();
            finish();
        }).addOnFailureListener(e -> {
            savebtn.setEnabled(true);
            savebtn.setText("Save Changes");
            Toast.makeText(EditProfil.this, "Failed to update database", Toast.LENGTH_SHORT).show();
        });
    }

    private void showChangePasswordDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Change Password");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(30, 20, 30, 20);

        final EditText etCurrentPass = new EditText(this);
        etCurrentPass.setHint("Current Password");
        etCurrentPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etCurrentPass);

        final EditText etNewPass = new EditText(this);
        etNewPass.setHint("New Password");
        etNewPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etNewPass);

        builder.setView(layout);

        builder.setPositiveButton("Update", (dialog, which) -> {
            String current = etCurrentPass.getText().toString();
            String newPass = etNewPass.getText().toString();

            if(current.isEmpty() || newPass.isEmpty()) {
                Toast.makeText(this, "Fields cannot be empty", Toast.LENGTH_SHORT).show();
                return;
            }
            if(newPass.length() < 6) {
                Toast.makeText(this, "Password must be at least 6 chars", Toast.LENGTH_SHORT).show();
                return;
            }

            AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), current);
            user.reauthenticate(credential).addOnSuccessListener(aVoid -> {
                user.updatePassword(newPass).addOnSuccessListener(unused -> {
                    Toast.makeText(this, "Password Updated Successfully", Toast.LENGTH_SHORT).show();
                }).addOnFailureListener(e -> Toast.makeText(this, "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }).addOnFailureListener(e -> Toast.makeText(this, "Incorrect Current Password", Toast.LENGTH_SHORT).show());
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
}