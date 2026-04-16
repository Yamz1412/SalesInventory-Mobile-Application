package com.app.SalesInventory;

import android.content.Context;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReceiptPrinterManager {

    private static android.webkit.WebView keepAliveWebView;
    public static void printReceipt(Context context, String orderId, String receiptContent) {
        String uid = com.google.firebase.auth.FirebaseAuth.getInstance().getUid();
        if (uid == null) { executePrint(context, orderId, receiptContent, "Staff"); return; }

        // Fetch user name before printing
        com.google.firebase.firestore.FirebaseFirestore.getInstance().collection("users").document(uid).get()
                .addOnSuccessListener(doc -> {
                    String name = doc.exists() && doc.getString("name") != null ? doc.getString("name") : "Staff";
                    String role = doc.exists() && doc.getString("role") != null ? doc.getString("role") : "";
                    String preparedBy = name + (role.isEmpty() ? "" : " (" + role + ")");
                    executePrint(context, orderId, receiptContent, preparedBy);
                })
                .addOnFailureListener(e -> executePrint(context, orderId, receiptContent, "Staff"));
    }

    private static void executePrint(Context context, String orderId, String receiptContent, String preparedBy) {
        android.print.PrintManager printManager = (android.print.PrintManager) context.getSystemService(Context.PRINT_SERVICE);
        if (printManager == null) return;

        String dateGen = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(new Date());

        // INJECTED CSS: Forces the WebView to format for a 58mm/80mm thermal roll
        String htmlDocument =
                "<html>" +
                        "<head>" +
                        "<meta name='viewport' content='width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=0'>" +
                        "<style>" +
                        "  @page { margin: 0; } " +
                        "  body { font-family: monospace; font-size: 14px; width: 100%; max-width: 300px; margin: 0 auto; padding: 10px; color: black; }" +
                        "  h2 { font-size: 18px; text-align: center; margin-bottom: 5px; }" +
                        "  h4 { font-size: 14px; text-align: center; margin-top: 0; margin-bottom: 15px; font-weight: normal; }" +
                        "  hr { border-top: 1px dashed black; margin: 10px 0; }" +
                        "  .content { font-size: 14px; line-height: 1.4; white-space: pre-wrap; }" +
                        "  .footer { font-size: 12px; text-align: center; margin-top: 15px; }" +
                        "</style>" +
                        "</head>" +
                        "<body>" +
                        "<h2>Sales Inventory</h2>" +
                        "<h4>Order #" + orderId.substring(0, 8).toUpperCase() + "</h4>" +
                        "<hr>" +
                        "<div class='content'>" + receiptContent.replace("\n", "<br>") + "</div>" +
                        "<hr>" +
                        "<div class='footer'>Prepared by: " + preparedBy + "<br>Date: " + dateGen + "<br><br>Thank you for your business!</div>" +
                        "</body>" +
                        "</html>";

        keepAliveWebView = new android.webkit.WebView(context);
        keepAliveWebView.setWebViewClient(new android.webkit.WebViewClient() {
            @Override
            public void onPageFinished(android.webkit.WebView view, String url) {
                android.print.PrintDocumentAdapter printAdapter;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    printAdapter = keepAliveWebView.createPrintDocumentAdapter("Receipt_" + orderId);
                } else {
                    printAdapter = keepAliveWebView.createPrintDocumentAdapter();
                }

                android.print.PrintAttributes attributes = new android.print.PrintAttributes.Builder()
                        .setMediaSize(android.print.PrintAttributes.MediaSize.UNKNOWN_PORTRAIT)
                        .setResolution(new android.print.PrintAttributes.Resolution("res1", "Receipt", 300, 300))
                        .setMinMargins(android.print.PrintAttributes.Margins.NO_MARGINS)
                        .build();

                printManager.print("Receipt_" + orderId, printAdapter, attributes);
            }
        });
        keepAliveWebView.loadDataWithBaseURL(null, htmlDocument, "text/HTML", "UTF-8", null);
    }
}