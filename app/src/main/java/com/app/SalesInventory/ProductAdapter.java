package com.app.SalesInventory;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public class ProductAdapter extends ListAdapter<Product, ProductAdapter.VH> {
    private final Context ctx;
    private final ProductRepository repository;
    private final AuthManager authManager;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public interface OnProductOpenListener {
        void onOpen(String productId);
    }

    public interface OnProductLongClickListener {
        void onLongClick(Product p, int position);
    }

    private OnProductOpenListener openListener;
    private OnProductLongClickListener longClickListener;

    public ProductAdapter(Context ctx) {
        super(DIFF);
        this.ctx = ctx;
        this.repository = ProductRepository.getInstance((Application) ctx.getApplicationContext());
        this.authManager = AuthManager.getInstance();
    }

    public void setOnProductOpenListener(OnProductOpenListener l) {
        this.openListener = l;
    }

    public void setOnProductLongClickListener(OnProductLongClickListener l) {
        this.longClickListener = l;
    }

    public void submitSortedList(List<Product> list) {
        if (list == null) {
            submitList(new ArrayList<>());
            return;
        }
        List<Product> deduped = dedupeKeepLast(list);
        List<Product> copy = new ArrayList<>(deduped);
        Collections.sort(copy, new Comparator<Product>() {
            @Override
            public int compare(Product a, Product b) {
                String an = a.getProductName() == null ? "" : a.getProductName().toLowerCase(Locale.ROOT);
                String bn = b.getProductName() == null ? "" : b.getProductName().toLowerCase(Locale.ROOT);
                return an.compareTo(bn);
            }
        });
        submitList(copy);
    }

    public void addOrUpdate(Product p) {
        if (p == null) return;
        List<Product> cur = new ArrayList<>(getCurrentList());
        int idx = indexOfProduct(cur, p.getProductId());
        if (idx >= 0) {
            cur.set(idx, copyProductWithQuantity(p, p.getQuantity()));
        } else {
            cur.add(p);
        }
        List<Product> deduped = dedupeKeepLast(cur);
        submitList(deduped);
    }

    public void updateSingleProductQuantity(String productId, int newQty) {
        List<Product> cur = new ArrayList<>(getCurrentList());
        int idx = indexOfProduct(cur, productId);
        if (idx < 0) return;
        Product base = cur.get(idx);
        Product updated = copyProductWithQuantity(base, newQty);
        cur.set(idx, updated);
        List<Product> deduped = dedupeKeepLast(cur);
        submitList(deduped);
    }

    public void updateSingleProductQuantityByLocalId(long localId, int newQty) {
        List<Product> cur = new ArrayList<>(getCurrentList());
        int idx = -1;
        for (int i = 0; i < cur.size(); i++) {
            Product t = cur.get(i);
            if (t != null && t.getLocalId() == localId) { idx = i; break; }
        }
        if (idx < 0) return;
        Product base = cur.get(idx);
        Product updated = copyProductWithQuantity(base, newQty);
        cur.set(idx, updated);
        List<Product> deduped = dedupeKeepLast(cur);
        submitList(deduped);
    }

    public void removeByProductId(String productId) {
        if (productId == null) return;
        List<Product> cur = new ArrayList<>(getCurrentList());
        int idx = indexOfProduct(cur, productId);
        if (idx >= 0) {
            cur.remove(idx);
            List<Product> deduped = dedupeKeepLast(cur);
            submitList(deduped);
        }
    }

    private int indexOfProduct(List<Product> list, String productId) {
        if (productId != null && !productId.trim().isEmpty()) {
            for (int i = 0; i < list.size(); i++) {
                if (productId.equals(list.get(i).getProductId())) return i;
            }
            return -1;
        } else {
            for (int i = 0; i < list.size(); i++) {
                Product p = list.get(i);
                if (p != null && (p.getProductId() == null || p.getProductId().trim().isEmpty())) return i;
            }
            return -1;
        }
    }

    private Product copyProductWithQuantity(Product p, int qty) {
        Map<String, Object> m = p.toMap();
        m.put("quantity", qty);
        m.put("lastUpdated", System.currentTimeMillis());
        return Product.fromMap(m);
    }

    private List<Product> dedupeKeepLast(List<Product> list) {
        LinkedHashMap<String, Product> map = new LinkedHashMap<>();
        for (Product p : list) {
            if (p == null) continue;
            String key = null;
            if (p.getProductId() != null && !p.getProductId().trim().isEmpty()) {
                key = "id:" + p.getProductId();
            } else {
                key = "local:" + p.getLocalId();
            }
            map.put(key, p);
        }
        return new ArrayList<>(map.values());
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_inventory, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Product p = getItem(position);
        if (p == null || holder == null) return;
        String name = p.getProductName() == null ? "" : p.getProductName();
        int qty = p.getQuantity();
        if (holder.name != null) holder.name.setText(name);
        if (holder.quantityText != null) holder.quantityText.setText("Stock: " + qty);
        if (holder.costPriceText != null) holder.costPriceText.setText("Cost: ₱" + String.format(Locale.US, "%.2f", p.getCostPrice()));
        if (holder.floorText != null) holder.floorText.setVisibility(View.GONE);
        if (holder.sellingPriceText != null) holder.sellingPriceText.setVisibility(View.GONE);
        if (holder.stockText != null) holder.stockText.setVisibility(View.GONE);
        String toLoad = null;
        if (p.getImageUrl() != null && !p.getImageUrl().isEmpty()) toLoad = p.getImageUrl();
        else if (p.getImagePath() != null && !p.getImagePath().isEmpty()) toLoad = p.getImagePath();
        if (holder.productImage != null) {
            if (toLoad != null && !toLoad.isEmpty()) {
                boolean loaded = false;
                try {
                    Glide.with(ctx).load(Uri.parse(toLoad)).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_placeholder).centerCrop().into(holder.productImage);
                    loaded = true;
                } catch (SecurityException se) {
                } catch (Exception ignored) {}
                if (!loaded) {
                    try {
                        Uri uri = Uri.parse(toLoad);
                        String last = uri.getLastPathSegment();
                        if (last != null && last.contains("%2F")) {
                            String decoded = URLDecoder.decode(last, "UTF-8");
                            File f = new File(decoded);
                            if (f.exists() && f.canRead()) {
                                Glide.with(ctx).load(f).placeholder(R.drawable.ic_image_placeholder).error(R.drawable.ic_image_placeholder).centerCrop().into(holder.productImage);
                                loaded = true;
                            }
                        }
                    } catch (Exception ignored) {}
                }
                if (!loaded) holder.productImage.setImageResource(R.drawable.ic_image_placeholder);
            } else {
                holder.productImage.setImageResource(R.drawable.ic_image_placeholder);
            }
        }
        boolean isAdmin = authManager.isCurrentUserAdmin();
        if (holder.btnDecrease != null) {
            holder.btnDecrease.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
            holder.btnDecrease.setOnClickListener(isAdmin ? v -> {
                holder.btnDecrease.setEnabled(false);
                String pid = p.getProductId();
                int newQty = Math.max(0, qty - 1);
                if (pid == null || pid.trim().isEmpty()) {
                    long localId = p.getLocalId();
                    repository.updateProductQuantityByLocalId(localId, newQty, new ProductRepository.OnProductUpdatedListener() {
                        @Override public void onProductUpdated() {
                            mainHandler.post(() -> {
                                updateSingleProductQuantityByLocalId(localId, newQty);
                                holder.btnDecrease.setEnabled(true);
                            });
                        }
                        @Override public void onError(String error) {
                            mainHandler.post(() -> holder.btnDecrease.setEnabled(true));
                        }
                    });
                } else {
                    repository.updateProductQuantity(pid, newQty, new ProductRepository.OnProductUpdatedListener() {
                        @Override public void onProductUpdated() {
                            mainHandler.post(() -> {
                                updateSingleProductQuantity(pid, newQty);
                                holder.btnDecrease.setEnabled(true);
                            });
                        }
                        @Override public void onError(String error) {
                            mainHandler.post(() -> holder.btnDecrease.setEnabled(true));
                        }
                    });
                }
            } : null);
        }
        holder.itemView.setOnClickListener(v -> {
            if (openListener != null) {
                openListener.onOpen(p.getProductId());
                return;
            }
            Intent i = new Intent(ctx, ProductDetailActivity.class);
            i.putExtra("productId", p.getProductId());
            ctx.startActivity(i);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (!authManager.isCurrentUserAdmin()) return true;
            if (longClickListener != null) {
                longClickListener.onLongClick(p, holder.getAdapterPosition());
                return true;
            }
            return true;
        });
    }

    static final DiffUtil.ItemCallback<Product> DIFF = new DiffUtil.ItemCallback<Product>() {
        @Override
        public boolean areItemsTheSame(@NonNull Product a, @NonNull Product b) {
            String ai = a.getProductId();
            String bi = b.getProductId();
            if (ai != null && !ai.trim().isEmpty() && bi != null && !bi.trim().isEmpty()) {
                return ai.equals(bi);
            }
            return a.getLocalId() == b.getLocalId();
        }
        @Override
        public boolean areContentsTheSame(@NonNull Product a, @NonNull Product b) {
            if (!Objects.equals(a.getProductName(), b.getProductName())) return false;
            if (a.getQuantity() != b.getQuantity()) return false;
            if (Double.compare(a.getCostPrice(), b.getCostPrice()) != 0) return false;
            if (Double.compare(a.getSellingPrice(), b.getSellingPrice()) != 0) return false;
            if (a.getFloorLevel() != b.getFloorLevel()) return false;
            if (a.getReorderLevel() != b.getReorderLevel()) return false;
            if (a.getCeilingLevel() != b.getCeilingLevel()) return false;
            if (a.getLastUpdated() != b.getLastUpdated()) return false;
            return true;
        }
    };

    static class VH extends RecyclerView.ViewHolder {
        TextView name;
        TextView quantityText;
        TextView costPriceText;
        TextView stockText;
        TextView sellingPriceText;
        TextView floorText;
        ImageView productImage;
        ImageButton btnDecrease;

        VH(@NonNull View itemView) {
            super(itemView);
            productImage = itemView.findViewById(R.id.ivProductImage);
            name = itemView.findViewById(R.id.tvProductName);
            quantityText = itemView.findViewById(R.id.tvQuantity);
            costPriceText = itemView.findViewById(R.id.tvCostPrice);
            sellingPriceText = itemView.findViewById(R.id.tvSellingPrice);
            floorText = itemView.findViewById(R.id.tvFloorLevel);
            btnDecrease = itemView.findViewById(R.id.btnDecreaseQty);
        }
    }
}