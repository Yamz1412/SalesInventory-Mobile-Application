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

    public DashboardViewModel(@NonNull Application application) {
        super(application);
        repository = new DashboardRepository();
        dashboardMetrics = new MutableLiveData<>();
        recentActivities = new MutableLiveData<>();
        isLoading = new MutableLiveData<>(false);
        errorMessage = new MutableLiveData<>();
        executorService = Executors.newSingleThreadExecutor();
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

    public void loadDashboardData() {
        isLoading.setValue(true);
        executorService.execute(() -> {
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
        });
    }

    public void loadRecentActivities() {
        executorService.execute(() -> {
            try {
                repository.getRecentActivities(10, new DashboardRepository.OnActivitiesLoadedListener() {
                    @Override
                    public void onActivitiesLoaded(List<RecentActivity> activities) {
                        recentActivities.postValue(activities);
                    }
                });
            } catch (Exception e) {
                errorMessage.postValue("Error loading activities: " + e.getMessage());
            }
        });
    }

    public void loadChartData(LineChart salesTrendChart, BarChart topProductsChart, PieChart inventoryStatusChart) {
        executorService.execute(() -> {
            try {
                if (salesTrendChart != null) {
                    setupSalesTrendChart(salesTrendChart);
                }
                if (topProductsChart != null) {
                    setupTopProductsChart(topProductsChart);
                }
                if (inventoryStatusChart != null) {
                    setupInventoryStatusChart(inventoryStatusChart);
                }
            } catch (Exception e) {
                errorMessage.postValue("Error loading charts: " + e.getMessage());
            }
        });
    }

    private void setupSalesTrendChart(LineChart chart) {
        List<Entry> entries = repository.getSalesTrendData();
        LineDataSet dataSet = new LineDataSet(entries, "Daily Sales");
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
            chart.invalidate();
        });
    }

    private void setupTopProductsChart(BarChart chart) {
        List<BarEntry> entries = repository.getTopProductsData();
        BarDataSet dataSet = new BarDataSet(entries, "Product Sales");
        dataSet.setColor(Color.parseColor("#4ECDC4"));
        dataSet.setValueTextColor(Color.BLACK);
        dataSet.setValueTextSize(9f);
        BarData data = new BarData(dataSet);
        data.setBarWidth(0.6f);
        chart.post(() -> {
            chart.setData(data);
            chart.getXAxis().setDrawGridLines(false);
            chart.getAxisLeft().setDrawGridLines(false);
            chart.getAxisRight().setDrawGridLines(false);
            chart.getLegend().setEnabled(true);
            chart.setTouchEnabled(true);
            chart.setDragEnabled(true);
            chart.setScaleEnabled(true);
            chart.invalidate();
        });
    }

    private void setupInventoryStatusChart(PieChart chart) {
        List<PieEntry> entries = new ArrayList<>();
        entries.add(new PieEntry(45f, "In Stock"));
        entries.add(new PieEntry(30f, "Low Stock"));
        entries.add(new PieEntry(15f, "Critical"));
        entries.add(new PieEntry(10f, "Out of Stock"));
        PieDataSet dataSet = new PieDataSet(entries, "Inventory Status");
        int[] colors = new int[] {
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
            chart.invalidate();
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        executorService.shutdown();
    }
}