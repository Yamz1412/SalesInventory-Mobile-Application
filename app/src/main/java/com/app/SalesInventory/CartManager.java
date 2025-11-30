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
        public int quantity;
        public int stock;
        public String size;
        public String addon;

        public CartItem(String productId, String productName, double unitPrice, int quantity, int stock, String size, String addon) {
            this.productId = productId;
            this.productName = productName;
            this.unitPrice = unitPrice;
            this.quantity = quantity;
            this.stock = stock;
            this.size = size;
            this.addon = addon;
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

    public synchronized void addItem(String productId, String productName, double unitPrice, int quantity, int stock, String size, String addon) {
        items.add(new CartItem(productId, productName, unitPrice, quantity, stock, size, addon));
    }

    public synchronized void updateQuantity(String productId, String size, String addon, int quantity) {
        Iterator<CartItem> iterator = items.iterator();
        while (iterator.hasNext()) {
            CartItem item = iterator.next();
            boolean sameSize = (item.size == null && size == null) || (item.size != null && item.size.equals(size));
            boolean sameAddon = (item.addon == null && addon == null) || (item.addon != null && item.addon.equals(addon));
            if (item.productId.equals(productId) && sameSize && sameAddon) {
                if (quantity <= 0) {
                    iterator.remove();
                } else {
                    item.quantity = quantity;
                }
                return;
            }
        }
    }

    public synchronized void removeItem(CartItem target) {
        items.remove(target);
    }

    public synchronized double getSubtotal() {
        double total = 0;
        for (CartItem item : items) {
            total += item.getLineTotal();
        }
        return total;
    }
}