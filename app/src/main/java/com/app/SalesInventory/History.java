package com.app.SalesInventory;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class History extends BaseActivity {

    private RecyclerView recyclerView;
    private HistoryAdapter adapter;
    private List<Sales> salesList = new ArrayList<>();
    private SalesRepository salesRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        recyclerView = findViewById(R.id.recyclerViewHistory);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));

        salesRepository = SalesInventoryApplication.getSalesRepository();

        adapter = new HistoryAdapter(this, salesList, sale -> confirmVoidSale(sale));
        recyclerView.setAdapter(adapter);

        loadSalesHistory();
    }

    private void loadSalesHistory() {
        salesRepository.getAllSales().observe(this, sales -> {
            if (sales != null) {
                salesList.clear();
                salesList.addAll(sales);

                // Sort by newest first
                Collections.sort(salesList, (s1, s2) -> Long.compare(s2.getTimestamp(), s1.getTimestamp()));
                adapter.notifyDataSetChanged();
            }
        });
    }

    private void confirmVoidSale(Sales sale) {
        new AlertDialog.Builder(this)
                .setTitle("Void Transaction")
                .setMessage("Are you sure you want to VOID this sale?\n\nThis will return the stock to inventory and deduct the total from your cash/wallet.")
                .setPositiveButton("VOID", (dialog, which) -> {
                    salesRepository.voidSale(sale, new SalesRepository.OnSaleVoidedListener() {
                        @Override
                        public void onSuccess() {
                            Toast.makeText(History.this, "Sale Voided. Stock Returned.", Toast.LENGTH_LONG).show();
                        }
                        @Override
                        public void onError(String error) {
                            Toast.makeText(History.this, "Failed to void: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}