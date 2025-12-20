package com.app.SalesInventory;

import android.util.Log;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.util.HashMap;
import java.util.Map;

public class AppFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "AppFirebaseMsgSvc";

    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        String title = "";
        String body = "";
        String alertId = null;

        if (remoteMessage.getData() != null && !remoteMessage.getData().isEmpty()) {
            Map<String, String> data = remoteMessage.getData();
            if (data.containsKey("title")) title = data.get("title");
            if (data.containsKey("body")) body = data.get("body");
            if (data.containsKey("alertId")) alertId = data.get("alertId");
        }

        if ((title == null || title.isEmpty()) && remoteMessage.getNotification() != null) {
            RemoteMessage.Notification n = remoteMessage.getNotification();
            title = n.getTitle() != null ? n.getTitle() : "";
            body = n.getBody() != null ? n.getBody() : "";
        }

        try {
            NotificationHelper.showNotification(
                    getApplicationContext(),
                    (title == null || title.isEmpty()) ? "Alert" : title,
                    (body == null ? "" : body),
                    alertId
            );
        } catch (Exception e) {
            Log.e(TAG, "Failed to show notification", e);
        }
    }

    @Override
    public void onNewToken(String token) {
        super.onNewToken(token);
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        String uid = FirebaseAuth.getInstance().getCurrentUser() == null ? null : FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (uid != null && token != null && !token.isEmpty()) {
            Map<String, Object> doc = new HashMap<>();
            doc.put("token", token);
            doc.put("platform", "android");
            doc.put("createdAt", FieldValue.serverTimestamp());
            db.collection("users").document(uid).collection("devices").document(token).set(doc);
        } else {
            FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    String t = task.getResult();
                    String u = FirebaseAuth.getInstance().getCurrentUser() == null ? null : FirebaseAuth.getInstance().getCurrentUser().getUid();
                    if (u != null && t != null) {
                        Map<String, Object> doc = new HashMap<>();
                        doc.put("token", t);
                        doc.put("platform", "android");
                        doc.put("createdAt", FieldValue.serverTimestamp());
                        db.collection("users").document(u).collection("devices").document(t).set(doc);
                    }
                }
            });
        }
    }
}