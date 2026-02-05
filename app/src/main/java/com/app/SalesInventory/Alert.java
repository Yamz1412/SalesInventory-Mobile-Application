package com.app.SalesInventory;

import com.google.firebase.firestore.Exclude;
import com.google.firebase.firestore.ServerTimestamp;
import java.util.Date;

public class Alert {
    private String id;
    private String productId;
    private String type;
    private String message;
    private boolean read;

    @ServerTimestamp
    public Date timestamp;

    @ServerTimestamp
    public Date createdAt;

    private String source;
    private String createdBy;
    private String lastModifiedBy;

    public Alert() {}

    public Alert(String productId, String type, String message, boolean read, long timestamp) {
        this.productId = productId;
        this.type = type;
        this.message = message;
        this.read = read;
        this.timestamp = new Date(timestamp);
        this.createdAt = new Date(timestamp);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    @Exclude
    public long getTimestamp() {
        return timestamp != null ? timestamp.getTime() : 0L;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = new Date(timestamp);
    }

    public Date getTimestampDate() {
        return timestamp;
    }

    public void setTimestampDate(Date timestamp) {
        this.timestamp = timestamp;
    }

    @Exclude
    public long getCreatedAt() {
        return createdAt != null ? createdAt.getTime() : 0L;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = new Date(createdAt);
    }

    public Date getCreatedAtDate() {
        return createdAt;
    }

    public void setCreatedAtDate(Date createdAt) {
        this.createdAt = createdAt;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getLastModifiedBy() {
        return lastModifiedBy;
    }

    public void setLastModifiedBy(String lastModifiedBy) {
        this.lastModifiedBy = lastModifiedBy;
    }
}