package com.app.SalesInventory;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class sellProduct extends AppCompatActivity {

    private TextView name1, code1, sellPrice1, amount12, totalPriceTV;
    private TextInputEditText qtyToSell, discountInput;
    private Button btnCalculate, btnConfirmSale;

    private SalesRepository salesRepository;
    private ProductRepository productRepository;

    private String productId, productName;
    private double unitPrice;
    private int currentStock;
    private double finalTotal = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sell_product);

        salesRepository = SalesInventoryApplication.getSalesRepository();
        productRepository = SalesInventoryApplication.getProductRepository();

        initializeViews();
        getIntentData();
        setupListeners();
    }

    private void initializeViews() {
        name1 = findViewById(R.id.name1);
        code1 = findViewById(R.id.code1);
        sellPrice1 = findViewById(R.id.SellPrice1);
        amount12 = findViewById(R.id.Amount12);
        totalPriceTV = findViewById(R.id.TotalPriceTV);
        qtyToSell = findViewById(R.id.QtyToSell);
        discountInput = findViewById(R.id.DiscountInput);
        btnCalculate = findViewById(R.id.BtnCalculate);
        btnConfirmSale = findViewById(R.id.BtnConfirmSale);
    }

    private void getIntentData() {
        if (getIntent().getExtras() != null) {
            productId = getIntent().getStringExtra("productId");
            productName = getIntent().getStringExtra("productName");
            String code = getIntent().getStringExtra("productCode");
            unitPrice = getIntent().getDoubleExtra("productPrice", 0.0);
            currentStock = getIntent().getIntExtra("productStock", 0);

            name1.setText(productName);
            code1.setText(code);
            sellPrice1.setText(String.format(Locale.getDefault(), "%.2f", unitPrice));
            amount12.setText(String.valueOf(currentStock));
        }
    }

    private void setupListeners() {
        btnCalculate.setOnClickListener(v -> calculateTotal());
        btnConfirmSale.setOnClickListener(v -> confirmSale());
    }

    private void calculateTotal() {
        if (qtyToSell.getText() == null) return;
        String qtyStr = qtyToSell.getText().toString();

        String discountStr = "";
        if (discountInput.getText() != null) {
            discountStr = discountInput.getText().toString();
        }

        if (qtyStr.isEmpty()) {
            qtyToSell.setError("Required");
            return;
        }

        try {
            int qty = Integer.parseInt(qtyStr);
            double discountPercent = discountStr.isEmpty() ? 0 : Double.parseDouble(discountStr);

            if (qty > currentStock) {
                qtyToSell.setError("Not enough stock");
                return;
            }

            double subtotal = qty * unitPrice;
            double discountAmount = subtotal * (discountPercent / 100);
            finalTotal = subtotal - discountAmount;

            totalPriceTV.setText(String.format(Locale.getDefault(), "%.2f", finalTotal));
        } catch (NumberFormatException e) {
            qtyToSell.setError("Invalid number");
        }
    }

    private void confirmSale() {
        if (qtyToSell.getText() == null) return;

        // Recalculate to ensure finalTotal is correct before saving
        calculateTotal();
        if (finalTotal <= 0) return;

        String qtyStr = qtyToSell.getText().toString();
        int qty = Integer.parseInt(qtyStr);

        Sales sale = new Sales();
        sale.setProductId(productName);

        // FIXED: Pass int directly, do not convert to String
        sale.setQuantity(qty);

        // FIXED: Pass double directly
        sale.setPrice(unitPrice);

        sale.setTotalPrice(finalTotal);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sale.setDate(sdf.format(new Date()));
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
        productRepository.updateProductQuantity(productId, newStock, new ProductRepository.OnProductUpdatedListener() {
            @Override
            public void onProductUpdated() {
                Toast.makeText(sellProduct.this, "Sale Successful", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String error) {
                Toast.makeText(sellProduct.this, "Stock update failed: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}