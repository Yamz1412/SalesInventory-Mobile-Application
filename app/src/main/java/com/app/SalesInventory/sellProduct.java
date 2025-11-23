package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

public class sellProduct extends AppCompatActivity {

    TextView nameTV, codeTV, priceTV, currentStockTV, totalPriceTV;
    EditText qtyInput, discountInput;
    Button btnCalculate, btnConfirm;

    DatabaseReference productRef, salesRef;
    String productKey, productName, productCode, currentStockStr, priceStr;
    double unitPrice;
    int currentStock;

    double finalTotal = 0.0;
    int qtyToSell = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sell_product);

        productRef = FirebaseDatabase.getInstance().getReference("Product");
        salesRef = FirebaseDatabase.getInstance().getReference("Sales");

        nameTV = findViewById(R.id.name1);
        codeTV = findViewById(R.id.code1);
        priceTV = findViewById(R.id.SellPrice1);
        currentStockTV = findViewById(R.id.Amount12);

        qtyInput = findViewById(R.id.QtyToSell);
        discountInput = findViewById(R.id.DiscountInput);
        totalPriceTV = findViewById(R.id.TotalPriceTV);

        btnCalculate = findViewById(R.id.BtnCalculate);
        btnConfirm = findViewById(R.id.BtnConfirmSale);

        productName = getIntent().getStringExtra("name1");
        productCode = getIntent().getStringExtra("code1");
        currentStockStr = getIntent().getStringExtra("amount1");
        priceStr = getIntent().getStringExtra("price1");

        nameTV.setText(productName);
        codeTV.setText(productCode);
        priceTV.setText(priceStr);
        currentStockTV.setText("Current Stock: " + currentStockStr);

        // Parse numbers
        try {
            currentStock = Integer.parseInt(currentStockStr);
            unitPrice = Double.parseDouble(priceStr);
        } catch (Exception e) {
            currentStock = 0;
            unitPrice = 0.0;
        }

        btnCalculate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                calculateTotal();
            }
        });

        btnConfirm.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (finalTotal > 0) {
                    processSale();
                } else {
                    Toast.makeText(sellProduct.this, "Please Calculate First", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void calculateTotal() {
        String q = qtyInput.getText().toString();
        String d = discountInput.getText().toString();

        if (TextUtils.isEmpty(q)) { qtyInput.setError("Required"); return; }
        if (TextUtils.isEmpty(d)) d = "0"; // Default discount 0

        qtyToSell = Integer.parseInt(q);
        double discountPercent = Double.parseDouble(d);

        if (qtyToSell > currentStock) {
            Toast.makeText(this, "Not enough stock!", Toast.LENGTH_SHORT).show();
            return;
        }

        double subtotal = qtyToSell * unitPrice;
        double discountAmount = subtotal * (discountPercent / 100);
        finalTotal = subtotal - discountAmount;

        totalPriceTV.setText(String.format("%.2f", finalTotal));
    }

    private void processSale() {
        int newStock = currentStock - qtyToSell;

        productRef.orderByChild("code").equalTo(productCode).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                for (DataSnapshot ds : snapshot.getChildren()) {
                    ds.getRef().child("amount").setValue(String.valueOf(newStock));

                    recordSaleTransaction();

                    Toast.makeText(sellProduct.this, "Sale Successful!", Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void recordSaleTransaction() {
        String saleId = salesRef.push().getKey();
        String date = DateFormat.getDateInstance().format(Calendar.getInstance().getTime());

        Map<String, Object> sale = new HashMap<>();
        sale.put("productName", productName);
        sale.put("quantity", qtyToSell);
        sale.put("totalPrice", finalTotal);
        sale.put("date", date);
        sale.put("timestamp", System.currentTimeMillis());

        salesRef.child(saleId).setValue(sale);
    }
}