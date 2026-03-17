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

public class DamagedItemAdapter extends RecyclerView.Adapter<DamagedItemAdapter.ViewHolder> {

    private List<DamagedItem> damagedList = new ArrayList<>();
    private SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());

    public void setDamagedList(List<DamagedItem> list) {
        this.damagedList = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_damaged_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DamagedItem item = damagedList.get(position);
        holder.tvName.setText(item.productName);
        holder.tvReason.setText("Reason: " + item.reason);
        holder.tvDate.setText(sdf.format(new Date(item.timestamp)));
        holder.tvQty.setText("Lost: " + formatQuantity(item.qtyLost) + " units");
        holder.tvLoss.setText(String.format(Locale.US, "-₱%,.2f", item.monetaryLoss));
    }

    private String formatQuantity(double value) {
        if (value % 1 == 0) return String.valueOf((long) value);
        return String.format(Locale.US, "%.2f", value);
    }

    @Override
    public int getItemCount() {
        return damagedList.size();
    }

    public static class DamagedItem {
        String productName;
        String reason;
        long timestamp;
        double qtyLost;
        double monetaryLoss;

        public DamagedItem(String productName, String reason, long timestamp, double qtyLost, double monetaryLoss) {
            this.productName = productName;
            this.reason = reason;
            this.timestamp = timestamp;
            this.qtyLost = qtyLost;
            this.monetaryLoss = monetaryLoss;
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvReason, tvDate, tvQty, tvLoss;
        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvDamagedName);
            tvReason = itemView.findViewById(R.id.tvDamagedReason);
            tvDate = itemView.findViewById(R.id.tvDamagedDate);
            tvQty = itemView.findViewById(R.id.tvDamagedQty);
            tvLoss = itemView.findViewById(R.id.tvDamagedLoss);
        }
    }
}