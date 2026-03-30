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
     * Checks if the conversion is standard volume or weight.
     */
    private static boolean isStandardConversion(String u1, String u2) {
        if (u1.equals("l") && (u2.equals("ml") || u2.equals("oz"))) return true;
        if (u1.equals("ml") && (u2.equals("l") || u2.equals("oz"))) return true;
        if (u1.equals("oz") && u2.equals("ml")) return true;
        if (u1.equals("kg") && u2.equals("g")) return true;
        if (u1.equals("g") && u2.equals("kg")) return true;
        return false;
    }

    public static Object[] convertBaseInventoryUnit(double currentInventoryQty, String inventoryUnit, String targetSalesUnit, int piecesPerUnit) {
        String invUnit = standardizeUnit(inventoryUnit);
        String salesUnit = standardizeUnit(targetSalesUnit);

        double convertedQty = currentInventoryQty;
        String newUnit = invUnit;

        // Standard Volume Conversions
        if (invUnit.equals("l") && (salesUnit.equals("ml") || salesUnit.equals("oz"))) {
            convertedQty *= 1000.0;
            newUnit = "ml";
        }
        // Standard Weight Conversions
        else if (invUnit.equals("kg") && salesUnit.equals("g")) {
            convertedQty *= 1000.0;
            newUnit = "g";
        }
        // CUSTOM BULK CONVERSIONS (e.g., bottle -> pump, pack -> scoop, tub -> scoop)
        else if (piecesPerUnit > 1 && !invUnit.equals(salesUnit) && !isStandardConversion(invUnit, salesUnit)) {
            convertedQty *= piecesPerUnit;
            newUnit = salesUnit;
        }

        return new Object[]{convertedQty, newUnit};
    }

    public static double calculateDeductionAmount(double salesQuantity, String invUnit, String salesUnit, int piecesPerUnit) {
        invUnit = standardizeUnit(invUnit);
        salesUnit = standardizeUnit(salesUnit);
        double finalDeduction = salesQuantity;

        if (invUnit.equals(salesUnit)) {
            return finalDeduction;
        }

        // Upwards conversions (sales unit is larger than inventory unit)
        if (invUnit.equals("ml") && salesUnit.equals("l")) {
            finalDeduction *= 1000.0;
        } else if (invUnit.equals("g") && salesUnit.equals("kg")) {
            finalDeduction *= 1000.0;
        }
        // Downwards conversions
        else if (invUnit.equals("l") && salesUnit.equals("ml")) {
            finalDeduction /= 1000.0;
        } else if (invUnit.equals("kg") && salesUnit.equals("g")) {
            finalDeduction /= 1000.0;
        }
        // Cross conversions (Ounces to ML)
        else if (invUnit.equals("ml") && salesUnit.equals("oz")) {
            finalDeduction *= 29.5735;
        } else if (invUnit.equals("oz") && salesUnit.equals("ml")) {
            finalDeduction /= 29.5735;
        }
        // CUSTOM SUB-UNIT DEDUCTIONS (e.g., Sales uses 'scoop', Inventory uses 'pack' or 'tub')
        else if (piecesPerUnit > 1 && !isStandardConversion(invUnit, salesUnit)) {
            finalDeduction /= piecesPerUnit;
        }

        return finalDeduction;
    }

    public static double calculateNewStock(double currentQty, double deductAmt) {
        return Math.max(0.0, currentQty - deductAmt);
    }
}