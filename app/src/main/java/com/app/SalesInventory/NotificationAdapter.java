package com.app.SalesInventory;

import android.content.Context;
import android.graphics.Color;
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

    private List<Alert> alerts = new ArrayList<>();
    private List<Product> products = new ArrayList<>();
    private final OnNotificationClickListener listener;
    private final Context context;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

    public interface OnNotificationClickListener {
        void onNotificationClick(Alert alert);
    }

    public NotificationAdapter(Context context, OnNotificationClickListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setAlerts(List<Alert> newAlerts) {
        this.alerts = newAlerts != null ? newAlerts : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setProducts(List<Product> products) {
        this.products = products != null ? products : new ArrayList<>();
        notifyDataSetChanged();
    }

    private Product findProductForAlert(Alert alert) {
        try {
            String pid = alert.getProductId();
            if (pid != null && !pid.isEmpty()) {
                for (Product p : products) {
                    if (p.getProductId().equals(pid)) return p;
                }
            }
        } catch (Exception ignored) {}

        // Fallback: match product name from message string if ID is missing
        if (alert.getMessage() != null) {
            for (Product p : products) {
                if (p.getProductName() != null && alert.getMessage().toLowerCase().contains(p.getProductName().toLowerCase())) {
                    return p;
                }
            }
        }
        return null;
    }

    @Override
    public int getItemViewType(int position) {
        String type = alerts.get(position).getType();
        if (type != null) {
            if (type.equals("LOW_STOCK") || type.equals("CRITICAL_STOCK")) return TYPE_LOW_STOCK;
            if (type.contains("EXPIRY") || type.equals("EXPIRED")) return TYPE_NEAR_EXPIRY;
        }
        return TYPE_DEFAULT;
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
        Alert alert = alerts.get(position);
        Product product = findProductForAlert(alert);

        if (holder instanceof LowStockViewHolder) {
            ((LowStockViewHolder) holder).bind(alert, product);
        } else if (holder instanceof NearExpiryViewHolder) {
            ((NearExpiryViewHolder) holder).bind(alert, product);
        } else if (holder instanceof DefaultViewHolder) {
            ((DefaultViewHolder) holder).bind(alert);
        }
    }

    @Override
    public int getItemCount() {
        return alerts.size();
    }

    private String getFriendlyTitle(String type) {
        if (type == null) return "Notification";
        switch (type) {
            case "LOW_STOCK": return "Low Stock Alert";
            case "CRITICAL_STOCK": return "Critical Stock Level";
            case "EXPIRY_7_DAYS": return "Expiring Soon (7 Days)";
            case "EXPIRY_3_DAYS": return "Expiring Soon (3 Days)";
            case "EXPIRED": return "Product Expired";
            case "PO_RECEIVED": return "Shipment Received";
            default: return type.replace("_", " ");
        }
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
            tvMessage.setText(alert.getMessage());

            long now = System.currentTimeMillis();
            long timestamp = alert.getTimestamp() <= 0 ? now : alert.getTimestamp();
            tvTime.setText(DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.MINUTE_IN_MILLIS));

            if (!alert.isRead()) {
                viewUnreadDot.setVisibility(View.VISIBLE);
                tvTitle.setTextColor(Color.BLACK);
            } else {
                viewUnreadDot.setVisibility(View.GONE);
                tvTitle.setTextColor(Color.parseColor("#424242"));
            }

            imgIcon.setColorFilter(Color.parseColor("#6200EE")); // Default Purple
            if (type.equals("PO_RECEIVED")) imgIcon.setColorFilter(Color.parseColor("#4CAF50")); // Green

            itemView.setOnClickListener(v -> listener.onNotificationClick(alert));
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
            // Tint background orange if unread
            if (!alert.isRead()) ((CardView) itemView).setCardBackgroundColor(Color.parseColor("#FFF3E0"));
            else ((CardView) itemView).setCardBackgroundColor(Color.WHITE);

            if (p != null) {
                tvProductName.setText(p.getProductName() != null ? p.getProductName() : "Unknown");
                tvCategory.setText(p.getCategoryName() != null ? p.getCategoryName() : "No Category");
                tvCurrentStock.setText(String.valueOf(p.getQuantity()));
                tvStockInfo.setText("Stock: " + p.getQuantity() + " | Reorder: " + p.getReorderLevel() + " | Critical: " + p.getCriticalLevel());

                String img = p.getImageUrl() != null && !p.getImageUrl().isEmpty() ? p.getImageUrl() : p.getImagePath();
                if (img != null && !img.isEmpty()) Glide.with(context).load(img).placeholder(R.drawable.ic_image_placeholder).centerCrop().into(ivProductImage);
                else ivProductImage.setImageResource(R.drawable.ic_image_placeholder);
            } else {
                tvProductName.setText(alert.getMessage());
                tvCategory.setText(getFriendlyTitle(alert.getType()));
                tvStockInfo.setText("Tap to view details");
                tvCurrentStock.setText("!");
                ivProductImage.setImageResource(R.drawable.ic_image_placeholder);
            }
            itemView.setOnClickListener(v -> listener.onNotificationClick(alert));
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
            // Tint background red if unread
            if (!alert.isRead()) ((CardView) itemView).setCardBackgroundColor(Color.parseColor("#FFEBEE"));
            else ((CardView) itemView).setCardBackgroundColor(Color.WHITE);

            if (p != null) {
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
                        ivStatusIcon.setColorFilter(Color.parseColor("#D32F2F"));
                    } else {
                        tvDaysRemaining.setText("In " + days + " days");
                        tvStatusLabel.setText("Soon");
                        ivStatusIcon.setColorFilter(Color.parseColor("#FF9800"));
                    }
                }

                String img = p.getImageUrl() != null && !p.getImageUrl().isEmpty() ? p.getImageUrl() : p.getImagePath();
                if (img != null && !img.isEmpty()) Glide.with(context).load(img).placeholder(R.drawable.ic_image_placeholder).centerCrop().into(ivProductImage);
                else ivProductImage.setImageResource(R.drawable.ic_image_placeholder);
            } else {
                tvProductName.setText(alert.getMessage());
                tvCategory.setText(getFriendlyTitle(alert.getType()));
                tvExpiryDate.setText("Tap to view details");
                tvDaysRemaining.setText("");
                tvStatusLabel.setText("!");
                ivProductImage.setImageResource(R.drawable.ic_image_placeholder);
            }
            itemView.setOnClickListener(v -> listener.onNotificationClick(alert));
        }
    }
}