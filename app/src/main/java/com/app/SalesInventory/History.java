package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

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
    ArrayAdapter<Product> arrayAdapter;

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

        UserId = fAuth.getCurrentUser().getUid();
        Query query = databaseReference.orderByChild("userId").equalTo(UserId);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for(DataSnapshot ds : dataSnapshot.getChildren()){

                    //
                    String name=ds.getValue(Product.class).getName();
                    String Code=ds.getValue(Product.class).getCode();
                    String amount=ds.getValue(Product.class).getAmount();
                    String sellPrice=ds.getValue(Product.class).getSellPrice();
                    String Date = ds.getValue(Product.class).getDate();

                    Product product =new Product(name,Code,sellPrice,amount,Date);

                    //

                    arrayList.add(product);
                    historyAdapter.notifyDataSetChanged();
                }
            }


            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        historyAdapter= new HistoryAdapter(arrayList,this);
        listView.setAdapter(historyAdapter);


    }

}

