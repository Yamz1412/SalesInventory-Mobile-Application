package com.app.SalesInventory;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SellAdapter extends RecyclerView.Adapter<SellAdapter.VH> {

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }

    public interface OnSelectionChangeListener {
        void onSelectionChanged(Set<String> selectedIds);
    }

    private final Context ctx;
    private final List<Product> items = new ArrayList<>();
    private OnProductClickListener clickListener;
    private OnSelectionChangeListener selectionChangeListener;

    private Set<String> selectedIds = new HashSet<>();
    private boolean isSelectionMode = false;

    public SellAdapter(Context ctx, List<Product> initial, OnProductClickListener clickListener, OnSelectionChangeListener selectionChangeListener) {
        this.ctx = ctx;
        if (initial != null) {
            this.items.addAll(initial);
        }
        this.clickListener = clickListener;
        this.selectionChangeListener = selectionChangeListener;
    }

    // FIXED: Added the missing updateData method
    public void updateData(List<Product> newItems) {
        items.clear();
        if (newItems != null) {
            items.addAll(newItems);
        }
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selectedIds.clear();
        isSelectionMode = false;
        notifyDataSetChanged();
        if (selectionChangeListener != null) selectionChangeListener.onSelectionChanged(selectedIds);
    }

    public Set<String> getSelectedIds() {
        return selectedIds;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(ctx).inflate(R.layout.productsell, parent, false);
        return new VH(view);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Product p = items.get(position);
        holder.name.setText(p.getProductName());
        holder.price.setText(String.format(Locale.getDefault(), "₱%.2f", p.getSellingPrice()));

        if (p.getBarcode() != null && !p.getBarcode().isEmpty()) {
            holder.code.setVisibility(View.VISIBLE);
            holder.code.setText("Code: " + p.getBarcode());
        } else {
            holder.code.setVisibility(View.GONE);
        }

        String currentId = p.getProductId() != null ? p.getProductId() : "local:" + p.getLocalId();
        boolean isSelected = selectedIds.contains(currentId);

        if (isSelected) {
            holder.cardView.setCardBackgroundColor(Color.parseColor("#D3E3FD"));
            holder.itemView.setAlpha(0.8f);
        } else {
            holder.cardView.setCardBackgroundColor(Color.WHITE);
            holder.itemView.setAlpha(1.0f);
        }

        String onlineUrl = p.getImageUrl();
        if (onlineUrl != null && !onlineUrl.isEmpty()) {
            Glide.with(ctx).load(onlineUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(200, 200)
                    .thumbnail(0.25f)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(holder.productImage);
        } else {
            holder.productImage.setImageResource(R.drawable.ic_image_placeholder);
        }

        holder.itemView.setOnClickListener(v -> {
            if (isSelectionMode) {
                toggleSelection(currentId);
            } else {
                if (clickListener != null) clickListener.onProductClick(p);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode) {
                isSelectionMode = true;
                toggleSelection(currentId);
                return true;
            }
            return false;
        });
    }

    private void toggleSelection(String id) {
        if (selectedIds.contains(id)) {
            selectedIds.remove(id);
        } else {
            selectedIds.add(id);
        }
        if (selectedIds.isEmpty()) {
            isSelectionMode = false;
        }
        notifyDataSetChanged();
        if (selectionChangeListener != null) selectionChangeListener.onSelectionChanged(selectedIds);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, code, price;
        ImageView productImage;
        CardView cardView;

        VH(@NonNull View itemView) {
            super(itemView);
            cardView = (CardView) itemView;
            productImage = itemView.findViewById(R.id.ivProductImageSell);
            name = itemView.findViewById(R.id.NameTVS11);
            code = itemView.findViewById(R.id.CodeTVS11);
            price = itemView.findViewById(R.id.SellPriceTVS11);
        }
    }
}