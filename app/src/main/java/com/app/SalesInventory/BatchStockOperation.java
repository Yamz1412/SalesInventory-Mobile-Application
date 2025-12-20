package com.app.SalesInventory;

import java.util.HashMap;
import java.util.Map;

public class BatchStockOperation {
    private String operationId;
    private String operationName;
    private String operationType;
    private int quantity;
    private String reason;
    private String remarks;
    private long createdAt;
    private String createdBy;
    private Map<String, Integer> productChanges;
    private int totalProductsAffected;
    private String status;

    public BatchStockOperation() {
        this.productChanges = new HashMap<>();
    }

    public BatchStockOperation(String operationId, String operationName, String operationType,
                               int quantity, String reason, String remarks, long createdAt,
                               String createdBy, int totalProductsAffected) {
        this.operationId = operationId;
        this.operationName = operationName;
        this.operationType = operationType;
        this.quantity = quantity;
        this.reason = reason;
        this.remarks = remarks;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
        this.totalProductsAffected = totalProductsAffected;
        this.status = "PENDING";
        this.productChanges = new HashMap<>();
    }

    public String getOperationId() { return operationId; }
    public void setOperationId(String operationId) { this.operationId = operationId; }

    public String getOperationName() { return operationName; }
    public void setOperationName(String operationName) { this.operationName = operationName; }

    public String getOperationType() { return operationType; }
    public void setOperationType(String operationType) { this.operationType = operationType; }

    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Map<String, Integer> getProductChanges() { return productChanges; }
    public void setProductChanges(Map<String, Integer> productChanges) { this.productChanges = productChanges; }

    public int getTotalProductsAffected() { return totalProductsAffected; }
    public void setTotalProductsAffected(int totalProductsAffected) { this.totalProductsAffected = totalProductsAffected; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public void addProductChange(String productId, int newQuantity) {
        productChanges.put(productId, newQuantity);
    }

    public String getOperationSummary() {
        String action = "";
        switch (operationType) {
            case "ADD":
                action = "Added " + quantity + " units";
                break;
            case "SUBTRACT":
                action = "Subtracted " + quantity + " units";
                break;
            case "SET":
                action = "Set to " + quantity + " units";
                break;
        }
        return action + " to " + totalProductsAffected + " products";
    }
}