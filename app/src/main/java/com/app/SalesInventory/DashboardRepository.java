package com.app.SalesInventory;

import static com.app.SalesInventory.EditProfil.TAG;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.database.DatabaseError;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

public class DashboardRepository {
    private SalesRepository salesRepository;
    private ProductRepository productRepository;
    private AlertRepository alertRepository;
    private final MediatorLiveData<DashboardMetrics> metricsLiveData = new MediatorLiveData<>();
    private boolean metricsSourcesAdded = false;
    private DashboardRepository.OnMetricsLoadedListener metricsListener;
    private final FirebaseFirestore firestore;
    private int pendingPOCount = 0;

    public DashboardRepository() {
        salesRepository = SalesRepository.getInstance();
        productRepository = ProductRepository.getInstance(SalesInventoryApplication.getInstance());
        alertRepository = AlertRepository.getInstance(SalesInventoryApplication.getInstance());
        firestore = FirestoreManager.getInstance().getDb();
    }

    public void getDashboardMetrics(OnMetricsLoadedListener listener) {
        metricsListener = listener;
        this.pendingPOCount = 0;

        LiveData<Double> totalSalesLive = salesRepository.getTotalSalesToday();
        LiveData<List<Product>> productsLive = productRepository.getAllProducts();
        LiveData<List<Alert>> alertsLive = alertRepository.getUnreadAlerts();
        LiveData<Double> revenueLive = salesRepository.getTotalMonthlyRevenue();
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();

        if (ownerId != null && !ownerId.isEmpty()) {
            FirebaseDatabase.getInstance().getReference("PurchaseOrders")
                    .orderByChild("ownerAdminId")
                    .equalTo(ownerId)
                    .addValueEventListener(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            int count = 0;
                            for (DataSnapshot ds : snapshot.getChildren()) {
                                String status = ds.child("status").getValue(String.class);
                                if ("PENDING".equalsIgnoreCase(status)) {
                                    count++;
                                }
                            }
                            pendingPOCount = count;
                            recomputeMetrics(totalSalesLive, productsLive, alertsLive, revenueLive);
                        }
                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            Log.e("DashboardRepo", "Database error: " + error.getMessage());
                        }
                    });
        }

        if (!metricsSourcesAdded) {
            metricsSourcesAdded = true;
            metricsLiveData.addSource(totalSalesLive, v -> recomputeMetrics(totalSalesLive, productsLive, alertsLive, revenueLive));
            metricsLiveData.addSource(productsLive, v -> recomputeMetrics(totalSalesLive, productsLive, alertsLive, revenueLive));
            metricsLiveData.addSource(alertsLive, v -> recomputeMetrics(totalSalesLive, productsLive, alertsLive, revenueLive));
            metricsLiveData.addSource(revenueLive, v -> recomputeMetrics(totalSalesLive, productsLive, alertsLive, revenueLive));
            metricsLiveData.observeForever(metrics -> {
                if (metrics != null && metricsListener != null) {
                    metricsListener.onMetricsLoaded(metrics);
                }
            });

            if (ownerId != null && !ownerId.isEmpty()) {
                firestore.collection("dashboard").document(ownerId)
                        .addSnapshotListener((snapshot, e) -> {
                            if (snapshot != null && snapshot.exists()) {
                                metricsLiveData.postValue(DashboardMetrics.fromMap(snapshot.getData()));
                            }
                        });
            }
        }

        recomputeMetrics(totalSalesLive, productsLive, alertsLive, revenueLive);
    }

    private void recomputeMetrics(LiveData<Double> totalSalesLive,
                                  LiveData<List<Product>> productsLive,
                                  LiveData<List<Alert>> alertsLive,
                                  LiveData<Double> revenueLive) {
        Double totalSales = totalSalesLive.getValue();
        List<Product> products = productsLive.getValue();

        double inventoryValue = 0;
        int lowOrCriticalCount = 0;
        int nearExpiryCount = 0;
        long now = System.currentTimeMillis();

        if (products != null) {
            for (Product p : products) {
                if (p == null || !p.isActive()) continue;
                if ("Menu".equalsIgnoreCase(p.getProductType())) continue;

                inventoryValue += (p.getQuantity() * p.getCostPrice());

                if (p.isCriticalStock() || p.isLowStock()) {
                    lowOrCriticalCount++;
                }

                long expiry = p.getExpiryDate();
                if (expiry > 0) {
                    long diffMillis = expiry - now;
                    long days = diffMillis / (24L * 60L * 60L * 1000L);
                    if (diffMillis <= 0 || days <= 7) nearExpiryCount++;
                }
            }
        }

        DashboardMetrics metrics = new DashboardMetrics(
                totalSales != null ? totalSales : 0.0,
                inventoryValue,
                lowOrCriticalCount,
                pendingPOCount,
                nearExpiryCount,
                revenueLive.getValue() != null ? revenueLive.getValue() : 0.0
        );
        metricsLiveData.setValue(metrics);
        saveMetricsToFirestore(metrics);
    }

    private void saveMetricsToFirestore(DashboardMetrics metrics) {
        String owner = FirestoreManager.getInstance().getBusinessOwnerId();
        if (owner == null || owner.isEmpty()) return;
        try {
            firestore.collection("dashboard")
                    .document(owner)
                    .set(metrics.toMap());
        } catch (Exception ignored) {}
    }

    public void refreshMetrics() {
        if (salesRepository != null) {
            salesRepository.reloadAllSales();
            salesRepository.reloadTodaySales();
            salesRepository.reloadMonthlySales();
            salesRepository.reloadRecentSales();
        }
        if (productRepository != null) {
            productRepository.refreshProducts();
        }
    }

    public void getRecentActivities(OnActivitiesLoadedListener listener) {
        salesRepository.getAllSales().observeForever(sales -> {
            List<RecentActivity> activities = new ArrayList<>();
            if (sales != null) {
                Calendar cal = Calendar.getInstance();
                cal.set(Calendar.HOUR_OF_DAY, 0);
                cal.set(Calendar.MINUTE, 0);
                cal.set(Calendar.SECOND, 0);
                cal.set(Calendar.MILLISECOND, 0);
                long startOfToday = cal.getTimeInMillis();

                for (Sales sale : sales) {
                    long ts = sale.getTimestamp() > 0 ? sale.getTimestamp() : sale.getDate();
                    if (ts >= startOfToday) {
                        RecentActivity activity = new RecentActivity(
                                sale.getId(),
                                "Sale: " + sale.getProductName(),
                                "Qty: " + sale.getQuantity() + " | ₱" + String.format("%,.2f", sale.getTotalPrice()),
                                "SALE",
                                "COMPLETED",
                                ts
                        );
                        activities.add(activity);
                    }
                }
            }
            listener.onActivitiesLoaded(activities);
        });
    }

    public List<Entry> getSalesTrendData(List<Sales> allSales) {
        List<Entry> entries = new ArrayList<>();
        if (allSales == null || allSales.isEmpty()) return entries;
        int days = 7;
        long now = System.currentTimeMillis();
        long oneDayMillis = 24L * 60L * 60L * 1000L;
        Map<Integer, Double> dayIndexToAmount = new HashMap<>();
        for (Sales s : allSales) {
            long ts = s.getTimestamp() > 0 ? s.getTimestamp() : s.getDate();
            if (ts <= 0) continue;
            long diff = now - ts;
            int dayOffset = (int) (diff / oneDayMillis);
            if (dayOffset < 0 || dayOffset >= days) continue;
            int index = days - 1 - dayOffset;
            double amount = s.getTotalPrice();
            Double current = dayIndexToAmount.get(index);
            dayIndexToAmount.put(index, (current != null ? current : 0.0) + amount);
        }
        for (int i = 0; i < days; i++) {
            double value = dayIndexToAmount.get(i) != null ? dayIndexToAmount.get(i) : 0.0;
            entries.add(new Entry(i, (float) value));
        }
        return entries;
    }

    public TopProductsResult getTopProductsData(List<Sales> allSales, List<Product> allProducts) {
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        if (allSales == null || allSales.isEmpty()) return new TopProductsResult(entries, labels);
        Map<String, Integer> productQty = new HashMap<>();
        for (Sales s : allSales) {
            String productId = s.getProductId();
            if (productId == null) continue;
            int qty = s.getQuantity();
            Integer current = productQty.get(productId);
            productQty.put(productId, (current != null ? current : 0) + qty);
        }
        Map<String, String> idToName = new HashMap<>();
        if (allProducts != null) {
            for (Product p : allProducts) {
                if (p.getProductId() != null && p.getProductName() != null) {
                    idToName.put(p.getProductId(), p.getProductName());
                }
            }
        }
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(productQty.entrySet());
        Collections.sort(sorted, (a, b) -> Integer.compare(b.getValue(), a.getValue()));
        int max = Math.min(5, sorted.size());
        for (int i = 0; i < max; i++) {
            Map.Entry<String, Integer> e = sorted.get(i);
            String productId = e.getKey();
            int qty = e.getValue();
            entries.add(new BarEntry(i, qty));
            String name = idToName.get(productId);
            if (name == null || name.isEmpty()) name = productId;
            labels.add(name);
        }
        return new TopProductsResult(entries, labels);
    }

    public int[] getInventoryStatusBreakdown(List<Product> products) {
        int inStock = 0, low = 0, critical = 0, out = 0;
        if (products != null) {
            for (Product p : products) {
                int qty = p.getQuantity();
                if (qty <= 0) out++;
                else if (p.isCriticalStock()) critical++;
                else if (p.isLowStock()) low++;
                else inStock++;
            }
        }
        return new int[]{inStock, low, critical, out};
    }

    public interface OnMetricsLoadedListener {
        void onMetricsLoaded(DashboardMetrics metrics);
    }

    public interface OnActivitiesLoadedListener {
        void onActivitiesLoaded(List<RecentActivity> activities);
    }
}