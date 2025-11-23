package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class EditProduct extends AppCompatActivity {

    EditText nameEt, priceEt, categoryEt, stockEt;
    Button btnUpdate;
    DatabaseReference productRef;

    // Variables to hold the keys/identifiers
    String targetCode;
    String originalName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_product);

        // Init Views
        nameEt = findViewById(R.id.EditProdName);
        priceEt = findViewById(R.id.EditProdPrice);
        categoryEt = findViewById(R.id.EditProdCategory);
        stockEt = findViewById(R.id.EditProdStock);
        btnUpdate = findViewById(R.id.BtnUpdateProduct);

        productRef = FirebaseDatabase.getInstance().getReference("Product");

        // 1. Get Data passed from Stock Activity
        if(getIntent().getExtras() != null) {
            targetCode = getIntent().getStringExtra("code"); // We use Code to find the item
            originalName = getIntent().getStringExtra("name"); // Fallback

            // Set fields
            nameEt.setText(getIntent().getStringExtra("name"));
            priceEt.setText(getIntent().getStringExtra("price"));
            categoryEt.setText(getIntent().getStringExtra("category"));
            stockEt.setText(getIntent().getStringExtra("amount"));
        }

        // 2. Update Button Logic
        btnUpdate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                updateProductInFirebase();
            }
        });
    }

    private void updateProductInFirebase() {
        String newName = nameEt.getText().toString().trim();
        String newPrice = priceEt.getText().toString().trim();
        String newCat = categoryEt.getText().toString().trim();
        String newStock = stockEt.getText().toString().trim();

        // QUERY Firebase to find the product with this CODE
        productRef.orderByChild("code").equalTo(targetCode)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                // Create update map
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("name", newName);
                                updates.put("sellPrice", newPrice);
                                updates.put("category", newCat);
                                updates.put("amount", newStock);

                                // Update the specific node
                                ds.getRef().updateChildren(updates);

                                Toast.makeText(EditProduct.this, "Product Updated Successfully!", Toast.LENGTH_SHORT).show();
                                finish(); // Go back to Stock list
                            }
                        } else {
                            Toast.makeText(EditProduct.this, "Error: Product not found.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) { }
                });
    }
}