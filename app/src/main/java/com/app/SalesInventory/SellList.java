package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.widget.ListView;
import java.util.ArrayList;
import java.util.List;

public class SellList extends AppCompatActivity {

    private ListView sellListView;
    private SellAdapter sellAdapter;
    private List<Product> productList;
    private ProductRepository productRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sell_list);

        productRepository = SalesInventoryApplication.getProductRepository();

        sellListView = findViewById(R.id.SellListD);
        productList = new ArrayList<>();
        sellAdapter = new SellAdapter(this, productList);
        sellListView.setAdapter(sellAdapter);

        loadProducts();
        setupListClick();
    }

    private void loadProducts() {
        productRepository.getAllProducts().observe(this, products -> {
            if (products != null) {
                productList.clear();
                productList.addAll(products);
                sellAdapter.notifyDataSetChanged();
            }
        });
    }

    private void setupListClick() {
        sellListView.setOnItemClickListener((parent, view, position, id) -> {
            Product selectedProduct = productList.get(position);
            Intent intent = new Intent(SellList.this, sellProduct.class);
            intent.putExtra("productId", selectedProduct.getProductId());
            intent.putExtra("productName", selectedProduct.getProductName());
            intent.putExtra("productCode", selectedProduct.getProductId());
            intent.putExtra("productPrice", selectedProduct.getSellingPrice());
            intent.putExtra("productStock", selectedProduct.getQuantity());
            startActivity(intent);
        });
    }
}