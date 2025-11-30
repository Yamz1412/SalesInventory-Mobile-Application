package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class InventoryReportsActivity extends BaseActivity  {

    private Button btnStockValue, btnStockMovement, btnAdjustmentSummary, btnExport;
    private LinearLayout llReportCards;
    private DatabaseReference productRef, salesRef, adjustmentRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_inventory_reports);

        TextView tv = findViewById(android.R.id.text1);
        if (tv != null) tv.setText("Inventory Reports");

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Inventory Reports");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        initializeViews();
        setupClickListeners();
    }

    private void initializeViews() {
        btnStockValue = findViewById(R.id.btnStockValue);
        btnStockMovement = findViewById(R.id.btnStockMovement);
        btnAdjustmentSummary = findViewById(R.id.btnAdjustmentSummary);
        btnExport = findViewById(R.id.btnExport);

        productRef = FirebaseDatabase.getInstance().getReference("Product");
        salesRef = FirebaseDatabase.getInstance().getReference("Sales");
        adjustmentRef = FirebaseDatabase.getInstance().getReference("StockAdjustments");
    }

    private void setupClickListeners() {
        btnStockValue.setOnClickListener(v ->
                startActivity(new Intent(this, StockValueReportActivity.class)));

        btnStockMovement.setOnClickListener(v ->
                startActivity(new Intent(this, StockMovementReportActivity.class)));

        btnAdjustmentSummary.setOnClickListener(v ->
                startActivity(new Intent(this, AdjustmentSummaryReportActivity.class)));

        btnExport.setOnClickListener(v -> {
            // Export functionality can be added later
            android.widget.Toast.makeText(this, "Export feature coming soon!",
                    android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }
}