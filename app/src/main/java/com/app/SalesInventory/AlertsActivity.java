package com.app.SalesInventory;

import android.app.Application;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class AlertsActivity extends AppCompatActivity {
    private RecyclerView rvCritical;
    private RecyclerView rvAlerts;
    private TextView tvAlertCount;
    private TextView tvEmpty;
    private AlertAdapter alertAdapter;
    private AlertAdapter criticalAdapter;
    private AlertRepository repo;
    private ProductRepository productRepository;
    private List<Alert> latestAlerts = new ArrayList<>();
    private List<Product> latestProducts = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alerts);
        Toolbar tb = findViewById(R.id.toolbar_alerts);
        setSupportActionBar(tb);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        tb.setNavigationOnClickListener(v -> finish());
        rvCritical = findViewById(R.id.rv_critical);
        rvAlerts = findViewById(R.id.rv_alerts);
        tvAlertCount = findViewById(R.id.tv_alert_count);
        tvEmpty = findViewById(R.id.tv_empty_alerts);
        alertAdapter = new AlertAdapter();
        criticalAdapter = new AlertAdapter();
        rvCritical.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvAlerts.setLayoutManager(new LinearLayoutManager(this));
        rvCritical.setAdapter(criticalAdapter);
        rvAlerts.setAdapter(alertAdapter);
        findViewById(R.id.btn_view_all).setOnClickListener(v -> {
            try {
                Class<?> cls = Class.forName("com.app.SalesInventory.AlertsActivity");
                startActivity(new Intent(AlertsActivity.this, cls));
            } catch (Exception e) {
                rvAlerts.smoothScrollToPosition(0);
            }
        });
        repo = AlertRepository.getInstance((Application) getApplication());
        productRepository = SalesInventoryApplication.getProductRepository();
        productRepository.getAllProducts().observe(this, products -> {
            if (products == null) latestProducts = new ArrayList<>();
            else latestProducts = new ArrayList<>(products);
            applyFilterAndBind();
        });
        repo.fetchUnreadAlerts(new AlertRepository.OnAlertsFetchedListener() {
            @Override
            public void onAlertsFetched(List<Alert> alerts) {
                if (alerts == null) latestAlerts = new ArrayList<>();
                else latestAlerts = new ArrayList<>(alerts);
                runOnUiThread(() -> applyFilterAndBind());
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(AlertsActivity.this, "Failed to load alerts", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void applyFilterAndBind() {
        if (latestAlerts == null || latestAlerts.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvAlertCount.setText("0");
            alertAdapter.setItems(new ArrayList<>());
            criticalAdapter.setItems(new ArrayList<>());
            return;
        }
        Set<String> menuProductIds = new HashSet<>();
        for (Product p : latestProducts) {
            if (p == null) continue;
            String type = p.getProductType() == null ? "" : p.getProductType().trim().toLowerCase(Locale.ROOT);
            if (type.contains("menu") || type.contains("for sale") || type.contains("for-sale") || type.contains("food") || type.contains("menu item")) {
                String pid = p.getProductId();
                if (pid != null && !pid.isEmpty()) menuProductIds.add(pid);
            }
        }
        List<Alert> filtered = new ArrayList<>();
        for (Alert a : latestAlerts) {
            if (a == null) continue;
            String pid = a.getProductId();
            if (pid != null && menuProductIds.contains(pid)) continue;
            filtered.add(a);
        }
        if (filtered.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvAlertCount.setText("0");
            alertAdapter.setItems(new ArrayList<>());
            criticalAdapter.setItems(new ArrayList<>());
            return;
        }
        tvEmpty.setVisibility(View.GONE);
        tvAlertCount.setText(String.valueOf(filtered.size()));
        List<Alert> critical = new ArrayList<>();
        List<Alert> others = new ArrayList<>();
        for (Alert a : filtered) {
            String type = a.getType() != null ? a.getType() : "";
            if ("CRITICAL_STOCK".equalsIgnoreCase(type) || "DAMAGED_PRODUCT".equalsIgnoreCase(type) || "MISSING_PRODUCT".equalsIgnoreCase(type)) critical.add(a);
            else others.add(a);
        }
        criticalAdapter.setItems(critical);
        alertAdapter.setItems(others);
    }

    private static class AlertViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvMsg;
        private final TextView tvTime;
        AlertViewHolder(@NonNull View itemView) {
            super(itemView);
            tvMsg = itemView.findViewById(R.id.item_alert_message);
            tvTime = itemView.findViewById(R.id.item_alert_time);
        }
        void bind(Alert a) {
            tvMsg.setText(a.getMessage() != null && !a.getMessage().isEmpty() ? a.getMessage() : a.getType());
            long ts = a.getTimestamp();
            if (ts > 0) {
                SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault());
                tvTime.setText(sdf.format(new Date(ts)));
            } else {
                tvTime.setText("");
            }
        }
    }

    private class AlertAdapter extends RecyclerView.Adapter<AlertViewHolder> {
        private final List<Alert> items = new ArrayList<>();
        void setItems(List<Alert> list) {
            items.clear();
            if (list != null) items.addAll(list);
            notifyDataSetChanged();
        }
        @NonNull
        @Override
        public AlertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_alert, parent, false);
            return new AlertViewHolder(v);
        }
        @Override
        public void onBindViewHolder(@NonNull AlertViewHolder holder, int position) {
            Alert a = items.get(position);
            holder.bind(a);
            holder.itemView.setOnClickListener(v -> openProduct(a.getProductId()));
        }
        @Override
        public int getItemCount() {
            return items.size();
        }
    }

    private void openProduct(String productId) {
        if (productId == null || productId.isEmpty()) return;
        try {
            Class<?> cls = Class.forName("com.app.SalesInventory.ProductDetailActivity");
            Intent i = new Intent(this, cls);
            i.putExtra("productId", productId);
            startActivity(i);
            return;
        } catch (Exception ignored) {}
        try {
            Class<?> cls = Class.forName("com.app.SalesInventory.Inventory");
            Intent i = new Intent(this, cls);
            i.putExtra("productId", productId);
            startActivity(i);
        } catch (Exception ignored) {}
    }
}