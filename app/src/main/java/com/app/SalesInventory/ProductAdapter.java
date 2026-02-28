package com.app.SalesInventory;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
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
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.File;
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
        if (list == null) {
            this.items.clear();
        } else {
            this.items.clear();
            this.items.addAll(new ArrayList<>(list));
        }
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

        // Set product name
        if (holder.name != null) {
            holder.name.setText(p.getProductName() != null ? p.getProductName() : "");
        }

        // Set quantity info
        int qty = p.getQuantity();
        if (holder.quantityText != null) {
            holder.quantityText.setText("Stock: " + qty);
        }
        if (holder.stockText != null) {
            holder.stockText.setText(String.valueOf(qty));
        }

        // Set cost price
        if (holder.costPriceText != null) {
            holder.costPriceText.setText("Cost: ₱" + String.format(Locale.US, "%.2f", p.getCostPrice()));
        }

        loadProductImage(holder.productImage, p);

        // --- ADMIN ONLY: Show the EDIT Word Box ---
        if (holder.btnEdit != null) {
            if (authManager.isCurrentUserAdmin()) {
                holder.btnEdit.setVisibility(View.VISIBLE);
                holder.btnEdit.setOnClickListener(v -> {
                    Intent intent = new Intent(ctx, EditProduct.class);
                    intent.putExtra("productId", p.getProductId());
                    ctx.startActivity(intent);
                });
            } else {
                holder.btnEdit.setVisibility(View.GONE);
            }
        }

        // --- SHORT CLICK (VIEW DETAILS) ---
        holder.itemView.setOnClickListener(v -> {
            Intent i = new Intent(ctx, ProductDetailActivity.class);
            i.putExtra("productId", p.getProductId());
            ctx.startActivity(i);
        });

        // --- LONG CLICK (DELETE ONLY - ADMIN ONLY) ---
        holder.itemView.setOnLongClickListener(v -> {
            if (!authManager.isCurrentUserAdmin()) return true;
            new AlertDialog.Builder(ctx)
                    .setTitle("Delete Product")
                    .setMessage("Are you sure you want to delete " + p.getProductName() + "?")
                    .setPositiveButton("Delete", (dialog, which) ->
                            repository.deleteProduct(p.getProductId(), new ProductRepository.OnProductDeletedListener() {
                                @Override public void onProductDeleted(String archiveFilename) {}
                                @Override public void onError(String error) {}
                            }))
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });

        // --- PLUS BUTTON (INCREASE STOCK) ---
        if (holder.btnIncrease != null) {
            holder.btnIncrease.setOnClickListener(v -> {
                int newQty = p.getQuantity() + 1;
                repository.updateProductQuantity(p.getProductId(), newQty, new ProductRepository.OnProductUpdatedListener() {
                    @Override
                    public void onProductUpdated() {
                        p.setQuantity(newQty);
                        if (ctx instanceof Activity) {
                            ((Activity) ctx).runOnUiThread(() -> {
                                if (holder.stockText != null) holder.stockText.setText(String.valueOf(newQty));
                                if (holder.quantityText != null) holder.quantityText.setText("Stock: " + newQty);
                            });
                        }
                    }
                    @Override public void onError(String error) {}
                });
            });
        }

        // --- MINUS BUTTON (DECREASE STOCK) ---
        if (holder.btnDecrease != null) {
            holder.btnDecrease.setOnClickListener(v -> {
                int current = p.getQuantity();
                if (current <= 0) return; // Prevent negative stock
                int newQty = current - 1;
                repository.updateProductQuantity(p.getProductId(), newQty, new ProductRepository.OnProductUpdatedListener() {
                    @Override
                    public void onProductUpdated() {
                        p.setQuantity(newQty);
                        if (ctx instanceof Activity) {
                            ((Activity) ctx).runOnUiThread(() -> {
                                if (holder.stockText != null) holder.stockText.setText(String.valueOf(newQty));
                                if (holder.quantityText != null) holder.quantityText.setText("Stock: " + newQty);
                            });
                        }
                    }
                    @Override public void onError(String error) {}
                });
            });
        }
    }

    private void loadProductImage(ImageView imageView, Product product) {
        if (imageView == null || product == null) return;
        String localPath = product.getImagePath();
        String onlineUrl = product.getImageUrl();

        if (localPath != null && !localPath.isEmpty()) {
            File localFile = new File(localPath);
            if (localFile.exists() && localFile.canRead()) {
                Glide.with(ctx).load(localFile).thumbnail(0.1f).override(300, 300)
                        .diskCacheStrategy(DiskCacheStrategy.ALL).placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder).centerCrop().into(imageView);
                return;
            }
        }
        if (onlineUrl != null && !onlineUrl.isEmpty()) {
            Glide.with(ctx).load(onlineUrl).thumbnail(0.1f).override(300, 300)
                    .diskCacheStrategy(DiskCacheStrategy.ALL).placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder).centerCrop().into(imageView);
            return;
        }
        imageView.setImageResource(R.drawable.ic_image_placeholder);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, quantityText, costPriceText, stockText, btnEdit; // btnEdit mapped as TextView box
        ImageView productImage;
        ImageButton btnIncrease, btnDecrease;

        VH(@NonNull View itemView) {
            super(itemView);
            productImage = itemView.findViewById(R.id.ivProductImage);
            name = itemView.findViewById(R.id.tvProductName);
            quantityText = itemView.findViewById(R.id.tvQuantity);
            costPriceText = itemView.findViewById(R.id.tvCostPrice);
            stockText = itemView.findViewById(R.id.tvStock);

            // Connect to XML Buttons
            btnDecrease = itemView.findViewById(R.id.btnDecreaseQty);
            btnIncrease = itemView.findViewById(R.id.btnIncreaseQty);
            btnEdit = itemView.findViewById(R.id.btnEdit); // Text Box
        }
    }
}