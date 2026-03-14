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
        updateStockDisplay(holder, p.getQuantity(), p.getUnit(), p.getPiecesPerUnit());
        holder.costPriceText.setText("Price: ₱" + String.format(Locale.US, "%.2f", p.getSellingPrice()));

        if (p.getSellingPrice() > 0) {
            holder.tvSellingPrice.setVisibility(View.VISIBLE);
            holder.tvSellingPrice.setText(String.format(java.util.Locale.getDefault(), "₱%.2f", p.getSellingPrice()));
        } else {
            holder.tvSellingPrice.setVisibility(View.GONE);
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
                ctx.startActivity(intent);
            }
        });

        holder.btnEdit.setOnClickListener(v -> {
            int currentPos = holder.getAdapterPosition();
            if (currentPos != RecyclerView.NO_POSITION) {
                Product currentProduct = items.get(currentPos);
                Intent intent = new Intent(ctx, EditProduct.class);
                intent.putExtra("productId", currentProduct.getProductId());
                ctx.startActivity(intent);
            }
        });

        if (isReadOnly) {
            holder.btnEdit.setVisibility(View.GONE);
            holder.btnIncrease.setVisibility(View.GONE);
            holder.btnDecrease.setVisibility(View.GONE);
        } else {
            holder.btnEdit.setVisibility(View.VISIBLE);
            holder.btnIncrease.setVisibility(View.VISIBLE);
            holder.btnDecrease.setVisibility(View.VISIBLE);

            holder.btnIncrease.setOnClickListener(v -> {
                int currentPos = holder.getAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION) {
                    Product currentProduct = items.get(currentPos);
                    // FIX: Replaced int with double
                    double newQty = currentProduct.getQuantity() + 1;
                    repository.updateProductQuantity(currentProduct.getProductId(), newQty, new ProductRepository.OnProductUpdatedListener() {
                        @Override
                        public void onProductUpdated() {
                            if (ctx instanceof Activity) {
                                ((Activity) ctx).runOnUiThread(() -> {
                                    currentProduct.setQuantity(newQty);
                                    updateStockDisplay(holder, newQty, currentProduct.getUnit(), currentProduct.getPiecesPerUnit());
                                    notifyItemChanged(currentPos);
                                });
                            }
                        }
                        @Override
                        public void onError(String error) {
                            if (ctx instanceof Activity) {
                                ((Activity) ctx).runOnUiThread(() ->
                                        Toast.makeText(ctx, error, Toast.LENGTH_SHORT).show());
                            }
                        }
                    });
                }
            });

            holder.btnDecrease.setOnClickListener(v -> {
                int currentPos = holder.getAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION) {
                    Product currentProduct = items.get(currentPos);
                    if (currentProduct.getQuantity() > 0) {
                        // FIX: Replaced int with double
                        double newQty = currentProduct.getQuantity() - 1;
                        repository.updateProductQuantity(currentProduct.getProductId(), newQty, new ProductRepository.OnProductUpdatedListener() {
                            @Override
                            public void onProductUpdated() {
                                if (ctx instanceof Activity) {
                                    ((Activity) ctx).runOnUiThread(() -> {
                                        currentProduct.setQuantity(newQty);
                                        updateStockDisplay(holder, newQty, currentProduct.getUnit(), currentProduct.getPiecesPerUnit());
                                        notifyItemChanged(currentPos);
                                    });
                                }
                            }
                            @Override
                            public void onError(String error) {
                                if (ctx instanceof Activity) {
                                    ((Activity) ctx).runOnUiThread(() ->
                                            Toast.makeText(ctx, error, Toast.LENGTH_SHORT).show());
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
                                repository.deleteProduct(currentProduct.getProductId(), new ProductRepository.OnProductDeletedListener() {
                                    @Override
                                    public void onProductDeleted(String msg) {
                                        if (ctx instanceof Activity) {
                                            ((Activity) ctx).runOnUiThread(() ->
                                                    Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show());
                                        }
                                    }
                                    @Override
                                    public void onError(String error) {
                                        if (ctx instanceof Activity) {
                                            ((Activity) ctx).runOnUiThread(() ->
                                                    Toast.makeText(ctx, error, Toast.LENGTH_SHORT).show());
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

    // FIX: Safely handles decimals without crashing or rounding off fractions incorrectly
    private void updateStockDisplay(VH holder, double qty, String unit, int ppu) {
        String qtyStr = (qty % 1 == 0) ? String.valueOf((long) qty) : String.valueOf(qty);
        String displayStr = qtyStr + (unit != null ? " " + unit : "");

        if (unit != null && !unit.isEmpty()) {
            String u = unit.toLowerCase(Locale.ROOT).trim();

            if (u.equals("g") || u.equals("kg")) {
                if (u.equals("g") && qty >= 1000) {
                    long kg = (long) (qty / 1000);
                    double g = qty % 1000;
                    String gStr = (g % 1 == 0) ? String.valueOf((long) g) : String.format(Locale.US, "%.2f", g);
                    displayStr = kg + "kg" + (g > 0 ? " " + gStr + "g" : "");
                } else {
                    displayStr = qtyStr + u;
                }
            } else if (u.equals("ml") || u.equals("l")) {
                if (u.equals("ml") && qty >= 1000) {
                    long l = (long) (qty / 1000);
                    double ml = qty % 1000;
                    String mlStr = (ml % 1 == 0) ? String.valueOf((long) ml) : String.format(Locale.US, "%.2f", ml);
                    displayStr = l + "L" + (ml > 0 ? " " + mlStr + "ml" : "");
                } else {
                    displayStr = qtyStr + u;
                }
            } else if (u.contains("box") || u.contains("pack")) {
                if (ppu > 1) {
                    long packages = (long) (qty / ppu);
                    double pcs = qty % ppu;
                    String pcsStr = (pcs % 1 == 0) ? String.valueOf((long) pcs) : String.format(Locale.US, "%.2f", pcs);
                    String pkgLabel = u.contains("box") ? (packages > 1 ? " boxes" : " box") : (packages > 1 ? " packs" : " pack");
                    String pcsLabel = (pcs > 1 || pcs != 1.0) ? " pcs" : " pc";

                    if (packages > 0 && pcs > 0) {
                        displayStr = packages + pkgLabel + " " + pcsStr + pcsLabel;
                    } else if (packages > 0) {
                        displayStr = packages + pkgLabel;
                    } else {
                        displayStr = pcsStr + pcsLabel;
                    }
                } else {
                    displayStr = qtyStr + " " + u;
                }
            } else if (u.equals("pcs")) {
                if (ppu > 1) {
                    long packages = (long) (qty / ppu);
                    double pcs = qty % ppu;
                    String pcsStr = (pcs % 1 == 0) ? String.valueOf((long) pcs) : String.format(Locale.US, "%.2f", pcs);

                    if (packages > 0 && pcs > 0) {
                        displayStr = packages + " boxes " + pcsStr + " pcs";
                    } else if (packages > 0) {
                        displayStr = packages + (packages > 1 ? " boxes" : " box");
                    } else {
                        displayStr = pcsStr + " pcs";
                    }
                } else {
                    displayStr = qtyStr + " pcs";
                }
            }
        }

        if (holder.stockText != null) holder.stockText.setText(displayStr);
        if (holder.quantityText != null) holder.quantityText.setText("Stock: " + displayStr);
    }

    private void loadImage(String localPath, String onlineUrl, ImageView imageView) {
        if (localPath != null && !localPath.isEmpty()) {
            File file = new File(localPath);
            if (file.exists()) {
                Glide.with(ctx).load(file)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .override(200, 200)
                        .thumbnail(0.25f)
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .centerCrop()
                        .into(imageView);
                return;
            }
        }
        if (onlineUrl != null && !onlineUrl.isEmpty()) {
            Glide.with(ctx).load(onlineUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .override(200, 200)
                    .thumbnail(0.25f)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .centerCrop()
                    .into(imageView);
            return;
        }
        imageView.setImageResource(R.drawable.ic_image_placeholder);
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView name, quantityText, costPriceText, stockText, btnEdit, tvSellingPrice;
        ImageView productImage;
        ImageButton btnIncrease, btnDecrease;

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
            tvSellingPrice = itemView.findViewById(R.id.tvSellingPrice);
        }
    }
}