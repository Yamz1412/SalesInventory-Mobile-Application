package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.firebase.auth.FirebaseAuth;
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

import javax.annotation.Nullable;

public class Dashboard extends AppCompatActivity {

    Button BtnProfil, BtnSelles, BtnPruchse, BtnStock, btnDelete, btnPurchase;
    TextView Welcome, criticalCountTV, totalCountTV;;
    FirebaseAuth fAuth;
    FirebaseFirestore fStore;
    String userID;
    DatabaseReference productRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        BtnProfil = findViewById(R.id.ProfilBtn);
        BtnStock = findViewById(R.id.StockBtn);
        BtnSelles = findViewById(R.id.SellesBtn);
        Welcome = findViewById(R.id.welcomeT);
        criticalCountTV = findViewById(R.id.CriticalCountTV);
        totalCountTV = findViewById(R.id.TotalCountTV);
        btnDelete = findViewById(R.id.DeleteBtn);
        btnPurchase = findViewById(R.id.PruchaseBtn);

        productRef = FirebaseDatabase.getInstance().getReference("Product");

        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        userID = fAuth.getCurrentUser().getUid();

        DocumentReference docRef = fStore.collection("users").document(userID);
        docRef.addSnapshotListener(new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot doc, @Nullable FirebaseFirestoreException e) {
                if (doc != null && doc.exists()) {
                    Welcome.setText(doc.getString("Name"));

                    String role = doc.getString("Role");
                    if ("Employee".equals(role)) {
                        btnDelete.setVisibility(View.GONE);
                        btnPurchase.setVisibility(View.GONE);
                    } else {
                        btnDelete.setVisibility(View.VISIBLE);
                        btnPurchase.setVisibility(View.VISIBLE);
                    }
                }
            }
        });
            loadDashboardStats();
    }

    private void loadDashboardStats() {
        productRef.orderByChild("userId").equalTo(userID).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int totalItems = 0;
                int criticalItems = 0;

                for (DataSnapshot ds : snapshot.getChildren()) {
                    Product p = ds.getValue(Product.class);
                    if (p != null) {
                        totalItems++;

                        try {
                            int currentAmount = Integer.parseInt(p.getAmount());
                            int minStock = (p.getMinStockLevel() != null && !p.getMinStockLevel().isEmpty())
                                    ? Integer.parseInt(p.getMinStockLevel()) : 5;

                            if (currentAmount <= minStock) {
                                criticalItems++;
                            }
                        } catch (NumberFormatException e) {
                            // Handle error
                        }
                    }
                }
                // Update UI
                totalCountTV.setText(String.valueOf(totalItems));
                criticalCountTV.setText(String.valueOf(criticalItems));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) { }
        });
    }

    public void LogOut(View view) {
        FirebaseAuth.getInstance().signOut();
        startActivity(new Intent(getApplicationContext(), FirstActivity.class));
        finish();
    }

    public void OpenAdd(View view) {
        startActivity(new Intent(this, AddProduct.class));
    }

    public void OpenProfil(View view) {
        startActivity(new Intent(this, Profile.class));
    }

    public void OpenStock(View view) {
        startActivity(new Intent(getApplicationContext(), Inventory.class));
    }

    public void OpenHistory(View view) {startActivity(new Intent(getApplicationContext(), Reports.class));    }

    public void OpenSell(View view) {
        startActivity(new Intent(getApplicationContext(), SellList.class));
    }

    public void OpenDelete(View view) {
        startActivity(new Intent(getApplicationContext(), DeleteProduct.class));
    }
}
