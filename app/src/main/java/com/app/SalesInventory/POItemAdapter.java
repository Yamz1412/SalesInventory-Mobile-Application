package com.app.SalesInventory;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class POItemAdapter extends RecyclerView.Adapter<POItemAdapter.ItemViewHolder> {

    private Context context;
    private List<POItem> itemsList;
    private OnItemRemoveListener removeListener;
    private OnItemChangeListener changeListener;

    private boolean isReceiveMode = false;
    private Map<Integer, Integer> newlyReceivedMap = new HashMap<>();

    public interface OnItemRemoveListener {
        void onRemove(int position);
    }

    public interface OnItemChangeListener {
        void onChange();
    }

    // FIXED: 3-argument constructor to prevent crashes in older files
    public POItemAdapter(Context context, List<POItem> itemsList, OnItemRemoveListener removeListener) {
        this.context = context;
        this.itemsList = itemsList;
        this.removeListener = removeListener;
        this.changeListener = null;
    }

    // 4-argument constructor
    public POItemAdapter(Context context, List<POItem> itemsList, OnItemRemoveListener removeListener, OnItemChangeListener changeListener) {
        this.context = context;
        this.itemsList = itemsList;
        this.removeListener = removeListener;
        this.changeListener = changeListener;
    }

    public void setReceiveMode(boolean receiveMode) {
        this.isReceiveMode = receiveMode;
        this.newlyReceivedMap.clear();
        notifyDataSetChanged();
    }

    public Map<Integer, Integer> getNewlyReceivedMap() {
        return newlyReceivedMap;
    }

    @NonNull
    @Override
    public ItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_po_item, parent, false);
        return new ItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ItemViewHolder holder, int position) {
        POItem item = itemsList.get(position);

        holder.tvProductName.setText(item.getProductName() != null ? item.getProductName() : "Unknown Item");
        holder.tvQuantity.setText(String.format(Locale.getDefault(), "Ordered: %d", item.getQuantity()));

        if (holder.tvReceivedQty != null) {
            holder.tvReceivedQty.setText(String.format(Locale.getDefault(), "Received: %d", item.getReceivedQuantity()));
        }

        holder.tvUnitPrice.setText(String.format(Locale.getDefault(), "₱%.2f", item.getUnitPrice()));
        holder.tvSubtotal.setText(String.format(Locale.getDefault(), "Total: ₱%.2f", item.getSubtotal()));

        if (removeListener == null || holder.btnRemove == null) {
            if (holder.btnRemove != null) holder.btnRemove.setVisibility(View.GONE);
        } else {
            holder.btnRemove.setVisibility(View.VISIBLE);
            holder.btnRemove.setOnClickListener(v -> removeListener.onRemove(position));
        }

        if (holder.layoutReceiveInput != null) {
            if (isReceiveMode && item.getReceivedQuantity() < item.getQuantity()) {
                holder.layoutReceiveInput.setVisibility(View.VISIBLE);

                if (holder.textWatcher != null) holder.etReceiveNow.removeTextChangedListener(holder.textWatcher);
                holder.etReceiveNow.setText(newlyReceivedMap.containsKey(position) ? String.valueOf(newlyReceivedMap.get(position)) : "");

                holder.textWatcher = new TextWatcher() {
                    @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                    @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                    @Override
                    public void afterTextChanged(Editable s) {
                        if (s.toString().trim().isEmpty()) {
                            newlyReceivedMap.remove(holder.getAdapterPosition());
                        } else {
                            try {
                                int val = Integer.parseInt(s.toString().trim());
                                int maxAllowed = item.getQuantity() - item.getReceivedQuantity();
                                if (val > maxAllowed) {
                                    holder.etReceiveNow.setError("Max: " + maxAllowed);
                                    newlyReceivedMap.put(holder.getAdapterPosition(), maxAllowed);
                                } else {
                                    newlyReceivedMap.put(holder.getAdapterPosition(), val);
                                }
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                };
                holder.etReceiveNow.addTextChangedListener(holder.textWatcher);
            } else {
                holder.layoutReceiveInput.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public int getItemCount() {
        return itemsList.size();
    }

    static class ItemViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvQuantity, tvReceivedQty, tvUnitPrice, tvSubtotal;
        ImageButton btnRemove;
        LinearLayout layoutReceiveInput;
        EditText etReceiveNow;
        TextWatcher textWatcher;

        public ItemViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvQuantity = itemView.findViewById(R.id.tvQuantity);
            tvReceivedQty = itemView.findViewById(R.id.tvReceivedQty);
            tvUnitPrice = itemView.findViewById(R.id.tvUnitPrice);
            tvSubtotal = itemView.findViewById(R.id.tvSubtotal);
            btnRemove = itemView.findViewById(R.id.btnRemove);
            layoutReceiveInput = itemView.findViewById(R.id.layoutReceiveInput);
            etReceiveNow = itemView.findViewById(R.id.etReceiveNow);
        }
    }
}