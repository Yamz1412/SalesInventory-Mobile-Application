package com.app.SalesInventory;

import android.app.Application;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DashboardViewModel extends AndroidViewModel {
    private final DashboardRepository repository;
    private final MutableLiveData<DashboardMetrics> dashboardMetrics;
    private final MutableLiveData<List<RecentActivity>> recentActivities;
    private final MutableLiveData<Boolean> isLoading;
    private final MutableLiveData<String> errorMessage;
    private final ExecutorService executorService;

    private final SalesRepository salesRepository;
    private final ProductRepository productRepository;

    public DashboardViewModel(@NonNull Application application) {
        super(application);
        repository = new DashboardRepository();
        dashboardMetrics = new MutableLiveData<>();
        recentActivities = new MutableLiveData<>();
        isLoading = new MutableLiveData<>(false);
        errorMessage = new MutableLiveData<>();
        executorService = Executors.newSingleThreadExecutor();
        salesRepository = SalesRepository.getInstance();
        productRepository = ProductRepository.getInstance(SalesInventoryApplication.getInstance());
    }

    public LiveData<DashboardMetrics> getDashboardMetrics() {
        return dashboardMetrics;
    }

    public LiveData<List<RecentActivity>> getRecentActivities() {
        return recentActivities;
    }

    public LiveData<Boolean> isLoading() {
        return isLoading;
    }

    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void clearErrorMessage() {
        errorMessage.setValue("");
    }

    /**
     * Load dashboard data initially
     */
    public void loadDashboardData() {
        isLoading.setValue(true);
        try {
            repository.getDashboardMetrics(new DashboardRepository.OnMetricsLoadedListener() {
                @Override
                public void onMetricsLoaded(DashboardMetrics metrics) {
                    dashboardMetrics.postValue(metrics);
                    isLoading.postValue(false);
                }
            });
        } catch (Exception e) {
            errorMessage.postValue("Error loading dashboard: " + e.getMessage());
            isLoading.postValue(false);
        }
    }

    /**
     * Refresh dashboard data after a sale completes
     * This forces a reload of all metrics
     */
    public void refreshDashboardData() {
        try {
            repository.refreshMetrics();

            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                loadDashboardData();
                loadRecentActivities();
            }, 500);
        } catch (Exception e) {
            errorMessage.postValue("Error refreshing dashboard: " + e.getMessage());
        }
    }

    public void loadRecentActivities() {
        try {
            repository.getRecentActivities(activities -> recentActivities.postValue(activities));
        } catch (Exception e) {
            errorMessage.postValue("Error loading activities: " + e.getMessage());
        }
    }

    public void loadChartData(LineChart salesTrendChart, BarChart topProductsChart, PieChart inventoryStatusChart) {
        executorService.execute(() -> {
            try {
                LiveData<List<Sales>> salesLive = salesRepository.getAllSales();
                LiveData<List<Product>> productsLive = productRepository.getAllProducts();

                List<Sales> allSales = salesLive.getValue();
                List<Product> products = productsLive.getValue();

                List<Entry> salesTrendEntries = repository.getSalesTrendData(allSales);
                TopProductsResult topProductsResult = repository.getTopProductsData(allSales, products);
                List<BarEntry> topProductEntries = topProductsResult.getEntries();
                List<String> topProductNames = topProductsResult.getProductNames();
                int[] invStatus = repository.getInventoryStatusBreakdown(products);

                if (salesTrendChart != null) {
                    setupSalesTrendChart(salesTrendChart, salesTrendEntries);
                }
                if (topProductsChart != null) {
                    setupTopProductsChart(topProductsChart, topProductEntries, topProductNames);
                }
                if (inventoryStatusChart != null) {
                    setupInventoryStatusChart(inventoryStatusChart, invStatus);
                }
            } catch (Exception e) {
                errorMessage.postValue("Error loading charts: " + e.getMessage());
            }
        });
    }

    private void setupSalesTrendChart(LineChart chart, List<Entry> entries) {
        if (entries == null) entries = new ArrayList<>();
        LineDataSet dataSet = new LineDataSet(entries, "Sales (Last 7 Days)");
        dataSet.setColor(Color.parseColor("#FF6B6B"));
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setLineWidth(2f);
        dataSet.setCircleRadius(4f);
        dataSet.setDrawCircleHole(false);
        dataSet.setMode(LineDataSet.Mode.CUBIC_BEZIER);
        dataSet.setDrawFilled(true);
        dataSet.setFillColor(Color.parseColor("#FF6B6B"));
        dataSet.setFillAlpha(30);
        LineData data = new LineData(dataSet);
        data.setValueTextSize(9f);
        List<Entry> finalEntries = entries;
        chart.post(() -> {
            chart.setData(data);
            chart.getXAxis().setDrawGridLines(false);
            chart.getAxisLeft().setDrawGridLines(false);
            chart.getAxisRight().setDrawGridLines(false);
            chart.getLegend().setEnabled(true);
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(true);
            chart.setPinchZoom(true);
            chart.getDescription().setText("Daily net sales");
            if (finalEntries.isEmpty()) {
                chart.getDescription().setText("No sales data");
            }
            chart.invalidate();
        });
    }

    private void setupTopProductsChart(BarChart chart, List<BarEntry> entries, List<String> labels) {
        if (entries == null) entries = new ArrayList<>();
        if (labels == null) labels = new ArrayList<>();

        BarDataSet dataSet = new BarDataSet(entries, "Top Products (Qty Sold)");
        dataSet.setColor(Color.parseColor("#4ECDC4"));
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueTextSize(9f);
        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);

        List<BarEntry> finalEntries = entries;
        List<String> finalLabels = new ArrayList<>(labels);

        chart.post(() -> {
            chart.setData(data);
            chart.getAxisLeft().setDrawGridLines(false);
            chart.getAxisRight().setDrawGridLines(false);
            chart.getLegend().setEnabled(true);
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(true);

            com.github.mikephil.charting.components.XAxis xAxis = chart.getXAxis();
            xAxis.setGranularity(1f);
            xAxis.setPosition(com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM);
            xAxis.setDrawGridLines(false);

            xAxis.setValueFormatter(new com.github.mikephil.charting.formatter.ValueFormatter() {
                @Override
                public String getAxisLabel(float value, com.github.mikephil.charting.components.AxisBase axis) {
                    int index = (int) value;
                    if (index >= 0 && index < finalLabels.size()) {
                        return finalLabels.get(index);
                    }
                    return "";
                }
            });

            chart.getDescription().setText(finalEntries.isEmpty() ? "No sales data" : "Top selling products");
            chart.invalidate();
        });
    }

    private void setupInventoryStatusChart(PieChart chart, int[] statusCounts) {
        if (statusCounts == null || statusCounts.length < 4) {
            statusCounts = new int[]{0, 0, 0, 0};
        }
        int inStock = statusCounts[0];
        int low = statusCounts[1];
        int critical = statusCounts[2];
        int out = statusCounts[3];

        List<PieEntry> entries = new ArrayList<>();
        if (inStock > 0) entries.add(new PieEntry(inStock, "In Stock"));
        if (low > 0) entries.add(new PieEntry(low, "Low"));
        if (critical > 0) entries.add(new PieEntry(critical, "Critical"));
        if (out > 0) entries.add(new PieEntry(out, "Out of Stock"));
        if (entries.isEmpty()) {
            entries.add(new PieEntry(1f, "No Data"));
        }

        PieDataSet dataSet = new PieDataSet(entries, "Inventory Status");
        int[] colors = new int[]{
                Color.parseColor("#95E1D3"),
                Color.parseColor("#FFD93D"),
                Color.parseColor("#FF6B6B"),
                Color.parseColor("#A8A8A8")
        };
        List<Integer> colorList = new ArrayList<>();
        for (int c : colors) colorList.add(c);
        dataSet.setColors(colorList);
        dataSet.setValueTextColor(Color.WHITE);
        dataSet.setValueTextSize(10f);
        PieData data = new PieData(dataSet);
        chart.post(() -> {
            chart.setData(data);
            chart.getLegend().setEnabled(true);
            chart.setTouchEnabled(true);
            chart.setDrawEntryLabels(true);
            chart.getDescription().setText("Inventory health overview");
            chart.invalidate();
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}