package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AdjustmentHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerViewHistory;
    private ProgressBar progressBar;
    private TextView tvNoData;
    private StockAdjustmentAdapter adapter;
    private List<StockAdjustment> adjustmentList;
    private DatabaseReference adjustmentRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adjustment_history);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Stock Adjustment History");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        recyclerViewHistory = findViewById(R.id.recyclerViewHistory);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);

        adjustmentList = new ArrayList<>();
        adapter = new StockAdjustmentAdapter(adjustmentList);
        recyclerViewHistory.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewHistory.setAdapter(adapter);

        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");

        loadAdjustmentHistory();
    }

    private void loadAdjustmentHistory() {
        progressBar.setVisibility(View.VISIBLE);
        tvNoData.setVisibility(View.GONE);

        Query query = adjustmentRef.orderByChild("timestamp");
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                adjustmentList.clear();

                for (DataSnapshot dataSnapshot : snapshot.getChildren()) {
                    StockAdjustment adjustment = dataSnapshot.getValue(StockAdjustment.class);
                    if (adjustment != null) {
                        adjustmentList.add(adjustment);
                    }
                }

                // Sort by timestamp in descending order (newest first)
                Collections.reverse(adjustmentList);

                progressBar.setVisibility(View.GONE);

                if (adjustmentList.isEmpty()) {
                    tvNoData.setVisibility(View.VISIBLE);
                    recyclerViewHistory.setVisibility(View.GONE);
                } else {
                    tvNoData.setVisibility(View.GONE);
                    recyclerViewHistory.setVisibility(View.VISIBLE);
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(AdjustmentHistoryActivity.this,
                        "Error loading history: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}