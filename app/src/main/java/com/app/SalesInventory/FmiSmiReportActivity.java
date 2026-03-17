package com.app.SalesInventory;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButtonToggleGroup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FmiSmiReportActivity extends AppCompatActivity {

    private RecyclerView rvFmiSmi;
    private MaterialButtonToggleGroup toggleGroup;
    private FmiSmiAdapter adapter;
    private Spinner spinnerProductLine; // NEW

    private ProductRepository productRepository;
    private SalesRepository salesRepository;

    private List<Product> allProducts = new ArrayList<>();
    private List<Sales> last30DaysSales = new ArrayList<>();
    private List<ReportItem> currentDisplayList = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_fmi_smi_report);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        rvFmiSmi = findViewById(R.id.rvFmiSmi);
        toggleGroup = findViewById(R.id.toggleGroup);
        spinnerProductLine = findViewById(R.id.spinnerProductLine); // NEW

        rvFmiSmi.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FmiSmiAdapter();
        rvFmiSmi.setAdapter(adapter);

        productRepository = SalesInventoryApplication.getProductRepository();
        salesRepository = SalesRepository.getInstance(getApplication());

        toggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                calculateAndDisplayData(checkedId == R.id.btnFmi);
            }
        });
        spinnerProductLine.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (!allProducts.isEmpty()) {
                    boolean isFmi = toggleGroup.getCheckedButtonId() == R.id.btnFmi;
                    calculateAndDisplayData(isFmi);
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
        // Load the lines from the database
        loadProductLinesFilter();
        loadData();
    }

    private void loadProductLinesFilter() {
        String currentUserId = AuthManager.getInstance().getCurrentUserId();
        com.google.firebase.database.DatabaseReference ref = com.google.firebase.database.FirebaseDatabase.getInstance().getReference("ProductLines");

        ref.orderByChild("ownerAdminId").equalTo(currentUserId).addListenerForSingleValueEvent(new com.google.firebase.database.ValueEventListener() {
            @Override
            public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                List<String> lines = new ArrayList<>();
                lines.add("All Product Lines"); // Default option

                // Add Defaults
                lines.add("Core Products"); lines.add("Specialty / Unique Offerings");
                lines.add("Complementary Goods"); lines.add("Retail / Merchandise");
                lines.add("Seasonal Items"); lines.add("Grab-and-Go");

                // Add Custom lines from DB
                for (com.google.firebase.database.DataSnapshot ds : snapshot.getChildren()) {
                    String lineName = ds.child("lineName").getValue(String.class);
                    if (lineName != null && !lines.contains(lineName)) lines.add(lineName);
                }

                android.widget.ArrayAdapter<String> lineAdapter = new android.widget.ArrayAdapter<>(FmiSmiReportActivity.this, android.R.layout.simple_spinner_item, lines);
                lineAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                spinnerProductLine.setAdapter(lineAdapter);
            }
            @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
        });
    }

    private void loadData() {
        // Load Products
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                allProducts = products;
                checkDataReady();
            }
        });

        // Load Sales from the last 30 days
        long now = System.currentTimeMillis();
        long thirtyDaysAgo = now - (30L * 24L * 60L * 60L * 1000L);
        MutableLiveData<List<Sales>> salesLiveData = new MutableLiveData<>();

        salesRepository.getSalesByDateRange(thirtyDaysAgo, now, salesLiveData);
        salesLiveData.observe(this, sales -> {
            if (sales != null) {
                last30DaysSales = sales;
                checkDataReady();
            }
        });
    }

    private void checkDataReady() {
        if (!allProducts.isEmpty()) {
            boolean isFmi = toggleGroup.getCheckedButtonId() == R.id.btnFmi;
            calculateAndDisplayData(isFmi);
        }
    }

    private void calculateAndDisplayData(boolean isFmi) {
        Map<String, Integer> salesCountMap = new HashMap<>();

        // Aggregate quantities sold per product
        for (Sales s : last30DaysSales) {
            String baseName = cleanProductName(s.getProductName());
            int currentQty = salesCountMap.containsKey(baseName) ? salesCountMap.get(baseName) : 0;
            salesCountMap.put(baseName, currentQty + s.getQuantity());
        }

        List<ReportItem> reportItems = new ArrayList<>();
        String selectedLine = spinnerProductLine.getSelectedItem() != null ? spinnerProductLine.getSelectedItem().toString() : "All Product Lines";

        // Match with inventory to include items that have ZERO sales
        for (Product p : allProducts) {
            if (!p.isActive()) continue;

            if (!selectedLine.equals("All Product Lines")) {
                String pLine = p.getProductLine() != null ? p.getProductLine() : "";
                if (!pLine.equalsIgnoreCase(selectedLine)) continue;
            }
            
            String pName = p.getProductName();
            int qtySold = salesCountMap.containsKey(pName) ? salesCountMap.get(pName) : 0;

            reportItems.add(new ReportItem(p, qtySold));
        }

        // Sort Data
        if (isFmi) {
            // FMI: Sort Highest to Lowest
            Collections.sort(reportItems, (a, b) -> Integer.compare(b.qtySold, a.qtySold));
            // Optional: Remove items with 0 sales from the FMI view
            reportItems.removeIf(item -> item.qtySold == 0);
        } else {
            // SMI: Sort Lowest to Highest (Zeroes first)
            Collections.sort(reportItems, (a, b) -> Integer.compare(a.qtySold, b.qtySold));
        }

        currentDisplayList = reportItems;
        adapter.notifyDataSetChanged();
    }

    private String cleanProductName(String rawName) {
        if (rawName == null) return "";
        int parenIdx = rawName.indexOf(" (");
        int bracketIdx = rawName.indexOf(" [");
        int minIdx = rawName.length();
        if (parenIdx != -1) minIdx = Math.min(minIdx, parenIdx);
        if (bracketIdx != -1) minIdx = Math.min(minIdx, bracketIdx);
        return rawName.substring(0, minIdx).trim();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    // --- INNER CLASSES FOR ADAPTER & DATA ---

    class ReportItem {
        Product product;
        int qtySold;
        ReportItem(Product product, int qtySold) {
            this.product = product;
            this.qtySold = qtySold;
        }
    }

    class FmiSmiAdapter extends RecyclerView.Adapter<FmiSmiAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_fmi_smi, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ReportItem item = currentDisplayList.get(position);
            holder.tvProductName.setText(item.product.getProductName());
            holder.tvCategory.setText(item.product.getCategoryName() != null ? item.product.getCategoryName() : "Uncategorized");
            holder.tvQtySold.setText(item.qtySold + " Sold");
            holder.tvCurrentStock.setText("Stock Left: " + item.product.getQuantity());

            // Highlight SMI in red, FMI in green
            if (item.qtySold == 0) {
                holder.tvQtySold.setTextColor(android.graphics.Color.parseColor("#D32F2F")); // Red
            } else {
                holder.tvQtySold.setTextColor(android.graphics.Color.parseColor("#388E3C")); // Green
            }
        }

        @Override
        public int getItemCount() {
            return currentDisplayList.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvProductName, tvCategory, tvQtySold, tvCurrentStock;
            ViewHolder(View itemView) {
                super(itemView);
                tvProductName = itemView.findViewById(R.id.tvProductName);
                tvCategory = itemView.findViewById(R.id.tvCategory);
                tvQtySold = itemView.findViewById(R.id.tvQtySold);
                tvCurrentStock = itemView.findViewById(R.id.tvCurrentStock);
            }
        }
    }
}