package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.graphics.Color;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PurchaseOrderDetailActivity extends BaseActivity {

    private TextView tvPONumber, tvSupplier, tvStatus, tvDate;
    private Button btnProcessDelivery, btnCancelOrder, btnForceClose, btnDispatchPO;
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
        btnForceClose = findViewById(R.id.btnForceClose);
        btnDispatchPO = findViewById(R.id.btnDispatchPO);
        tilDeliveryNote = findViewById(R.id.tilDeliveryNote);
        etDeliveryNote = findViewById(R.id.etDeliveryNote);
        layoutActionButtons = findViewById(R.id.layoutActionButtons);
        recyclerViewOrderItems = findViewById(R.id.recyclerViewOrderItems);

        recyclerViewOrderItems.setLayoutManager(new LinearLayoutManager(this));

        loadPurchaseOrder();

        btnProcessDelivery.setOnClickListener(v -> processDelivery());
        btnCancelOrder.setOnClickListener(v -> updateStatus(PurchaseOrder.STATUS_CANCELLED));

        if (btnForceClose != null) {
            btnForceClose.setOnClickListener(v -> promptForceClose());
        }

        if (btnDispatchPO != null) {
            btnDispatchPO.setOnClickListener(v -> dispatchOrder());
        }
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

        if (status.equalsIgnoreCase(PurchaseOrder.STATUS_PENDING) || status.equalsIgnoreCase("SENT") || status.equalsIgnoreCase(PurchaseOrder.STATUS_PARTIAL)) {
            layoutActionButtons.setVisibility(View.VISIBLE);
            tilDeliveryNote.setVisibility(View.VISIBLE);
            tvStatus.setTextColor(getResources().getColor(R.color.warningYellow));

            if (btnDispatchPO != null) {
                if (status.equalsIgnoreCase(PurchaseOrder.STATUS_PENDING)) {
                    btnDispatchPO.setVisibility(View.VISIBLE);
                } else {
                    btnDispatchPO.setVisibility(View.GONE);
                }
            }

            if (btnForceClose != null) {
                if (status.equalsIgnoreCase(PurchaseOrder.STATUS_PARTIAL)) {
                    btnForceClose.setVisibility(View.VISIBLE);
                } else {
                    btnForceClose.setVisibility(View.GONE);
                }
            }

            itemsAdapter = new POItemAdapter(this, currentPo.getItems(), position -> promptDeleteItem(position), null);
            itemsAdapter.setReceiveMode(true);
        } else {
            layoutActionButtons.setVisibility(View.GONE);
            tilDeliveryNote.setVisibility(View.GONE);
            if (btnDispatchPO != null) btnDispatchPO.setVisibility(View.GONE);

            if (status.equalsIgnoreCase(PurchaseOrder.STATUS_RECEIVED) || status.equalsIgnoreCase("COMPLETED")) {
                tvStatus.setTextColor(getResources().getColor(R.color.successGreen));
            } else {
                tvStatus.setTextColor(getResources().getColor(R.color.errorRed));
            }

            itemsAdapter = new POItemAdapter(this, currentPo.getItems(), null, null);
            itemsAdapter.setReceiveMode(false);
        }

        recyclerViewOrderItems.setAdapter(itemsAdapter);
    }

    private void dispatchOrder() {
        new AlertDialog.Builder(this)
                .setTitle("Dispatch Order")
                .setMessage("Are you sure you want to officially send this order to the supplier? \n\n(This will lock the items so you can begin receiving them).")
                .setPositiveButton("Dispatch", (dialog, which) -> {
                    poRef.child("status").setValue("SENT")
                            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Order Dispatched to Supplier!", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptForceClose() {
        new AlertDialog.Builder(this)
                .setTitle("Force Close Order")
                .setMessage("Are you sure you want to mark this partial order as Completed? You will not be able to receive any more items for this PO.")
                .setPositiveButton("Close Order", (dialog, which) -> {
                    poRef.child("status").setValue("COMPLETED")
                            .addOnSuccessListener(aVoid -> Toast.makeText(this, "Order marked as Completed", Toast.LENGTH_SHORT).show());
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptDeleteItem(int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Item")
                .setMessage("Are you sure you want to completely remove this item from the Purchase Order?")
                .setPositiveButton("Delete", (dialog, which) -> {

                    currentPo.getItems().remove(position);

                    if (currentPo.getItems().isEmpty()) {
                        poRef.child("status").setValue(PurchaseOrder.STATUS_CANCELLED);
                        Toast.makeText(this, "Order cancelled because all items were removed.", Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }

                    double newTotal = 0;
                    for (POItem item : currentPo.getItems()) {
                        newTotal += (item.getUnitPrice() * item.getQuantity());
                    }
                    currentPo.setTotalAmount(newTotal);

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

    private void processDelivery() {
        boolean hasNewReceives = false;
        boolean isPartial = false;

        List<POItem> itemsReceivedNow = new ArrayList<>();

        List<Map<String, Object>> inspectionData = new ArrayList<>();

        for (int i = 0; i < currentPo.getItems().size(); i++) {
            POItem item = currentPo.getItems().get(i);

            double newlyReceived = itemsAdapter.getNewlyReceivedMap().containsKey(i) ? itemsAdapter.getNewlyReceivedMap().get(i) : 0.0;
            double remainingExpected = item.getQuantity() - item.getReceivedQuantity();

            if (newlyReceived > 0) {
                hasNewReceives = true;

                POItem tempItem = new POItem(item.getProductId(), item.getProductName(), newlyReceived, item.getUnitPrice(), item.getUnit());
                itemsReceivedNow.add(tempItem);
            }

            if ((item.getReceivedQuantity() + newlyReceived) < item.getQuantity()) {
                isPartial = true;
            }

            if (remainingExpected > 0) {
                Map<String, Object> map = new HashMap<>();
                map.put("name", item.getProductName());
                map.put("expected", remainingExpected);
                map.put("received", newlyReceived);
                inspectionData.add(map);
            }
        }

        if (!hasNewReceives) {
            Toast.makeText(this, "Please enter at least one received quantity.", Toast.LENGTH_SHORT).show();
            return;
        }

        showDeliveryInspectionDialog(itemsReceivedNow, inspectionData, isPartial);
    }

    private void showDeliveryInspectionDialog(List<POItem> itemsReceivedNow, List<Map<String, Object>> inspectionData, boolean isPartial) {
        View view = getLayoutInflater().inflate(R.layout.dialog_delivery_inspection, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(view).setCancelable(false).create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        LinearLayout layoutInspectionItems = view.findViewById(R.id.layoutInspectionItems);
        TextView tvInspectionWarning = view.findViewById(R.id.tvInspectionWarning);
        Button btnInspectCancel = view.findViewById(R.id.btnInspectCancel);
        Button btnInspectConfirm = view.findViewById(R.id.btnInspectConfirm);

        // FIX: Ensure Dialog text is perfectly visible in Dark/Light Mode
        boolean isDark = ThemeManager.getInstance(this).getCurrentTheme().name.equals("dark");
        int textColor = isDark ? Color.WHITE : Color.BLACK;

        if (tvInspectionWarning != null) {
            tvInspectionWarning.setTextColor(textColor);
        }

        boolean hasDiscrepancy = false;

        for (Map<String, Object> data : inspectionData) {
            View row = getLayoutInflater().inflate(R.layout.item_inspection_row, null);
            TextView tvName = row.findViewById(R.id.tvInspectName);
            TextView tvExpected = row.findViewById(R.id.tvInspectExpected);
            TextView tvReceived = row.findViewById(R.id.tvInspectReceived);

            String name = (String) data.get("name");
            double expected = (Double) data.get("expected");
            double received = (Double) data.get("received");

            tvName.setText(name);
            tvExpected.setText(String.valueOf(expected));
            tvReceived.setText(String.valueOf(received));

            // Force general text coloring to match theme
            tvName.setTextColor(textColor);
            tvExpected.setTextColor(textColor);

            if (received < expected) {
                tvReceived.setTextColor(getResources().getColor(R.color.errorRed));
                hasDiscrepancy = true;
            } else {
                tvReceived.setTextColor(getResources().getColor(R.color.successGreen));
            }

            layoutInspectionItems.addView(row);
        }

        if (isPartial || hasDiscrepancy) {
            tvInspectionWarning.setVisibility(View.VISIBLE);
        } else {
            tvInspectionWarning.setVisibility(View.GONE);
        }

        btnInspectCancel.setOnClickListener(v -> dialog.dismiss());

        btnInspectConfirm.setOnClickListener(v -> {
            dialog.dismiss();

            for (int i = 0; i < currentPo.getItems().size(); i++) {
                POItem item = currentPo.getItems().get(i);
                double newlyReceived = itemsAdapter.getNewlyReceivedMap().containsKey(i) ? itemsAdapter.getNewlyReceivedMap().get(i) : 0.0;
                item.setReceivedQuantity(item.getReceivedQuantity() + newlyReceived);
            }

            checkProductsAndFinalize(itemsReceivedNow, isPartial);
        });

        dialog.show();
    }

    private void checkProductsAndFinalize(List<POItem> itemsReceivedNow, boolean isPartial) {
        ArrayList<Bundle> registrationQueue = new ArrayList<>();
        int[] processedCount = {0};

        if (itemsReceivedNow.isEmpty()) {
            finalizeDeliveryStatus(isPartial, registrationQueue);
            return;
        }

        for (POItem item : itemsReceivedNow) {
            productRepository.getProductById(item.getProductId(), new ProductRepository.OnProductFetchedListener() {
                @Override
                public void onProductFetched(Product p) {
                    if (p != null) {
                        double convertedQty = calculateConvertedQuantity(item.getReceivedQuantity(), item.getUnit(), p.getUnit(), p.getPiecesPerUnit());
                        double newQty = p.getQuantity() + convertedQty;

                        double conversionRatio = convertedQty / item.getReceivedQuantity();
                        double newCostPerUnit = item.getUnitPrice() / conversionRatio;

                        productRepository.updateProductQuantityAndCost(p.getProductId(), newQty, newCostPerUnit, null);

                    } else {
                        Bundle b = new Bundle();
                        b.putString("productName", item.getProductName());
                        b.putDouble("quantity", item.getReceivedQuantity());
                        b.putDouble("costPrice", item.getUnitPrice());
                        b.putString("unit", item.getUnit());
                        registrationQueue.add(b);
                    }

                    processedCount[0]++;
                    if (processedCount[0] == itemsReceivedNow.size()) {
                        finalizeDeliveryStatus(isPartial, registrationQueue);
                    }
                }

                @Override
                public void onError(String error) {
                    processedCount[0]++;
                    if (processedCount[0] == itemsReceivedNow.size()) {
                        finalizeDeliveryStatus(isPartial, registrationQueue);
                    }
                }
            });
        }
    }


    private double calculateConvertedQuantity(double receivedQty, String inUnit, String invUnit, int piecesPerUnit) {
        if (inUnit == null || invUnit == null) return receivedQty;

        inUnit = inUnit.toLowerCase(Locale.ROOT).trim();
        invUnit = invUnit.toLowerCase(Locale.ROOT).trim();

        if (inUnit.equals("l") && invUnit.equals("ml")) {
            return receivedQty * 1000.0;
        } else if (inUnit.equals("kg") && invUnit.equals("g")) {
            return receivedQty * 1000.0;
        } else if (inUnit.equals("l") && invUnit.equals("oz")) {
            return receivedQty * 33.814;
        }
        if (!inUnit.equals(invUnit) && piecesPerUnit > 1) {
            return receivedQty * piecesPerUnit;
        }

        return receivedQty;
    }

    private void finalizeDeliveryStatus(boolean isPartial, ArrayList<Bundle> registrationQueue) {
        String newStatus = isPartial ? PurchaseOrder.STATUS_PARTIAL : PurchaseOrder.STATUS_RECEIVED;

        poRef.child("status").setValue(newStatus).addOnSuccessListener(aVoid -> {
            poRef.child("items").setValue(currentPo.getItems());

            Toast.makeText(this, "Delivery updated and inventory synchronized!", Toast.LENGTH_SHORT).show();

            SyncScheduler.enqueueImmediateSync(getApplicationContext());

            if (!registrationQueue.isEmpty()) {
                Intent intent = new Intent(this, AddProductActivity.class);
                intent.putParcelableArrayListExtra("REGISTRATION_QUEUE", registrationQueue);
                startActivity(intent);
            } else {
                finish();
            }
        });
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