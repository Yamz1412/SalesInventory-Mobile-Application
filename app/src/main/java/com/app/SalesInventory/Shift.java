package com.app.SalesInventory;

import java.util.HashMap;
import java.util.Map;

public class Shift {
    private String shiftId;
    private String cashierId;
    private String cashierName;
    private long startTime;
    private long endTime;
    private double startingCash;
    private double cashSales;
    private double ePaymentSales;
    private double refunds;
    private double expectedCash;
    private double actualCash;
    private double difference;
    private String status;
    // ADDED: Tracks if the shift is currently running for real-time sync
    private boolean active;

    public Shift() {}

    public String getShiftId() { return shiftId; }
    public void setShiftId(String shiftId) { this.shiftId = shiftId; }
    public String getCashierId() { return cashierId; }
    public void setCashierId(String cashierId) { this.cashierId = cashierId; }
    public String getCashierName() { return cashierName; }
    public void setCashierName(String cashierName) { this.cashierName = cashierName; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public double getStartingCash() { return startingCash; }
    public void setStartingCash(double startingCash) { this.startingCash = startingCash; }
    public double getCashSales() { return cashSales; }
    public void setCashSales(double cashSales) { this.cashSales = cashSales; }
    public double getEPaymentSales() { return ePaymentSales; }
    public void setEPaymentSales(double ePaymentSales) { this.ePaymentSales = ePaymentSales; }
    public double getRefunds() { return refunds; }
    public void setRefunds(double refunds) { this.refunds = refunds; }
    public double getExpectedCash() { return expectedCash; }
    public void setExpectedCash(double expectedCash) { this.expectedCash = expectedCash; }
    public double getActualCash() { return actualCash; }
    public void setActualCash(double actualCash) { this.actualCash = actualCash; }
    public double getDifference() { return difference; }
    public void setDifference(double difference) { this.difference = difference; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // ADDED: Getter and Setter for the active status
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("shiftId", shiftId);
        map.put("cashierId", cashierId);
        map.put("cashierName", cashierName);
        map.put("startTime", startTime);
        map.put("endTime", endTime);
        map.put("startingCash", startingCash);
        map.put("cashSales", cashSales);
        map.put("ePaymentSales", ePaymentSales);
        map.put("refunds", refunds);
        map.put("expectedCash", expectedCash);
        map.put("actualCash", actualCash);
        map.put("difference", difference);
        map.put("status", status);
        map.put("active", active); // ADDED: Include active state in Firestore mapping
        return map;
    }
}