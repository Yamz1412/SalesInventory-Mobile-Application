package com.app.SalesInventory;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.RecyclerView;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PurchaseOrderAdapter extends RecyclerView.Adapter<PurchaseOrderAdapter.POViewHolder> {

    private Context context;
    private List<PurchaseOrder> poList;
    private OnPOClickListener clickListener;
    private PurchaseOrderRepository repository;

    public interface OnPOClickListener {
        void onPOClick(PurchaseOrder po);
    }

    public PurchaseOrderAdapter(Context context, List<PurchaseOrder> poList, OnPOClickListener clickListener) {
        this.context = context;
        this.poList = poList;
        this.clickListener = clickListener;
        this.repository = PurchaseOrderRepository.getInstance();
    }

    public void setPurchaseOrders(List<PurchaseOrder> list) {
        this.poList = list;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public POViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_purchase_order, parent, false);
        return new POViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull POViewHolder holder, int position) {
        if (poList == null || position < 0 || position >= poList.size()) return;
        PurchaseOrder po = poList.get(position);

        holder.tvPONumber.setText(po.getPoNumber() == null ? (po.getPoId() == null ? "" : po.getPoId()) : po.getPoNumber());
        holder.tvSupplier.setText(po.getSupplierName() == null ? "" : po.getSupplierName());
        holder.tvStatus.setText(po.getStatus() == null ? "" : po.getStatus());

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy", Locale.getDefault());
        holder.tvOrderDate.setText(sdf.format(new Date(po.getOrderDate())));

        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
        holder.tvTotalAmount.setText(currencyFormat.format(po.getTotalAmount()));

        int statusColor;
        String st = po.getStatus() == null ? "" : po.getStatus();
        if (st.equalsIgnoreCase("Pending") || st.equalsIgnoreCase(PurchaseOrder.STATUS_PENDING)) {
            statusColor = context.getResources().getColor(R.color.warningYellow);
        } else if (st.equalsIgnoreCase("Received") || st.equalsIgnoreCase(PurchaseOrder.STATUS_RECEIVED)) {
            statusColor = context.getResources().getColor(R.color.successGreen);
        } else if (st.equalsIgnoreCase("Cancelled") || st.equalsIgnoreCase(PurchaseOrder.STATUS_CANCELLED)) {
            statusColor = context.getResources().getColor(R.color.errorRed);
        } else {
            statusColor = context.getResources().getColor(R.color.textColorSecondary);
        }
        holder.tvStatus.setTextColor(statusColor);

        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onPOClick(po);
            }
        });

        holder.itemView.setOnLongClickListener(v -> {
            AlertDialog.Builder b = new AlertDialog.Builder(context);
            b.setTitle("Archive Purchase Order");
            b.setMessage("Archive this purchase order?");
            b.setPositiveButton("Archive", (d, w) -> {
                String poId = po.getPoId();
                if (poId == null || poId.isEmpty()) {
                    Toast.makeText(context, "Cannot archive: missing PO id", Toast.LENGTH_SHORT).show();
                    return;
                }
                repository.archivePurchaseOrder(context, poId, new PurchaseOrderRepository.OnArchiveListener() {
                    @Override
                    public void onArchived(String filename) {
                        int idx = findIndexById(poId);
                        if (idx >= 0) {
                            poList.remove(idx);
                            notifyItemRemoved(idx);
                        } else {
                            notifyDataSetChanged();
                        }
                        Toast.makeText(context, "PO archived", Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onError(String error) {
                        Toast.makeText(context, "Archive failed: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            });
            b.setNegativeButton("Cancel", null);
            b.show();
            return true;
        });

        if (holder.btnArchive != null) {
            holder.btnArchive.setOnClickListener(v -> {
                String poId = po.getPoId();
                if (poId == null || poId.isEmpty()) {
                    Toast.makeText(context, "Cannot archive: missing PO id", Toast.LENGTH_SHORT).show();
                    return;
                }
                repository.archivePurchaseOrder(context, poId, new PurchaseOrderRepository.OnArchiveListener() {
                    @Override
                    public void onArchived(String filename) {
                        int idx = findIndexById(poId);
                        if (idx >= 0) {
                            poList.remove(idx);
                            notifyItemRemoved(idx);
                        } else {
                            notifyDataSetChanged();
                        }
                        Intent i = new Intent(context, DeleteProductActivity.class);
                        i.putExtra("archiveType", "po");
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(i);
                    }
                    @Override
                    public void onError(String error) {
                        Toast.makeText(context, "Archive failed: " + error, Toast.LENGTH_LONG).show();
                    }
                });
            });
        }
    }

    private int findIndexById(String poId) {
        if (poList == null || poId == null) return -1;
        for (int i = 0; i < poList.size(); i++) {
            PurchaseOrder p = poList.get(i);
            if (p == null) continue;
            String id = p.getPoId();
            if (id == null) id = p.getPoNumber();
            if (poId.equals(id)) return i;
        }
        return -1;
    }

    @Override
    public int getItemCount() {
        return poList == null ? 0 : poList.size();
    }

    static class POViewHolder extends RecyclerView.ViewHolder {
        TextView tvPONumber, tvSupplier, tvOrderDate, tvStatus, tvTotalAmount;
        ImageButton btnArchive;

        public POViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPONumber = itemView.findViewById(R.id.tvPONumber);
            tvSupplier = itemView.findViewById(R.id.tvSupplier);
            tvOrderDate = itemView.findViewById(R.id.tvOrderDate);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvTotalAmount = itemView.findViewById(R.id.tvTotalAmount);
            btnArchive = itemView.findViewById(R.id.btnArchive);
        }
    }
}