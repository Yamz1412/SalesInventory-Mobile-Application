package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;

public class SellList extends AppCompatActivity {

    DatabaseReference databaseReference;
    ListView listView;
    ArrayList<Product> arrayList = new ArrayList<>();
    Button btnSell;

    private SellAdapter sellAdapter;


    FirebaseAuth fAuth;
    String UserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sell_list);

        databaseReference = FirebaseDatabase.getInstance().getReference("Product");
        listView = findViewById(R.id.SellListD);
        btnSell = findViewById(R.id.sellBtnD);
//        module = ((Module)getApplicationContext());

        fAuth = FirebaseAuth.getInstance();

        UserId = fAuth.getCurrentUser().getUid();
        Query query = databaseReference.orderByChild("userId").equalTo(UserId);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for (DataSnapshot ds : dataSnapshot.getChildren()) {

                    String amount;
                    String AmountTest = ds.getValue(Product.class).getAmount();
                    int myNum = Integer.parseInt(AmountTest);
                    if (myNum == 0) {
                        amount = "Out Of Stock";
                    } else {
                        amount = ds.getValue(Product.class).getAmount();

                    }

                    String name = ds.getValue(Product.class).getName();
                    String Code = ds.getValue(Product.class).getCode();
                    String sellPrice = ds.getValue(Product.class).getSellPrice();
                    String lot = ds.getValue(Product.class).getLot();

                    Product productt = new Product(name, Code, sellPrice, amount, lot);


                    arrayList.add(productt);
                    sellAdapter.notifyDataSetChanged();


                }
            }


            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });

        sellAdapter = new SellAdapter(arrayList, this);
        listView.setAdapter(sellAdapter);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                Intent intent = new Intent(getApplicationContext(), sellProduct.class);
                Product p = arrayList.get(position);

                intent.putExtra("name1", p.getName());
                intent.putExtra("code1", p.getCode());
                intent.putExtra("amount1", p.getAmount());
                intent.putExtra("price1", p.getSellPrice());

                startActivity(intent);


            }
        });
    }
}







