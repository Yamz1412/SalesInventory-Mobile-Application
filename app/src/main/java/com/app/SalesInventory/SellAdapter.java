package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;

public class SellAdapter extends BaseAdapter {
    private Context context;
    private List<Product> productList;

    public SellAdapter(Context context, List<Product> productList) {
        this.context = context;
        this.productList = productList;
    }

    @Override
    public int getCount() {
        return productList.size();
    }

    @Override
    public Product getItem(int position) {
        return productList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.productsell, parent, false);
        }

        Product product = productList.get(position);

        TextView nameTV = convertView.findViewById(R.id.NameTVS11);
        TextView codeTV = convertView.findViewById(R.id.CodeTVS11);
        TextView stockTV = convertView.findViewById(R.id.AmountTVS11);
        TextView priceTV = convertView.findViewById(R.id.SellPriceTVS11);
        TextView lotTV = convertView.findViewById(R.id.LotTVS11);

        nameTV.setText(product.getProductName());
        codeTV.setText(product.getProductId());
        stockTV.setText(String.valueOf(product.getQuantity()));
        priceTV.setText(String.format(Locale.getDefault(), "P %.2f", product.getSellingPrice()));

        if (lotTV != null) {
            lotTV.setText(product.getProductId());
        }

        return convertView;
    }
}