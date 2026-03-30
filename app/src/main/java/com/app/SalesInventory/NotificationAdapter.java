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
        filter("All Notifications");

    }

    public void filter(String category) {
        displayAlerts.clear();
        displayProducts.clear();

        if (category.equals("All Notifications")) {
            displayAlerts.addAll(masterAlerts);
            long now = System.currentTimeMillis();
            for (Product p : masterProducts) {
                if (p != null && p.isActive() && p.getExpiryDate() > 0) {
                    long diffMillis = p.getExpiryDate() - now;
                    long days = diffMillis / (24L * 60L * 60L * 1000L);
                    if (diffMillis <= 0 || days <= 7) {
                        displayProducts.add(p);
                    }
                }
            }
        } else {
            for (Alert alert : masterAlerts) {
                String type = alert.getType() != null ? alert.getType().toLowerCase() : "";
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
                    if (p != null && p.isActive() && p.getExpiryDate() > 0) {
                        long diffMillis = p.getExpiryDate() - now;
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

    // ==========================================
    // VIEW HOLDER 1: DEFAULT NOTIFICATION
    // ==========================================
    public class DefaultViewHolder extends RecyclerView.ViewHolder {
        ImageView imgIcon;
        View iconContainer;
        TextView tvTitle, tvMessage, tvTime;
        View viewUnreadDot;

        public DefaultViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.img_icon);
            iconContainer = itemView.findViewById(R.id.icon_container);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvMessage = itemView.findViewById(R.id.tv_message);
            tvTime = itemView.findViewById(R.id.tv_time);
            viewUnreadDot = itemView.findViewById(R.id.view_unread_dot);
        }

        public void bind(final Alert alert) {
            String type = alert.getType() != null ? alert.getType() : "Notification";
            tvTitle.setText(getFriendlyTitle(type));

            // Clean old messages
            String msg = alert.getMessage() != null ? alert.getMessage() : "";
            msg = msg.replaceAll("\\(Qty:.*Floor:.*\\)", "").trim();
            tvMessage.setText(msg);

            long now = System.currentTimeMillis();
            long timestamp = alert.getTimestamp() <= 0 ? now : alert.getTimestamp();
            if (tvTime != null) tvTime.setText(DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.MINUTE_IN_MILLIS));

            if (!alert.isRead()) {
                if (viewUnreadDot != null) viewUnreadDot.setVisibility(View.VISIBLE);
                tvTitle.setTypeface(null, Typeface.BOLD);
            } else {
                if (viewUnreadDot != null) viewUnreadDot.setVisibility(View.GONE);
                tvTitle.setTypeface(null, Typeface.NORMAL);
            }

            if (imgIcon != null) {
                imgIcon.setColorFilter(Color.parseColor("#6200EE"));
                if (type.equals("PO_RECEIVED")) imgIcon.setColorFilter(Color.parseColor("#4CAF50"));
            }

            String exactCategory = determineCategory(alert.getType());
            itemView.setOnClickListener(v -> listener.onNotificationClick(alert, null, exactCategory));
        }
    }

    // ==========================================
    // VIEW HOLDER 2: LOW STOCK ITEM
    // ==========================================
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

        public void bind(Alert alert, Product p) {
            boolean isCritical = alert.getType().contains("CRITICAL") || alert.getType().contains("FLOOR");

            // Apply solid Red or Orange backgrounds
            if (isCritical) {
                ((CardView) itemView).setCardBackgroundColor(Color.parseColor("#D32F2F")); // Solid Red
            } else {
                ((CardView) itemView).setCardBackgroundColor(Color.parseColor("#F57C00")); // Solid Orange
            }

            // Force ALL text to pure white so it is perfectly readable!
            tvProductName.setTextColor(Color.WHITE);
            tvCategory.setTextColor(Color.WHITE);
            tvStockInfo.setTextColor(Color.WHITE);
            tvCurrentStock.setTextColor(Color.WHITE);

            if (p != null) {
                tvProductName.setText(p.getProductName() != null ? p.getProductName() : "Unknown");
                tvCategory.setText(getFriendlyTitle(alert.getType()));
                tvCurrentStock.setText(String.valueOf(p.getQuantity()));

                // Clean short text without QTY/FLOOR junk
                String levelStr = isCritical ? "Critical Level" : "Reorder Level";
                tvStockInfo.setText(p.getQuantity() + " left (" + levelStr + ")");

                String img = p.getImageUrl() != null && !p.getImageUrl().isEmpty() ? p.getImageUrl() : p.getImagePath();
                if (img != null && !img.isEmpty()) Glide.with(context).load(img).placeholder(R.drawable.ic_image_placeholder).centerCrop().into(ivProductImage);
                else ivProductImage.setImageResource(R.drawable.ic_image_placeholder);
            } else {
                String cleanMsg = alert.getMessage() != null ? alert.getMessage() : "";
                cleanMsg = cleanMsg.replaceAll("\\(Qty:.*Floor:.*\\)", "").trim();
                tvProductName.setText(cleanMsg);

                tvCategory.setText(getFriendlyTitle(alert.getType()));
                tvStockInfo.setText("Tap to view details");
                tvCurrentStock.setText("!");
                ivProductImage.setImageResource(R.drawable.ic_image_placeholder);
            }

            String exactCategory = determineCategory(alert.getType());
            itemView.setOnClickListener(v -> listener.onNotificationClick(alert, p, exactCategory));
        }
    }

    // ==========================================
    // VIEW HOLDER 3: NEAR EXPIRY ITEM
    // ==========================================
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
                String cleanMsg = alert.getMessage() != null ? alert.getMessage() : "";
                tvProductName.setText(cleanMsg);
                tvCategory.setText(getFriendlyTitle(alert.getType()));
                tvExpiryDate.setText("Tap to view details");
                tvDaysRemaining.setText("");
                tvStatusLabel.setText("!");
                ivProductImage.setImageResource(R.drawable.ic_image_placeholder);
            }

            String exactCategory = determineCategory(alert.getType());
            itemView.setOnClickListener(v -> listener.onNotificationClick(alert, p, exactCategory));
        }

        public void bindProductOnly(Product p) {
            applyExpiryStyling();
            populateProductData(p);
            itemView.setOnClickListener(v -> listener.onNotificationClick(null, p, "Expiration"));
        }

        private void applyExpiryStyling() {
            ((CardView) itemView).setCardBackgroundColor(Color.parseColor("#D32F2F")); // Solid Red

            // Force ALL text to pure white
            tvProductName.setTextColor(Color.WHITE);
            tvCategory.setTextColor(Color.WHITE);
            tvExpiryDate.setTextColor(Color.WHITE);
            tvDaysRemaining.setTextColor(Color.WHITE);
            tvStatusLabel.setTextColor(Color.WHITE);
            if (ivStatusIcon != null) ivStatusIcon.setColorFilter(Color.WHITE);
        }

        private void populateProductData(Product p) {
            tvProductName.setText(p.getProductName() != null ? p.getProductName() : "Unknown");
            tvCategory.setText(p.getCategoryName() != null ? p.getCategoryName() : "No Category");

            long expiry = p.getExpiryDate();
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