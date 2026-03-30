package com.app.SalesInventory;

import android.content.Context;
import android.os.Environment;
import android.widget.Toast;
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

    public ReportExportUtil(Context context) {
        this.context = context;
    }

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
        return reportType + "_" + timestamp + extension;
    }

    public boolean isStorageAvailable() {
        return true;
    }

    public void showExportSuccess(String filePath) {
        Toast.makeText(context, "Report exported successfully!\nSaved to: " + filePath, Toast.LENGTH_LONG).show();
    }

    public void showExportError(String errorMessage) {
        Toast.makeText(context, "Export failed: " + errorMessage, Toast.LENGTH_LONG).show();
    }

    public static class ExportResult {
        public OutputStream outputStream;
        public String displayPath;
        public java.net.URI uri;
        public File file;
    }

    public ExportResult createOutputStreamForFile(String filename, int exportType) throws Exception {
        File dir = getExportDirectory();
        if (dir == null) throw new Exception("Export directory not available");
        File out = new File(dir, filename);
        OutputStream os = new FileOutputStream(out);
        ExportResult r = new ExportResult();
        r.outputStream = os;
        r.uri = out.toURI();
        r.displayPath = out.getAbsolutePath();
        r.file = out;
        return r;
    }

    public void shareFileViaEmail(File file, String subject) {
        try {
            // Securely share the file via Android's FileProvider
            android.net.Uri fileUri = androidx.core.content.FileProvider.getUriForFile(
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