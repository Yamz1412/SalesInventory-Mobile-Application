package com.app.SalesInventory;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardRepository {
    private SalesRepository salesRepository;
    private ProductRepository productRepository;
    private AlertRepository alertRepository;

    private final MediatorLiveData<DashboardMetrics> metricsLiveData = new MediatorLiveData<>();
    private boolean metricsSourcesAdded = false;
    private DashboardRepository.OnMetricsLoadedListener metricsListener;

    public DashboardRepository() {
        salesRepository = SalesRepository.getInstance();
        productRepository = ProductRepository.getInstance(SalesInventoryApplication.getInstance());
        alertRepository = AlertRepository.getInstance(SalesInventoryApplication.getInstance());
    }

    public void getDashboardMetrics(OnMetricsLoadedListener listener) {
        metricsListener = listener;

        LiveData<Double> totalSalesLive = salesRepository.getTotalSalesToday();
        LiveData<List<Product>> productsLive = productRepository.getAllProducts();
        LiveData<List<Alert>> alertsLive = alertRepository.getUnreadAlerts();
        LiveData<Double> revenueLive = salesRepository.getTotalMonthlyRevenue();

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
        }

        recomputeMetrics(totalSalesLive, productsLive, alertsLive, revenueLive);
    }

    private void recomputeMetrics(LiveData<Double> totalSalesLive,
                                  LiveData<List<Product>> productsLive,
                                  LiveData<List<Alert>> alertsLive,
                                  LiveData<Double> revenueLive) {
        Double totalSales = totalSalesLive.getValue();
        List<Product> products = productsLive.getValue();
        List<Alert> alerts = alertsLive.getValue();
        Double revenue = revenueLive.getValue();

        double inventoryValue = 0;
        int lowOrCriticalCount = 0;

        if (products != null) {
            for (Product p : products) {
                if (p == null || !p.isActive()) continue;
                String type = p.getProductType() == null ? "" : p.getProductType();
                if ("Menu".equalsIgnoreCase(type)) continue;

                inventoryValue += (p.getQuantity() * p.getCostPrice());

                if (p.isCriticalStock() || p.isLowStock()) {
                    lowOrCriticalCount++;
                }
            }
        }

        int pendingAlertsCount = alerts != null ? alerts.size() : 0;

        DashboardMetrics metrics = new DashboardMetrics(
                totalSales != null ? totalSales : 0.0,
                inventoryValue,
                lowOrCriticalCount,
                pendingAlertsCount,
                revenue != null ? revenue : 0.0
        );
        metricsLiveData.setValue(metrics);
    }

    public void getRecentActivities(int limit, OnActivitiesLoadedListener listener) {
        salesRepository.getRecentSales().observeForever(sales -> {
            List<RecentActivity> activities = new ArrayList<>();
            if (sales != null) {
                int count = Math.min(limit, sales.size());
                for (int i = 0; i < count; i++) {
                    Sales sale = sales.get(i);
                    RecentActivity activity = new RecentActivity(
                            sale.getId(),
                            "Sale: " + sale.getProductName(),
                            "Qty: " + sale.getQuantity() + " | ₱" + String.format("%.2f", sale.getTotalPrice()),
                            "SALE",
                            "COMPLETED",
                            sale.getTimestamp()
                    );
                    activities.add(activity);
                }
            }
            listener.onActivitiesLoaded(activities);
        });
    }

    public List<Entry> getSalesTrendData(List<Sales> allSales) {
        List<Entry> entries = new ArrayList<>();
        if (allSales == null || allSales.isEmpty()) {
            return entries;
        }

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
        if (allSales == null || allSales.isEmpty()) {
            return new TopProductsResult(entries, labels);
        }

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
        int inStock = 0;
        int low = 0;
        int critical = 0;
        int out = 0;

        if (products != null) {
            for (Product p : products) {
                int qty = p.getQuantity();
                if (qty <= 0) {
                    out++;
                } else if (p.isCriticalStock()) {
                    critical++;
                } else if (p.isLowStock()) {
                    low++;
                } else {
                    inStock++;
                }
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