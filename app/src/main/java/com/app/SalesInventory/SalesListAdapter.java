package com.app.SalesInventory;

import android.content. Context;
import android.view. LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class SalesListAdapter extends ArrayAdapter<Sales> {

    public SalesListAdapter(Context ctx, List<Sales> items) {
        super(ctx, 0, items);
    }

    public void update(List<Sales> list) {
        clear();
        if (list != null) addAll(list);
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Sales s = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_2, parent, false);
        }
        TextView t1 = convertView.findViewById(android.R.id.text1);
        TextView t2 = convertView.findViewById(android.R.id.text2);
        if (s != null) {
            t1.setText(s.getProductName() + " x" + s.getQuantity());
            t2.setText("₱" + String.format(Locale.US, "%.2f", s.getTotalPrice()));
        }
        return convertView;
    }
}