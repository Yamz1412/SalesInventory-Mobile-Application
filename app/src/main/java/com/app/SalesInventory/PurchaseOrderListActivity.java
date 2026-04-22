package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class PurchaseOrderListActivity extends BaseActivity  {

    private RecyclerView recyclerView;
    private PurchaseOrderAdapter adapter;
    private List<PurchaseOrder> purchaseOrderList;

    private Button btnCreatePO, btnAddSupplier, btnReturnProduct, btnChecklist;
    private DatabaseReference poRef;
    private List<PurchaseOrder> fullList = new ArrayList<>(); // To store all data
    private List<PurchaseOrder> displayedList = new ArrayList<>(); // For the adapter
    private com.google.android.material.switchmaterial.SwitchMaterial switchCompletedOnly;
    private android.widget.ImageButton btnFilterSort;
    private String currentSortOption = "Recently Added";
    private androidx.cardview.widget.CardView cardChecklistBadge;
    private TextView tvChecklistBadge;
    private DatabaseReference checklistRef;
    private ValueEventListener checklistBadgeListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_purchase_order_list);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
        }

        recyclerView = findViewById(R.id.recyclerViewPurchaseOrders);
        btnCreatePO = findViewById(R.id.btnCreatePO);
        btnAddSupplier = findViewById(R.id.btnAddSupplier);
        btnReturnProduct = findViewById(R.id.btnReturnProduct);
        btnChecklist = findViewById(R.id.btnChecklist);
        cardChecklistBadge = findViewById(R.id.cardChecklistBadge);
        tvChecklistBadge = findViewById(R.id.tvChecklistBadge);

        if (!AuthManager.getInstance().hasManagerAccess()) {
            btnCreatePO.setVisibility(View.GONE);
            btnAddSupplier.setVisibility(View.GONE);
            btnReturnProduct.setVisibility(View.GONE);
        }

        // New UI Links
        switchCompletedOnly = findViewById(R.id.switchCompletedOnly);
        btnFilterSort = findViewById(R.id.btnFilterSortPO);

        poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");

        adapter = new PurchaseOrderAdapter(this, displayedList, this::viewPurchaseOrder, this::showManageOptions);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        setupListeners();
        injectMockPOs();
        injectMockReturns();
        listenToChecklistCount();
        loadPurchaseOrders();

        btnCreatePO.setOnClickListener(v -> {
            Intent intent = new Intent(PurchaseOrderListActivity.this, CreatePurchaseOrderActivity.class);
            startActivity(intent);
        });

        btnAddSupplier.setOnClickListener(v -> {
            Intent intent = new Intent(PurchaseOrderListActivity.this, AddSupplierActivity.class);
            startActivity(intent);
        });

        btnReturnProduct.setOnClickListener(v -> {
            Intent intent = new Intent(PurchaseOrderListActivity.this, ReturnProductActivity.class);
            startActivity(intent);
        });
    }

    private void listenToChecklistCount() {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = AuthManager.getInstance().getCurrentUserId();
        }
        if (ownerId == null) return;

        checklistRef = FirebaseDatabase.getInstance().getReference("DeliveryChecklist").child(ownerId);
        checklistBadgeListener = checklistRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                runOnUiThread(() -> {
                    if (count > 0) {
                        // Show the red badge and update the number
                        if (cardChecklistBadge != null) cardChecklistBadge.setVisibility(View.VISIBLE);
                        if (tvChecklistBadge != null) tvChecklistBadge.setText(String.valueOf(count));
                    } else {
                        // Hide the badge if checklist is empty
                        if (cardChecklistBadge != null) cardChecklistBadge.setVisibility(View.GONE);
                    }
                });
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void injectMockReturns() {
        String currentAdminId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (currentAdminId == null || currentAdminId.isEmpty()) {
            currentAdminId = AuthManager.getInstance().getCurrentUserId();
        }
        if (currentAdminId == null) return;

        final String adminId = currentAdminId;
        DatabaseReference returnsRef = FirebaseDatabase.getInstance().getReference("Returns");

        returnsRef.orderByChild("ownerAdminId").equalTo(adminId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean hasMock = false;
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String reason = ds.child("reason").getValue(String.class);
                    if (reason != null && reason.contains("[MOCK]")) {
                        hasMock = true;
                        break;
                    }
                }
                if (!hasMock) {
                    generateMockReturns(adminId, returnsRef);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void generateMockReturns(String adminId, DatabaseReference returnsRef) {
        long now = System.currentTimeMillis();
        long oneDay = 24L * 60 * 60 * 1000L;

        Map<String, Object> ret1 = new HashMap<>();
        ret1.put("ownerAdminId", adminId);
        ret1.put("date", now - (14 * oneDay));
        ret1.put("supplierName", "Sweet Syrups Inc.");
        ret1.put("reason", "Damaged during transit (Shattered bottles) [MOCK]");
        List<Map<String, Object>> items1 = new ArrayList<>();
        Map<String, Object> item1a = new HashMap<>();
        item1a.put("productName", "Vanilla Syrup");
        item1a.put("returnQty", 2);
        item1a.put("unit", "bottle");
        items1.add(item1a);
        ret1.put("items", items1);

        Map<String, Object> ret2 = new HashMap<>();
        ret2.put("ownerAdminId", adminId);
        ret2.put("date", now - (5 * oneDay));
        ret2.put("supplierName", "Daily Dairy Suppliers");
        ret2.put("reason", "Delivered milk was already sour/spoiled upon arrival [MOCK]");
        List<Map<String, Object>> items2 = new ArrayList<>();
        Map<String, Object> item2a = new HashMap<>();
        item2a.put("productName", "Fresh Whole Milk");
        item2a.put("returnQty", 10);
        item2a.put("unit", "L");
        items2.add(item2a);
        ret2.put("items", items2);

        Map<String, Object> ret3 = new HashMap<>();
        ret3.put("ownerAdminId", adminId);
        ret3.put("date", now - (25 * oneDay));
        ret3.put("supplierName", "Packaging Pros");
        ret3.put("reason", "Wrong sizes delivered (Received 12oz cups instead of 16oz) [MOCK]");
        List<Map<String, Object>> items3 = new ArrayList<>();
        Map<String, Object> item3a = new HashMap<>();
        item3a.put("productName", "16oz Plastic Cups");
        item3a.put("returnQty", 5);
        item3a.put("unit", "box");
        items3.add(item3a);
        ret3.put("items", items3);

        returnsRef.push().setValue(ret1);
        returnsRef.push().setValue(ret2);
        returnsRef.push().setValue(ret3);
    }

    private void injectMockPOs() {
        String currentAdminId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (currentAdminId == null || currentAdminId.isEmpty()) {
            currentAdminId = AuthManager.getInstance().getCurrentUserId();
        }
        if (currentAdminId == null) return;

        final String adminId = currentAdminId;

        poRef.orderByChild("ownerAdminId").equalTo(adminId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                boolean hasMock = false;
                for (DataSnapshot ds : snapshot.getChildren()) {
                    PurchaseOrder po = ds.getValue(PurchaseOrder.class);
                    if (po != null && po.getPoNumber() != null && po.getPoNumber().startsWith("PO-MOCK-")) {
                        hasMock = true;
                        break;
                    }
                }
                if (!hasMock) {
                    generateMockData(adminId);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void generateMockData(String adminId) {
        long now = System.currentTimeMillis();
        long oneDay = 24L * 60 * 60 * 1000L;

        List<PurchaseOrder> mockPOs = new ArrayList<>();

        List<POItem> items1 = new ArrayList<>();
        items1.add(new POItem("MOCK-P1", "Arabica Beans", 10, 800.0, "kg"));
        items1.get(0).setReceivedQuantity(10);
        items1.add(new POItem("MOCK-P2", "Robusta Beans", 5, 600.0, "kg"));
        items1.get(1).setReceivedQuantity(5);
        PurchaseOrder po1 = new PurchaseOrder("MOCK-PO-1", "PO-MOCK-1001", "Bean Crafters Co.", "09171234567", PurchaseOrder.STATUS_COMPLETED, now - (30 * oneDay), 11000.0, items1);
        po1.setOwnerAdminId(adminId); po1.setExpectedDeliveryDate(now - (28 * oneDay));
        mockPOs.add(po1);

        List<POItem> items2 = new ArrayList<>();
        items2.add(new POItem("MOCK-P3", "16oz Plastic Cups", 20, 150.0, "box"));
        items2.get(0).setReceivedQuantity(10);
        items2.add(new POItem("MOCK-P4", "Dome Lids", 20, 100.0, "box"));
        items2.get(1).setReceivedQuantity(20);
        PurchaseOrder po2 = new PurchaseOrder("MOCK-PO-2", "PO-MOCK-1002", "Packaging Pros", "09179876543", PurchaseOrder.STATUS_PARTIAL, now - (15 * oneDay), 5000.0, items2);
        po2.setOwnerAdminId(adminId); po2.setExpectedDeliveryDate(now - (10 * oneDay));
        po2.setDeliveryNote("Supplier ran out of 16oz cups. Will deliver remaining next week.");
        mockPOs.add(po2);

        List<POItem> items3 = new ArrayList<>();
        items3.add(new POItem("MOCK-P5", "Vanilla Syrup", 12, 350.0, "bottle"));
        items3.add(new POItem("MOCK-P6", "Caramel Sauce", 6, 450.0, "bottle"));
        PurchaseOrder po3 = new PurchaseOrder("MOCK-PO-3", "PO-MOCK-1003", "Sweet Syrups Inc.", "09181112222", PurchaseOrder.STATUS_PENDING, now - (2 * oneDay), 6900.0, items3);
        po3.setOwnerAdminId(adminId); po3.setExpectedDeliveryDate(now + (2 * oneDay));
        mockPOs.add(po3);

        List<POItem> items4 = new ArrayList<>();
        items4.add(new POItem("MOCK-P7", "Fresh Whole Milk", 50, 95.0, "L"));
        items4.get(0).setReceivedQuantity(50);
        items4.add(new POItem("MOCK-P8", "Oat Milk", 20, 180.0, "L"));
        items4.get(1).setReceivedQuantity(20);
        PurchaseOrder po4 = new PurchaseOrder("MOCK-PO-4", "PO-MOCK-1004", "Daily Dairy Suppliers", "09193334444", PurchaseOrder.STATUS_COMPLETED, now - (20 * oneDay), 8350.0, items4);
        po4.setOwnerAdminId(adminId); po4.setExpectedDeliveryDate(now - (19 * oneDay));
        mockPOs.add(po4);

        List<POItem> items5 = new ArrayList<>();
        items5.add(new POItem("MOCK-P9", "Bleach", 5, 120.0, "gal"));
        PurchaseOrder po5 = new PurchaseOrder("MOCK-PO-5", "PO-MOCK-1005", "Clean & Clear Goods", "09205556666", PurchaseOrder.STATUS_CANCELLED, now - (40 * oneDay), 600.0, items5);
        po5.setOwnerAdminId(adminId);
        po5.setDeliveryNote("Cancelled due to extreme delay in shipping. Ordered from elsewhere.");
        mockPOs.add(po5);

        List<POItem> items6 = new ArrayList<>();
        items6.add(new POItem("MOCK-P10", "Butter Croissants", 100, 45.0, "pcs"));
        items6.get(0).setReceivedQuantity(100);
        items6.add(new POItem("MOCK-P11", "Blueberry Muffins", 50, 55.0, "pcs"));
        items6.get(1).setReceivedQuantity(50);
        PurchaseOrder po6 = new PurchaseOrder("MOCK-PO-6", "PO-MOCK-1006", "City Bakery", "09217778888", PurchaseOrder.STATUS_COMPLETED, now - (5 * oneDay), 7250.0, items6);
        po6.setOwnerAdminId(adminId); po6.setExpectedDeliveryDate(now - (4 * oneDay));
        mockPOs.add(po6);

        List<POItem> items7 = new ArrayList<>();
        items7.add(new POItem("MOCK-P12", "Premium Matcha Powder", 5, 1200.0, "kg"));
        items7.add(new POItem("MOCK-P13", "Dutch Cocoa Powder", 10, 600.0, "kg"));
        PurchaseOrder po7 = new PurchaseOrder("MOCK-PO-7", "PO-MOCK-1007", "Asian Imports Ltd.", "09229990000", PurchaseOrder.STATUS_PENDING, now - (1 * oneDay), 12000.0, items7);
        po7.setOwnerAdminId(adminId); po7.setExpectedDeliveryDate(now + (4 * oneDay));
        mockPOs.add(po7);

        List<POItem> items8 = new ArrayList<>();
        items8.add(new POItem("MOCK-P14", "Paper Straws", 30, 100.0, "box"));
        items8.get(0).setReceivedQuantity(30);
        items8.add(new POItem("MOCK-P15", "Branded Tissue Napkins", 50, 80.0, "pack"));
        items8.get(1).setReceivedQuantity(10);
        PurchaseOrder po8 = new PurchaseOrder("MOCK-PO-8", "PO-MOCK-1008", "Packaging Pros", "09179876543", PurchaseOrder.STATUS_PARTIAL, now - (12 * oneDay), 7000.0, items8);
        po8.setOwnerAdminId(adminId); po8.setExpectedDeliveryDate(now - (8 * oneDay));
        po8.setDeliveryNote("Tissues are on backorder.");
        mockPOs.add(po8);

        List<POItem> items9 = new ArrayList<>();
        items9.add(new POItem("MOCK-P16", "House Blend Espresso", 20, 900.0, "kg"));
        items9.get(0).setReceivedQuantity(20);
        PurchaseOrder po9 = new PurchaseOrder("MOCK-PO-9", "PO-MOCK-1009", "Bean Crafters Co.", "09171234567", PurchaseOrder.STATUS_COMPLETED, now - (60 * oneDay), 18000.0, items9);
        po9.setOwnerAdminId(adminId); po9.setExpectedDeliveryDate(now - (58 * oneDay));
        mockPOs.add(po9);

        for (PurchaseOrder po : mockPOs) {
            poRef.child(po.getPoId()).setValue(po.toMap());
        }
    }

    private void setupListeners() {
        btnCreatePO.setOnClickListener(v -> startActivity(new Intent(this, CreatePurchaseOrderActivity.class)));
        btnAddSupplier.setOnClickListener(v -> startActivity(new Intent(this, AddSupplierActivity.class)));
        btnReturnProduct.setOnClickListener(v -> startActivity(new Intent(this, ReturnProductActivity.class)));
        btnChecklist.setOnClickListener(v -> startActivity(new Intent(this, DeliveryChecklistActivity.class)));

        // Handle Switch toggle
        switchCompletedOnly.setOnCheckedChangeListener((buttonView, isChecked) -> applyFiltersAndSort());

        // Handle Filter Button
        btnFilterSort.setOnClickListener(v -> showSortDialog());
    }

    private void loadPurchaseOrders() {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();

        if (ownerId == null || ownerId.isEmpty()) {
            ownerId = AuthManager.getInstance().getCurrentUserId();
        }

        if (ownerId == null) return; // Failsafe

        poRef.orderByChild("ownerAdminId").equalTo(ownerId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                fullList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    PurchaseOrder po = ds.getValue(PurchaseOrder.class);
                    if (po != null) {
                        po.setId(ds.getKey());
                        fullList.add(po);
                    }
                }
                applyFiltersAndSort();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showSortDialog() {
        String[] options = {"Recently Added", "Oldest First", "Highest Amount"};
        int checkedItem = java.util.Arrays.asList(options).indexOf(currentSortOption);

        new AlertDialog.Builder(this)
                .setTitle("Sort Orders")
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    currentSortOption = options[which];
                    applyFiltersAndSort();
                    dialog.dismiss();
                })
                .show();
    }

    private void applyFiltersAndSort() {
        displayedList.clear();
        boolean completedOnly = switchCompletedOnly.isChecked();

        for (PurchaseOrder po : fullList) {
            String status = po.getStatus() != null ? po.getStatus().toUpperCase() : "";
            boolean isCompleted = status.equals("RECEIVED") || status.equals("COMPLETED");

            if (completedOnly) {
                if (isCompleted) displayedList.add(po);
            } else {
                if (!isCompleted && !status.equals("CANCELLED")) displayedList.add(po);
            }
        }

        // Sorting Logic
        Collections.sort(displayedList, (o1, o2) -> {
            switch (currentSortOption) {
                case "Recently Added":
                    return Long.compare(o2.getOrderDate(), o1.getOrderDate());
                case "Oldest First":
                    return Long.compare(o1.getOrderDate(), o2.getOrderDate());
                // FIXED: Removed the Nearest Delivery math logic
                case "Highest Amount":
                    return Double.compare(o2.getTotalAmount(), o1.getTotalAmount());
                default:
                    return 0;
            }
        });

        adapter.notifyDataSetChanged();
    }

    private void viewPurchaseOrder(PurchaseOrder po) {
        Intent intent = new Intent(this, PurchaseOrderDetailActivity.class);
        intent.putExtra("PO_ID", po.getPoId());
        startActivity(intent);
    }

    private void showManageOptions(PurchaseOrder po) {
        CharSequence[] options = {"Cancel Order", "Delete Order", "Close"};
        new AlertDialog.Builder(this)
                .setTitle("Manage Purchase Order: " + po.getPoNumber())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        if (!"Pending".equalsIgnoreCase(po.getStatus())) {
                            Toast.makeText(this, "Only pending orders can be cancelled.", Toast.LENGTH_SHORT).show();
                        } else {
                            poRef.child(po.getPoId()).child("status").setValue("Cancelled");
                        }
                    } else if (which == 1) {
                        poRef.child(po.getPoId()).removeValue();
                    } else dialog.dismiss();
                })
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public interface OnItemClickListener { void onItemClick(PurchaseOrder po); }
    public interface OnItemLongClickListener { void onItemLongClick(PurchaseOrder po); }

    private class PurchaseOrderAdapter extends RecyclerView.Adapter<PurchaseOrderAdapter.POViewHolder> {
        private PurchaseOrderListActivity context;
        private List<PurchaseOrder> orders;
        private OnItemClickListener clickListener;
        private OnItemLongClickListener longClickListener;

        public PurchaseOrderAdapter(List<PurchaseOrder> orders) {
            this.orders = orders;
        }

        public PurchaseOrderAdapter(PurchaseOrderListActivity context, List<PurchaseOrder> orders, OnItemClickListener clickListener, OnItemLongClickListener longClickListener) {
            this.context = context;
            this.orders = orders;
            this.clickListener = clickListener;
            this.longClickListener = longClickListener;
        }

        @NonNull
        @Override
        public POViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_purchase_order, parent, false);
            return new POViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull POViewHolder holder, int position) {
            PurchaseOrder po = orders.get(position);
            holder.tvPoNumber.setText("PO #: " + po.getPoNumber());
            holder.tvSupplier.setText(po.getSupplierName());

            String status = po.getStatus() != null ? po.getStatus() : "PENDING";
            holder.tvStatus.setText(status.toUpperCase());

            holder.tvTotalAmount.setText(String.format(Locale.getDefault(), "₱%,.2f", po.getTotalAmount()));

            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
            holder.tvDate.setText(sdf.format(new java.util.Date(po.getOrderDate())));

            boolean isDark = false;
            try {
                isDark = ThemeManager.getInstance(context).getCurrentTheme().name.equals("dark");
            } catch (Exception e) {}
            int mainTextColor = isDark ? Color.WHITE : Color.BLACK;

            holder.tvPoNumber.setTextColor(mainTextColor);
            holder.tvSupplier.setTextColor(mainTextColor);
            holder.tvDate.setTextColor(mainTextColor);
            holder.tvTotalAmount.setTextColor(mainTextColor);

            // CRITICAL FIX: Added null safety checks for the listeners
            holder.itemView.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onItemClick(po);
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                if (longClickListener != null) {
                    longClickListener.onItemLongClick(po);
                }
                return true;
            });

            int color;
            switch (status.toUpperCase()) {
                case "RECEIVED":
                case "COMPLETED":
                    color = getResources().getColor(R.color.successGreen); break;
                case "PENDING":
                case "PARTIAL":
                    color = getResources().getColor(R.color.warningYellow); break;
                case "CANCELLED":
                    color = getResources().getColor(R.color.errorRed); break;
                default:
                    TypedValue typedValue = new TypedValue();
                    context.getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true);
                    color = typedValue.data;
            }
            holder.tvStatus.setTextColor(color);
        }

        @Override public int getItemCount() { return orders.size(); }

        class POViewHolder extends RecyclerView.ViewHolder {
            TextView tvPoNumber, tvSupplier, tvStatus, tvDate, tvTotalAmount;
            public POViewHolder(@NonNull View itemView) {
                super(itemView);
                tvPoNumber = itemView.findViewById(R.id.tvPONumber);
                tvSupplier = itemView.findViewById(R.id.tvSupplier);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                tvDate = itemView.findViewById(R.id.tvOrderDate);
                tvTotalAmount = itemView.findViewById(R.id.tvTotalAmount);
            }
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (checklistRef != null && checklistBadgeListener != null) {
            checklistRef.removeEventListener(checklistBadgeListener);
        }
    }
}