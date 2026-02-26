package com.app.SalesInventory;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class StaffListAdapter extends RecyclerView.Adapter<StaffListAdapter.Holder> {

    private final List<Object> items = new ArrayList<>();
    private final Context ctx;
    private int selected = RecyclerView.NO_POSITION;
    private OnItemLongClickListener longClickListener;

    public interface OnItemLongClickListener {
        boolean onItemLongClick(int position, AdminUserItem item);
    }

    public StaffListAdapter(Context ctx, List<Object> initial) {
        this.ctx = ctx;
        if (initial != null) items.addAll(initial);
    }

    public void setItems(List<Object> list) {
        items.clear();
        if (list != null) items.addAll(list);
        selected = RecyclerView.NO_POSITION;
        notifyDataSetChanged();
    }

    public Object getSelected() {
        if (selected == RecyclerView.NO_POSITION) return null;
        if (selected < 0 || selected >= items.size()) return null;
        return items.get(selected);
    }

    public void setOnItemLongClickListener(OnItemLongClickListener listener) {
        this.longClickListener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(ctx).inflate(R.layout.item_staff_account, parent, false);
        return new Holder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Object ai = items.get(position);
        String name = extract(ai, "name", "getName");
        String email = extract(ai, "email", "getEmail");
        String phone = extract(ai, "phone", "getPhone");
        String role = extract(ai, "role", "getRole");

        holder.tvName.setText(name != null && !name.isEmpty() ? name : "Unnamed");
        holder.tvEmail.setText(email != null ? email : "");
        holder.tvPhone.setText(phone != null ? phone : "");
        holder.tvRole.setText(role != null ? role : "Staff");
        holder.itemView.setSelected(position == selected);

        holder.itemView.setOnClickListener(v -> {
            int old = selected;
            selected = holder.getAdapterPosition();
            notifyItemChanged(old);
            notifyItemChanged(selected);
        });

        holder.itemView.setOnLongClickListener(v -> {
            int old = selected;
            selected = holder.getAdapterPosition();
            notifyItemChanged(old);
            notifyItemChanged(selected);

            if (ai instanceof AdminUserItem && longClickListener != null) {
                AdminUserItem staff = (AdminUserItem) ai;
                return longClickListener.onItemLongClick(position, staff);
            }
            return false;
        });
    }

    private String extract(Object obj, String fieldName, String getterName) {
        if (obj == null) return null;
        try {
            Method m = obj.getClass().getMethod(getterName);
            Object r = m.invoke(obj);
            return r == null ? null : String.valueOf(r);
        } catch (Exception ignored) {}
        try {
            Field f = obj.getClass().getDeclaredField(fieldName);
            f.setAccessible(true);
            Object r = f.get(obj);
            return r == null ? null : String.valueOf(r);
        } catch (Exception ignored) {}
        return null;
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvName;
        TextView tvEmail;
        TextView tvPhone;
        TextView tvRole;
        Holder(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tvStaffName);
            tvEmail = v.findViewById(R.id.tvStaffEmail);
            tvPhone = v.findViewById(R.id.tvStaffPhone);
            tvRole = v.findViewById(R.id.tvStaffRole);
        }
    }
}