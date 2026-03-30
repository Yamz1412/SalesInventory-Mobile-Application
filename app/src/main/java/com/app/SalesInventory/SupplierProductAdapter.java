package com.app.SalesInventory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SupplierProductAdapter extends RecyclerView.Adapter<SupplierProductAdapter.ViewHolder> {
    private List<Product> products;
    private final OnProductClickListener listener;

    public interface OnProductClickListener {
        void onProductClick(Product product);
        void onProductLongClick(Product product);
    }

    public SupplierProductAdapter(List<Product> products, OnProductClickListener listener) {
        this.products = new ArrayList<>(products != null ? products : new ArrayList<>());
        this.listener = listener;
    }

    public void filterList(List<Product> filteredList) {
        this.products = filteredList != null ? filteredList : new ArrayList<>();
        // Post to avoid notifyDataSetChanged during RecyclerView layout/measure pass
        new android.os.Handler(android.os.Looper.getMainLooper()).post(this::notifyDataSetChanged);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_supplier_product_grid, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (position < 0 || position >= products.size()) return;
        Product p = products.get(position);
        if (p == null) return;

        // 1. Product Name
        holder.tvName.setText(p.getProductName() != null ? p.getProductName() : "Unknown Product");

        // 2. Unit Price Calculation (Total Cost / Quantity)
        double cost = p.getCostPrice();
        if (p.getQuantity() > 0) {
            cost = cost / p.getQuantity();
        }
        String unit = p.getUnit() != null ? p.getUnit() : "pcs";
        holder.tvPrice.setText(String.format(Locale.getDefault(), "₱%.2f / %s", cost, unit));

        // 3. Category Badge
        String category = p.getCategoryName();
        if (holder.tvCategory != null) {
            if (category != null && !category.trim().isEmpty()) {
                holder.tvCategory.setText(category);
                holder.tvCategory.setVisibility(View.VISIBLE);
            } else {
                holder.tvCategory.setVisibility(View.GONE);
            }
        }

        // 4. Stock Info
        if (holder.tvStock != null) {
            double qty = p.getQuantity();
            holder.tvStock.setText(String.format(Locale.getDefault(), "Stock: %.0f %s", qty, unit));
        }

        // 5. Click Listeners
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onProductClick(p);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onProductLongClick(p);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return products != null ? products.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvPrice, tvCategory, tvStock;

        ViewHolder(View itemView) {
            super(itemView);
            // These IDs perfectly match your item_supplier_product_grid.xml
            tvName     = itemView.findViewById(R.id.tvProductNameGrid);
            tvPrice    = itemView.findViewById(R.id.tvProductPriceGrid);
            tvCategory = itemView.findViewById(R.id.tvProductCategoryGrid);
            tvStock    = itemView.findViewById(R.id.tvProductStockGrid);
        }
    }
}