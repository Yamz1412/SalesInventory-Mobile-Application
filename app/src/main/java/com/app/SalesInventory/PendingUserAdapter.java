package com.app.SalesInventory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

public class PendingUserAdapter extends RecyclerView.Adapter<PendingUserAdapter.VH> {
    public interface OnPendingActionListener {
        void onApprove(String uid);
        void onCancel(String uid);
    }

    private List<AdminUserItem> items;
    private OnPendingActionListener listener;

    public PendingUserAdapter(List<AdminUserItem> items, OnPendingActionListener listener) {
        this.items = items != null ? items : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_pending_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AdminUserItem it = items.get(position);
        holder.name.setText(it.getName() != null && !it.getName().isEmpty() ? it.getName() : it.getEmail());
        holder.email.setText(it.getEmail() != null ? it.getEmail() : "");
        holder.status.setText(it.isApproved() ? "Approved" : "Pending");
        holder.approve.setOnClickListener(v -> {
            if (listener != null) listener.onApprove(it.getUid());
        });
        holder.cancel.setOnClickListener(v -> {
            if (listener != null) listener.onCancel(it.getUid());
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public void update(List<AdminUserItem> newItems) {
        if (items == null) items = new ArrayList<>();
        items.clear();
        if (newItems != null) items.addAll(newItems);
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        TextView name;
        TextView email;
        TextView status;
        Button approve;
        Button cancel;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvPendingName);
            email = itemView.findViewById(R.id.tvPendingEmail);
            status = itemView.findViewById(R.id.tvPendingStatus);
            approve = itemView.findViewById(R.id.btnApprove);
            cancel = itemView.findViewById(R.id.btnCancel);
        }
    }
}