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

public class ManageUserAdapter extends RecyclerView.Adapter<ManageUserAdapter.VH> {
    public interface OnManageActionListener {
        void onPromote(String uid);
    }

    private List<AdminUserItem> items;
    private final OnManageActionListener listener;

    public ManageUserAdapter(List<AdminUserItem> items, OnManageActionListener listener) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_manage_user, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AdminUserItem it = items.get(position);
        holder.name.setText(it.getName() != null && !it.getName().isEmpty() ? it.getName() : it.getEmail());
        holder.email.setText(it.getEmail() != null ? it.getEmail() : "");
        holder.status.setText(it.isApproved() ? "Approved" : "Pending");
        holder.promote.setOnClickListener(v -> {
            if (listener != null) listener.onPromote(it.getUid());
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public void update(List<AdminUserItem> newItems) {
        if (newItems == null) {
            this.items = new ArrayList<>();
        } else {
            this.items = new ArrayList<>(newItems);
        }
        notifyDataSetChanged();
    }

    static class VH extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView email;
        final TextView status;
        final Button promote;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvManageName);
            email = itemView.findViewById(R.id.tvManageEmail);
            status = itemView.findViewById(R.id.tvManageStatus);
            promote = itemView.findViewById(R.id.btnPromote);
        }
    }
}