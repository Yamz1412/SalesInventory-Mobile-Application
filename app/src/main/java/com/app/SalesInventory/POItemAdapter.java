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
        notifyDataSetChanged();
    }

    public void setViewOnlyMode(boolean viewOnlyMode) {
        this.viewOnlyMode = viewOnlyMode;
        notifyDataSetChanged();
    }

    public Map<Integer, Double> getNewlyReceivedMap() {
        return newlyReceivedMap;
    }

    public Map<Integer, Long> getNewlyReceivedExpiryMap() {
        return newlyReceivedExpiryMap;
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

        String unit = item.getUnit() != null ? item.getUnit() : "pcs";
        holder.tvName.setText(item.getProductName() + " (" + unit + ")");

        // FORMAT FIX: Safely applied comma formatting to PO Item Subtotal
        holder.tvPrice.setText(String.format(Locale.US, "₱%,.2f", item.getSubtotal()));

        if (viewOnlyMode) {
            holder.btnDelete.setVisibility(View.GONE);
            holder.etQty.setEnabled(false);
            holder.etQty.setText(String.valueOf(item.getQuantity()));
            holder.tvExpiryDate.setVisibility(View.GONE);
        } else if (receiveMode) {
            holder.btnDelete.setVisibility(View.GONE);
            holder.etQty.setEnabled(true);

            Double currentlyInputted = newlyReceivedMap.get(position);
            holder.etQty.setText(currentlyInputted != null ? String.valueOf(currentlyInputted) : "");
            holder.etQty.setHint("Max: " + (item.getQuantity() - item.getReceivedQuantity()));

            holder.tvExpiryDate.setVisibility(View.VISIBLE);
            Long currentExpiry = newlyReceivedExpiryMap.get(position);
            if (currentExpiry != null && currentExpiry > 0) {
                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy", Locale.US);
                holder.tvExpiryDate.setText("Expiry: " + sdf.format(new java.util.Date(currentExpiry)));
            } else {
                holder.tvExpiryDate.setText("Set Expiry (Opt)");
            }

            holder.tvExpiryDate.setOnClickListener(v -> {
                java.util.Calendar cal = java.util.Calendar.getInstance();
                if (currentExpiry != null && currentExpiry > 0) cal.setTimeInMillis(currentExpiry);

                new android.app.DatePickerDialog(context, (view, year, month, dayOfMonth) -> {
                    java.util.Calendar newCal = java.util.Calendar.getInstance();
                    newCal.set(year, month, dayOfMonth, 23, 59, 59);
                    newlyReceivedExpiryMap.put(position, newCal.getTimeInMillis());
                    notifyItemChanged(position);
                }, cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH), cal.get(java.util.Calendar.DAY_OF_MONTH)).show();
            });

            if (holder.textWatcher != null) holder.etQty.removeTextChangedListener(holder.textWatcher);
            holder.textWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (holder.getAdapterPosition() == RecyclerView.NO_POSITION) return;
                    int currentPos = holder.getAdapterPosition();
                    String input = s.toString();
                    if (input.isEmpty()) {
                        newlyReceivedMap.remove(currentPos);
                    } else {
                        try {
                            double qty = Double.parseDouble(input);
                            double maxAllowed = items.get(currentPos).getQuantity() - items.get(currentPos).getReceivedQuantity();
                            if (qty > maxAllowed) {
                                holder.etQty.setError("Cannot exceed " + maxAllowed);
                                qty = maxAllowed;
                            } else {
                                holder.etQty.setError(null);
                            }
                            newlyReceivedMap.put(currentPos, qty);
                        } catch (NumberFormatException ignored) {}
                    }
                }
            };
            holder.etQty.addTextChangedListener(holder.textWatcher);
        } else {
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.etQty.setEnabled(true);

            String qtyStr = (item.getQuantity() % 1 == 0) ? String.valueOf((long)item.getQuantity()) : String.valueOf(item.getQuantity());
            holder.etQty.setText(qtyStr);
            holder.tvExpiryDate.setVisibility(View.GONE);

            holder.btnDelete.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION && removeListener != null) {
                    removeListener.onItemRemoved(pos);
                }
            });

            if (holder.textWatcher != null) holder.etQty.removeTextChangedListener(holder.textWatcher);
            holder.textWatcher = new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override public void afterTextChanged(Editable s) {
                    if (holder.getAdapterPosition() == RecyclerView.NO_POSITION) return;
                    int currentPos = holder.getAdapterPosition();
                    String input = s.toString();
                    if (!input.isEmpty()) {
                        try {
                            double newQty = Double.parseDouble(input);
                            if (newQty >= 0) {
                                items.get(currentPos).setQuantity(newQty);
                                // FORMAT FIX: Safely applied comma formatting on real-time text edits
                                holder.tvPrice.setText(String.format(Locale.US, "₱%,.2f", items.get(currentPos).getSubtotal()));
                                if (onQtyChangedTask != null) {
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