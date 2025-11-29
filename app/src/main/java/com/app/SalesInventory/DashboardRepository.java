package com.app.SalesInventory;

import androidx.lifecycle.MutableLiveData;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DashboardRepository {
    private SalesRepository salesRepository;
    private ProductRepository productRepository;
    private AlertRepository alertRepository;

    public DashboardRepository() {
        salesRepository = SalesRepository.getInstance();
        productRepository = ProductRepository.getInstance(SalesInventoryApplication.getInstance());
        alertRepository = AlertRepository.getInstance(SalesInventoryApplication.getInstance());
    }

    public void getDashboardMetrics(OnMetricsLoadedListener listener) {
        salesRepository.getTotalSalesToday().observeForever(totalSales -> {
            productRepository.getAllProducts().observeForever(products -> {
                alertRepository.getUnreadAlerts().observeForever(lowStockAlerts -> {
                    salesRepository.getTotalMonthlyRevenue().observeForever(revenue -> {
                        double inventoryValue = 0;
                        if (products != null) {
                            for (Product p : products) {
                                inventoryValue += (p.getQuantity() * p.getCostPrice());
                            }
                        }
                        DashboardMetrics metrics = new DashboardMetrics(
                                totalSales != null ? totalSales : 0.0,
                                inventoryValue,
                                lowStockAlerts != null ? lowStockAlerts.size() : 0,
                                0,
                                revenue != null ? revenue : 0.0
                        );
                        listener.onMetricsLoaded(metrics);
                    });
                });
            });
        });
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

    public List<Entry> getSalesTrendData() {
        List<Entry> entries = new ArrayList<>();
        Random random = new Random();
        entries.add(new Entry(0, 12000 + random.nextFloat() * 5000));
        entries.add(new Entry(1, 15000 + random.nextFloat() * 8000));
        entries.add(new Entry(2, 14000 + random.nextFloat() * 6000));
        entries.add(new Entry(3, 18000 + random.nextFloat() * 7000));
        entries.add(new Entry(4, 16000 + random.nextFloat() * 9000));
        entries.add(new Entry(5, 20000 + random.nextFloat() * 8000));
        entries.add(new Entry(6, 17000 + random.nextFloat() * 7000));
        return entries;
    }

    public List<BarEntry> getTopProductsData() {
        List<BarEntry> entries = new ArrayList<>();
        entries.add(new BarEntry(0, 350));
        entries.add(new BarEntry(1, 280));
        entries.add(new BarEntry(2, 220));
        entries.add(new BarEntry(3, 190));
        entries.add(new BarEntry(4, 150));
        return entries;
    }

    public interface OnMetricsLoadedListener {
        void onMetricsLoaded(DashboardMetrics metrics);
    }

    public interface OnActivitiesLoadedListener {
        void onActivitiesLoaded(List<RecentActivity> activities);
    }
}