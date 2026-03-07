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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

    private boolean isReceiveModeActive = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_order_detail);

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
        productRepository = SalesInventoryApplication.getProductRepository();

        poId = getIntent().getStringExtra("poId");
        if (poId == null) {
            Toast.makeText(this, "Error: No PO ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders").child(poId);
        loadPurchaseOrder();

        btnProcessDelivery.setOnClickListener(v -> handleProcessDeliveryClick());
        btnCancelOrder.setOnClickListener(v -> updateStatus(PurchaseOrder.STATUS_CANCELLED));
    }

    private void loadPurchaseOrder() {
        poRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                currentPo = snapshot.getValue(PurchaseOrder.class);
                if (currentPo == null) return;

                tvPONumber.setText("PO: " + currentPo.getPoNumber());
                tvSupplier.setText("Supplier: " + currentPo.getSupplierName());
                tvStatus.setText("Status: " + currentPo.getStatus());

                if (currentPo.getOrderDate() != null) {
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
                    tvDate.setText("Date: " + sdf.format(currentPo.getOrderDate()));
                }

                if (currentPo.getDeliveryNote() != null && !currentPo.getDeliveryNote().isEmpty()) {
                    tilDeliveryNote.setVisibility(View.VISIBLE);
                    etDeliveryNote.setText(currentPo.getDeliveryNote());
                }

                if (itemsAdapter == null) {
                    itemsAdapter = new POItemAdapter(PurchaseOrderDetailActivity.this, currentPo.getItems(), null, null);
                    recyclerViewOrderItems.setAdapter(itemsAdapter);
                } else {
                    itemsAdapter.notifyDataSetChanged();
                }

                // Hide action buttons if the order is completely finished or cancelled
                if (PurchaseOrder.STATUS_RECEIVED.equals(currentPo.getStatus()) || PurchaseOrder.STATUS_CANCELLED.equals(currentPo.getStatus())) {
                    layoutActionButtons.setVisibility(View.GONE);
                    tilDeliveryNote.setEnabled(false);
                    etDeliveryNote.setEnabled(false);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(PurchaseOrderDetailActivity.this, "Failed to load details.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void handleProcessDeliveryClick() {
        if (!isReceiveModeActive) {
            isReceiveModeActive = true;
            itemsAdapter.setReceiveMode(true);
            tilDeliveryNote.setVisibility(View.VISIBLE);
            btnProcessDelivery.setText("Confirm Inventory Addition");
            btnCancelOrder.setVisibility(View.GONE);
        } else {
            confirmDeliverySubmission();
        }
    }

    // =========================================================================
    // NEW: Redirection Logic for Unregistered Items & Partial Deliveries
    // =========================================================================

    private void confirmDeliverySubmission() {
        Map<Integer, Integer> newReceives = itemsAdapter.getNewlyReceivedMap();
        boolean hasUpdates = false;
        boolean isFullyReceived = true;

        // Track items that do not exist in the main inventory yet
        ArrayList<POItem> unlinkedItemsReceived = new ArrayList<>();

        for (int i = 0; i < currentPo.getItems().size(); i++) {
            POItem item = currentPo.getItems().get(i);
            int newlyReceived = newReceives.containsKey(i) ? newReceives.get(i) : 0;

            if (newlyReceived > 0) {
                hasUpdates = true;
                item.setReceivedQuantity(item.getReceivedQuantity() + newlyReceived);

                final String productId = item.getProductId();
                final int finalNewlyReceived = newlyReceived; // Prevent lambda error
                final String receivedUnit = item.getUnit() != null ? item.getUnit().toLowerCase() : "pcs";

                // If it's a completely new typed item from Create PO
                if (productId != null && productId.startsWith("CUSTOM_")) {
                    POItem customItem = new POItem(productId, item.getProductName(), item.getQuantity(), item.getUnitPrice(), item.getUnit());
                    customItem.setReceivedQuantity(newlyReceived); // Pass exactly what was delivered
                    unlinkedItemsReceived.add(customItem);
                } else {
                    // Standard Restock for Existing Items WITH SMART UNIT CONVERSION
                    productRepository.getProductById(productId, new ProductRepository.OnProductFetchedListener() {
                        @Override
                        public void onProductFetched(Product product) {
                            String baseUnit = product.getUnit() != null ? product.getUnit().toLowerCase() : "pcs";
                            int qtyToAdd = finalNewlyReceived;

                            // ========================================================
                            // SMART UNIT CONVERSION ENGINE
                            // ========================================================
                            if (!baseUnit.equals(receivedUnit)) {
                                // 1. Kilograms to Grams (e.g., receive 1kg -> add 1000g)
                                if ((receivedUnit.equals("kg") || receivedUnit.equals("kilogram")) && baseUnit.equals("g")) {
                                    qtyToAdd = finalNewlyReceived * 1000;
                                }
                                // 2. Grams to Kilograms (e.g., receive 1000g -> add 1kg)
                                else if (receivedUnit.equals("g") && (baseUnit.equals("kg") || baseUnit.equals("kilogram"))) {
                                    qtyToAdd = finalNewlyReceived / 1000;
                                }
                                // 3. Liters to Milliliters (e.g., receive 1L -> add 1000ml)
                                else if ((receivedUnit.equals("l") || receivedUnit.equals("liter") || receivedUnit.equals("liters")) && baseUnit.equals("ml")) {
                                    qtyToAdd = finalNewlyReceived * 1000;
                                }
                                // 4. Milliliters to Liters (e.g., receive 1000ml -> add 1L)
                                else if (receivedUnit.equals("ml") && (baseUnit.equals("l") || baseUnit.equals("liter") || baseUnit.equals("liters"))) {
                                    qtyToAdd = finalNewlyReceived / 1000;
                                }
                            }
                            // ========================================================

                            int updatedStock = product.getQuantity() + qtyToAdd;
                            productRepository.updateProductQuantity(productId, updatedStock, null);
                        }
                        @Override public void onError(String error) {}
                    });
                }
            }

            // If the total received is still less than what we ordered, it's a Partial Delivery
            if (item.getReceivedQuantity() < item.getQuantity()) {
                isFullyReceived = false;
            }
        }

        if (!hasUpdates) {
            Toast.makeText(this, "You must enter received quantities to process.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Determine overall PO status
        String newStatus = isFullyReceived ? PurchaseOrder.STATUS_RECEIVED : PurchaseOrder.STATUS_PARTIAL;
        currentPo.setStatus(newStatus);

        String note = etDeliveryNote.getText().toString().trim();
        if (!note.isEmpty()) currentPo.setDeliveryNote(note);

        // Prevent Lambda error by making this final
        final boolean finalIsFullyReceived = isFullyReceived;

        // Save back to Firebase
        poRef.setValue(currentPo.toMap())
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "Delivery Processed!", Toast.LENGTH_LONG).show();
                    isReceiveModeActive = false;
                    itemsAdapter.setReceiveMode(false);
                    btnProcessDelivery.setText("Process Delivery");

                    // Check for unlinked items FIRST, otherwise check for Partial Delivery
                    if (!unlinkedItemsReceived.isEmpty()) {
                        promptAddUnlinkedProduct(unlinkedItemsReceived.get(0));
                    } else if (!finalIsFullyReceived) {
                        showPartialDeliveryWarning();
                    }
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Failed to save data.", Toast.LENGTH_SHORT).show());
    }
    private void promptAddUnlinkedProduct(POItem customItem) {
        new AlertDialog.Builder(this)
                .setTitle("New Item Detected")
                .setMessage("You received '" + customItem.getProductName() + "', but it is not registered in your main inventory yet.\n\nWould you like to register it now?")
                .setPositiveButton("Register Product", (dialog, which) -> {
                    Intent intent = new Intent(PurchaseOrderDetailActivity.this, AddProductActivity.class);
                    // Pass the data so AddProductActivity can pre-fill the form!
                    intent.putExtra("PREFILL_NAME", customItem.getProductName());
                    intent.putExtra("PREFILL_COST", customItem.getUnitPrice());
                    intent.putExtra("PREFILL_QTY", customItem.getReceivedQuantity());
                    intent.putExtra("PREFILL_UNIT", customItem.getUnit());
                    startActivity(intent);
                })
                .setNegativeButton("Skip for now", null)
                .setCancelable(false)
                .show();
    }

    private void showPartialDeliveryWarning() {
        new AlertDialog.Builder(this)
                .setTitle("Partial Delivery Recorded")
                .setMessage("You received fewer items than requested. The missing items have been placed on backorder.\n\nThe Purchase Order status has been changed to PARTIAL and will remain open until fully fulfilled.")
                .setPositiveButton("Understood", null)
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
}