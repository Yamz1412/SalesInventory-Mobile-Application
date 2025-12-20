package com.app.SalesInventory;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class StockAlertAdapter extends RecyclerView.Adapter<StockAlertAdapter.ViewHolder> {

    private List<StockAlert> alertList;
    private Context context;
    private SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault());

    public StockAlertAdapter(List<StockAlert> alertList, Context context) {
        this.alertList = alertList;
        this.context = context;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_stock_alert, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        StockAlert alert = alertList.get(position);
        holder.tvProductName.setText(alert.getProductName());
        holder.tvCategory.setText("Category: " + alert.getCategory());
        holder.tvAlertMessage.setText(alert.getAlertMessage());
        holder.tvCurrentQty.setText("Current: " + alert.getCurrentQuantity() + " units");
        String dateText = dateFormat.format(new Date(alert.getCreatedAt()));
        holder.tvAlertDate.setText(dateText);
        String type = alert.getAlertType();
        if ("CRITICAL".equals(type)) {
            holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.errorRed));
            holder.tvAlertType.setText("🔴 CRITICAL");
            holder.tvAlertType.setTextColor(context.getResources().getColor(R.color.white));
        } else if ("LOW".equals(type)) {
            holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.warningYellow));
            holder.tvAlertType.setText("🟡 LOW STOCK");
            holder.tvAlertType.setTextColor(context.getResources().getColor(R.color.textColorPrimary));
        } else if ("OVERSTOCK".equals(type)) {
            holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.successGreen));
            holder.tvAlertType.setText("🟢 OVERSTOCK");
            holder.tvAlertType.setTextColor(context.getResources().getColor(R.color.white));
        } else if ("EXPIRY_7_DAYS".equals(type)) {
            holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.warningYellow));
            holder.tvAlertType.setText("🟡 EXPIRY 7 DAYS");
            holder.tvAlertType.setTextColor(context.getResources().getColor(R.color.textColorPrimary));
        } else if ("EXPIRY_3_DAYS".equals(type)) {
            holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.warningYellow));
            holder.tvAlertType.setText("🟠 EXPIRY 3 DAYS");
            holder.tvAlertType.setTextColor(context.getResources().getColor(R.color.textColorPrimary));
        } else if ("EXPIRED".equals(type)) {
            holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.errorRed));
            holder.tvAlertType.setText("⚫ EXPIRED");
            holder.tvAlertType.setTextColor(context.getResources().getColor(R.color.white));
        } else {
            holder.itemView.setBackgroundColor(context.getResources().getColor(R.color.errorRed));
            holder.tvAlertType.setText(type);
            holder.tvAlertType.setTextColor(context.getResources().getColor(R.color.white));
        }
        holder.tvAlertMessage.setTextColor(context.getResources().getColor(R.color.white));
        holder.tvProductName.setTextColor(context.getResources().getColor(R.color.white));
        holder.tvCategory.setTextColor(context.getResources().getColor(R.color.white));
        holder.tvCurrentQty.setTextColor(context.getResources().getColor(R.color.white));
        holder.tvAlertDate.setTextColor(context.getResources().getColor(R.color.white));
        holder.itemView.setOnClickListener(v -> {
            String productId = alert.getProductId();
            if (productId == null || productId.isEmpty()) return;
            try {
                Class<?> cls = Class.forName("com.app.SalesInventory.ProductDetailsActivity");
                Intent i = new Intent(context, cls);
                i.putExtra("productId", productId);
                context.startActivity(i);
                return;
            } catch (Exception ignored) {}
            try {
                Class<?> cls = Class.forName("com.app.SalesInventory.AddProductActivity");
                Intent i = new Intent(context, cls);
                i.putExtra("productId", productId);
                context.startActivity(i);
                return;
            } catch (Exception ignored) {}
            try {
                Class<?> cls = Class.forName("com.app.SalesInventory.Inventory");
                Intent i = new Intent(context, cls);
                i.putExtra("productId", productId);
                i.putExtra("readonly", false);
                context.startActivity(i);
                return;
            } catch (Exception ignored) {}
        });
    }

    @Override
    public int getItemCount() {
        return alertList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvAlertType, tvProductName, tvCategory, tvAlertMessage, tvCurrentQty, tvAlertDate;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAlertType = itemView.findViewById(R.id.tvAlertType);
            tvProductName = itemView.findViewById(R.id.tvProductName);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvAlertMessage = itemView.findViewById(R.id.tvAlertMessage);
            tvCurrentQty = itemView.findViewById(R.id.tvCurrentQty);
            tvAlertDate = itemView.findViewById(R.id.tvAlertDate);
        }
    }
}