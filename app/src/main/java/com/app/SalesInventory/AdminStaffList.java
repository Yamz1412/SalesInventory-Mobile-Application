package com.app.SalesInventory;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class AdminStaffList extends AppCompatActivity {

    private RecyclerView rvStaff;
    private StaffListAdapter adapter;
    private ProgressBar progressBar;
    private TextView tvNoStaff;
    private Button btnCreateStaff;
    private Button btnEditStaff;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_list);
        rvStaff = findViewById(R.id.rvStaffAccounts);
        progressBar = findViewById(R.id.progressBarStaffList);
        tvNoStaff = findViewById(R.id.tvNoStaff);
        btnCreateStaff = findViewById(R.id.btnCreateStaff);
        btnEditStaff = findViewById(R.id.btnEditStaffAccount);
        adapter = new StaffListAdapter(this, new ArrayList<>());
        rvStaff.setLayoutManager(new LinearLayoutManager(this));
        rvStaff.setAdapter(adapter);
        btnCreateStaff.setOnClickListener(v -> AdminCreateStaffsActivity.startForCreate(this));
        btnEditStaff.setOnClickListener(v -> showEditForSelected());
        loadStaff();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadStaff();
    }

    private void loadStaff() {
        progressBar.setVisibility(View.VISIBLE);
        tvNoStaff.setVisibility(View.GONE);
        AuthManager.getInstance().fetchAllStaffAccounts(new AuthManager.UsersCallback() {
            @Override
            public void onComplete(List<AdminUserItem> users) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (users == null || users.isEmpty()) {
                        tvNoStaff.setVisibility(View.VISIBLE);
                        adapter.setItems(new ArrayList<>());
                    } else {
                        tvNoStaff.setVisibility(View.GONE);
                        List<Object> raw = new ArrayList<>();
                        raw.addAll(users);
                        adapter.setItems(raw);
                    }
                });
            }
        });
    }

    private void showEditForSelected() {
        Object selected = adapter.getSelected();
        if (selected == null) {
            return;
        }
        String uid = extractString(selected, "uid", "getUid");
        String name = extractString(selected, "name", "getName");
        String email = extractString(selected, "email", "getEmail");
        String phone = extractString(selected, "phone", "getPhone");
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_edit_staff, null);
        EditText etName = dialogView.findViewById(R.id.etEditStaffName);
        EditText etEmail = dialogView.findViewById(R.id.etEditStaffEmail);
        EditText etPhone = dialogView.findViewById(R.id.etEditStaffPhone);
        EditText etPassword = dialogView.findViewById(R.id.etEditPassword);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelEditStaff);
        Button btnUpdate = dialogView.findViewById(R.id.btnUpdateStaff);
        etName.setText(name != null ? name : "");
        etEmail.setText(email != null ? email : "");
        etPhone.setText(phone != null ? phone : "");
        AlertDialog dlg = new AlertDialog.Builder(this).setView(dialogView).create();
        btnCancel.setOnClickListener(v -> dlg.dismiss());
        btnUpdate.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            String newEmail = etEmail.getText().toString().trim();
            String newPhone = etPhone.getText().toString().trim();
            String newPassword = etPassword.getText().toString();
            dlg.dismiss();
            progressBar.setVisibility(View.VISIBLE);
            AdminCreateStaffsActivity.updateStaffAccount(this, uid, newName, newEmail, newPhone, newPassword, success -> runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (success) {
                    loadStaff();
                }
            }));
        });
        dlg.show();
    }

    private String extractString(Object obj, String fieldName, String getterName) {
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
}