package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ProductDeleteAdapter extends RecyclerView.Adapter<ProductDeleteAdapter.ViewHolder> {
    private List<Product> productList;
    private Context context;
    private OnProductDeleteListener listener;
    private AuthManager authManager;

    public ProductDeleteAdapter(List<Product> productList, Context context, OnProductDeleteListener listener) {
        this.productList = productList == null ? new ArrayList<>() : productList;
        this.context = context;
        this.listener = listener;
        this.authManager = AuthManager.getInstance();
    }

    public void updateProducts(List<Product> newProducts) {
        this.productList = newProducts == null ? new ArrayList<>() : newProducts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProductDeleteAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_delete_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductDeleteAdapter.ViewHolder holder, int position) {
        Product p = productList.get(position);
        holder.productNameTV.setText(p.getProductName());
        holder.categoryTV.setText(p.getCategoryName());
        holder.deleteBtn.setOnClickListener(v -> {
            if (authManager.isCurrentUserAdmin()) {
                if (listener != null) {
                    listener.onProductDelete(p.getProductId(), p.getProductName());
                }
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            return !authManager.isCurrentUserAdmin();
        });
    }

    @Override
    public int getItemCount() {
        return productList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView productNameTV;
        TextView categoryTV;
        ImageButton deleteBtn;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            productNameTV = itemView.findViewById(R.id.productNameTV);
            categoryTV = itemView.findViewById(R.id.categoryTV);
            deleteBtn = itemView.findViewById(R.id.deleteBtn);
        }
    }

    public interface OnProductDeleteListener {
        void onProductDelete(String productId, String productName);
    }
}