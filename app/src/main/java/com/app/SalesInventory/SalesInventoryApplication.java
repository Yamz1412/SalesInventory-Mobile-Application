package com.app.SalesInventory;

import android.app.Application;
import android.util.Log;

import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.ExistingWorkPolicy;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.TimeUnit;

public class SalesInventoryApplication extends Application {
    private static SalesInventoryApplication instance;
    private ProductRepository productRepository;
    private ProductRemoteSyncer productRemoteSyncer;
    private SalesRepository salesRepository;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                StringWriter sw = new StringWriter();
                throwable.printStackTrace(new PrintWriter(sw));
                String content = "Thread: " + thread.getName() + "\n" + sw.toString();
                File f = new File(getFilesDir(), "crash_log.txt");
                try (FileOutputStream fos = new FileOutputStream(f, true)) {
                    fos.write((System.currentTimeMillis() + " ---\n").getBytes());
                    fos.write(content.getBytes());
                    fos.write("\n\n".getBytes());
                    fos.flush();
                } catch (Exception ignored) {}
            } catch (Throwable ignored) {}
            try {
                Log.e("UncaughtException", "Uncaught exception in thread " + thread.getName(), throwable);
            } catch (Throwable ignored) {}
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {}
            System.exit(2);
        });
        productRepository = ProductRepository.getInstance(this);
        productRemoteSyncer = new ProductRemoteSyncer(this);
        salesRepository = SalesRepository.getInstance(this);
        scheduleMonthlyWorkerIfNeeded();

        String owner = FirestoreManager.getInstance().getBusinessOwnerId();
        if (owner != null && !owner.isEmpty()) {
            productRemoteSyncer.startRealtimeSync(owner);
        }
    }

    private void scheduleMonthlyWorkerIfNeeded() {
        long nextMillis = MonthlyReportWorker.computeNextMonthBoundaryMillis();
        long delay = Math.max(60_000L, nextMillis - System.currentTimeMillis());
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(MonthlyReportWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build();
        WorkManager.getInstance(this).enqueueUniqueWork("monthly_report_worker", ExistingWorkPolicy.KEEP, req);
    }

    public static SalesInventoryApplication getInstance() {
        return instance;
    }

    public static ProductRepository getProductRepository() {
        return instance.productRepository;
    }

    public static ProductRemoteSyncer getProductRemoteSyncer() {
        return instance.productRemoteSyncer;
    }

    public static SalesRepository getSalesRepository() {
        return instance.salesRepository;
    }
}