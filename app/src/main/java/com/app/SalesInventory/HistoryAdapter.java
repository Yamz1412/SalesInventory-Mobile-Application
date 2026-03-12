package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    private Context context;
    private List<Sales> salesList;
    private OnVoidClickListener voidClickListener;

    public interface OnVoidClickListener {
        void onVoidClick(Sales sale);
    }

    public HistoryAdapter(Context context, List<Sales> salesList, OnVoidClickListener voidClickListener) {
        this.context = context;
        this.salesList = salesList;
        this.voidClickListener = voidClickListener;
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_sale_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        Sales sale = salesList.get(position);

        holder.tvProductName.setText(sale.getProductName());

        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault());
        String dateStr = sale.getTimestamp() > 0 ? sdf.format(new Date(sale.getTimestamp())) : "Unknown Date";
        holder.tvDateAndPay.setText(dateStr + " | " + sale.getPaymentMethod());

        holder.tvTotal.setText(String.format(Locale.getDefault(), "₱%.2f (Qty: %d)", sale.getTotalPrice(), sale.getQuantity()));

        if ("VOIDED".equals(sale.getStatus())) {
            holder.btnVoid.setVisibility(View.GONE);
            holder.tvVoidedLabel.setVisibility(View.VISIBLE);
        } else {
            holder.btnVoid.setVisibility(View.VISIBLE);
            holder.tvVoidedLabel.setVisibility(View.GONE);
            holder.btnVoid.setOnClickListener(v -> voidClickListener.onVoidClick(sale));
        }
    }

    @Override
    public int getItemCount() {
        return salesList.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {
        TextView tvProductName, tvDateAndPay, tvTotal, tvVoidedLabel;
        Button btnVoid;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvDateAndPay = itemView.findViewById(R.id.tvDateAndPay);
            tvTotal = itemView.findViewById(R.id.tvTotal);
            tvVoidedLabel = itemView.findViewById(R.id.tvVoidedLabel);
            btnVoid = itemView.findViewById(R.id.btnVoid);
        }
    }
}