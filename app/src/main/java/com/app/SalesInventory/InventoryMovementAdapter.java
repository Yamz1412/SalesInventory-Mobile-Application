package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class InventoryMovementAdapter extends RecyclerView.Adapter<InventoryMovementAdapter.VH> {
    private final List<InventoryMovement> items;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    public InventoryMovementAdapter(List<InventoryMovement> items) {
        this.items = items;
    }
    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_inventory_movement, parent, false);
        return new VH(v);
    }
    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        InventoryMovement m = items.get(position);
        holder.tvProduct.setText(m.getProductName().isEmpty() ? m.getProductId() : m.getProductName());
        holder.tvChange.setText((m.getChange() >= 0 ? "+" : "") + m.getChange());
        holder.tvQtyBefore.setText(String.valueOf(m.getQuantityBefore()));
        holder.tvQtyAfter.setText(String.valueOf(m.getQuantityAfter()));
        holder.tvType.setText(m.getType());
        holder.tvReason.setText(m.getReason());
        holder.tvRemarks.setText(m.getRemarks());
        long ts = m.getTimestamp();
        holder.tvDate.setText(ts > 0 ? dateFormat.format(new Date(ts)) : "");
        holder.tvUser.setText(m.getPerformedBy());
    }
    @Override
    public int getItemCount() {
        return items == null ? 0 : items.size();
    }
    static class VH extends RecyclerView.ViewHolder {
        TextView tvProduct;
        TextView tvChange;
        TextView tvQtyBefore;
        TextView tvQtyAfter;
        TextView tvType;
        TextView tvReason;
        TextView tvRemarks;
        TextView tvDate;
        TextView tvUser;
        VH(@NonNull View itemView) {
            super(itemView);
            tvProduct = itemView.findViewById(R.id.tvMovementProduct);
            tvChange = itemView.findViewById(R.id.tvMovementChange);
            tvQtyBefore = itemView.findViewById(R.id.tvMovementBefore);
            tvQtyAfter = itemView.findViewById(R.id.tvMovementAfter);
            tvType = itemView.findViewById(R.id.tvMovementType);
            tvReason = itemView.findViewById(R.id.tvMovementReason);
            tvRemarks = itemView.findViewById(R.id.tvMovementRemarks);
            tvDate = itemView.findViewById(R.id.tvMovementDate);
            tvUser = itemView.findViewById(R.id.tvMovementUser);
        }
    }
}