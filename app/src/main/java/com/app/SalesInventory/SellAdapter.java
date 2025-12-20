package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.Key;
import com.bumptech.glide.signature.ObjectKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SellAdapter extends RecyclerView.Adapter<SellAdapter.VH> {

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }

    public interface OnProductLongClickListener {
        void onProductLongClick(Product product);
    }

    private final Context ctx;
    private final List<Product> items = new ArrayList<>();
    private final ProductRepository productRepository;
    private final AuthManager authManager;
    private OnProductClickListener clickListener;
    private OnProductLongClickListener longClickListener;

    public SellAdapter(Context ctx, List<Product> initial) {
        this.ctx = ctx;
        if (initial != null) {
            items.addAll(initial);
        }
        productRepository = SalesInventoryApplication.getProductRepository();
        authManager = AuthManager.getInstance();
    }

    public void updateProducts(List<Product> list) {
        items.clear();
        if (list != null) items.addAll(list);
        notifyDataSetChanged();
    }

    public void setOnProductClickListener(OnProductClickListener listener) {
        this.clickListener = listener;
    }

    public void setOnProductLongClickListener(OnProductLongClickListener listener) {
        this.longClickListener = listener;
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

        String category = p.getCategoryName() == null ? "" : p.getCategoryName();
        holder.code.setText(category.isEmpty() ? "Uncategorized" : category);

        double selling = p.getSellingPrice();
        holder.price.setText("₱" + String.format(Locale.US, "%.2f", selling));

        String imageUrl = p.getImageUrl();
        String imagePath = p.getImagePath();
        String toLoad = null;
        if (imageUrl != null && !imageUrl.isEmpty()) {
            toLoad = imageUrl;
        } else if (imagePath != null && !imagePath.isEmpty()) {
            toLoad = imagePath;
        }

        if (toLoad != null && !toLoad.isEmpty()) {
            Key sig = new ObjectKey((p.getProductId() != null ? p.getProductId() : "") + "_" + p.getDateAdded() + "_" + p.getExpiryDate());
            Glide.with(ctx)
                    .load(toLoad)
                    .signature(sig)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(holder.productImage);
        } else {
            holder.productImage.setImageResource(R.drawable.ic_image_placeholder);
        }

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) clickListener.onProductClick(p);
        });

        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onProductLongClick(p);
                return true;
            }
            if (!authManager.isCurrentUserAdmin()) return true;
            new AlertDialog.Builder(ctx)
                    .setTitle("Delete Menu Item")
                    .setMessage("Delete " + p.getProductName() + " from menu?")
                    .setPositiveButton("Delete", (dialog, which) -> {
                        productRepository.deleteProduct(p.getProductId(), new ProductRepository.OnProductDeletedListener() {
                            @Override
                            public void onProductDeleted(String archiveFilename) {
                            }

                            @Override
                            public void onError(String error) {
                            }
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name;
        TextView code;
        TextView price;
        ImageView productImage;

        VH(@NonNull View itemView) {
            super(itemView);
            productImage = itemView.findViewById(R.id.ivProductImageSell);
            name = itemView.findViewById(R.id.NameTVS11);
            code = itemView.findViewById(R.id.CodeTVS11);
            price = itemView.findViewById(R.id.SellPriceTVS11);
        }
    }
}