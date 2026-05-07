package com.app.SalesInventory;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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

        String targetUid = extract(ai, "id", "getId");
        if (targetUid == null) targetUid = extract(ai, "uid", "getUid");

        // --- FETCH LAST LOGIN & ONLINE STATUS ---
        if (targetUid != null) {
            com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("UsersStatus").child(targetUid)
                    .addValueEventListener(new com.google.firebase.database.ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull com.google.firebase.database.DataSnapshot snapshot) {
                            String status = snapshot.child("status").getValue(String.class);
                            Long lastActive = snapshot.child("lastActive").getValue(Long.class);

                            // 1. Update Online/Offline Indicator
                            android.graphics.drawable.GradientDrawable gd = new android.graphics.drawable.GradientDrawable();
                            gd.setShape(android.graphics.drawable.GradientDrawable.OVAL);
                            if ("online".equals(status)) {
                                gd.setColor(android.graphics.Color.parseColor("#4CAF50")); // Green
                            } else {
                                gd.setColor(android.graphics.Color.parseColor("#F44336")); // Red
                            }
                            holder.statusIndicator.setBackground(gd);

                            // 2. Update Last Login Time
                            if (lastActive != null && lastActive > 0) {

                                java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy hh:mm a", java.util.Locale.getDefault());
                                holder.tvLastLogin.setText("Last Activity: " + sdf.format(new java.util.Date(lastActive)));
                            } else {
                                holder.tvLastLogin.setText("Last Activity: Never");
                            }
                        }
                        @Override public void onCancelled(@NonNull com.google.firebase.database.DatabaseError error) {}
                    });
        }

        holder.itemView.setOnClickListener(v -> {
            int old = selected;
            selected = holder.getAdapterPosition();
            notifyItemChanged(old);
            notifyItemChanged(selected);
        });

        Button btnViewAs = holder.itemView.findViewById(R.id.btnViewAs);
        if (btnViewAs != null) {
            if ("Admin".equalsIgnoreCase(role) || "Owner".equalsIgnoreCase(role)) {
                btnViewAs.setVisibility(View.GONE);
            } else {
                btnViewAs.setVisibility(View.VISIBLE);
                final String finalName = name != null ? name : "Staff";
                btnViewAs.setOnClickListener(v -> {
                    Intent intent = new Intent(ctx, MainActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    intent.putExtra("IMPERSONATE_STAFF_NAME", finalName);
                    ctx.startActivity(intent);
                });
            }
        }
    }

    static class Holder extends RecyclerView.ViewHolder {
        TextView tvName, tvEmail, tvPhone, tvRole, tvLastLogin;
        View statusIndicator;

        Holder(@NonNull View v) {
            super(v);
            tvName = v.findViewById(R.id.tvStaffName);
            tvEmail = v.findViewById(R.id.tvStaffEmail);
            tvPhone = v.findViewById(R.id.tvStaffPhone);
            tvRole = v.findViewById(R.id.tvStaffRole);
            tvLastLogin = v.findViewById(R.id.tvStaffLastLogin); // This matches the XML update we made
            statusIndicator = v.findViewById(R.id.statusIndicator);
        }
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
}