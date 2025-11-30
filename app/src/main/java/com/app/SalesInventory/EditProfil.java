package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class EditProfil extends BaseActivity  {

    public static final String TAG = "Tag";
    EditText ProfilName ,  ProfilEmail ,ProfilPhone;
    TextView NameInfo;
    FirebaseAuth fAuth;
    FirebaseFirestore fStore;
    Button savebtn;
    FirebaseUser user;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profil);
        ProfilName = findViewById(R.id.ProfilNameE);
        ProfilEmail = findViewById(R.id.ProfilEmailTE);
        ProfilPhone = findViewById(R.id.ProfilPhoneTE);
        NameInfo = findViewById(R.id.ProfilNameInfo);
        savebtn = findViewById(R.id.SaveProfile);
        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        user = fAuth.getCurrentUser();

        Intent data = getIntent();

        String name = data.getStringExtra("name");
        String email = data.getStringExtra("email");
        String phone = data.getStringExtra("phone");

        ProfilName.setText(name);
        ProfilPhone.setText(phone);
        ProfilEmail.setText(email);
        NameInfo.setText(name
        );

        Log.i(TAG,"done"+" "+name+" "+email+" "+phone);

        savebtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ProfilName.getText().toString().isEmpty() || ProfilEmail.getText().toString().isEmpty() || ProfilPhone.getText().toString().isEmpty()){
                    Toast.makeText(EditProfil.this, "You Need To Put All The Informations ", Toast.LENGTH_SHORT).show();
                    return;
                }

                String email = ProfilEmail.getText().toString();
                DocumentReference docref = fStore.collection("users").document(user.getUid());
                Map<String,Object> edited = new HashMap<>();
                edited.put("Email",email);
                edited.put("Name",ProfilName.getText().toString());
                edited.put("Phone",ProfilPhone.getText().toString());
                docref.update(edited).addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        startActivity(new Intent(getApplicationContext(), Profile.class));
                        finish();
                    }
                });
                user.updateEmail(email).addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Toast.makeText(EditProfil.this, "Profil Updated", Toast.LENGTH_SHORT).show();
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(EditProfil.this, e.getMessage() , Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
    }

