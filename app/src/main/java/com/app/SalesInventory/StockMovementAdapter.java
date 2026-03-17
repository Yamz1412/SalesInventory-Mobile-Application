package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class StockMovementAdapter extends RecyclerView.Adapter<StockMovementAdapter.ViewHolder> {

    private List<StockMovementReport> reportList;

    public StockMovementAdapter(List<StockMovementReport> reportList) {
        this.reportList = reportList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stock_movement_report, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StockMovementReport report = reportList.get(position);

        holder.tvProductName.setText(report.getProductName());
        holder.tvCategory.setText(report.getCategory());

        holder.tvOpeningStock.setText(formatQuantity(report.getOpeningStock()));
        holder.tvClosingStock.setText(formatQuantity(report.getClosingStock()));

        holder.tvReceived.setText("IN: +" + formatQuantity(report.getReceived()));
        holder.tvSold.setText("SOLD: -" + report.getSold());
        holder.tvAdjusted.setText("ADJ: -" + formatQuantity(report.getAdjusted()));
    }

    private String formatQuantity(double value) {
        if (value % 1 == 0) {
            return String.valueOf((long) value);
        }
        return String.format(java.util.Locale.US, "%.2f", value);
    }

    @Override
    public int getItemCount() {
        return reportList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvCategory, tvOpeningStock, tvReceived, tvSold, tvAdjusted, tvClosingStock;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvOpeningStock = itemView.findViewById(R.id.tvOpeningStock);
            tvReceived = itemView.findViewById(R.id.tvReceived);
            tvSold = itemView.findViewById(R.id.tvSold);
            tvAdjusted = itemView.findViewById(R.id.tvAdjusted);
            tvClosingStock = itemView.findViewById(R.id.tvClosingStock);
        }
    }
}