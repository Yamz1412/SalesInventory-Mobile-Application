package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import java.util.List;

public class SellAdapter extends ArrayAdapter<Product> {
    private Context context;
    private List<Product> products;

    public SellAdapter(Context context, List<Product> products) {
        super(context, 0, products);
        this.context = context;
        this.products = products;
    }

    public void updateProducts(List<Product> newProducts) {
        products.clear();
        if (newProducts != null) {
            products.addAll(newProducts);
        }
        notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        Product p = getItem(position);
        if (convertView == null) {
            convertView = LayoutInflater.from(context).inflate(R.layout.productsell, parent, false);
        }
        TextView nameTV = convertView.findViewById(R.id.NameTVS11);
        TextView codeTV = convertView.findViewById(R.id.CodeTVS11);
        TextView amountTV = convertView.findViewById(R.id.AmountTVS11);
        TextView priceTV = convertView.findViewById(R.id.SellPriceTVS11);
        TextView lotTV = convertView.findViewById(R.id.LotTVS11);
        if (p != null) {
            nameTV.setText(p.getProductName());
            codeTV.setText(p.getProductId() != null ? p.getProductId() : "");
            amountTV.setText(String.valueOf(p.getQuantity()));
            priceTV.setText("P " + String.format("%.2f", p.getSellingPrice()));
            if (p.getBarcode() != null && !p.getBarcode().isEmpty()) {
                lotTV.setText(p.getBarcode());
                lotTV.setVisibility(View.VISIBLE);
            } else {
                lotTV.setVisibility(View.GONE);
            }
        }
        return convertView;
    }
}