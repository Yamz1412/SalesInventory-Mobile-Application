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

public class BatchOperationAdapter extends RecyclerView.Adapter<BatchOperationAdapter.ViewHolder> {

    private List<Product> selectedProducts;
    private Context context;
    private OnProductRemoveListener listener;

    public interface OnProductRemoveListener {
        void onRemove(Product product);
    }

    public BatchOperationAdapter(List<Product> selectedProducts, Context context, OnProductRemoveListener listener) {
        this.selectedProducts = selectedProducts;
        this.context = context;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_batch_operation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Product product = selectedProducts.get(position);

        holder.tvProductName.setText(product.getProductName());
        holder.tvCategory.setText(product.getCategoryName());
        holder.tvCurrentQty.setText("Current: " + product.getQuantity() + " units");
        holder.tvSKU.setText("SKU: " + (product.getBarcode() != null ? product.getBarcode() : "N/A"));

        holder.ibRemove.setOnClickListener(v -> {
            if (listener != null) {
                listener.onRemove(product);
            }
        });
    }

    @Override
    public int getItemCount() {
        return selectedProducts.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvCategory, tvCurrentQty, tvSKU;
        ImageButton ibRemove;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvCurrentQty = itemView.findViewById(R.id.tvCurrentQty);
            tvSKU = itemView.findViewById(R.id.tvSKU);
            ibRemove = itemView.findViewById(R.id.ibRemove);
        }
    }
}