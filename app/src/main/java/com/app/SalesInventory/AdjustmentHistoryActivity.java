package com.app.SalesInventory;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
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

        recyclerViewHistory = findViewById(R.id.recyclerViewHistory);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);

        adjustmentList = new ArrayList<>();
        adapter = new StockAdjustmentAdapter(adjustmentList);
        recyclerViewHistory.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewHistory.setAdapter(adapter);

        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");

        loadAdjustments();
    }

    private void loadAdjustments() {
        progressBar.setVisibility(View.VISIBLE);
        tvNoData.setVisibility(View.GONE);
        recyclerViewHistory.setVisibility(View.GONE);

        adjustmentRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                adjustmentList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockAdjustment adjustment = ds.getValue(StockAdjustment.class);
                    if (adjustment != null) {
                        adjustmentList.add(adjustment);
                    }
                }
                Collections.sort(adjustmentList, new Comparator<StockAdjustment>() {
                    @Override
                    public int compare(StockAdjustment o1, StockAdjustment o2) {
                        return Long.compare(o2.getTimestamp(), o1.getTimestamp());
                    }
                });

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
                Toast.makeText(AdjustmentHistoryActivity.this, "Error: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}