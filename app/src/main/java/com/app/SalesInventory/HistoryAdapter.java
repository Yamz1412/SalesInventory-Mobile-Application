package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class HistoryAdapter extends BaseAdapter {
    private ArrayList<Product> products;
    private Context context;

    public HistoryAdapter(Context context, ArrayList<Product> products) {
        this.context = context;
        this.products = products;
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
            convertView = inflater.inflate(R.layout.producthistory, parent, false);
        }

        Product product = products.get(position);

        TextView name = convertView.findViewById(R.id.NameTVH);
        TextView code = convertView.findViewById(R.id.CodeTVH);
        TextView amount = convertView.findViewById(R.id.AmountTVH);
        TextView sellprice = convertView.findViewById(R.id.SellPriceTVH);
        TextView pDate = convertView.findViewById(R.id.PruchaseDateTV);

        name.setText(product.getProductName());
        code.setText(product.getProductId());
        amount.setText(String.valueOf(product.getQuantity()));
        sellprice.setText(String.format(Locale.getDefault(), "₱ %.2f", product.getSellingPrice()));
        if (product.getDateAdded() != 0) {
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            pDate.setText(sdf.format(new Date(product.getDateAdded())));
        } else {
            pDate.setText("No Date");
        }

        return convertView;
    }
}