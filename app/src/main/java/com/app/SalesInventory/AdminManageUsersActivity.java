package com.app.SalesInventory;

import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.functions.HttpsCallableResult;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AdminManageUsersActivity extends AppCompatActivity {
    private static final String TAG = "AdminManageUsers";
    private RecyclerView rvPending;
    private RecyclerView rvManage;
    private ProgressBar progressBar;
    private TextView tvNoPending;
    private PendingUserAdapter pendingAdapter;
    private ManageUserAdapter manageAdapter;
    private List<AdminUserItem> pendingUsers = new ArrayList<>();
    private List<AdminUserItem> staffUsers = new ArrayList<>();
    private FirebaseFirestore fStore;
    private ListenerRegistration usersListener;
    private AuthManager authManager;
    private boolean currentUserIsAdmin = false;
    private boolean adminCheckCompleted = false;
    private FirebaseFunctions functions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_manage_users);
        rvPending = findViewById(R.id.rvPendingUsers);
        rvManage = findViewById(R.id.rvManageUsers);
        progressBar = findViewById(R.id.progressBarManage);
        tvNoPending = findViewById(R.id.tvNoPending);
        rvPending.setLayoutManager(new LinearLayoutManager(this));
        rvManage.setLayoutManager(new LinearLayoutManager(this));
        fStore = FirebaseFirestore.getInstance();
        authManager = AuthManager.getInstance();
        pendingAdapter = new PendingUserAdapter(pendingUsers, new PendingUserAdapter.OnPendingActionListener() {
            @Override
            public void onApprove(String uid) {
                if (!adminCheckCompleted) {
                    Toast.makeText(AdminManageUsersActivity.this, "Checking admin status...", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!currentUserIsAdmin) {
                    Toast.makeText(AdminManageUsersActivity.this, "Admin access required", Toast.LENGTH_SHORT).show();
                    return;
                }
                approveUser(uid);
            }
            @Override
            public void onCancel(String uid) {
                if (!adminCheckCompleted) {
                    Toast.makeText(AdminManageUsersActivity.this, "Checking admin status...", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!currentUserIsAdmin) {
                    Toast.makeText(AdminManageUsersActivity.this, "Admin access required", Toast.LENGTH_SHORT).show();
                    return;
                }
                cancelUser(uid);
            }
        });
        manageAdapter = new ManageUserAdapter(staffUsers, uid -> {
            if (!adminCheckCompleted) {
                Toast.makeText(AdminManageUsersActivity.this, "Checking admin status...", Toast.LENGTH_SHORT).show();
                return;
            }
            if (!currentUserIsAdmin) {
                Toast.makeText(AdminManageUsersActivity.this, "Admin access required", Toast.LENGTH_SHORT).show();
                return;
            }
            promoteUser(uid);
        });
        rvPending.setAdapter(pendingAdapter);
        rvManage.setAdapter(manageAdapter);
        DebugUtil.logFirebaseInfo();
        functions = FirebaseFunctions.getInstance();
        authManager.isCurrentUserAdminAsync(success -> {
            currentUserIsAdmin = success;
            adminCheckCompleted = true;
            runOnUiThread(() -> {
                if (!currentUserIsAdmin) {
                    Toast.makeText(AdminManageUsersActivity.this, "You are not an admin. Actions are disabled.", Toast.LENGTH_LONG).show();
                }
            });
        });
        attachUsersListener();
    }

    private void attachUsersListener() {
        progressBar.setVisibility(View.VISIBLE);
        usersListener = fStore.collection("users").addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(QuerySnapshot snapshot, FirebaseFirestoreException e) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    pendingUsers.clear();
                    staffUsers.clear();
                    if (e != null) {
                        Log.e(TAG, "Listener error: " + e.getMessage(), e);
                        Toast.makeText(AdminManageUsersActivity.this, "Failed to load users: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (snapshot == null) {
                        Log.w(TAG, "snapshot == null");
                        Toast.makeText(AdminManageUsersActivity.this, "No snapshot returned", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    int total = 0;
                    for (DocumentSnapshot doc : snapshot.getDocuments()) {
                        total++;
                        String id = doc.getId();
                        Boolean approved = null;
                        if (doc.contains("approved")) {
                            Object a = doc.get("approved");
                            if (a instanceof Boolean) approved = (Boolean) a;
                            else if (a instanceof String) approved = "true".equalsIgnoreCase((String) a);
                        }
                        if (approved == null) approved = false;
                        String role = null;
                        if (doc.contains("role")) role = doc.getString("role");
                        if (role == null && doc.contains("Role")) role = doc.getString("Role");
                        if (role != null) role = role.replace("\"", "").trim();
                        String email = doc.contains("email") ? doc.getString("email") : (doc.contains("Email") ? doc.getString("Email") : "");
                        String name = doc.contains("name") ? doc.getString("name") : (doc.contains("Name") ? doc.getString("Name") : "");
                        AdminUserItem u = new AdminUserItem(id, name != null ? name : "", email != null ? email : "", role != null ? role : "Staff", approved);
                        Log.d(TAG, "ADDING -> id=" + id + " approved=" + approved + " role=" + role + " email=" + email + " name=" + name);
                        if (u.isApproved()) {
                            staffUsers.add(u);
                            Log.d(TAG, "ADDED TO staffUsers: " + id);
                        } else {
                            pendingUsers.add(u);
                            Log.d(TAG, "ADDED TO pendingUsers: " + id);
                        }
                        Log.d(TAG, "INTERMEDIATE sizes: pending=" + pendingUsers.size() + " staff=" + staffUsers.size());
                    }
                    Log.d(TAG, "BEFORE ADAPTER update total=" + total + " pending=" + pendingUsers.size() + " staff=" + staffUsers.size());
                    pendingAdapter.update(pendingUsers);
                    manageAdapter.update(staffUsers);
                    Log.d(TAG, "AFTER ADAPTER update total=" + total + " pending=" + pendingUsers.size() + " staff=" + staffUsers.size());
                    tvNoPending.setVisibility(pendingUsers.isEmpty() ? View.VISIBLE : View.GONE);
                });
            }
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
        callAdminUpdateUser(uid, true, null, null, success -> runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            if (success) {
                movePendingToStaff(uid);
                createAlertForUser(uid, "Account Approved", "Your account has been approved by an administrator.");
                Toast.makeText(AdminManageUsersActivity.this, "User approved", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(AdminManageUsersActivity.this, "Approve failed", Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private void cancelUser(String uid) {
        progressBar.setVisibility(View.VISIBLE);
        fStore.collection("users").document(uid).delete().addOnCompleteListener(task -> runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            if (task.isSuccessful()) {
                removePendingLocal(uid);
                createAlertForUser(uid, "Registration Cancelled", "Your registration has been cancelled by an administrator.");
                Toast.makeText(AdminManageUsersActivity.this, "User registration cancelled", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(AdminManageUsersActivity.this, "Cancel failed", Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private static final class DebugUtil {
        static void logFirebaseInfo() {
            try {
                String projectId = FirebaseApp.getInstance().getOptions().getProjectId();
                FirebaseUser u = FirebaseAuth.getInstance().getCurrentUser();
                String uid = u != null ? u.getUid() : "null";
                Log.d(TAG, "Firebase projectId=" + projectId + " currentUserUid=" + uid);
            } catch (Exception e) {
                Log.e(TAG, "Failed to log Firebase info", e);
            }
        }
    }

    private void promoteUser(String uid) {
        progressBar.setVisibility(View.VISIBLE);
        callAdminUpdateUser(uid, true, "Admin", true, success -> runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            if (success) {
                removeFromStaff(uid);
                createAlertForUser(uid, "Promoted to Admin", "Your account has been promoted to Admin. Please sign out and sign in again to refresh permissions.");
                Toast.makeText(AdminManageUsersActivity.this, "User promoted to Admin", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(AdminManageUsersActivity.this, "Promote failed", Toast.LENGTH_SHORT).show();
            }
        }));
    }

    private void demoteUser(String uid) {
        progressBar.setVisibility(View.VISIBLE);
        callAdminUpdateUser(uid, true, "Staff", false, success -> runOnUiThread(() -> {
            progressBar.setVisibility(View.GONE);
            if (success) {
                createAlertForUser(uid, "Demoted from Admin", "Your admin privileges have been removed.");
                Toast.makeText(AdminManageUsersActivity.this, "User demoted from Admin", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(AdminManageUsersActivity.this, "Demote failed", Toast.LENGTH_SHORT).show();
            }
        }));
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

    private void callAdminUpdateUser(String targetUid, Boolean approved, String role, Boolean setAsAdmin, AuthManager.SimpleCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", targetUid);
        if (approved != null) data.put("approved", approved);
        if (role != null) data.put("role", role);
        if (setAsAdmin != null) data.put("setAsAdmin", setAsAdmin);
        functions.getHttpsCallable("adminUpdateUser").call(data).addOnCompleteListener(new OnCompleteListener<HttpsCallableResult>() {
            @Override
            public void onComplete(Task<HttpsCallableResult> task) {
                if (task.isSuccessful()) {
                    callback.onComplete(true);
                } else {
                    Log.e(TAG, "adminUpdateUser failed", task.getException());
                    callback.onComplete(false);
                }
            }
        });
    }
}