package com.app.SalesInventory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class SupplierAdapter extends RecyclerView.Adapter<SupplierAdapter.SupplierViewHolder> {

    private List<CreatePurchaseOrderActivity.SupplierItem> suppliers;
    private final OnSupplierClickListener listener;
    private int selectedPosition = -1;

    public interface OnSupplierClickListener {
        void onSupplierSelected(CreatePurchaseOrderActivity.SupplierItem supplier);
        void onSupplierDoubleClicked(CreatePurchaseOrderActivity.SupplierItem supplier);
        void onSupplierLongClicked(CreatePurchaseOrderActivity.SupplierItem supplier); // NEW: Long press
    }

    public SupplierAdapter(List<CreatePurchaseOrderActivity.SupplierItem> suppliers, OnSupplierClickListener listener) {
        this.suppliers = new ArrayList<>(suppliers);
        this.listener = listener;
    }

    public void filterList(List<CreatePurchaseOrderActivity.SupplierItem> filteredList) {
        suppliers = filteredList;
        selectedPosition = -1;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public SupplierViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_supplier, parent, false);
        return new SupplierViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SupplierViewHolder holder, int position) {
        CreatePurchaseOrderActivity.SupplierItem supplier = suppliers.get(position);
        holder.tvName.setText(supplier.name);
        holder.tvCategories.setText("Supplies: " + supplier.categories);

        if (selectedPosition == holder.getAdapterPosition()) {
            holder.itemView.setBackgroundColor(holder.itemView.getContext().getResources().getColor(R.color.CYAN, null));
        } else {
            holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        }

        // Single & Double Click
        holder.itemView.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();
            if (currentPosition == RecyclerView.NO_POSITION) return;

            if (selectedPosition == currentPosition) {
                listener.onSupplierDoubleClicked(supplier);
            } else {
                int previousPosition = selectedPosition;
                selectedPosition = currentPosition;
                notifyItemChanged(previousPosition);
                notifyItemChanged(selectedPosition);
                listener.onSupplierSelected(supplier);
            }
        });

        // NEW: Long Press Listener
        holder.itemView.setOnLongClickListener(v -> {
            listener.onSupplierLongClicked(supplier);
            return true; // Return true to indicate the long click was consumed
        });
    }

    @Override
    public int getItemCount() {
        return suppliers.size();
    }

    static class SupplierViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvCategories;

        SupplierViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvSupplierName);
            tvCategories = itemView.findViewById(R.id.tvSupplierCategories);
        }
    }
}