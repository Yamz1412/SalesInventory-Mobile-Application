package com.app.SalesInventory;

import android.content.Context;
import android.os.Environment;
import android.widget.Toast;
import android.net.Uri;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ReportExportUtil {

    public static final int EXPORT_PDF = 1;
    public static final int EXPORT_CSV = 2;

    private Context context;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault());

    public static class ExportResult {
        public OutputStream outputStream;
        public Uri uri;
        public String displayPath;
        public File file;
    }

    public ReportExportUtil(Context context) {
        this.context = context;
    }

    // --- ADDED THIS BACK TO FIX THE ERROR ---
    public boolean isStorageAvailable() {
        String state = Environment.getExternalStorageState();
        return Environment.MEDIA_MOUNTED.equals(state);
    }
    // ----------------------------------------

    public File getExportDirectory() {
        File base = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS);
        if (base == null) base = context.getFilesDir();
        File directory = new File(base, "SalesInventory_Reports");
        if (!directory.exists()) directory.mkdirs();
        return directory;
    }

    public String generateFileName(String reportType, int exportType) {
        String timestamp = dateFormat.format(new Date());
        String extension = exportType == EXPORT_PDF ? ".pdf" : ".csv";
        return reportType + timestamp + extension;
    }

    public ExportResult createOutputStreamForFile(String filename, int exportType) {
        try {
            File dir = getExportDirectory();
            if (dir == null) return null;
            File out = new File(dir, filename);
            OutputStream os = new FileOutputStream(out);
            ExportResult r = new ExportResult();
            r.outputStream = os;
            r.uri = Uri.fromFile(out);
            r.displayPath = out.getAbsolutePath();
            r.file = out;
            return r;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public void showExportSuccess(String path) {
        Toast.makeText(context, "Export Successful!\nSaved to: " + path, Toast.LENGTH_LONG).show();
    }

    public void showExportError(String error) {
        Toast.makeText(context, "Export Failed: " + error, Toast.LENGTH_LONG).show();
    }

    public void shareFileViaEmail(File file, String subject) {
        try {
            Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
                    context, context.getPackageName() + ".provider", file);

            android.content.Intent emailIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            emailIntent.setType("application/pdf");
            emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, subject);
            emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, "Please find the attached report generated from the Sales Inventory App.");
            emailIntent.putExtra(android.content.Intent.EXTRA_STREAM, fileUri);
            emailIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);

            context.startActivity(android.content.Intent.createChooser(emailIntent, "Send Report via..."));
        } catch (Exception e) {
            Toast.makeText(context, "Error opening email app. Ensure FileProvider is setup.", Toast.LENGTH_SHORT).show();
        }
    }
}