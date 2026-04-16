package com.app.SalesInventory;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_DEFAULT = 0;
    private static final int TYPE_LOW_STOCK = 1;
    private static final int TYPE_NEAR_EXPIRY = 2;

    private List<Alert> masterAlerts = new ArrayList<>();
    private List<Product> masterProducts = new ArrayList<>();
    private List<Alert> displayAlerts = new ArrayList<>();
    private List<Product> displayProducts = new ArrayList<>();
    private java.util.Set<String> selectedIds = new java.util.HashSet<>();
    private boolean isSelectionMode = false;
    private final OnNotificationClickListener listener;
    private final Context context;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    public interface OnNotificationClickListener {
        void onNotificationClick(Alert alert, Product product, String exactCategory);
    }

    public NotificationAdapter(Context context, OnNotificationClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setAlertsAndProducts(List<Alert> newAlerts, List<Product> newProducts) {
        this.masterAlerts.clear();
        this.masterProducts.clear();
        if (newAlerts != null) this.masterAlerts.addAll(newAlerts);
        if (newProducts != null) this.masterProducts.addAll(newProducts);
        // FIXED: Changed to "All" to perfectly match your MainActivity dropdown!
        filter("All");
    }

    public void filter(String category) {
        displayAlerts.clear();
        displayProducts.clear();

        if (category.equals("All") || category.equals("All Notifications")) {
            for (Alert alert : masterAlerts) {
                Product p = findProductForAlert(alert);
                String type = alert.getType() != null ? alert.getType().toUpperCase() : "";

                if (p != null && p.isFinishedProduct() && type.contains("STOCK")) {
                    continue;
                }
                displayAlerts.add(alert);
            }

            long now = System.currentTimeMillis();
            for (Product p : masterProducts) {
                if (p != null && p.isActive() && p.getExpiryDate() != null && !p.isFinishedProduct()) {
                    // FIXED: Handle ExpiryDate as a Date object safely
                    long expiryTime = p.getExpiryDate().getTime();
                    long diffMillis = expiryTime - now;
                    long days = diffMillis / (24L * 60L * 60L * 1000L);

                    if (diffMillis <= 0 || days <= 7) {
                        displayProducts.add(p);
                    }
                }
            }
        } else {
            for (Alert alert : masterAlerts) {
                Product p = findProductForAlert(alert);
                String type = alert.getType() != null ? alert.getType().toLowerCase() : "";

                if (p != null && p.isFinishedProduct() && type.contains("stock")) {
                    continue;
                }

                if (category.equals("Low Stock") && (type.contains("low") || type.contains("stock"))) {
                    displayAlerts.add(alert);
                } else if (category.equals("Expiration") && type.contains("expir")) {
                    displayAlerts.add(alert);
                } else if (category.equals("Damaged/Spoiled") && (type.contains("damage") || type.contains("spoil"))) {
                    displayAlerts.add(alert);
                } else if (category.equals("Supplier Updates") && (type.contains("po") || type.contains("email") || type.contains("supplier"))) {
                    displayAlerts.add(alert);
                }
            }

            if (category.equals("Expiration")) {
                long now = System.currentTimeMillis();
                for (Product p : masterProducts) {
                    if (p != null && p.isActive() && p.getExpiryDate() != null && !p.isFinishedProduct()) {
                        // FIXED: Handle ExpiryDate as a Date object safely
                        long expiryTime = p.getExpiryDate().getTime();
                        long diffMillis = expiryTime - now;
                        long days = diffMillis / (24L * 60L * 60L * 1000L);

                        if (diffMillis <= 0 || days <= 7) {
                            displayProducts.add(p);
                        }
                    }
                }
            }
        }
        notifyDataSetChanged();
    }

    public void toggleSelection(String alertId) {
        if (selectedIds.contains(alertId)) selectedIds.remove(alertId);
        else selectedIds.add(alertId);
        isSelectionMode = !selectedIds.isEmpty();
        notifyDataSetChanged();
    }

    public void clearSelection() {
        selectedIds.clear();
        isSelectionMode = false;
        notifyDataSetChanged();
    }

    public java.util.Set<String> getSelectedIds() {
        return selectedIds;
    }

    private Product findProductForAlert(Alert alert) {
        if (alert == null) return null;
        try {
            String pid = alert.getProductId();
            if (pid != null && !pid.isEmpty()) {
                for (Product p : masterProducts) {
                    if (pid.equals(p.getProductId()) ||
                            pid.equals(String.valueOf(p.getLocalId())) ||
                            pid.equals("local:" + p.getLocalId())) {
                        return p;
                    }
                }
            }
        } catch (Exception ignored) {}

        if (alert.getMessage() != null) {
            for (Product p : masterProducts) {
                if (p.getProductName() != null && alert.getMessage().toLowerCase().contains(p.getProductName().toLowerCase())) {
                    return p;
                }
            }
        }
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        if (position < displayAlerts.size()) {
            String type = displayAlerts.get(position).getType();
            if (type != null) {
                if (type.equals("LOW_STOCK") || type.equals("CRITICAL_STOCK") || type.equals("FLOOR_STOCK")) return TYPE_LOW_STOCK;
                if (type.contains("EXPIRY") || type.equals("EXPIRED")) return TYPE_NEAR_EXPIRY;
            }
            return TYPE_DEFAULT;
        }
        return TYPE_NEAR_EXPIRY;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == TYPE_LOW_STOCK) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_low_stock_product, parent, false);
            return new LowStockViewHolder(view);
        } else if (viewType == TYPE_NEAR_EXPIRY) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_near_expiry_product, parent, false);
            return new NearExpiryViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_notification, parent, false);
            return new DefaultViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (position < displayAlerts.size()) {
            Alert alert = displayAlerts.get(position);
            Product product = findProductForAlert(alert);

            if (holder instanceof LowStockViewHolder) {
                ((LowStockViewHolder) holder).bind(alert, product);
            } else if (holder instanceof NearExpiryViewHolder) {
                ((NearExpiryViewHolder) holder).bind(alert, product);
            } else if (holder instanceof DefaultViewHolder) {
                ((DefaultViewHolder) holder).bind(alert);
            }
        } else {
            Product product = displayProducts.get(position - displayAlerts.size());
            if (holder instanceof NearExpiryViewHolder) {
                ((NearExpiryViewHolder) holder).bindProductOnly(product);
            }
        }
    }

    @Override
    public int getItemCount() {
        return displayAlerts.size() + displayProducts.size();
    }

    private String getFriendlyTitle(String type) {
        if (type == null) return "Notification";
        switch (type) {
            case "LOW_STOCK": return "Low Stock Alert";
            case "CRITICAL_STOCK": return "Critical Stock Level";
            case "FLOOR_STOCK": return "Critical Reorder Level";
            case "EXPIRY_7_DAYS": return "Expiring Soon (7 Days)";
            case "EXPIRY_3_DAYS": return "Expiring Soon (3 Days)";
            case "EXPIRED": return "Product Expired";
            case "PO_RECEIVED": return "Shipment Received";
            default: return type.replace("_", " ");
        }
    }

    private String determineCategory(String type) {
        if (type == null) return "General";
        type = type.toLowerCase();
        if (type.contains("low") || type.contains("stock")) return "Low Stock";
        if (type.contains("expir")) return "Expiration";
        if (type.contains("damage") || type.contains("spoil")) return "Damaged";
        if (type.contains("po") || type.contains("email") || type.contains("supplier")) return "Supplier";
        return "General";
    }

    public class DefaultViewHolder extends RecyclerView.ViewHolder {
        ImageView imgIcon;
        TextView tvTitle, tvMessage, tvTime;
        View viewUnreadDot;

        public DefaultViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.img_icon);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvMessage = itemView.findViewById(R.id.tv_message);
            tvTime = itemView.findViewById(R.id.tv_time);
            viewUnreadDot = itemView.findViewById(R.id.view_unread_dot);
        }

        public void bind(final Alert alert) {
            tvTitle.setText(getFriendlyTitle(alert.getType()));
            tvMessage.setText(alert.getMessage());

            long now = System.currentTimeMillis();
            long timestamp = alert.getTimestamp() <= 0 ? now : alert.getTimestamp();
            tvTime.setText(DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.MINUTE_IN_MILLIS));

            viewUnreadDot.setVisibility(alert.isRead() ? View.GONE : View.VISIBLE);
            tvTitle.setTypeface(null, alert.isRead() ? Typeface.NORMAL : Typeface.BOLD);

            // Selection Logic
            if (selectedIds.contains(alert.getId())) {
                itemView.setBackgroundColor(Color.parseColor("#33888888"));
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            itemView.setOnLongClickListener(v -> { toggleSelection(alert.getId()); return true; });

            itemView.setOnClickListener(v -> {
                if (isSelectionMode) {
                    toggleSelection(alert.getId());
                } else if (listener != null) {
                    listener.onNotificationClick(alert, null, determineCategory(alert.getType()));
                }
            });
        }
    }

    public class LowStockViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProductImage;
        TextView tvProductName, tvCategory, tvStockInfo, tvCurrentStock;

        public LowStockViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProductImage = itemView.findViewById(R.id.ivProductImage);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvStockInfo = itemView.findViewById(R.id.tvStockInfo);
            tvCurrentStock = itemView.findViewById(R.id.tvCurrentStock);
        }

        public void bind(Alert alert, Product product) {
            if (product != null) {
                tvProductName.setText(product.getProductName());
                tvCategory.setText(product.getCategoryName());

                String qtyStr = (product.getQuantity() % 1 == 0) ?
                        String.valueOf((long)product.getQuantity()) : String.valueOf(product.getQuantity());
                tvCurrentStock.setText(qtyStr + " " + (product.getUnit() != null ? product.getUnit() : "pcs"));

                tvStockInfo.setText("Reorder: " + product.getReorderLevel() + " | Critical: " + product.getCriticalLevel());

                String img = (product.getImageUrl() != null && !product.getImageUrl().isEmpty()) ?
                        product.getImageUrl() : product.getImagePath();
                if (img != null && !img.isEmpty()) {
                    Glide.with(context).load(img).placeholder(R.drawable.ic_image_placeholder).centerCrop().into(ivProductImage);
                } else {
                    ivProductImage.setImageResource(R.drawable.ic_image_placeholder);
                }
            }

            // Selection & Click Logic
            if (alert != null && selectedIds.contains(alert.getId())) {
                itemView.setBackgroundColor(Color.parseColor("#33888888"));
            } else {
                itemView.setBackgroundColor(Color.TRANSPARENT);
            }

            itemView.setOnLongClickListener(v -> {
                if (alert != null) { toggleSelection(alert.getId()); return true; }
                return false;
            });

            itemView.setOnClickListener(v -> {
                if (isSelectionMode && alert != null) {
                    toggleSelection(alert.getId());
                } else if (listener != null) {
                    listener.onNotificationClick(alert, product, determineCategory(alert != null ? alert.getType() : ""));
                }
            });
        }
    }

    public class NearExpiryViewHolder extends RecyclerView.ViewHolder {
        ImageView ivProductImage, ivStatusIcon;
        TextView tvProductName, tvCategory, tvExpiryDate, tvDaysRemaining, tvStatusLabel;

        public NearExpiryViewHolder(@NonNull View itemView) {
            super(itemView);
            ivProductImage = itemView.findViewById(R.id.ivProductImage);
            ivStatusIcon = itemView.findViewById(R.id.ivStatusIcon);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvExpiryDate = itemView.findViewById(R.id.tvExpiryDate);
            tvDaysRemaining = itemView.findViewById(R.id.tvDaysRemaining);
            tvStatusLabel = itemView.findViewById(R.id.tvStatusLabel);
        }

        public void bind(Alert alert, Product p) {
            applyExpiryStyling();

            if (p != null) {
                populateProductData(p);
            } else {
                String cleanMsg = alert != null && alert.getMessage() != null ? alert.getMessage() : "";
                tvProductName.setText(cleanMsg);
                tvCategory.setText(alert != null ? getFriendlyTitle(alert.getType()) : "Expiration");
                tvExpiryDate.setText("Tap to view details");
                tvDaysRemaining.setText("");
                tvStatusLabel.setText("!");
                ivProductImage.setImageResource(R.drawable.ic_image_placeholder);
            }

            String exactCategory = alert != null ? determineCategory(alert.getType()) : "Expiration";

            if (alert != null && selectedIds.contains(alert.getId())) {
                itemView.setBackgroundColor(Color.parseColor("#33888888"));
            } else {
                ((CardView) itemView).setCardBackgroundColor(Color.parseColor("#D32F2F"));
            }

            itemView.setOnLongClickListener(v -> {
                if (alert != null) {
                    toggleSelection(alert.getId());
                    return true;
                }
                return false;
            });

            itemView.setOnClickListener(v -> {
                if (isSelectionMode && alert != null) {
                    toggleSelection(alert.getId());
                } else {
                    if (listener != null) listener.onNotificationClick(alert, p, exactCategory);
                }
            });
        }

        public void bindProductOnly(Product p) {
            applyExpiryStyling();
            populateProductData(p);
            itemView.setOnClickListener(v -> listener.onNotificationClick(null, p, "Expiration"));
        }

        private void applyExpiryStyling() {
            ((CardView) itemView).setCardBackgroundColor(Color.parseColor("#D32F2F"));

            tvProductName.setTextColor(Color.WHITE);
            tvCategory.setTextColor(Color.WHITE);
            tvExpiryDate.setTextColor(Color.WHITE);
            tvDaysRemaining.setTextColor(Color.WHITE);
            tvStatusLabel.setTextColor(Color.WHITE);
            if (ivStatusIcon != null) ivStatusIcon.setColorFilter(Color.WHITE);
        }

        private void populateProductData(Product p) {
            if (p == null) return;
            tvProductName.setText(p.getProductName() != null ? p.getProductName() : "Unknown");
            tvCategory.setText(p.getCategoryName() != null ? p.getCategoryName() : "No Category");

            long expiry = (p.getExpiryDate() != null) ? p.getExpiryDate().getTime() : 0L;
            if (expiry > 0) {
                tvExpiryDate.setText("Expires: " + dateFormat.format(new Date(expiry)));
                long diff = expiry - System.currentTimeMillis();
                long days = diff / (24L * 60L * 60L * 1000L);

                if (diff <= 0) {
                    tvDaysRemaining.setText("Expired");
                    tvStatusLabel.setText("Expired");
                } else {
                    tvDaysRemaining.setText("In " + days + " d");
                    tvStatusLabel.setText("Soon");
                }
            }

            String img = p.getImageUrl() != null && !p.getImageUrl().isEmpty() ? p.getImageUrl() : p.getImagePath();
            if (img != null && !img.isEmpty()) Glide.with(context).load(img).placeholder(R.drawable.ic_image_placeholder).centerCrop().into(ivProductImage);
            else ivProductImage.setImageResource(R.drawable.ic_image_placeholder);
        }
    }
}