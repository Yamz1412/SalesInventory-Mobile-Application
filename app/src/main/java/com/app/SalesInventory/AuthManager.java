package com.app.SalesInventory;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
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
import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AuthManager {
    private static AuthManager instance;
    private FirebaseFirestore fStore;
    private FirebaseAuth auth;

    public interface UsersCallback {
        void onComplete(List<AdminUserItem> users);
    }

    public interface SimpleCallback {
        void onComplete(boolean success);
    }

    private AuthManager() {
        fStore = FirebaseFirestore.getInstance();
        auth = FirebaseAuth.getInstance();
    }

    public static AuthManager getInstance() {
        if (instance == null) instance = new AuthManager();
        return instance;
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public String getCurrentUserId() {
        FirebaseUser u = auth.getCurrentUser();
        return u == null ? null : u.getUid();
    }

    public FirebaseFirestore getFirestore() {
        return fStore;
    }

    public boolean isCurrentUserAdminByAdminCollection() {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return false;
        String uid = u.getUid();
        try {
            DocumentSnapshot snap = Tasks.await(fStore.collection("admin").document(uid).get());
            if (snap != null && snap.exists()) {
                Boolean approved = snap.getBoolean("approved");
                if (approved == null) approved = true;
                return approved;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public boolean isCurrentUserAdminByUsersCollection() {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return false;
        String uid = u.getUid();
        try {
            DocumentSnapshot snap = Tasks.await(fStore.collection("users").document(uid).get());
            if (snap != null && snap.exists()) {
                String role = snap.getString("role");
                if (role == null) role = snap.getString("Role");
                Boolean approved = snap.getBoolean("approved");
                if (approved == null) approved = false;
                if (role != null && "admin".equalsIgnoreCase(role) && approved) return true;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public boolean isCurrentUserAdminByClaim() {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return false;
        try {
            GetTokenResult tr = Tasks.await(u.getIdToken(true));
            if (tr != null) {
                Object claim = tr.getClaims().get("admin");
                if (claim instanceof Boolean) return (Boolean) claim;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public boolean isCurrentUserAdmin() {
        if (isCurrentUserAdminByClaim()) return true;
        if (isCurrentUserAdminByAdminCollection()) return true;
        return isCurrentUserAdminByUsersCollection();
    }

    public boolean isCurrentUserApproved() {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return false;
        String uid = u.getUid();
        try {
            DocumentSnapshot snap = Tasks.await(fStore.collection("users").document(uid).get());
            if (snap != null && snap.exists()) {
                Boolean approved = snap.getBoolean("approved");
                if (approved == null) approved = false;
                return approved;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public void isCurrentUserAdminAsync(@NonNull final SimpleCallback callback) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
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
                    callback.onComplete(true);
                    return;
                }
                final String uid = u.getUid();
                fStore.collection("admin").document(uid).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> adminTask) {
                        if (adminTask.isSuccessful() && adminTask.getResult() != null && adminTask.getResult().exists()) {
                            DocumentSnapshot adminDoc = adminTask.getResult();
                            Boolean adminApproved = adminDoc.getBoolean("approved");
                            if (adminApproved == null) adminApproved = true;
                            if (adminApproved) {
                                callback.onComplete(true);
                                return;
                            }
                        }
                        fStore.collection("users").document(uid).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                            @Override
                            public void onComplete(@NonNull Task<DocumentSnapshot> userTask) {
                                if (userTask.isSuccessful() && userTask.getResult() != null && userTask.getResult().exists()) {
                                    DocumentSnapshot userDoc = userTask.getResult();
                                    String role = userDoc.getString("role");
                                    if (role == null) role = userDoc.getString("Role");
                                    Boolean approved = userDoc.getBoolean("approved");
                                    if (approved == null) approved = false;
                                    if (role != null && "admin".equalsIgnoreCase(role) && approved) {
                                        callback.onComplete(true);
                                        return;
                                    }
                                }
                                callback.onComplete(false);
                            }
                        });
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
                        AdminUserItem u = new AdminUserItem(uid, name != null ? name : "", email != null ? email : "", "Staff", approved);
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
                        fStore.collection("admin").document(uid).set(adminData, SetOptions.merge()).addOnCompleteListener(new OnCompleteListener<Void>() {
                            @Override
                            public void onComplete(@NonNull Task<Void> setTask) {
                                if (!setTask.isSuccessful()) {
                                    callback.onComplete(false);
                                    return;
                                }
                                Map<String, Object> updates = new HashMap<>();
                                updates.put("role", "Admin");
                                updates.put("approved", true);
                                fStore.collection("users").document(uid).set(updates, SetOptions.merge()).addOnCompleteListener(new OnCompleteListener<Void>() {
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
}