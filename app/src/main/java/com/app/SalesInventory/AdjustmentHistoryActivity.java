package com.app.SalesInventory;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.datepicker.CalendarConstraints;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

public class AdjustmentHistoryActivity extends BaseActivity {

    private RecyclerView recyclerViewHistory;
    private ProgressBar progressBar;
    private TextView tvNoData;
    private Button btnFilterDate;
    private ImageButton btnClearFilter;

    private StockAdjustmentAdapter adapter;
    private List<StockAdjustment> allAdjustments;
    private List<StockAdjustment> displayedList;

    private DatabaseReference adjustmentRef;
    private String userId;

    private Long filterStartOfDay = null;
    private Long filterEndOfDay = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adjustment_history);

        recyclerViewHistory = findViewById(R.id.recyclerViewHistory);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);
        btnFilterDate = findViewById(R.id.btnFilterDate);
        btnClearFilter = findViewById(R.id.btnClearFilter);

        allAdjustments = new ArrayList<>();
        displayedList = new ArrayList<>();

        adapter = new StockAdjustmentAdapter(displayedList, this::showDeleteOptions);
        recyclerViewHistory.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewHistory.setAdapter(adapter);

        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");
        FirebaseAuth fAuth = FirebaseAuth.getInstance();
        if (fAuth.getCurrentUser() != null) {
            userId = fAuth.getCurrentUser().getUid();
        }

        setupListeners();
        loadAdjustmentHistory();
    }

    private void setupListeners() {
        btnFilterDate.setOnClickListener(v -> showDatePicker());
        btnClearFilter.setOnClickListener(v -> clearFilter());
    }

    private void showDatePicker() {
        if (allAdjustments.isEmpty()) {
            Toast.makeText(this, "No history available to filter", Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. Gather all unique dates that have history
        Set<String> validDateStrings = new HashSet<>();
        SimpleDateFormat localSdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (StockAdjustment adj : allAdjustments) {
            validDateStrings.add(localSdf.format(new Date(adj.getTimestamp())));
        }

        // 2. Create constraints to disable dates with no history
        CalendarConstraints.Builder constraintsBuilder = new CalendarConstraints.Builder();
        constraintsBuilder.setValidator(new HistoryDateValidator(validDateStrings));

        // 3. Build the Material Calendar UI
        MaterialDatePicker<Long> datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Select Date With History")
                .setCalendarConstraints(constraintsBuilder.build())
                .build();

        datePicker.addOnPositiveButtonClickListener(selection -> {
            // MaterialDatePicker operates in UTC. Convert back to local timezone day start/end.
            Calendar utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            utcCal.setTimeInMillis(selection);

            Calendar localCal = Calendar.getInstance();
            localCal.set(utcCal.get(Calendar.YEAR), utcCal.get(Calendar.MONTH), utcCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0);
            localCal.set(Calendar.MILLISECOND, 0);
            filterStartOfDay = localCal.getTimeInMillis();

            localCal.set(Calendar.HOUR_OF_DAY, 23);
            localCal.set(Calendar.MINUTE, 59);
            localCal.set(Calendar.SECOND, 59);
            localCal.set(Calendar.MILLISECOND, 999);
            filterEndOfDay = localCal.getTimeInMillis();

            SimpleDateFormat displaySdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            btnFilterDate.setText(displaySdf.format(localCal.getTime()));
            btnClearFilter.setVisibility(View.VISIBLE);

            applyFilter();
        });

        datePicker.show(getSupportFragmentManager(), "HISTORY_DATE_PICKER");
    }

    private void clearFilter() {
        filterStartOfDay = null;
        filterEndOfDay = null;
        btnFilterDate.setText("Select Date");
        btnClearFilter.setVisibility(View.GONE);
        applyFilter();
    }

    private void loadAdjustmentHistory() {
        progressBar.setVisibility(View.VISIBLE);

        adjustmentRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                allAdjustments.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockAdjustment adjustment = ds.getValue(StockAdjustment.class);
                    if (adjustment != null && adjustment.getAdjustedBy() != null && adjustment.getAdjustedBy().equals(userId)) {
                        allAdjustments.add(adjustment);
                    }
                }
                progressBar.setVisibility(View.GONE);
                applyFilter();
            }

            @Override
            public void onCancelled(DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                tvNoData.setVisibility(View.VISIBLE);
            }
        });
    }

    private void applyFilter() {
        displayedList.clear();

        for (StockAdjustment adj : allAdjustments) {
            if (filterStartOfDay != null && filterEndOfDay != null) {
                if (adj.getTimestamp() >= filterStartOfDay && adj.getTimestamp() <= filterEndOfDay) {
                    displayedList.add(adj);
                }
            } else {
                displayedList.add(adj);
            }
        }

        Collections.sort(displayedList, (a1, a2) -> Long.compare(a2.getTimestamp(), a1.getTimestamp()));

        adapter.notifyDataSetChanged();

        if (displayedList.isEmpty()) {
            tvNoData.setVisibility(View.VISIBLE);
            recyclerViewHistory.setVisibility(View.GONE);
        } else {
            tvNoData.setVisibility(View.GONE);
            recyclerViewHistory.setVisibility(View.VISIBLE);
        }
    }

    public void showDeleteOptions(StockAdjustment adjustment) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Record")
                .setMessage("Are you sure you want to permanently delete this adjustment record for " + adjustment.getProductName() + "?")
                .setPositiveButton("Delete", (dialog, which) -> deleteAdjustment(adjustment))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteAdjustment(StockAdjustment adjustment) {
        if (adjustment.getAdjustmentId() != null) {
            adjustmentRef.child(adjustment.getAdjustmentId()).removeValue()
                    .addOnSuccessListener(aVoid -> Toast.makeText(AdjustmentHistoryActivity.this, "Record deleted successfully", Toast.LENGTH_SHORT).show())
                    .addOnFailureListener(e -> Toast.makeText(AdjustmentHistoryActivity.this, "Failed to delete: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        }
    }

    /**
     * Custom Validator to only allow dates that have an adjustment history.
     * All other dates on the calendar will be grayed out and unclickable.
     */
    public static class HistoryDateValidator implements CalendarConstraints.DateValidator {
        private final HashSet<String> validDates;

        public HistoryDateValidator(Set<String> validDates) {
            this.validDates = new HashSet<>(validDates);
        }

        protected HistoryDateValidator(Parcel in) {
            ArrayList<String> list = in.createStringArrayList();
            validDates = new HashSet<>(list != null ? list : new ArrayList<>());
        }

        public static final Creator<HistoryDateValidator> CREATOR = new Creator<HistoryDateValidator>() {
            @Override
            public HistoryDateValidator createFromParcel(Parcel in) {
                return new HistoryDateValidator(in);
            }

            @Override
            public HistoryDateValidator[] newArray(int size) {
                return new HistoryDateValidator[size];
            }
        };

        @Override
        public boolean isValid(long date) {
            // Check if the calendar date exists in our history dataset
            SimpleDateFormat utcFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            String dateStr = utcFormat.format(new Date(date));
            return validDates.contains(dateStr);
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeStringList(new ArrayList<>(validDates));
        }
    }
}