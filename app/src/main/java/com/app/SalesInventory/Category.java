package com.app.SalesInventory;

public class Category {
    private String color;
    private String categoryId;
    private String categoryName;
    private String description;
    private long timestamp;

    public Category() {
    }

    public Category(String categoryId, String categoryName, String description, long timestamp) {
        this.categoryId = categoryId;
        this.categoryName = categoryName;
        this.description = description;
        this.timestamp = timestamp;
    }

    public String getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(String categoryId) {
        this.categoryId = categoryId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
}