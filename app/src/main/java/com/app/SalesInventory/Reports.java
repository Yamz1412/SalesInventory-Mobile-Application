package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

public class Reports extends AppCompatActivity {

    ListView reportList;
    TextView totalSalesTV;
    Button btnSales, btnInventory;

    DatabaseReference salesRef, productRef;
    ArrayList<Map<String, String>> listData = new ArrayList<>();
    ReportAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_reports);

        // Init Views
        reportList = findViewById(R.id.ReportsListView);
        totalSalesTV = findViewById(R.id.TotalSalesTV);
        btnSales = findViewById(R.id.BtnSalesReport);
        btnInventory = findViewById(R.id.BtnInventoryReport);

        // Init Firebase
        salesRef = FirebaseDatabase.getInstance().getReference("Sales");
        productRef = FirebaseDatabase.getInstance().getReference("Product");

        // Init Adapter
        adapter = new ReportAdapter();
        reportList.setAdapter(adapter);

        // Load Sales by default
        loadSalesData();

        // Button Listeners
        btnSales.setOnClickListener(v -> loadSalesData());
        btnInventory.setOnClickListener(v -> loadInventoryData());
    }

    private void loadSalesData() {
        listData.clear();
        salesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listData.clear();
                double totalRevenue = 0;

                for (DataSnapshot ds : snapshot.getChildren()) {
                    // Extract data from "Sales" node (created in sellProduct.java)
                    String name = String.valueOf(ds.child("productName").getValue());
                    String qty = String.valueOf(ds.child("quantity").getValue());
                    String date = String.valueOf(ds.child("date").getValue());
                    String total = String.valueOf(ds.child("totalPrice").getValue());

                    try {
                        totalRevenue += Double.parseDouble(total);
                    } catch (Exception e) {}

                    // Add to list
                    java.util.Map<String, String> item = new java.util.HashMap<>();
                    item.put("name", name);
                    item.put("qty", qty);
                    item.put("detail", date);
                    item.put("value", total);
                    listData.add(item);
                }

                Collections.reverse(listData);

                totalSalesTV.setText(String.format("%.2f", totalRevenue));
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadInventoryData() {
        listData.clear();
        productRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                listData.clear();

                for (DataSnapshot ds : snapshot.getChildren()) {
                    Product p = ds.getValue(Product.class);
                    if(p != null) {
                        java.util.Map<String, String> item = new java.util.HashMap<>();
                        item.put("name", p.getName());
                        item.put("qty", p.getAmount());
                        item.put("detail", p.getCategory());
                        item.put("value", p.getSellPrice());
                        listData.add(item);
                    }
                }
                adapter.notifyDataSetChanged();
                totalSalesTV.setText("-");
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    class ReportAdapter extends BaseAdapter {
        @Override
        public int getCount() { return listData.size(); }

        @Override
        public Object getItem(int position) { return listData.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.item_report_row, parent, false);
            }

            TextView name = convertView.findViewById(R.id.RowName);
            TextView date = convertView.findViewById(R.id.RowDate);
            TextView qty = convertView.findViewById(R.id.RowQty);
            TextView val = convertView.findViewById(R.id.RowTotal);

            java.util.Map<String, String> item = listData.get(position);

            name.setText(item.get("name"));
            date.setText(item.get("detail"));
            qty.setText(item.get("qty"));
            val.setText(item.get("value"));

            return convertView;
        }
    }
}