package com.app.SalesInventory;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.os.Bundle;
import android.view.LayoutInflater;
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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DeliveryReportActivity extends BaseActivity {

    private RecyclerView recyclerView;
    private TextView tvNoData;
    private DeliveryReportAdapter adapter;
    private SalesRepository salesRepository;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());
    private DatabaseReference poRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_delivery_report);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Delivery Report");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        recyclerView = findViewById(R.id.recyclerViewDelivery);
        tvNoData = findViewById(R.id.tvNoDataDelivery);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeliveryReportAdapter(new ArrayList<>(), orderId -> markOrderDelivered(orderId));
        recyclerView.setAdapter(adapter);
        salesRepository = SalesRepository.getInstance();
        poRef = FirebaseDatabase.getInstance().getReference("PurchaseOrders");
        loadData();
    }

    private void loadData() {
        salesRepository.getAllSales().observe(this, sales -> {
            Map<String, DeliveryOrder> map = new HashMap<>();
            if (sales != null) {
                for (Sales s : sales) {
                    if (s == null) continue;
                    if (s.getDeliveryType() == null || !"DELIVERY".equalsIgnoreCase(s.getDeliveryType())) continue;
                    String orderId = s.getOrderId();
                    if (orderId == null) continue;
                    DeliveryOrder o = map.get(orderId);
                    if (o == null) {
                        o = new DeliveryOrder();
                        o.orderId = orderId;
                        o.deliveryStatus = s.getDeliveryStatus() == null ? "PENDING" : s.getDeliveryStatus();
                        o.deliveryDate = s.getDeliveryDate();
                        o.orderDate = s.getDate();
                        o.totalAmount = 0;
                        o.customerName = s.getDeliveryName();
                        o.paymentType = s.getDeliveryPaymentMethod();
                        o.source = "SALE";
                        map.put(orderId, o);
                    }
                    o.totalAmount += s.getTotalPrice();
                }
            }
            poRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot snapshot) {
                    if (snapshot != null && snapshot.exists()) {
                        for (DataSnapshot ds : snapshot.getChildren()) {
                            PurchaseOrder po = ds.getValue(PurchaseOrder.class);
                            if (po == null) continue;
                            String status = po.getStatus();
                            if (status == null) continue;
                            if (!("RECEIVED".equalsIgnoreCase(status) || PurchaseOrder.STATUS_RECEIVED.equalsIgnoreCase(status))) continue;
                            String poId = po.getPoId();
                            if (poId == null || poId.isEmpty()) poId = ds.getKey();
                            if (poId == null) continue;
                            if (map.containsKey(poId)) continue;
                            DeliveryOrder od = new DeliveryOrder();
                            od.orderId = poId;
                            od.deliveryStatus = "RECEIVED";
                            od.deliveryDate = po.getOrderDate();
                            od.orderDate = po.getOrderDate();
                            od.totalAmount = po.getTotalAmount();
                            od.customerName = po.getSupplierName();
                            od.paymentType = "PURCHASE_ORDER";
                            od.source = "PO";
                            map.put(poId, od);
                        }
                    }
                    List<DeliveryOrder> list = new ArrayList<>(map.values());
                    if (list.isEmpty()) {
                        tvNoData.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        tvNoData.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        adapter.setItems(list);
                    }
                }

                @Override
                public void onCancelled(@NonNull DatabaseError error) {
                    List<DeliveryOrder> list = new ArrayList<>(map.values());
                    if (list.isEmpty()) {
                        tvNoData.setVisibility(View.VISIBLE);
                        recyclerView.setVisibility(View.GONE);
                    } else {
                        tvNoData.setVisibility(View.GONE);
                        recyclerView.setVisibility(View.VISIBLE);
                        adapter.setItems(list);
                    }
                }
            });
        });
    }

    private void markOrderDelivered(String orderId) {
        if (orderId == null || orderId.isEmpty()) return;
        salesRepository.getAllSales().observe(this, sales -> {
            if (sales == null) return;
            long now = System.currentTimeMillis();
            for (Sales s : sales) {
                if (orderId.equals(s.getOrderId()) && "DELIVERY".equalsIgnoreCase(s.getDeliveryType())) {
                    s.setDeliveryStatus("DELIVERED");
                    s.setDeliveryDate(now);
                    salesRepository.updateSaleDeliveryStatus(s);
                }
            }
            Toast.makeText(this, "Order marked as delivered", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    private static class DeliveryOrder {
        String orderId;
        double totalAmount;
        String deliveryStatus;
        long orderDate;
        long deliveryDate;
        String customerName;
        String paymentType;
        String source;
    }

    private interface OnMarkDeliveredListener {
        void onMarkDelivered(String orderId);
    }

    private class DeliveryReportAdapter extends RecyclerView.Adapter<DeliveryReportAdapter.ViewHolder> {

        private List<DeliveryOrder> items;
        private OnMarkDeliveredListener listener;

        DeliveryReportAdapter(List<DeliveryOrder> items, OnMarkDeliveredListener listener) {
            this.items = items;
            this.listener = listener;
        }

        void setItems(List<DeliveryOrder> newItems) {
            this.items = newItems;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_delivery_order, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DeliveryOrder o = items.get(position);
            holder.tvOrderId.setText(o.orderId);
            String dateStr = o.orderDate > 0 ? dateFormat.format(new Date(o.orderDate)) : "-";
            holder.tvOrderDate.setText(dateStr);
            holder.tvAmount.setText("₱" + String.format(Locale.getDefault(), "%.2f", o.totalAmount));
            holder.tvStatus.setText(o.deliveryStatus == null ? "PENDING" : o.deliveryStatus);
            holder.tvCustomer.setText(o.customerName == null ? "" : o.customerName);
            holder.tvPayment.setText(o.paymentType == null ? "" : o.paymentType);
            if ("PO".equals(o.source)) {
                holder.tvStatus.setTextColor(getResources().getColor(R.color.successGreen));
                holder.btnMarkDelivered.setVisibility(View.GONE);
            } else {
                if ("DELIVERED".equalsIgnoreCase(o.deliveryStatus)) {
                    holder.btnMarkDelivered.setVisibility(View.GONE);
                } else {
                    holder.btnMarkDelivered.setVisibility(View.VISIBLE);
                    holder.btnMarkDelivered.setOnClickListener(v -> {
                        if (listener != null) listener.onMarkDelivered(o.orderId);
                    });
                }
            }
        }

        @Override
        public int getItemCount() {
            return items == null ? 0 : items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvOrderId;
            TextView tvOrderDate;
            TextView tvAmount;
            TextView tvStatus;
            TextView tvCustomer;
            TextView tvPayment;
            Button btnMarkDelivered;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvOrderId = itemView.findViewById(R.id.tvOrderId);
                tvOrderDate = itemView.findViewById(R.id.tvOrderDate);
                tvAmount = itemView.findViewById(R.id.tvAmount);
                tvStatus = itemView.findViewById(R.id.tvStatus);
                tvCustomer = itemView.findViewById(R.id.tvCustomer);
                tvPayment = itemView.findViewById(R.id.tvPayment);
                btnMarkDelivered = itemView.findViewById(R.id.btnMarkDelivered);
            }
        }
    }
}