package com.app.SalesInventory;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;


import java.util.List;

public class SalesActivity extends AppCompatActivity {
    private static final String TAG = "SalesActivity";
    private SalesRepository salesRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sales);

        // Initialize repository
        salesRepository = SalesInventoryApplication.getSalesRepository();

        // Set context
        SalesInventoryApplication.BaseContext.setContext(this);

        // Observe sales
        observeSales();
    }

    /**
     * Observe sales from Firestore
     */
    private void observeSales() {
        salesRepository.getAllSales().observe(this, sales -> {
            if (sales != null) {
                Log.d(TAG, "Sales updated: " + sales.size());
                // Update UI with sales
            }
        });
    }

    /**
     * Example: Add new sale
     */
    public void addNewSale(Sales sale) {
        salesRepository.addSale(sale, new SalesRepository.OnSaleAddedListener() {
            @Override
            public void onSaleAdded(String saleId) {
                Toast.makeText(SalesActivity.this, "Sale recorded: " + saleId, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Sale added successfully");
            }

            @Override
            public void onError(String error) {
                Toast.makeText(SalesActivity.this, "Error: " + error, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Error adding sale: " + error);
            }
        });
    }

    /**
     * Example: Get sales by product
     */
    public void getSalesByProduct(String productId) {
        salesRepository.getSalesByProduct(productId, new SalesRepository.OnSalesFetchedListener() {
            @Override
            public void onSalesFetched(List<Sales> sales) {
                Log.d(TAG, "Sales fetched for product: " + productId + ", Count: " + sales.size());
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error fetching sales: " + error);
            }
        });
    }

    /**
     * Example: Get sales by date range
     */
    public void getSalesByDateRange(long startDate, long endDate) {
        salesRepository.getSalesByDateRange(startDate, endDate, new SalesRepository.OnSalesFetchedListener() {
            @Override
            public void onSalesFetched(List<Sales> sales) {
                Log.d(TAG, "Sales fetched for date range, Count: " + sales.size());
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Error fetching sales: " + error);
            }
        });
    }
}