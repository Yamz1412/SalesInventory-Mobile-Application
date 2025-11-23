package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
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

public class Inventory extends AppCompatActivity {

    DatabaseReference databaseReference;
    ListView listView;
    ArrayList<Product> arrayList = new ArrayList<>();
   ArrayAdapter<Product> arrayAdapter;

   private ProductAdapter adapterPro;


    FirebaseAuth fAuth;
    String UserId;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory);
        databaseReference = FirebaseDatabase.getInstance().getReference("Product");
        listView = findViewById(R.id.ProductList);

        fAuth = FirebaseAuth.getInstance();
        adapterPro=new ProductAdapter(arrayList,this);
        listView.setAdapter(adapterPro);
        listView.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                Product selectedProduct = arrayList.get(position);

                android.content.Intent intent = new android.content.Intent(Inventory.this, EditProduct.class);
                intent.putExtra("code", selectedProduct.getCode());
                intent.putExtra("name", selectedProduct.getName());
                intent.putExtra("code", selectedProduct.getCode());
                intent.putExtra("price", selectedProduct.getSellPrice());
                intent.putExtra("amount", selectedProduct.getAmount());
                intent.putExtra("category", selectedProduct.getCategory());
                startActivity(intent);
            }
        });

        UserId = fAuth.getCurrentUser().getUid();
        Query query = databaseReference.orderByChild("userId").equalTo(UserId);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {

                for(DataSnapshot ds : dataSnapshot.getChildren()){
                    String amount;
                    String AmountTest = ds.getValue(Product.class).getAmount();
                    int myNum = Integer. parseInt(AmountTest);
                    if (myNum == 0){
                         amount = "Out Of Stock";}else {
                         amount = ds.getValue(Product.class).getAmount();
                    }
                    String name = ds.getValue(Product.class).getName();
                    String Lot = ds.getValue(Product.class).getLot();
                    String sellPrice = ds.getValue(Product.class).getSellPrice();
                    String category = ds.getValue(Product.class).getCategory();

                    Product product = new Product(name,Lot,sellPrice,amount, category);

                    arrayList.add(product);
                    adapterPro.notifyDataSetChanged();
                }
            }


            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {

            }
        });



    }
}
