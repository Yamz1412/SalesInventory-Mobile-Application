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
import android.widget.Toast;

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

    private boolean isReadOnly = false;

    public ProductAdapter(Context ctx) {
        this.ctx = ctx;
        this.repository = ProductRepository.getInstance((Application) ctx.getApplicationContext());
        this.authManager = AuthManager.getInstance();
    }

    public ProductAdapter(List<Product> initialList, Context ctx) {
        this(ctx);
        if (initialList != null) {
            items.addAll(initialList);
        }
    }

    public void setReadOnly(boolean readOnly) {
        this.isReadOnly = readOnly;
        notifyDataSetChanged();
    }

    public void setItems(List<Product> newProducts) {
        updateProducts(newProducts);
    }

    public void updateProducts(List<Product> newProducts) {
        items.clear();
        if (newProducts != null) {
            items.addAll(newProducts);
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

        holder.name.setText(p.getProductName());

        // Display Menu items appropriately
        boolean isMenu = p.isSellable() || "finished".equalsIgnoreCase(p.getProductType()) || "Menu".equalsIgnoreCase(p.getProductType());

        if (isMenu) {
            if (holder.stockText != null) holder.stockText.setText("Recipe");
            if (holder.quantityText != null) holder.quantityText.setText("Menu Item (No physical stock)");
        } else {
            updateStockDisplay(holder, p.getQuantity(), p.getUnit(), p.getPiecesPerUnit());
        }

        if (p.getCostPrice() > 0) {
            holder.costPriceText.setVisibility(View.VISIBLE);
            holder.costPriceText.setText(String.format(java.util.Locale.getDefault(), "Cost: ₱%.2f", p.getCostPrice()));
        } else {
            if (holder.costPriceText != null) holder.costPriceText.setVisibility(View.GONE);
        }

        loadImage(p.getImagePath(), p.getImageUrl(), holder.productImage);

        holder.itemView.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                Product currentProduct = items.get(currentPos);
                Intent intent = new Intent(ctx, ProductDetailActivity.class);
                intent.putExtra("productId", currentProduct.getProductId());
                intent.putExtra("PRODUCT_ID", currentProduct.getProductId()); // Safety fallback
                ctx.startActivity(intent);
            }
        });

        holder.btnEdit.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                Product currentProduct = items.get(currentPos);

                // FIX: Point to AddProductActivity instead of EditProduct, as this handles the Edit UI
                Intent intent = new Intent(ctx, AddProductActivity.class);
                intent.putExtra("EDIT_PRODUCT_ID", currentProduct.getProductId());
                intent.putExtra("productId", currentProduct.getProductId()); // Safety fallback
                ctx.startActivity(intent);
            }
        });

        if (isReadOnly) {
            holder.btnEdit.setVisibility(View.GONE);
            holder.btnIncrease.setVisibility(View.GONE);
            holder.btnDecrease.setVisibility(View.GONE);
        } else {
            holder.btnEdit.setVisibility(View.VISIBLE);

            // Hides the +/- stock buttons for Menu items
            holder.btnIncrease.setVisibility(isMenu ? View.GONE : View.VISIBLE);
            holder.btnDecrease.setVisibility(isMenu ? View.GONE : View.VISIBLE);

            holder.btnIncrease.setOnClickListener(v -> {
                int currentPos = holder.getAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION) {
                    Product currentProduct = items.get(currentPos);
                    double oldQty = currentProduct.getQuantity();
                    double newQty = oldQty + 1;

                    // 1. OPTIMISTIC UI UPDATE: Change the number instantly so it feels snappy
                    currentProduct.setQuantity(newQty);
                    updateStockDisplay(holder, newQty, currentProduct.getUnit(), currentProduct.getPiecesPerUnit());

                    // 2. BACKGROUND SYNC: Update the database silently
                    repository.updateProductQuantity(currentProduct.getProductId(), newQty, new ProductRepository.OnProductUpdatedListener() {
                        @Override
                        public void onProductUpdated() {
                            // Success! The UI is already updated, do nothing.
                        }
                        @Override
                        public void onError(String error) {
                            if (ctx instanceof Activity) {
                                ((Activity) ctx).runOnUiThread(() -> {
                                    // If database fails, safely revert the UI back to the old number
                                    currentProduct.setQuantity(oldQty);
                                    updateStockDisplay(holder, oldQty, currentProduct.getUnit(), currentProduct.getPiecesPerUnit());
                                    Toast.makeText(ctx, "Sync failed: " + error, Toast.LENGTH_SHORT).show();
                                });
                            }
                        }
                    });
                }
            });

            holder.btnDecrease.setOnClickListener(v -> {
                int currentPos = holder.getAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION) {
                    Product currentProduct = items.get(currentPos);
                    double oldQty = currentProduct.getQuantity();

                    if (oldQty > 0) {
                        double newQty = oldQty - 1;

                        // 1. OPTIMISTIC UI UPDATE
                        currentProduct.setQuantity(newQty);
                        updateStockDisplay(holder, newQty, currentProduct.getUnit(), currentProduct.getPiecesPerUnit());

                        // 2. BACKGROUND SYNC
                        repository.updateProductQuantity(currentProduct.getProductId(), newQty, new ProductRepository.OnProductUpdatedListener() {
                            @Override
                            public void onProductUpdated() {
                                // Success!
                            }
                            @Override
                            public void onError(String error) {
                                if (ctx instanceof Activity) {
                                    ((Activity) ctx).runOnUiThread(() -> {
                                        // Revert on failure
                                        currentProduct.setQuantity(oldQty);
                                        updateStockDisplay(holder, oldQty, currentProduct.getUnit(), currentProduct.getPiecesPerUnit());
                                        Toast.makeText(ctx, "Sync failed: " + error, Toast.LENGTH_SHORT).show();
                                    });
                                }
                            }
                        });
                    }
                }
            });

            holder.itemView.setOnLongClickListener(v -> {
                int currentPos = holder.getAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION) {
                    Product currentProduct = items.get(currentPos);

                    new AlertDialog.Builder(ctx)
                            .setTitle("Archive Product")
                            .setMessage("Are you sure you want to archive " + currentProduct.getProductName() + "?")
                            .setPositiveButton("Archive", (dialog, which) -> {

                                final Product archivedProduct = currentProduct;
                                final int archivedPos = currentPos;

                                repository.deleteProduct(currentProduct.getProductId(), new ProductRepository.OnProductDeletedListener() {
                                    @Override
                                    public void onProductDeleted(String filename) {
                                        if (ctx instanceof Activity) {
                                            ((Activity) ctx).runOnUiThread(() -> {
                                                View rootView = ((Activity) ctx).findViewById(android.R.id.content);
                                                com.google.android.material.snackbar.Snackbar.make(rootView,
                                                                archivedProduct.getProductName() + " archived",
                                                                com.google.android.material.snackbar.Snackbar.LENGTH_LONG)
                                                        .setAction("UNDO", view -> {
                                                            repository.restoreArchived(filename, new ProductRepository.OnProductRestoreListener() {
                                                                @Override
                                                                public void onProductRestored() {
                                                                    ((Activity) ctx).runOnUiThread(() ->
                                                                            Toast.makeText(ctx, "Restored!", Toast.LENGTH_SHORT).show());
                                                                }
                                                                @Override
                                                                public void onError(String error) {
                                                                    ((Activity) ctx).runOnUiThread(() ->
                                                                            Toast.makeText(ctx, "Failed to undo", Toast.LENGTH_SHORT).show());
                                                                }
                                                            });
                                                        }).show();
                                            });
                                        }
                                    }

                                    @Override
                                    public void onError(String error) {
                                        if (ctx instanceof Activity) {
                                            ((Activity) ctx).runOnUiThread(() ->
                                                    Toast.makeText(ctx, "Error: " + error, Toast.LENGTH_SHORT).show());
                                        }
                                    }
                                });
                            })
                            .setNegativeButton("Cancel", null)
                            .show();
                }
                return true;
            });
        }
    }

    private void updateStockDisplay(VH holder, double qty, String unit, int ppu) {
        String qtyStr = (qty % 1 == 0) ? String.valueOf((long) qty) : String.format(Locale.US, "%.2f", qty).replaceAll("0*$", "").replaceAll("\\.$", "");
        String displayStr = qtyStr;

        if (unit != null && !unit.isEmpty()) {
            String u = unit.trim();

            if (u.equalsIgnoreCase("pcs") || u.equalsIgnoreCase("box") || u.equalsIgnoreCase("pack")) {
                displayStr += " " + u;
            } else if (u.equalsIgnoreCase("g") || u.equalsIgnoreCase("kg") ||
                    u.equalsIgnoreCase("ml") || u.equalsIgnoreCase("L") ||
                    u.equalsIgnoreCase("oz")) {
                displayStr += u;
            } else {
                displayStr += " " + u;
            }
        }

        if (holder.stockText != null) holder.stockText.setText(displayStr);
        if (holder.quantityText != null) holder.quantityText.setText("Stock: " + displayStr);
    }

    private void loadImage(String localPath, String onlineUrl, ImageView imageView) {
        // Decide the primary image to load (Online URL first, Local Path second)
        String primaryLoad = (onlineUrl != null && !onlineUrl.isEmpty()) ? onlineUrl : localPath;

        Glide.with(ctx)
                .load(primaryLoad)
                .diskCacheStrategy(DiskCacheStrategy.ALL) // Saves for offline use
                .override(200, 200)
                .placeholder(R.drawable.ic_image_placeholder)
                .centerCrop()
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