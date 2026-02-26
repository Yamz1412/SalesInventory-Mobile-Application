package com.app.SalesInventory;

import android.content.Context;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PDFGenerator {

    private Context context;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private PdfFont boldFont;
    private PdfFont regularFont;

    public PDFGenerator(Context context) throws Exception {
        this.context = context;
        this.boldFont = PdfFontFactory.createFont();
        this.regularFont = PdfFontFactory.createFont();
    }

    public void generateOverallSummaryReportPDF(File outputFile,
                                                int totalProducts,
                                                int lowOrCriticalProducts,
                                                double inventoryValue,
                                                int totalTransactions,
                                                double totalSalesAmount,
                                                int deliveryCount,
                                                double deliverySalesAmount) throws Exception {
        OutputStream os = new FileOutputStream(outputFile);
        try {
            generateOverallSummaryReportPDF(os, totalProducts, lowOrCriticalProducts, inventoryValue, totalTransactions, totalSalesAmount, deliveryCount, deliverySalesAmount);
        } finally {
            try { os.close(); } catch (Exception ignored) {}
        }
    }

    public void generateOverallSummaryReportPDF(OutputStream outputStream,
                                                int totalProducts,
                                                int lowOrCriticalProducts,
                                                double inventoryValue,
                                                int totalTransactions,
                                                double totalSalesAmount,
                                                int deliveryCount,
                                                double deliverySalesAmount) throws Exception {
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        Text titleText = new Text("OVERALL BUSINESS SUMMARY REPORT");
        Paragraph title = new Paragraph(titleText)
                .setFont(boldFont)
                .setFontSize(20)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(title);
        Paragraph dateGen = new Paragraph("Generated on: " + dateFormat.format(new Date()))
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(dateGen);
        Paragraph kpiTitle = new Paragraph("KEY PERFORMANCE INDICATORS")
                .setFont(boldFont)
                .setFontSize(14)
                .setMarginTop(10);
        document.add(kpiTitle);
        document.add(new Paragraph("Total Products: " + totalProducts));
        document.add(new Paragraph("Low/Critical Products: " + lowOrCriticalProducts));
        document.add(new Paragraph(String.format(Locale.getDefault(), "Inventory Value (Selling): ₱%.2f", inventoryValue)));
        document.add(new Paragraph("Total Sales Transactions: " + totalTransactions));
        document.add(new Paragraph(String.format(Locale.getDefault(), "Total Sales Amount: ₱%.2f", totalSalesAmount)));
        document.add(new Paragraph("Delivery Orders: " + deliveryCount));
        document.add(new Paragraph(String.format(Locale.getDefault(), "Delivery Sales Amount: ₱%.2f", deliverySalesAmount)));
        Paragraph chartTitle = new Paragraph("VISUAL SUMMARY")
                .setFont(boldFont)
                .setFontSize(14)
                .setMarginTop(12);
        document.add(chartTitle);
        double maxAmount = Math.max(totalSalesAmount, deliverySalesAmount);
        if (maxAmount <= 0) maxAmount = 1;
        int barWidthSales = (int) Math.round((totalSalesAmount / maxAmount) * 40);
        int barWidthDelivery = (int) Math.round((deliverySalesAmount / maxAmount) * 40);
        StringBuilder salesBar = new StringBuilder();
        for (int i = 0; i < barWidthSales; i++) salesBar.append("█");
        StringBuilder deliveryBar = new StringBuilder();
        for (int i = 0; i < barWidthDelivery; i++) deliveryBar.append("█");
        document.add(new Paragraph("Sales vs Delivery (relative scale)").setFontSize(11));
        document.add(new Paragraph("Sales    : " + salesBar + "  " + String.format(Locale.getDefault(), "₱%.2f", totalSalesAmount)));
        document.add(new Paragraph("Delivery : " + deliveryBar + "  " + String.format(Locale.getDefault(), "₱%.2f", deliverySalesAmount)));
        Paragraph invChartTitle = new Paragraph("Inventory Status (approximate ratio)")
                .setFontSize(11)
                .setMarginTop(8);
        document.add(invChartTitle);
        int normalProducts = Math.max(0, totalProducts - lowOrCriticalProducts);
        int totalForRatio = Math.max(1, totalProducts);
        int barWidthNormal = (int) Math.round(((double) normalProducts / totalForRatio) * 40);
        int barWidthLow = (int) Math.round(((double) lowOrCriticalProducts / totalForRatio) * 40);
        StringBuilder normalBar = new StringBuilder();
        for (int i = 0; i < barWidthNormal; i++) normalBar.append("█");
        StringBuilder lowBar = new StringBuilder();
        for (int i = 0; i < barWidthLow; i++) lowBar.append("█");
        document.add(new Paragraph("Normal   : " + normalBar + "  " + normalProducts + " products"));
        document.add(new Paragraph("Low/Crit : " + lowBar + "  " + lowOrCriticalProducts + " products"));
        document.add(new Paragraph("\nThis report summarizes overall sales, delivery, and inventory performance.")
                .setFontSize(10));
        document.add(new Paragraph("\nReport generated by: Sales Inventory System")
                .setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER));
        document.close();
    }

    public void generateStockValueReportPDF(File outputFile, List<StockValueReport> reports) throws Exception {
        OutputStream os = new FileOutputStream(outputFile);
        try {
            generateStockValueReportPDF(os, reports);
        } finally {
            try { os.close(); } catch (Exception ignored) {}
        }
    }

    public void generateStockValueReportPDF(OutputStream outputStream, List<StockValueReport> reports) throws Exception {
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        Text titleText = new Text("STOCK VALUE REPORT");
        Paragraph title = new Paragraph(titleText)
                .setFont(boldFont)
                .setFontSize(20)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(title);
        Paragraph dateGen = new Paragraph("Generated on: " + dateFormat.format(new Date()))
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(dateGen);
        Text summaryText = new Text("SUMMARY");
        Paragraph summaryTitle = new Paragraph(summaryText)
                .setFont(boldFont)
                .setFontSize(14);
        document.add(summaryTitle);
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
        document.add(new Paragraph(String.format("Total Cost Value: ₱%.2f", totalCostValue)));
        document.add(new Paragraph(String.format("Total Selling Value: ₱%.2f", totalSellingValue)));
        document.add(new Paragraph(String.format("Total Profit: ₱%.2f", totalProfit)));
        document.add(new Paragraph(String.format("Overall Margin: %.2f%%",
                totalSellingValue > 0 ? (totalProfit / totalSellingValue) * 100 : 0)));
        Text detailsText = new Text("DETAILED REPORT");
        Paragraph detailsTitle = new Paragraph(detailsText)
                .setFont(boldFont)
                .setFontSize(14)
                .setMarginTop(10);
        document.add(detailsTitle);
        Table table = new Table(UnitValue.createPercentArray(new float[]{2, 1.5f, 1, 1.2f, 1.2f, 1, 0.8f}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.addHeaderCell(createHeaderCell("Product Name"));
        table.addHeaderCell(createHeaderCell("Category"));
        table.addHeaderCell(createHeaderCell("Qty"));
        table.addHeaderCell(createHeaderCell("Cost Value"));
        table.addHeaderCell(createHeaderCell("Selling Value"));
        table.addHeaderCell(createHeaderCell("Profit"));
        table.addHeaderCell(createHeaderCell("Margin"));
        if (reports != null) {
            for (StockValueReport report : reports) {
                table.addCell(report.getProductName());
                table.addCell(report.getCategory());
                table.addCell(String.valueOf(report.getQuantity()));
                table.addCell(String.format("₱%.2f", report.getTotalCostValue()));
                table.addCell(String.format("₱%.2f", report.getTotalSellingValue()));
                table.addCell(String.format("₱%.2f", report.getProfit()));
                table.addCell(report.getProfitMargin());
            }
        }
        document.add(table);
        document.add(new Paragraph("\n\nReport generated by: Sales Inventory System")
                .setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER));
        document.close();
    }

    public void generateStockMovementReportPDF(File outputFile, List<StockMovementReport> reports,
                                               int totalReceived, int totalSold, int totalAdjustments) throws Exception {
        OutputStream os = new FileOutputStream(outputFile);
        try {
            generateStockMovementReportPDF(os, reports, totalReceived, totalSold, totalAdjustments);
        } finally {
            try { os.close(); } catch (Exception ignored) {}
        }
    }

    public void generateStockMovementReportPDF(OutputStream outputStream, List<StockMovementReport> reports,
                                               int totalReceived, int totalSold, int totalAdjustments) throws Exception {
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        Text titleText = new Text("STOCK MOVEMENT REPORT");
        Paragraph title = new Paragraph(titleText)
                .setFont(boldFont)
                .setFontSize(20)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(title);
        Paragraph dateGen = new Paragraph("Generated on: " + dateFormat.format(new Date()))
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(dateGen);
        Text summaryText = new Text("SUMMARY");
        Paragraph summaryTitle = new Paragraph(summaryText)
                .setFont(boldFont)
                .setFontSize(14);
        document.add(summaryTitle);
        document.add(new Paragraph("Total Received: " + totalReceived + " units"));
        document.add(new Paragraph("Total Sold: " + totalSold + " units"));
        document.add(new Paragraph("Total Adjustments: " + totalAdjustments + " units"));
        Text detailsText = new Text("DETAILED REPORT");
        Paragraph detailsTitle = new Paragraph(detailsText)
                .setFont(boldFont)
                .setFontSize(14)
                .setMarginTop(10);
        document.add(detailsTitle);
        Table table = new Table(UnitValue.createPercentArray(new float[]{1.5f, 1.2f, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.addHeaderCell(createHeaderCell("Product"));
        table.addHeaderCell(createHeaderCell("Category"));
        table.addHeaderCell(createHeaderCell("Opening"));
        table.addHeaderCell(createHeaderCell("Received"));
        table.addHeaderCell(createHeaderCell("Sold"));
        table.addHeaderCell(createHeaderCell("Adjusted"));
        table.addHeaderCell(createHeaderCell("Closing"));
        table.addHeaderCell(createHeaderCell("Movement %"));
        if (reports != null) {
            for (StockMovementReport report : reports) {
                table.addCell(report.getProductName());
                table.addCell(report.getCategory());
                table.addCell(String.valueOf(report.getOpeningStock()));
                table.addCell(String.valueOf(report.getReceived()));
                table.addCell(String.valueOf(report.getSold()));
                table.addCell(String.valueOf(report.getAdjusted()));
                table.addCell(String.valueOf(report.getClosingStock()));
                table.addCell(String.format("%.2f%%", report.getMovementPercentage()));
            }
        }
        document.add(table);
        document.add(new Paragraph("\n\nReport generated by: Sales Inventory System")
                .setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER));
        document.close();
    }

    public void generateAdjustmentSummaryReportPDF(File outputFile, List<AdjustmentSummaryData> summaryList,
                                                   int totalAdjustments, int totalAdditions, int totalRemovals) throws Exception {
        OutputStream os = new FileOutputStream(outputFile);
        try {
            generateAdjustmentSummaryReportPDF(os, summaryList, totalAdjustments, totalAdditions, totalRemovals);
        } finally {
            try { os.close(); } catch (Exception ignored) {}
        }
    }

    public void generateAdjustmentSummaryReportPDF(OutputStream outputStream, List<AdjustmentSummaryData> summaryList,
                                                   int totalAdjustments, int totalAdditions, int totalRemovals) throws Exception {
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        Text titleText = new Text("ADJUSTMENT SUMMARY REPORT");
        Paragraph title = new Paragraph(titleText)
                .setFont(boldFont)
                .setFontSize(20)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(title);
        Paragraph dateGen = new Paragraph("Generated on: " + dateFormat.format(new Date()))
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(dateGen);
        Text summaryText = new Text("SUMMARY");
        Paragraph summaryTitle = new Paragraph(summaryText)
                .setFont(boldFont)
                .setFontSize(14);
        document.add(summaryTitle);
        document.add(new Paragraph("Total Adjustments: " + totalAdjustments));
        document.add(new Paragraph("Total Units Added: +" + totalAdditions));
        document.add(new Paragraph("Total Units Removed: -" + totalRemovals));
        document.add(new Paragraph("Net Change: " + (totalAdditions - totalRemovals)));
        Text detailsText = new Text("DETAILED REPORT");
        Paragraph detailsTitle = new Paragraph(detailsText)
                .setFont(boldFont)
                .setFontSize(14)
                .setMarginTop(10);
        document.add(detailsTitle);
        Table table = new Table(UnitValue.createPercentArray(new float[]{2, 1, 1, 1, 1, 2}));
        table.setWidth(UnitValue.createPercentValue(100));
        table.addHeaderCell(createHeaderCell("Product"));
        table.addHeaderCell(createHeaderCell("Total Adj"));
        table.addHeaderCell(createHeaderCell("Added"));
        table.addHeaderCell(createHeaderCell("Removed"));
        table.addHeaderCell(createHeaderCell("Net Change"));
        table.addHeaderCell(createHeaderCell("Reasons"));
        if (summaryList != null) {
            for (AdjustmentSummaryData summary : summaryList) {
                table.addCell(summary.getProductName());
                table.addCell(String.valueOf(summary.getTotalAdjustments()));
                table.addCell("+" + summary.getTotalAdditions());
                table.addCell("-" + summary.getTotalRemovals());
                int net = summary.getTotalAdditions() - summary.getTotalRemovals();
                table.addCell(String.valueOf(net));
                StringBuilder reasons = new StringBuilder();
                if (!summary.getAdditionReasons().isEmpty()) {
                    reasons.append("Added: ").append(String.join(", ", summary.getAdditionReasons()));
                }
                if (!summary.getRemovalReasons().isEmpty()) {
                    if (reasons.length() > 0) reasons.append("; ");
                    reasons.append("Removed: ").append(String.join(", ", summary.getRemovalReasons()));
                }
                table.addCell(reasons.toString());
            }
        }
        document.add(table);
        document.add(new Paragraph("\n\nReport generated by: Sales Inventory System")
                .setFontSize(9)
                .setTextAlignment(TextAlignment.CENTER));
        document.close();
    }

    private Cell createHeaderCell(String text) throws Exception {
        Text headerText = new Text(text);
        Paragraph headerParagraph = new Paragraph(headerText)
                .setFont(boldFont);
        return new Cell()
                .add(headerParagraph)
                .setTextAlignment(TextAlignment.CENTER)
                .setBackgroundColor(ColorConstants.LIGHT_GRAY);
    }

    public void generateCombinedInventoryReportPDF(OutputStream outputStream,
                                                   List<StockValueReport> stockValueReports,
                                                   List<StockMovementReport> stockMovementReports,
                                                   List<AdjustmentSummaryData> adjustmentSummaries,
                                                   int totalReceived,
                                                   int totalSold,
                                                   int totalAdjustments) throws Exception {
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        Text titleText = new Text("INVENTORY REPORTS - COMBINED");
        Paragraph title = new Paragraph(titleText)
                .setFont(boldFont)
                .setFontSize(18)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(title);
        Paragraph dateGen = new Paragraph("Generated on: " + dateFormat.format(new Date()))
                .setFontSize(10)
                .setTextAlignment(TextAlignment.CENTER);
        document.add(dateGen);
        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("1) STOCK VALUE REPORT").setFont(boldFont).setFontSize(14).setMarginBottom(6));
        if (stockValueReports == null || stockValueReports.isEmpty()) {
            document.add(new Paragraph("No stock value data available").setFontSize(10));
        } else {
            Table table1 = new Table(UnitValue.createPercentArray(new float[]{3f, 2f, 1f, 1f, 1f})).setWidth(UnitValue.createPercentValue(100));
            table1.addHeaderCell(createHeaderCell("Product"));
            table1.addHeaderCell(createHeaderCell("Category"));
            table1.addHeaderCell(createHeaderCell("Qty"));
            table1.addHeaderCell(createHeaderCell("Cost Value"));
            table1.addHeaderCell(createHeaderCell("Selling Value"));
            for (StockValueReport r : stockValueReports) {
                table1.addCell(r.getProductName());
                table1.addCell(r.getCategory());
                table1.addCell(String.valueOf(r.getQuantity()));
                table1.addCell(String.format("₱%.2f", r.getTotalCostValue()));
                table1.addCell(String.format("₱%.2f", r.getTotalSellingValue()));
            }
            document.add(table1);
        }
        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("2) STOCK MOVEMENT REPORT").setFont(boldFont).setFontSize(14).setMarginBottom(6));
        if (stockMovementReports == null || stockMovementReports.isEmpty()) {
            document.add(new Paragraph("No stock movement data available").setFontSize(10));
        } else {
            Table table2 = new Table(UnitValue.createPercentArray(new float[]{3f, 2f, 1f, 1f, 1f, 1f, 1f, 1f})).setWidth(UnitValue.createPercentValue(100));
            table2.addHeaderCell(createHeaderCell("Product"));
            table2.addHeaderCell(createHeaderCell("Category"));
            table2.addHeaderCell(createHeaderCell("Opening"));
            table2.addHeaderCell(createHeaderCell("Received"));
            table2.addHeaderCell(createHeaderCell("Sold"));
            table2.addHeaderCell(createHeaderCell("Adjusted"));
            table2.addHeaderCell(createHeaderCell("Closing"));
            table2.addHeaderCell(createHeaderCell("Movement %"));
            for (StockMovementReport r : stockMovementReports) {
                table2.addCell(r.getProductName());
                table2.addCell(r.getCategory());
                table2.addCell(String.valueOf(r.getOpeningStock()));
                table2.addCell(String.valueOf(r.getReceived()));
                table2.addCell(String.valueOf(r.getSold()));
                table2.addCell(String.valueOf(r.getAdjusted()));
                table2.addCell(String.valueOf(r.getClosingStock()));
                table2.addCell(String.format("%.2f%%", r.getMovementPercentage()));
            }
            document.add(table2);
            document.add(new Paragraph(String.format("Totals — Received: %d units | Sold: %d units | Adjusted: %d units",
                    totalReceived, totalSold, totalAdjustments)).setFontSize(10).setMarginTop(8));
        }
        document.add(new Paragraph("\n\n"));
        document.add(new Paragraph("3) ADJUSTMENT SUMMARY").setFont(boldFont).setFontSize(14).setMarginBottom(6));
        if (adjustmentSummaries == null || adjustmentSummaries.isEmpty()) {
            document.add(new Paragraph("No adjustment summary data available").setFontSize(10));
        } else {
            Table table3 = new Table(UnitValue.createPercentArray(new float[]{3f, 1f, 1f, 1f, 2f})).setWidth(UnitValue.createPercentValue(100));
            table3.addHeaderCell(createHeaderCell("Product"));
            table3.addHeaderCell(createHeaderCell("Total Adj"));
            table3.addHeaderCell(createHeaderCell("Added"));
            table3.addHeaderCell(createHeaderCell("Removed"));
            table3.addHeaderCell(createHeaderCell("Reasons"));
            for (AdjustmentSummaryData s : adjustmentSummaries) {
                table3.addCell(s.getProductName());
                table3.addCell(String.valueOf(s.getTotalAdjustments()));
                table3.addCell(String.valueOf(s.getTotalAdditions()));
                table3.addCell(String.valueOf(s.getTotalRemovals()));
                StringBuilder reasons = new StringBuilder();
                if (!s.getAdditionReasons().isEmpty()) reasons.append("Added: ").append(String.join(", ", s.getAdditionReasons()));
                if (!s.getRemovalReasons().isEmpty()) {
                    if (reasons.length() > 0) reasons.append("; ");
                    reasons.append("Removed: ").append(String.join(", ", s.getRemovalReasons()));
                }
                table3.addCell(reasons.toString());
            }
            document.add(table3);
        }
        document.add(new Paragraph("\n\nReport generated by: Sales Inventory System").setFontSize(9).setTextAlignment(TextAlignment.CENTER));
        document.close();
    }
}