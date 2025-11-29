package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.google.android.material.textfield.TextInputEditText;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class sellProduct extends AppCompatActivity {
    private SalesRepository salesRepository;
    private ProductRepository productRepository;
    private int qty;
    private double unitPrice;
    private double finalTotal;
    private String productId;
    private String productName;
    private int currentStock;
    private double discountPercent;
    private TextView tvProductName;
    private TextView tvProductCode;
    private TextView tvUnitPrice;
    private TextView tvCurrentStock;
    private TextInputEditText etQuantity;
    private TextInputEditText etDiscount;
    private TextView tvTotalPrice;
    private Button btnCalculate;
    private Button btnConfirmSale;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sell_product);
        initRepositories();
        getIntentData();
        initViews();
        displayProductInfo();
        setupListeners();
    }

    private void initRepositories() {
        salesRepository = SalesRepository.getInstance();
        productRepository = ProductRepository.getInstance(getApplication());
    }

    private void getIntentData() {
        Intent intent = getIntent();
        productId = intent.getStringExtra("productId");
        productName = intent.getStringExtra("productName");
        unitPrice = intent.getDoubleExtra("productPrice", 0.0);
        currentStock = intent.getIntExtra("productStock", 0);
    }

    private void initViews() {
        tvProductName = findViewById(R.id.name1);
        tvProductCode = findViewById(R.id.code1);
        tvUnitPrice = findViewById(R.id.SellPrice1);
        tvCurrentStock = findViewById(R.id.Amount12);
        etQuantity = findViewById(R.id.QtyToSell);
        etDiscount = findViewById(R.id.DiscountInput);
        tvTotalPrice = findViewById(R.id.TotalPriceTV);
        btnCalculate = findViewById(R.id.BtnCalculate);
        btnConfirmSale = findViewById(R.id.BtnConfirmSale);
    }

    private void displayProductInfo() {
        tvProductName.setText(productName);
        tvProductCode.setText(productId);
        tvUnitPrice.setText("₱" + String.format(Locale.US, "%.2f", unitPrice));
        tvCurrentStock.setText(String.valueOf(currentStock));
        tvTotalPrice.setText("₱0.00");
    }

    private void setupListeners() {
        btnCalculate.setOnClickListener(v -> calculateTotal());
        btnConfirmSale.setOnClickListener(v -> confirmSale());
    }

    private void calculateTotal() {
        try {
            qty = Integer.parseInt(etQuantity.getText().toString().trim());
            if (qty <= 0) {
                Toast.makeText(this, "Quantity must be greater than 0", Toast.LENGTH_SHORT).show();
                tvTotalPrice.setText("₱0.00");
                return;
            }
            if (qty > currentStock) {
                Toast.makeText(this, "Insufficient stock.  Available: " + currentStock, Toast.LENGTH_SHORT).show();
                tvTotalPrice.setText("₱0.00");
                return;
            }
            String discountStr = etDiscount.getText().toString().trim();
            discountPercent = discountStr.isEmpty() ? 0 : Double.parseDouble(discountStr);
            if (discountPercent < 0 || discountPercent > 100) {
                Toast.makeText(this, "Discount must be between 0 and 100", Toast.LENGTH_SHORT).show();
                discountPercent = 0;
                etDiscount.setText("0");
                return;
            }
            double subtotal = qty * unitPrice;
            double discountAmount = subtotal * (discountPercent / 100);
            finalTotal = subtotal - discountAmount;
            tvTotalPrice.setText("₱" + String.format(Locale.US, "%.2f", finalTotal));
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Please enter valid numbers", Toast.LENGTH_SHORT).show();
            tvTotalPrice.setText("₱0.00");
        }
    }

    private void confirmSale() {
        if (qty <= 0) {
            Toast.makeText(this, "Please calculate total first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (finalTotal <= 0) {
            Toast.makeText(this, "Total must be greater than 0", Toast.LENGTH_SHORT).show();
            return;
        }
        Sales sale = new Sales();
        sale.setProductId(productId);
        sale.setProductName(productName);
        sale.setQuantity(qty);
        sale.setPrice(unitPrice);
        sale.setTotalPrice(finalTotal);
        sale.setPaymentMethod("");
        sale.setDate(System.currentTimeMillis());
        sale.setTimestamp(System.currentTimeMillis());
        salesRepository.addSale(sale,new SalesRepository.OnSaleAddedListener() {
            @Override
            public void onSaleAdded(String saleId) {
                updateStock(qty);
                Toast.makeText(sellProduct.this, "Sale recorded successfully", Toast.LENGTH_SHORT).show();
                clearInputs();
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
        final int finalNewStock = newStock;
        productRepository.updateProductQuantity(productId, finalNewStock, new ProductRepository.OnProductUpdatedListener() {
            @Override
            public void onProductUpdated() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        currentStock = finalNewStock;
                        if (tvCurrentStock != null) tvCurrentStock.setText(String.valueOf(finalNewStock));
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> Toast.makeText(sellProduct.this, "Error updating stock: " + error, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void clearInputs() {
        etQuantity.setText("");
        etDiscount.setText("0");
        tvTotalPrice.setText("₱0.00");
        qty = 0;
        finalTotal = 0;
        discountPercent = 0;
    }
}