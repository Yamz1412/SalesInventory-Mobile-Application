package com.app.SalesInventory;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy; // Added correct import
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OutOfQuotaPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public class SyncScheduler {
    // Reduced interval to 15 minutes (minimum allowed by Android) for fresher data
    private static final long PERIODIC_INTERVAL_MINUTES = 15;
    private static final String SYNC_WORK_NAME = "com.app.SalesInventory.sync_work";
    private static final String SYNC_WORK_PERIODIC_NAME = "com.app.SalesInventory.sync_work_periodic";

    /**
     * Call this when a user performs an action (Add Sale, Update Stock)
     * to schedule an immediate upload as soon as network is available.
     */
    public static void enqueueImmediateSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // Expedited policy ensures the job runs as soon as constraints (Network) are met,
        // even if the app is in the background.
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SyncWorker.class)
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build();

        // Use APPEND_OR_REPLACE so if a sync is already running, we queue this one after it,
        // ensuring no data is missed.
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniqueWork(SYNC_WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request);
    }

    /**
     * Schedules a background sync that runs periodically (every ~15 mins)
     * to fetch updates from the server (e.g., new products added by other admins).
     */
    public static void schedulePeriodicSync(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        PeriodicWorkRequest periodic = new PeriodicWorkRequest.Builder(SyncWorker.class, PERIODIC_INTERVAL_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build();

        // KEEP ensures we don't restart the timer if it's already scheduled.
        // FIXED: Changed ExistingWorkPolicy -> ExistingPeriodicWorkPolicy
        WorkManager.getInstance(context.getApplicationContext())
                .enqueueUniquePeriodicWork(SYNC_WORK_PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, periodic);
    }
}