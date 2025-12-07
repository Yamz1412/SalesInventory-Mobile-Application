package com.app.SalesInventory;


import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GetTokenResult;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.functions.HttpsCallableResult;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthManager {
    private static AuthManager instance;
    private FirebaseFirestore fStore;
    private FirebaseAuth auth;
    private volatile Boolean cachedIsAdmin = null;
    private volatile Boolean cachedIsApproved = null;
    private volatile String cachedRole = null;

    public interface UsersCallback {
        void onComplete(List<AdminUserItem> users);
    }

    public interface SimpleCallback {
        void onComplete(boolean success);
    }

    public interface RoleCallback {
        void onComplete(String role);
    }

    private AuthManager() {
        fStore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public static AuthManager getInstance() {
        if (instance == null) instance = new AuthManager();
        return instance;
    }

    public com.google.firebase.auth.FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public String getCurrentUserId() {
        FirebaseUser u = auth.getCurrentUser();
        return u == null ? null : u.getUid();
    }

    public FirebaseFirestore getFirestore() {
        return fStore;
    }

    public boolean isCurrentUserAdmin() {
        return cachedRole != null && cachedRole.equalsIgnoreCase("Admin");
    }

    public boolean isCurrentUserApproved() {
        if (cachedIsApproved != null) return cachedIsApproved;
        return false;
    }

    public String getCurrentUserRole() {
        return cachedRole != null ? cachedRole : "Unknown";
    }

    public void refreshCurrentUserStatus(@NonNull final SimpleCallback callback) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            cachedIsAdmin = false;
            cachedIsApproved = false;
            cachedRole = "Unknown";
            callback.onComplete(false);
            return;
        }
        u.getIdToken(true).addOnCompleteListener(new OnCompleteListener<GetTokenResult>() {
            @Override
            public void onComplete(@NonNull Task<GetTokenResult> task) {
                boolean isAdminClaim = false;
                if (task.isSuccessful() && task.getResult() != null) {
                    Object claim = task.getResult().getClaims().get("admin");
                    if (claim instanceof Boolean) isAdminClaim = (Boolean) claim;
                }
                if (isAdminClaim) {
                    cachedIsAdmin = true;
                    cachedIsApproved = true;
                    cachedRole = "Admin";
                    FirestoreManager.getInstance().setBusinessOwnerId(u.getUid());
                    callback.onComplete(true);
                    return;
                }
                final String uid = u.getUid();
                fStore.collection("admin").document(uid).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> adminTask) {
                        String foundRole = "Unknown";
                        boolean resultAdmin = false;
                        boolean adminApprovedFlag = false;
                        if (adminTask.isSuccessful() && adminTask.getResult() != null && adminTask.getResult().exists()) {
                            DocumentSnapshot adminDoc = adminTask.getResult();
                            String role = adminDoc.getString("role");
                            Boolean adminApproved = adminDoc.getBoolean("approved");
                            if (adminApproved == null) adminApproved = false;
                            adminApprovedFlag = adminApproved;
                            if (role != null && role.equalsIgnoreCase("Admin") && adminApproved) {
                                foundRole = "Admin";
                                resultAdmin = true;
                            }
                        }
                        if (resultAdmin) {
                            cachedIsAdmin = true;
                            cachedIsApproved = adminApprovedFlag;
                            cachedRole = foundRole;
                            FirestoreManager.getInstance().setBusinessOwnerId(uid);
                            callback.onComplete(true);
                            return;
                        }
                        fStore.collection("users").document(uid).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<DocumentSnapshot> userTask) {
                                boolean result = false;
                                boolean approvedFlag = false;
                                String foundRole = "Unknown";
                                if (userTask.isSuccessful() && userTask.getResult() != null && userTask.getResult().exists()) {
                                    DocumentSnapshot userDoc = userTask.getResult();
                                    String role = userDoc.getString("role");
                                    if (role == null) role = userDoc.getString("Role");
                                    Boolean approved = userDoc.getBoolean("approved");
                                    if (approved == null) approved = false;
                                    approvedFlag = approved;
                                    if (role != null && "Admin".equalsIgnoreCase(role)) {
                                        foundRole = "Admin";
                                        if (approved) result = true;
                                    } else if (role != null && "Staff".equalsIgnoreCase(role)) {
                                        foundRole = "Staff";
                                    } else if (role != null) {
                                        foundRole = role.trim();
                                    }
                                    String ownerAdminId = userDoc.getString("ownerAdminId");
                                    if (ownerAdminId != null && !ownerAdminId.isEmpty()) {
                                        FirestoreManager.getInstance().setBusinessOwnerId(ownerAdminId);
                                    } else {
                                        FirestoreManager.getInstance().setBusinessOwnerId(uid);
                                    }
                                } else {
                                    FirestoreManager.getInstance().setBusinessOwnerId(uid);
                                }
                                cachedIsAdmin = result;
                                cachedIsApproved = approvedFlag;
                                cachedRole = foundRole;
                                callback.onComplete(result);
                            }
                        });
                    }
                });
            }
        });
    }

    public void isCurrentUserAdminAsync(@NonNull final SimpleCallback callback) {
        if (cachedRole != null && cachedRole.equalsIgnoreCase("Admin")) {
            callback.onComplete(true);
            return;
        }
        refreshCurrentUserStatus(callback);
    }

    public void getCurrentUserRoleAsync(@NonNull final RoleCallback callback) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            callback.onComplete("Unknown");
            return;
        }
        final String uid = u.getUid();
        fStore.collection("admin").document(uid).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> adminTask) {
                if (adminTask.isSuccessful() && adminTask.getResult() != null && adminTask.getResult().exists()) {
                    DocumentSnapshot adminDoc = adminTask.getResult();
                    Boolean adminApproved = adminDoc.getBoolean("approved");
                    String adminRole = adminDoc.getString("role");
                    if (adminRole != null && adminRole.trim().equalsIgnoreCase("Admin") && adminApproved != null && adminApproved) {
                        cachedRole = "Admin";
                        FirestoreManager.getInstance().setBusinessOwnerId(uid);
                        callback.onComplete("Admin");
                        return;
                    }
                }
                fStore.collection("users").document(uid).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> userTask) {
                        String resolvedRole = "Unknown";
                        if (userTask.isSuccessful() && userTask.getResult() != null && userTask.getResult().exists()) {
                            DocumentSnapshot doc = userTask.getResult();
                            Boolean approved = doc.getBoolean("approved");
                            String role = doc.getString("role");
                            if (role == null) role = doc.getString("Role");
                            if (role != null && approved != null && approved) {
                                resolvedRole = role.trim();
                            } else if (role != null) {
                                resolvedRole = role.trim();
                            } else {
                                resolvedRole = "Staff";
                            }
                            String ownerAdminId = doc.getString("ownerAdminId");
                            if (ownerAdminId != null && !ownerAdminId.isEmpty()) {
                                FirestoreManager.getInstance().setBusinessOwnerId(ownerAdminId);
                            } else {
                                FirestoreManager.getInstance().setBusinessOwnerId(uid);
                            }
                        } else {
                            FirestoreManager.getInstance().setBusinessOwnerId(uid);
                        }
                        cachedRole = resolvedRole;
                        callback.onComplete(resolvedRole);
                    }
                });
            }
        });
    }

    public void fetchPendingStaffAccounts(final UsersCallback callback) {
        fStore.collection("users").whereEqualTo("approved", false).get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                List<AdminUserItem> list = new ArrayList<>();
                if (task.isSuccessful() && task.getResult() != null) {
                    for (QueryDocumentSnapshot doc : task.getResult()) {
                        String uid = doc.getId();
                        String email = doc.getString("email");
                        if (email == null) email = doc.getString("Email");
                        String name = doc.getString("name");
                        if (name == null) name = doc.getString("Name");
                        Boolean approved = doc.getBoolean("approved");
                        if (approved == null) approved = false;
                        AdminUserItem u = new AdminUserItem(uid, name != null ? name : "", email != null ? email : "", "Staff", approved);
                        list.add(u);
                    }
                }
                callback.onComplete(list);
            }
        });
    }

    public void fetchAllStaffAccounts(final UsersCallback callback) {
        fStore.collection("users").whereEqualTo("approved", true).get().addOnCompleteListener(new OnCompleteListener<QuerySnapshot>() {
            @Override
            public void onComplete(@NonNull Task<QuerySnapshot> task) {
                List<AdminUserItem> list = new ArrayList<>();
                if (task.isSuccessful() && task.getResult() != null) {
                    for (QueryDocumentSnapshot doc : task.getResult()) {
                        String uid = doc.getId();
                        String email = doc.getString("email");
                        if (email == null) email = doc.getString("Email");
                        String name = doc.getString("name");
                        if (name == null) name = doc.getString("Name");
                        Boolean approved = doc.getBoolean("approved");
                        if (approved == null) approved = true;
                        String role = doc.getString("role");
                        if (role == null) role = doc.getString("Role");
                        if (role == null) role = "Staff";
                        AdminUserItem u = new AdminUserItem(uid, name != null ? name : "", email != null ? email : "", role, approved);
                        list.add(u);
                    }
                }
                callback.onComplete(list);
            }
        });
    }

    public void callAdminUpdateUser(String uid, Boolean approved, String role, Boolean setAsAdmin, final SimpleCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        if (approved != null) data.put("approved", approved);
        if (role != null) data.put("role", role);
        if (setAsAdmin != null) data.put("setAsAdmin", setAsAdmin);
        FirebaseFunctions.getInstance().getHttpsCallable("adminUpdateUser").call(data).addOnCompleteListener(new OnCompleteListener<HttpsCallableResult>() {
            @Override
            public void onComplete(@NonNull Task<HttpsCallableResult> task) {
                if (task.isSuccessful() && task.getResult() != null) {
                    callback.onComplete(true);
                } else {
                    callback.onComplete(false);
                    if (task.getException() != null) {
                        android.util.Log.e("AuthManager", "callAdminUpdateUser failed: " + task.getException().getMessage(), task.getException());
                    }
                }
            }
        });
    }

    public void approveUser(String uid, final SimpleCallback callback) {
        callAdminUpdateUser(uid, true, null, null, callback);
    }

    public void promoteToAdmin(String uid, final SimpleCallback callback) {
        fStore.collection("users").document(uid).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if (!task.isSuccessful() || task.getResult() == null) {
                    callback.onComplete(false);
                    return;
                }
                DocumentSnapshot doc = task.getResult();
                String email = doc.getString("email");
                if (email == null) email = doc.getString("Email");
                String name = doc.getString("name");
                if (name == null) name = doc.getString("Name");
                String phone = null;
                if (doc.contains("phone")) phone = doc.getString("phone");
                if (phone == null && doc.contains("Phone")) phone = doc.getString("Phone");
                Long createdAt = null;
                if (doc.contains("createdAt")) {
                    Object c = doc.get("createdAt");
                    if (c instanceof Number) createdAt = ((Number) c).longValue();
                }
                Map<String, Object> adminData = new HashMap<>();
                adminData.put("uid", uid);
                adminData.put("email", email != null ? email : "");
                adminData.put("name", name != null ? name : "");
                if (phone != null) adminData.put("phone", phone);
                adminData.put("role", "Admin");
                adminData.put("approved", true);
                if (createdAt != null) adminData.put("createdAt", createdAt);
                callAdminUpdateUser(uid, true, "Admin", true, new SimpleCallback() {
                    @Override
                    public void onComplete(boolean success) {
                        if (!success) {
                            callback.onComplete(false);
                            return;
                        }
                        fStore.collection("admin").document(uid).set(adminData, com.google.firebase.firestore.SetOptions.merge()).addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> setTask) {
                                if (!setTask.isSuccessful()) {
                                    callback.onComplete(false);
                                    return;
                                }
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("role", "Admin");
                                updates.put("approved", true);
                                fStore.collection("users").document(uid).set(updates, com.google.firebase.firestore.SetOptions.merge()).addOnCompleteListener(new OnCompleteListener<Void>() {
                                    @Override
                                    public void onComplete(@NonNull Task<Void> upd) {
                                        callback.onComplete(upd.isSuccessful());
                                    }
                                });
                            }
                        });
                    }
                });
            }
        });
    }

    public void demoteAdmin(String uid, final SimpleCallback callback) {
        callAdminUpdateUser(uid, true, "Staff", false, new SimpleCallback() {
            @Override
            public void onComplete(boolean success) {
                if (!success) {
                    callback.onComplete(false);
                    return;
                }
                fStore.collection("admin").document(uid).delete().addOnCompleteListener(new OnCompleteListener<Void>() {
                    @Override
                    public void onComplete(@NonNull Task<Void> task) {
                        if (!task.isSuccessful()) {
                            callback.onComplete(false);
                            return;
                        }
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("role", "Staff");
                        updates.put("approved", true);
                        fStore.collection("users").document(uid).update(updates).addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> upd) {
                                callback.onComplete(upd.isSuccessful());
                            }
                        });
                    }
                });
            }
        });
    }

    public void signOutAndCleanup(final Runnable onComplete) {
        final String uid = getCurrentUserId();
        final String owner = FirestoreManager.getInstance().getBusinessOwnerId();
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            String token = null;
            if (task.isSuccessful()) token = task.getResult();
            if (uid != null && token != null && !token.isEmpty()) {
                fStore.collection("users").document(uid).collection("devices").document(token).delete();
            }
            if (owner != null && !owner.isEmpty()) {
                FirebaseMessaging.getInstance().unsubscribeFromTopic("owner_" + owner).addOnCompleteListener(unsubTask -> {
                    FirebaseAuth.getInstance().signOut();
                    if (onComplete != null) onComplete.run();
                });
            } else {
                FirebaseAuth.getInstance().signOut();
                if (onComplete != null) onComplete.run();
            }
        });
    }
}