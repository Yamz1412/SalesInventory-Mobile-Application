package com.app.SalesInventory;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NearExpiryItemsAdapter extends RecyclerView.Adapter<NearExpiryItemsAdapter.VH> {

    private final Context ctx;
    private final List<Product> items;
    private final ProductRepository repository;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    public NearExpiryItemsAdapter(Context ctx, List<Product> items, ProductRepository repository) {
        this.ctx = ctx;
        this.items = items;
        this.repository = repository != null ? repository : ProductRepository.getInstance((Application) ctx.getApplicationContext());
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_near_expiry_product, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Product p = items.get(position);
        if (p == null) return;

        holder.name.setText(p.getProductName() != null ? p.getProductName() : "");
        holder.category.setText(p.getCategoryName() != null ? p.getCategoryName() : "");

        long expiry = p.getExpiryDate();
        if (expiry > 0) {
            holder.expiryDate.setText("Expires: " + dateFormat.format(new Date(expiry)));
            long now = System.currentTimeMillis();
            long diffMillis = expiry - now;
            long days = diffMillis / (24L * 60L * 60L * 1000L);

            if (diffMillis <= 0) {
                holder.daysRemaining.setText("Expired");
                holder.statusLabel.setText("Expired");
                holder.statusIcon.setColorFilter(ctx.getResources().getColor(R.color.errorRed));
            } else if (days <= 3) {
                holder.daysRemaining.setText("In " + days + " day" + (days == 1 ? "" : "s"));
                holder.statusLabel.setText("Almost");
                holder.statusIcon.setColorFilter(ctx.getResources().getColor(R.color.errorRed));
            } else if (days <= 7) {
                holder.daysRemaining.setText("In " + days + " days");
                holder.statusLabel.setText("Soon");
                holder.statusIcon.setColorFilter(ctx.getResources().getColor(R.color.warningYellow));
            } else {
                holder.daysRemaining.setText("In " + days + " days");
                holder.statusLabel.setText("OK");
                holder.statusIcon.setColorFilter(ctx.getResources().getColor(R.color.successGreen));
            }
        } else {
            holder.expiryDate.setText("No expiry");
            holder.daysRemaining.setText("");
            holder.statusLabel.setText("N/A");
            holder.statusIcon.setColorFilter(ctx.getResources().getColor(R.color.text_secondary));
        }

        String imageUrl = p.getImageUrl();
        String imagePath = p.getImagePath();
        String toLoad = null;
        if (imageUrl != null && !imageUrl.isEmpty()) {
            toLoad = imageUrl;
        } else if (imagePath != null && !imagePath.isEmpty()) {
            toLoad = imagePath;
        }
        if (toLoad != null && !toLoad.isEmpty()) {
            Glide.with(ctx)
                    .load(toLoad)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(holder.productImage);
        } else {
            holder.productImage.setImageResource(R.drawable.ic_image_placeholder);
        }

        holder.itemView.setOnClickListener(v -> {
            Intent i = new Intent(ctx, ProductDetailActivity.class);
            i.putExtra("productId", p.getProductId());
            ctx.startActivity(i);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        ImageView productImage;
        TextView name;
        TextView category;
        TextView expiryDate;
        TextView daysRemaining;
        ImageView statusIcon;
        TextView statusLabel;

        VH(@NonNull View itemView) {
            super(itemView);
            productImage = itemView.findViewById(R.id.ivProductImage);
            name = itemView.findViewById(R.id.tvProductName);
            category = itemView.findViewById(R.id.tvCategory);
            expiryDate = itemView.findViewById(R.id.tvExpiryDate);
            daysRemaining = itemView.findViewById(R.id.tvDaysRemaining);
            statusIcon = itemView.findViewById(R.id.ivStatusIcon);
            statusLabel = itemView.findViewById(R.id.tvStatusLabel);
        }
    }
}