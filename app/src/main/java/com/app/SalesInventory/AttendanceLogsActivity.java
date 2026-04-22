package com.app.SalesInventory;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import android.os.Parcel;
import android.os.Parcelable;
import java.util.HashSet;
import java.util.TimeZone;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AttendanceLogsActivity extends BaseActivity {

    private RecyclerView rvAttendanceLogs;
    private TextView tvNoLogs;
    private ProgressBar progressBar;
    private LinearLayout layoutAdminFilters;
    private Button btnFilterDate;
    private Spinner spinnerStaffFilter;

    private AggregatedAdapter adapter;
    private String currentOwnerId;
    private long selectedDateMillis;
    private boolean isInitialSpinnerSetup = true;

    // Real-time tracking maps
    private Map<String, List<DocumentSnapshot>> shiftDocsByUser = new HashMap<>();
    private Map<String, List<DocumentSnapshot>> logDocsByUser = new HashMap<>();
    private ListenerRegistration shiftsListener;
    private ListenerRegistration logsListener;
    private List<AggregatedLog> masterList = new ArrayList<>();

    private FirebaseFirestore fStore;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_logs);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Attendance Logs");
            getSupportActionBar().setSubtitle("Monitors daily staff shifts, locked breaks, and total hours");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        fStore = FirebaseFirestore.getInstance();

        rvAttendanceLogs = findViewById(R.id.rvAttendanceLogs);
        tvNoLogs = findViewById(R.id.tvNoLogs);
        if (tvNoLogs != null) {
            tvNoLogs.setText("No attendance records for this date. Staff time-ins, time-outs, and breaks will be logged here.");
        }

        progressBar = findViewById(R.id.progressBar);
        layoutAdminFilters = findViewById(R.id.layoutAdminFilters);
        btnFilterDate = findViewById(R.id.btnFilterDate);
        spinnerStaffFilter = findViewById(R.id.spinnerStaffFilter);

        rvAttendanceLogs.setLayoutManager(new LinearLayoutManager(this));

        currentOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (currentOwnerId == null || currentOwnerId.isEmpty()) {
            currentOwnerId = AuthManager.getInstance().getCurrentUserId();
        }

        selectedDateMillis = System.currentTimeMillis();
        updateDateButtonText();

        if (layoutAdminFilters != null) {
            layoutAdminFilters.setVisibility(View.VISIBLE);
        }

        btnFilterDate.setOnClickListener(v -> showDatePicker());

        spinnerStaffFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (!isInitialSpinnerSetup) {
                    applyFilters();
                }
                isInitialSpinnerSetup = false;
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        loadRealtimeLogs(selectedDateMillis);
    }

    private void updateDateButtonText() {
        btnFilterDate.setText(dateFormat.format(new Date(selectedDateMillis)));
    }

    private void showDatePicker() {
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Shift Date")
                .setSelection(selectedDateMillis)
                .setTheme(R.style.CustomCalendarTheme)
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar selectedCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            selectedCal.setTimeInMillis(selection);

            Calendar localCal = Calendar.getInstance();
            localCal.set(selectedCal.get(Calendar.YEAR), selectedCal.get(Calendar.MONTH), selectedCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0);

            selectedDateMillis = localCal.getTimeInMillis();
            updateDateButtonText();
            loadRealtimeLogs(selectedDateMillis);
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    // ====================================================================================
    // CRITICAL FIX: Real-Time Listeners that instantly fetch and map data
    // ====================================================================================
    private void loadRealtimeLogs(long dateInMillis) {
        if (shiftsListener != null) shiftsListener.remove();
        if (logsListener != null) logsListener.remove();

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dateInMillis);
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();
        cal.add(Calendar.DAY_OF_MONTH, 1);
        long endOfDay = cal.getTimeInMillis() - 1;

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        String dateKey = sdf.format(new Date(dateInMillis));

        progressBar.setVisibility(View.VISIBLE);

        // 1. Listen for instant Shift changes (Login / Logout / Active Status)
        shiftsListener = fStore.collection("users").document(currentOwnerId).collection("shifts")
                .whereGreaterThanOrEqualTo("startTime", startOfDay)
                .whereLessThanOrEqualTo("startTime", endOfDay)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) return;
                    shiftDocsByUser.clear();
                    if (snap != null) {
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            String uid = doc.getString("cashierId");
                            if (uid != null) {
                                if (!shiftDocsByUser.containsKey(uid)) shiftDocsByUser.put(uid, new ArrayList<>());
                                shiftDocsByUser.get(uid).add(doc);
                            }
                        }
                    }
                    buildRealtimeGroups();
                });

        // 2. Listen for instant Break changes (Screen Lock / Unlock)
        logsListener = fStore.collection("AttendanceLogs").document(currentOwnerId).collection(dateKey)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) return;
                    logDocsByUser.clear();
                    if (snap != null) {
                        for (DocumentSnapshot doc : snap.getDocuments()) {
                            String uid = doc.getString("userId");
                            if (uid != null) {
                                if (!logDocsByUser.containsKey(uid)) logDocsByUser.put(uid, new ArrayList<>());
                                logDocsByUser.get(uid).add(doc);
                            }
                        }
                    }
                    buildRealtimeGroups();
                });
    }

    // ====================================================================================
    // CRITICAL FIX: The Grouping Engine (Eliminates Duplicates & Calculating Bugs)
    // ====================================================================================
    private void buildRealtimeGroups() {
        masterList.clear();

        for (String uid : shiftDocsByUser.keySet()) {
            List<DocumentSnapshot> shifts = shiftDocsByUser.get(uid);
            if (shifts == null || shifts.isEmpty()) continue;

            AggregatedLog log = new AggregatedLog();
            log.cashierId = uid;
            log.cashierName = shifts.get(0).getString("cashierName");
            if (log.cashierName == null) log.cashierName = "Unknown Staff";
            log.startTime = Long.MAX_VALUE;
            log.endTime = 0;
            log.isActive = false;
            log.isLocked = false;

            // Merge all shifts for the day into ONE row
            for (DocumentSnapshot s : shifts) {
                Long st = s.getLong("startTime");
                Long et = s.getLong("endTime");
                Boolean act = s.getBoolean("active");
                Boolean lck = s.getBoolean("locked");

                if (st != null && st < log.startTime) log.startTime = st;
                if (et != null && et > log.endTime) log.endTime = et;
                if (act != null && act) {
                    log.isActive = true;
                    log.endTime = System.currentTimeMillis();
                    if (lck != null && lck) log.isLocked = true;
                }
            }

            if (log.startTime == Long.MAX_VALUE) continue;

            // Instantly Calculate Breaks in memory (No more 'Calculating...')
            log.totalBreakMillis = 0;
            if (logDocsByUser.containsKey(uid)) {
                List<DocumentSnapshot> userLogs = new ArrayList<>(logDocsByUser.get(uid));
                Collections.sort(userLogs, (d1, d2) -> {
                    Long t1 = d1.getLong("timestamp"); if (t1 == null) t1 = 0L;
                    Long t2 = d2.getLong("timestamp"); if (t2 == null) t2 = 0L;
                    return t1.compareTo(t2);
                });

                long currentBreakStart = 0;
                for (DocumentSnapshot doc : userLogs) {
                    String status = doc.getString("status");
                    Long ts = doc.getLong("timestamp");
                    if (status == null || ts == null) continue;

                    if (status.equals("BREAK_START")) {
                        currentBreakStart = ts;
                    } else if (status.equals("BREAK_END") || status.equals("SHIFT_END")) {
                        if (currentBreakStart > 0) {
                            log.totalBreakMillis += (ts - currentBreakStart);
                            currentBreakStart = 0;
                        }
                    }
                }
                // Actively count real-time breaks right now
                if (currentBreakStart > 0 && log.isActive) {
                    log.totalBreakMillis += (System.currentTimeMillis() - currentBreakStart);
                    log.isLocked = true;
                }
            }

            masterList.add(log);
        }

        Collections.sort(masterList, (a, b) -> Long.compare(a.startTime, b.startTime));
        populateSpinner();
        applyFilters();
    }

    private void populateSpinner() {
        List<String> staffNames = new ArrayList<>();
        staffNames.add("All Staff");
        for (AggregatedLog log : masterList) {
            if (!staffNames.contains(log.cashierName)) {
                staffNames.add(log.cashierName);
            }
        }

        String currentSelection = spinnerStaffFilter.getSelectedItem() != null ? spinnerStaffFilter.getSelectedItem().toString() : "All Staff";

        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, staffNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerStaffFilter.setAdapter(spinnerAdapter);

        int pos = staffNames.indexOf(currentSelection);
        if (pos >= 0) spinnerStaffFilter.setSelection(pos);
    }

    private void applyFilters() {
        List<AggregatedLog> filteredList = new ArrayList<>();
        String selectedStaff = spinnerStaffFilter.getSelectedItem() != null ? spinnerStaffFilter.getSelectedItem().toString() : "All Staff";

        if (selectedStaff.equals("All Staff")) {
            filteredList.addAll(masterList);
        } else {
            for (AggregatedLog log : masterList) {
                if (log.cashierName.equalsIgnoreCase(selectedStaff)) {
                    filteredList.add(log);
                }
            }
        }

        if (adapter == null) {
            adapter = new AggregatedAdapter(filteredList);
            rvAttendanceLogs.setAdapter(adapter);
        } else {
            adapter.setList(filteredList);
        }

        progressBar.setVisibility(View.GONE);
        if (filteredList.isEmpty()) {
            tvNoLogs.setVisibility(View.VISIBLE);
        } else {
            tvNoLogs.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (shiftsListener != null) shiftsListener.remove();
        if (logsListener != null) logsListener.remove();
    }

    // ====================================================================================
    // CLASSES: Unified Data Model and Fast Adapter
    // ====================================================================================
    public static class AggregatedLog {
        String cashierId;
        String cashierName;
        long startTime;
        long endTime;
        boolean isActive;
        boolean isLocked;
        long totalBreakMillis;
    }

    public class AggregatedAdapter extends RecyclerView.Adapter<AggregatedAdapter.VH> {
        private List<AggregatedLog> list;

        public AggregatedAdapter(List<AggregatedLog> list) {
            this.list = list;
        }

        public void setList(List<AggregatedLog> newList) {
            this.list = newList;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_shift_log, parent, false);
            return new VH(view);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            AggregatedLog log = list.get(position);

            holder.tvShiftStaffName.setText(log.cashierName);
            holder.tvShiftDate.setText("Date: " + dateFormat.format(new Date(log.startTime)));

            String endTimeStr = log.isActive ? "Present" : timeFormat.format(new Date(log.endTime));
            holder.tvShiftTime.setText("Time: " + timeFormat.format(new Date(log.startTime)) + " - " + endTimeStr);

            // Format Instantly Calculated Break Time
            long breakMins = (log.totalBreakMillis / 1000) / 60;
            long breakHours = breakMins / 60;
            long remainingMins = breakMins % 60;
            String breakText = breakHours > 0 ? breakHours + " hr " + remainingMins + " mins" : breakMins + " mins";
            holder.tvShiftBreak.setText("Total Break Time: " + breakText);

            if (log.isActive) {
                if (log.isLocked) {
                    holder.tvShiftStatus.setText("ON BREAK");
                    holder.tvShiftStatus.setTextColor(getResources().getColor(R.color.warningYellow));
                } else {
                    holder.tvShiftStatus.setText("ACTIVE");
                    holder.tvShiftStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                }
            } else {
                holder.tvShiftStatus.setText("CLOSED");
                holder.tvShiftStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
            }
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        class VH extends RecyclerView.ViewHolder {
            TextView tvShiftStaffName, tvShiftStatus, tvShiftDate, tvShiftTime, tvShiftBreak;

            VH(@NonNull View itemView) {
                super(itemView);
                tvShiftStaffName = itemView.findViewById(R.id.tvShiftStaffName);
                tvShiftStatus = itemView.findViewById(R.id.tvShiftStatus);
                tvShiftDate = itemView.findViewById(R.id.tvShiftDate);
                tvShiftTime = itemView.findViewById(R.id.tvShiftTime);
                tvShiftBreak = itemView.findViewById(R.id.tvShiftBreak);
            }
        }
    }
}