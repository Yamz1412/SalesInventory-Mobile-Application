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
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

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
    private AuthManager authManager;
    private FirebaseFirestore fStore;
    private FirebaseAuth fAuth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_staff_list);

        rvStaff = findViewById(R.id.rvStaffAccounts);
        progressBar = findViewById(R.id.progressBarStaffList);
        tvNoStaff = findViewById(R.id.tvNoStaff);
        btnCreateStaff = findViewById(R.id.btnCreateStaff);
        btnEditStaff = findViewById(R.id.btnEditStaffAccount);

        fStore = FirebaseFirestore.getInstance();
        fAuth = FirebaseAuth.getInstance();
        authManager = AuthManager.getInstance();

        adapter = new StaffListAdapter(this, new ArrayList<>());
        rvStaff.setLayoutManager(new LinearLayoutManager(this));
        rvStaff.setAdapter(adapter);

        btnCreateStaff.setOnClickListener(v -> AdminCreateStaffsActivity.startForCreate(this));
        btnEditStaff.setOnClickListener(v -> showEditForSelected());

        authManager.refreshCurrentUserStatus(success -> {
            if (!authManager.isCurrentUserAdmin()) {
                Toast.makeText(AdminStaffList.this, "Error: Admin access only", Toast.LENGTH_SHORT).show();
                finish();
            }
        });

        setupRecyclerViewLongPress();
        loadStaff();
    }

    private void setupRecyclerViewLongPress() {
        adapter.setOnItemLongClickListener((position, item) -> {
            showStaffContextMenu(item);
            return true;
        });
    }

    private void showStaffContextMenu(AdminUserItem staff) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(staff.getName());
        builder.setItems(new CharSequence[]{"Edit", "Delete"}, (dialog, which) -> {
            if (which == 0) {
                showEditDialog(staff);
            } else if (which == 1) {
                showDeleteConfirmation(staff);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    private void showEditDialog(AdminUserItem staff) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View dialogView = inflater.inflate(R.layout.dialog_edit_staff, null);

        EditText etName = dialogView.findViewById(R.id.etEditStaffName);
        EditText etEmail = dialogView.findViewById(R.id.etEditStaffEmail);
        EditText etPhone = dialogView.findViewById(R.id.etEditStaffPhone);
        EditText etPassword = dialogView.findViewById(R.id.etEditPassword);
        Button btnCancel = dialogView.findViewById(R.id.btnCancelEditStaff);
        Button btnUpdate = dialogView.findViewById(R.id.btnUpdateStaff);

        etName.setText(staff.getName() != null ? staff.getName() : "");
        etEmail.setText(staff.getEmail() != null ? staff.getEmail() : "");
        etPhone.setText(staff.getPhone() != null ? staff.getPhone() : "");

        AlertDialog dlg = new AlertDialog.Builder(this).setView(dialogView).create();

        btnCancel.setOnClickListener(v -> dlg.dismiss());
        btnUpdate.setOnClickListener(v -> {
            String newName = etName.getText().toString().trim();
            String newEmail = etEmail.getText().toString().trim();
            String newPhone = etPhone.getText().toString().trim();
            String newPassword = etPassword.getText().toString();

            if (newName.isEmpty() || newEmail.isEmpty()) {
                Toast.makeText(this, "Name and email are required", Toast.LENGTH_SHORT).show();
                return;
            }

            dlg.dismiss();
            progressBar.setVisibility(View.VISIBLE);

            AdminCreateStaffsActivity.updateStaffAccount(this, staff.getUid(), newName, newEmail, newPhone, newPassword, success -> {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (success) {
                        Toast.makeText(AdminStaffList.this, "Staff account updated", Toast.LENGTH_SHORT).show();
                        loadStaff();
                    } else {
                        Toast.makeText(AdminStaffList.this, "Failed to update staff account", Toast.LENGTH_SHORT).show();
                    }
                });
            });
        });

        dlg.show();
    }

    private void showDeleteConfirmation(AdminUserItem staff) {
        new AlertDialog.Builder(this)
                .setTitle("Delete Staff Account")
                .setMessage("Are you sure you want to delete " + staff.getName() + "? This action cannot be undone.")
                .setPositiveButton("Delete", (dialog, which) -> deleteStaffAccount(staff))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteStaffAccount(AdminUserItem staff) {
        progressBar.setVisibility(View.VISIBLE);

        String staffUid = staff.getUid();

        fStore.collection("users").document(staffUid).delete()
                .addOnSuccessListener(aVoid -> {
                    deleteStaffFromAuth(staffUid);
                })
                .addOnFailureListener(e -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(AdminStaffList.this, "Failed to delete from database: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void deleteStaffFromAuth(String staffUid) {
        FirebaseAuth currentAuth = FirebaseAuth.getInstance();
        com.google.firebase.auth.FirebaseUser currentUser = currentAuth.getCurrentUser();

        if (currentUser == null) {
            progressBar.setVisibility(View.GONE);
            Toast.makeText(AdminStaffList.this, "Authentication error: Please login again", Toast.LENGTH_SHORT).show();
            return;
        }

        currentUser.getIdToken(true).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                com.google.firebase.functions.FirebaseFunctions.getInstance()
                        .getHttpsCallable("deleteUser")
                        .call(new java.util.HashMap<String, Object>() {{
                            put("uid", staffUid);
                        }})
                        .addOnCompleteListener(deleteTask -> {
                            progressBar.setVisibility(View.GONE);
                            if (deleteTask.isSuccessful()) {
                                Toast.makeText(AdminStaffList.this, "Staff account deleted permanently", Toast.LENGTH_SHORT).show();
                                loadStaff();
                            } else {
                                Exception error = deleteTask.getException();
                                String errorMsg = error != null ? error.getMessage() : "Unknown error";
                                Toast.makeText(AdminStaffList.this, "Staff removed from database (auth deletion may have failed): " + errorMsg, Toast.LENGTH_SHORT).show();
                                loadStaff();
                            }
                        });
            } else {
                progressBar.setVisibility(View.GONE);
                Exception error = task.getException();
                String errorMsg = error != null ? error.getMessage() : "Unknown error";
                Toast.makeText(AdminStaffList.this, "Token error: " + errorMsg, Toast.LENGTH_SHORT).show();
            }
        });
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
                        for (AdminUserItem user : users) {
                            raw.add(user);
                        }
                        adapter.setItems(raw);
                    }
                });
            }
        });
    }

    private void showEditForSelected() {
        Object selected = adapter.getSelected();
        if (selected == null) {
            Toast.makeText(this, "Please select a staff member", Toast.LENGTH_SHORT).show();
            return;
        }
        if (selected instanceof AdminUserItem) {
            showEditDialog((AdminUserItem) selected);
        }
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