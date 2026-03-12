package com.app.SalesInventory;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class PurchaseOrderAdapter extends RecyclerView.Adapter<PurchaseOrderAdapter.POViewHolder> {

    private Context context;
    private List<PurchaseOrder> poList;
    private OnPOClickListener clickListener;
    private OnPOLongClickListener longClickListener;

    public interface OnPOClickListener {
        void onPOClick(PurchaseOrder po);
    }

    public interface OnPOLongClickListener {
        void onPOLongClick(PurchaseOrder po);
    }

    public PurchaseOrderAdapter(Context context, List<PurchaseOrder> poList, OnPOClickListener clickListener) {
        this.context = context;
        this.poList = poList;
        this.clickListener = clickListener;
        this.longClickListener = null;
    }

    public PurchaseOrderAdapter(Context context, List<PurchaseOrder> poList, OnPOClickListener clickListener, OnPOLongClickListener longClickListener) {
        this.context = context;
        this.poList = poList;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }

    @NonNull
    @Override
    public POViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_purchase_order, parent, false);
        return new POViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull POViewHolder holder, int position) {
        PurchaseOrder po = poList.get(position);

        holder.tvPONumber.setText(po.getPoNumber());
        holder.tvSupplier.setText(po.getSupplierName());
        holder.tvTotalAmount.setText(String.format(Locale.getDefault(), "₱%.2f", po.getTotalAmount()));

        if (po.getOrderDate() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            holder.tvOrderDate.setText(sdf.format(po.getOrderDate()));
        } else {
            holder.tvOrderDate.setText("Unknown Date");
        }

        if (holder.tvStatus != null) {
            String status = po.getStatus() != null ? po.getStatus() : "PENDING";
            holder.tvStatus.setText(status);

            // Safe fallback colors for statuses
            int statusColor;
            switch (status.toUpperCase()) {
                case "RECEIVED":
                    statusColor = Color.parseColor("#388E3C"); // Green
                    break;
                case "PARTIAL":
                    statusColor = Color.parseColor("#F57C00"); // Orange
                    break;
                case "CANCELLED":
                    statusColor = Color.parseColor("#D32F2F"); // Red
                    break;
                default:
                    statusColor = context.getResources().getColor(R.color.colorPrimary); // Blue/Default
            }
            holder.tvStatus.setTextColor(statusColor);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onPOClick(po);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onPOLongClick(po);
                return true;
            }
            return false;
        });
    }

    @Override
    public int getItemCount() {
        return poList.size();
    }

    static class POViewHolder extends RecyclerView.ViewHolder {
        TextView tvPONumber, tvSupplier, tvOrderDate, tvStatus, tvTotalAmount;

        public POViewHolder(@NonNull View itemView) {
            super(itemView);
            // Matches perfectly with item_purchase_order.xml
            tvPONumber = itemView.findViewById(R.id.tvPONumber);
            tvSupplier = itemView.findViewById(R.id.tvSupplier);
            tvOrderDate = itemView.findViewById(R.id.tvOrderDate);
            tvTotalAmount = itemView.findViewById(R.id.tvTotalAmount);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }
    }
}