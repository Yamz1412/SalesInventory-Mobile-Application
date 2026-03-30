package com.app.SalesInventory;

import android.content.Context;
import com.itextpdf.kernel.colors.ColorConstants;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
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
        this.boldFont = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA_BOLD);
        this.regularFont = PdfFontFactory.createFont(com.itextpdf.io.font.constants.StandardFonts.HELVETICA);
    }

    public void generateOverallSummaryReportPDF(File outputFile, int totalProducts, int lowOrCriticalProducts,
                                                double inventoryValue, int totalTransactions, double totalSalesAmount,
                                                int deliveryCount, double deliverySalesAmount) throws Exception {
        OutputStream os = new FileOutputStream(outputFile);
        try {
            generateOverallSummaryReportPDF(os, totalProducts, lowOrCriticalProducts, inventoryValue, totalTransactions, totalSalesAmount, deliveryCount, deliverySalesAmount);
        } finally {
            try { os.close(); } catch (Exception ignored) {}
        }
    }

    public void generateOverallSummaryReportPDF(OutputStream outputStream, int totalProducts, int lowOrCriticalProducts,
                                                double inventoryValue, int totalTransactions, double totalSalesAmount,
                                                int deliveryCount, double deliverySalesAmount) throws Exception {
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        Text titleText = new Text("OVERALL BUSINESS SUMMARY REPORT");
        Paragraph title = new Paragraph(titleText).setFont(boldFont).setFontSize(20).setTextAlignment(TextAlignment.CENTER);
        document.add(title);
        Paragraph dateGen = new Paragraph("Generated on: " + dateFormat.format(new Date())).setFontSize(10).setTextAlignment(TextAlignment.CENTER);
        document.add(dateGen);
        Paragraph kpiTitle = new Paragraph("KEY PERFORMANCE INDICATORS").setFont(boldFont).setFontSize(14).setMarginTop(10);
        document.add(kpiTitle);
        document.add(new Paragraph("Total Products: " + totalProducts));
        document.add(new Paragraph("Low/Critical Products: " + lowOrCriticalProducts));
        document.add(new Paragraph(String.format(Locale.getDefault(), "Inventory Value (Selling): ₱%.2f", inventoryValue)));
        document.add(new Paragraph("Total Sales Transactions: " + totalTransactions));
        document.add(new Paragraph(String.format(Locale.getDefault(), "Total Sales Amount: ₱%.2f", totalSalesAmount)));
        document.add(new Paragraph("Delivery Orders: " + deliveryCount));
        document.add(new Paragraph(String.format(Locale.getDefault(), "Delivery Sales Amount: ₱%.2f", deliverySalesAmount)));
        Paragraph chartTitle = new Paragraph("VISUAL SUMMARY").setFont(boldFont).setFontSize(14).setMarginTop(12);
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
        Paragraph invChartTitle = new Paragraph("Inventory Status (approximate ratio)").setFontSize(11).setMarginTop(8);
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
        document.add(new Paragraph("\nThis report summarizes overall sales, delivery, and inventory performance.").setFontSize(10));
        document.add(new Paragraph("\nReport generated by: Sales Inventory System").setFontSize(9).setTextAlignment(TextAlignment.CENTER));
        document.close();
    }

    public void generateStockValueReportPDF(File outputFile, List<StockValueReport> reports) throws Exception {
        OutputStream os = new FileOutputStream(outputFile);
        try { generateStockValueReportPDF(os, reports); } finally { try { os.close(); } catch (Exception ignored) {} }
    }

    public void generateStockValueReportPDF(OutputStream outputStream, List<StockValueReport> reports) throws Exception {
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        document.add(new Paragraph("STOCK VALUE REPORT").setFont(boldFont).setFontSize(20).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("Generated on: " + dateFormat.format(new Date())).setFontSize(10).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("SUMMARY").setFont(boldFont).setFontSize(14));
        double totalCostValue = 0, totalSellingValue = 0, totalProfit = 0;
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
        document.add(new Paragraph(String.format("Overall Margin: %.2f%%", totalSellingValue > 0 ? (totalProfit / totalSellingValue) * 100 : 0)));
        document.add(new Paragraph("DETAILED REPORT").setFont(boldFont).setFontSize(14).setMarginTop(10));
        Table table = new Table(UnitValue.createPercentArray(new float[]{2, 1.5f, 1, 1.2f, 1.2f, 1, 0.8f})).setWidth(UnitValue.createPercentValue(100));
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
        document.add(new Paragraph("\n\nReport generated by: Sales Inventory System").setFontSize(9).setTextAlignment(TextAlignment.CENTER));
        document.close();
    }

    public void generateStockMovementReportPDF(File outputFile, List<StockMovementReport> reports, double totalReceived, double totalSold, double totalAdjustments) throws Exception {
        OutputStream os = new FileOutputStream(outputFile);
        try { generateStockMovementReportPDF(os, reports, totalReceived, totalSold, totalAdjustments); } finally { try { os.close(); } catch (Exception ignored) {} }
    }

    public void generateStockMovementReportPDF(OutputStream outputStream, List<StockMovementReport> reports, double totalReceived, double totalSold, double totalAdjustments) throws Exception {
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        document.add(new Paragraph("STOCK MOVEMENT REPORT").setFont(boldFont).setFontSize(20).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("Generated on: " + dateFormat.format(new Date())).setFontSize(10).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("SUMMARY").setFont(boldFont).setFontSize(14));

        // Updated to use Double formats
        document.add(new Paragraph(String.format(Locale.US, "Total Received: %.2f units", totalReceived)));
        document.add(new Paragraph(String.format(Locale.US, "Total Sold: %.2f units", totalSold)));
        document.add(new Paragraph(String.format(Locale.US, "Total Adjustments: %.2f units", totalAdjustments)));

        document.add(new Paragraph("DETAILED REPORT").setFont(boldFont).setFontSize(14).setMarginTop(10));
        Table table = new Table(UnitValue.createPercentArray(new float[]{1.5f, 1.2f, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f, 0.8f})).setWidth(UnitValue.createPercentValue(100));
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
                table.addCell(String.format(Locale.US, "%.2f%%", report.getMovementPercentage()));
            }
        }
        document.add(table);
        document.add(new Paragraph("\n\nReport generated by: Sales Inventory System").setFontSize(9).setTextAlignment(TextAlignment.CENTER));
        document.close();
    }

    public void generateAdjustmentSummaryReportPDF(File outputFile, List<AdjustmentSummaryData> summaryList, int totalAdjustments, int totalAdditions, int totalRemovals) throws Exception {
        OutputStream os = new FileOutputStream(outputFile);
        try { generateAdjustmentSummaryReportPDF(os, summaryList, totalAdjustments, totalAdditions, totalRemovals); } finally { try { os.close(); } catch (Exception ignored) {} }
    }

    public void generateAdjustmentSummaryReportPDF(OutputStream outputStream, List<AdjustmentSummaryData> summaryList, int totalAdjustments, int totalAdditions, int totalRemovals) throws Exception {
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        document.add(new Paragraph("ADJUSTMENT SUMMARY REPORT").setFont(boldFont).setFontSize(20).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("Generated on: " + dateFormat.format(new Date())).setFontSize(10).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("SUMMARY").setFont(boldFont).setFontSize(14));
        document.add(new Paragraph("Total Adjustments: " + totalAdjustments));
        document.add(new Paragraph("Total Units Added: +" + totalAdditions));
        document.add(new Paragraph("Total Units Removed: -" + totalRemovals));
        document.add(new Paragraph("Net Change: " + (totalAdditions - totalRemovals)));
        document.add(new Paragraph("DETAILED REPORT").setFont(boldFont).setFontSize(14).setMarginTop(10));
        Table table = new Table(UnitValue.createPercentArray(new float[]{2, 1, 1, 1, 1, 2})).setWidth(UnitValue.createPercentValue(100));
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
                if (!summary.getAdditionReasons().isEmpty()) reasons.append("Added: ").append(String.join(", ", summary.getAdditionReasons()));
                if (!summary.getRemovalReasons().isEmpty()) {
                    if (reasons.length() > 0) reasons.append("; ");
                    reasons.append("Removed: ").append(String.join(", ", summary.getRemovalReasons()));
                }
                table.addCell(reasons.toString());
            }
        }
        document.add(table);
        document.add(new Paragraph("\n\nReport generated by: Sales Inventory System").setFontSize(9).setTextAlignment(TextAlignment.CENTER));
        document.close();
    }

    private Cell createHeaderCell(String text) throws Exception {
        return new Cell().add(new Paragraph(text).setFont(boldFont)).setTextAlignment(TextAlignment.CENTER).setBackgroundColor(ColorConstants.LIGHT_GRAY);
    }

    public void generateCombinedInventoryReportPDF(OutputStream outputStream, List<StockValueReport> stockValueReports, List<StockMovementReport> stockMovementReports, List<AdjustmentSummaryData> adjustmentSummaries, int totalReceived, int totalSold, int totalAdjustments) throws Exception {
        PdfWriter writer = new PdfWriter(outputStream);
        PdfDocument pdfDoc = new PdfDocument(writer);
        Document document = new Document(pdfDoc);
        document.add(new Paragraph("INVENTORY REPORTS - COMBINED").setFont(boldFont).setFontSize(18).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("Generated on: " + dateFormat.format(new Date())).setFontSize(10).setTextAlignment(TextAlignment.CENTER));
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
                table3.addCell(s.getAdditionReasons().toString() + s.getRemovalReasons().toString());
            }
            document.add(table3);
        }
        document.add(new Paragraph("\n\nReport generated by: Sales Inventory System").setFontSize(9).setTextAlignment(TextAlignment.CENTER));
        document.close();
    }


    // ==========================================================
    // UPDATED: GAAP ACCOUNTING FORMAT FOR FINANCIAL REPORT
    // ==========================================================
    public void generateAccountingReportPDF(File file, String dateRange, String businessName,
                                            double grossSales, double discounts, double netSales,
                                            double cogs, double grossProfit, double opex, double netIncome,
                                            double cashSales, double gcashSales, int transactions, double inventoryValue,
                                            List<Reports.ReportItem> items) throws Exception {

        PdfWriter writer = new PdfWriter(new FileOutputStream(file));
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        // 1. ACCOUNTING 3-LINE HEADER
        document.add(new Paragraph(businessName.toUpperCase())
                .setFont(boldFont)
                .setFontSize(18)
                .setTextAlignment(TextAlignment.CENTER));

        document.add(new Paragraph("Income Statement & Financial Report")
                .setFont(boldFont)
                .setFontSize(14)
                .setTextAlignment(TextAlignment.CENTER));

        document.add(new Paragraph("For the Period: " + dateRange)
                .setFontSize(10)
                .setFontColor(ColorConstants.DARK_GRAY)
                .setTextAlignment(TextAlignment.CENTER)
                .setMarginBottom(20));

        // 2. ACCOUNTING FINANCIAL FORMAT TABLE (No borders, right-aligned numbers)
        Table isTable = new Table(UnitValue.createPercentArray(new float[]{4, 1.5f})).useAllAvailableWidth();
        isTable.setMarginBottom(20);

        // Revenue Section
        isTable.addCell(createNoBorderCell("Revenue", true, false));
        isTable.addCell(createNoBorderCell("", true, true));

        isTable.addCell(createNoBorderCell("    Gross Sales", false, false));
        isTable.addCell(createNoBorderCell(String.format(Locale.getDefault(), "₱ %,.2f", grossSales), false, true));

        isTable.addCell(createNoBorderCell("    Less: Sales Discounts", false, false));
        isTable.addCell(createBottomBorderCell(String.format(Locale.getDefault(), "%,.2f", discounts), false));

        isTable.addCell(createNoBorderCell("Net Sales", true, false));
        isTable.addCell(createNoBorderCell(String.format(Locale.getDefault(), "%,.2f", netSales), true, true));

        isTable.addCell(createNoBorderCell("\nCost of Goods Sold", true, false));
        isTable.addCell(createNoBorderCell("", true, true));

        isTable.addCell(createNoBorderCell("    Total Cost of Products Sold", false, false));
        isTable.addCell(createBottomBorderCell(String.format(Locale.getDefault(), "%,.2f", cogs), false));

        isTable.addCell(createNoBorderCell("Gross Profit", true, false));
        isTable.addCell(createNoBorderCell(String.format(Locale.getDefault(), "%,.2f", grossProfit), true, true));

        isTable.addCell(createNoBorderCell("\nOperating Expenses", true, false));
        isTable.addCell(createNoBorderCell("", true, true));

        isTable.addCell(createNoBorderCell("    Total Operating Expenses", false, false));
        isTable.addCell(createBottomBorderCell(String.format(Locale.getDefault(), "%,.2f", opex), false));

        isTable.addCell(createNoBorderCell("NET INCOME", true, false).setFontSize(14));
        isTable.addCell(createDoubleBottomBorderCell(String.format(Locale.getDefault(), "₱ %,.2f", netIncome), true).setFontSize(14));

        document.add(isTable);

        // 3. CASH & ASSET SUMMARY
        document.add(new Paragraph("Asset & Operations Summary").setFont(boldFont).setFontSize(12).setFontColor(ColorConstants.DARK_GRAY));
        Table assetTable = new Table(UnitValue.createPercentArray(new float[]{3f, 1f})).useAllAvailableWidth();
        assetTable.addCell(createNoBorderCell("Total Physical Cash", false, false));
        assetTable.addCell(createNoBorderCell(String.format(Locale.getDefault(), "₱ %,.2f", cashSales), false, true));

        assetTable.addCell(createNoBorderCell("Total GCash", false, false));
        assetTable.addCell(createNoBorderCell(String.format(Locale.getDefault(), "₱ %,.2f", gcashSales), false, true));

        assetTable.addCell(createNoBorderCell("Remaining Inventory Value", false, false));
        assetTable.addCell(createNoBorderCell(String.format(Locale.getDefault(), "₱ %,.2f", inventoryValue), false, true));

        assetTable.addCell(createNoBorderCell("Total Transactions Completed", false, false));
        assetTable.addCell(createNoBorderCell(String.valueOf(transactions), false, true));

        document.add(assetTable);
        document.add(new Paragraph("\n"));

        // 4. TRANSACTION BREAKDOWN
        document.add(new Paragraph("Transaction Breakdown").setFont(boldFont).setFontSize(12));
        Table transTable = new Table(UnitValue.createPercentArray(new float[]{2.5f, 2f, 1f, 1.5f})).useAllAvailableWidth();
        transTable.addHeaderCell(createHeaderCell("Item / Details"));
        transTable.addHeaderCell(createHeaderCell("Date / Payment Type"));
        transTable.addHeaderCell(createHeaderCell("Quantity"));
        transTable.addHeaderCell(createHeaderCell("Net Revenue"));

        for (Reports.ReportItem item : items) {
            String itemText = item.name;
            if (item.details != null && !item.details.isEmpty()) itemText += "\n" + item.details;
            if (item.discount > 0) itemText += "\nDisc: -P" + String.format("%.2f", item.discount);

            transTable.addCell(new Cell().add(new Paragraph(itemText).setFontSize(9)));
            transTable.addCell(new Cell().add(new Paragraph(item.date).setFontSize(9)));
            transTable.addCell(new Cell().add(new Paragraph(item.quantity).setFontSize(9).setTextAlignment(TextAlignment.CENTER)));
            transTable.addCell(new Cell().add(new Paragraph(item.amount).setFontSize(9).setTextAlignment(TextAlignment.RIGHT)));
        }

        document.add(transTable);
        document.add(new Paragraph("\nReport generated by: Sales Inventory System").setFontSize(9).setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.GRAY));
        document.close();
    }

    // Helper Methods for Accounting PDF Formatting
    private Cell createNoBorderCell(String text, boolean isBold, boolean isRightAligned) {
        Cell cell = new Cell().add(new Paragraph(text).setFont(isBold ? boldFont : regularFont))
                .setBorder(Border.NO_BORDER);
        if (isRightAligned) cell.setTextAlignment(TextAlignment.RIGHT);
        return cell;
    }

    private Cell createBottomBorderCell(String text, boolean isBold) {
        return new Cell().add(new Paragraph(text).setFont(isBold ? boldFont : regularFont))
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(ColorConstants.BLACK, 1f))
                .setTextAlignment(TextAlignment.RIGHT);
    }

    private Cell createDoubleBottomBorderCell(String text, boolean isBold) {
        return new Cell().add(new Paragraph(text).setFont(isBold ? boldFont : regularFont))
                .setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(ColorConstants.BLACK, 2f))
                .setTextAlignment(TextAlignment.RIGHT);
    }

    public void generateBestSellersReportPDF(File file, String dateRange, List<Reports.BestSellerItem> bestSellers) throws Exception {
        PdfWriter writer = new PdfWriter(new FileOutputStream(file));
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        document.add(new Paragraph("BEST SELLERS REPORT")
                .setFont(boldFont).setFontSize(20).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("Date Range: " + dateRange)
                .setFont(regularFont).setFontSize(12).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("Generated on: " + dateFormat.format(new Date()))
                .setFontSize(10).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("\n"));

        Table table = new Table(UnitValue.createPercentArray(new float[]{1, 3, 1.5f, 1.5f})).useAllAvailableWidth();
        table.addHeaderCell(createHeaderCell("Rank"));
        table.addHeaderCell(createHeaderCell("Product Name"));
        table.addHeaderCell(createHeaderCell("Units Sold"));
        table.addHeaderCell(createHeaderCell("Total Revenue"));

        int rank = 1;
        for (Reports.BestSellerItem item : bestSellers) {
            table.addCell(new Cell().add(new Paragraph(String.valueOf(rank)).setTextAlignment(TextAlignment.CENTER)));
            table.addCell(new Cell().add(new Paragraph(item.productName)));
            table.addCell(new Cell().add(new Paragraph(String.valueOf(item.quantitySold)).setTextAlignment(TextAlignment.CENTER).setFont(boldFont)));
            table.addCell(new Cell().add(new Paragraph(String.format(Locale.getDefault(), "₱%,.2f", item.totalRevenue)).setTextAlignment(TextAlignment.RIGHT)));
            rank++;
        }

        if (bestSellers.isEmpty()) {
            document.add(new Paragraph("No sales data available for the selected date range.").setTextAlignment(TextAlignment.CENTER));
        } else {
            document.add(table);
        }

        document.add(new Paragraph("\nReport generated by: Sales Inventory System").setFontSize(9).setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.GRAY));
        document.close();
    }

    // ==========================================================
    // NEW: OPERATIONS, RECEIVING & ADJUSTMENTS REPORT
    // ==========================================================
    public void generateOperationsAndReceivingReportPDF(File file, String dateRange, String businessName,
                                                        String poDetails, String returnsDetails,
                                                        String damageDetails) throws Exception {
        PdfWriter writer = new PdfWriter(new FileOutputStream(file));
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        document.add(new Paragraph(businessName.toUpperCase()).setFont(boldFont).setFontSize(18).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("Operations, Receiving & Adjustments Report").setFont(boldFont).setFontSize(14).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("For the Period: " + dateRange).setFontSize(10).setFontColor(ColorConstants.DARK_GRAY).setTextAlignment(TextAlignment.CENTER).setMarginBottom(20));

        // Damages & Adjustments
        document.add(new Paragraph("Stock Adjustments & Damages").setFont(boldFont).setFontSize(12));
        document.add(new Paragraph(damageDetails.isEmpty() ? "No damages recorded." : damageDetails).setFont(regularFont).setFontSize(10).setMarginBottom(15));

        // Purchase Orders & Receiving
        document.add(new Paragraph("Purchase Orders & Receiving").setFont(boldFont).setFontSize(12));
        document.add(new Paragraph(poDetails.isEmpty() ? "No Purchase Orders found." : poDetails).setFont(regularFont).setFontSize(10).setMarginBottom(15));

        // Supplier Returns
        document.add(new Paragraph("Supplier Returns").setFont(boldFont).setFontSize(12));
        document.add(new Paragraph(returnsDetails.isEmpty() ? "No Supplier Returns found." : returnsDetails).setFont(regularFont).setFontSize(10).setMarginBottom(15));

        document.add(new Paragraph("\nReport generated by: Sales Inventory System").setFontSize(9).setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.GRAY));
        document.close();
    }

    // ==========================================================
    // NEW: INVENTORY MASTER REPORT
    // ==========================================================
    public void generateInventoryMasterPDF(File file, String businessName, List<Product> inventory, double totalValue) throws Exception {
        PdfWriter writer = new PdfWriter(new FileOutputStream(file));
        PdfDocument pdf = new PdfDocument(writer);
        Document document = new Document(pdf);

        document.add(new Paragraph(businessName.toUpperCase()).setFont(boldFont).setFontSize(18).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("Inventory Master Report").setFont(boldFont).setFontSize(14).setTextAlignment(TextAlignment.CENTER));
        document.add(new Paragraph("Generated on: " + dateFormat.format(new Date())).setFontSize(10).setFontColor(ColorConstants.DARK_GRAY).setTextAlignment(TextAlignment.CENTER).setMarginBottom(20));

        document.add(new Paragraph(String.format(Locale.getDefault(), "Total Warehouse Value (Cost): ₱%,.2f", totalValue)).setFont(boldFont).setFontSize(12).setMarginBottom(10));

        Table table = new Table(UnitValue.createPercentArray(new float[]{2.5f, 1.5f, 1f, 1.5f, 1.5f})).useAllAvailableWidth();
        table.addHeaderCell(createHeaderCell("Product Name"));
        table.addHeaderCell(createHeaderCell("Category"));
        table.addHeaderCell(createHeaderCell("Stock"));
        table.addHeaderCell(createHeaderCell("Unit Cost"));
        table.addHeaderCell(createHeaderCell("Total Value"));

        for (Product p : inventory) {
            if ("Menu".equalsIgnoreCase(p.getProductType())) continue; // Skip finished products, only count raw/both
            double val = p.getQuantity() * p.getCostPrice();
            table.addCell(new Cell().add(new Paragraph(p.getProductName()).setFontSize(9)));
            table.addCell(new Cell().add(new Paragraph(p.getCategoryName() != null ? p.getCategoryName() : "General").setFontSize(9)));
            table.addCell(new Cell().add(new Paragraph(String.valueOf(p.getQuantity())).setFontSize(9).setTextAlignment(TextAlignment.CENTER)));
            table.addCell(new Cell().add(new Paragraph(String.format(Locale.US, "₱%,.2f", p.getCostPrice())).setFontSize(9).setTextAlignment(TextAlignment.RIGHT)));
            table.addCell(new Cell().add(new Paragraph(String.format(Locale.US, "₱%,.2f", val)).setFontSize(9).setTextAlignment(TextAlignment.RIGHT)));
        }

        document.add(table);
        document.add(new Paragraph("\nReport generated by: Sales Inventory System").setFontSize(9).setTextAlignment(TextAlignment.CENTER).setFontColor(ColorConstants.GRAY));
        document.close();
    }
}