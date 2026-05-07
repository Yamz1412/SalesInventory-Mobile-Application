package com.app.SalesInventory;

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
import android.widget.Toast;

import com.google.android.material.datepicker.MaterialDatePicker;
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

    private Map<String, List<DocumentSnapshot>> shiftDocsByUser = new HashMap<>();
    private Map<String, List<DocumentSnapshot>> logDocsByUser = new HashMap<>();
    private ListenerRegistration shiftsListener;
    private ListenerRegistration logsListener;
    private List<AggregatedLog> masterList = new ArrayList<>();
    private List<String> allRegisteredStaffNames = new ArrayList<>();
    private java.util.HashSet<Long> validShiftDates = new java.util.HashSet<>();
    private Map<String, String> registeredStaffMap = new HashMap<>();
    private FirebaseFirestore fStore;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
    private final SimpleDateFormat timeFormat = new SimpleDateFormat("hh:mm a", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_attendance_logs);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Attendance Logs");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        fStore = FirebaseFirestore.getInstance();
        rvAttendanceLogs = findViewById(R.id.rvAttendanceLogs);
        tvNoLogs = findViewById(R.id.tvNoLogs);
        progressBar = findViewById(R.id.progressBar);
        layoutAdminFilters = findViewById(R.id.layoutAdminFilters);
        btnFilterDate = findViewById(R.id.btnDateFilter);
        spinnerStaffFilter = findViewById(R.id.spinnerStaffFilter);

        if (rvAttendanceLogs != null) rvAttendanceLogs.setLayoutManager(new LinearLayoutManager(this));

        selectedDateMillis = System.currentTimeMillis();
        updateDateButtonText();

        // CRITICAL FIX: Bypass the unreliable StaffDataManager and directly fetch the cached Owner ID
        currentOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (currentOwnerId == null || currentOwnerId.isEmpty()) {
            currentOwnerId = AuthManager.getInstance().getCurrentUserId();
        }

        // Instantly load the listeners without waiting for a faulty callback
        if (currentOwnerId != null && !currentOwnerId.isEmpty()) {
            setupListenersAndLoad();
        } else {
            Toast.makeText(this, "Error resolving business account.", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void setupListenersAndLoad() {
        if (layoutAdminFilters != null) layoutAdminFilters.setVisibility(View.VISIBLE);
        if (btnFilterDate != null) btnFilterDate.setOnClickListener(v -> showDatePicker());

        if (spinnerStaffFilter != null) {
            spinnerStaffFilter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                    if (!isInitialSpinnerSetup) applyFilters();
                    isInitialSpinnerSetup = false;
                }
                @Override public void onNothingSelected(AdapterView<?> p) {}
            });
        }

        // Fetch all registered staff and valid dates immediately
        fetchAllRegisteredStaff();
        fetchValidShiftDates();

        loadRealtimeLogs(selectedDateMillis);
    }

    private void fetchAllRegisteredStaff() {
        registeredStaffMap.clear();
        allRegisteredStaffNames.clear();
        allRegisteredStaffNames.add("All Staff");

        // 1. Add the Admin to the staff list
        fStore.collection("users").document(currentOwnerId).get().addOnSuccessListener(doc -> {
            if (doc.exists()) {
                String name = doc.getString("name");
                if (name == null) name = doc.getString("Name");
                if (name != null) {
                    registeredStaffMap.put(currentOwnerId, name);
                    if (!allRegisteredStaffNames.contains(name)) allRegisteredStaffNames.add(name);
                }
            }
        });

        // 2. Add all Sub-Admins and Staff
        fStore.collection("users").whereEqualTo("ownerAdminId", currentOwnerId).get().addOnSuccessListener(query -> {
            for (DocumentSnapshot doc : query.getDocuments()) {
                String name = doc.getString("name");
                if (name == null) name = doc.getString("Name");
                if (name != null) {
                    registeredStaffMap.put(doc.getId(), name);
                    if (!allRegisteredStaffNames.contains(name)) allRegisteredStaffNames.add(name);
                }
            }
            populateSpinner();
            buildRealtimeGroups(); // Re-trigger to ensure offline staff appear
        });
    }

    private void fetchValidShiftDates() {
        fStore.collection("users").document(currentOwnerId).collection("shifts")
                .get()
                .addOnSuccessListener(query -> {
                    validShiftDates.clear();
                    Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));

                    for (DocumentSnapshot doc : query.getDocuments()) {
                        Long st = doc.getLong("startTime");
                        if (st != null) {
                            // Sync local shift time to UTC Midnight for the Material Calendar UI
                            Calendar localCal = Calendar.getInstance();
                            localCal.setTimeInMillis(st);

                            utcCal.set(Calendar.YEAR, localCal.get(Calendar.YEAR));
                            utcCal.set(Calendar.MONTH, localCal.get(Calendar.MONTH));
                            utcCal.set(Calendar.DAY_OF_MONTH, localCal.get(Calendar.DAY_OF_MONTH));
                            utcCal.set(Calendar.HOUR_OF_DAY, 0);
                            utcCal.set(Calendar.MINUTE, 0);
                            utcCal.set(Calendar.SECOND, 0);
                            utcCal.set(Calendar.MILLISECOND, 0);

                            validShiftDates.add(utcCal.getTimeInMillis());
                        }
                    }
                });
    }

    private void updateDateButtonText() {
        if (btnFilterDate != null) {
            btnFilterDate.setText(dateFormat.format(new Date(selectedDateMillis)));
        }
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        calendar.setTimeInMillis(selectedDateMillis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        com.google.android.material.datepicker.CalendarConstraints.Builder constraintsBuilder =
                new com.google.android.material.datepicker.CalendarConstraints.Builder();

        // Lock the calendar to only show days that have recorded shifts
        if (!validShiftDates.isEmpty()) {
            constraintsBuilder.setValidator(new ValidDatesValidator(validShiftDates));
        }

        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Shift Date")
                .setSelection(calendar.getTimeInMillis())
                .setCalendarConstraints(constraintsBuilder.build())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            Calendar selectedCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            selectedCal.setTimeInMillis(selection);

            Calendar localCal = Calendar.getInstance();
            localCal.set(selectedCal.get(Calendar.YEAR),
                    selectedCal.get(Calendar.MONTH),
                    selectedCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0);

            selectedDateMillis = localCal.getTimeInMillis();
            updateDateButtonText();
            loadRealtimeLogs(selectedDateMillis);
        });

        datePicker.show(getSupportFragmentManager(), "DATE_PICKER");
    }

    public static class ValidDatesValidator implements com.google.android.material.datepicker.CalendarConstraints.DateValidator {
        private final java.util.HashSet<Long> validDates;

        public ValidDatesValidator(java.util.HashSet<Long> validDates) {
            this.validDates = validDates;
        }

        public static final android.os.Parcelable.Creator<ValidDatesValidator> CREATOR = new android.os.Parcelable.Creator<ValidDatesValidator>() {
            @Override
            public ValidDatesValidator createFromParcel(android.os.Parcel source) {
                int size = source.readInt();
                java.util.HashSet<Long> dates = new java.util.HashSet<>();
                for (int i = 0; i < size; i++) {
                    dates.add(source.readLong());
                }
                return new ValidDatesValidator(dates);
            }

            @Override
            public ValidDatesValidator[] newArray(int size) {
                return new ValidDatesValidator[size];
            }
        };

        @Override
        public boolean isValid(long date) {
            return validDates.contains(date);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(android.os.Parcel dest, int flags) {
            dest.writeInt(validDates.size());
            for (Long date : validDates) {
                dest.writeLong(date);
            }
        }
    }

    private void loadRealtimeLogs(long dateInMillis) {
        if (shiftsListener != null) shiftsListener.remove();
        if (logsListener != null) logsListener.remove();

        if (currentOwnerId == null || currentOwnerId.isEmpty()) {
            currentOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();
        }

        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(dateInMillis);
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0);
        long startOfDay = cal.getTimeInMillis();
        long endOfDay = startOfDay + 86399999L; // 23:59:59.999

        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);

        // CRITICAL FIX: STRICTLY filter shifts that actually started on this exact day. No ghost shifts allowed!
        shiftsListener = fStore.collection("users").document(currentOwnerId).collection("shifts")
                .whereGreaterThanOrEqualTo("startTime", startOfDay)
                .whereLessThanOrEqualTo("startTime", endOfDay)
                .addSnapshotListener((snap, e) -> {
                    if (e != null) { if (progressBar != null) progressBar.setVisibility(View.GONE); return; }
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
    }

    private void buildRealtimeGroups() {
        masterList.clear();
        long now = System.currentTimeMillis();

        // 1. Process staff who ACTUALLY worked on the selected date
        for (String uid : shiftDocsByUser.keySet()) {
            List<DocumentSnapshot> shifts = shiftDocsByUser.get(uid);
            if (shifts == null || shifts.isEmpty()) continue;

            for (DocumentSnapshot s : shifts) {
                AggregatedLog log = new AggregatedLog();
                log.cashierId = uid;
                log.cashierName = s.getString("cashierName");
                if (log.cashierName == null) log.cashierName = registeredStaffMap.getOrDefault(uid, "Unknown Staff");

                Long st = s.getLong("startTime");
                Long et = s.getLong("endTime");
                Boolean act = s.getBoolean("active");
                Boolean lck = s.getBoolean("locked");

                log.startTime = st != null ? st : 0;
                log.endTime = et != null ? et : 0;
                log.isActive = Boolean.TRUE.equals(act);
                log.isLocked = Boolean.TRUE.equals(lck);
                log.isOffline = false;

                long totalBreak = 0;
                List<Long> locks = (List<Long>) s.get("lockTimes");
                List<Long> unlocks = (List<Long>) s.get("unlockTimes");

                if (locks != null) {
                    int unlocksSize = unlocks != null ? unlocks.size() : 0;
                    int pairs = Math.min(locks.size(), unlocksSize);

                    for (int i = 0; i < pairs; i++) {
                        long duration = unlocks.get(i) - locks.get(i);
                        if (duration > 0) totalBreak += duration;
                    }

                    if (log.isActive && log.isLocked && locks.size() > unlocksSize) {
                        long ongoing = now - locks.get(locks.size() - 1);
                        if (ongoing > 0) totalBreak += ongoing;
                    }
                }

                log.totalBreakMillis = totalBreak;
                masterList.add(log);
            }
        }

        // 2. CRITICAL FIX: Add all other registered staff who did NOT work on this date as "OFFLINE"
        for (Map.Entry<String, String> entry : registeredStaffMap.entrySet()) {
            String uid = entry.getKey();
            if (!shiftDocsByUser.containsKey(uid)) {
                AggregatedLog log = new AggregatedLog();
                log.cashierId = uid;
                log.cashierName = entry.getValue();
                log.startTime = selectedDateMillis; // Bind it to the selected date visually
                log.endTime = 0;
                log.isActive = false;
                log.isLocked = false;
                log.isOffline = true;
                log.totalBreakMillis = 0;
                masterList.add(log);
            }
        }

        // Sort: Active first, then Closed, then Offline, alphabetically by name
        Collections.sort(masterList, (a, b) -> {
            if (a.isOffline && !b.isOffline) return 1;
            if (!a.isOffline && b.isOffline) return -1;
            if (a.isActive && !b.isActive) return -1;
            if (!a.isActive && b.isActive) return 1;
            return a.cashierName.compareToIgnoreCase(b.cashierName);
        });

        populateSpinner();
        applyFilters();
    }


    private void populateSpinner() {
        if (spinnerStaffFilter == null) return;

        List<String> staffNames = new ArrayList<>(allRegisteredStaffNames);
        if (staffNames.isEmpty()) staffNames.add("All Staff");

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
        String selectedStaff = "All Staff";
        if (spinnerStaffFilter != null && spinnerStaffFilter.getSelectedItem() != null) {
            selectedStaff = spinnerStaffFilter.getSelectedItem().toString();
        }

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
            if (rvAttendanceLogs != null) rvAttendanceLogs.setAdapter(adapter);
        } else {
            adapter.setList(filteredList);
        }

        if (progressBar != null) progressBar.setVisibility(View.GONE);
        if (tvNoLogs != null) {
            if (filteredList.isEmpty()) {
                tvNoLogs.setVisibility(View.VISIBLE);
            } else {
                tvNoLogs.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (shiftsListener != null) shiftsListener.remove();
        if (logsListener != null) logsListener.remove();
    }

    public static class AggregatedLog {
        String cashierId;
        String cashierName;
        long startTime;
        long endTime;
        boolean isActive;
        boolean isLocked;
        boolean isOffline;
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

            // CRITICAL FIX: Explicitly handle the OFFLINE state first
            if (log.isOffline) {
                holder.tvShiftStatus.setText("OFFLINE");
                holder.tvShiftStatus.setTextColor(getResources().getColor(android.R.color.darker_gray));
                holder.tvShiftTime.setText("Time: No shift recorded");
                holder.tvShiftBreak.setText("Total Break Time: 0 mins");
                return;
            }

            boolean isToday = android.text.format.DateUtils.isToday(log.startTime);
            String endTimeStr;

            if (log.isActive && isToday) {
                endTimeStr = "Present";
            } else if (log.endTime > 0) {
                endTimeStr = timeFormat.format(new Date(log.endTime));
            } else {
                endTimeStr = "System Closed";
            }

            holder.tvShiftTime.setText("Time: " + timeFormat.format(new Date(log.startTime)) + " - " + endTimeStr);

            long breakMins = (log.totalBreakMillis / 1000) / 60;
            long breakHours = breakMins / 60;
            long remainingMins = breakMins % 60;

            String breakText = breakHours > 0 ? breakHours + " hr " + Math.max(0, remainingMins) + " mins" : Math.max(0, breakMins) + " mins";
            holder.tvShiftBreak.setText("Total Break Time: " + breakText);

            if (log.isActive && isToday) {
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