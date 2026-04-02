package com.app.SalesInventory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CartManager {

    private static CartManager instance;

    public static class CartItem {
        public String productId;
        public String productName;
        public double unitPrice;
        public int quantity; // The number of "drinks/items" ordered
        public double stock; // The amount of raw inventory left (now supports decimals)
        public String size;
        public String addon;
        public String excludedIngredients; // NEW FIELD

        public CartItem(String productId, String productName, double unitPrice, int quantity, double stock, String size, String addon, String excludedIngredients) {
            this.productId = productId;
            this.productName = productName;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
            this.stock = stock;
            this.size = size;
            this.addon = addon;
            this.excludedIngredients = excludedIngredients; // ASSIGN NEW FIELD
        }

        public double getLineTotal() {
            return unitPrice * quantity;
        }
    }

    private final List<CartItem> items = new ArrayList<>();

    private CartManager() {}

    public static synchronized CartManager getInstance() {
        if (instance == null) {
            instance = new CartManager();
        }
        return instance;
    }

    public synchronized void clear() {
        items.clear();
    }

    public synchronized List<CartItem> getItems() {
        return new ArrayList<>(items);
    }

    public synchronized boolean addItem(String productId, String productName, double unitPrice, int quantity, double stock, String size, String addon, String excludedIngredients) {
        for (CartItem item : items) {
            boolean sameId = item.productId.equals(productId);
            boolean sameSize = (item.size == null && size == null) || (item.size != null && item.size.equals(size));
            boolean sameAddon = (item.addon == null && addon == null) || (item.addon != null && item.addon.equals(addon));
            boolean sameExclusions = (item.excludedIngredients == null && excludedIngredients == null) ||
                    (item.excludedIngredients != null && item.excludedIngredients.equals(excludedIngredients));

            if (sameId && sameSize && sameAddon && sameExclusions) {
                item.quantity += quantity;
                item.stock = stock;
                return true;
            }
        }
        items.add(new CartItem(productId, productName, unitPrice, quantity, stock, size, addon, excludedIngredients));
        return true;
    }

    public synchronized boolean updateQuantity(String productId, String size, String addon, int quantity) {
        Iterator<CartItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            CartItem item = iterator.next();
            boolean sameSize = (item.size == null && size == null) || (item.size != null && item.size.equals(size));
            boolean sameAddon = (item.addon == null && addon == null) || (item.addon != null && item.addon.equals(addon));

            if (item.productId.equals(productId) && sameSize && sameAddon) {
                if (quantity <= 0) {
                    iterator.remove();
                    return true;
                } else if (quantity > item.stock) {
                    return false;
                } else {
                    item.quantity = quantity;
                    return true;
                }
            }
        }
        return false;
    }

    public synchronized void removeItemById(String productId) {
        Iterator<CartItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            CartItem item = iterator.next();
            if (item.productId.equals(productId)) {
                iterator.remove();
                return;
            }
        }
    }

    public synchronized double getSubtotal() {
        double total = 0;
        for (CartItem item : items) {
            total += item.getLineTotal();
        }
        return total;
    }
}