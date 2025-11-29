package com.app.SalesInventory;

public class RecentActivity {
    private String activityId;
    private String title;
    private String description;
    private String activityType;
    private String status;
    private long timestamp;
    private String iconResId;

    public RecentActivity(String activityId, String title, String description,
                          String activityType, String status, long timestamp) {
        this.activityId = activityId;
        this.title = title;
        this.description = description;
        this.activityType = activityType;
        this.status = status;
        this.timestamp = timestamp;
    }

    public String getActivityId() {
        return activityId;
    }

    public void setActivityId(String activityId) {
        this.activityId = activityId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this. title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getActivityType() {
        return activityType;
    }

    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getIconResId() {
        return iconResId;
    }

    public void setIconResId(String iconResId) {
        this.iconResId = iconResId;
    }
}