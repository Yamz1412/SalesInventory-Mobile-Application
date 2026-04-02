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
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class POItemAdapter extends RecyclerView.Adapter<POItemAdapter.ViewHolder> {

    private Context context;
    private List<POItem> items;
    private Map<Integer, Double> newlyReceivedMap = new HashMap<>();
    private Map<Integer, Long> newlyReceivedExpiryMap = new HashMap<>();

    private boolean receiveMode = false;
    private boolean viewOnlyMode = false;
    private OnItemRemovedListener removeListener;
    private Runnable onQtyChangedTask;

    public interface OnItemRemovedListener {
        void onItemRemoved(int position);
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

    public Map<Integer, Double> getNewlyReceivedMap() {
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

        // 1. ALWAYS remove the old text watcher before doing anything else
        if (holder.textWatcher != null) {
            holder.etQty.removeTextChangedListener(holder.textWatcher);
        }

        // Reset listener
        holder.btnDelete.setOnClickListener(null);

        if (receiveMode) {
            holder.btnDelete.setVisibility(View.GONE);
            holder.tvPrice.setText(String.format(Locale.getDefault(), "₱%.2f / %s", item.getUnitPrice(), item.getUnit()));

            double remaining = item.getQuantity() - item.getReceivedQuantity();

            if (remaining > 0) {
                holder.etQty.setVisibility(View.VISIBLE);
                holder.etQty.setText(newlyReceivedMap.containsKey(holder.getAdapterPosition()) ?
                        String.valueOf(newlyReceivedMap.get(holder.getAdapterPosition())) : "");

                holder.textWatcher = new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override public void afterTextChanged(Editable s) {
                        String input = s.toString().trim();
                        if (input.isEmpty()) { newlyReceivedMap.remove(holder.getAdapterPosition()); return; }
                        try {
                            double qty = Double.parseDouble(input);
                            if (qty > remaining) {
                                holder.etQty.setText(String.valueOf(remaining));
                                holder.etQty.setSelection(holder.etQty.getText().length());
                                newlyReceivedMap.put(holder.getAdapterPosition(), remaining);
                            } else {
                                newlyReceivedMap.put(holder.getAdapterPosition(), qty);
                            }
                        } catch (Exception e) { newlyReceivedMap.remove(holder.getAdapterPosition()); }
                    }
                };
                holder.etQty.addTextChangedListener(holder.textWatcher);
            } else {
                holder.etQty.setVisibility(View.GONE);
            }
        } else {
            // --- CART MODE LOGIC ---
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.tvPrice.setText(String.format(Locale.getDefault(), "₱%.2f", item.getSubtotal()));

            // Set text WITHOUT triggering listeners
            if (item.getQuantity() == (long) item.getQuantity()) {
                holder.etQty.setText(String.format(Locale.getDefault(), "%d", (long) item.getQuantity()));
            } else {
                holder.etQty.setText(String.valueOf(item.getQuantity()));
            }

            // Handle Item Deletion
            holder.btnDelete.setOnClickListener(v -> {
                int currentPos = holder.getAdapterPosition();
                if (currentPos != RecyclerView.NO_POSITION && removeListener != null) {
                    removeListener.onItemRemoved(currentPos);
                    notifyItemRemoved(currentPos);
                    notifyItemRangeChanged(currentPos, items.size());
                }
            });

            // Handle Quantity Updates
            holder.textWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    int currentPos = holder.getAdapterPosition();
                    if (currentPos == RecyclerView.NO_POSITION) return;

                    String input = s.toString().trim();
                    if (!input.isEmpty()) {
                        try {
                            double newQty = Double.parseDouble(input);
                            if (newQty >= 0) {
                                items.get(currentPos).setQuantity(newQty);
                                holder.tvPrice.setText(String.format(Locale.getDefault(), "₱%.2f", items.get(currentPos).getSubtotal()));
                                if (onQtyChangedTask != null) {
                                    // Trigger the activity calculation without refreshing the whole list
                                    holder.itemView.post(() -> onQtyChangedTask.run());
                                }
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }
            };
            holder.etQty.addTextChangedListener(holder.textWatcher);
        }
    }

    @Override public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvPrice, tvExpiryDate;
        EditText etQty;
        ImageButton btnDelete;
        TextWatcher textWatcher;

        ViewHolder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvItemName);
            tvPrice = itemView.findViewById(R.id.tvItemPrice);
            etQty = itemView.findViewById(R.id.etReceiveQty);
            btnDelete = itemView.findViewById(R.id.btnDeleteItem);
            tvExpiryDate = itemView.findViewById(R.id.tvExpiryDate);
        }
    }
}