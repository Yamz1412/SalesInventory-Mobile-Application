package com.app.SalesInventory;

public class Alert {
    private String id;
    private String productId;
    private String type;
    private String message;
    private boolean read;
    private long timestamp;
    private String source;
    private String createdBy;

    public Alert() {}

    // --- THESE WERE MISSING ---
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    // --------------------------

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}