package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PurchaseOrderDetailActivity extends BaseActivity {

    private TextView tvPONumber, tvSupplier, tvStatus, tvDate;
    private Button btnProcessDelivery, btnCancelOrder;
    private TextInputLayout tilDeliveryNote;
    private TextInputEditText etDeliveryNote;
    private LinearLayout layoutActionButtons;

    private DatabaseReference poRef;
    private String poId;
    private PurchaseOrder currentPo;
    private ProductRepository productRepository;
    private RecyclerView recyclerViewOrderItems;
    private POItemAdapter itemsAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_order_detail);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("PO Details");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        poId = getIntent().getStringExtra("PO_ID");
        if (poId == null) poId = getIntent().getStringExtra("poId");

        if (poId == null) {
            Toast.makeText(this, "Error: No PO ID provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders").child(poId);
        productRepository = SalesInventoryApplication.getProductRepository();

        tvPONumber = findViewById(R.id.tvPONumber);
        tvSupplier = findViewById(R.id.tvSupplier);
        tvStatus = findViewById(R.id.tvStatus);
        tvDate = findViewById(R.id.tvDate);
        btnProcessDelivery = findViewById(R.id.btnProcessDelivery);
        btnCancelOrder = findViewById(R.id.btnCancelOrder);
        tilDeliveryNote = findViewById(R.id.tilDeliveryNote);
        etDeliveryNote = findViewById(R.id.etDeliveryNote);
        layoutActionButtons = findViewById(R.id.layoutActionButtons);
        recyclerViewOrderItems = findViewById(R.id.recyclerViewOrderItems);

        recyclerViewOrderItems.setLayoutManager(new LinearLayoutManager(this));

        loadPurchaseOrder();

        btnProcessDelivery.setOnClickListener(v -> processDelivery());
        btnCancelOrder.setOnClickListener(v -> updateStatus(PurchaseOrder.STATUS_CANCELLED));
    }

    private void loadPurchaseOrder() {
        poRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentPo = snapshot.getValue(PurchaseOrder.class);
                if (currentPo != null) {
                    updateUI();
                } else {
                    Toast.makeText(PurchaseOrderDetailActivity.this, "PO Not Found", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(PurchaseOrderDetailActivity.this, "Database Error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUI() {
        tvPONumber.setText(currentPo.getPoNumber());
        tvSupplier.setText(currentPo.getSupplierName());

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        tvDate.setText("Expected: " + sdf.format(currentPo.getOrderDate()));

        String status = currentPo.getStatus();
        tvStatus.setText(status.toUpperCase());

        if (status.equalsIgnoreCase(PurchaseOrder.STATUS_PENDING) || status.equalsIgnoreCase(PurchaseOrder.STATUS_PARTIAL)) {
            layoutActionButtons.setVisibility(View.VISIBLE);
            tilDeliveryNote.setVisibility(View.VISIBLE);
            tvStatus.setTextColor(getResources().getColor(R.color.warningYellow));

            // FIX: Pass the Delete Listener so it functions in Receive Mode
            itemsAdapter = new POItemAdapter(this, currentPo.getItems(), position -> promptDeleteItem(position));
            itemsAdapter.setReceiveMode(true);
        } else {
            layoutActionButtons.setVisibility(View.GONE);
            tilDeliveryNote.setVisibility(View.GONE);

            if (status.equalsIgnoreCase(PurchaseOrder.STATUS_RECEIVED)) {
                tvStatus.setTextColor(getResources().getColor(R.color.successGreen));
            } else {
                tvStatus.setTextColor(getResources().getColor(R.color.errorRed));
            }

            // FIX: Set to ViewOnly mode so input boxes hide correctly on completed orders
            itemsAdapter = new POItemAdapter(this, currentPo.getItems(), null);
            itemsAdapter.setViewOnlyMode(true);
        }

        recyclerViewOrderItems.setAdapter(itemsAdapter);
    }

    // =====================================================================
    // NEW: Handles the deletion of a PO Item directly from Firebase
    // =====================================================================
    private void promptDeleteItem(int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Item")
                .setMessage("Are you sure you want to completely remove this item from the Purchase Order?")
                .setPositiveButton("Delete", (dialog, which) -> {

                    currentPo.getItems().remove(position);

                    // If they deleted the last item, cancel the order automatically
                    if (currentPo.getItems().isEmpty()) {
                        poRef.child("status").setValue(PurchaseOrder.STATUS_CANCELLED);
                        Toast.makeText(this, "Order cancelled because all items were removed.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    // Recalculate total amount with the remaining items
                    double newTotal = 0;
                    for (POItem item : currentPo.getItems()) {
                        newTotal += (item.getUnitPrice() * item.getQuantity());
                    }
                    currentPo.setTotalAmount(newTotal);

                    // Push changes to Firebase immediately
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("items", currentPo.getItems());
                    updates.put("totalAmount", newTotal);

                    poRef.updateChildren(updates).addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Item removed and total updated", Toast.LENGTH_SHORT).show();
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    // =====================================================================

    private void processDelivery() {
        boolean hasNewReceives = false;
        boolean isPartial = false;
        double totalCostDeduction = 0.0;

        List<POItem> itemsReceivedNow = new ArrayList<>();

        for (int i = 0; i < currentPo.getItems().size(); i++) {
            POItem item = currentPo.getItems().get(i);
            int newlyReceived = itemsAdapter.getNewlyReceivedMap().containsKey(i) ? itemsAdapter.getNewlyReceivedMap().get(i) : 0;

            if (newlyReceived > 0) {
                hasNewReceives = true;
                totalCostDeduction += (newlyReceived * item.getUnitPrice());

                // Temporarily store the newly received amount in the object to pass it later
                item.setReceivedQuantity(item.getReceivedQuantity() + newlyReceived);
                POItem tempItem = new POItem(item.getProductId(), item.getProductName(), newlyReceived, item.getUnitPrice(), item.getUnit());
                itemsReceivedNow.add(tempItem);
            }

            if (item.getReceivedQuantity() < item.getQuantity()) {
                isPartial = true;
            }
        }

        if (!hasNewReceives) {
            Toast.makeText(this, "Please enter at least one received quantity.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Run the async check for existing products
        checkProductsAndFinalize(itemsReceivedNow, isPartial, totalCostDeduction);
    }

    private void checkProductsAndFinalize(List<POItem> itemsToCheck, boolean isPartial, double finalCostToDeduct) {
        ArrayList<Bundle> registrationQueue = new ArrayList<>();
        final int[] pendingQueries = {itemsToCheck.size()};

        if (pendingQueries[0] == 0) {
            finalizeDeliveryStatus(isPartial, finalCostToDeduct, registrationQueue);
            return;
        }

        for (POItem item : itemsToCheck) {
            FirebaseDatabase.getInstance().getReference("Products")
                    .orderByChild("productName")
                    .equalTo(item.getProductName())
                    .addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot snapshot) {
                            boolean productExists = false;

                            for (DataSnapshot ds : snapshot.getChildren()) {
                                Product p = ds.getValue(Product.class);
                                if (p != null && p.getOwnerAdminId().equals(currentPo.getOwnerAdminId())) {
                                    productExists = true;
                                    // 1. PRODUCT EXISTS: Silently Add Stocks (Restock)
                                    int newTotal = p.getQuantity() + item.getQuantity(); // item.getQuantity() holds the newlyReceived amount
                                    productRepository.updateProductQuantity(p.getProductId(), newTotal, null);
                                    break;
                                }
                            }

                            // 2. PRODUCT DOES NOT EXIST: Add to Queue
                            if (!productExists) {
                                Bundle b = new Bundle();
                                b.putString("PREFILL_NAME", item.getProductName());
                                b.putDouble("PREFILL_COST", item.getUnitPrice());
                                b.putInt("PREFILL_QTY", item.getQuantity());
                                b.putString("PREFILL_UNIT", item.getUnit());
                                b.putString("PREFILL_SUPPLIER", currentPo.getSupplierName());
                                registrationQueue.add(b);
                            }

                            pendingQueries[0]--;
                            if (pendingQueries[0] == 0) {
                                finalizeDeliveryStatus(isPartial, finalCostToDeduct, registrationQueue);
                            }
                        }

                        @Override
                        public void onCancelled(@NonNull DatabaseError error) {
                            pendingQueries[0]--;
                            if (pendingQueries[0] == 0) {
                                finalizeDeliveryStatus(isPartial, finalCostToDeduct, registrationQueue);
                            }
                        }
                    });
        }
    }

    private void finalizeDeliveryStatus(boolean isPartial, double finalCostToDeduct, ArrayList<Bundle> registrationQueue) {
        String newStatus = isPartial ? PurchaseOrder.STATUS_PARTIAL : PurchaseOrder.STATUS_RECEIVED;

        poRef.child("status").setValue(newStatus).addOnSuccessListener(aVoid -> {
            poRef.child("items").setValue(currentPo.getItems());
            if (finalCostToDeduct > 0) deductFromWallet(finalCostToDeduct);

            Toast.makeText(this, "Delivery updated successfully", Toast.LENGTH_SHORT).show();

            // If there are new products to register, launch the Queue
            if (!registrationQueue.isEmpty()) {
                Intent intent = new Intent(this, AddProductActivity.class);
                intent.putParcelableArrayListExtra("REGISTRATION_QUEUE", registrationQueue);
                startActivity(intent);
            }
            finish();
        });
    }

    private void promptToRegisterNewProduct(POItem customItem, int newlyReceivedQty) {
        new AlertDialog.Builder(this)
                .setTitle("New Product Detected")
                .setMessage("The item '" + customItem.getProductName() + "' is not currently in your inventory. You must register it to track its stock.")
                .setPositiveButton("Register Now", (dialog, which) -> {
                    Intent intent = new Intent(PurchaseOrderDetailActivity.this, AddProductActivity.class);
                    intent.putExtra("PREFILL_NAME", customItem.getProductName());
                    intent.putExtra("PREFILL_COST", customItem.getUnitPrice());
                    intent.putExtra("PREFILL_QTY", newlyReceivedQty);
                    intent.putExtra("PREFILL_UNIT", customItem.getUnit());
                    startActivity(intent);
                })
                .setCancelable(false)
                .show();
    }

    private void updateStatus(String newStatus) {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Action")
                .setMessage("Are you sure you want to mark this order as " + newStatus + "?")
                .setPositiveButton("Yes", (dialog, which) -> {
                    poRef.child("status").setValue(newStatus)
                            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Order " + newStatus, Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("No", null)
                .show();
    }

    private void deductFromWallet(double totalAmount) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null) return;

        DocumentReference walletRef = FirebaseFirestore.getInstance().collection("users")
                .document(ownerId).collection("wallets").document("CASH");

        FirebaseFirestore.getInstance().runTransaction(transaction -> {
            DocumentSnapshot snap = transaction.get(walletRef);
            if (snap.exists() && snap.getDouble("balance") != null) {
                double currentBal = snap.getDouble("balance");
                transaction.update(walletRef, "balance", currentBal - totalAmount);
            }
            return null;
        }).addOnSuccessListener(aVoid -> {
            Map<String, Object> transLog = new HashMap<>();
            transLog.put("title", "PO Payment: " + tvPONumber.getText().toString());
            transLog.put("date", new java.text.SimpleDateFormat("MMM dd, yyyy - hh:mm a", java.util.Locale.getDefault()).format(new java.util.Date()));
            transLog.put("amount", totalAmount);
            transLog.put("isIncome", false);
            transLog.put("timestamp", System.currentTimeMillis());
            FirebaseFirestore.getInstance().collection("users").document(ownerId).collection("cash_transactions").add(transLog);
        });
    }
}