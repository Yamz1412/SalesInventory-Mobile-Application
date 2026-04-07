package com.app.SalesInventory;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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

    public SellAdapter(Context ctx, List<Product> initialItems, List<Product> inventory,
                       OnProductClickListener clickListener, OnSelectionChangeListener selectionChangeListener,
                       boolean isFlexibleHandlingEnabled) {
        this.ctx = ctx;
        if (initialItems != null) this.items.addAll(initialItems);
        if (inventory != null) this.masterInventory.addAll(inventory);
        this.clickListener = clickListener;
        this.selectionChangeListener = selectionChangeListener;
        this.isFlexibleHandlingEnabled = isFlexibleHandlingEnabled;
    }

    public void setAdminStatus(boolean isAdmin) {
        this.isAdmin = isAdmin;
        notifyDataSetChanged();
    }

    public void filterList(List<Product> filteredList, List<Product> inventory) {
        items.clear();
        if (filteredList != null) items.addAll(filteredList);

        masterInventory.clear();
        if (inventory != null) masterInventory.addAll(inventory);

        notifyDataSetChanged();
    }

    public Set<String> getSelectedIds() {
        return selectedIds;
    }

    public void clearSelection() {
        selectedIds.clear();
        isSelectionMode = false;
        notifyDataSetChanged();
        if (selectionChangeListener != null) selectionChangeListener.onSelectionChanged(selectedIds);
    }

    public void setSelectionMode(boolean enabled) {
        isSelectionMode = enabled;
        if (!enabled) selectedIds.clear();
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
        holder.price.setText("₱" + String.format(Locale.US, "%.2f", p.getSellingPrice()));

        // Hide explicit code view for a cleaner POS UI
        if(holder.code != null) holder.code.setVisibility(View.GONE);

        // Options indicator dot logic
        boolean hasOptions = (p.getSizesList() != null && !p.getSizesList().isEmpty()) ||
                (p.getAddonsList() != null && !p.getAddonsList().isEmpty()) ||
                (p.getNotesList() != null && !p.getNotesList().isEmpty());

        if (holder.indicatorDot != null) {
            if (hasOptions) {
                holder.indicatorDot.setVisibility(View.VISIBLE);
                holder.indicatorDot.setCardBackgroundColor(Color.parseColor("#4CAF50")); // Green Dot
            } else {
                holder.indicatorDot.setVisibility(View.GONE);
            }
        }

        // Promo badge logic
        if (p.isPromo()) {
            holder.badgeSell.setVisibility(View.VISIBLE);
            holder.badgeSell.setText(p.getPromoName() != null && !p.getPromoName().isEmpty() ? p.getPromoName() : "Promo");
            holder.badgeSell.setTextColor(Color.WHITE);
            holder.badgeSell.setBackgroundColor(Color.parseColor("#E91E63")); // Pink/Red for promos
            holder.badgeSell.setPadding(12, 4, 12, 4);
        } else {
            holder.badgeSell.setVisibility(View.GONE);
        }

        // Image Loading
        String imageToLoad = p.getImageUrl();
        if (imageToLoad == null || imageToLoad.isEmpty()) imageToLoad = p.getImagePath();

        if (ctx instanceof Activity) {
            if (((Activity) ctx).isDestroyed() || ((Activity) ctx).isFinishing()) return;
        }

        if (imageToLoad != null && !imageToLoad.isEmpty()) {
            Glide.with(ctx).load(imageToLoad)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(holder.productImage);
        } else {
            holder.productImage.setImageResource(R.drawable.ic_image_placeholder);
        }

        // Inventory checking for max servings
        int maxServings = calculateMaxServings(p);

        if (!p.isSellable() || !p.isActive()) {
            holder.unavailableBadge.setVisibility(View.VISIBLE);
            holder.unavailableBadge.setText("Unavailable");
            holder.unavailableBadge.setBackgroundColor(Color.parseColor("#D32F2F")); // Red
            holder.cardView.setAlpha(0.5f);
        } else if (maxServings <= 0) {
            holder.unavailableBadge.setVisibility(View.VISIBLE);
            if(isAdmin) {
                holder.unavailableBadge.setText("Out of Stock");
                holder.unavailableBadge.setBackgroundColor(Color.parseColor("#D32F2F")); // Red
            } else {
                // Cashier Override Warning
                holder.unavailableBadge.setText("Ingredient Missing");
                holder.unavailableBadge.setBackgroundColor(Color.parseColor("#FFA500")); // Orange Warning
            }
            holder.cardView.setAlpha(0.8f);
        } else {
            holder.unavailableBadge.setVisibility(View.GONE);
            holder.cardView.setAlpha(1.0f);
        }

        // Selection mode UI changes
        String currentId = p.getProductId() != null ? p.getProductId() : "local:" + p.getLocalId();
        if (isSelectionMode && selectedIds.contains(currentId)) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#40007BFF")); // Translucent overlay
            holder.cardView.setStrokeColor(Color.parseColor("#4CAF50")); // Green stroke
            holder.cardView.setStrokeWidth(4);
        } else {
            holder.cardView.setCardBackgroundColor(Color.TRANSPARENT);
            holder.cardView.setStrokeWidth(0);
        }

        // --- CLICK LISTENERS ---
        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                if (isAdmin) toggleSelection(currentId);
            } else {
                if (maxServings <= 0 && isAdmin && !isFlexibleHandlingEnabled) {
                    Toast.makeText(ctx, "Out of stock. Cannot sell.", Toast.LENGTH_SHORT).show();
                } else if (clickListener != null) {
                    clickListener.onProductClick(p, maxServings);
                }
            }
        });

        // Long Press to trigger selection mode (Admin Only)
        holder.itemView.setOnLongClickListener(v -> {
            if (isAdmin) {
                isSelectionMode = true;
                toggleSelection(currentId);
                return true;
            }
            return false;
        });
    }

    private int calculateMaxServings(Product menuProduct) {
        if (menuProduct.getBomList() == null || menuProduct.getBomList().isEmpty()) {
            return 999;
        }

        int minServings = Integer.MAX_VALUE;
        for (Map<String, Object> bomItem : menuProduct.getBomList()) {
            // Check if ingredient is essential (blocks sale if missing)
            boolean isEssential = true;
            if (bomItem.containsKey("isEssential")) {
                Object essObj = bomItem.get("isEssential");
                if (essObj instanceof Boolean) isEssential = (Boolean) essObj;
                else if (essObj instanceof String) isEssential = Boolean.parseBoolean((String) essObj);
            }

            if (!isEssential) continue;

            // Extract values based on item_config_bom.xml structure
            String matName = (String) bomItem.get("materialName");
            if (matName == null) matName = (String) bomItem.get("rawMaterialName");

            double requiredQty = 0;
            try { requiredQty = Double.parseDouble(bomItem.get("quantity").toString()); } catch (Exception e) {}
            if (requiredQty == 0) {
                try { requiredQty = Double.parseDouble(bomItem.get("quantityRequired").toString()); } catch (Exception e) {}
            }

            String reqUnit = (String) bomItem.get("unit");

            Product inventoryItem = findInventoryProduct(matName);
            if (inventoryItem == null) return 0;

            double availableQty = inventoryItem.getQuantity();
            if (availableQty <= 0) return 0;

            String invUnit = inventoryItem.getUnit() != null ? inventoryItem.getUnit() : "pcs";
            int ppu = inventoryItem.getPiecesPerUnit() > 0 ? inventoryItem.getPiecesPerUnit() : 1;

            // Handle unit conversions (e.g. Kg to Grams)
            double deductedBaseAmount = UnitConverterUtil.calculateDeductionAmount(requiredQty, invUnit, reqUnit, ppu);
            if (deductedBaseAmount <= 0) continue;

            int servings = (int) (availableQty / deductedBaseAmount);
            if (servings < minServings) minServings = servings;
        }

        return minServings == Integer.MAX_VALUE ? 0 : minServings;
    }

    private Product findInventoryProduct(String name) {
        if (name == null) return null;
        for (Product p : masterInventory) {
            if (p.getProductName() != null && p.getProductName().equalsIgnoreCase(name)) {
                return p;
            }
        }
        return null;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, code, price, unavailableBadge, badgeSell;
        ImageView productImage;
        com.google.android.material.card.MaterialCardView cardView;
        com.google.android.material.card.MaterialCardView indicatorDot;

        VH(@NonNull View itemView) {
            super(itemView);
            cardView = (com.google.android.material.card.MaterialCardView) itemView;
            productImage = itemView.findViewById(R.id.ivProductImageSell);
            name = itemView.findViewById(R.id.NameTVS11);
            code = itemView.findViewById(R.id.CodeTVS11);
            price = itemView.findViewById(R.id.SellPriceTVS11);
            unavailableBadge = itemView.findViewById(R.id.tvUnavailableBadge);
            badgeSell = itemView.findViewById(R.id.BadgeSell);
            indicatorDot = itemView.findViewById(R.id.indicatorDot);
        }
    }
}