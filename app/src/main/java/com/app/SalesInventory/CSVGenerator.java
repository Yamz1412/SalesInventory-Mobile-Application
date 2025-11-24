package com.app.SalesInventory;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class CSVGenerator {

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    /**
     * Generate Stock Value Report CSV
     */
    public void generateStockValueReportCSV(File outputFile, List<StockValueReport> reports) throws Exception {
        FileWriter writer = new FileWriter(outputFile);

        // Header with metadata
        writer.append("STOCK VALUE REPORT\n");
        writer.append("Generated on,").append(dateFormat.format(new Date())).append("\n");
        writer.append("Total Records,").append(String.valueOf(reports.size())).append("\n\n");

        // Summary
        double totalCostValue = 0;
        double totalSellingValue = 0;
        double totalProfit = 0;

        for (StockValueReport report : reports) {
            totalCostValue += report.getTotalCostValue();
            totalSellingValue += report.getTotalSellingValue();
            totalProfit += report.getProfit();
        }

        writer.append("SUMMARY\n");
        writer.append("Total Cost Value,").append(String.format("%.2f", totalCostValue)).append("\n");
        writer.append("Total Selling Value,").append(String.format("%.2f", totalSellingValue)).append("\n");
        writer.append("Total Profit,").append(String.format("%.2f", totalProfit)).append("\n");
        writer.append("Overall Margin,").append(String.format("%.2f%%",
                totalSellingValue > 0 ? (totalProfit / totalSellingValue) * 100 : 0)).append("\n\n");

        // Column headers
        writer.append("Product Name,Category,Quantity,Cost Price,Selling Price,Total Cost Value,Total Selling Value,Profit,Profit Margin,Stock Status\n");

        // Data rows
        for (StockValueReport report : reports) {
            writer.append(escapeCsv(report.getProductName())).append(",");
            writer.append(escapeCsv(report.getCategory())).append(",");
            writer.append(String.valueOf(report.getQuantity())).append(",");
            writer.append(String.format("%.2f", report.getCostPrice())).append(",");
            writer.append(String.format("%.2f", report.getSellingPrice())).append(",");
            writer.append(String.format("%.2f", report.getTotalCostValue())).append(",");
            writer.append(String.format("%.2f", report.getTotalSellingValue())).append(",");
            writer.append(String.format("%.2f", report.getProfit())).append(",");
            writer.append(report.getProfitMargin()).append(",");
            writer.append(report.getStockStatus()).append("\n");
        }

        writer.flush();
        writer.close();
    }

    /**
     * Generate Stock Movement Report CSV
     */
    public void generateStockMovementReportCSV(File outputFile, List<StockMovementReport> reports,
                                               int totalReceived, int totalSold, int totalAdjustments) throws Exception {
        FileWriter writer = new FileWriter(outputFile);

        // Header with metadata
        writer.append("STOCK MOVEMENT REPORT\n");
        writer.append("Generated on,").append(dateFormat.format(new Date())).append("\n");
        writer.append("Total Records,").append(String.valueOf(reports.size())).append("\n\n");

        // Summary
        writer.append("SUMMARY\n");
        writer.append("Total Received,").append(String.valueOf(totalReceived)).append(" units\n");
        writer.append("Total Sold,").append(String.valueOf(totalSold)).append(" units\n");
        writer.append("Total Adjustments,").append(String.valueOf(totalAdjustments)).append(" units\n\n");

        // Column headers
        writer.append("Product Name,Category,Opening Stock,Received,Sold,Adjusted,Closing Stock,Total Movement,Movement Percentage\n");

        // Data rows
        for (StockMovementReport report : reports) {
            writer.append(escapeCsv(report.getProductName())).append(",");
            writer.append(escapeCsv(report.getCategory())).append(",");
            writer.append(String.valueOf(report.getOpeningStock())).append(",");
            writer.append(String.valueOf(report.getReceived())).append(",");
            writer.append(String.valueOf(report.getSold())).append(",");
            writer.append(String.valueOf(report.getAdjusted())).append(",");
            writer.append(String.valueOf(report.getClosingStock())).append(",");
            writer.append(String.valueOf(report.getTotalMovement())).append(",");
            writer.append(String.format("%.2f%%", report.getMovementPercentage())).append("\n");
        }

        writer.flush();
        writer.close();
    }

    /**
     * Generate Adjustment Summary Report CSV
     */
    public void generateAdjustmentSummaryReportCSV(File outputFile, List<AdjustmentSummaryData> summaryList,
                                                   int totalAdjustments, int totalAdditions, int totalRemovals) throws Exception {
        FileWriter writer = new FileWriter(outputFile);

        // Header with metadata
        writer.append("ADJUSTMENT SUMMARY REPORT\n");
        writer.append("Generated on,").append(dateFormat.format(new Date())).append("\n");
        writer.append("Total Records,").append(String.valueOf(summaryList.size())).append("\n\n");

        // Summary
        writer.append("SUMMARY\n");
        writer.append("Total Adjustments,").append(String.valueOf(totalAdjustments)).append("\n");
        writer.append("Total Units Added,").append(String.valueOf(totalAdditions)).append("\n");
        writer.append("Total Units Removed,").append(String.valueOf(totalRemovals)).append("\n");
        writer.append("Net Change,").append(String.valueOf(totalAdditions - totalRemovals)).append("\n\n");

        // Column headers
        writer.append("Product Name,Total Adjustments,Total Added,Total Removed,Net Change,Addition Reasons,Removal Reasons\n");

        // Data rows
        for (AdjustmentSummaryData summary : summaryList) {
            writer.append(escapeCsv(summary.getProductName())).append(",");
            writer.append(String.valueOf(summary.getTotalAdjustments())).append(",");
            writer.append(String.valueOf(summary.getTotalAdditions())).append(",");
            writer.append(String.valueOf(summary.getTotalRemovals())).append(",");
            writer.append(String.valueOf(summary.getNetChange())).append(",");
            writer.append(escapeCsv(String.join("; ", summary.getAdditionReasons()))).append(",");
            writer.append(escapeCsv(String.join("; ", summary.getRemovalReasons()))).append("\n");
        }

        writer.flush();
        writer.close();
    }

    /**
     * Escape special characters for CSV
     */
    private String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}