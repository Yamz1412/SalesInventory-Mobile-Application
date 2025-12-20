package com.app.SalesInventory;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DashboardRepository {
    private final SalesRepository salesRepository;
    private final ProductRepository productRepository;
    private final AlertRepository alertRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final MediatorLiveData<DashboardMetrics> metricsLiveData = new MediatorLiveData<>();
    private boolean metricsSourcesAdded = false;
    private DashboardRepository.OnMetricsLoadedListener metricsListener;
    private final FirebaseFirestore firestore;

    public DashboardRepository() {
        salesRepository = SalesRepository.getInstance();
        productRepository = ProductRepository.getInstance(SalesInventoryApplication.getInstance());
        alertRepository = AlertRepository.getInstance(SalesInventoryApplication.getInstance());
        purchaseOrderRepository = PurchaseOrderRepository.getInstance();
        firestore = FirestoreManager.getInstance().getDb();
    }

    public void getDashboardMetrics(OnMetricsLoadedListener listener) {
        metricsListener = listener;
        LiveData<Double> totalSalesLive = salesRepository.getTotalSalesToday();
        LiveData<List<Product>> productsLive = productRepository.getAllProducts();
        LiveData<List<Alert>> alertsLive = alertRepository.getUnreadAlerts();
        LiveData<Double> revenueLive = salesRepository.getTotalMonthlyRevenue();
        LiveData<Integer> pendingPOLive = purchaseOrderRepository.getPendingCount();
        LiveData<List<Sales>> allSalesLive = salesRepository.getAllSales();
        if (!metricsSourcesAdded) {
            metricsSourcesAdded = true;
            metricsLiveData.addSource(totalSalesLive, v -> recomputeMetrics(totalSalesLive, allSalesLive, productsLive, alertsLive, revenueLive, pendingPOLive));
            metricsLiveData.addSource(allSalesLive, v -> recomputeMetrics(totalSalesLive, allSalesLive, productsLive, alertsLive, revenueLive, pendingPOLive));
            metricsLiveData.addSource(productsLive, v -> recomputeMetrics(totalSalesLive, allSalesLive, productsLive, alertsLive, revenueLive, pendingPOLive));
            metricsLiveData.addSource(alertsLive, v -> recomputeMetrics(totalSalesLive, allSalesLive, productsLive, alertsLive, revenueLive, pendingPOLive));
            metricsLiveData.addSource(revenueLive, v -> recomputeMetrics(totalSalesLive, allSalesLive, productsLive, alertsLive, revenueLive, pendingPOLive));
            metricsLiveData.addSource(pendingPOLive, v -> recomputeMetrics(totalSalesLive, allSalesLive, productsLive, alertsLive, revenueLive, pendingPOLive));
            metricsLiveData.observeForever(metrics -> {
                if (metrics != null && metricsListener != null) {
                    metricsListener.onMetricsLoaded(metrics);
                }
            });
            String owner = FirestoreManager.getInstance().getBusinessOwnerId();
            if (owner != null && !owner.isEmpty()) {
                DocumentReference dr = firestore.collection("dashboard").document(owner);
                dr.addSnapshotListener(new EventListener<DocumentSnapshot>() {
                    @Override
                    public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable com.google.firebase.firestore.FirebaseFirestoreException e) {
                        if (snapshot != null && snapshot.exists()) {
                            Map<String, Object> data = snapshot.getData();
                            if (data != null) {
                                DashboardMetrics dm = DashboardMetrics.fromMap(data);
                                metricsLiveData.postValue(dm);
                            }
                        }
                    }
                });
            }
        }
        recomputeMetrics(totalSalesLive, allSalesLive, productsLive, alertsLive, revenueLive, pendingPOLive);
    }

    public LiveData<DashboardMetrics> getMetricsLiveData() {
        getDashboardMetrics(null);
        return metricsLiveData;
    }

    private void recomputeMetrics(LiveData<Double> totalSalesLive,
                                  LiveData<List<Sales>> allSalesLive,
                                  LiveData<List<Product>> productsLive,
                                  LiveData<List<Alert>> alertsLive,
                                  LiveData<Double> revenueLive,
                                  LiveData<Integer> pendingPOLive) {
        Double totalSales = totalSalesLive != null ? totalSalesLive.getValue() : null;
        List<Sales> allSales = allSalesLive != null ? allSalesLive.getValue() : null;
        List<Product> products = productsLive != null ? productsLive.getValue() : null;
        List<Alert> alerts = alertsLive != null ? alertsLive.getValue() : null;
        Double revenue = revenueLive != null ? revenueLive.getValue() : null;
        Integer pendingPO = pendingPOLive != null ? pendingPOLive.getValue() : null;
        if ((totalSales == null || totalSales <= 0.0) && allSales != null && !allSales.isEmpty()) {
            long startOfDay = getStartOfDay();
            long endOfDay = getEndOfDay();
            double computedTotal = 0.0;
            for (Sales s : allSales) {
                if (s == null) continue;
                long ts = s.getDate() > 0 ? s.getDate() : s.getTimestamp();
                if (ts >= startOfDay && ts <= endOfDay) {
                    computedTotal += s.getTotalPrice();
                }
            }
            totalSales = computedTotal;
        }
        if ((revenue == null || revenue <= 0.0) && allSales != null && !allSales.isEmpty()) {
            long startOfMonth = getStartOfMonth();
            long endOfMonth = getEndOfMonth();
            double computedRevenue = 0.0;
            for (Sales s : allSales) {
                if (s == null) continue;
                long ts = s.getDate() > 0 ? s.getDate() : s.getTimestamp();
                if (ts >= startOfMonth && ts <= endOfMonth) {
                    computedRevenue += s.getTotalPrice();
                }
            }
            revenue = computedRevenue;
        }
        double inventoryValue = 0;
        int lowOrCriticalCount = 0;
        int nearExpiryCount = 0;
        long now = System.currentTimeMillis();
        long sevenDaysMs = 7L * 24L * 60L * 60L * 1000L;
        if (products != null) {
            for (Product p : products) {
                if (p == null || !p.isActive()) continue;
                String type = p.getProductType() == null ? "" : p.getProductType();
                if ("Menu".equalsIgnoreCase(type)) continue;
                inventoryValue += (p.getQuantity() * p.getCostPrice());
                if (p.isCriticalStock() || p.isLowStock()) {
                    lowOrCriticalCount++;
                }
                long expiry = p.getExpiryDate();
                if (expiry > 0 && (expiry - now) <= sevenDaysMs) {
                    nearExpiryCount++;
                }
            }
        }
        int pendingAlertsCount = alerts != null ? alerts.size() : 0;
        DashboardMetrics metrics = new DashboardMetrics();
        metrics.setTotalSalesToday(totalSales != null ? totalSales : 0.0);
        metrics.setTotalInventoryValue(inventoryValue);
        metrics.setLowStockCount(lowOrCriticalCount);
        metrics.setPendingOrdersCount(pendingPO != null ? pendingPO : pendingAlertsCount);
        metrics.setRevenue(revenue != null ? revenue : 0.0);
        metrics.setNearExpiryCount(nearExpiryCount);
        metrics.setTopProducts(computeTopProductsForMetrics());
        metricsLiveData.setValue(metrics);
        saveMetricsToFirestore(metrics);
    }

    private List<DashboardMetrics.TopProduct> computeTopProductsForMetrics() {
        List<Sales> allSales = salesRepository.getAllSales().getValue();
        if (allSales == null) return new ArrayList<>();
        Map<String, Integer> counts = new HashMap<>();
        for (Sales s : allSales) {
            if (s == null) continue;
            String name = s.getProductName() == null ? "" : s.getProductName();
            int qty = s.getQuantity();
            Integer current = counts.get(name);
            if (current == null) current = 0;
            counts.put(name, current + qty);
        }
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(counts.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return Integer.compare(b.getValue(), a.getValue());
            }
        });
        List<DashboardMetrics.TopProduct> top = new ArrayList<>();
        int limit = Math.min(entries.size(), 5);
        for (int i = 0; i < limit; i++) {
            Map.Entry<String, Integer> e = entries.get(i);
            top.add(new DashboardMetrics.TopProduct(e.getKey(), e.getValue()));
        }
        return top;
    }

    private void saveMetricsToFirestore(DashboardMetrics metrics) {
        String owner = FirestoreManager.getInstance().getBusinessOwnerId();
        if (owner == null || owner.isEmpty()) return;
        try {
            firestore.collection("dashboard").document(owner).set(metrics.toMap());
        } catch (Exception ignored) {}
    }

    public void getRecentActivities(int limit, OnActivitiesLoadedListener listener) {
        salesRepository.getRecentSales().observeForever(sales -> {
            List<RecentActivity> activities = new ArrayList<>();
            if (sales != null && !sales.isEmpty()) {
                Collections.sort(sales, (a, b) -> {
                    long ta = a == null ? 0L : (a.getDate() > 0 ? a.getDate() : a.getTimestamp());
                    long tb = b == null ? 0L : (b.getDate() > 0 ? b.getDate() : b.getTimestamp());
                    return Long.compare(tb, ta);
                });
                long startOfDay = getStartOfDay();
                long endOfDay = getEndOfDay();
                int added = 0;
                for (Sales sale : sales) {
                    if (sale == null) continue;
                    long ts = sale.getDate() > 0 ? sale.getDate() : sale.getTimestamp();
                    if (ts <= 0) continue;
                    if (ts < startOfDay || ts > endOfDay) continue;
                    RecentActivity activity = new RecentActivity(
                            sale.getOrderId(),
                            "Sale: " + (sale.getProductName() == null ? "" : sale.getProductName()),
                            "Qty: " + sale.getQuantity() + " | ₱" + String.format(Locale.getDefault(), "%.2f", sale.getTotalPrice()),
                            "SALE",
                            sale.getDeliveryStatus() == null ? "" : sale.getDeliveryStatus(),
                            ts
                    );
                    activities.add(activity);
                    added++;
                    if (added >= limit) break;
                }
            }
            listener.onActivitiesLoaded(activities);
        });
    }

    public List<com.github.mikephil.charting.data.Entry> getSalesTrendData(List<Sales> allSales) {
        List<com.github.mikephil.charting.data.Entry> entries = new ArrayList<>();
        if (allSales == null || allSales.isEmpty()) {
            return entries;
        }
        int days = 7;
        long now = System.currentTimeMillis();
        long oneDayMillis = 24L * 60L * 60L * 1000L;
        Map<Integer, Double> dayIndexToAmount = new HashMap<>();
        for (Sales s : allSales) {
            long ts = s.getDate() > 0 ? s.getDate() : s.getTimestamp();
            if (ts <= 0) continue;
            long diff = now - ts;
            int dayOffset = (int) (diff / oneDayMillis);
            if (dayOffset < 0 || dayOffset >= days) continue;
            int index = days - 1 - dayOffset;
            double amount = s.getTotalPrice();
            Double current = dayIndexToAmount.get(index);
            if (current == null) current = 0.0;
            dayIndexToAmount.put(index, current + amount);
        }
        for (int i = 0; i < days; i++) {
            Double v = dayIndexToAmount.get(i);
            double value = v == null ? 0.0 : v;
            entries.add(new com.github.mikephil.charting.data.Entry(i, (float) value));
        }
        return entries;
    }

    public TopProductsResult getTopProductsData(List<Sales> allSales, List<Product> allProducts) {
        List<com.github.mikephil.charting.data.BarEntry> entries = new ArrayList<>();
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
            if (current == null) current = 0;
            productQty.put(productId, current + qty);
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
        Collections.sort(sorted, new Comparator<Map.Entry<String, Integer>>() {
            @Override
            public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b) {
                return Integer.compare(b.getValue(), a.getValue());
            }
        });
        int max = Math.min(5, sorted.size());
        for (int i = 0; i < max; i++) {
            Map.Entry<String, Integer> e = sorted.get(i);
            String productId = e.getKey();
            int qty = e.getValue();
            entries.add(new com.github.mikephil.charting.data.BarEntry(i, qty));
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

    public static class TopProductsResult {
        private final List<com.github.mikephil.charting.data.BarEntry> entries;
        private final List<String> productNames;

        public TopProductsResult(List<com.github.mikephil.charting.data.BarEntry> entries, List<String> productNames) {
            this.entries = entries == null ? new ArrayList<>() : entries;
            this.productNames = productNames == null ? new ArrayList<>() : productNames;
        }

        public List<com.github.mikephil.charting.data.BarEntry> getEntries() {
            return entries;
        }

        public List<String> getProductNames() {
            return productNames;
        }
    }

    private long getStartOfDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getEndOfDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }

    private long getStartOfMonth() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private long getEndOfMonth() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, calendar.getActualMaximum(Calendar.DAY_OF_MONTH));
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        calendar.set(Calendar.MILLISECOND, 999);
        return calendar.getTimeInMillis();
    }
}