package com.app.SalesInventory;

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

        // Format quantities properly
        holder.tvReceived.setText("IN: +" + formatQuantity(report.getReceived()));
        holder.tvSold.setText("SOLD: -" + formatQuantity(report.getSold()));

        // Adjustments can be positive or negative, so we format it smartly
        String adjPrefix = report.getAdjusted() >= 0 ? "ADJ: +" : "ADJ: ";
        holder.tvAdjusted.setText(adjPrefix + formatQuantity(report.getAdjusted()));
    }

    private String formatQuantity(double value) {
        if (value % 1 == 0) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }

    @Override
    public int getItemCount() {
        return reportList == null ? 0 : reportList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvCategory, tvReceived, tvSold, tvAdjusted;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvReceived = itemView.findViewById(R.id.tvReceived);
            tvSold = itemView.findViewById(R.id.tvSold);
            tvAdjusted = itemView.findViewById(R.id.tvAdjusted);
        }
    }
}