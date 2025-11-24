package com.app.SalesInventory;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class InventoryAdapter extends RecyclerView.Adapter<InventoryAdapter.ViewHolder> {

    private List<Product> productList;
    private Context context;

    public InventoryAdapter(List<Product> productList, Context context) {
        this.productList = productList;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_inventory, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Product product = productList.get(position);

        holder.tvProductName.setText(product.getProductName());
        holder.tvCategory.setText(product.getCategoryName());
        holder.tvQuantity.setText("Stock: " + product.getQuantity());
        holder.tvCostPrice.setText("Cost: ₱" + String.format("%.2f", product.getCostPrice()));
        holder.tvSellingPrice.setText("Selling: ₱" + String.format("%.2f", product.getSellingPrice()));

        // Set status indicator
        if (product.isCriticalStock()) {
            holder.tvStatus.setText("🔴 CRITICAL");
            holder.tvStatus.setTextColor(context.getResources().getColor(R.color.errorRed));
            holder.ivStatusIcon.setColorFilter(context.getResources().getColor(R.color.errorRed));
        } else if (product.isLowStock()) {
            holder.tvStatus.setText("🟡 LOW STOCK");
            holder.tvStatus.setTextColor(context.getResources().getColor(R.color.warningYellow));
            holder.ivStatusIcon.setColorFilter(context.getResources().getColor(R.color.warningYellow));
        } else if (product.isOverstock()) {
            holder.tvStatus.setText("🟢 OVERSTOCK");
            holder.tvStatus.setTextColor(context.getResources().getColor(R.color.successGreen));
            holder.ivStatusIcon.setColorFilter(context.getResources().getColor(R.color.successGreen));
        } else {
            holder.tvStatus.setText("✓ NORMAL");
            holder.tvStatus.setTextColor(context.getResources().getColor(R.color.colorPrimary));
            holder.ivStatusIcon.setColorFilter(context.getResources().getColor(R.color.colorPrimary));
        }

        // Click listener to edit product
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, EditProduct.class);
            intent.putExtra("productId", product.getProductId());
            context.startActivity(intent);
        });
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvCategory, tvQuantity, tvCostPrice, tvSellingPrice, tvStatus;
        ImageView ivStatusIcon;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            tvCostPrice = itemView.findViewById(R.id.tvCostPrice);
            tvSellingPrice = itemView.findViewById(R.id.tvSellingPrice);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            ivStatusIcon = itemView.findViewById(R.id.ivStatusIcon);
        }
    }
}