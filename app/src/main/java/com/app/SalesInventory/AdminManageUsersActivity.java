package com.app.SalesInventory;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AdminManageUsersActivity extends AppCompatActivity {
    private RecyclerView rvPending;
    private RecyclerView rvManage;
    private ProgressBar progressBar;
    private PendingUserAdapter pendingAdapter;
    private ManageUserAdapter manageAdapter;
    private List<AdminUserItem> pendingUsers = new ArrayList<>();
    private List<AdminUserItem> staffUsers = new ArrayList<>();
    private FirebaseFirestore fStore;
    private ListenerRegistration usersListener;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_manage_users);
        rvPending = findViewById(R.id.rvPendingUsers);
        rvManage = findViewById(R.id.rvManageUsers);
        progressBar = findViewById(R.id.progressBarManage);
        rvPending.setLayoutManager(new LinearLayoutManager(this));
        rvManage.setLayoutManager(new LinearLayoutManager(this));
        pendingAdapter = new PendingUserAdapter(pendingUsers, new PendingUserAdapter.OnPendingActionListener() {
            @Override
            public void onApprove(String uid) {
                approveUser(uid);
            }
            @Override
            public void onCancel(String uid) {
                cancelUser(uid);
            }
        });
        manageAdapter = new ManageUserAdapter(staffUsers, uid -> promoteUser(uid));
        rvPending.setAdapter(pendingAdapter);
        rvManage.setAdapter(manageAdapter);
        fStore = FirebaseFirestore.getInstance();
        authManager = AuthManager.getInstance();
        authManager.isCurrentUserAdminAsync(success -> {
            if (!success) {
                finish();
            } else {
                attachUsersListener();
            }
        });
    }

    private void attachUsersListener() {
        progressBar.setVisibility(View.VISIBLE);
        usersListener = fStore.collection("users").addSnapshotListener((snapshot, e) -> {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                pendingUsers.clear();
                staffUsers.clear();
                if (e != null) {
                    Toast.makeText(AdminManageUsersActivity.this, "Failed to load users", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (snapshot == null || snapshot.getDocuments().isEmpty()) {
                    pendingAdapter.update(pendingUsers);
                    manageAdapter.update(staffUsers);
                    Toast.makeText(AdminManageUsersActivity.this, "No users found", Toast.LENGTH_SHORT).show();
                    return;
                }
                int total = 0;
                for (QueryDocumentSnapshot doc : snapshot) {
                    total++;
                    String uid = doc.getId();
                    String email = doc.getString("email");
                    if (email == null) email = doc.getString("Email");
                    String name = doc.getString("name");
                    if (name == null) name = doc.getString("Name");
                    Object roleObj = doc.get("role");
                    if (roleObj == null) roleObj = doc.get("Role");
                    String role = roleObj != null ? roleObj.toString().replace("\"", "").trim() : "Staff";
                    Boolean approved = doc.getBoolean("approved");
                    if (approved == null) approved = false;
                    AdminUserItem u = new AdminUserItem(uid, name != null ? name : "", email != null ? email : "", role, approved);
                    if (approved) {
                        staffUsers.add(u);
                    } else {
                        pendingUsers.add(u);
                    }
                }
                pendingAdapter.update(pendingUsers);
                manageAdapter.update(staffUsers);
                android.util.Log.d("AdminManageUsers", "loadUsers total=" + total + " pending=" + pendingUsers.size() + " staff=" + staffUsers.size());
                if (pendingUsers.isEmpty()) {
                    Toast.makeText(AdminManageUsersActivity.this, "No pending accounts", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (usersListener != null) {
            usersListener.remove();
            usersListener = null;
        }
    }

    private void approveUser(String uid) {
        progressBar.setVisibility(View.VISIBLE);
        authManager.approveUser(uid, success -> {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (success) {
                    movePendingToStaff(uid);
                    createAlertForUser(uid, "Account Approved", "Your account has been approved by an administrator.");
                    Toast.makeText(AdminManageUsersActivity.this, "User approved", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AdminManageUsersActivity.this, "Approve failed", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void cancelUser(String uid) {
        progressBar.setVisibility(View.VISIBLE);
        fStore.collection("users").document(uid).delete().addOnCompleteListener(task -> {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (task.isSuccessful()) {
                    removePendingLocal(uid);
                    createAlertForUser(uid, "Registration Cancelled", "Your registration has been cancelled by an administrator.");
                    Toast.makeText(AdminManageUsersActivity.this, "User registration cancelled", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AdminManageUsersActivity.this, "Cancel failed", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void promoteUser(String uid) {
        progressBar.setVisibility(View.VISIBLE);
        authManager.promoteToAdmin(uid, success -> {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (success) {
                    removeFromStaff(uid);
                    createAlertForUser(uid, "Promoted to Admin", "Your account has been promoted to Admin. Please sign out and sign in again to refresh permissions.");
                    Toast.makeText(AdminManageUsersActivity.this, "User promoted to Admin", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AdminManageUsersActivity.this, "Promote failed", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void demoteUser(String uid) {
        progressBar.setVisibility(View.VISIBLE);
        authManager.demoteAdmin(uid, success -> {
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (success) {
                    createAlertForUser(uid, "Demoted from Admin", "Your admin privileges have been removed.");
                    Toast.makeText(AdminManageUsersActivity.this, "User demoted from Admin", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(AdminManageUsersActivity.this, "Demote failed", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void createAlertForUser(String uid, String title, String message) {
        Map<String, Object> alert = new HashMap<>();
        alert.put("userId", uid);
        alert.put("title", title);
        alert.put("message", message);
        alert.put("read", false);
        alert.put("createdAt", System.currentTimeMillis());
        fStore.collection("alerts").add(alert);
    }

    private void movePendingToStaff(String uid) {
        Iterator<AdminUserItem> it = pendingUsers.iterator();
        while (it.hasNext()) {
            AdminUserItem u = it.next();
            if (u.getUid().equals(uid)) {
                u.setApproved(true);
                staffUsers.add(u);
                it.remove();
                pendingAdapter.update(pendingUsers);
                manageAdapter.update(staffUsers);
                return;
            }
        }
    }

    private void removePendingLocal(String uid) {
        Iterator<AdminUserItem> it = pendingUsers.iterator();
        while (it.hasNext()) {
            AdminUserItem u = it.next();
            if (u.getUid().equals(uid)) {
                it.remove();
                pendingAdapter.update(pendingUsers);
                return;
            }
        }
    }

    private void removeFromStaff(String uid) {
        Iterator<AdminUserItem> it = staffUsers.iterator();
        while (it.hasNext()) {
            AdminUserItem u = it.next();
            if (u.getUid().equals(uid)) {
                it.remove();
                manageAdapter.update(staffUsers);
                return;
            }
        }
    }
}