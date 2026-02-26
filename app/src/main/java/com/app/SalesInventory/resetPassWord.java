package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;

public class resetPassWord extends AppCompatActivity {

    EditText emailreset;
    Button Resetbtn;
    FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reset_pass_word);
        emailreset =(EditText) findViewById(R.id.resetemail);
        Resetbtn = (Button)findViewById(R.id.resetpassbtn);
        firebaseAuth = FirebaseAuth.getInstance();

        Resetbtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                String mail = emailreset.getText().toString().trim();
                if (TextUtils.isEmpty(mail)){
                    emailreset.setError("Your Email Please");
                    return;
                }

                firebaseAuth.sendPasswordResetEmail(mail).addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void aVoid) {
                        Toast.makeText(resetPassWord.this, "Reset Link Sent To Your Email", Toast.LENGTH_SHORT).show();
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(resetPassWord.this, "Reset Link Is Not Sent", Toast.LENGTH_SHORT).show();
                    }
                });

            }

        });
    }



}
