package com.app.SalesInventory;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Calendar;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MonthlyReportWorker extends Worker {
    private static final String TAG = "MonthlyReportWorker";

    private final FirebaseStorage storage;
    private final FirebaseDatabase database;
    private final Context ctx;

    public MonthlyReportWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
        this.ctx = ctx.getApplicationContext();
        storage = FirebaseStorage.getInstance();
        database = FirebaseDatabase.getInstance();
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            File exportDir = ctx.getFilesDir();
            if (exportDir == null) {
                Log.e(TAG, "No export dir available");
                return Result.retry();
            }

            String monthTag = getYearMonthTag();
            String pdfName = "MonthlyBackup_" + monthTag + ".pdf";
            String csvName = "MonthlyBackup_" + monthTag + ".csv";

            File pdfFile = new File(exportDir, pdfName);
            File csvFile = new File(exportDir, csvName);

            exportDir.mkdirs();

            try (FileOutputStream pdfFos = new FileOutputStream(pdfFile)) {
                PDFGenerator pdfGen = new PDFGenerator(ctx);
                DashboardRepository repo = new DashboardRepository();
                DashboardMetrics metrics = repo.getMetricsLiveData().getValue();
                if (metrics == null) metrics = new DashboardMetrics();
                pdfGen.generateOverallSummaryReportPDF(pdfFos,
                        metrics.getTopProducts() == null ? 0 : metrics.getTopProducts().size(),
                        metrics.getLowStockCount(),
                        metrics.getTotalInventoryValue(),
                        0, 0, 0, 0,
                        null
                );
            } catch (Exception e) {
                Log.e(TAG, "Failed to generate PDF", e);
                return Result.retry();
            }

            try (FileOutputStream csvFos = new FileOutputStream(csvFile)) {
                CSVGenerator csvGen = new CSVGenerator();
                csvGen.generateStockValueReportCSV(csvFos, null);
            } catch (Exception e) {
                Log.e(TAG, "Failed to generate CSV", e);
                return Result.retry();
            }

            try {
                uploadFileToStorage(pdfFile, "backups/" + monthTag + "/" + pdfFile.getName());
                uploadFileToStorage(csvFile, "backups/" + monthTag + "/" + csvFile.getName());
            } catch (Exception e) {
                Log.w(TAG, "Upload had problems but continuing: " + e.getMessage());
            }

            archiveAndResetMonthlyData(monthTag);

            scheduleNextRun();

            Calendar cal = Calendar.getInstance();
            if (cal.get(Calendar.MONTH) == Calendar.DECEMBER) {
                OneTimeWorkRequest yearly = new OneTimeWorkRequest.Builder(YearlyRestoreWorker.class).build();
                WorkManager.getInstance(getApplicationContext()).enqueue(yearly);
            }

            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Monthly worker failed", e);
            return Result.retry();
        }
    }

    private void uploadFileToStorage(File file, String storagePath) {
        try {
            StorageReference ref = storage.getReference().child(storagePath);
            ref.putFile(Uri.fromFile(file))
                    .addOnSuccessListener(taskSnapshot -> Log.i(TAG, "Uploaded " + file.getName()))
                    .addOnFailureListener(err -> Log.e(TAG, "Upload failed: " + err.getMessage()));
        } catch (Exception e) {
            Log.e(TAG, "uploadFileToStorage error", e);
        }
    }

    private void archiveAndResetMonthlyData(String monthTag) {
        try {
            DatabaseReference root = database.getReference();
            String archivePath = "archives/" + monthTag;
            DatabaseReference monthlyMetricsRef = root.child("monthlyMetrics");
            DatabaseReference archiveRef = root.child(archivePath).child("monthlyMetrics");
            monthlyMetricsRef.get().addOnSuccessListener(snapshot -> {
                if (snapshot != null && snapshot.exists()) {
                    archiveRef.setValue(snapshot.getValue()).addOnCompleteListener(t -> monthlyMetricsRef.setValue(null));
                } else {
                    monthlyMetricsRef.setValue(null);
                }
            }).addOnFailureListener(e -> Log.e(TAG, "archive read failed", e));
        } catch (Exception e) {
            Log.e(TAG, "archiveAndResetMonthlyData error", e);
        }
    }

    private void scheduleNextRun() {
        long next = computeNextMonthBoundaryMillis();
        long now = System.currentTimeMillis();
        long delay = Math.max(60_000L, next - now);
        OneTimeWorkRequest nextWork = new OneTimeWorkRequest.Builder(MonthlyReportWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(getApplicationContext()).enqueue(nextWork);
        Log.i(TAG, "Scheduled next monthly worker in " + (delay / 1000 / 60) + " minutes");
    }

    private String getYearMonthTag() {
        Calendar c = Calendar.getInstance();
        int y = c.get(Calendar.YEAR);
        int m = c.get(Calendar.MONTH) + 1;
        return String.format(Locale.US, "%04d-%02d", y, m);
    }

    public static long computeNextMonthBoundaryMillis() {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.MONTH, 1);
        c.set(Calendar.DAY_OF_MONTH, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 5);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }
}