package com.app.SalesInventory;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget. ListView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import java.util.ArrayList;
import java.util.List;

public class SalesActivity extends AppCompatActivity {
    private ProductRepository productRepository;
    private SalesRepository salesRepository;
    private EditText etProductId;
    private EditText etQuantity;
    private EditText etPrice;
    private Button btnAddSale;
    private ListView listView;
    private SalesListAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sales);
        initViews();
        initRepositories();
        setupAdapter();
        loadSales();
        setupAddSaleButton();
    }

    private void initViews() {
        listView = findViewById(R.id. salesListView);
        etProductId = findViewById(R.id.etSaleProductId);
        etQuantity = findViewById(R.id.etSaleQuantity);
        etPrice = findViewById(R.id. etSalePrice);
        btnAddSale = findViewById(R. id.btnAddSale);
    }

    private void initRepositories() {
        salesRepository = SalesRepository.getInstance();
        productRepository = ProductRepository. getInstance(getApplication());
    }

    private void setupAdapter() {
        adapter = new SalesListAdapter(this, new ArrayList<>());
        listView.setAdapter(adapter);
    }

    private void loadSales() {
        salesRepository.getAllSales(). observe(this, new Observer<List<Sales>>() {
            @Override
            public void onChanged(List<Sales> sales) {
                adapter.update(sales);
            }
        });
    }

    private void setupAddSaleButton() {
        btnAddSale.setOnClickListener(v -> addSale());
    }

    private void addSale() {
        String productId = etProductId.getText() != null ? etProductId.getText().toString(). trim() : "";
        int qty = 0;
        double price = 0;
        try {
            qty = Integer.parseInt(etQuantity.getText() != null ? etQuantity.getText().toString() : "0");
        } catch (Exception ignored) {
        }
        try {
            price = Double.parseDouble(etPrice.getText() != null ? etPrice.getText().toString() : "0");
        } catch (Exception ignored) {
        }
        if (productId.isEmpty() || qty <= 0) {
            Toast.makeText(this, "Enter product and quantity", Toast.LENGTH_SHORT). show();
            return;
        }
        Sales s = new Sales();
        s.setProductId(productId);
        s.setQuantity(qty);
        s.setPrice(price);
        s.setTotalPrice(qty * price);
        s.setDate(System.currentTimeMillis());
        s.setTimestamp(System.currentTimeMillis());
        productRepository.getProductById(productId, new ProductRepository.OnProductFetchedListener() {
            @Override
            public void onProductFetched(Product product) {
                s.setProductName(product.getProductName());
                salesRepository.addSale(s, new SalesRepository.OnSaleAddedListener() {
                    @Override
                    public void onSaleAdded(String saleId) {
                        int newQty = product.getQuantity() - s. getQuantity();
                        productRepository.updateProductQuantity(product.getProductId(), Math.max(0, newQty), new ProductRepository.OnProductUpdatedListener() {
                            @Override
                            public void onProductUpdated() {
                                runOnUiThread(() -> {
                                    Toast.makeText(SalesActivity. this, "Sale recorded", Toast.LENGTH_SHORT).show();
                                    etProductId.setText("");
                                    etQuantity.setText("");
                                    etPrice.setText("");
                                });
                            }

                            @Override
                            public void onError(String error) {
                                runOnUiThread(() -> Toast.makeText(SalesActivity. this, "Error updating product", Toast.LENGTH_SHORT).show());
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> Toast.makeText(SalesActivity.this, "Error adding sale: " + error, Toast.LENGTH_SHORT).show());
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast. makeText(SalesActivity.this, "Product not found", Toast.LENGTH_SHORT).show());
            }
        });
    }
}