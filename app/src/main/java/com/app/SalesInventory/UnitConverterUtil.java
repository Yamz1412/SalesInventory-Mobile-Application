package com.app.SalesInventory;

import java.util.Locale;

public class UnitConverterUtil {

    /**
     * Standardizes unit strings to lowercase for accurate comparisons.
     */
    public static String standardizeUnit(String unit) {
        if (unit == null || unit.isEmpty()) return "";
        return unit.toLowerCase(Locale.ROOT).trim();
    }

    /**
     * Determines if the base inventory unit needs to be converted down
     * (e.g., if Inventory is in Liters, but we are selling in ml).
     * Returns an Object array: [ConvertedQuantity (double), NewUnitString (String), UnitChanged (boolean)]
     */
    public static Object[] convertBaseInventoryUnit(double currentInventoryQty, String inventoryUnit, String targetSalesUnit, int piecesPerUnit) {
        String invUnit = standardizeUnit(inventoryUnit);
        String salesUnit = standardizeUnit(targetSalesUnit);

        double convertedQty = currentInventoryQty;
        String newUnit = invUnit;
        boolean unitChanged = false;

        // Volume
        if (invUnit.equals("l") && (salesUnit.equals("ml") || salesUnit.equals("oz"))) {
            convertedQty *= 1000.0;
            newUnit = "ml";
            unitChanged = true;
        }
        // Weight
        else if (invUnit.equals("kg") && salesUnit.equals("g")) {
            convertedQty *= 1000.0;
            newUnit = "g";
            unitChanged = true;
        }
        // Packaging
        else if ((invUnit.equals("box") || invUnit.equals("pack")) && salesUnit.equals("pcs")) {
            convertedQty *= (piecesPerUnit > 0 ? piecesPerUnit : 1);
            newUnit = "pcs";
            unitChanged = true;
        }

        return new Object[]{convertedQty, newUnit, unitChanged};
    }

    /**
     * Calculates exactly how much to deduct from the inventory based on the units.
     * Handles complex conversions like deducting ounces from milliliters.
     */
    public static double calculateDeductionAmount(double baseDeductionAmount, String inventoryUnit, String salesUnit, int piecesPerUnit) {
        String invUnit = standardizeUnit(inventoryUnit);
        String sUnit = standardizeUnit(salesUnit);
        double finalDeduction = baseDeductionAmount;

        // Upwards conversion (Selling 100ml but deducting from Liters directly)
        if (invUnit.equals("l") && sUnit.equals("ml")) {
            finalDeduction /= 1000.0;
        } else if (invUnit.equals("kg") && sUnit.equals("g")) {
            finalDeduction /= 1000.0;
        }

        // Downwards / Cross-conversions
        else if (invUnit.equals("ml") && sUnit.equals("oz")) {
            finalDeduction *= 29.5735;
        } else if (invUnit.equals("ml") && sUnit.equals("l")) {
            finalDeduction *= 1000.0;
        } else if (invUnit.equals("g") && sUnit.equals("kg")) {
            finalDeduction *= 1000.0;
        } else if (invUnit.equals("pcs") && (sUnit.equals("box") || sUnit.equals("pack"))) {
            finalDeduction *= (piecesPerUnit > 0 ? piecesPerUnit : 1);
        } else if (invUnit.equals("oz") && sUnit.equals("ml")) {
            finalDeduction /= 29.5735;
        }

        return finalDeduction;
    }

    /**
     * Calculates the final clamped integer quantity to save back to the database.
     */
    public static int calculateNewStock(double currentStock, double deductAmount) {
        return (int) Math.max(0, Math.ceil(currentStock - deductAmount));
    }
}