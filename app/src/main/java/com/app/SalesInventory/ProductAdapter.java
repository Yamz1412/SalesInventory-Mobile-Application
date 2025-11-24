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

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ViewHolder> {

    private List<Product> productList;
    private Context context;

    public ProductAdapter(List<Product> productList, Context context) {
        this.productList = productList;
        this.context = context;
    }

    /**
     * Updates the list of products and refreshes the view.
     * This fixes the "Cannot resolve method updateProducts" error.
     */
    public void updateProducts(List<Product> newProducts) {
        this.productList = newProducts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Ensure your layout file is named 'item_inventory.xml'
        View view = LayoutInflater.from(context).inflate(R.layout.item_inventory, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Product product = productList.get(position);

        holder.tvProductName.setText(product.getProductName());
        holder.tvCategory.setText(product.getCategoryName());
        holder.tvQuantity.setText("Stock: " + product.getQuantity());

        // Format prices
        holder.tvCostPrice.setText(String.format("Cost: ₱%.2f", product.getCostPrice()));
        holder.tvSellingPrice.setText(String.format("Selling: ₱%.2f", product.getSellingPrice()));

        // Set status indicator
        if (product.isCriticalStock()) {
            holder.tvStatus.setText("🔴 CRITICAL");
            holder.tvStatus.setTextColor(android.graphics.Color.RED);
        } else if (product.isLowStock()) {
            holder.tvStatus.setText("🟡 LOW STOCK");
            holder.tvStatus.setTextColor(android.graphics.Color.parseColor("#FFA000")); // Orange/Yellow
        } else if (product.isOverstock()) {
            holder.tvStatus.setText("🟢 OVERSTOCK");
            holder.tvStatus.setTextColor(android.graphics.Color.GREEN);
        } else {
            holder.tvStatus.setText("✓ NORMAL");
            holder.tvStatus.setTextColor(android.graphics.Color.BLUE);
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
        // Make sure these IDs exist in your res/layout/item_inventory.xml
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