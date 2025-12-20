package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class StockMovementAdapter extends RecyclerView.Adapter<StockMovementAdapter.ViewHolder> {

    private List<StockMovementReport> reportList;

    public StockMovementAdapter(List<StockMovementReport> reportList) {
        this.reportList = reportList;
    }

    public List<StockMovementReport> getReportList() {
        return reportList;
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
        Context context = holder.itemView.getContext();

        holder.tvProductName.setText(report.getProductName());
        holder.tvCategory.setText("Category: " + report.getCategory());
        holder.tvOpening.setText("Opening: " + report.getOpeningStock());
        holder.tvReceived.setText("Received: +" + report.getReceived());
        holder.tvSold.setText("Sold: -" + report.getSold());
        holder.tvAdjusted.setText("Adjusted: " + (report.getAdjusted() >= 0 ? "+" : "") + report.getAdjusted());
        holder.tvClosing.setText("Closing: " + report.getClosingStock());
        holder.tvMovement.setText(String.format(Locale.getDefault(), "%.2f%%", report.getMovementPercentage()));

        if (report.getMovementPercentage() > 50) {
            holder.tvMovement.setTextColor(context.getResources().getColor(R.color.successGreen));
        } else if (report.getMovementPercentage() > 20) {
            holder.tvMovement.setTextColor(context.getResources().getColor(R.color.colorPrimary));
        } else {
            holder.tvMovement.setTextColor(context.getResources().getColor(R.color.warningYellow));
        }
    }

    @Override
    public int getItemCount() {
        return reportList == null ? 0 : reportList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvCategory, tvOpening, tvReceived, tvSold, tvAdjusted, tvClosing, tvMovement;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvOpening = itemView.findViewById(R.id.tvOpening);
            tvReceived = itemView.findViewById(R.id.tvReceived);
            tvSold = itemView.findViewById(R.id.tvSold);
            tvAdjusted = itemView.findViewById(R.id.tvAdjusted);
            tvClosing = itemView.findViewById(R.id.tvClosing);
            tvMovement = itemView.findViewById(R.id.tvMovement);
        }
    }
}