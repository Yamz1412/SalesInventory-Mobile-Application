package com.app.SalesInventory;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.MeasureSpec;
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
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.text.SimpleDateFormat;

public class ChartRenderer {

    public static List<Bitmap> renderCharts(Context context, List<Sales> salesList, List<Product> productList) {
        List<Bitmap> out = new ArrayList<>();
        Bitmap b1 = renderSalesOverTime(context, salesList);
        Bitmap b2 = renderTopProducts(context, salesList);
        Bitmap b3 = renderInventoryPie(context, productList);
        if (b1 != null) out.add(b1);
        if (b2 != null) out.add(b2);
        if (b3 != null) out.add(b3);
        return out;
    }

    private static Bitmap renderSalesOverTime(Context context, List<Sales> salesList) {
        if (salesList == null || salesList.isEmpty()) return null;
        Map<String, Double> map = new HashMap<>();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (Sales s : salesList) {
            long ts = s.getDate() > 0 ? s.getDate() : s.getTimestamp();
            String key = fmt.format(new Date(ts));
            double v = map.containsKey(key) ? map.get(key) : 0;
            v += s.getTotalPrice();
            map.put(key, v);
        }
        List<String> keys = new ArrayList<>(map.keySet());
        Collections.sort(keys);
        List<Entry> entries = new ArrayList<>();
        for (int i = 0; i < keys.size(); i++) {
            entries.add(new Entry(i, map.get(keys.get(i)).floatValue()));
        }
        LineChart chart = new LineChart(context);
        LineDataSet ds = new LineDataSet(entries, "Sales");
        ds.setColor(0xFF1976D2);
        ds.setValueTextSize(10f);
        LineData data = new LineData(ds);
        chart.setData(data);
        int w = 1000;
        int h = 600;
        chart.setLayoutParams(new ViewGroup.LayoutParams(w, h));
        chart.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        chart.layout(0, 0, w, h);
        chart.invalidate();
        return chart.getChartBitmap();
    }

    private static Bitmap renderTopProducts(Context context, List<Sales> salesList) {
        if (salesList == null || salesList.isEmpty()) return null;
        Map<String, Integer> map = new HashMap<>();
        for (Sales s : salesList) {
            String pname = s.getProductName() == null || s.getProductName().isEmpty() ? s.getProductId() : s.getProductName();
            int q = map.containsKey(pname) ? map.get(pname) : 0;
            q += s.getQuantity();
            map.put(pname, q);
        }
        List<Map.Entry<String,Integer>> list = new ArrayList<>(map.entrySet());
        list.sort((a,b) -> Integer.compare(b.getValue(), a.getValue()));
        int limit = Math.min(10, list.size());
        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < limit; i++) {
            entries.add(new BarEntry(i, list.get(i).getValue()));
        }
        BarChart chart = new BarChart(context);
        BarDataSet ds = new BarDataSet(entries, "Top Products");
        ds.setColor(0xFFFFA000);
        ds.setValueTextSize(10f);
        BarData data = new BarData(ds);
        chart.setData(data);
        int w = 1000;
        int h = 600;
        chart.setLayoutParams(new ViewGroup.LayoutParams(w, h));
        chart.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        chart.layout(0, 0, w, h);
        chart.invalidate();
        return chart.getChartBitmap();
    }

    private static Bitmap renderInventoryPie(Context context, List<Product> productList) {
        if (productList == null || productList.isEmpty()) return null;
        Map<String, Double> catValue = new HashMap<>();
        for (Product p : productList) {
            String cat = p.getCategoryName() == null || p.getCategoryName().isEmpty() ? "Uncategorized" : p.getCategoryName();
            double v = catValue.containsKey(cat) ? catValue.get(cat) : 0;
            v += p.getQuantity() * p.getSellingPrice();
            catValue.put(cat, v);
        }
        List<PieEntry> entries = new ArrayList<>();
        for (Map.Entry<String, Double> e : catValue.entrySet()) {
            entries.add(new PieEntry(e.getValue().floatValue(), e.getKey()));
        }
        PieChart chart = new PieChart(context);
        PieDataSet ds = new PieDataSet(entries, "Inventory Value by Category");
        ds.setSliceSpace(2f);
        ds.setValueTextSize(10f);
        PieData data = new PieData(ds);
        chart.setData(data);
        int w = 800;
        int h = 800;
        chart.setLayoutParams(new ViewGroup.LayoutParams(w, h));
        chart.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        chart.layout(0, 0, w, h);
        chart.invalidate();
        return chart.getChartBitmap();
    }
}