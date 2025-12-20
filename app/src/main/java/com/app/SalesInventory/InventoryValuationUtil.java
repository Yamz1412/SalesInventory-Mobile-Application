package com.app.SalesInventory;

public class InventoryValuationUtil {
    public static final double DEFAULT_NORMAL_PROFIT_PERCENT = 20.0;

    public static double computeNRV(double estimatedSellingPrice, double costToComplete, double sellingCosts) {
        double nrv = estimatedSellingPrice - costToComplete - sellingCosts;
        if (Double.isNaN(nrv) || Double.isInfinite(nrv)) return 0.0;
        return Math.max(0.0, nrv);
    }

    public static double computeCeiling(double estimatedSellingPrice, double costToComplete, double sellingCosts) {
        return computeNRV(estimatedSellingPrice, costToComplete, sellingCosts);
    }

    public static double computeFloor(double nrv, double normalProfitPercent, double estimatedSellingPrice) {
        double profit = (normalProfitPercent / 100.0) * estimatedSellingPrice;
        double floor = nrv - profit;
        if (Double.isNaN(floor) || Double.isInfinite(floor)) return 0.0;
        return Math.max(0.0, floor);
    }

    public static double applyLCM(double marketValuePerUnit, double estimatedSellingPrice, double costToComplete, double sellingCosts, double normalProfitPercent) {
        double ceiling = computeCeiling(estimatedSellingPrice, costToComplete, sellingCosts);
        double floor = computeFloor(ceiling, normalProfitPercent, estimatedSellingPrice);
        if (Double.isNaN(marketValuePerUnit) || Double.isInfinite(marketValuePerUnit)) marketValuePerUnit = 0.0;
        if (marketValuePerUnit > ceiling) return ceiling;
        if (marketValuePerUnit < floor) return floor;
        return marketValuePerUnit;
    }

    public static double applyLCMWithDefaults(double marketValuePerUnit, double estimatedSellingPrice) {
        return applyLCM(marketValuePerUnit, estimatedSellingPrice, 0.0, 0.0, DEFAULT_NORMAL_PROFIT_PERCENT);
    }
}