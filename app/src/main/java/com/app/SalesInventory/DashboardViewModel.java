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

import java.lang.ref.WeakReference;
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
        // FIX: Wrap incoming UI charts in WeakReferences before sending to background thread
        WeakReference<LineChart> lineChartRef = new WeakReference<>(salesTrendChart);
        WeakReference<BarChart> barChartRef = new WeakReference<>(topProductsChart);
        WeakReference<PieChart> pieChartRef = new WeakReference<>(inventoryStatusChart);

        executorService.execute(() -> {
            try {
                LiveData<List<Sales>> salesLive = salesRepository.getAllSales();
                LiveData<List<Product>> productsLive = productRepository.getAllProducts();

                List<Sales> allSales = salesLive.getValue();
                List<Product> products = productsLive.getValue();

                List<Entry> salesTrendEntries = repository.getSalesTrendData(allSales);
                TopProductsResult topProductsResult = repository.getTopProductsData(allSales, products);
                List<BarEntry> topProductEntries = topProductsResult.getEntries();

                // Mismatch safely avoided! Using your exact method name.
                List<String> topProductNames = topProductsResult.getProductNames();
                int[] invStatus = repository.getInventoryStatusBreakdown(products);

                LineChart lineChart = lineChartRef.get();
                if (lineChart != null) {
                    setupSalesTrendChart(lineChart, salesTrendEntries);
                }

                BarChart barChart = barChartRef.get();
                if (barChart != null) {
                    setupTopProductsChart(barChart, topProductEntries, topProductNames);
                }

                PieChart pieChart = pieChartRef.get();
                if (pieChart != null) {
                    setupInventoryStatusChart(pieChart, invStatus);
                }
            } catch (Exception e) {
                errorMessage.postValue("Error loading charts: " + e.getMessage());
            }
        });
    }

    private void setupSalesTrendChart(LineChart chart, List<Entry> entries) {
        WeakReference<LineChart> chartRef = new WeakReference<>(chart);
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

        LineChart safeChart = chartRef.get();
        if (safeChart != null) {
            safeChart.post(() -> {
                LineChart activeChart = chartRef.get();
                if (activeChart == null) return;

                activeChart.setData(data);
                activeChart.getXAxis().setDrawGridLines(false);
                activeChart.getAxisLeft().setDrawGridLines(false);
                activeChart.getAxisRight().setDrawGridLines(false);
                activeChart.getLegend().setEnabled(true);
                activeChart.setTouchEnabled(true);
                activeChart.setDragEnabled(true);
                activeChart.setScaleEnabled(true);
                activeChart.setPinchZoom(true);
                activeChart.getDescription().setText("Daily net sales");
                if (finalEntries.isEmpty()) {
                    activeChart.getDescription().setText("No sales data");
                }
                activeChart.invalidate();
            });
        }
    }

    private void setupTopProductsChart(BarChart chart, List<BarEntry> entries, List<String> labels) {
        WeakReference<BarChart> chartRef = new WeakReference<>(chart);
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

        BarChart safeChart = chartRef.get();
        if (safeChart != null) {
            safeChart.post(() -> {
                BarChart activeChart = chartRef.get();
                if (activeChart == null) return;

                activeChart.setData(data);
                activeChart.getAxisLeft().setDrawGridLines(false);
                activeChart.getAxisRight().setDrawGridLines(false);
                activeChart.getLegend().setEnabled(true);
                activeChart.setTouchEnabled(true);
                activeChart.setDragEnabled(true);
                activeChart.setScaleEnabled(true);

                com.github.mikephil.charting.components.XAxis xAxis = activeChart.getXAxis();
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

                activeChart.getDescription().setText(finalEntries.isEmpty() ? "No sales data" : "Top selling products");
                activeChart.invalidate();
            });
        }
    }

    private void setupInventoryStatusChart(PieChart chart, int[] statusCounts) {
        WeakReference<PieChart> chartRef = new WeakReference<>(chart);
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

        PieChart safeChart = chartRef.get();
        if (safeChart != null) {
            safeChart.post(() -> {
                PieChart activeChart = chartRef.get();
                if (activeChart == null) return;

                activeChart.setData(data);
                activeChart.getLegend().setEnabled(true);
                activeChart.setTouchEnabled(true);
                activeChart.setDrawEntryLabels(true);
                activeChart.getDescription().setText("Inventory health overview");
                activeChart.invalidate();
            });
        }
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}