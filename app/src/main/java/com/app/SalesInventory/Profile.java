package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;

import javax.annotation.Nullable;

public class Profile extends BaseActivity {

    TextView ProfilName, ProfilEmail, ProfilPhone, ProfilNameInfo;
    Button button, btnLogout;
    ImageView imgAvatar;
    String userId;
    FirebaseAuth fAuth;
    FirebaseFirestore fStore;
    ListenerRegistration profileListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profil);

        ProfilName = findViewById(R.id.ProfilNameT);
        ProfilEmail = findViewById(R.id.ProfilEmailT);
        ProfilPhone = findViewById(R.id.ProfilPhoneT);
        ProfilNameInfo = findViewById(R.id.ProfilNameInfo);
        button = findViewById(R.id.EditProfil);
        btnLogout = findViewById(R.id.btnLogout);
        imgAvatar = findViewById(R.id.imgAvatar);

        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();

        if (fAuth.getCurrentUser() == null) {
            redirectToLogin();
            return;
        }

        userId = fAuth.getCurrentUser().getUid();

        // Listen to real-time updates for the logged-in user
        DocumentReference documentReference = fStore.collection("users").document(userId);
        profileListener = documentReference.addSnapshotListener(this, new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                if (e != null) {
                    Log.e("Profile", "Error loading profile", e);
                    return;
                }
                if (documentSnapshot != null && documentSnapshot.exists()) {
                    String name = documentSnapshot.getString("fName");
                    // Also check "name" in case of legacy data
                    if (name == null) name = documentSnapshot.getString("name");

                    String email = documentSnapshot.getString("email");
                    String phone = documentSnapshot.getString("phone");
                    String photoUrl = documentSnapshot.getString("photoUrl");

                    // Fallback for phone capitalization inconsistency
                    if (phone == null) phone = documentSnapshot.getString("Phone");

                    ProfilName.setText(name != null ? name : "No Name");
                    ProfilNameInfo.setText(name != null ? name : "No Name");
                    ProfilEmail.setText(email != null ? email : "");
                    ProfilPhone.setText(phone != null ? phone : "");

                    loadAvatar(photoUrl);
                } else {
                    Log.d("Profile", "Document does not exist");
                }
            }
        });

        button.setOnClickListener(v -> {
            Intent i = new Intent(v.getContext(), EditProfil.class);
            i.putExtra("name", ProfilName.getText().toString());
            i.putExtra("email", ProfilEmail.getText().toString());
            i.putExtra("phone", ProfilPhone.getText().toString());
            startActivity(i);
        });

        btnLogout.setOnClickListener(v -> {
            if (AuthManager.getInstance() != null) {
                AuthManager.getInstance().signOutAndCleanup(() -> {
                    redirectToLogin();
                });
            } else {
                FirebaseAuth.getInstance().signOut();
                redirectToLogin();
            }
        });
    }

    private void redirectToLogin() {
        Intent intent = new Intent(Profile.this, SignInActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loadAvatar(String photoUrlFromProfile) {
        String urlToLoad = null;

        // 1. Try Firestore URL first (Most accurate)
        if (photoUrlFromProfile != null && !photoUrlFromProfile.isEmpty()) {
            urlToLoad = photoUrlFromProfile;
        }
        // 2. Fallback to Firebase Auth Provider URL (e.g. Google Photo)
        else {
            FirebaseUser user = fAuth.getCurrentUser();
            if (user != null && user.getPhotoUrl() != null) {
                urlToLoad = user.getPhotoUrl().toString();
            }
        }

        if (urlToLoad != null && !urlToLoad.isEmpty()) {
            Glide.with(this)
                    .load(urlToLoad)
                    .diskCacheStrategy(DiskCacheStrategy.ALL) // Cache image to save data
                    .placeholder(R.drawable.avatarprofil)
                    .error(R.drawable.avatarprofil)
                    .circleCrop()
                    .into(imgAvatar);
        } else {
            imgAvatar.setImageResource(R.drawable.avatarprofil);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (profileListener != null) {
            profileListener.remove();
        }
    }
}