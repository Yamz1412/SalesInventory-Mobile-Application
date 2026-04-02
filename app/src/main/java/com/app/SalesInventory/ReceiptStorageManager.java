package com.app.SalesInventory;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReceiptStorageManager {

    public static void generateAndSaveReceipt(Context context, String orderId, String receiptContent) {
        String dateFolder = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // UPGRADED to .pdf format
        String fileName = "Receipt_" + orderId + ".pdf";

        File baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (baseDir == null) baseDir = context.getFilesDir();

        File receiptsDir = new File(baseDir, "Receipts/" + dateFolder);

        if (!receiptsDir.exists()) {
            receiptsDir.mkdirs();
        }

        File localFile = new File(receiptsDir, fileName);

        try {
            // Generate the actual PDF document
            PDFGenerator pdfGenerator = new PDFGenerator(context);
            String businessName = "Sales Inventory System"; // Fallback name
            pdfGenerator.generateReceiptPDF(localFile, businessName, orderId, receiptContent);

            Log.d("ReceiptManager", "Saved PDF locally to: " + localFile.getAbsolutePath());

            // Upload the newly generated PDF to Firebase
            uploadToCloud(localFile, dateFolder, fileName);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to save local receipt PDF.", Toast.LENGTH_SHORT).show();
        }
    }

    private static void uploadToCloud(File localFile, String dateFolder, String fileName) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = AuthManager.getInstance().getCurrentUserId();
        }
        if (ownerId == null) ownerId = "Unknown_Store";

        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("SalesInventoryApp")
                .child(ownerId)
                .child("Receipts")
                .child(dateFolder)
                .child(fileName);

        storageRef.putFile(Uri.fromFile(localFile))
                .addOnSuccessListener(taskSnapshot -> Log.d("ReceiptManager", "Cloud backup successful!"))
                .addOnFailureListener(e -> Log.e("ReceiptManager", "Cloud backup failed", e));
    }
}