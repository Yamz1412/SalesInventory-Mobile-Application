package com.app.SalesInventory;

import android.app.Activity;
import android.content.Context;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class SellAdapter extends RecyclerView.Adapter<SellAdapter.VH> {

    public interface OnProductClickListener {
        void onProductClick(Product product, int maxServings);
    }

    public interface OnSelectionChangeListener {
        void onSelectionChanged(Set<String> selectedIds);
    }

    private final Context ctx;
    private final List<Product> items = new ArrayList<>();
    private final List<Product> masterInventory = new ArrayList<>();
    private OnProductClickListener clickListener;
    private OnSelectionChangeListener selectionChangeListener;

    private Set<String> selectedIds = new HashSet<>();
    private boolean isSelectionMode = false;
    private boolean isAdmin = false;
    private boolean isFlexibleHandlingEnabled;

    // UPDATED: Added isFlexibleHandlingEnabled to Constructor
    public SellAdapter(Context ctx, List<Product> initial, List<Product> inventory, OnProductClickListener clickListener, OnSelectionChangeListener selectionChangeListener, boolean isFlexibleHandlingEnabled) {
        this.ctx = ctx;
        if (initial != null) this.items.addAll(initial);
        if (inventory != null) this.masterInventory.addAll(inventory);
        this.clickListener = clickListener;
        this.selectionChangeListener = selectionChangeListener;
        this.isFlexibleHandlingEnabled = isFlexibleHandlingEnabled;
    }

    public void setAdminStatus(boolean isAdmin) {
        this.isAdmin = isAdmin;
        notifyDataSetChanged();
    }

    public void filterList(List<Product> filtered, List<Product> inventory) {
        this.items.clear();
        if (filtered != null) this.items.addAll(filtered);

        this.masterInventory.clear();
        if (inventory != null) this.masterInventory.addAll(inventory);

        notifyDataSetChanged();
    }

    private Product findInventoryProduct(String name) {
        for (Product p : masterInventory) {
            if (p.getProductName() != null && p.getProductName().equalsIgnoreCase(name)) return p;
        }
        return null;
    }

    private int calculateMaxServings(Product menuProduct) {
        int baseMaxServings = 9999;

        if (menuProduct.getBomList() != null && !menuProduct.getBomList().isEmpty()) {
            baseMaxServings = Integer.MAX_VALUE;
            for (Map<String, Object> bomItem : menuProduct.getBomList()) {
                boolean isEssential = true;
                if (bomItem.containsKey("isEssential")) {
                    Object essObj = bomItem.get("isEssential");
                    if (essObj instanceof Boolean) isEssential = (Boolean) essObj;
                    else if (essObj instanceof String) isEssential = Boolean.parseBoolean((String) essObj);
                }

                if (!isEssential) continue;

                String matName = (String) bomItem.get("materialName");
                double reqQty = 0;
                try { reqQty = Double.parseDouble(String.valueOf(bomItem.get("quantity"))); } catch (Exception ignored) {}
                String reqUnit = (String) bomItem.get("unit");

                Product rawMat = findInventoryProduct(matName);
                if (rawMat == null || reqQty <= 0) {
                    return 0;
                }

                int ppu = rawMat.getPiecesPerUnit() > 0 ? rawMat.getPiecesPerUnit() : 1;
                String invUnit = rawMat.getUnit() != null ? rawMat.getUnit() : "pcs";

                Object[] conversion = UnitConverterUtil.convertBaseInventoryUnit(rawMat.getQuantity(), invUnit, reqUnit, ppu);
                double convertedInvQty = (double) conversion[0];
                String newInvUnit = (String) conversion[1];

                double actualReqQty = UnitConverterUtil.calculateDeductionAmount(reqQty, newInvUnit, reqUnit, ppu);

                if (actualReqQty > 0) {
                    int possibleServings = (int) (convertedInvQty / actualReqQty);
                    if (possibleServings < baseMaxServings) {
                        baseMaxServings = possibleServings;
                    }
                }
            }
            if (baseMaxServings == Integer.MAX_VALUE) baseMaxServings = 0;
        }

        if (baseMaxServings == 0) return 0;

        if (menuProduct.getSizesList() != null && !menuProduct.getSizesList().isEmpty()) {
            int maxServingsFromSizes = 0;

            for (Map<String, Object> sizeItem : menuProduct.getSizesList()) {
                String linkedMat = (String) sizeItem.get("linkedMaterial");
                double deductQty = 0;
                try { deductQty = Double.parseDouble(String.valueOf(sizeItem.get("deductQty"))); } catch (Exception ignored) {}

                if (linkedMat == null || linkedMat.isEmpty() || deductQty <= 0) {
                    maxServingsFromSizes = 9999;
                    break;
                }

                Product sizeMat = findInventoryProduct(linkedMat);
                if (sizeMat != null) {
                    int possibleCups = (int) (sizeMat.getQuantity() / deductQty);
                    if (possibleCups > maxServingsFromSizes) {
                        maxServingsFromSizes = possibleCups;
                    }
                }
            }
            return Math.min(baseMaxServings, maxServingsFromSizes);
        }

        return baseMaxServings;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.productsell, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Product p = items.get(position);

        holder.name.setText(p.getProductName());
        holder.code.setText(p.getCategoryName());
        holder.price.setText(String.format(Locale.US, "₱%.2f", p.getSellingPrice()));

        boolean hasOptions = (p.getSizesList() != null && !p.getSizesList().isEmpty()) ||
                (p.getAddonsList() != null && !p.getAddonsList().isEmpty()) ||
                (p.getNotesList() != null && !p.getNotesList().isEmpty());

        if (p.isPromo()) { // NEW PROMO BADGE LOGIC
            holder.badgeSell.setVisibility(View.VISIBLE);
            holder.badgeSell.setText(p.getPromoName() != null && !p.getPromoName().isEmpty() ? p.getPromoName() : "Promo");
            holder.badgeSell.setTextColor(Color.WHITE);
            holder.badgeSell.setBackgroundColor(Color.parseColor("#E91E63")); // A nice Pink/Red color for promos
            holder.badgeSell.setPadding(12, 4, 12, 4);
        } else if (hasOptions) {
            holder.badgeSell.setVisibility(View.VISIBLE);
            holder.badgeSell.setText("Customizable");
            holder.badgeSell.setTextColor(Color.WHITE);
            holder.badgeSell.setBackgroundColor(Color.parseColor("#FF9800"));
            holder.badgeSell.setPadding(12, 4, 12, 4);
        } else {
            holder.badgeSell.setVisibility(View.GONE);
        }

        String imageToLoad = p.getImageUrl();
        if (imageToLoad == null || imageToLoad.isEmpty()) {
            imageToLoad = p.getImagePath();
        }

        if (ctx instanceof Activity) {
            if (((Activity) ctx).isDestroyed() || ((Activity) ctx).isFinishing()) return;
        }

        if (imageToLoad != null && !imageToLoad.isEmpty()) {
            Glide.with(ctx)
                    .load(imageToLoad)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(holder.productImage);
        } else {
            holder.productImage.setImageResource(R.drawable.ic_image_placeholder);
        }

        int maxServings = calculateMaxServings(p);
        boolean isAvailable = maxServings > 0;

        // NEW: Check if the product can be clicked based on the flexible handling setting
        boolean canClick = isAvailable || isFlexibleHandlingEnabled;

        if (!isAvailable) {
            holder.productImage.setAlpha(0.3f);
            if (holder.unavailableBadge != null) holder.unavailableBadge.setVisibility(View.VISIBLE);
        } else {
            holder.productImage.setAlpha(1.0f);
            if (holder.unavailableBadge != null) holder.unavailableBadge.setVisibility(View.GONE);
        }

        String currentId = p.getProductId() != null ? p.getProductId() : "local:" + p.getLocalId();

        // UPDATED: Use a translucent overlay for selection so it doesn't break the dynamic XML theme,
        // and set it to transparent when not selected to let the app:cardBackgroundColor show.
        if (selectedIds.contains(currentId)) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#40007BFF")); // Translucent selection overlay
        } else {
            holder.cardView.setCardBackgroundColor(Color.TRANSPARENT);
        }

        // UPDATED: Use 'canClick' instead of 'isAvailable' to allow selections when flexible handling is true
        holder.itemView.setOnClickListener(v -> {
            if (!canClick) return;
            if (isSelectionMode) {
                toggleSelection(currentId);
            } else {
                if (clickListener != null) clickListener.onProductClick(p, maxServings);
            }
        });

        // UPDATED: Use 'canClick' instead of 'isAvailable'
        holder.itemView.setOnLongClickListener(v -> {
            if (!canClick) return false;
            if (!isSelectionMode) {
                isSelectionMode = true;
                toggleSelection(currentId);
                return true;
            }
            return false;
        });
    }

    public void clearSelection() {
        selectedIds.clear();
        isSelectionMode = false;
        notifyDataSetChanged();
        if (selectionChangeListener != null) selectionChangeListener.onSelectionChanged(selectedIds);
    }

    private void toggleSelection(String id) {
        if (selectedIds.contains(id)) selectedIds.remove(id);
        else selectedIds.add(id);

        if (selectedIds.isEmpty()) isSelectionMode = false;
        notifyDataSetChanged();
        if (selectionChangeListener != null) selectionChangeListener.onSelectionChanged(selectedIds);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, code, price, unavailableBadge, badgeSell;
        ImageView productImage;
        com.google.android.material.card.MaterialCardView cardView;

        VH(@NonNull View itemView) {
            super(itemView);
            cardView = (com.google.android.material.card.MaterialCardView) itemView;
            productImage = itemView.findViewById(R.id.ivProductImageSell);
            name = itemView.findViewById(R.id.NameTVS11);
            code = itemView.findViewById(R.id.CodeTVS11);
            price = itemView.findViewById(R.id.SellPriceTVS11);
            unavailableBadge = itemView.findViewById(R.id.tvUnavailableBadge);
            badgeSell = itemView.findViewById(R.id.BadgeSell);
        }
    }
}