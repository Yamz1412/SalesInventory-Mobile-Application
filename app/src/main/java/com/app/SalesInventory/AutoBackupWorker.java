package com.app.SalesInventory;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class AutoBackupWorker extends Worker {

    public AutoBackupWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d("AutoBackupWorker", "Starting daily automated backup...");

        // Triggers the silent overwrite backup
        boolean success = BackupManager.createAutomatedBackup(getApplicationContext());

        if (success) {
            return Result.success();
        } else {
            return Result.retry();
        }
    }
}