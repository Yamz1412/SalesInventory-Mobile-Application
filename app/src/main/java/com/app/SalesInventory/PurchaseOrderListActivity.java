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

        adapter = new PurchaseOrderAdapter(this, purchaseOrderList, this::viewPurchaseOrder, this::showManageOptions);

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

    private void showManageOptions(PurchaseOrder po) {
        CharSequence[] options = {"Cancel Order", "Delete Order", "Close"};

        new AlertDialog.Builder(this)
                .setTitle("Manage Purchase Order: " + po.getPoNumber())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (!"Pending".equalsIgnoreCase(po.getStatus())) {
                            Toast.makeText(this, "Only pending orders can be cancelled.", Toast.LENGTH_SHORT).show();
                        } else {
                            cancelOrder(po);
                        }
                    } else if (which == 1) {
                        confirmDelete(po);
                    } else {
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void cancelOrder(PurchaseOrder po) {
        poRef.child(po.getPoId()).child("status").setValue("Cancelled")
                .addOnSuccessListener(aVoid -> Toast.makeText(PurchaseOrderListActivity.this, "Order Cancelled Successfully", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(PurchaseOrderListActivity.this, "Failed to cancel order: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void confirmDelete(PurchaseOrder po) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Purchase Order")
                .setMessage("Are you sure you want to permanently delete this order: " + po.getPoNumber() + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteOrder(po))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteOrder(PurchaseOrder po) {
        poRef.child(po.getPoId()).removeValue()
                .addOnSuccessListener(aVoid -> Toast.makeText(PurchaseOrderListActivity.this, "Order Deleted Successfully", Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(PurchaseOrderListActivity.this, "Failed to delete order: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }
}