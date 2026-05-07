package com.app.SalesInventory;

import java.util.Locale;

public class UnitConverterUtil {

    public static String standardizeUnit(String unit) {
        if (unit == null || unit.isEmpty()) return "";
        return unit.toLowerCase(Locale.ROOT).trim();
    }

    private static boolean isStandardConversion(String u1, String u2) {
        if (u1.equals("l") && (u2.equals("ml") || u2.equals("oz"))) return true;
        if (u1.equals("ml") && (u2.equals("l") || u2.equals("oz"))) return true;
        if (u1.equals("oz") && u2.equals("ml")) return true;
        if (u1.equals("kg") && u2.equals("g")) return true;
        if (u1.equals("g") && u2.equals("kg")) return true;
        return false;
    }

    public static Object[] convertBaseInventoryUnit(double currentInventoryQty, String inventoryUnit, String targetSalesUnit, int piecesPerUnit) {
        // FIX: We DO NOT change the base unit in the database anymore.
        // The system now uses fractional deduction (e.g. 4.5 Boxes) to safely preserve the main unit.
        return new Object[]{currentInventoryQty, standardizeUnit(inventoryUnit), false};
    }

    // NEW METHOD: Safely calculate the true cost per unit if the user entered a bulk price but named it a "piece"
    public static double calculateTrueUnitCost(double costPrice, String invUnit, int piecesPerUnit) {
        invUnit = standardizeUnit(invUnit);

        // If piecesPerUnit > 1, the user bought a bulk pack (e.g. 30 eggs for ₱250).
        if (piecesPerUnit > 1) {
            // If they lazily named their inventory unit as "pcs" or "ml" instead of "tray" or "box",
            // it means the ₱250 is still the BULK price, so we must divide it to get the true per-piece price.
            boolean isSmallUnit = invUnit.equals("pcs") || invUnit.equals("pc")
                    || invUnit.equals("piece") || invUnit.equals("pieces")
                    || invUnit.equals("ml") || invUnit.equals("g") || invUnit.equals("oz");

            if (isSmallUnit) {
                return costPrice / piecesPerUnit;
            }
        }

        // Otherwise, the cost price is perfectly fine as is!
        return costPrice;
    }

    public static double calculateDeductionAmount(double salesQuantity, String invUnit, String salesUnit, int piecesPerUnit) {
        invUnit = standardizeUnit(invUnit);
        salesUnit = standardizeUnit(salesUnit);
        double finalDeduction = salesQuantity;

        if (invUnit.equals(salesUnit)) {
            return finalDeduction;
        }

        if (invUnit.equals("l") && salesUnit.equals("ml")) {
            finalDeduction = salesQuantity / 1000.0;
        } else if (invUnit.equals("kg") && salesUnit.equals("g")) {
            finalDeduction = salesQuantity / 1000.0;
        }

        else if (invUnit.equals("ml") && salesUnit.equals("l")) {
            finalDeduction *= 1000.0;
        } else if (invUnit.equals("g") && salesUnit.equals("kg")) {
            finalDeduction *= 1000.0;
        }
        // Cross conversions (Ounces to ML/L)
        else if (invUnit.equals("l") && salesUnit.equals("oz")) {
            finalDeduction /= 33.814;
        } else if (invUnit.equals("ml") && salesUnit.equals("oz")) {
            finalDeduction *= 29.5735;
        } else if (invUnit.equals("oz") && salesUnit.equals("ml")) {
            finalDeduction /= 29.5735;
        }
        else if (invUnit.equals("oz") && salesUnit.equals("l")) {
            finalDeduction *= 33.814;
        }
        else if (piecesPerUnit > 1 && !isStandardConversion(invUnit, salesUnit)) {
            finalDeduction = salesQuantity / piecesPerUnit;
        }

        return finalDeduction;
    }

    public static double calculateNewStock(double currentQty, double deductAmt) {
        return Math.max(0.0, currentQty - deductAmt);
    }
}