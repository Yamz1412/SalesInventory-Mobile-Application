package com.app.SalesInventory;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdjustmentSummaryReportActivity extends BaseActivity {

    private RecyclerView recyclerViewReport;
    private ProgressBar progressBar;
    private TextView tvNoData, tvTotalAdjustments, tvAdditions, tvRemovals;
    private Button btnExportPDF, btnExportCSV;
    private AdjustmentSummaryAdapter adapter;
    private List<AdjustmentSummaryReport> reportList;
    private DatabaseReference adjustmentRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_adjustment_summary_report);

        recyclerViewReport = findViewById(R.id.recyclerViewReport);
        progressBar = findViewById(R.id.progressBar);
        tvNoData = findViewById(R.id.tvNoData);
        tvTotalAdjustments = findViewById(R.id.tvTotalAdjustments);
        tvAdditions = findViewById(R.id.tvAdditions);
        tvRemovals = findViewById(R.id.tvRemovals);
        btnExportPDF = findViewById(R.id.btnExportPDF);
        btnExportCSV = findViewById(R.id.btnExportCSV);

        reportList = new ArrayList<>();
        adapter = new AdjustmentSummaryAdapter(reportList);
        recyclerViewReport.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewReport.setAdapter(adapter);

        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");

        loadSummary();
    }

    private void loadSummary() {
        progressBar.setVisibility(View.VISIBLE);
        adjustmentRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                Map<String, AdjustmentSummaryReport> productMap = new HashMap<>();
                int totalAdd = 0, totalRemove = 0, totalAdjustments = 0;

                for (DataSnapshot ds : snapshot.getChildren()) {
                    StockAdjustment adj = ds.getValue(StockAdjustment.class);
                    if (adj != null && adj.getProductId() != null) {
                        AdjustmentSummaryReport report = productMap.get(adj.getProductId());
                        if (report == null) {
                            report = new AdjustmentSummaryReport(adj.getProductId(), adj.getProductName());
                            productMap.put(adj.getProductId(), report);
                        }
                        report.addAdjustment(adj);
                        totalAdjustments++;
                        if ("Add Stock".equals(adj.getAdjustmentType())) totalAdd += adj.getQuantityAdjusted();
                        else totalRemove += adj.getQuantityAdjusted();
                    }
                }

                reportList.clear();
                reportList.addAll(productMap.values());
                progressBar.setVisibility(View.GONE);

                tvTotalAdjustments.setText(String.valueOf(totalAdjustments));
                tvAdditions.setText("+" + totalAdd + " units");
                tvRemovals.setText("-" + totalRemove + " units");

                adapter.notifyDataSetChanged();
                if (reportList.isEmpty()) {
                    tvNoData.setVisibility(View.VISIBLE);
                    recyclerViewReport.setVisibility(View.GONE);
                } else {
                    tvNoData.setVisibility(View.GONE);
                    recyclerViewReport.setVisibility(View.VISIBLE);
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