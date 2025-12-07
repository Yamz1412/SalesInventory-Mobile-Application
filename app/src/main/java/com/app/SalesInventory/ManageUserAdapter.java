package com.app.SalesInventory;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_staff_account, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        AdminUserItem it = items.get(position);
        String name = it.getName() != null && !it.getName().isEmpty() ? it.getName() : it.getEmail();
        String email = it.getEmail() != null ? it.getEmail() : "";
        String role = it.getRole() != null ? it.getRole() : "Staff";

        holder.name.setText(name);
        holder.email.setText(email);
        holder.role.setText(role);
        holder.phone.setText("");

        if (listener != null) {
            holder.itemView.setOnClickListener(v -> listener.onPromote(it.getUid()));
        } else {
            holder.itemView.setOnClickListener(null);
        }
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
        final TextView phone;
        final TextView role;

        VH(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.tvStaffName);
            email = itemView.findViewById(R.id.tvStaffEmail);
            phone = itemView.findViewById(R.id.tvStaffPhone);
            role = itemView.findViewById(R.id.tvStaffRole);
        }
    }
}