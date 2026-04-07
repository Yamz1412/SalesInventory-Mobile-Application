package com.app.SalesInventory;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AttendanceLogsActivity extends BaseActivity {

    private RecyclerView rvAttendanceLogs;
    private TextView tvNoLogs;
    private LogsAdapter adapter;
    private List<LogModel> logList = new ArrayList<>();
    private String currentOwnerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_logs);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        rvAttendanceLogs = findViewById(R.id.rvAttendanceLogs);
        tvNoLogs = findViewById(R.id.tvNoLogs);

        adapter = new LogsAdapter();
        rvAttendanceLogs.setLayoutManager(new LinearLayoutManager(this));
        rvAttendanceLogs.setAdapter(adapter);

        currentOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (currentOwnerId == null || currentOwnerId.isEmpty()) {
            currentOwnerId = AuthManager.getInstance().getCurrentUserId();
        }

        fetchLogs();
    }

    private void fetchLogs() {
        FirebaseFirestore.getInstance().collection("users")
                .document(currentOwnerId)
                .collection("attendance_logs")
                .orderBy("timestamp", Query.Direction.DESCENDING) // Newest first
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    logList.clear();
                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        LogModel log = new LogModel();
                        log.action = doc.getString("action");
                        log.userId = doc.getString("userId");
                        Long ts = doc.getLong("timestamp");
                        log.timestamp = ts != null ? ts : 0;
                        logList.add(log);
                    }

                    adapter.notifyDataSetChanged();
                    if (logList.isEmpty()) {
                        tvNoLogs.setVisibility(View.VISIBLE);
                        rvAttendanceLogs.setVisibility(View.GONE);
                    } else {
                        tvNoLogs.setVisibility(View.GONE);
                        rvAttendanceLogs.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to load logs", Toast.LENGTH_SHORT).show());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // --- RECYCLERVIEW ADAPTER ---
    private class LogsAdapter extends RecyclerView.Adapter<LogsAdapter.LogViewHolder> {
        private SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());

        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_attendance_log, parent, false);
            return new LogViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            LogModel log = logList.get(position);

            holder.tvLogAction.setText(log.action != null ? log.action : "Unknown Action");

            // If the user ID matches the owner, label them Admin, otherwise show ID
            if (log.userId != null && log.userId.equals(currentOwnerId)) {
                holder.tvLogUser.setText("User: Admin");
            } else {
                holder.tvLogUser.setText("User ID: " + (log.userId != null ? log.userId : "Unknown"));
            }

            Date date = new Date(log.timestamp);
            holder.tvLogTime.setText(timeFormat.format(date));
            holder.tvLogDate.setText(dateFormat.format(date));
        }

        @Override
        public int getItemCount() {
            return logList.size();
        }

        class LogViewHolder extends RecyclerView.ViewHolder {
            TextView tvLogAction, tvLogUser, tvLogTime, tvLogDate;

            LogViewHolder(@NonNull View itemView) {
                super(itemView);
                tvLogAction = itemView.findViewById(R.id.tvLogAction);
                tvLogUser = itemView.findViewById(R.id.tvLogUser);
                tvLogTime = itemView.findViewById(R.id.tvLogTime);
                tvLogDate = itemView.findViewById(R.id.tvLogDate);
            }
        }
    }

    // --- DATA MODEL ---
    private static class LogModel {
        String action;
        String userId;
        long timestamp;
    }
}