package com.app.SalesInventory;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.ListResult;
import com.google.firebase.storage.StorageReference;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class YearlyRestoreWorker extends Worker {
    private static final String TAG = "YearlyRestoreWorker";

    private final FirebaseStorage storage;
    private final ReportExportUtil exportUtil;

    public YearlyRestoreWorker(@NonNull Context ctx, @NonNull WorkerParameters params) {
        super(ctx, params);
        storage = FirebaseStorage.getInstance();
        exportUtil = new ReportExportUtil(ctx);
    }

    @NonNull
    @Override
    public Result doWork() {
        try {
            int prevYear = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) - 1;
            String prefix = "backups/" + prevYear;
            StorageReference baseRef = storage.getReference().child(prefix);
            baseRef.listAll().addOnSuccessListener((ListResult lr) -> {
                for (StorageReference item : lr.getItems()) {
                    try {
                        File outDir = exportUtil.getExportDirectory();
                        if (outDir == null) continue;
                        File outFile = new File(outDir, item.getName());
                        item.getFile(outFile).addOnSuccessListener(taskSnapshot -> Log.i(TAG, "Downloaded " + outFile.getName()))
                                .addOnFailureListener(e -> Log.e(TAG, "Download failed: " + e.getMessage()));
                    } catch (Exception e) {
                        Log.e(TAG, "download item error", e);
                    }
                }
            }).addOnFailureListener(e -> Log.e(TAG, "listAll failed", e));
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Yearly restore failed", e);
            return Result.retry();
        }
    }
}