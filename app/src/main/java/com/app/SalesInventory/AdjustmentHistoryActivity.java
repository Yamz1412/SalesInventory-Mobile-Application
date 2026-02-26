package com.app.SalesInventory;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class AdjustmentHistoryActivity extends BaseActivity {

    private RecyclerView recyclerViewHistory;
    private ProgressBar progressBar;
    private TextView tvNoData;
    private StockAdjustmentAdapter adapter;
    private List<StockAdjustment> adjustmentList;
    private DatabaseReference adjustmentRef;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adjustment_history);

        recyclerViewHistory = findViewById(R.id.recyclerViewHistory);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);

        adjustmentList = new ArrayList<>();
        adapter = new StockAdjustmentAdapter(adjustmentList);
        recyclerViewHistory.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewHistory.setAdapter(adapter);

        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");
        FirebaseAuth fAuth = FirebaseAuth.getInstance();
        if (fAuth.getCurrentUser() != null) {
            userId = fAuth.getCurrentUser().getUid();
        }

        loadAdjustmentHistory();
    }

    private void loadAdjustmentHistory() {
        progressBar.setVisibility(View.VISIBLE);
        adjustmentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                adjustmentList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockAdjustment adjustment = ds.getValue(StockAdjustment.class);
                    if (adjustment != null && adjustment.getAdjustedBy() != null && adjustment.getAdjustedBy().equals(userId)) {
                        adjustmentList.add(adjustment);
                    }
                }
                progressBar.setVisibility(View.GONE);

                adapter.notifyDataSetChanged();
                if (adjustmentList.isEmpty()) {
                    tvNoData.setVisibility(View.VISIBLE);
                    recyclerViewHistory.setVisibility(View.GONE);
                } else {
                    tvNoData.setVisibility(View.GONE);
                    recyclerViewHistory.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onCancelled(DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                tvNoData.setVisibility(View.VISIBLE);
            }
        });
    }
}