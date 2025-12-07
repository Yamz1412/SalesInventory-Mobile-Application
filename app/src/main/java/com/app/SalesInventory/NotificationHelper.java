package com.app.SalesInventory;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/**
 * Centralized helper for showing local notifications.
 * - Creates notification channel on Android O+
 * - Deep-links into StockAlertsActivity if alertId provided
 */
public class NotificationHelper {
    private static final String CHANNEL_ID = "sales_inventory_alerts";
    private static final String CHANNEL_NAME = "Alerts";

    public static void showNotification(Context ctx, String title, String message, String alertId) {
        if (ctx == null) return;
        createChannelIfNeeded(ctx);

        Intent intent;
        if (alertId != null && !alertId.isEmpty()) {
            intent = new Intent(ctx, StockAlertsActivity.class);
            intent.putExtra("alertId", alertId);
        } else {
            intent = new Intent(ctx, MainActivity.class);
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);

        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0;
        PendingIntent pi = PendingIntent.getActivity(ctx, (int) System.currentTimeMillis(), intent, flags);

        NotificationCompat.Builder nb = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(title == null || title.isEmpty() ? "Alert" : title)
                .setContentText(message == null ? "" : message)
                .setAutoCancel(true)
                .setColor(Color.parseColor("#FF6B6B"))
                .setContentIntent(pi)
                .setPriority(NotificationCompat.PRIORITY_HIGH);

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify((int) System.currentTimeMillis(), nb.build());
    }

    private static void createChannelIfNeeded(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = nm.getNotificationChannel(CHANNEL_ID);
        if (ch == null) {
            ch = new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Inventory and sales alerts");
            ch.enableLights(true);
            ch.setLightColor(Color.RED);
            nm.createNotificationChannel(ch);
        }
    }
}