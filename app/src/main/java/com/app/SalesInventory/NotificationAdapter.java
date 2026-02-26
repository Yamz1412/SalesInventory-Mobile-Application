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
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.ViewHolder> {

    private List<Alert> alerts = new ArrayList<>();
    private final OnNotificationClickListener listener;
    private final Context context;

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

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_notification, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Alert alert = alerts.get(position);
        holder.bind(alert);
    }

    @Override
    public int getItemCount() {
        return alerts.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgIcon;
        View iconContainer;
        TextView tvTitle, tvMessage, tvTime;
        View viewUnreadDot;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.img_icon);
            iconContainer = itemView.findViewById(R.id.icon_container);
            tvTitle = itemView.findViewById(R.id.tv_title);
            tvMessage = itemView.findViewById(R.id.tv_message);
            tvTime = itemView.findViewById(R.id.tv_time);
            viewUnreadDot = itemView.findViewById(R.id.view_unread_dot);
        }

        public void bind(final Alert alert) {
            // Set Text
            String type = alert.getType() != null ? alert.getType() : "Notification";
            tvTitle.setText(getFriendlyTitle(type));
            tvMessage.setText(alert.getMessage());

            // Set Time (e.g., "5 mins ago")
            long now = System.currentTimeMillis();
            long timestamp = alert.getTimestamp();
            if (timestamp <= 0) timestamp = now;

            CharSequence ago = DateUtils.getRelativeTimeSpanString(timestamp, now, DateUtils.MINUTE_IN_MILLIS);
            tvTime.setText(ago);

            // Set Unread Dot
            if (!alert.isRead()) {
                viewUnreadDot.setVisibility(View.VISIBLE);
                tvTitle.setTextColor(Color.BLACK);
            } else {
                viewUnreadDot.setVisibility(View.GONE);
                tvTitle.setTextColor(Color.parseColor("#424242"));
            }

            // Set Icon Color
            configureIcon(type);

            itemView.setOnClickListener(v -> listener.onNotificationClick(alert));
        }

        private String getFriendlyTitle(String type) {
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

        private void configureIcon(String type) {
            imgIcon.setColorFilter(Color.parseColor("#6200EE")); // Default Purple
            switch (type) {
                case "LOW_STOCK":
                case "CRITICAL_STOCK":
                    imgIcon.setColorFilter(Color.parseColor("#FF9800")); // Orange
                    break;
                case "EXPIRED":
                case "EXPIRY_3_DAYS":
                    imgIcon.setColorFilter(Color.parseColor("#D32F2F")); // Red
                    break;
                case "PO_RECEIVED":
                    imgIcon.setColorFilter(Color.parseColor("#4CAF50")); // Green
                    break;
            }
        }
    }
}