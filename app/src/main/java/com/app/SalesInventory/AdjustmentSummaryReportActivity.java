package com.app.SalesInventory;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

public class AdjustmentSummaryReportActivity extends BaseActivity {

    private TextView tvTotalAdjustments, tvNoData;
    private Button btnExportCSV;
    private ProgressBar progressBar;
    private RecyclerView recyclerViewReport;

    private DatabaseReference adjustmentRef;
    private String currentOwnerId;
    private List<StockAdjustment> adjustmentList = new ArrayList<>();
    private AdjustmentAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adjustment_summary_report);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Stock Adjustments");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();
        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");

        tvTotalAdjustments = findViewById(R.id.tvTotalAdjustments);
        tvNoData = findViewById(R.id.tvNoData);
        btnExportCSV = findViewById(R.id.btnExportCSV);
        progressBar = findViewById(R.id.progressBar);
        recyclerViewReport = findViewById(R.id.recyclerViewReport);

        recyclerViewReport.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdjustmentAdapter();
        recyclerViewReport.setAdapter(adapter);

        btnExportCSV.setOnClickListener(v -> {
            // Add CSV Export Logic here if needed
        });

        loadAdjustments();
    }

    private void loadAdjustments() {
        progressBar.setVisibility(View.VISIBLE);
        adjustmentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                adjustmentList.clear();
                int totalAdjustments = 0;

                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockAdjustment adj = ds.getValue(StockAdjustment.class);
                    if (adj != null) {
                        String owner = ds.child("ownerAdminId").getValue(String.class);
                        if (currentOwnerId.equals(owner) || currentOwnerId.equals(adj.getOwnerAdminId())) {
                            adjustmentList.add(adj);
                            totalAdjustments++;
                        }
                    }
                }

                // Sort by most recent first
                Collections.sort(adjustmentList, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));

                tvTotalAdjustments.setText(String.valueOf(totalAdjustments));
                progressBar.setVisibility(View.GONE);

                if (adjustmentList.isEmpty()) {
                    tvNoData.setVisibility(View.VISIBLE);
                    recyclerViewReport.setVisibility(View.GONE);
                } else {
                    tvNoData.setVisibility(View.GONE);
                    recyclerViewReport.setVisibility(View.VISIBLE);
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

    private class AdjustmentAdapter extends RecyclerView.Adapter<AdjustmentAdapter.ViewHolder> {
        private SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_stock_adjustment, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            StockAdjustment adj = adjustmentList.get(position);

            holder.tvProductName.setText(adj.getProductName());
            holder.tvAdjustmentType.setText(adj.getAdjustmentType());

            // Format Quantity explicitly
            int qty = adj.getQuantityAdjusted();
            if (qty > 0) {
                holder.tvQuantity.setText("+" + qty);
                holder.tvQuantity.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                holder.tvAdjustmentType.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
            } else {
                holder.tvQuantity.setText(String.valueOf(qty));
                holder.tvQuantity.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                holder.tvAdjustmentType.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            }

            holder.tvRemarks.setText(adj.getReason() != null ? adj.getReason() : "No remarks");
            holder.tvDate.setText(sdf.format(new Date(adj.getTimestamp())));
            holder.tvAdjustedBy.setText("By: " + (adj.getAdjustedBy() != null ? adj.getAdjustedBy() : "Admin"));
        }

        @Override
        public int getItemCount() {
            return adjustmentList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvProductName, tvAdjustmentType, tvQuantity, tvRemarks, tvDate, tvAdjustedBy;

            ViewHolder(View itemView) {
                super(itemView);
                tvProductName = itemView.findViewById(R.id.tvProductName);
                tvAdjustmentType = itemView.findViewById(R.id.tvAdjustmentType);
                tvQuantity = itemView.findViewById(R.id.tvQuantity);
                tvRemarks = itemView.findViewById(R.id.tvRemarks);
                tvDate = itemView.findViewById(R.id.tvDate);
                tvAdjustedBy = itemView.findViewById(R.id.tvAdjustedBy);
            }
        }
    }
}