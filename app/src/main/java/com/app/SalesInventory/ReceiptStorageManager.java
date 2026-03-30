package com.app.SalesInventory;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReceiptStorageManager {

    public static void generateAndSaveReceipt(Context context, String orderId, String receiptContent) {
        // 1. Get Today's Date for the Folder Name (e.g., "2023-10-25")
        String dateFolder = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // Note: We are using .txt for now to test the folders. We will change this to .pdf in Step 5!
        String fileName = "Receipt_" + orderId + ".txt";

        // 2. CREATE LOCAL FOLDER STRUCTURE
        // This builds: Documents / SalesInventoryApp / Receipts / 2023-10-25 /
        File baseDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "SalesInventoryApp");
        File receiptsDir = new File(baseDir, "Receipts/" + dateFolder);

        // If the folder for today doesn't exist yet, build it automatically!
        if (!receiptsDir.exists()) {
            receiptsDir.mkdirs();
        }

        File localFile = new File(receiptsDir, fileName);

        try {
            // Write the temporary file to the phone
            FileWriter writer = new FileWriter(localFile);
            writer.append(receiptContent);
            writer.flush();
            writer.close();

            Log.d("ReceiptManager", "Saved locally to: " + localFile.getAbsolutePath());

            // 3. UPLOAD TO CLOUD FOLDER STRUCTURE
            uploadToCloud(localFile, dateFolder, fileName);

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(context, "Failed to create local receipt folder.", Toast.LENGTH_SHORT).show();
        }
    }

    private static void uploadToCloud(File localFile, String dateFolder, String fileName) {
        // This builds the exact same folder structure inside Firebase Storage!
        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("SalesInventoryApp")
                .child("Receipts")
                .child(dateFolder)
                .child(fileName);

        // Upload it silently in the background
        storageRef.putFile(Uri.fromFile(localFile))
                .addOnSuccessListener(taskSnapshot -> Log.d("ReceiptManager", "Cloud backup successful!"))
                .addOnFailureListener(e -> Log.e("ReceiptManager", "Cloud backup failed", e));
    }
}