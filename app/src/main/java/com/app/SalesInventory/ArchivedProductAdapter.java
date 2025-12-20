package com.app.SalesInventory;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ArchivedProductAdapter extends RecyclerView.Adapter<ArchivedProductAdapter.VH> {
    public interface Listener {
        void onRestore(String filename);
        void onPermanentDelete(String filename);
    }

    private final Context ctx;
    private final Listener listener;
    private final List<Entry> entries = new ArrayList<>();
    private static final String TAG = "ArchivedAdapter";
    private final ExecutorService executor;
    private final Handler mainHandler;

    public ArchivedProductAdapter(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
        this.executor = Executors.newSingleThreadExecutor();
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void setFiles(List<String> files) {
        entries.clear();
        File dir = new File(ctx.getFilesDir(), "archives");
        for (String filePathOrName : files) {
            Entry e = new Entry();
            String filename = new File(filePathOrName).getName();
            e.raw = filename;
            e.displayName = filename;
            e.isPO = filename.startsWith("po_");
            File file = new File(dir, filename);
            e.exists = file.exists();
            e.filepath = file.getAbsolutePath();
            e.title = e.displayName;
            e.subtitle = "";
            e.meta = "Qty: 0  Price: " + formatCurrency(0.0);
            entries.add(e);
        }
        notifyDataSetChanged();
        parseExistingFilesAsync();
    }

    private void parseExistingFilesAsync() {
        File dir = new File(ctx.getFilesDir(), "archives");
        executor.submit(() -> {
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                if (!e.exists) continue;
                File file = new File(dir, e.displayName);
                if (!file.exists()) continue;
                try {
                    StringBuilder sb = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                        String line;
                        while ((line = br.readLine()) != null) sb.append(line);
                    }
                    JSONObject o = new JSONObject(sb.toString());
                    if (e.isPO) {
                        String title = o.optString("poNumber", o.optString("poId", e.displayName));
                        String supplier = o.optString("supplierName", o.optString("supplier", o.optString("supplier_name", "")));
                        double total = o.optDouble("totalAmount", o.optDouble("total", 0.0));
                        int qtySum = 0;
                        if (o.has("items")) {
                            JSONArray arr = o.optJSONArray("items");
                            if (arr != null) {
                                for (int j = 0; j < arr.length(); j++) {
                                    JSONObject it = arr.optJSONObject(j);
                                    if (it == null) continue;
                                    int q = it.has("quantity") ? it.optInt("quantity", 0) : it.optInt("qty", 0);
                                    qtySum += q;
                                }
                            } else {
                                Object itemsObj = o.opt("items");
                                if (itemsObj instanceof JSONObject) {
                                    JSONObject itemsMap = (JSONObject) itemsObj;
                                    JSONArray names = itemsMap.names();
                                    if (names != null) {
                                        for (int j = 0; j < names.length(); j++) {
                                            JSONObject it = itemsMap.optJSONObject(names.optString(j));
                                            if (it == null) continue;
                                            int q = it.has("quantity") ? it.optInt("quantity", 0) : it.optInt("qty", 0);
                                            qtySum += q;
                                        }
                                    }
                                }
                            }
                        }
                        e.title = title;
                        e.subtitle = supplier;
                        e.meta = "Qty: " + qtySum + "  Price: " + formatCurrency(total);
                    } else {
                        String name = o.optString("productName", o.optString("name", e.displayName));
                        String supplier = o.optString("supplier", "");
                        int qty = o.has("quantity") ? o.optInt("quantity", 0) : o.optInt("qty", 0);
                        double price = o.optDouble("sellingPrice", o.optDouble("unitPrice", o.optDouble("costPrice", 0.0)));
                        e.title = name;
                        e.subtitle = supplier;
                        e.meta = "Qty: " + qty + "  Price: " + formatCurrency(price);
                    }
                    final int idx = i;
                    mainHandler.post(() -> {
                        if (idx >= 0 && idx < entries.size()) notifyItemChanged(idx);
                    });
                } catch (Exception ex) {
                    Log.e(TAG, "Error reading archive file: " + e.displayName, ex);
                    final int idx = i;
                    mainHandler.post(() -> {
                        if (idx >= 0 && idx < entries.size()) {
                            entries.get(idx).title = entries.get(idx).displayName;
                            entries.get(idx).subtitle = "";
                            entries.get(idx).meta = "Qty: 0  Price: " + formatCurrency(0.0);
                            notifyItemChanged(idx);
                        }
                    });
                }
            }
        });
    }

    private String formatCurrency(double v) {
        try {
            NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("en", "PH"));
            return nf.format(v);
        } catch (Exception ignored) {
            return "₱" + String.format(Locale.getDefault(), "%.2f", v);
        }
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_archived_product, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        Entry e = entries.get(position);
        holder.tvName.setText(e.title == null ? e.displayName : e.title);
        holder.tvSupplier.setText(e.subtitle == null ? "" : e.subtitle);
        holder.tvMeta.setText(e.meta == null ? "" : e.meta);
        holder.btnRestore.setEnabled(true);
        holder.btnDelete.setEnabled(true);
        holder.btnRestore.setOnClickListener(v -> {
            holder.btnRestore.setEnabled(false);
            if (e.isPO) {
                PurchaseOrderRepository.getInstance().restorePOArchive(ctx, e.displayName, new PurchaseOrderRepository.OnRestorePOListener() {
                    @Override
                    public void onRestored() {
                        if (ctx instanceof Activity) {
                            ((Activity) ctx).runOnUiThread(() -> {
                                Toast.makeText(ctx, "PO restored successfully", Toast.LENGTH_SHORT).show();
                                int pos = holder.getAdapterPosition();
                                if (pos != RecyclerView.NO_POSITION) {
                                    entries.remove(pos);
                                    notifyItemRemoved(pos);
                                    notifyItemRangeChanged(pos, entries.size());
                                }
                                if (listener != null) listener.onRestore(e.displayName);
                            });
                        }
                    }
                    @Override
                    public void onError(String error) {
                        if (ctx instanceof Activity) {
                            ((Activity) ctx).runOnUiThread(() -> {
                                holder.btnRestore.setEnabled(true);
                                Toast.makeText(ctx, "Restore failed: " + error, Toast.LENGTH_LONG).show();
                            });
                        }
                    }
                });
            } else {
                ProductRepository.getInstance((Application) ctx.getApplicationContext()).restoreArchived(e.displayName, new ProductRepository.OnProductRestoreListener() {
                    @Override
                    public void onProductRestored() {
                        if (ctx instanceof Activity) {
                            ((Activity) ctx).runOnUiThread(() -> {
                                Toast.makeText(ctx, "Product restored successfully", Toast.LENGTH_SHORT).show();
                                int pos = holder.getAdapterPosition();
                                if (pos != RecyclerView.NO_POSITION) {
                                    entries.remove(pos);
                                    notifyItemRemoved(pos);
                                    notifyItemRangeChanged(pos, entries.size());
                                }
                                if (listener != null) listener.onRestore(e.displayName);
                            });
                        }
                    }
                    @Override
                    public void onError(String error) {
                        if (ctx instanceof Activity) {
                            ((Activity) ctx).runOnUiThread(() -> {
                                holder.btnRestore.setEnabled(true);
                                Toast.makeText(ctx, "Restore failed: " + error, Toast.LENGTH_LONG).show();
                            });
                        }
                    }
                });
            }
        });
        holder.btnDelete.setOnClickListener(v -> {
            holder.btnDelete.setEnabled(false);
            if (e.isPO) {
                PurchaseOrderRepository.getInstance().permanentlyDeletePOArchive(ctx, e.displayName, new PurchaseOrderRepository.OnArchiveListener() {
                    @Override
                    public void onArchived(String fname) {
                        if (ctx instanceof Activity) {
                            ((Activity) ctx).runOnUiThread(() -> {
                                Toast.makeText(ctx, "PO deleted permanently", Toast.LENGTH_SHORT).show();
                                int pos = holder.getAdapterPosition();
                                if (pos != RecyclerView.NO_POSITION) {
                                    entries.remove(pos);
                                    notifyItemRemoved(pos);
                                    notifyItemRangeChanged(pos, entries.size());
                                }
                                if (listener != null) listener.onPermanentDelete(e.displayName);
                            });
                        }
                    }
                    @Override
                    public void onError(String error) {
                        if (ctx instanceof Activity) {
                            ((Activity) ctx).runOnUiThread(() -> {
                                holder.btnDelete.setEnabled(true);
                                Toast.makeText(ctx, "Delete failed: " + error, Toast.LENGTH_LONG).show();
                            });
                        }
                    }
                });
            } else {
                ProductRepository.getInstance((Application) ctx.getApplicationContext()).permanentlyDeleteArchive(e.displayName, new ProductRepository.OnPermanentDeleteListener() {
                    @Override
                    public void onPermanentDeleted() {
                        if (ctx instanceof Activity) {
                            ((Activity) ctx).runOnUiThread(() -> {
                                Toast.makeText(ctx, "Product deleted permanently", Toast.LENGTH_SHORT).show();
                                int pos = holder.getAdapterPosition();
                                if (pos != RecyclerView.NO_POSITION) {
                                    entries.remove(pos);
                                    notifyItemRemoved(pos);
                                    notifyItemRangeChanged(pos, entries.size());
                                }
                                if (listener != null) listener.onPermanentDelete(e.displayName);
                            });
                        }
                    }
                    @Override
                    public void onError(String error) {
                        if (ctx instanceof Activity) {
                            ((Activity) ctx).runOnUiThread(() -> {
                                holder.btnDelete.setEnabled(true);
                                Toast.makeText(ctx, "Delete failed: " + error, Toast.LENGTH_LONG).show();
                            });
                        }
                    }
                });
            }
        });
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvSupplier;
        TextView tvMeta;
        Button btnRestore;
        Button btnDelete;
        VH(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tv_item_name);
            tvSupplier = v.findViewById(R.id.tv_item_supplier);
            tvMeta = v.findViewById(R.id.tv_item_meta);
            btnRestore = v.findViewById(R.id.btn_restore);
            btnDelete = v.findViewById(R.id.btn_delete_permanent);
        }
    }

    private static class Entry {
        String raw;
        String filepath;
        String displayName;
        boolean exists;
        boolean isPO;
        String title;
        String subtitle;
        String meta;
    }
}