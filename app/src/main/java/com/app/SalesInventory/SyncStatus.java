package com.app.SalesInventory;

public class SyncStatus {
    public enum Status {
        SYNCED,
        SYNCING,
        PENDING,
        OFFLINE,
        ERROR
    }

    private Status status;
    private String message;
    private long timestamp;

    public SyncStatus() {
        this.status = Status.OFFLINE;
        this.message = "Initializing...";
        this.timestamp = System.currentTimeMillis();
    }

    public SyncStatus(Status status, String message) {
        this.status = status;
        this.message = message;
        this.timestamp = System.currentTimeMillis();
    }

    // Getters and Setters
    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
        this.timestamp = System.currentTimeMillis();
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public boolean isSynced() {
        return status == Status.SYNCED;
    }

    public boolean isSyncing() {
        return status == Status.SYNCING;
    }

    public boolean isOffline() {
        return status == Status.OFFLINE;
    }

    public boolean hasError() {
        return status == Status.ERROR;
    }

    @Override
    public String toString() {
        return "SyncStatus{" +
                "status=" + status +
                ", message='" + message + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}