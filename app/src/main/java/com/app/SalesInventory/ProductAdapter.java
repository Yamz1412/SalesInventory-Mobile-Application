package com.app.SalesInventory;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
    private List<Product> items = new ArrayList<>();
    private Context ctx;
    private ProductRepository repository;
    private AuthManager authManager;

    public ProductAdapter(Context ctx) {
        this.ctx = ctx;
        this.repository = ProductRepository.getInstance((Application) ctx.getApplicationContext());
        this.authManager = AuthManager.getInstance();
    }

    public ProductAdapter(List<Product> initialList, Context ctx) {
        this(ctx);
        if (initialList != null) {
            this.items = new ArrayList<>(initialList);
        } else {
            this.items = new ArrayList<>();
        }
    }

    public void updateProducts(List<Product> list) {
        this.items = list == null ? new ArrayList<>() : new ArrayList<>(list);
        notifyDataSetChanged();
    }

    public void update(List<Product> list) {
        updateProducts(list);
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_inventory, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Product p = items.get(position);
        holder.name.setText(p.getProductName());
        holder.category.setText(p.getCategoryName());
        holder.qty.setText("Stock: " + p.getQuantity());
        holder.price.setText("Selling: ₱" + String.format("%.2f", p.getSellingPrice()));
        holder.syncState.setText("");
        holder.retryBtn.setVisibility(View.GONE);
        final long localId = p.getLocalId();
        new Thread(() -> {
            ProductDao dao = AppDatabase.getInstance(ctx).productDao();
            ProductEntity e = dao.getByLocalId(localId);
            if (e != null) {
                String state = e.syncState == null ? "" : e.syncState;
                holder.syncState.post(() -> {
                    switch (state) {
                        case "PENDING":
                            holder.syncState.setText("Pending");
                            holder.retryBtn.setVisibility(View.GONE);
                            break;
                        case "DELETE_PENDING":
                            holder.syncState.setText("Delete pending");
                            holder.retryBtn.setVisibility(View.VISIBLE);
                            break;
                        case "ERROR":
                            holder.syncState.setText("Error");
                            holder.retryBtn.setVisibility(View.VISIBLE);
                            break;
                        case "SYNCED":
                            holder.syncState.setText("Synced");
                            holder.retryBtn.setVisibility(View.GONE);
                            break;
                        default:
                            holder.syncState.setText(state);
                            holder.retryBtn.setVisibility(View.GONE);
                            break;
                    }
                });
            }
        }).start();
        if (authManager.isCurrentUserAdmin()) {
            holder.itemView.setOnClickListener(v -> {
                Intent i = new Intent(ctx, EditProduct.class);
                i.putExtra("productId", p.getProductId());
                ctx.startActivity(i);
            });
        } else {
            holder.itemView.setOnClickListener(null);
        }
        holder.retryBtn.setOnClickListener(v -> repository.retrySync(localId));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, category, qty, price, syncState;
        ImageButton retryBtn;
        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvProductName);
            category = itemView.findViewById(R.id.tvCategory);
            qty = itemView.findViewById(R.id.tvQuantity);
            price = itemView.findViewById(R.id.tvSellingPrice);
            syncState = itemView.findViewById(R.id.tvSyncState);
            retryBtn = itemView.findViewById(R.id.btnRetrySync);
        }
    }
}