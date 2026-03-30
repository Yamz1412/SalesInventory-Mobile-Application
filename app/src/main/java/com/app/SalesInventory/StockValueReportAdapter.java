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
        Context context = holder.itemView.getContext();

        holder.tvProductName.setText(report.getProductName());
        holder.tvCategory.setText("Category: " + report.getCategory());
        holder.tvQuantity.setText("Qty: " + report.getQuantity() + " units");

        // FIX: Display exact Cost Price, calculate accurate Selling and Profit
        double exactCost = report.getCostPrice();
        double exactSellingValue = report.getSellingPrice() * report.getQuantity();
        double exactProfit = exactSellingValue - exactCost;

        String margin = "0%";
        if (exactSellingValue > 0) {
            margin = String.format(Locale.getDefault(), "%.1f%%", (exactProfit / exactSellingValue) * 100);
        }

        holder.tvCostValue.setText(String.format(Locale.getDefault(), "Cost: ₱%,.2f", exactCost));
        holder.tvSellingValue.setText(String.format(Locale.getDefault(), "Selling: ₱%,.2f", exactSellingValue));
        holder.tvProfit.setText(String.format(Locale.getDefault(), "Profit: ₱%,.2f", exactProfit));
        holder.tvMargin.setText("Margin: " + margin);

        // Display the Automated Stock Numbers
        if (holder.tvCeiling != null) holder.tvCeiling.setText("Max: " + report.getCeilingLevel());
        if (holder.tvReorder != null) holder.tvReorder.setText("Reorder: " + report.getReorderLevel());
        if (holder.tvCritical != null) holder.tvCritical.setText("Critical: " + report.getCriticalLevel());

        String status = report.getStockStatus();
        switch (status) {
            case "CRITICAL":
                holder.tvStatus.setTextColor(context.getResources().getColor(R.color.errorRed));
                holder.tvStatus.setText("⚠️ CRITICAL");
                break;
            case "LOW STOCK":
                holder.tvStatus.setTextColor(context.getResources().getColor(R.color.warningYellow));
                holder.tvStatus.setText("⚠️ LOW STOCK");
                break;
            case "NORMAL":
                holder.tvStatus.setTextColor(context.getResources().getColor(R.color.colorPrimary));
                holder.tvStatus.setText("✓ NORMAL");
                break;
            case "OVERSTOCK":
                holder.tvStatus.setTextColor(context.getResources().getColor(R.color.successGreen));
                holder.tvStatus.setText("🟢 OVERSTOCK");
                break;
            default:
                holder.tvStatus.setText("");
                break;
        }
    }

    @Override
    public int getItemCount() {
        return reportList == null ? 0 : reportList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvCategory, tvQuantity, tvCostValue, tvSellingValue,
                tvProfit, tvMargin, tvStatus;

        TextView tvCeiling, tvReorder, tvCritical;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            tvCostValue = itemView.findViewById(R.id.tvCostValue);
            tvSellingValue = itemView.findViewById(R.id.tvSellingValue);
            tvProfit = itemView.findViewById(R.id.tvProfit);
            tvMargin = itemView.findViewById(R.id.tvMargin);
            tvStatus = itemView.findViewById(R.id.tvStatus);

            // Map the new automated threshold fields
            tvCeiling = itemView.findViewById(R.id.tvCeiling);
            tvReorder = itemView.findViewById(R.id.tvReorder);
            tvCritical = itemView.findViewById(R.id.tvCritical);
        }
    }
}