package com.app.SalesInventory;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class FileUtils {

    /**
     * Convert URI to File object
     * Supports both file:// and content:// schemes
     */
    public static File getFileFromUri(Context context, Uri uri) {
        if (uri == null) return null;

        if ("file".equalsIgnoreCase(uri.getScheme())) {
            return new File(uri.getPath());
        }

        if ("content".equalsIgnoreCase(uri.getScheme())) {
            String[] projection = {MediaStore.Images.Media.DATA};
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    String path = cursor.getString(columnIndex);
                    if (path != null && !path.isEmpty()) {
                        return new File(path);
                    }
                }
            } catch (Exception ignored) {
            } finally {
                if (cursor != null) cursor.close();
            }
        }

        return null;
    }

    /**
     * Check if a file path is valid and readable
     * @param filePath Path to check
     * @return true if file exists, is readable, and is not empty
     */
    public static boolean isValidImageFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        try {
            File file = new File(filePath);
            return file.exists() && file.isFile() && file.canRead() && file.length() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Get app-specific image directory for storing product images
     * Uses app cache directory: /data/data/com.app.SalesInventory/cache/images/
     * @param context Application context
     * @return Image cache directory
     */
    public static File getImageCacheDir(Context context) {
        File cacheDir = new File(context.getCacheDir(), "images");
        if (!cacheDir.exists()) {
            cacheDir.mkdirs();
        }
        return cacheDir;
    }

    /**
     * Get app-specific image directory for persistent storage
     * Uses app files directory: /data/data/com.app.SalesInventory/files/images/
     * @param context Application context
     * @return Image persistent directory
     */
    public static File getImageFilesDir(Context context) {
        File filesDir = new File(context.getFilesDir(), "images");
        if (!filesDir.exists()) {
            filesDir.mkdirs();
        }
        return filesDir;
    }

    /**
     * Generate a unique filename for product images
     * Format: product_[productId]_[timestamp].jpg
     * @param productId Product ID
     * @return Generated filename
     */
    public static String generateImageFilename(String productId) {
        long timestamp = System.currentTimeMillis();
        return "product_" + (productId != null ? productId : "unknown") + "_" + timestamp + ".jpg";
    }

    /**
     * Save image file to app's persistent image directory
     * @param context Application context
     * @param sourceFile Source image file
     * @param productId Product ID (used for filename)
     * @return Full path to saved file, or null if failed
     */
    public static String saveImageToAppStorage(Context context, File sourceFile, String productId) {
        if (sourceFile == null || !sourceFile.exists() || !sourceFile.canRead()) {
            return null;
        }

        try {
            File targetDir = getImageFilesDir(context);
            String filename = generateImageFilename(productId);
            File targetFile = new File(targetDir, filename);

            // Copy file
            copyFile(sourceFile, targetFile);

            return targetFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static boolean copyFile(File source, File dest) {
        try {
            java.nio.file.Files.copy(
                    source.toPath(),
                    dest.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Delete image file
     * @param filePath Path to file to delete
     * @return true if successful
     */
    public static boolean deleteImageFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        try {
            File file = new File(filePath);
            return file.delete();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Get file size in bytes
     * @param filePath Path to file
     * @return File size in bytes, or -1 if file doesn't exist
     */
    public static long getFileSize(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return -1;
        }

        try {
            File file = new File(filePath);
            return file.exists() ? file.length() : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    public static boolean clearImageCache(Context context) {
        try {
            File cacheDir = getImageCacheDir(context);
            File[] files = cacheDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    file.delete();
                }
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}