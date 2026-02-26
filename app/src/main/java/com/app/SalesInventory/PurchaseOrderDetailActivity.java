package com.app.SalesInventory;

import androidx.annotation.NonNull;

import android.os.Bundle;
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
import java.util.List;
import java.util.Locale;

public class PurchaseOrderDetailActivity extends BaseActivity {

    private TextView tvPONumber, tvSupplier, tvStatus, tvDate, tvTotal;
    private Button btnMarkReceived, btnCancelOrder;
    private DatabaseReference poRef;
    private String poId;
    private PurchaseOrder currentPo;
    private ProductRepository productRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_order_detail);
        tvPONumber = findViewById(R.id.tvPONumber);
        tvSupplier = findViewById(R.id.tvSupplier);
        tvStatus = findViewById(R.id.tvStatus);
        tvDate = findViewById(R.id.tvDate);
        tvTotal = findViewById(R.id.tvTotal);
        btnMarkReceived = findViewById(R.id.btnMarkReceived);
        btnCancelOrder = findViewById(R.id.btnCancelOrder);
        productRepository = SalesInventoryApplication.getProductRepository();
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
                    currentPo = po;
                    tvPONumber.setText(po.getPoNumber());
                    tvSupplier.setText(po.getSupplierName());
                    tvStatus.setText(po.getStatus());
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                    tvDate.setText(sdf.format(new Date(po.getOrderDate())));
                    NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
                    tvTotal.setText(format.format(po.getTotalAmount()));
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
            btnMarkReceived.setVisibility(Button.GONE);
            btnCancelOrder.setVisibility(Button.GONE);
        } else {
            btnMarkReceived.setVisibility(Button.VISIBLE);
            btnCancelOrder.setVisibility(Button.VISIBLE);
        }
    }

    private void setupButtons() {
        btnMarkReceived.setOnClickListener(v -> markAsReceived());
        btnCancelOrder.setOnClickListener(v -> updateStatus("Cancelled"));
    }

    private void markAsReceived() {
        if (currentPo == null) {
            Toast.makeText(this, "No PO loaded", Toast.LENGTH_SHORT).show();
            return;
        }
        List<POItem> items = currentPo.getItems();
        if (items != null) {
            for (POItem item : items) {
                String productId = item.getProductId();
                int qty = item.getQuantity();
                if (productId == null || qty <= 0) continue;
                productRepository.getProductById(productId, new ProductRepository.OnProductFetchedListener() {
                    @Override
                    public void onProductFetched(Product product) {
                        int newQty = product.getQuantity() + qty;
                        productRepository.updateProductQuantity(productId, newQty, new ProductRepository.OnProductUpdatedListener() {
                            @Override
                            public void onProductUpdated() {
                            }

                            @Override
                            public void onError(String error) {
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                    }
                });
            }
        }
        updateStatus("Received");
    }

    private void updateStatus(String newStatus) {
        poRef.child("status").setValue(newStatus)
                .addOnSuccessListener(aVoid -> Toast.makeText(this, "Order marked as " + newStatus, Toast.LENGTH_SHORT).show())
                .addOnFailureListener(e -> Toast.makeText(this, "Error updating status", Toast.LENGTH_SHORT).show());
    }
}