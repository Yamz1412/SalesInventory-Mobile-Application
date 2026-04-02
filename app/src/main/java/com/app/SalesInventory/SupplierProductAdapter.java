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

        holder.tvName.setText(p.getProductName() != null ? p.getProductName() : "Unknown Product");

        double cost = p.getCostPrice();
        if (p.getQuantity() > 0) {
            cost = cost / p.getQuantity();
        }
        String unit = p.getUnit() != null ? p.getUnit() : "pcs";
        holder.tvPrice.setText(String.format(Locale.getDefault(), "₱%.2f / %s", cost, unit));

        String category = p.getCategoryName();
        if (holder.tvCategory != null) {
            if (category != null && !category.trim().isEmpty()) {
                holder.tvCategory.setText(category);
                holder.tvCategory.setVisibility(View.VISIBLE);
            } else {
                holder.tvCategory.setVisibility(View.GONE);
            }
        }

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
        TextView tvName, tvPrice, tvCategory;

        ViewHolder(View itemView) {
            super(itemView);
            tvName     = itemView.findViewById(R.id.tvProductNameGrid);
            tvPrice    = itemView.findViewById(R.id.tvProductPriceGrid);
            tvCategory = itemView.findViewById(R.id.tvProductCategoryGrid);
        }
    }
}