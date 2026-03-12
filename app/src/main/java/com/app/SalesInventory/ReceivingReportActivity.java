package com.app.SalesInventory;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

public class ReceivingReportActivity extends BaseActivity {

    private TextView tvSummary, tvNoData;
    private ProgressBar progressBar;
    private RecyclerView recyclerViewReceiving;

    private DatabaseReference poRef;
    private String currentOwnerId;
    private List<PurchaseOrder> receivedPOList = new ArrayList<>();
    private ReceivingAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiving_report);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Receiving Report");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();
        poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");

        tvSummary = findViewById(R.id.tvSummary);
        tvNoData = findViewById(R.id.tvNoData);
        progressBar = findViewById(R.id.progressBar);
        recyclerViewReceiving = findViewById(R.id.recyclerViewReceiving);

        recyclerViewReceiving.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ReceivingAdapter();
        recyclerViewReceiving.setAdapter(adapter);

        loadReceivingData();
    }

    private void loadReceivingData() {
        progressBar.setVisibility(View.VISIBLE);
        poRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                receivedPOList.clear();
                double totalSpent = 0.0;
                int totalOrders = 0;

                for (DataSnapshot ds : snapshot.getChildren()) {
                    PurchaseOrder po = ds.getValue(PurchaseOrder.class);
                    if (po != null && currentOwnerId.equals(po.getOwnerAdminId())) {

                        // FIXED: Use STATUS_RECEIVED instead of STATUS_DELIVERED
                        if (PurchaseOrder.STATUS_RECEIVED.equalsIgnoreCase(po.getStatus()) ||
                                PurchaseOrder.STATUS_PARTIAL.equalsIgnoreCase(po.getStatus())) {
                            receivedPOList.add(po);
                            totalSpent += po.getTotalAmount();
                            totalOrders++;
                        }
                    }
                }

                // FIXED: Safely compare Date objects by converting them to milliseconds
                Collections.sort(receivedPOList, (a, b) -> Long.compare(
                        b.getOrderDate() != null ? b.getOrderDate().getTime() : 0L,
                        a.getOrderDate() != null ? a.getOrderDate().getTime() : 0L
                ));

                tvSummary.setText(String.format(Locale.US, "Summary: %d Orders Received | Total Value: ₱%,.2f", totalOrders, totalSpent));
                progressBar.setVisibility(View.GONE);

                if (receivedPOList.isEmpty()) {
                    tvNoData.setVisibility(View.VISIBLE);
                    recyclerViewReceiving.setVisibility(View.GONE);
                } else {
                    tvNoData.setVisibility(View.GONE);
                    recyclerViewReceiving.setVisibility(View.VISIBLE);
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private class ReceivingAdapter extends RecyclerView.Adapter<ReceivingAdapter.ViewHolder> {
        private SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_receiving_report_row, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PurchaseOrder po = receivedPOList.get(position);

            holder.tvPoNumber.setText(po.getPoNumber());
            holder.tvSupplier.setText(po.getSupplierName());

            // FIXED: Safely format the Date directly
            if (po.getOrderDate() != null) {
                holder.tvDate.setText(sdf.format(po.getOrderDate()));
            } else {
                holder.tvDate.setText("Unknown Date");
            }

            holder.tvTotalAmount.setText(String.format(Locale.US, "₱%,.2f", po.getTotalAmount()));
            holder.tvStatus.setText(po.getStatus() != null ? po.getStatus().toUpperCase() : "UNKNOWN");

            // FIXED: Properly apply colors based on RECEIVED vs PARTIAL
            if (PurchaseOrder.STATUS_PARTIAL.equalsIgnoreCase(po.getStatus())) {
                holder.tvStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
            } else if (PurchaseOrder.STATUS_RECEIVED.equalsIgnoreCase(po.getStatus())) {
                holder.tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                holder.tvStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
        }

        @Override
        public int getItemCount() {
            return receivedPOList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvPoNumber, tvSupplier, tvDate, tvTotalAmount, tvStatus;

            ViewHolder(View itemView) {
                super(itemView);
                tvPoNumber = itemView.findViewById(R.id.tvPoNumber);
                tvSupplier = itemView.findViewById(R.id.tvSupplier);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvTotalAmount = itemView.findViewById(R.id.tvTotalAmount);
                tvStatus = itemView.findViewById(R.id.tvStatus);
            }
        }
    }
}