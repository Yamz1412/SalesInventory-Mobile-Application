package com.app.SalesInventory;

import android.content.Context;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportExportUtil {

    public static final int EXPORT_PDF = 1;
    public static final int EXPORT_CSV = 2;

    private Context context;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());

    public ReportExportUtil(Context context) {
        this.context = context;
    }

    /**
     * Get the exports directory
     */
    public File getExportDirectory() {
        File directory = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "SalesInventory_Reports");

        if (!directory.exists()) {
            directory.mkdirs();
        }

        return directory;
    }

    /**
     * Generate filename with timestamp
     */
    public String generateFileName(String reportType, int exportType) {
        String timestamp = dateFormat.format(new Date());
        String extension = exportType == EXPORT_PDF ? ".pdf" : ".csv";
        return reportType + "_" + timestamp + extension;
    }

    /**
     * Check if storage permission is granted
     */
    public boolean isStorageAvailable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }

    /**
     * Show export success message
     */
    public void showExportSuccess(String filePath) {
        Toast.makeText(context,
                "Report exported successfully!\nSaved to: " + filePath,
                Toast.LENGTH_LONG).show();
    }

    public void showExportError(String errorMessage) {
        Toast.makeText(context,
                "Export failed: " + errorMessage,
                Toast.LENGTH_LONG).show();
    }
}