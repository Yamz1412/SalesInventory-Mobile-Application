package com.app.SalesInventory;

import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class Category {
    private String color;
    private String categoryId;
    private String categoryName;
    private String description;

    @ServerTimestamp
    public Date timestamp;

    private String type;
    private boolean active;

    public Category() {
    }

    public Category(String categoryId, String categoryName, String description, long timestamp) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.description = description;
        this.timestamp = new Date(timestamp);
        this.type = "Raw";
        this.active = true;
    }

    public Category(String categoryId, String categoryName, String description, long timestamp, String type, boolean active, String color) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.description = description;
        this.timestamp = new Date(timestamp);
        this.type = type == null || type.isEmpty() ? "Raw" : type;
        this.active = active;
        this.color = color;
    }

    public String getCategoryId() { return categoryId; }
    public void setCategoryId(String categoryId) { this.categoryId = categoryId; }

    public String getCategoryName() { return categoryName == null ? "" : categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }

    public String getDescription() { return description == null ? "" : description; }
    public void setDescription(String description) { this.description = description; }

    public long getTimestamp() { return timestamp != null ? timestamp.getTime() : 0L; }
    public void setTimestamp(long timestamp) { this.timestamp = new Date(timestamp); }

    public String getColor() { return color == null ? "" : color; }
    public void setColor(String color) { this.color = color; }

    public String getType() { return type == null || type.isEmpty() ? "Raw" : type; }
    public void setType(String type) { this.type = type; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public String getId() { return getCategoryId(); }
    public String getName() { return getCategoryName(); }
}