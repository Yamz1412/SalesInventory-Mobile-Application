package com.app.SalesInventory;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SalesRealtimeSyncManager {
    private static final String TAG = "SalesRealtimeSyncManager";
    private static SalesRealtimeSyncManager instance;
    private final MutableLiveData<List<SalesJournalEntry>> journalLive = new MutableLiveData<>(new ArrayList<>());
    private final SalesRepository salesRepo;
    private final Observer<List<Sales>> salesObserver = new Observer<List<Sales>>() {
        @Override
        public void onChanged(List<Sales> sales) {
            aggregateSales(sales);
        }
    };

    private SalesRealtimeSyncManager(Application app) {
        this.salesRepo = SalesInventoryApplication.getSalesRepository();
        try {
            LiveData<List<Sales>> salesLive = salesRepo.getAllSales();
            if (salesLive != null) salesLive.observeForever(salesObserver);
        } catch (Exception e) {
            Log.w(TAG, "Failed to attach sales observer", e);
        }
    }

    public static synchronized SalesRealtimeSyncManager getInstance(Application app) {
        if (instance == null) {
            instance = new SalesRealtimeSyncManager(app);
        }
        return instance;
    }

    public LiveData<List<SalesJournalEntry>> getJournalLiveData() {
        return journalLive;
    }

    private void aggregateSales(List<Sales> salesList) {
        Map<String, SalesJournalEntry> map = new HashMap<>();
        if (salesList != null) {
            for (Sales s : salesList) {
                if (s == null) continue;
                String pid = s.getProductId() == null ? "" : s.getProductId();
                String pname = s.getProductName() == null ? "" : s.getProductName();
                SalesJournalEntry e = map.get(pid);
                if (e == null) {
                    e = new SalesJournalEntry(pid, pname);
                    map.put(pid, e);
                } else {
                    if (e.getProductName().isEmpty() && !pname.isEmpty()) e.setProductName(pname);
                }
                int qty = s.getQuantity();
                double amount = s.getTotalPrice();
                long ts = s.getDate() > 0 ? s.getDate() : s.getTimestamp();
                e.addSale(qty, amount, ts);
            }
        }
        List<SalesJournalEntry> out = new ArrayList<>(map.values());
        Collections.sort(out, (a, b) -> Long.compare(b.getLastSaleTimestamp(), a.getLastSaleTimestamp()));
        journalLive.postValue(out);
    }

    public void shutdown() {
        try {
            LiveData<List<Sales>> salesLive = salesRepo.getAllSales();
            if (salesLive != null) salesLive.removeObserver(salesObserver);
        } catch (Exception ignored) {}
    }
}