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

        // FIX: Accept the new double format and format it nicely for the user
        double qty = product.getQuantity();
        String qtyDisplay = (qty % 1 == 0) ? String.valueOf((long) qty) : String.valueOf(qty);

        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Critical Stock Alert");
        builder.setMessage("Product \"" + name + "\" is at critical level.\nCurrent quantity: " + qtyDisplay + "\n\nPlease restock soon.");

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