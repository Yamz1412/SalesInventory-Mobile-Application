package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class HistoryAdapter extends BaseAdapter {
    ArrayList<Product> products;
    Context context;

    public HistoryAdapter(ArrayList<Product> products, Context context) {
        this.products = products;
        this.context = context;
    }

    @Override
    public int getCount() {
        return products.size();
    }

    @Override
    public Product getItem(int position) {
        return products.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        if (convertView == null) {
            LayoutInflater inflater = LayoutInflater.from(context);
            convertView = inflater.inflate(R.layout.producthistory, null);

            TextView name = convertView.findViewById(R.id.NameTVH);
            TextView code = convertView.findViewById(R.id.CodeTVH);
            TextView amount = convertView.findViewById(R.id.AmountTVH);
            TextView sellprice = convertView.findViewById(R.id.SellPriceTVH);
            TextView Pdate = convertView.findViewById(R.id.PruchaseDateTV);

            name.setText(getItem(position).getName());
            code.setText(getItem(position).getCode());
            amount.setText(getItem(position).getAmount());
            sellprice.setText(getItem(position).getSellPrice());
            Pdate.setText(getItem(position).getDate());




        }
        return convertView;
    }
}
