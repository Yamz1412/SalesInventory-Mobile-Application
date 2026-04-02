package com.app.SalesInventory;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.List;
import java.util.Locale;

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

        holder.name.setText(p.getProductName());

        String categoryStr = p.getCategoryName() != null && !p.getCategoryName().isEmpty() ? p.getCategoryName() : "Uncategorized";
        holder.category.setText(categoryStr);

        // Clean decimal formatting for stock and limits
        String currentQtyStr = (p.getQuantity() % 1 == 0) ? String.valueOf((long) p.getQuantity()) : String.format(Locale.US, "%.2f", p.getQuantity());

        holder.stockInfo.setText(String.format(Locale.US, "Reorder: %d | Critical: %d", p.getReorderLevel(), p.getCriticalLevel()));

        String unitStr = p.getUnit() != null ? p.getUnit() : "pcs";
        holder.currentStock.setText(currentQtyStr + " " + unitStr);

        // Dynamic color coding for severity
        if (p.getQuantity() <= p.getCriticalLevel()) {
            // Critical Stock -> Red
            holder.currentStock.setTextColor(Color.parseColor("#E53935"));
        } else {
            // Low Stock (Below reorder, above critical) -> Orange
            holder.currentStock.setTextColor(Color.parseColor("#FB8C00"));
        }

        // ROBUST OFFLINE IMAGE FALLBACK (Same as ProductAdapter)
        String imageUrl = p.getImageUrl();
        String localPath = p.getImagePath();

        if (imageUrl != null && !imageUrl.isEmpty()) {
            Glide.with(ctx)
                    .load(imageUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(
                            // Fallback to local gallery path if URL fails
                            Glide.with(ctx)
                                    .load(localPath)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .error(R.drawable.ic_image_placeholder)
                    )
                    .centerCrop()
                    .into(holder.productImage);
        } else if (localPath != null && !localPath.isEmpty()) {
            Glide.with(ctx)
                    .load(localPath)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
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
            // Added safety fallback extra matching Inventory logic
            i.putExtra("PRODUCT_ID", p.getProductId());
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