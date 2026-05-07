package com.app.SalesInventory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;

public class StockValueReportAdapter extends RecyclerView.Adapter<StockValueReportAdapter.ViewHolder> {

    private List<StockValueReport> reportList;

    public StockValueReportAdapter(List<StockValueReport> reportList) {
        this.reportList = reportList;
    }

    @NonNull
    @Override
    public StockValueReportAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_stock_value_report, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull StockValueReportAdapter.ViewHolder holder, int position) {
        StockValueReport report = reportList.get(position);

        holder.tvProductName.setText(report.getProductName());
        holder.tvCategory.setText(report.getCategory());

        // Format dynamically to perfectly match the UI
        holder.tvCostValue.setText(String.format(Locale.US, "Cost: ₱%,.2f", report.getTotalCostValue()));
        holder.tvSellingValue.setText(String.format(Locale.US, "Selling: ₱%,.2f", report.getTotalSellingValue()));
        holder.tvProfit.setText(String.format(Locale.US, "Profit: ₱%,.2f", report.getProfit()));
        holder.tvMargin.setText("Margin: " + report.getProfitMargin());
    }

    @Override
    public int getItemCount() {
        return reportList == null ? 0 : reportList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvCategory, tvCostValue, tvSellingValue, tvProfit, tvMargin;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvCostValue = itemView.findViewById(R.id.tvCostValue);
            tvSellingValue = itemView.findViewById(R.id.tvSellingValue);
            tvProfit = itemView.findViewById(R.id.tvProfit);
            tvMargin = itemView.findViewById(R.id.tvMargin);
        }
    }
}