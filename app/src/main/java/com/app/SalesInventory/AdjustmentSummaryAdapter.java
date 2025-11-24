package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class AdjustmentSummaryAdapter extends RecyclerView.Adapter<AdjustmentSummaryAdapter.ViewHolder> {

    private List<AdjustmentSummaryData> summaryList;

    public AdjustmentSummaryAdapter(List<AdjustmentSummaryData> summaryList) {
        this.summaryList = summaryList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_adjustment_summary, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AdjustmentSummaryData summary = summaryList.get(position);
        Context context = holder.itemView.getContext();

        holder.tvProductName.setText(summary.getProductName());
        holder.tvTotalAdjustments.setText("Total: " + summary.getTotalAdjustments() + " adjustments");
        holder.tvAdditions.setText("+Add: " + summary.getTotalAdditions() + " units");
        holder.tvRemovals.setText("-Remove: " + summary.getTotalRemovals() + " units");
        holder.tvNetChange.setText("Net: " + (summary.getNetChange() >= 0 ? "+" : "") + summary.getNetChange());

        // Color code net change
        if (summary.getNetChange() > 0) {
            holder.tvNetChange.setTextColor(context.getResources().getColor(R.color.successGreen));
        } else if (summary.getNetChange() < 0) {
            holder.tvNetChange.setTextColor(context.getResources().getColor(R.color.errorRed));
        } else {
            holder.tvNetChange.setTextColor(context.getResources().getColor(R.color.textColorSecondary));
        }

        // Build reason summary
        StringBuilder reasons = new StringBuilder();
        if (!summary.getAdditionReasons().isEmpty()) {
            reasons.append("Added: ").append(String.join(", ", summary.getAdditionReasons()));
        }
        if (!summary.getRemovalReasons().isEmpty()) {
            if (reasons.length() > 0) reasons.append("\n");
            reasons.append("Removed: ").append(String.join(", ", summary.getRemovalReasons()));
        }

        if (reasons.length() > 0) {
            holder.tvReasons.setText(reasons.toString());
            holder.tvReasons.setVisibility(View.VISIBLE);
        } else {
            holder.tvReasons.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return summaryList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvTotalAdjustments, tvAdditions, tvRemovals, tvNetChange, tvReasons;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvTotalAdjustments = itemView.findViewById(R.id.tvTotalAdjustments);
            tvAdditions = itemView.findViewById(R.id.tvAdditions);
            tvRemovals = itemView.findViewById(R.id.tvRemovals);
            tvNetChange = itemView.findViewById(R.id.tvNetChange);
            tvReasons = itemView.findViewById(R.id.tvReasons);
        }
    }
}