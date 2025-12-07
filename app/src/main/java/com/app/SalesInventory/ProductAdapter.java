package com.app.SalesInventory;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
    private final List<Product> items = new ArrayList<>();
    private final Context ctx;
    private final ProductRepository repository;
    private final AuthManager authManager;

    public ProductAdapter(Context ctx) {
        this.ctx = ctx;
        this.repository = ProductRepository.getInstance((Application) ctx.getApplicationContext());
        this.authManager = AuthManager.getInstance();
    }

    public ProductAdapter(List<Product> initialList, Context ctx) {
        this(ctx);
        if (initialList != null) {
            this.items.clear();
            this.items.addAll(initialList);
        }
    }

    public void setItems(List<Product> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void updateProducts(List<Product> list) {
        setItems(list);
    }

    public void addOrUpdate(Product p) {
        if (p == null) return;
        for (int i = 0; i < items.size(); i++) {
            Product ex = items.get(i);
            if (ex.getProductId().equals(p.getProductId())) {
                items.set(i, p);
                notifyItemChanged(i);
                return;
            }
        }
        items.add(0, p);
        notifyItemInserted(0);
    }

    public void removeByProductId(String productId) {
        if (productId == null) return;
        for (int i = 0; i < items.size(); i++) {
            if (productId.equals(items.get(i).getProductId())) {
                items.remove(i);
                notifyItemRemoved(i);
                return;
            }
        }
    }

    public void clear() {
        items.clear();
        notifyDataSetChanged();
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
        if (p == null) return;

        holder.name.setText(p.getProductName() != null ? p.getProductName() : "");
        int qty = p.getQuantity();
        holder.quantityText.setText("Stock: " + qty);
        holder.stockText.setText(String.valueOf(qty));
        holder.costPriceText.setText("Cost: ₱" + String.format(Locale.US, "%.2f", p.getCostPrice()));

        holder.floorText.setVisibility(p.getFloorLevel() > 0 ? View.VISIBLE : View.GONE);
        holder.floorText.setText("Floor: " + p.getFloorLevel());
        if (p.getFloorLevel() > 0 && qty <= p.getFloorLevel()) {
            holder.floorText.setTextColor(ctx.getResources().getColor(R.color.errorRed));
        } else {
            holder.floorText.setTextColor(ctx.getResources().getColor(R.color.textColorSecondary));
        }

        String type = p.getProductType() == null ? "" : p.getProductType();
        if (holder.sellingPriceText != null) {
            if ("Raw".equalsIgnoreCase(type)) {
                holder.sellingPriceText.setVisibility(View.GONE);
            } else {
                holder.sellingPriceText.setVisibility(View.VISIBLE);
                holder.sellingPriceText.setText("Selling: ₱" + String.format(Locale.US, "%.2f", p.getSellingPrice()));
            }
        }

        String toLoad = null;
        if (p.getImageUrl() != null && !p.getImageUrl().isEmpty()) toLoad = p.getImageUrl();
        else if (p.getImagePath() != null && !p.getImagePath().isEmpty()) toLoad = p.getImagePath();

        if (toLoad != null && !toLoad.isEmpty()) {
            Glide.with(ctx).load(toLoad).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_placeholder).centerCrop().into(holder.productImage);
        } else {
            holder.productImage.setImageResource(R.drawable.ic_image_placeholder);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent i = new Intent(ctx, ProductDetailActivity.class);
            i.putExtra("productId", p.getProductId());
            ctx.startActivity(i);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!authManager.isCurrentUserAdmin()) return true;
            new AlertDialog.Builder(ctx)
                    .setTitle("Delete Product")
                    .setMessage("Delete " + p.getProductName() + "?")
                    .setPositiveButton("Delete", (dialog, which) ->
                            repository.deleteProduct(p.getProductId(), new ProductRepository.OnProductDeletedListener() {
                                @Override
                                public void onProductDeleted(String archiveFilename) {
                                }

                                @Override
                                public void onError(String error) {
                                }
                            }))
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });

        holder.btnIncrease.setOnClickListener(v -> {
            int newQty = p.getQuantity() + 1;
            repository.updateProductQuantity(p.getProductId(), newQty, new ProductRepository.OnProductUpdatedListener() {
                @Override
                public void onProductUpdated() {
                    p.setQuantity(newQty);
                    if (ctx instanceof Activity) {
                        ((Activity) ctx).runOnUiThread(() -> {
                            holder.stockText.setText(String.valueOf(newQty));
                            holder.quantityText.setText("Stock: " + newQty);
                            if (p.getFloorLevel() > 0 && newQty <= p.getFloorLevel()) {
                                holder.floorText.setTextColor(ctx.getResources().getColor(R.color.errorRed));
                            } else {
                                holder.floorText.setTextColor(ctx.getResources().getColor(R.color.textColorSecondary));
                            }
                        });
                    }
                }

                @Override
                public void onError(String error) {
                }
            });
        });

        holder.btnDecrease.setOnClickListener(v -> {
            int current = p.getQuantity();
            if (current <= 0) return;
            int newQty = current - 1;
            repository.updateProductQuantity(p.getProductId(), newQty, new ProductRepository.OnProductUpdatedListener() {
                @Override
                public void onProductUpdated() {
                    p.setQuantity(newQty);
                    if (ctx instanceof Activity) {
                        ((Activity) ctx).runOnUiThread(() -> {
                            holder.stockText.setText(String.valueOf(newQty));
                            holder.quantityText.setText("Stock: " + newQty);
                            if (p.getFloorLevel() > 0 && newQty <= p.getFloorLevel()) {
                                holder.floorText.setTextColor(ctx.getResources().getColor(R.color.errorRed));
                            } else {
                                holder.floorText.setTextColor(ctx.getResources().getColor(R.color.textColorSecondary));
                            }
                        });
                    }
                }

                @Override
                public void onError(String error) {
                }
            });
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name;
        TextView quantityText;
        TextView costPriceText;
        TextView stockText;
        TextView sellingPriceText;
        TextView floorText;
        ImageView productImage;
        ImageButton btnIncrease;
        ImageButton btnDecrease;

        VH(@NonNull View itemView) {
            super(itemView);
            productImage = itemView.findViewById(R.id.ivProductImage);
            name = itemView.findViewById(R.id.tvProductName);
            quantityText = itemView.findViewById(R.id.tvQuantity);
            costPriceText = itemView.findViewById(R.id.tvCostPrice);
            stockText = itemView.findViewById(R.id.tvStock);
            sellingPriceText = itemView.findViewById(R.id.tvSellingPrice);
            floorText = itemView.findViewById(R.id.tvFloorLevel);
            btnIncrease = itemView.findViewById(R.id.btnIncreaseQty);
            btnDecrease = itemView.findViewById(R.id.btnDecreaseQty);
        }
    }
}