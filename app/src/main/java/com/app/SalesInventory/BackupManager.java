package com.app.SalesInventory;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class BackupManager {

    private static final String DB_NAME = "sales_inventory_db";
    private static final String TAG = "BackupManager";

    public static File getDatabaseFile(Context context) {
        return context.getDatabasePath(DB_NAME);
    }

    // 1. STANDARD MANUAL EXPORT
    public static boolean exportDatabase(Context context, Uri destUri) {
        AppDatabase.closeDatabase();

        File dbFile = getDatabaseFile(context);
        if (dbFile == null || !dbFile.exists()) {
            Log.e(TAG, "Source database file does not exist.");
            return false;
        }

        ContentResolver resolver = context.getContentResolver();
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(destUri, "w");
             FileInputStream in = new FileInputStream(dbFile);
             FileOutputStream out = new FileOutputStream(pfd.getFileDescriptor())) {

            if (pfd == null) return false;

            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            out.flush();
            AppDatabase.getInstance(context);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Export failed", e);
            return false;
        }
    }

    // 5. NEW: SILENT AUTOMATED BACKUP
    public static boolean createAutomatedBackup(Context context) {
        AppDatabase.closeDatabase();

        File dbFile = getDatabaseFile(context);
        if (dbFile == null || !dbFile.exists()) return false;

        // Save to the app's private external folder (Safe from accidental user deletion)
        File backupDir = new File(context.getExternalFilesDir(null), "backups");
        if (!backupDir.exists()) backupDir.mkdirs();

        File backupFile = new File(backupDir, "nightly_backup.db");

        try (FileInputStream in = new FileInputStream(dbFile);
             FileOutputStream out = new FileOutputStream(backupFile)) {

            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            Log.d(TAG, "Nightly backup saved successfully at: " + backupFile.getAbsolutePath());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Automated backup failed", e);
            return false;
        }
    }

    // 3. STANDARD RESTORE
    public static boolean importDatabase(Context context, Uri srcUri) {
        AppDatabase.closeDatabase();

        File dbFile = getDatabaseFile(context);
        if (dbFile == null) return false;

        File dbWal = new File(dbFile.getPath() + "-wal");
        File dbShm = new File(dbFile.getPath() + "-shm");
        if (dbWal.exists()) dbWal.delete();
        if (dbShm.exists()) dbShm.delete();

        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        ContentResolver resolver = context.getContentResolver();
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(srcUri, "r");
             InputStream in = new FileInputStream(pfd.getFileDescriptor());
             OutputStream out = new FileOutputStream(dbFile, false)) {

            if (pfd == null) return false;

            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            out.flush();

            AppDatabase.getInstance(context.getApplicationContext());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Import failed", e);
            return false;
        }
    }

    // 4. NEW: ACCOUNT MIGRATION FIX
    // Forces the newly restored database to upload everything to the new Firebase account
    public static void prepareDatabaseForNewAccount(Context context) {
        try {
            androidx.sqlite.db.SupportSQLiteDatabase db = AppDatabase.getInstance(context).getOpenHelper().getWritableDatabase();
            // Mark all items as unsynced (0) so SyncWorker pushes them to the new cloud account
            db.execSQL("UPDATE products SET isSynced = 0");
            db.execSQL("UPDATE sales SET isSynced = 0");
            Log.d(TAG, "Database prepped for new account migration.");
        } catch (Exception e) {
            Log.e(TAG, "Failed to prep database", e);
        }
    }
}