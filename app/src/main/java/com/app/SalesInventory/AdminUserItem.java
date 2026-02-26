package com.app.SalesInventory;

public class AdminUserItem {
    public String uid;
    public String name;
    public String email;
    public String phone;
    public String role;
    public boolean approved;

    public AdminUserItem() {}

    public AdminUserItem(String uid, String name, String email, String phone, String role, boolean approved) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.role = role;
        this.approved = approved;
    }

    public String getUid() { return uid; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getRole() { return role; }
    public boolean isApproved() { return approved; }

    public void setUid(String uid) { this.uid = uid; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setRole(String role) { this.role = role; }
    public void setApproved(boolean approved) { this.approved = approved; }
}