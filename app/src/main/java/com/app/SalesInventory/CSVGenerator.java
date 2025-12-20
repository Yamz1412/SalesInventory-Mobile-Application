package com.app.SalesInventory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.io.OutputStream;

public class CSVGenerator {
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public void generateStockValueReportCSV(File outputFile, List<StockValueReport> reports) throws Exception {
        FileOutputStream fos = new FileOutputStream(outputFile);
        try {
            generateStockValueReportCSV(fos, reports);
        } finally {
            try { fos.close(); } catch (Exception ignored) {}
        }
    }

    public void generateStockValueReportCSV(OutputStream outputStream, List<StockValueReport> reports) throws Exception {
        Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, "UTF-8"));
        int totalRecords = reports == null ? 0 : reports.size();
        writer.append("STOCK VALUE REPORT\n");
        writer.append("Generated on,").append(dateFormat.format(new Date())).append("\n");
        writer.append("Total Records,").append(String.valueOf(totalRecords)).append("\n\n");
        double totalCostValue = 0;
        double totalSellingValue = 0;
        double totalProfit = 0;
        if (reports != null) {
            for (StockValueReport report : reports) {
                totalCostValue += report.getTotalCostValue();
                totalSellingValue += report.getTotalSellingValue();
                totalProfit += report.getProfit();
            }
        }
        writer.append("SUMMARY\n");
        writer.append("Total Cost Value,").append(String.format(Locale.getDefault(), "%.2f", totalCostValue)).append("\n");
        writer.append("Total Selling Value,").append(String.format(Locale.getDefault(), "%.2f", totalSellingValue)).append("\n");
        writer.append("Total Profit,").append(String.format(Locale.getDefault(), "%.2f", totalProfit)).append("\n");
        writer.append("Overall Margin,").append(String.format(Locale.getDefault(), "%.2f%%",
                totalSellingValue > 0 ? (totalProfit / totalSellingValue) * 100 : 0)).append("\n\n");
        writer.append("Product Name,Category,Quantity,Floor Level,Cost Price,Selling Price,Total Cost Value,Total Selling Value,Profit,Profit Margin,Stock Status\n");
        if (reports != null) {
            for (StockValueReport report : reports) {
                writer.append(escapeCsv(report.getProductName())).append(",");
                writer.append(escapeCsv(report.getCategory())).append(",");
                writer.append(String.valueOf(report.getQuantity())).append(",");
                writer.append(String.valueOf(report.getFloorLevel())).append(",");
                writer.append(String.format(Locale.getDefault(), "%.2f", report.getCostPrice())).append(",");
                writer.append(String.format(Locale.getDefault(), "%.2f", report.getSellingPrice())).append(",");
                writer.append(String.format(Locale.getDefault(), "%.2f", report.getTotalCostValue())).append(",");
                writer.append(String.format(Locale.getDefault(), "%.2f", report.getTotalSellingValue())).append(",");
                writer.append(String.format(Locale.getDefault(), "%.2f", report.getProfit())).append(",");
                writer.append(escapeCsv(report.getProfitMargin())).append(",");
                writer.append(escapeCsv(report.getStockStatus())).append("\n");
            }
        }
        writer.flush();
        writer.close();
    }

    public void generateStockMovementReportCSV(File outputFile, List<StockMovementReport> reports,
                                               int totalReceived, int totalSold, int totalAdjustments) throws Exception {
        FileOutputStream fos = new FileOutputStream(outputFile);
        try {
            generateStockMovementReportCSV(fos, reports, totalReceived, totalSold, totalAdjustments);
        } finally {
            try { fos.close(); } catch (Exception ignored) {}
        }
    }

    public void generateStockMovementReportCSV(OutputStream outputStream, List<StockMovementReport> reports,
                                               int totalReceived, int totalSold, int totalAdjustments) throws Exception {
        Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, "UTF-8"));
        int totalRecords = reports == null ? 0 : reports.size();
        writer.append("STOCK MOVEMENT REPORT\n");
        writer.append("Generated on,").append(dateFormat.format(new Date())).append("\n");
        writer.append("Total Records,").append(String.valueOf(totalRecords)).append("\n\n");
        writer.append("SUMMARY\n");
        writer.append("Total Received,").append(String.valueOf(totalReceived)).append(" units\n");
        writer.append("Total Sold,").append(String.valueOf(totalSold)).append(" units\n");
        writer.append("Total Adjustments,").append(String.valueOf(totalAdjustments)).append(" units\n\n");
        writer.append("Product Name,Category,Opening Stock,Received,Sold,Adjusted,Closing Stock,Total Movement,Movement Percentage\n");
        if (reports != null) {
            for (StockMovementReport report : reports) {
                writer.append(escapeCsv(report.getProductName())).append(",");
                writer.append(escapeCsv(report.getCategory())).append(",");
                writer.append(String.valueOf(report.getOpeningStock())).append(",");
                writer.append(String.valueOf(report.getReceived())).append(",");
                writer.append(String.valueOf(report.getSold())).append(",");
                writer.append(String.valueOf(report.getAdjusted())).append(",");
                writer.append(String.valueOf(report.getClosingStock())).append(",");
                writer.append(String.valueOf(report.getTotalMovement())).append(",");
                writer.append(String.format(Locale.getDefault(), "%.2f%%", report.getMovementPercentage())).append("\n");
            }
        }
        writer.flush();
        writer.close();
    }

    public void generateAdjustmentSummaryReportCSV(File outputFile, List<AdjustmentSummaryData> summaryList,
                                                   int totalAdjustments, int totalAdditions, int totalRemovals) throws Exception {
        FileOutputStream fos = new FileOutputStream(outputFile);
        try {
            generateAdjustmentSummaryReportCSV(fos, summaryList, totalAdjustments, totalAdditions, totalRemovals);
        } finally {
            try { fos.close(); } catch (Exception ignored) {}
        }
    }

    public void generateAdjustmentSummaryReportCSV(OutputStream outputStream, List<AdjustmentSummaryData> summaryList,
                                                   int totalAdjustments, int totalAdditions, int totalRemovals) throws Exception {
        Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, "UTF-8"));
        int totalRecords = summaryList == null ? 0 : summaryList.size();
        writer.append("ADJUSTMENT SUMMARY REPORT\n");
        writer.append("Generated on,").append(dateFormat.format(new Date())).append("\n");
        writer.append("Total Records,").append(String.valueOf(totalRecords)).append("\n\n");
        writer.append("SUMMARY\n");
        writer.append("Total Adjustments,").append(String.valueOf(totalAdjustments)).append("\n");
        writer.append("Total Units Added,").append(String.valueOf(totalAdditions)).append("\n");
        writer.append("Total Units Removed,").append(String.valueOf(totalRemovals)).append("\n");
        writer.append("Net Change,").append(String.valueOf(totalAdditions - totalRemovals)).append("\n\n");
        writer.append("Product Name,Total Adjustments,Total Added,Total Removed,Net Change,Addition Reasons,Removal Reasons\n");
        if (summaryList != null) {
            for (AdjustmentSummaryData summary : summaryList) {
                int additions = summary.getTotalAdditions();
                int removals = summary.getTotalRemovals();
                int net = additions - removals;
                writer.append(escapeCsv(summary.getProductName())).append(",");
                writer.append(String.valueOf(summary.getTotalAdjustments())).append(",");
                writer.append(String.valueOf(additions)).append(",");
                writer.append(String.valueOf(removals)).append(",");
                writer.append(String.valueOf(net)).append(",");
                writer.append(escapeCsv(String.join("; ", summary.getAdditionReasons()))).append(",");
                writer.append(escapeCsv(String.join("; ", summary.getRemovalReasons()))).append("\n");
            }
        }
        writer.flush();
        writer.close();
    }

    public void generateInventoryMovementsCSV(File outputFile, List<InventoryMovement> movements) throws Exception {
        FileOutputStream fos = new FileOutputStream(outputFile);
        try {
            generateInventoryMovementsCSV(fos, movements);
        } finally {
            try { fos.close(); } catch (Exception ignored) {}
        }
    }

    public void generateInventoryMovementsCSV(OutputStream outputStream, List<InventoryMovement> movements) throws Exception {
        Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, "UTF-8"));
        writer.append("INVENTORY MOVEMENTS\n");
        writer.append("Generated on,").append(dateFormat.format(new Date())).append("\n\n");
        writer.append("Movement ID,Product ID,Product Name,Change,Quantity Before,Quantity After,Type,Reason,Remarks,Timestamp,Performed By\n");
        if (movements != null) {
            for (InventoryMovement m : movements) {
                String ts = m.getTimestamp() > 0 ? dateFormat.format(new Date(m.getTimestamp())) : "";
                writer.append(escapeCsv(m.getMovementId())).append(",");
                writer.append(escapeCsv(m.getProductId())).append(",");
                writer.append(escapeCsv(m.getProductName())).append(",");
                writer.append(String.valueOf(m.getChange())).append(",");
                writer.append(String.valueOf(m.getQuantityBefore())).append(",");
                writer.append(String.valueOf(m.getQuantityAfter())).append(",");
                writer.append(escapeCsv(m.getType())).append(",");
                writer.append(escapeCsv(m.getReason())).append(",");
                writer.append(escapeCsv(m.getRemarks())).append(",");
                writer.append(escapeCsv(ts)).append(",");
                writer.append(escapeCsv(m.getPerformedBy())).append("\n");
            }
        }
        writer.flush();
        writer.close();
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}