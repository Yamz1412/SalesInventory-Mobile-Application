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

    public static boolean exportDatabase(Context context, Uri destUri) {
        // 1. Check point (Force save) or Close DB to ensure all data is in the main .db file
        AppDatabase.closeDatabase();

        File dbFile = getDatabaseFile(context);
        // Also check for WAL files if they exist, though closing usually merges them
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
            // Re-open DB after export if needed
            AppDatabase.getInstance(context);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Export failed", e);
            return false;
        }
    }

    public static boolean importDatabase(Context context, Uri srcUri) {
        // 1. Close the active database connection to remove locks
        AppDatabase.closeDatabase();

        File dbFile = getDatabaseFile(context);
        if (dbFile == null) return false;

        // 2. Delete temporary WAL/SHM files to prevent version conflicts
        File dbWal = new File(dbFile.getPath() + "-wal");
        File dbShm = new File(dbFile.getPath() + "-shm");
        if (dbWal.exists()) dbWal.delete();
        if (dbShm.exists()) dbShm.delete();

        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        ContentResolver resolver = context.getContentResolver();
        try (ParcelFileDescriptor pfd = resolver.openFileDescriptor(srcUri, "r");
             InputStream in = new FileInputStream(pfd.getFileDescriptor());
             OutputStream out = new FileOutputStream(dbFile, false)) { // 'false' overwrites file

            if (pfd == null) return false;

            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) > 0) {
                out.write(buffer, 0, len);
            }
            out.flush();

            // 3. Re-initialize the database instance
            AppDatabase.getInstance(context.getApplicationContext());
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Import failed", e);
            return false;
        }
    }
}