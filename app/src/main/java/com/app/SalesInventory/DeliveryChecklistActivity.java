package com.app.SalesInventory;

import android.app.Application;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DeliveryChecklistActivity extends BaseActivity {

    private CheckBox cbSelectAll;
    private TextView tvSelectedCount;
    private Button btnMoveToInventory;
    private RecyclerView recyclerView;

    private DatabaseReference stagingRef;
    private ProductRepository productRepository;
    private ChecklistAdapter adapter;
    private List<StagedItem> stagedItemsList = new ArrayList<>();
    private String currentOwnerId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_checklist);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Delivery Checklist");
            getSupportActionBar().setSubtitle("Verify physical items received to update live, on-hand stock");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        cbSelectAll = findViewById(R.id.cbSelectAll);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        btnMoveToInventory = findViewById(R.id.btnMoveToInventory);
        recyclerView = findViewById(R.id.recyclerViewChecklist);

        productRepository = SalesInventoryApplication.getProductRepository();
        currentOwnerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (currentOwnerId == null || currentOwnerId.isEmpty()) {
            currentOwnerId = AuthManager.getInstance().getCurrentUserId();
        }

        // Firebase node specifically for the Staging Area
        stagingRef = FirebaseDatabase.getInstance().getReference("DeliveryChecklist").child(currentOwnerId);

        adapter = new ChecklistAdapter();
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        loadStagedItems();

        cbSelectAll.setOnCheckedChangeListener((buttonView, isChecked) -> {
            for (StagedItem item : stagedItemsList) {
                item.isSelected = isChecked;
            }
            adapter.notifyDataSetChanged();
            updateSelectedCount();
        });

        btnMoveToInventory.setOnClickListener(v -> processSelectedItems());
    }

    private void loadStagedItems() {
        stagingRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                stagedItemsList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    StagedItem item = ds.getValue(StagedItem.class);
                    if (item != null) {
                        item.dbKey = ds.getKey();
                        stagedItemsList.add(item);
                    }
                }
                adapter.notifyDataSetChanged();
                updateSelectedCount();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(DeliveryChecklistActivity.this, "Failed to load checklist", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateSelectedCount() {
        int count = 0;
        for (StagedItem item : stagedItemsList) {
            if (item.isSelected) count++;
        }
        tvSelectedCount.setText(count + " Selected");
        btnMoveToInventory.setEnabled(count > 0);
    }

    private void processSelectedItems() {
        List<StagedItem> itemsToProcess = new ArrayList<>();
        boolean hasPartial = false;
        StringBuilder partialWarning = new StringBuilder("The following items are incomplete:\n\n");

        for (StagedItem item : stagedItemsList) {
            if (item.isSelected) {
                itemsToProcess.add(item);

                // FIXED: Check if they are trying to move a partial delivery!
                if (item.expectedQuantity > 0 && item.quantity < item.expectedQuantity) {
                    hasPartial = true;
                    partialWarning.append("• ").append(item.productName)
                            .append("\n  (Expected: ").append(item.expectedQuantity)
                            .append(", Arrived: ").append(item.quantity).append(")\n\n");
                }
            }
        }

        if (itemsToProcess.isEmpty()) return;

        // FIXED: Show the Warning Popup if incomplete!
        if (hasPartial) {
            partialWarning.append("Do you still want to proceed and move these to inventory?");
            new AlertDialog.Builder(this)
                    .setTitle("Partial Delivery Warning")
                    .setMessage(partialWarning.toString())
                    .setPositiveButton("Proceed", (dialog, which) -> executeInventoryMove(itemsToProcess))
                    .setNegativeButton("Cancel", null)
                    .show();
        } else {
            // If everything is complete, move it immediately
            executeInventoryMove(itemsToProcess);
        }
    }

    // --- NEW: We extracted the actual moving logic into its own method ---
    private void executeInventoryMove(List<StagedItem> itemsToProcess) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setView(new android.widget.ProgressBar(this));
        builder.setMessage("Verifying and moving to inventory...");
        AlertDialog loadingDialog = builder.create();
        loadingDialog.show();

        ArrayList<Bundle> registrationQueue = new ArrayList<>();
        int[] processedCount = {0};

        for (StagedItem item : itemsToProcess) {
            String safeProductId = item.productId != null ? item.productId : "unknown";

            // CRITICAL FIX: If the item is marked unknown/new, immediately queue it for registration!
            if (safeProductId.equals("unknown") || safeProductId.trim().isEmpty()) {
                runOnUiThread(() -> {
                    Bundle b = new Bundle();
                    b.putString("productName", item.productName);
                    b.putDouble("quantity", item.quantity > 0 ? item.quantity : 1.0);
                    b.putDouble("costPrice", item.unitPrice);
                    b.putString("unit", item.unit);
                    b.putString("stagedItemKey", item.dbKey);
                    registrationQueue.add(b);

                    processedCount[0]++;
                    if (processedCount[0] == itemsToProcess.size()) {
                        if (loadingDialog.isShowing()) loadingDialog.dismiss();
                        finalizeProcess(registrationQueue);
                    }
                });
                continue; // Skip the database call completely!
            }

            // Otherwise, process normal registered items
            productRepository.getProductById(safeProductId, new ProductRepository.OnProductFetchedListener() {
                @Override
                public void onProductFetched(Product p) {
                    runOnUiThread(() -> {
                        double qtyToProcess = item.quantity > 0 ? item.quantity : 1.0;

                        if (p != null) {
                            double convertedQty = calculateConvertedQuantity(qtyToProcess, item.unit, p.getUnit(), p.getPiecesPerUnit());
                            double newQty = p.getQuantity() + convertedQty;
                            double newCostPerUnit = item.unitPrice;

                            productRepository.updateProductQuantityAndCost(p.getProductId(), newQty, newCostPerUnit, null);
                            if (item.dbKey != null) stagingRef.child(item.dbKey).removeValue();
                        } else {
                            Bundle b = new Bundle();
                            b.putString("productName", item.productName);
                            b.putDouble("quantity", qtyToProcess);
                            b.putDouble("costPrice", item.unitPrice);
                            b.putString("unit", item.unit);
                            b.putString("stagedItemKey", item.dbKey);

                            registrationQueue.add(b);
                        }

                        processedCount[0]++;
                        if (processedCount[0] == itemsToProcess.size()) {
                            if (loadingDialog.isShowing()) loadingDialog.dismiss();
                            finalizeProcess(registrationQueue);
                        }
                    });
                }

                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        Bundle b = new Bundle();
                        b.putString("productName", item.productName);
                        b.putDouble("quantity", item.quantity > 0 ? item.quantity : 1.0);
                        b.putDouble("costPrice", item.unitPrice);
                        b.putString("unit", item.unit);
                        b.putString("stagedItemKey", item.dbKey);

                        registrationQueue.add(b);

                        processedCount[0]++;
                        if (processedCount[0] == itemsToProcess.size()) {
                            if (loadingDialog.isShowing()) loadingDialog.dismiss();
                            finalizeProcess(registrationQueue);
                        }
                    });
                }
            });
        }
    }

    private void finalizeProcess(ArrayList<Bundle> registrationQueue) {
        // CRITICAL FIX: Ensure the Toasts and Intents always run on the Main UI Thread!
        runOnUiThread(() -> {
            if (!registrationQueue.isEmpty()) {
                Toast.makeText(this, "Some items are new! Redirecting to Registration...", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(this, AddProductActivity.class);
                intent.putParcelableArrayListExtra("REGISTRATION_QUEUE", registrationQueue);
                startActivity(intent);
            } else {
                Toast.makeText(this, "Successfully moved to Inventory!", Toast.LENGTH_SHORT).show();
                cbSelectAll.setChecked(false);
            }
            SyncScheduler.enqueueImmediateSync(getApplicationContext());
        });
    }

    private double calculateConvertedQuantity(double receivedQty, String inUnit, String invUnit, int piecesPerUnit) {
        if (inUnit == null || invUnit == null) return receivedQty;

        inUnit = inUnit.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "").trim();
        invUnit = invUnit.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "").trim();

        if (inUnit.equals("l") && invUnit.equals("ml")) return receivedQty * 1000.0;
        else if (inUnit.equals("kg") && invUnit.equals("g")) return receivedQty * 1000.0;
        else if (inUnit.equals("l") && invUnit.equals("oz")) return receivedQty * 33.814;

        if (!inUnit.equals(invUnit) && piecesPerUnit > 1) return receivedQty * piecesPerUnit;

        return receivedQty;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    // ==============================================================================
    // INNER CLASSES FOR ADAPTER AND DATA MODEL
    // ==============================================================================

    public static class StagedItem {
        public String dbKey;
        public String productId;
        public String productName;
        public double quantity;
        public double expectedQuantity;
        public double unitPrice;
        public String unit;
        public boolean isSelected = false;

        public StagedItem() {} // Required for Firebase
    }

    private class ChecklistAdapter extends RecyclerView.Adapter<ChecklistAdapter.VH> {
        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(DeliveryChecklistActivity.this).inflate(R.layout.item_delivery_checklist, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            StagedItem item = stagedItemsList.get(position);
            holder.tvItemName.setText(item.productName);
            holder.tvItemDetails.setText("Qty: " + item.quantity + " " + item.unit + " | Cost: ₱" + item.unitPrice);

            holder.cbItemSelect.setOnCheckedChangeListener(null);
            holder.cbItemSelect.setChecked(item.isSelected);
            holder.cbItemSelect.setOnCheckedChangeListener((btn, isChecked) -> {
                item.isSelected = isChecked;
                updateSelectedCount();
            });

            // FIXED: Immediately tag as new if there is no valid ID
            if (item.productId == null || item.productId.trim().isEmpty() || item.productId.equals("unknown")) {
                holder.tvItemStatus.setText("New Item: Will require registration");
                holder.tvItemStatus.setTextColor(Color.parseColor("#D32F2F")); // Red
                return;
            }

            final String fetchId = item.productId;
            holder.tvItemStatus.setTag(fetchId);
            holder.tvItemStatus.setText("Checking status...");
            holder.tvItemStatus.setTextColor(Color.GRAY);

            productRepository.getProductById(fetchId, new ProductRepository.OnProductFetchedListener() {
                @Override
                public void onProductFetched(Product p) {
                    DeliveryChecklistActivity.this.runOnUiThread(() -> {
                        // FIXED: Only apply the text if this row is STILL showing the original item
                        if (holder.tvItemStatus.getTag() != null && holder.tvItemStatus.getTag().equals(fetchId)) {
                            if (p != null) {
                                holder.tvItemStatus.setText("Registered: Will add stock");
                                holder.tvItemStatus.setTextColor(Color.parseColor("#2E7D32")); // Green
                            } else {
                                holder.tvItemStatus.setText("New Item: Will require registration");
                                holder.tvItemStatus.setTextColor(Color.parseColor("#D32F2F")); // Red
                            }
                        }
                    });
                }

                @Override
                public void onError(String e) {
                    DeliveryChecklistActivity.this.runOnUiThread(() -> {
                        if (holder.tvItemStatus.getTag() != null && holder.tvItemStatus.getTag().equals(fetchId)) {
                            holder.tvItemStatus.setText("New Item: Will require registration");
                            holder.tvItemStatus.setTextColor(Color.parseColor("#D32F2F")); // Red
                        }
                    });
                }
            });
        }

        @Override public int getItemCount() { return stagedItemsList.size(); }

        class VH extends RecyclerView.ViewHolder {
            CheckBox cbItemSelect;
            TextView tvItemName, tvItemDetails, tvItemStatus;
            public VH(@NonNull View itemView) {
                super(itemView);
                cbItemSelect = itemView.findViewById(R.id.cbItemSelect);
                tvItemName = itemView.findViewById(R.id.tvItemName);
                tvItemDetails = itemView.findViewById(R.id.tvItemDetails);
                tvItemStatus = itemView.findViewById(R.id.tvItemStatus);
            }
        }
    }
}