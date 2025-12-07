package com.app.SalesInventory;

import com.github.mikephil.charting.data.BarEntry;

import java.util.List;

public class TopProductsResult {
    private final List<BarEntry> entries;
    private final List<String> productNames;

    public TopProductsResult(List<BarEntry> entries, List<String> productNames) {
        this.entries = entries;
        this.productNames = productNames;
    }

    public List<BarEntry> getEntries() {
        return entries;
    }

    public List<String> getProductNames() {
        return productNames;
    }
}