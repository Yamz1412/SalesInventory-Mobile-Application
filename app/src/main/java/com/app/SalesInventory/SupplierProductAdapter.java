package com.app.SalesInventory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SupplierProductAdapter extends RecyclerView.Adapter<SupplierProductAdapter.ViewHolder> {
    private List<Product> products;
    private final OnProductClickListener listener;

    public interface OnProductClickListener {
        void onProductClick(Product product);
        void onProductLongClick(Product product); // NEW: Long press
    }

    public SupplierProductAdapter(List<Product> products, OnProductClickListener listener) {
        this.products = new ArrayList<>(products);
        this.listener = listener;
    }

    public void filterList(List<Product> filteredList) {
        this.products = filteredList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_supplier_product_grid, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Product p = products.get(position);
        holder.tvName.setText(p.getProductName());

        holder.tvPrice.setText(String.format(Locale.getDefault(), "₱%.2f", p.getCostPrice()));

        if (p.getImageUrl() != null && !p.getImageUrl().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(p.getImageUrl())
                    .placeholder(R.drawable.ic_image_placeholder)
                    .into(holder.ivImage);
        } else {
            holder.ivImage.setImageResource(R.drawable.ic_image_placeholder);
        }

        // Single Click -> Add to Cart
        holder.itemView.setOnClickListener(v -> listener.onProductClick(p));

        // NEW: Long Press -> Delete Product
        holder.itemView.setOnLongClickListener(v -> {
            listener.onProductLongClick(p);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return products.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivImage;
        TextView tvName, tvPrice;

        ViewHolder(View itemView) {
            super(itemView);
            ivImage = itemView.findViewById(R.id.ivProductImageGrid);
            tvName = itemView.findViewById(R.id.tvProductNameGrid);
            tvPrice = itemView.findViewById(R.id.tvProductPriceGrid);
        }
    }
}