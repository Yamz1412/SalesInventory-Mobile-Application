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

    private List<AdjustmentSummaryReport> summaryList;

    public AdjustmentSummaryAdapter(List<AdjustmentSummaryReport> summaryList) {
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
        AdjustmentSummaryReport report = summaryList.get(position);
        Context context = holder.itemView.getContext();

        holder.tvProductName.setText(report.getProductName());
        holder.tvTotalAdjustments.setText(String.valueOf(report.getTotalAdjustments()));
        holder.tvAdditions.setText("+" + report.getAdditions());
        holder.tvRemovals.setText("-" + report.getRemovals());

        int net = report.getNetChange();
        holder.tvNetChange.setText((net >= 0 ? "+" : "") + net);

        if (net > 0) {
            holder.tvNetChange.setTextColor(context.getResources().getColor(R.color.successGreen));
        } else if (net < 0) {
            holder.tvNetChange.setTextColor(context.getResources().getColor(R.color.errorRed));
        } else {
            holder.tvNetChange.setTextColor(context.getResources().getColor(R.color.textColorSecondary));
        }

        String reasonsText = report.getReasonsText();
        if (reasonsText != null && !reasonsText.isEmpty()) {
            holder.tvReasons.setText(reasonsText);
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