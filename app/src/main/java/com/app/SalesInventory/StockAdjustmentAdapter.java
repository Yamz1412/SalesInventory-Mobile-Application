package com.app.SalesInventory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StockAdjustmentAdapter extends RecyclerView.Adapter<StockAdjustmentAdapter.ViewHolder> {

    private List<StockAdjustment> adjustmentList = new ArrayList<>();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

    public StockAdjustmentAdapter(List<StockAdjustment> adjustmentList) {
        if (adjustmentList != null) this.adjustmentList = adjustmentList;
    }

    public void setAdjustments(List<StockAdjustment> adjustments) {
        if (adjustments == null) {
            this.adjustmentList = new ArrayList<>();
        } else {
            this.adjustmentList = adjustments;
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stock_adjustment, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (adjustmentList == null || position < 0 || position >= adjustmentList.size()) return;
        StockAdjustment adjustment = adjustmentList.get(position);
        if (adjustment == null) return;

        holder.tvProductName.setText(adjustment.getProductName() == null ? "" : adjustment.getProductName());
        holder.tvAdjustmentType.setText(adjustment.getAdjustmentType() == null ? "" : adjustment.getAdjustmentType());
        holder.tvReason.setText(adjustment.getReason() == null ? "" : adjustment.getReason());

        String quantityText = "Qty: " + adjustment.getQuantityBefore() + " → " + adjustment.getQuantityAfter();
        holder.tvQuantity.setText(quantityText);

        String dateText = dateFormat.format(new Date(adjustment.getTimestamp()));
        holder.tvDate.setText(dateText);

        if (adjustment.getRemarks() != null && !adjustment.getRemarks().isEmpty()) {
            holder.tvRemarks.setText(adjustment.getRemarks());
            holder.tvRemarks.setVisibility(View.VISIBLE);
        } else {
            holder.tvRemarks.setVisibility(View.GONE);
        }

        holder.tvAdjustedBy.setText("By: " + (adjustment.getAdjustedBy() == null ? "" : adjustment.getAdjustedBy()));

        if ("Add Stock".equalsIgnoreCase(adjustment.getAdjustmentType())) {
            holder.tvAdjustmentType.setTextColor(holder.itemView.getContext()
                    .getResources().getColor(R.color.successGreen));
        } else {
            holder.tvAdjustmentType.setTextColor(holder.itemView.getContext()
                    .getResources().getColor(R.color.errorRed));
        }
    }

    @Override
    public int getItemCount() {
        return adjustmentList == null ? 0 : adjustmentList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvAdjustmentType, tvReason, tvQuantity, tvDate, tvRemarks, tvAdjustedBy;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvAdjustmentType = itemView.findViewById(R.id.tvAdjustmentType);
            tvReason = itemView.findViewById(R.id.tvReason);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvRemarks = itemView.findViewById(R.id.tvRemarks);
            tvAdjustedBy = itemView.findViewById(R.id.tvAdjustedBy);
        }
    }
}