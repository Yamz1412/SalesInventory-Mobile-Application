package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SalesJournalAdapter extends BaseAdapter {
    private final Context ctx;
    private final List<SalesJournalEntry> items;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    public SalesJournalAdapter(Context ctx, List<SalesJournalEntry> items) {
        this.ctx = ctx;
        this.items = items;
    }

    @Override
    public int getCount() {
        return items == null ? 0 : items.size();
    }

    @Override
    public Object getItem(int position) {
        return items == null ? null : items.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            v = LayoutInflater.from(ctx).inflate(R.layout.item_report_row, parent, false);
        }
        TextView rowName = v.findViewById(R.id.RowName);
        TextView rowDate = v.findViewById(R.id.RowDate);
        TextView rowQty = v.findViewById(R.id.RowQty);
        TextView rowTotal = v.findViewById(R.id.RowTotal);
        SalesJournalEntry e = items.get(position);
        rowName.setText(e.getProductName().isEmpty() ? "(no name)" : e.getProductName());
        long ts = e.getLastSaleTimestamp();
        String dateStr = ts > 0 ? dateFormat.format(new Date(ts)) : "";
        rowDate.setText(dateStr);
        rowQty.setText("x " + e.getTotalQuantity());
        rowTotal.setText(String.format(Locale.getDefault(), "₱%.2f", e.getTotalAmount()));
        return v;
    }
}