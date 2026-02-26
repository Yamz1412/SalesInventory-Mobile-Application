package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ReceivingReportActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private ProgressBar progressBar;
    private TextView tvNoData, tvSummary;
    private ReceivingReportAdapter adapter;
    private List<PurchaseOrder> purchaseOrderList;
    private DatabaseReference poRef;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_receiving_report);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Receiving Report");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initializeViews();
        loadData();
    }

    private void initializeViews() {
        recyclerView = findViewById(R.id.recyclerViewReceiving);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);
        tvSummary = findViewById(R.id.tvSummary);

        purchaseOrderList = new ArrayList<>();
        adapter = new ReceivingReportAdapter(purchaseOrderList, dateFormat, currencyFormat);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");
    }

    private void loadData() {
        progressBar.setVisibility(View.VISIBLE);
        tvNoData.setVisibility(View.GONE);

        poRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                purchaseOrderList.clear();
                double totalReceivedAmount = 0;
                int receivedCount = 0;
                int pendingCount = 0;

                for (DataSnapshot ds : snapshot.getChildren()) {
                    PurchaseOrder po = ds.getValue(PurchaseOrder.class);
                    if (po != null) {
                        if (po.getPoId() == null || po.getPoId().isEmpty()) {
                            po.setPoId(ds.getKey());
                        }
                        purchaseOrderList.add(po);
                        if ("Received".equalsIgnoreCase(po.getStatus())) {
                            totalReceivedAmount += po.getTotalAmount();
                            receivedCount++;
                        } else if ("Pending".equalsIgnoreCase(po.getStatus())) {
                            pendingCount++;
                        }
                    }
                }

                progressBar.setVisibility(View.GONE);
                adapter.notifyDataSetChanged();

                if (purchaseOrderList.isEmpty()) {
                    tvNoData.setVisibility(View.VISIBLE);
                    tvSummary.setText("No purchase orders found.");
                } else {
                    tvNoData.setVisibility(View.GONE);
                    String summary = "Received: " + receivedCount +
                            "   Pending: " + pendingCount +
                            "   Total Received Amount: " + currencyFormat.format(totalReceivedAmount);
                    tvSummary.setText(summary);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(ReceivingReportActivity.this,
                        "Error loading receiving report: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    public static class ReceivingReportAdapter extends RecyclerView.Adapter<ReceivingReportAdapter.ViewHolder> {

        private List<PurchaseOrder> data;
        private SimpleDateFormat dateFormat;
        private NumberFormat currencyFormat;

        public ReceivingReportAdapter(List<PurchaseOrder> data,
                                      SimpleDateFormat dateFormat,
                                      NumberFormat currencyFormat) {
            this.data = data;
            this.dateFormat = dateFormat;
            this.currencyFormat = currencyFormat;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.view.View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_receiving_report_row, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            PurchaseOrder po = data.get(position);
            String dateStr = dateFormat.format(new Date(po.getOrderDate()));
            holder.tvPoNumber.setText(po.getPoNumber());
            holder.tvSupplier.setText(po.getSupplierName());
            holder.tvDate.setText(dateStr);
            holder.tvStatus.setText(po.getStatus());
            holder.tvTotalAmount.setText(currencyFormat.format(po.getTotalAmount()));

            int color;
            String status = po.getStatus() != null ? po.getStatus() : "";
            if ("Received".equalsIgnoreCase(status)) {
                color = holder.itemView.getContext().getResources().getColor(R.color.successGreen);
            } else if ("Pending".equalsIgnoreCase(status)) {
                color = holder.itemView.getContext().getResources().getColor(R.color.warningYellow);
            } else if ("Cancelled".equalsIgnoreCase(status)) {
                color = holder.itemView.getContext().getResources().getColor(R.color.errorRed);
            } else {
                color = holder.itemView.getContext().getResources().getColor(R.color.textColorSecondary);
            }
            holder.tvStatus.setTextColor(color);
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvPoNumber, tvSupplier, tvDate, tvStatus, tvTotalAmount;

            public ViewHolder(@NonNull android.view.View itemView) {
                super(itemView);
                tvPoNumber = itemView.findViewById(R.id.tvPoNumber);
                tvSupplier = itemView.findViewById(R.id.tvSupplier);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                tvTotalAmount = itemView.findViewById(R.id.tvTotalAmount);
            }
        }
    }
}