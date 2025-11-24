package com.app.SalesInventory;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.app.SalesInventory.R;
import com.app.SalesInventory.SalesInventoryApplication;

public class AlertActivity extends AppCompatActivity {
    private static final String TAG = "AlertActivity";
    private AlertRepository alertRepository;
    private TextView unreadCountTextView;
    private RecyclerView alertsRecyclerView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert);

        // Initialize repository
        alertRepository = SalesInventoryApplication.getAlertRepository();

        // Set context
        SalesInventoryApplication.BaseContext.setContext(this);

        // Initialize UI
        initializeUI();

        // Observe alerts
        observeAlerts();
    }

    /**
     * Initialize UI components
     */
    private void initializeUI() {
        unreadCountTextView = findViewById(R.id.unreadCountTextView);
        alertsRecyclerView = findViewById(R.id.alertsRecyclerView);
    }

    /**
     * Observe all alerts and unread count
     */
    private void observeAlerts() {
        // Observe all alerts
        alertRepository.getAllAlerts().observe(this, alerts -> {
            if (alerts != null) {
                Log.d(TAG, "Alerts updated: " + alerts.size());
                // Update RecyclerView with alerts
            }
        });

        // Observe unread count
        alertRepository.getUnreadAlertCount().observe(this, count -> {
            if (count != null) {
                unreadCountTextView.setText("Unread: " + count);
                Log.d(TAG, "Unread alerts: " + count);
            }
        });
    }

    /**
     * Example: Mark alert as read
     */
    public void markAlertAsRead(String alertId) {
        alertRepository.markAlertAsRead(alertId, new AlertRepository.OnAlertUpdatedListener() {
            @Override
            public void onAlertUpdated() {
                Toast.makeText(AlertActivity.this, "Alert marked as read", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Alert marked as read");
            }

            @Override
            public void onError(String error) {
                Toast.makeText(AlertActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error marking alert as read: " + error);
            }
        });
    }

    /**
     * Example: Mark all alerts as read
     */
    public void markAllAlertsAsRead() {
        alertRepository.markAllAlertsAsRead(new AlertRepository.OnBatchUpdatedListener() {
            @Override
            public void onBatchUpdated(int count) {
                Toast.makeText(AlertActivity.this, "Marked " + count + " alerts as read", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "All alerts marked as read: " + count);
            }

            @Override
            public void onError(String error) {
                Toast.makeText(AlertActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error marking alerts as read: " + error);
            }
        });
    }

    /**
     * Example: Delete alert
     */
    public void deleteAlert(String alertId) {
        alertRepository.deleteAlert(alertId, new AlertRepository.OnAlertDeletedListener() {
            @Override
            public void onAlertDeleted() {
                Toast.makeText(AlertActivity.this, "Alert deleted", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Alert deleted");
            }

            @Override
            public void onError(String error) {
                Toast.makeText(AlertActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error deleting alert: " + error);
            }
        });
    }
}