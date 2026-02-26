package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PurchaseOrderListActivity extends BaseActivity  {

    private RecyclerView recyclerView;
    private PurchaseOrderAdapter adapter;
    private List<PurchaseOrder> purchaseOrderList;
    private FloatingActionButton fabAddPO;
    private DatabaseReference poRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_order_list);

        recyclerView = findViewById(R.id.recyclerViewPurchaseOrders);
        fabAddPO = findViewById(R.id.fabAddPO);

        purchaseOrderList = new ArrayList<>();

        // Pass 'this::showCancelOptions' as the second listener for long clicks
        adapter = new PurchaseOrderAdapter(this, purchaseOrderList, this::viewPurchaseOrder, this::showCancelOptions);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");

        loadPurchaseOrders();

        fabAddPO.setOnClickListener(v -> {
            Intent intent = new Intent(PurchaseOrderListActivity.this, CreatePurchaseOrderActivity.class);
            startActivity(intent);
        });
    }

    private void loadPurchaseOrders() {
        poRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                purchaseOrderList.clear();
                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    PurchaseOrder po = dataSnapshot.getValue(PurchaseOrder.class);
                    if (po != null) {
                        purchaseOrderList.add(po);
                    }
                }
                Collections.reverse(purchaseOrderList);
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(PurchaseOrderListActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void viewPurchaseOrder(PurchaseOrder po) {
        Intent intent = new Intent(this, PurchaseOrderDetailActivity.class);
        intent.putExtra("poId", po.getPoId());
        startActivity(intent);
    }

    // New method to handle long click functionality
    private void showCancelOptions(PurchaseOrder po) {
        if (!"Pending".equalsIgnoreCase(po.getStatus())) {
            Toast.makeText(this, "Only pending orders can be cancelled or modified.", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("Manage Purchase Order")
                .setMessage("Do you want to cancel this order: " + po.getPoNumber() + "?")
                .setPositiveButton("Cancel Order", (dialog, which) -> cancelOrder(po))
                .setNegativeButton("Close", null)
                .show();
    }

    private void cancelOrder(PurchaseOrder po) {
        poRef.child(po.getPoId()).child("status").setValue("Cancelled")
                .addOnSuccessListener(aVoid -> Toast.makeText(PurchaseOrderListActivity.this, "Order Cancelled Successfully", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(PurchaseOrderListActivity.this, "Failed to cancel order: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}