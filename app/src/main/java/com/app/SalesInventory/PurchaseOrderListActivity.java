package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PurchaseOrderListActivity extends BaseActivity  {

    private RecyclerView recyclerView;
    private PurchaseOrderAdapter adapter;
    private List<PurchaseOrder> purchaseOrderList;

    private Button btnCreatePO, btnAddSupplier, btnReturnProduct;
    private DatabaseReference poRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_order_list);

        recyclerView = findViewById(R.id.recyclerViewPurchaseOrders);
        btnCreatePO = findViewById(R.id.btnCreatePO);
        btnAddSupplier = findViewById(R.id.btnAddSupplier);
        btnReturnProduct = findViewById(R.id.btnReturnProduct);

        purchaseOrderList = new ArrayList<>();

        adapter = new PurchaseOrderAdapter(this, purchaseOrderList, this::viewPurchaseOrder, this::showManageOptions);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");

        loadPurchaseOrders();

        btnCreatePO.setOnClickListener(v -> {
            Intent intent = new Intent(PurchaseOrderListActivity.this, CreatePurchaseOrderActivity.class);
            startActivity(intent);
        });

        btnAddSupplier.setOnClickListener(v -> {
            Intent intent = new Intent(PurchaseOrderListActivity.this, AddSupplierActivity.class);
            startActivity(intent);
        });

        btnReturnProduct.setOnClickListener(v -> {
            Intent intent = new Intent(PurchaseOrderListActivity.this, ReturnProductActivity.class);
            startActivity(intent);
        });
    }

    private void loadPurchaseOrders() {
        // FIX: Ensure we use the Business Owner's ID to fetch the unified list
        String currentAdminId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (currentAdminId == null || currentAdminId.isEmpty()) {
            currentAdminId = AuthManager.getInstance().getCurrentUserId();
        }

        if (currentAdminId == null) {
            Toast.makeText(this, "Session error: Cannot identify business owner.", Toast.LENGTH_SHORT).show();
            return;
        }

        poRef.orderByChild("ownerAdminId").equalTo(currentAdminId).addValueEventListener(new ValueEventListener() {
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
        intent.putExtra("PO_ID", po.getPoId());
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

    public interface OnItemClickListener {
        void onItemClick(PurchaseOrder po);
    }

    public interface OnItemLongClickListener {
        void onItemLongClick(PurchaseOrder po);
    }

    private class PurchaseOrderAdapter extends RecyclerView.Adapter<PurchaseOrderAdapter.POViewHolder> {
        private List<PurchaseOrder> orders;
        private OnItemClickListener clickListener;
        private OnItemLongClickListener longClickListener;

        public PurchaseOrderAdapter(PurchaseOrderListActivity context, List<PurchaseOrder> orders, OnItemClickListener clickListener, OnItemLongClickListener longClickListener) {
            this.orders = orders;
            this.clickListener = clickListener;
            this.longClickListener = longClickListener;
        }

        @NonNull
        @Override
        public POViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_purchase_order, parent, false);
            return new POViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull POViewHolder holder, int position) {
            PurchaseOrder po = orders.get(position);
            holder.tvPoNumber.setText("PO #: " + po.getPoNumber());
            holder.tvSupplier.setText(po.getSupplierName());
            holder.tvStatus.setText(po.getStatus().toUpperCase());

            holder.tvTotalAmount.setText(String.format(Locale.getDefault(), "₱%.2f", po.getTotalAmount()));

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            holder.tvDate.setText(sdf.format((po.getOrderDate())));

            holder.itemView.setOnClickListener(v -> clickListener.onItemClick(po));
            holder.itemView.setOnLongClickListener(v -> {
                longClickListener.onItemLongClick(po);
                return true;
            });

            int color;
            switch (po.getStatus().toUpperCase()) {
                case "RECEIVED": color = getResources().getColor(R.color.successGreen); break;
                case "PENDING": color = getResources().getColor(R.color.warningYellow); break;
                case "PARTIAL": color = getResources().getColor(R.color.warningYellow); break;
                case "CANCELLED": color = getResources().getColor(R.color.errorRed); break;
                default: color = getResources().getColor(R.color.textColorSecondary);
            }
            holder.tvStatus.setTextColor(color);
        }

        @Override
        public int getItemCount() { return orders.size(); }

        class POViewHolder extends RecyclerView.ViewHolder {
            TextView tvPoNumber, tvSupplier, tvStatus, tvDate, tvTotalAmount;

            public POViewHolder(@NonNull View itemView) {
                super(itemView);
                tvPoNumber = itemView.findViewById(R.id.tvPONumber);
                tvSupplier = itemView.findViewById(R.id.tvSupplier);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                tvDate = itemView.findViewById(R.id.tvOrderDate);
                tvTotalAmount = itemView.findViewById(R.id.tvTotalAmount);
            }
        }
    }
}