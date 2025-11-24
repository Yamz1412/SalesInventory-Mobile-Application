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

public class ProductDeleteAdapter extends RecyclerView.Adapter<ProductDeleteAdapter.ProductViewHolder> {

    private List<Product> productList;
    private Context context;
    private OnProductDeleteListener listener;

    public interface OnProductDeleteListener {
        void onProductDelete(String productId, String productName);
    }

    public ProductDeleteAdapter(List<Product> productList, Context context, OnProductDeleteListener listener) {
        this.productList = productList;
        this.context = context;
        this.listener = listener;
    }

    public void updateProducts(List<Product> newProducts) {
        this.productList = newProducts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_delete_product, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = productList.get(position);

        holder.nameTV.setText(product.getProductName());
        holder.categoryTV.setText(product.getCategoryName());

        holder.deleteBtn.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProductDelete(product.getProductId(), product.getProductName());
            }
        });
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    public static class ProductViewHolder extends RecyclerView.ViewHolder {
        TextView nameTV, categoryTV;
        ImageButton deleteBtn;

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            // Ensure these IDs match your layout item_delete_product.xml
            nameTV = itemView.findViewById(R.id.productNameTV);
            categoryTV = itemView.findViewById(R.id.categoryTV);
            deleteBtn = itemView.findViewById(R.id.deleteBtn);
        }
    }
}