package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class POItemAdapter extends RecyclerView.Adapter<POItemAdapter.ItemViewHolder> {

    private Context context;
    private List<POItem> itemsList; // Now uses the new POItem class
    private OnItemRemoveListener removeListener;
    private OnItemChangeListener changeListener;

    public interface OnItemRemoveListener {
        void onRemove(int position);
    }

    public interface OnItemChangeListener {
        void onChange();
    }

    public POItemAdapter(Context context, List<POItem> itemsList,
                         OnItemRemoveListener removeListener, OnItemChangeListener changeListener) {
        this.context = context;
        this.itemsList = itemsList;
        this.removeListener = removeListener;
        this.changeListener = changeListener;
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // This uses your existing item_po_item.xml file
        View view = LayoutInflater.from(context).inflate(R.layout.item_po_item, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        POItem item = itemsList.get(position);

        // Bind data to the views in your XML
        holder.tvProductName.setText(item.getProductName());
        holder.tvQuantity.setText(String.format(Locale.getDefault(), "Qty: %d", item.getQuantity()));
        holder.tvUnitPrice.setText(String.format(Locale.getDefault(), "₱%.2f", item.getUnitPrice()));
        holder.tvSubtotal.setText(String.format(Locale.getDefault(), "₱%.2f", item.getSubtotal()));

        // Handle delete button click
        holder.btnRemove.setOnClickListener(v -> {
            if (removeListener != null) {
                removeListener.onRemove(position);
            }
        });
    }

    @Override
    public int getItemCount() {
        return itemsList.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvQuantity, tvUnitPrice, tvSubtotal;
        ImageButton btnRemove;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            tvUnitPrice = itemView.findViewById(R.id.tvUnitPrice);
            tvSubtotal = itemView.findViewById(R.id.tvSubtotal);
            btnRemove = itemView.findViewById(R.id.btnRemove);
        }
    }
}