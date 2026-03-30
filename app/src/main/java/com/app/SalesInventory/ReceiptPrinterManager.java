package com.app.SalesInventory;

import android.content.Context;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class ReceiptPrinterManager {

    public static void printReceipt(Context context, String orderId, String receiptContent) {
        PrintManager printManager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);
        if (printManager == null) return;

        // 1. Format the text into a clean, thermal-printer style HTML layout
        String htmlDocument =
                "<html>" +
                        "<head><style>" +
                        "body { font-family: monospace; font-size: 14px; text-align: center; margin: 0; padding: 20px; }" +
                        "hr { border-top: 1px dashed black; }" +
                        ".left { text-align: left; }" +
                        ".right { text-align: right; float: right; }" +
                        "</style></head>" +
                        "<body>" +
                        "<h2>SALES INVENTORY</h2>" +
                        "<p>Official Receipt</p>" +
                        "<hr>" +
                        "<div class='left'>" + receiptContent.replace("\n", "<br>") + "</div>" +
                        "<hr>" +
                        "<p>Thank you for your business!</p>" +
                        "</body>" +
                        "</html>";

        // 2. Use a hidden WebView to render the HTML into a printable document
        WebView webView = new WebView(context);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // 3. Send to Android's Native Print Spooler once rendering is done
                PrintDocumentAdapter printAdapter;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    printAdapter = webView.createPrintDocumentAdapter("Receipt_" + orderId);
                } else {
                    printAdapter = webView.createPrintDocumentAdapter();
                }

                PrintAttributes attributes = new PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A6) // A6 is close to thermal receipt size
                        .setResolution(new PrintAttributes.Resolution("res1", "Receipt", 300, 300))
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build();

                printManager.print("Receipt_" + orderId, printAdapter, attributes);
            }
        });

        webView.loadDataWithBaseURL(null, htmlDocument, "text/HTML", "UTF-8", null);
    }
}