package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.recyclerview.widget.RecyclerView;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class ArchivedProductAdapter extends RecyclerView.Adapter<ArchivedProductAdapter.Holder> {
    public interface Listener {
        void onRestore(String filename);
        void onPermanentDelete(String filename);
    }
    private final Context ctx;
    private final List<String> files = new ArrayList<>();
    private final Listener listener;
    public ArchivedProductAdapter(Context ctx, Listener listener) {
        this.ctx = ctx;
        this.listener = listener;
    }
    public void setFiles(List<String> list) {
        files.clear();
        if (list != null) files.addAll(list);
        notifyDataSetChanged();
    }
    @Override
    public Holder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_archived_product, parent, false);
        return new Holder(v);
    }
    @Override
    public void onBindViewHolder(Holder holder, int position) {
        String fname = files.get(position);
        holder.bind(fname);
    }
    @Override
    public int getItemCount() {
        return files.size();
    }
    public class Holder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvSub;
        Button btnRestore;
        Button btnDelete;
        String filename;
        Holder(View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tv_item_name);
            tvSub = itemView.findViewById(R.id.tv_item_sub);
            btnRestore = itemView.findViewById(R.id.btn_restore);
            btnDelete = itemView.findViewById(R.id.btn_delete_permanent);
            btnRestore.setOnClickListener(v -> {
                if (listener != null && filename != null) listener.onRestore(filename);
            });
            btnDelete.setOnClickListener(v -> {
                if (listener != null && filename != null) listener.onPermanentDelete(filename);
            });
        }
        void bind(String fname) {
            filename = fname;
            tvName.setText(fname);
            try {
                File dir = new File(ctx.getFilesDir(), "archives");
                File f = new File(dir, fname);
                if (f.exists()) {
                    StringBuilder sb = new StringBuilder();
                    BufferedReader br = new BufferedReader(new FileReader(f));
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                    br.close();
                    JSONObject o = new JSONObject(sb.toString());
                    String productName = o.optString("productName", "");
                    int qty = o.optInt("quantity", 0);
                    double price = o.optDouble("sellingPrice", 0.0);
                    String sub = "Qty: " + qty + "  Price: ₱" + String.format("%.2f", price);
                    if (productName != null && !productName.isEmpty()) {
                        tvName.setText(productName);
                        tvSub.setText(sub);
                    } else {
                        tvSub.setText(sub);
                    }
                } else {
                    tvSub.setText("Missing archive file");
                }
            } catch (Exception ex) {
                tvSub.setText("");
            }
        }
    }
}