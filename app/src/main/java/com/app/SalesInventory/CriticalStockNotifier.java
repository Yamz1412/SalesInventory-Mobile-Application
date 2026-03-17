package com.app.SalesInventory;

import android.app.Activity;
import android.app.AlertDialog;

import java.util.HashSet;
import java.util.Set;

public class CriticalStockNotifier {

    private static CriticalStockNotifier instance;
    private final Set<String> dismissedForProduct = new HashSet<>();

    private CriticalStockNotifier() {
    }

    public static synchronized CriticalStockNotifier getInstance() {
        if (instance == null) {
            instance = new CriticalStockNotifier();
        }
        return instance;
    }

    public void showCriticalDialog(Activity activity, Product product) {
        if (activity == null || activity.isFinishing()) return;
        String productId = product.getProductId();
        if (productId == null) return;
        if (dismissedForProduct.contains(productId)) return;

        String name = product.getProductName() == null ? "" : product.getProductName();

        // Safely format decimal quantities
        double qty = product.getQuantity();
        String qtyDisplay = (qty % 1 == 0) ? String.valueOf((long) qty) : String.valueOf(qty);

        // Dynamically determine if it's Low Stock (Reorder) or Critical
        String alertTitle = "Stock Alert";
        String alertMessage = "";

        if (product.getCriticalLevel() > 0 && qty <= product.getCriticalLevel()) {
            alertTitle = "🚨 CRITICAL STOCK ALERT";
            alertMessage = "Product \"" + name + "\" has hit the Minimum Critical Level!\n\nCurrent quantity: " + qtyDisplay + "\n\nYou must restock immediately to avoid stockouts.";
        } else if (product.getReorderLevel() > 0 && qty <= product.getReorderLevel()) {
            alertTitle = "⚠️ LOW STOCK (REORDER)";
            alertMessage = "Product \"" + name + "\" has reached the Safety Reorder Point.\n\nCurrent quantity: " + qtyDisplay + "\n\nPlease prepare to order more from your supplier.";
        } else {
            // If it's not actually low, don't show the dialog
            return;
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(alertTitle);
        builder.setMessage(alertMessage);

        builder.setPositiveButton("Confirm", (dialog, which) -> {
            dismissedForProduct.add(productId);
            dialog.dismiss();
        });

        builder.setNegativeButton("Remind Me Later", (dialog, which) -> dialog.dismiss());

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    public void clearForProduct(String productId) {
        if (productId == null) return;
        dismissedForProduct.remove(productId);
    }

    public void resetAll() {
        dismissedForProduct.clear();
    }
}