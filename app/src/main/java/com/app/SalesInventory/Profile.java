package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

import javax.annotation.Nullable;

public class Profile extends AppCompatActivity {

    TextView ProfilName, ProfilEmail, ProfilPhone, ProfilNameInfo;
    Button button;
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

        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        userId = fAuth.getCurrentUser().getUid();

        final DocumentReference documentReference = fStore.collection("users").document(userId);
        documentReference.addSnapshotListener(this, new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot documentSnapshot, @Nullable FirebaseFirestoreException e) {
                ProfilEmail.setText(documentSnapshot.getString("Email"));
                ProfilName.setText(documentSnapshot.getString("Name"));
                ProfilPhone.setText(documentSnapshot.getString("Phone"));
                ProfilNameInfo.setText(documentSnapshot.getString("Name"));

            }
        });
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(v.getContext(),EditProfil.class);
                i.putExtra("name",ProfilName.getText().toString());
                i.putExtra("email",ProfilEmail.getText().toString());
                i.putExtra("phone",ProfilPhone.getText().toString());
                startActivity(i);
            }
        });

    }


}
