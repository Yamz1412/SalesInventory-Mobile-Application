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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// 1. UPDATED: Adapter now uses the generic RecyclerView.ViewHolder
public class SellAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    // 2. NEW: Define View Types
    private static final int VIEW_TYPE_NORMAL = 0;
    private static final int VIEW_TYPE_PROMO = 1;

    public interface OnProductClickListener {
        void onProductClick(Product product, int maxServings);
    }

    public interface OnSelectionChangeListener {
        void onSelectionChanged(Set<String> selectedIds);
    }

    private final Context ctx;
    private final List<Product> items = new ArrayList<>();
    private final List<Product> masterInventory = new ArrayList<>();
    private final Map<String, Product> fastInventoryLookup = new HashMap<>();
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
        if (inventory != null) {
            this.masterInventory.addAll(inventory);
            for (Product p : inventory) {
                if (p.getProductName() != null) {
                    this.fastInventoryLookup.put(p.getProductName().toLowerCase().trim(), p);
                }
            }
        }
        this.clickListener = clickListener;
        this.selectionChangeListener = selectionChangeListener;
        this.isFlexibleHandlingEnabled = isFlexibleHandlingEnabled;
    }

    public void filterList(List<Product> filteredList, List<Product> inventory) {
        items.clear();
        if (filteredList != null) items.addAll(filteredList);

        masterInventory.clear();
        fastInventoryLookup.clear();
        if (inventory != null) {
            masterInventory.addAll(inventory);
            for (Product p : inventory) {
                if (p.getProductName() != null) {
                    fastInventoryLookup.put(p.getProductName().toLowerCase().trim(), p);
                }
            }
        }
        notifyDataSetChanged();
    }

    public void setAdminStatus(boolean isAdmin) {
        this.isAdmin = isAdmin;
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

    @Override
    public int getItemViewType(int position) {
        Product p = items.get(position);
        if (p.isPromo() && p.getProductId() != null && p.getProductId().startsWith("PROMO_")) {
            return VIEW_TYPE_PROMO;
        }
        return VIEW_TYPE_NORMAL;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 4. NEW: Inflate the correct XML file
        if (viewType == VIEW_TYPE_PROMO) {
            View v = LayoutInflater.from(ctx).inflate(R.layout.item_promo_sell, parent, false);
            return new PromoVH(v);
        } else {
            View v = LayoutInflater.from(ctx).inflate(R.layout.productsell, parent, false);
            return new NormalVH(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Product p = items.get(position);
        int maxServings = calculateMaxServings(p);
        String currentId = p.getProductId() != null ? p.getProductId() : "local:" + p.getLocalId();

        boolean isMenuProduct = p.isSellable() ||
                "finished".equalsIgnoreCase(p.getProductType()) ||
                "Menu".equalsIgnoreCase(p.getProductType()) ||
                p.isPromo(); // Promos are inherently sellable

        // ==========================================
        // NORMAL PRODUCT BINDING
        // ==========================================
        if (holder instanceof NormalVH) {
            NormalVH normalHolder = (NormalVH) holder;
            normalHolder.name.setText(p.getProductName());
            double displayPrice = p.getSellingPrice();
            if (p.isPromo() && p.getPromoPrice() > 0) {
                displayPrice = p.getPromoPrice();
            }
            normalHolder.price.setText("₱" + String.format(Locale.US, "%.2f", displayPrice));

            if(normalHolder.code != null) normalHolder.code.setVisibility(View.GONE);

            boolean hasOptions = (p.getSizesList() != null && !p.getSizesList().isEmpty()) ||
                    (p.getAddonsList() != null && !p.getAddonsList().isEmpty()) ||
                    (p.getNotesList() != null && !p.getNotesList().isEmpty());

            if (normalHolder.indicatorDot != null) {
                if (hasOptions) {
                    normalHolder.indicatorDot.setVisibility(View.VISIBLE);
                    normalHolder.indicatorDot.setCardBackgroundColor(Color.parseColor("#4CAF50"));
                } else {
                    normalHolder.indicatorDot.setVisibility(View.GONE);
                }
            }

            normalHolder.badgeSell.setVisibility(View.GONE); // Promo handled by other VH now

            String imageToLoad = p.getImageUrl();
            if (imageToLoad == null || imageToLoad.isEmpty()) imageToLoad = p.getImagePath();

            if (ctx instanceof Activity && !((Activity) ctx).isDestroyed() && !((Activity) ctx).isFinishing()) {
                if (imageToLoad != null && !imageToLoad.isEmpty()) {
                    Glide.with(ctx).load(imageToLoad)
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .placeholder(R.drawable.ic_image_placeholder)
                            .error(R.drawable.ic_image_placeholder)
                            .centerCrop()
                            .into(normalHolder.productImage);
                } else {
                    normalHolder.productImage.setImageResource(R.drawable.ic_image_placeholder);
                }
            }

            if (!isMenuProduct || !p.isActive()) {
                normalHolder.unavailableBadge.setVisibility(View.VISIBLE);
                normalHolder.unavailableBadge.setText("Unavailable");
                normalHolder.unavailableBadge.setBackgroundColor(Color.parseColor("#D32F2F"));
                normalHolder.cardView.setAlpha(0.5f);
            } else if (maxServings <= 0) {
                normalHolder.unavailableBadge.setVisibility(View.VISIBLE);
                normalHolder.unavailableBadge.setText("Unavailable");
                normalHolder.unavailableBadge.setBackgroundColor(Color.parseColor("#D32F2F"));
                normalHolder.cardView.setAlpha(0.8f);
            } else {
                normalHolder.unavailableBadge.setVisibility(View.GONE);
                normalHolder.cardView.setAlpha(1.0f);
            }

            if (isSelectionMode && selectedIds.contains(currentId)) {
                normalHolder.cardView.setCardBackgroundColor(Color.parseColor("#40007BFF"));
                normalHolder.cardView.setStrokeColor(Color.parseColor("#4CAF50"));
                normalHolder.cardView.setStrokeWidth(4);
            } else {
                normalHolder.cardView.setCardBackgroundColor(Color.TRANSPARENT);
                normalHolder.cardView.setStrokeWidth(0);
            }

            normalHolder.itemView.setOnClickListener(v -> handleItemClick(p, maxServings, currentId));
            normalHolder.itemView.setOnLongClickListener(v -> handleLongClick(currentId));
        }

        // ==========================================
        // PROMO PRODUCT BINDING
        // ==========================================
        else if (holder instanceof PromoVH) {
            PromoVH promoHolder = (PromoVH) holder;

            String promoName = (p.getPromoName() != null && !p.getPromoName().isEmpty()) ? p.getPromoName() : p.getProductName();
            promoHolder.tvPromoName.setText(promoName);

            // Set the Discount text
            promoHolder.tvPromoDiscountBadge.setText("PROMO");

            if (p.isTemporaryPromo() && p.getPromoEndDate() > 0) {
                // Format the long timestamp into a readable date string for the UI
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                String formattedDate = sdf.format(new Date(p.getPromoEndDate()));
                promoHolder.tvPromoValidity.setText("Valid until " + formattedDate);
            } else {
                promoHolder.tvPromoValidity.setText("Ongoing Promo");
            }

            // Alpha out if unavailable
            if (!isMenuProduct || !p.isActive() || maxServings <= 0) {
                promoHolder.cardView.setAlpha(0.5f);
                promoHolder.tvPromoDiscountBadge.setText("UNAVAILABLE");
                promoHolder.tvPromoDiscountBadge.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#D32F2F")));
            } else {
                promoHolder.cardView.setAlpha(1.0f);
            }

            if (isSelectionMode && selectedIds.contains(currentId)) {
                promoHolder.cardView.setStrokeColor(Color.parseColor("#4CAF50")); // Green selected
                promoHolder.cardView.setStrokeWidth(6);
            } else {
                // To support theme colors, we reset stroke width to default
                promoHolder.cardView.setStrokeWidth(3);
            }

            promoHolder.itemView.setOnClickListener(v -> handleItemClick(p, maxServings, currentId));
            promoHolder.itemView.setOnLongClickListener(v -> handleLongClick(currentId));
        }
    }

    private void handleItemClick(Product p, int maxServings, String currentId) {
        if (isSelectionMode && isAdmin) {
            toggleSelection(currentId);
        } else {
            if (clickListener != null) {
                clickListener.onProductClick(p, maxServings);
            }
        }
    }

    private boolean handleLongClick(String currentId) {
        if (isAdmin) {
            isSelectionMode = true;
            toggleSelection(currentId);
            return true;
        }
        return false;
    }

    private int calculateMaxServings(Product menuProduct) {
        List<Map<String, Object>> bomList = menuProduct.getBomList();
        if (bomList == null || bomList.isEmpty()) {
            if (menuProduct.getQuantity() > 0) return (int) menuProduct.getQuantity();
            return 0;
        }

        int minServings = Integer.MAX_VALUE;
        for (Map<String, Object> bomItem : bomList) {
            boolean isEssential = true;
            if (bomItem.containsKey("isEssential")) {
                Object essObj = bomItem.get("isEssential");
                if (essObj instanceof Boolean) isEssential = (Boolean) essObj;
                else if (essObj instanceof String) isEssential = Boolean.parseBoolean((String) essObj);
            }
            if (!isEssential) continue;

            String matName = (String) bomItem.get("materialName");
            if (matName == null) matName = (String) bomItem.get("rawMaterialName");

            double requiredQty = 0;
            try { requiredQty = Double.parseDouble(bomItem.get("quantity").toString()); } catch (Exception ignored) {}
            if (requiredQty == 0) {
                try { requiredQty = Double.parseDouble(bomItem.get("quantityRequired").toString()); } catch (Exception ignored) {}
            }
            String reqUnit = (String) bomItem.get("unit");

            Product inventoryItem = findInventoryProduct(matName);
            if (inventoryItem == null) return 0;

            double availableQty = inventoryItem.getQuantity();
            if (availableQty <= 0) return 0;

            String invUnit = inventoryItem.getUnit() != null ? inventoryItem.getUnit() : "pcs";
            int ppu = inventoryItem.getPiecesPerUnit() > 0 ? inventoryItem.getPiecesPerUnit() : 1;

            double deductedBaseAmount = UnitConverterUtil.calculateDeductionAmount(requiredQty, invUnit, reqUnit, ppu);
            if (deductedBaseAmount <= 0) continue;

            int servings = (int) (availableQty / deductedBaseAmount);
            if (servings < minServings) minServings = servings;
        }
        return minServings == Integer.MAX_VALUE ? 0 : minServings;
    }

    private Product findInventoryProduct(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        return fastInventoryLookup.get(name.toLowerCase().trim());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private void toggleSelection(String id) {
        if (selectedIds.contains(id)) selectedIds.remove(id);
        else selectedIds.add(id);

        if (selectedIds.isEmpty()) isSelectionMode = false;
        notifyDataSetChanged();
        if (selectionChangeListener != null) selectionChangeListener.onSelectionChanged(selectedIds);
    }

    // ==========================================
    // VIEW HOLDERS
    // ==========================================

    static class NormalVH extends RecyclerView.ViewHolder {
        TextView name, code, price, unavailableBadge, badgeSell;
        ImageView productImage;
        com.google.android.material.card.MaterialCardView cardView;
        com.google.android.material.card.MaterialCardView indicatorDot;

        NormalVH(@NonNull View itemView) {
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

    // 5. NEW: Create a dedicated ViewHolder for Promo products
    static class PromoVH extends RecyclerView.ViewHolder {
        TextView tvPromoName, tvPromoDiscountBadge, tvPromoValidity;
        com.google.android.material.card.MaterialCardView cardView;

        PromoVH(@NonNull View itemView) {
            super(itemView);
            cardView = (com.google.android.material.card.MaterialCardView) itemView;
            tvPromoName = itemView.findViewById(R.id.tvPromoNameSell);
            tvPromoDiscountBadge = itemView.findViewById(R.id.tvPromoDiscountBadge);
            tvPromoValidity = itemView.findViewById(R.id.tvPromoValiditySell);
        }
    }
}