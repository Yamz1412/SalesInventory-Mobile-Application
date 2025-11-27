package com.app.SalesInventory;

import android.widget.Toast;
import java.util.Locale;

public class sellProduct extends androidx.appcompat.app.AppCompatActivity {
    private SalesRepository salesRepository;
    private ProductRepository productRepository;
    private int qty;
    private double unitPrice;
    private double finalTotal;
    private String productId;
    private String productName;
    private int currentStock;

    private void confirmSale() {
        Sales sale = new Sales();
        sale.setProductId(productId);
        sale.setProductName(productName);
        sale.setQuantity(qty);
        sale.setPrice(unitPrice);
        sale.setTotalPrice(finalTotal);
        sale.setDate(System.currentTimeMillis());
        sale.setTimestamp(System.currentTimeMillis());
        salesRepository.addSale(sale, new SalesRepository.OnSaleAddedListener() {
            @Override
            public void onSaleAdded(String saleId) {
                updateStock(qty);
            }
            @Override
            public void onError(String error) {
                Toast.makeText(sellProduct.this, "Failed: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateStock(int qtySold) {
        int newStock = currentStock - qtySold;
        if (newStock < 0) newStock = 0;
        productRepository.updateProductQuantity(productId, newStock, new ProductRepository.OnProductUpdatedListener() {
            @Override
            public void onProductUpdated() {}
            @Override
            public void onError(String error) {}
        });
    }
}