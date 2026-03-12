package com.app.SalesInventory;

public class Supplier {
    private String id;
    private String name;
    private String contact;
    private String email;
    private String address;
    private String categories;
    private String ownerAdminId;
    private long dateAdded;

    public Supplier() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContact() { return contact; }
    public void setContact(String contact) { this.contact = contact; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCategories() { return categories; }
    public void setCategories(String categories) { this.categories = categories; }

    public String getOwnerAdminId() { return ownerAdminId; }
    public void setOwnerAdminId(String ownerAdminId) { this.ownerAdminId = ownerAdminId; }

    public long getDateAdded() { return dateAdded; }
    public void setDateAdded(long dateAdded) { this.dateAdded = dateAdded; }
}