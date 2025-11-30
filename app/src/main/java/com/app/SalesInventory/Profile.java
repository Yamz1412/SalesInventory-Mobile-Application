package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import javax.annotation.Nullable;

public class Profile extends BaseActivity  {

    TextView ProfilName, ProfilEmail, ProfilPhone, ProfilNameInfo;
    Button button, btnLogout;
    ImageView imgAvatar;
    String userId;
    FirebaseAuth fAuth;
    FirebaseFirestore fStore;

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
        FirebaseUser currentUser = fAuth.getCurrentUser();
        if (currentUser == null) {
            Intent intent = new Intent(Profile.this, SignInActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }
        userId = currentUser.getUid();

        DocumentReference documentReference = fStore.collection("users").document(userId);
        documentReference.addSnapshotListener(this, new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                if (documentSnapshot == null) return;
                String email = documentSnapshot.getString("email");
                if (email == null) email = documentSnapshot.getString("Email");
                String name = documentSnapshot.getString("name");
                if (name == null) name = documentSnapshot.getString("Name");
                String phone = documentSnapshot.getString("phone");
                if (phone == null) phone = documentSnapshot.getString("Phone");
                String photoUrl = documentSnapshot.getString("photoUrl");

                ProfilEmail.setText(email != null ? email : "");
                ProfilName.setText(name != null ? name : "");
                ProfilPhone.setText(phone != null ? phone : "");
                ProfilNameInfo.setText(name != null ? name : "");

                loadAvatar(photoUrl);
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
            FirebaseAuth.getInstance().signOut();
            Intent intent = new Intent(Profile.this, SignInActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });
    }

    private void loadAvatar(String photoUrlFromProfile) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        Uri authPhoto = user != null ? user.getPhotoUrl() : null;
        String url = photoUrlFromProfile != null && !photoUrlFromProfile.isEmpty()
                ? photoUrlFromProfile
                : authPhoto != null ? authPhoto.toString() : null;

        if (url != null && !url.isEmpty()) {
            Glide.with(this)
                    .load(url)
                    .placeholder(R.drawable.avatarprofil)
                    .error(R.drawable.avatarprofil)
                    .circleCrop()
                    .into(imgAvatar);
        } else {
            imgAvatar.setImageResource(R.drawable.avatarprofil);
        }
    }
}