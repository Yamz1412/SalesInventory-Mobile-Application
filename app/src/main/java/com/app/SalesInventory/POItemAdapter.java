package com.app.SalesInventory;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class POItemAdapter extends RecyclerView.Adapter<POItemAdapter.ViewHolder> {

    private Context context;
    private List<POItem> items;
    private Map<Integer, Integer> newlyReceivedMap = new HashMap<>();

    // Modes
    private boolean receiveMode = false;
    private boolean viewOnlyMode = false;

    private OnItemRemovedListener removeListener;
    private Runnable onQtyChangedTask;

    public interface OnItemRemovedListener {
        void onItemRemoved(int position);
    }

    public POItemAdapter(Context context, List<POItem> items, OnItemRemovedListener removeListener) {
        this.context = context;
        this.items = items;
        this.removeListener = removeListener;
    }

    public POItemAdapter(Context context, List<POItem> items, OnItemRemovedListener removeListener, Runnable onQtyChangedTask) {
        this.context = context;
        this.items = items;
        this.removeListener = removeListener;
        this.onQtyChangedTask = onQtyChangedTask;
    }

    public void setReceiveMode(boolean receiveMode) {
        this.receiveMode = receiveMode;
        this.viewOnlyMode = false;
        notifyDataSetChanged();
    }

    public void setViewOnlyMode(boolean viewOnlyMode) {
        this.viewOnlyMode = viewOnlyMode;
        this.receiveMode = false;
        notifyDataSetChanged();
    }

    public Map<Integer, Integer> getNewlyReceivedMap() {
        return newlyReceivedMap;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_po_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        POItem item = items.get(position);
        holder.tvName.setText(item.getProductName());

        // Attach Delete Listener for all modes where the button is visible
        if (holder.btnDelete != null) {
            holder.btnDelete.setOnClickListener(v -> {
                if (removeListener != null) removeListener.onItemRemoved(holder.getAdapterPosition());
            });
        }

        if (viewOnlyMode) {
            // ========================================================
            // VIEW ONLY MODE (Completed / Cancelled POs)
            // ========================================================
            holder.tvPrice.setText(String.format(Locale.getDefault(), "₱%.2f / %s", item.getUnitPrice(), item.getUnit()));
            holder.tvStatus.setText(String.format(Locale.getDefault(), "Ordered: %d (Rcvd: %d)", item.getQuantity(), item.getReceivedQuantity()));
            holder.tvStatus.setVisibility(View.VISIBLE);
            holder.tvStatus.setTextColor(context.getResources().getColor(R.color.successGreen, null));
            holder.etQty.setVisibility(View.GONE);
            if (holder.btnDelete != null) holder.btnDelete.setVisibility(View.VISIBLE);

        } else if (receiveMode) {
            // ========================================================
            // RECEIVE MODE (Pending / Partial POs)
            // ========================================================
            holder.tvPrice.setText(String.format(Locale.getDefault(), "₱%.2f / %s", item.getUnitPrice(), item.getUnit()));
            holder.tvStatus.setText(String.format(Locale.getDefault(), "Ordered: %d (Rcvd: %d)", item.getQuantity(), item.getReceivedQuantity()));
            holder.tvStatus.setVisibility(View.VISIBLE);

            // FIX: Make Delete Button Visible in Receive Mode
            if (holder.btnDelete != null) holder.btnDelete.setVisibility(View.VISIBLE);

            int remaining = item.getQuantity() - item.getReceivedQuantity();

            if (remaining > 0) {
                holder.etQty.setVisibility(View.VISIBLE);

                if (holder.textWatcher != null) holder.etQty.removeTextChangedListener(holder.textWatcher);

                if (newlyReceivedMap.containsKey(position)) {
                    holder.etQty.setText(String.valueOf(newlyReceivedMap.get(position)));
                } else {
                    holder.etQty.setText("");
                }

                holder.textWatcher = new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                    @Override
                    public void afterTextChanged(Editable s) {
                        if (s == null) return;
                        String input = s.toString().trim();
                        if (input.isEmpty()) {
                            newlyReceivedMap.remove(holder.getAdapterPosition());
                            return;
                        }
                        try {
                            int qty = Integer.parseInt(input);
                            if (qty > remaining) {
                                Toast.makeText(context, "Limit reached! Only " + remaining + " remaining.", Toast.LENGTH_SHORT).show();
                                holder.etQty.setText(String.valueOf(remaining));
                                holder.etQty.setSelection(String.valueOf(remaining).length());
                                newlyReceivedMap.put(holder.getAdapterPosition(), remaining);
                            } else if (qty < 0) {
                                holder.etQty.setText("0");
                                newlyReceivedMap.put(holder.getAdapterPosition(), 0);
                            } else {
                                newlyReceivedMap.put(holder.getAdapterPosition(), qty);
                            }
                        } catch (NumberFormatException e) {
                            newlyReceivedMap.remove(holder.getAdapterPosition());
                        }
                    }
                };
                holder.etQty.addTextChangedListener(holder.textWatcher);
            } else {
                holder.etQty.setVisibility(View.GONE);
                if (holder.btnDelete != null) holder.btnDelete.setVisibility(View.GONE); // Hide delete if item is fully received
                holder.tvStatus.setText(String.format(Locale.getDefault(), "Ordered: %d (Rcvd: %d) - COMPLETED", item.getQuantity(), item.getReceivedQuantity()));
                holder.tvStatus.setTextColor(context.getResources().getColor(R.color.successGreen, null));
            }

        } else {
            // ========================================================
            // CART MODE (Create PO Screen)
            // ========================================================
            holder.tvPrice.setText(String.format(Locale.getDefault(), "₱%.2f", item.getSubtotal()));
            holder.tvStatus.setVisibility(View.GONE);
            holder.etQty.setVisibility(View.VISIBLE);

            if (holder.btnDelete != null) holder.btnDelete.setVisibility(View.VISIBLE);

            if (holder.textWatcher != null) holder.etQty.removeTextChangedListener(holder.textWatcher);
            holder.etQty.setText(String.valueOf(item.getQuantity()));

            holder.textWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(Editable s) {
                    if (s == null) return;
                    String input = s.toString().trim();
                    if (input.isEmpty()) return;
                    try {
                        int newQty = Integer.parseInt(input);
                        if (newQty > 0) {
                            item.setQuantity(newQty);
                            holder.tvPrice.setText(String.format(Locale.getDefault(), "₱%.2f", item.getSubtotal()));
                            if (onQtyChangedTask != null) onQtyChangedTask.run();
                        }
                    } catch (Exception ignored) {}
                }
            };
            holder.etQty.addTextChangedListener(holder.textWatcher);
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvPrice, tvStatus;
        EditText etQty;
        ImageButton btnDelete;
        TextWatcher textWatcher;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvItemName);
            tvPrice = itemView.findViewById(R.id.tvItemPrice);
            tvStatus = itemView.findViewById(R.id.tvItemStatus);
            etQty = itemView.findViewById(R.id.etReceiveQty);
            btnDelete = itemView.findViewById(R.id.btnDeleteItem);
        }
    }
}