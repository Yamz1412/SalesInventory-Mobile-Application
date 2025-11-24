package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PurchaseOrderDetailActivity extends AppCompatActivity {

    private TextView tvPONumber, tvSupplier, tvStatus, tvDate, tvTotal;
    private Button btnMarkReceived, btnCancelOrder;
    private DatabaseReference poRef;
    private String poId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_order_detail);

        // Initialize Views
        tvPONumber = findViewById(R.id.tvPONumber);
        tvSupplier = findViewById(R.id.tvSupplier);
        tvStatus = findViewById(R.id.tvStatus);
        tvDate = findViewById(R.id.tvDate);
        tvTotal = findViewById(R.id.tvTotal);
        btnMarkReceived = findViewById(R.id.btnMarkReceived);
        btnCancelOrder = findViewById(R.id.btnCancelOrder);

        // Get PO ID passed from the list
        poId = getIntent().getStringExtra("poId");

        if (poId != null) {
            poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders").child(poId);
            loadDetails();
            setupButtons();
        } else {
            Toast.makeText(this, "Error: No Order ID found", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void loadDetails() {
        poRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                PurchaseOrder po = snapshot.getValue(PurchaseOrder.class);
                if (po != null) {
                    tvPONumber.setText(po.getPoNumber());
                    tvSupplier.setText(po.getSupplierName());
                    tvStatus.setText(po.getStatus());

                    // Format Date
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                    tvDate.setText(sdf.format(new Date(po.getOrderDate())));

                    // Format Currency
                    NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
                    tvTotal.setText(format.format(po.getTotalAmount()));

                    // Update Button Visibility based on status
                    updateButtonState(po.getStatus());
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(PurchaseOrderDetailActivity.this, "Failed to load details", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateButtonState(String status) {
        if ("Received".equals(status) || "Cancelled".equals(status)) {
            btnMarkReceived.setVisibility(View.GONE);
            btnCancelOrder.setVisibility(View.GONE);
        } else {
            btnMarkReceived.setVisibility(View.VISIBLE);
            btnCancelOrder.setVisibility(View.VISIBLE);
        }
    }

    private void setupButtons() {
        btnMarkReceived.setOnClickListener(v -> updateStatus("Received"));
        btnCancelOrder.setOnClickListener(v -> updateStatus("Cancelled"));
    }

    private void updateStatus(String newStatus) {
        poRef.child("status").setValue(newStatus).addOnSuccessListener(aVoid -> {
            Toast.makeText(this, "Order marked as " + newStatus, Toast.LENGTH_SHORT).show();
        }).addOnFailureListener(e -> {
            Toast.makeText(this, "Error updating status", Toast.LENGTH_SHORT).show();
        });
    }
}