package com.app.SalesInventory;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;

import java.io.File;

public class FileUtils {

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
}