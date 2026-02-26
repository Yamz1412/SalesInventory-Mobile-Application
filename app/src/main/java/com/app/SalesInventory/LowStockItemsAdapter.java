package com.app.SalesInventory;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;

public class LowStockItemsAdapter extends RecyclerView.Adapter<LowStockItemsAdapter.VH> {

    private final Context ctx;
    private final List<Product> items;
    private final ProductRepository repository;
    private final AuthManager authManager;

    public LowStockItemsAdapter(Context ctx, List<Product> items, ProductRepository repository) {
        this.ctx = ctx;
        this.items = items;
        this.repository = repository != null ? repository : ProductRepository.getInstance((Application) ctx.getApplicationContext());
        this.authManager = AuthManager.getInstance();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_low_stock_product, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Product p = items.get(position);
        if (p == null) return;

        holder.name.setText(p.getProductName() != null ? p.getProductName() : "");
        holder.category.setText(p.getCategoryName() != null ? p.getCategoryName() : "");

        int qty = p.getQuantity();
        int reorder = p.getReorderLevel();
        int critical = p.getCriticalLevel();
        holder.stockInfo.setText("Stock: " + qty + " | Reorder: " + reorder + " | Critical: " + critical);
        holder.currentStock.setText(String.valueOf(qty));

        String imageUrl = p.getImageUrl();
        String imagePath = p.getImagePath();
        String toLoad = null;
        if (imageUrl != null && !imageUrl.isEmpty()) {
            toLoad = imageUrl;
        } else if (imagePath != null && !imagePath.isEmpty()) {
            toLoad = imagePath;
        }
        if (toLoad != null && !toLoad.isEmpty()) {
            Glide.with(ctx)
                    .load(toLoad)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(holder.productImage);
        } else {
            holder.productImage.setImageResource(R.drawable.ic_image_placeholder);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent i = new Intent(ctx, ProductDetailActivity.class);
            i.putExtra("productId", p.getProductId());
            ctx.startActivity(i);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView productImage;
        TextView name;
        TextView category;
        TextView stockInfo;
        TextView currentStock;

        VH(@NonNull View itemView) {
            super(itemView);
            productImage = itemView.findViewById(R.id.ivProductImage);
            name = itemView.findViewById(R.id.tvProductName);
            category = itemView.findViewById(R.id.tvCategory);
            stockInfo = itemView.findViewById(R.id.tvStockInfo);
            currentStock = itemView.findViewById(R.id.tvCurrentStock);
        }
    }
}