package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.ListView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class History extends AppCompatActivity {

    DatabaseReference databaseReference;
    ListView listView;
    ArrayList<Product> arrayList = new ArrayList<>();
    private HistoryAdapter historyAdapter;

    FirebaseAuth fAuth;
    String UserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        databaseReference = FirebaseDatabase.getInstance().getReference("History");
        listView = findViewById(R.id.ProductList2);

        fAuth = FirebaseAuth.getInstance();

        if (fAuth.getCurrentUser() == null) {
            return;
        }

        UserId = fAuth.getCurrentUser().getUid();

        historyAdapter = new HistoryAdapter(this, arrayList);
        listView.setAdapter(historyAdapter);

        Query query = databaseReference.orderByChild("userId").equalTo(UserId);
        query.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                arrayList.clear();
                for (DataSnapshot ds : dataSnapshot.getChildren()) {
                    try {
                        Product product = ds.getValue(Product.class);
                        if (product != null) {
                            arrayList.add(product);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                historyAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Toast.makeText(History.this, "Error: " + databaseError.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}