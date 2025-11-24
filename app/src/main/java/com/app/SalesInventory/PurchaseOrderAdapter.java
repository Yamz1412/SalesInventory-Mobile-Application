package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PurchaseOrderAdapter extends RecyclerView.Adapter<PurchaseOrderAdapter.POViewHolder> {

    private Context context;
    private List<PurchaseOrder> poList;
    private OnPOClickListener clickListener;

    public interface OnPOClickListener {
        void onPOClick(PurchaseOrder po);
    }

    public PurchaseOrderAdapter(Context context, List<PurchaseOrder> poList, OnPOClickListener clickListener) {
        this.context = context;
        this.poList = poList;
        this.clickListener = clickListener;
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
        holder.tvStatus.setText(po.getStatus());

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        holder.tvOrderDate.setText(sdf.format(new Date(po.getOrderDate())));

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        holder.tvTotalAmount.setText(currencyFormat.format(po.getTotalAmount()));

        int statusColor;
        switch (po.getStatus()) {
            case "Pending":
                statusColor = context.getResources().getColor(R.color.warningYellow);
                break;
            case "Received":
                statusColor = context.getResources().getColor(R.color.successGreen);
                break;
            case "Cancelled":
                statusColor = context.getResources().getColor(R.color.errorRed);
                break;
            default:
                statusColor = context.getResources().getColor(R.color.textColorSecondary);
        }
        holder.tvStatus.setTextColor(statusColor);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onPOClick(po);
            }
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
            tvPONumber = itemView.findViewById(R.id.tvPONumber);
            tvSupplier = itemView.findViewById(R.id.tvSupplier);
            tvOrderDate = itemView.findViewById(R.id.tvOrderDate);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvTotalAmount = itemView.findViewById(R.id.tvTotalAmount);
        }
    }
}