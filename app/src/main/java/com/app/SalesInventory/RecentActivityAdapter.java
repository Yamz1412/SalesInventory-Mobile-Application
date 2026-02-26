package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RecentActivityAdapter extends RecyclerView.Adapter<RecentActivityAdapter.ViewHolder> {

    private final Context context;
    private List<RecentActivity> activities;

    public RecentActivityAdapter(Context context) {
        this.context = context;
        this.activities = new ArrayList<>();
    }

    @NonNull
    @Override
    public RecentActivityAdapter.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_recent_activity, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull RecentActivityAdapter.ViewHolder holder, int position) {
        RecentActivity activity = activities.get(position);
        holder.bind(activity, context);
    }

    @Override
    public int getItemCount() {
        return activities != null ? activities.size() : 0;
    }

    public void setActivities(List<RecentActivity> activities) {
        this.activities = activities != null ? activities : new ArrayList<>();
        notifyDataSetChanged();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final android.widget.LinearLayout root;
        private final View vStatusIndicator;
        private final android.widget.LinearLayout layoutContent;
        private final TextView tvActivityTitle;
        private final TextView tvActivityDescription;
        private final TextView tvActivityTime;

        public ViewHolder(View itemView) {
            super(itemView);
            root = itemView.findViewById(R.id.root_activity);
            vStatusIndicator = itemView.findViewById(R.id.v_status_indicator);
            layoutContent = itemView.findViewById(R.id.layout_content);
            tvActivityTitle = itemView.findViewById(R.id.tv_activity_title);
            tvActivityDescription = itemView.findViewById(R.id.tv_activity_description);
            tvActivityTime = itemView.findViewById(R.id.tv_activity_time);
        }

        public void bind(RecentActivity activity, Context context) {
            tvActivityTitle.setText(activity.getTitle());
            tvActivityDescription.setText(activity.getDescription());
            tvActivityTime.setText(getRelativeTime(activity.getTimestamp()));

            int statusColor = getStatusColor(activity.getStatus(), context);
            vStatusIndicator.setBackgroundColor(statusColor);
        }

        private String getRelativeTime(long timestamp) {
            long now = System.currentTimeMillis();
            long diff = now - timestamp;

            if (diff < 60000) {
                return "Just now";
            } else if (diff < 3600000) {
                return (diff / 60000) + " minutes ago";
            } else if (diff < 86400000) {
                return (diff / 3600000) + " hours ago";
            } else {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault());
                return sdf.format(new Date(timestamp));
            }
        }

        private int getStatusColor(String status, Context context) {
            if (status == null) return context.getColor(android.R.color.holo_blue_dark);
            switch (status) {
                case "COMPLETED":
                    return context.getColor(android.R.color.holo_green_dark);
                case "PENDING":
                    return context.getColor(android.R.color.holo_orange_dark);
                case "FAILED":
                    return context.getColor(android.R.color.holo_red_dark);
                default:
                    return context.getColor(android.R.color.holo_blue_dark);
            }
        }
    }
}