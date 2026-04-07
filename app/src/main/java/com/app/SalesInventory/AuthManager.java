package com.app.SalesInventory;

import android.app.Application;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
    private Application application;

    public interface UsersCallback {
        void onComplete(List<AdminUserItem> users);
    }

    public interface SimpleCallback {
        void onComplete(boolean success);
    }

    public interface RoleCallback {
        void onComplete(String role);
    }

    public interface AdminCheckCallback {
        void onResult(boolean isAdmin);
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
        return u == null ? "" : u.getUid();
    }

    public FirebaseFirestore getFirestore() {
        return fStore;
    }

    public boolean isCurrentUserAdmin() {
        return cachedRole != null && (cachedRole.equalsIgnoreCase("Admin") || cachedRole.equalsIgnoreCase("Owner") || cachedRole.equalsIgnoreCase("BusinessOwner"));
    }

    public boolean isCurrentUserSubAdmin() {
        return cachedRole != null && cachedRole.equalsIgnoreCase("Sub-Admin");
    }
    public boolean hasManagerAccess() {
        return isCurrentUserAdmin() || isCurrentUserSubAdmin();
    }

    public boolean isCurrentUserApproved() {
        if (Boolean.TRUE.equals(cachedIsAdmin)) return true;
        if (cachedIsApproved != null) return cachedIsApproved;
        return false;
    }

    public void init(Application app) {
        this.application = app;
    }

    // =========================================================
    // NEW: Centralized method to ensure data always loads on login
    // =========================================================
    // Replace this method inside AuthManager.java
    private void triggerDataSync() {
        android.util.Log.d("SalesInventory_SYNC", "==== TRIGGERING DATA SYNC (AuthManager) ====");
        try {
            android.util.Log.d("SalesInventory_SYNC", "Restarting FirestoreSyncListener...");
            FirestoreSyncListener.getInstance().restartAllListeners();

            if (application != null) {
                android.util.Log.d("SalesInventory_SYNC", "Refreshing SalesRepository data...");
                SalesRepository.getInstance(application).reloadAllData();
                android.util.Log.d("SalesInventory_SYNC", "Starting ProductRemoteSyncer listening...");
                new ProductRemoteSyncer(application).startListening();
            } else {
                android.util.Log.d("SalesInventory_SYNC", "SalesRepository reloaded (Staff Mode)...");
                SalesRepository.getInstance().reloadAllData();
            }
            android.util.Log.d("SalesInventory_SYNC", "==== SYNC TRIGGER COMPLETE ====");
        } catch (Exception e) {
            android.util.Log.e("SalesInventory_SYNC", "ERROR triggering data sync", e);
        }
    }

    public void isUserAdmin(AdminCheckCallback callback) {
        if (cachedIsAdmin != null) {
            callback.onResult(cachedIsAdmin);
            return;
        }
        if (auth == null) auth = FirebaseAuth.getInstance();
        if (fStore == null) fStore = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() == null) {
            callback.onResult(false);
            return;
        }
        String uid = auth.getCurrentUser().getUid();

        fStore.collection("users").document(uid).get().addOnSuccessListener(documentSnapshot -> {
            if (documentSnapshot.exists()) {
                String role = documentSnapshot.getString("role");
                cachedRole = role;
                cachedIsAdmin = "Admin".equalsIgnoreCase(role) || "Owner".equalsIgnoreCase(role) || "BusinessOwner".equalsIgnoreCase(role);
                callback.onResult(cachedIsAdmin);
            } else {
                callback.onResult(false);
            }
        }).addOnFailureListener(e -> callback.onResult(false));
    }

    public String getCurrentUserRole() {
        return cachedRole != null ? cachedRole : "Unknown";
    }

    public void refreshCurrentUserStatus(@NonNull final SimpleCallback callback) {
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) {
            cachedIsAdmin = false;
            cachedIsApproved = false;
            cachedRole = null;
            if (callback != null) callback.onComplete(false);
            return;
        }

        final String uid = u.getUid();

        fStore.collection("admin").document(uid).get().addOnCompleteListener(adminTask -> {
            if (adminTask.isSuccessful() && adminTask.getResult() != null && adminTask.getResult().exists()) {
                DocumentSnapshot doc = adminTask.getResult();
                cachedIsAdmin = true;
                cachedIsApproved = true;
                cachedRole = "Admin";
                FirestoreManager.getInstance().setBusinessOwnerId(uid);

                // FIX: Trigger data download
                triggerDataSync();

                Map<String, Object> fixData = new HashMap<>();
                fixData.put("role", "Admin");
                fixData.put("approved", true);
                if (!doc.contains("ownerAdminId")) fixData.put("ownerAdminId", uid);
                fStore.collection("users").document(uid).set(fixData, SetOptions.merge());

                callback.onComplete(true);
            } else {
                fStore.collection("users").document(uid).get().addOnCompleteListener(userTask -> {
                    if (userTask.isSuccessful() && userTask.getResult() != null && userTask.getResult().exists()) {
                        setupUserSession(userTask.getResult(), uid, callback);
                    } else {
                        FirestoreManager.getInstance().setBusinessOwnerId(uid);
                        callback.onComplete(false);
                    }
                });
            }
        });
    }

    private void setupUserSession(DocumentSnapshot doc, String uid, SimpleCallback callback) {
        String role = doc.getString("role");
        if (role == null) role = doc.getString("Role");

        boolean approved = false;
        Object approvedObj = doc.get("approved");
        if (approvedObj instanceof Boolean) approved = (Boolean) approvedObj;
        else if (approvedObj instanceof String) approved = "true".equalsIgnoreCase((String) approvedObj);

        cachedRole = (role != null) ? role.trim() : "Staff";
        cachedIsAdmin = "Admin".equalsIgnoreCase(cachedRole);

        if (cachedIsAdmin) approved = true;
        cachedIsApproved = approved;

        String ownerAdminId = doc.getString("ownerAdminId");
        if (ownerAdminId != null && !ownerAdminId.isEmpty()) {
            FirestoreManager.getInstance().setBusinessOwnerId(ownerAdminId);
        } else {
            FirestoreManager.getInstance().setBusinessOwnerId(uid);
        }

        // FIX: Trigger data download
        triggerDataSync();

        callback.onComplete(cachedIsAdmin && cachedIsApproved);
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

        fStore.collection("admin").document(uid).get().addOnCompleteListener(adminTask -> {
            if (adminTask.isSuccessful() && adminTask.getResult() != null && adminTask.getResult().exists()) {
                cachedRole = "Admin";
                cachedIsAdmin = true;
                cachedIsApproved = true;
                FirestoreManager.getInstance().setBusinessOwnerId(uid);

                // FIX: Trigger data download
                triggerDataSync();

                Map<String, Object> fixData = new HashMap<>();
                fixData.put("role", "Admin");
                fixData.put("approved", true);
                fStore.collection("users").document(uid).set(fixData, SetOptions.merge());

                callback.onComplete("Admin");
                return;
            }

            fStore.collection("users").document(uid).get().addOnCompleteListener(userTask -> {
                String resolvedRole = "Unknown";
                if (userTask.isSuccessful() && userTask.getResult() != null && userTask.getResult().exists()) {
                    DocumentSnapshot doc = userTask.getResult();

                    boolean approved = false;
                    Object approvedObj = doc.get("approved");
                    if (approvedObj instanceof Boolean) approved = (Boolean) approvedObj;
                    else if (approvedObj instanceof String) approved = "true".equalsIgnoreCase((String) approvedObj);

                    String role = doc.getString("role");
                    if (role == null) role = doc.getString("Role");

                    if (role != null) resolvedRole = role.trim();
                    else resolvedRole = "Staff";

                    cachedRole = resolvedRole;
                    cachedIsAdmin = "Admin".equalsIgnoreCase(cachedRole);

                    if (cachedIsAdmin) cachedIsApproved = true;
                    else cachedIsApproved = approved;

                    String ownerAdminId = doc.getString("ownerAdminId");
                    if (ownerAdminId != null && !ownerAdminId.isEmpty()) {
                        FirestoreManager.getInstance().setBusinessOwnerId(ownerAdminId);
                    } else {
                        FirestoreManager.getInstance().setBusinessOwnerId(uid);
                    }
                } else {
                    FirestoreManager.getInstance().setBusinessOwnerId(uid);
                }

                // FIX: Trigger data download
                triggerDataSync();

                callback.onComplete(resolvedRole);
            });
        });
    }

    public void fetchPendingStaffAccounts(final UsersCallback callback) {
        fStore.collection("users").whereEqualTo("approved", false).get().addOnCompleteListener(task -> {
            List<AdminUserItem> list = new ArrayList<>();
            if (task.isSuccessful() && task.getResult() != null) {
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    String uid = doc.getId();
                    String email = doc.getString("email");
                    if (email == null) email = doc.getString("Email");
                    String name = doc.getString("name");
                    if (name == null) name = doc.getString("Name");
                    String phone = doc.getString("phone");
                    if (phone == null) phone = doc.getString("Phone");
                    if (phone == null) phone = "";

                    boolean approved = false;
                    Object approvedObj = doc.get("approved");
                    if (approvedObj instanceof Boolean) approved = (Boolean) approvedObj;

                    AdminUserItem u = new AdminUserItem(uid, name != null ? name : "", email != null ? email : "", phone, "Staff", approved);
                    list.add(u);
                }
            }
            callback.onComplete(list);
        });
    }

    public void fetchAllStaffAccounts(final UsersCallback callback) {
        String adminId = getCurrentUserId();
        fStore.collection("users").whereEqualTo("ownerAdminId", adminId).whereEqualTo("approved", true).get().addOnCompleteListener(task -> {
            List<AdminUserItem> list = new ArrayList<>();
            if (task.isSuccessful() && task.getResult() != null) {
                for (QueryDocumentSnapshot doc : task.getResult()) {
                    String uid = doc.getId();
                    String email = doc.getString("email");
                    if (email == null) email = doc.getString("Email");
                    String name = doc.getString("name");
                    if (name == null) name = doc.getString("Name");
                    String phone = doc.getString("phone");
                    if (phone == null) phone = doc.getString("Phone");
                    if (phone == null) phone = "";

                    boolean approved = true;
                    Object approvedObj = doc.get("approved");
                    if (approvedObj instanceof Boolean) approved = (Boolean) approvedObj;
                    else if (approvedObj instanceof String) approved = "true".equalsIgnoreCase((String) approvedObj);

                    String role = doc.getString("role");
                    if (role == null) role = doc.getString("Role");
                    if (role == null) role = "Staff";
                    AdminUserItem u = new AdminUserItem(uid, name != null ? name : "", email != null ? email : "", phone, role, approved);
                    list.add(u);
                }
            }
            if (callback != null) callback.onComplete(list);
        });
    }

    public void callAdminUpdateUser(String uid, Boolean approved, String role, Boolean setAsAdmin, final SimpleCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        if (approved != null) data.put("approved", approved);
        if (role != null) data.put("role", role);
        if (setAsAdmin != null) data.put("setAsAdmin", setAsAdmin);
        FirebaseFunctions.getInstance().getHttpsCallable("adminUpdateUser").call(data).addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                callback.onComplete(true);
            } else {
                callback.onComplete(false);
            }
        });
    }

    public void approveUser(String uid, SimpleCallback callback) {
        fStore.collection("users").document(uid).update("approved", true)
                .addOnCompleteListener(task -> {
                    if (callback != null) callback.onComplete(task.isSuccessful());
                });
    }

    public void promoteToAdmin(String uid, final SimpleCallback callback) {
        fStore.collection("users").document(uid).get().addOnCompleteListener(task -> {
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
            callAdminUpdateUser(uid, true, "Admin", true, success -> {
                if (!success) {
                    callback.onComplete(false);
                    return;
                }
                fStore.collection("admin").document(uid).set(adminData, com.google.firebase.firestore.SetOptions.merge()).addOnCompleteListener(setTask -> {
                    if (!setTask.isSuccessful()) {
                        callback.onComplete(false);
                        return;
                    }
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("role", "Admin");
                    updates.put("approved", true);
                    fStore.collection("users").document(uid).set(updates, com.google.firebase.firestore.SetOptions.merge()).addOnCompleteListener(upd -> callback.onComplete(upd.isSuccessful()));
                });
            });
        });
    }

    public void demoteAdmin(String uid, final SimpleCallback callback) {
        callAdminUpdateUser(uid, true, "Staff", false, success -> {
            if (!success) {
                callback.onComplete(false);
                return;
            }
            fStore.collection("admin").document(uid).delete().addOnCompleteListener(task -> {
                if (!task.isSuccessful()) {
                    callback.onComplete(false);
                    return;
                }
                Map<String, Object> updates = new HashMap<>();
                updates.put("role", "Staff");
                updates.put("approved", true);
                fStore.collection("users").document(uid).update(updates).addOnCompleteListener(upd -> callback.onComplete(upd.isSuccessful()));
            });
        });
    }

    // NEW: Flexible Promotion/Demotion System
    public void changeUserRole(String uid, String newRole, final SimpleCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("role", newRole);
        updates.put("approved", true); // Ensure they stay approved when role changes

        // Update the database
        fStore.collection("users").document(uid).update(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                // If they are demoted to Staff or Sub-Admin, make sure they are removed from the super-admin collection
                if (!newRole.equalsIgnoreCase("Admin")) {
                    fStore.collection("admin").document(uid).delete();
                }

                // Sync with Firebase Cloud Functions
                callAdminUpdateUser(uid, true, newRole, newRole.equalsIgnoreCase("Admin"), success -> {
                    callback.onComplete(true);
                });
            } else {
                callback.onComplete(false);
            }
        });
    }

    // =========================================================
    // AUTOMATED SHIFT MANAGEMENT (REVISION 3)
    // =========================================================
    private String activeShiftId = null;

    public void startAutomatedShift(String cashierName) {
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        String uid = getCurrentUserId();
        if (uid == null || ownerId == null) return;

        fStore.collection("SystemSettings").document(ownerId).get().addOnSuccessListener(doc -> {
            boolean autoShift = true; // Default to true if not set
            if (doc.exists() && doc.contains("autoShiftEnabled")) {
                Boolean val = doc.getBoolean("autoShiftEnabled");
                if (val != null) autoShift = val;
            }

            if (autoShift) {
                activeShiftId = "SHIFT_" + System.currentTimeMillis();
                Shift newShift = new Shift();
                newShift.setShiftId(activeShiftId);
                newShift.setCashierId(uid);
                newShift.setCashierName(cashierName != null ? cashierName : "Staff");
                newShift.setStartTime(System.currentTimeMillis());
                newShift.setActive(true);
                newShift.setLocked(false);
                newShift.setStatus("Ongoing");

                fStore.collection("shifts").document(ownerId).collection("records").document(activeShiftId).set(newShift.toMap());
            }
        });
    }

    public void endAutomatedShift() {
        if (activeShiftId == null) return;
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId != null) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("active", false);
            updates.put("endTime", System.currentTimeMillis());
            updates.put("status", "Completed");
            fStore.collection("shifts").document(ownerId).collection("records").document(activeShiftId).update(updates);
        }
        activeShiftId = null;
    }

    public void logShiftLock(boolean isLocking) {
        if (activeShiftId == null) return;
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (ownerId == null) return;

        String arrayField = isLocking ? "lockTimes" : "unlockTimes";

        Map<String, Object> updates = new HashMap<>();
        updates.put("locked", isLocking);
        updates.put(arrayField, com.google.firebase.firestore.FieldValue.arrayUnion(System.currentTimeMillis()));

        fStore.collection("shifts").document(ownerId).collection("records").document(activeShiftId).update(updates);
    }

    public void signOutAndCleanup(final Runnable onComplete) {
        final String uid = getCurrentUserId();
        final String owner = FirestoreManager.getInstance().getBusinessOwnerId();
        endAutomatedShift();

        if (application != null) {
            ProductRepository.getInstance(application).clearLocalData();
            SalesRepository.getInstance(application).clearData();
            try { DashboardRepository.getInstance().clearData(); } catch (Exception ignored) {}
            try { new ProductRemoteSyncer(application).stopListening(); } catch (Exception ignored) {}
        }

        FirestoreManager.getInstance().clearCachedIds();

        // FIX: Hard reset singletons to prevent stale data leaking between accounts
        try { ProductRepository.resetInstance(); } catch (Exception ignored) {}
        try { SalesRepository.resetInstance(); } catch (Exception ignored) {}
        try { FirestoreSyncListener.getInstance().reset(); } catch (Exception ignored) {}

        cachedIsAdmin = null;
        cachedIsApproved = null;
        cachedRole = null;

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