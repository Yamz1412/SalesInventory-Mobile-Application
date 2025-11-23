package com.app.SalesInventory;

public class Product {

    private String Name;
    private String Lot;
    private String Code;
    private String Price;
    private String SellPrice;
    private String UserId;
    private String amount;
    private String date; // Purchase date

    // NEW FIELDS based on Revisions
    private String Category;      //
    private String MinStockLevel; // For "Critical Order"/Notification
    private String ExpiryDate;    // For "Nearly expired"

    public Product() {
    }

    // Updated Constructor for Stock/Purchase
    public Product(String name, String lot, String code, String price, String sellPrice,
                   String userId, String amount, String date,
                   String category, String minStockLevel, String expiryDate) {
        Name = name;
        Lot = lot;
        Code = code;
        Price = price;
        SellPrice = sellPrice;
        UserId = userId;
        this.amount = amount;
        this.date = date;
        Category = category;
        MinStockLevel = minStockLevel;
        ExpiryDate = expiryDate;
    }

    // Constructor for List Display (simplified)
    public Product(String name, String lot, String sellPrice, String amount, String category) {
        Name = name;
        Lot = lot;
        SellPrice = sellPrice;
        this.amount = amount;
        Category = category;
    }

    // Getters and Setters
    public String getName() { return Name; }
    public void setName(String name) { Name = name; }

    public String getLot() { return Lot; }
    public void setLot(String lot) { Lot = lot; }

    public String getCode() { return Code; }
    public void setCode(String code) { Code = code; }

    public String getPrice() { return Price; }
    public void setPrice(String price) { Price = price; }

    public String getSellPrice() { return SellPrice; }
    public void setSellPrice(String sellPrice) { SellPrice = sellPrice; }

    public String getUserId() { return UserId; }
    public void setUserId(String userId) { UserId = userId; }

    public String getAmount() { return amount; }
    public void setAmount(String amount) { this.amount = amount; }

    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    // NEW GETTERS AND SETTERS
    public String getCategory() { return Category; }
    public void setCategory(String category) { Category = category; }

    public String getMinStockLevel() { return MinStockLevel; }
    public void setMinStockLevel(String minStockLevel) { MinStockLevel = minStockLevel; }

    public String getExpiryDate() { return ExpiryDate; }
    public void setExpiryDate(String expiryDate) { ExpiryDate = expiryDate; }
}