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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.VH> {
    private final List<Product> items = new ArrayList<>();
    private final Context ctx;
    private final ProductRepository repository;
    private final AuthManager authManager;

    private boolean isReadOnly = false;

    // --- NEW: MULTI-SELECTION VARIABLES ---
    private Set<String> selectedIds = new HashSet<>();
    private boolean isSelectionMode = false;

    public interface OnSelectionChangeListener {
        void onSelectionChanged(Set<String> selectedIds);
    }
    private OnSelectionChangeListener selectionChangeListener;
    // --------------------------------------

    public ProductAdapter(List<Product> initialItems, Context ctx) {
        this.ctx = ctx;
        if (initialItems != null) {
            this.items.addAll(initialItems);
        }
        this.repository = ProductRepository.getInstance((Application) ctx.getApplicationContext());
        this.authManager = AuthManager.getInstance();
    }

    public void setReadOnly(boolean readOnly) {
        this.isReadOnly = readOnly;
        notifyDataSetChanged();
    }

    public void updateProducts(List<Product> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    // --- NEW: MULTI-SELECTION METHODS ---
    public void setSelectionListener(OnSelectionChangeListener listener) {
        this.selectionChangeListener = listener;
    }

    public void setSelectionMode(boolean enabled) {
        isSelectionMode = enabled;
        if (!enabled) {
            selectedIds.clear();
        }
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedIds);
        }
    }

    public Set<String> getSelectedIds() {
        return selectedIds;
    }

    public void selectAll() {
        selectedIds.clear();
        for (Product p : items) {
            if (p != null && p.getProductId() != null) {
                selectedIds.add(p.getProductId());
            }
        }
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedIds);
        }
    }

    public void clearSelection() {
        selectedIds.clear();
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedIds);
        }
    }

    public void toggleSelection(String productId) {
        if (selectedIds.contains(productId)) {
            selectedIds.remove(productId);
        } else {
            selectedIds.add(productId);
        }
        notifyDataSetChanged();
        if (selectionChangeListener != null) {
            selectionChangeListener.onSelectionChanged(selectedIds);
        }
    }
    // --------------------------------------

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

        holder.name.setText(p.getProductName() != null ? p.getProductName() : "Unnamed");

        // =======================================================
        // FIX: HIDE SELL PRICE IF IT IS 0.00
        // =======================================================
        if (p.getSellingPrice() > 0) {
            // Show both Cost and Sell if there is a selling price
            holder.costPriceText.setText(String.format(Locale.US, "Cost: ₱%,.2f | Sell: ₱%,.2f", p.getCostPrice(), p.getSellingPrice()));
        } else {
            // Show ONLY Cost if selling price is 0
            holder.costPriceText.setText(String.format(Locale.US, "Cost: ₱%,.2f", p.getCostPrice()));
        }
        // =======================================================

        // --- NEW: Visual feedback for selection ---
        if (isSelectionMode) {
            if (selectedIds.contains(p.getProductId())) {
                holder.itemView.setBackgroundColor(Color.parseColor("#E0F7FA")); // Light blue highlight
            } else {
                holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            }
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
        }
        // ------------------------------------------

        String currentQtyStr = (p.getQuantity() % 1 == 0) ? String.valueOf((long) p.getQuantity()) : String.valueOf(p.getQuantity());
        String unitStr = p.getUnit() != null ? p.getUnit() : "pcs";
        holder.stockText.setText("Stock: " + currentQtyStr + " " + unitStr);

        boolean isLowStock = p.getQuantity() <= p.getReorderLevel();
        holder.stockText.setTextColor(isLowStock ? Color.RED : Color.parseColor("#4CAF50")); // Green if ok

        if (isReadOnly) {
            holder.btnEdit.setVisibility(View.GONE);
            holder.btnIncrease.setVisibility(View.GONE);
            holder.btnDecrease.setVisibility(View.GONE);
            holder.quantityText.setVisibility(View.GONE);
        } else {
            holder.btnEdit.setVisibility(View.VISIBLE);
            holder.btnIncrease.setVisibility(View.VISIBLE);
            holder.btnDecrease.setVisibility(View.VISIBLE);
            holder.quantityText.setVisibility(View.VISIBLE);

            holder.btnIncrease.setOnClickListener(v -> handleStockChange(p, holder, 1));
            holder.btnDecrease.setOnClickListener(v -> handleStockChange(p, holder, -1));

            holder.btnEdit.setOnClickListener(v -> {
                Intent i = new Intent(ctx, EditProduct.class);
                i.putExtra("PRODUCT_ID", p.getProductId());
                ctx.startActivity(i);
            });
        }

        loadImageWithOfflineFallback(p.getImageUrl(), p.getImagePath(), holder.productImage);

        // --- NEW: Toggle selection on click if in selection mode ---
        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(p.getProductId());
            } else {
                Intent i = new Intent(ctx, ProductDetailActivity.class);
                i.putExtra("PRODUCT_ID", p.getProductId());
                ctx.startActivity(i);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (isReadOnly) return true;

            if (!isSelectionMode) {
                setSelectionMode(true);
                toggleSelection(p.getProductId());
                return true;
            }
            return false;
        });
    }

    private void handleStockChange(Product p, VH holder, int change) {
        if (!authManager.isCurrentUserApproved()) {
            Toast.makeText(ctx, "You don't have permission to update stock.", Toast.LENGTH_SHORT).show();
            return;
        }

        double currentStock = p.getQuantity();
        double newStock = currentStock + change;
        if (newStock < 0) {
            Toast.makeText(ctx, "Stock cannot be negative", Toast.LENGTH_SHORT).show();
            return;
        }

        p.setQuantity(newStock);
        String updatedQtyStr = (newStock % 1 == 0) ? String.valueOf((long) newStock) : String.valueOf(newStock);
        String unitStr = p.getUnit() != null ? p.getUnit() : "pcs";
        holder.stockText.setText("Stock: " + updatedQtyStr + " " + unitStr);

        boolean isLowStock = newStock <= p.getReorderLevel();
        holder.stockText.setTextColor(isLowStock ? Color.RED : Color.parseColor("#4CAF50"));

        repository.updateProductQuantity(p.getProductId(), newStock, new ProductRepository.OnProductUpdatedListener() {
            @Override
            public void onProductUpdated() {
                if (ctx instanceof Activity) {
                    ((Activity) ctx).runOnUiThread(() -> notifyDataSetChanged());
                }
            }
            @Override
            public void onError(String error) {
                if (ctx instanceof Activity) {
                    ((Activity) ctx).runOnUiThread(() -> Toast.makeText(ctx, "Update failed: " + error, Toast.LENGTH_SHORT).show());
                }
            }
        });
    }

    private void loadImageWithOfflineFallback(String firebaseUrl, String localPath, ImageView imageView) {
        if (firebaseUrl != null && !firebaseUrl.isEmpty()) {
            loadFromUrlWithFallback(firebaseUrl, localPath, imageView);
        } else if (localPath != null && !localPath.isEmpty()) {
            File imgFile = new File(localPath);
            if (imgFile.exists()) {
                Glide.with(ctx)
                        .load(imgFile)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .override(200, 200)
                        .centerCrop()
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .into(imageView);
            } else {
                imageView.setImageResource(R.drawable.ic_image_placeholder);
            }
        } else {
            imageView.setImageResource(R.drawable.ic_image_placeholder);
        }
    }

    private void loadFromUrlWithFallback(String url, String localPath, ImageView imageView) {
        Glide.with(ctx)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .override(200, 200)
                .centerCrop()
                .placeholder(R.drawable.ic_image_placeholder)
                .timeout(3000) // Fast timeout so it doesn't hang if offline
                .error(
                        // THE OFFLINE FALLBACK: If the URL fails because of no internet, try the local phone gallery path
                        Glide.with(ctx)
                                .load(localPath)
                                .diskCacheStrategy(DiskCacheStrategy.ALL)
                                .override(200, 200)
                                .centerCrop()
                                .error(R.drawable.ic_image_placeholder) // If both fail, show the placeholder
                )
                .into(imageView);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, quantityText, costPriceText, stockText;
        ImageView productImage;
        ImageButton btnIncrease, btnDecrease, btnEdit;

        VH(@NonNull View itemView) {
            super(itemView);
            productImage = itemView.findViewById(R.id.ivProductImage);
            name = itemView.findViewById(R.id.tvProductName);
            quantityText = itemView.findViewById(R.id.tvQuantity);
            costPriceText = itemView.findViewById(R.id.tvCostPrice);
            stockText = itemView.findViewById(R.id.tvStock);
            btnEdit = itemView.findViewById(R.id.btnEdit);
            btnDecrease = itemView.findViewById(R.id.btnDecreaseQty);
            btnIncrease = itemView.findViewById(R.id.btnIncreaseQty);
        }
    }
}