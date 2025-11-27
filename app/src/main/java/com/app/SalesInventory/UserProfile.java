package com.app.SalesInventory;

public class UserProfile {
    private String uid;
    private String email;
    private String name;
    private String phone;
    private String role;
    private boolean approved;
    private long createdAt;

    public UserProfile() {}

    public UserProfile(String uid, String email, String name, String phone, String role, boolean approved, long createdAt) {
        this.uid = uid;
        this.email = email;
        this.name = name;
        this.phone = phone;
        this.role = role;
        this.approved = approved;
        this.createdAt = createdAt;
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getEmail() { return email == null ? "" : email; }
    public void setEmail(String email) { this.email = email; }

    public String getName() { return name == null ? "" : name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone == null ? "" : phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getRole() { return role == null ? "Staff" : role; }
    public void setRole(String role) { this.role = role; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}