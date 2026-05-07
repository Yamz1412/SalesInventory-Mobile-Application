package com.app.SalesInventory;

import android.app.Application;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
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
        if (cachedRole == null) return false;
        String r = cachedRole.trim();
        return r.equalsIgnoreCase("Sub-Admin") ||
                r.equalsIgnoreCase("Sub Admin") ||
                r.equalsIgnoreCase("Subadmin");
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
        // NEW: INSTANT SYNCHRONOUS ROUTING
        // Ensures the app knows exactly whose database to look at (Admin vs Staff) the second it opens!
        android.content.SharedPreferences prefs = app.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE);
        String cachedOwner = prefs.getString("business_owner_routing", null);
        if (cachedOwner != null && !cachedOwner.isEmpty()) {
            FirestoreManager.getInstance().setBusinessOwnerId(cachedOwner);
        }
    }

    private void triggerDataSync() {
        try {
            FirestoreSyncListener.getInstance().restartAllListeners();
            if (application != null) {
                SalesRepository.getInstance(application).reloadAllData();
                new ProductRemoteSyncer(application).startListening();
            } else {
                SalesRepository.getInstance().reloadAllData();
            }
        } catch (Exception e) {
            e.printStackTrace();
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
                if (application != null) {
                    application.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putString("business_owner_routing", uid).apply();
                }

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
        cachedIsAdmin = "Admin".equalsIgnoreCase(cachedRole) || "Owner".equalsIgnoreCase(cachedRole) || "BusinessOwner".equalsIgnoreCase(cachedRole);

        if (cachedIsAdmin) approved = true;
        cachedIsApproved = approved;

        String ownerAdminId = doc.getString("ownerAdminId");
        if (ownerAdminId != null && !ownerAdminId.isEmpty()) {
            FirestoreManager.getInstance().setBusinessOwnerId(ownerAdminId);
        } else {
            FirestoreManager.getInstance().setBusinessOwnerId(uid);
        }

        if (application != null) {
            application.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                    .edit().putString("business_owner_routing", FirestoreManager.getInstance().getBusinessOwnerId()).apply();
        }

        triggerDataSync();
        callback.onComplete(cachedIsAdmin || cachedIsApproved);
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
                if (application != null) {
                    application.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                            .edit().putString("business_owner_routing", uid).apply();
                }

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

                    if (application != null) {
                        application.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                                .edit().putString("business_owner_routing", FirestoreManager.getInstance().getBusinessOwnerId()).apply();
                    }
                } else {
                    FirestoreManager.getInstance().setBusinessOwnerId(uid);
                }

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

    public void changeUserRole(String uid, String newRole, final SimpleCallback callback) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("role", newRole);
        updates.put("approved", true);

        fStore.collection("users").document(uid).update(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                if (!newRole.equalsIgnoreCase("Admin")) {
                    fStore.collection("admin").document(uid).delete();
                }
                callAdminUpdateUser(uid, true, newRole, newRole.equalsIgnoreCase("Admin"), success -> {
                    callback.onComplete(true);
                });
            } else {
                callback.onComplete(false);
            }
        });
    }

    private String activeShiftId = null;

    public void startAutomatedShift(String cashierName) {
        String uid = getCurrentUserId();
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();

        if (uid == null || uid.isEmpty() || ownerId == null || ownerId.isEmpty()) return;

        FirebaseFirestore.getInstance().collection("users").document(ownerId).collection("shifts")
                .whereEqualTo("cashierId", uid)
                .whereEqualTo("active", true)
                .get().addOnSuccessListener(query -> {
                    boolean createNew = true;

                    if (!query.isEmpty()) {
                        DocumentSnapshot doc = query.getDocuments().get(0);
                        Long startTime = doc.getLong("startTime");

                        // Check if the active shift is ACTUALLY from today
                        if (startTime != null && android.text.format.DateUtils.isToday(startTime)) {
                            // Valid shift for today, resume it!
                            activeShiftId = doc.getId();
                            createNew = false;

                            // Ensure UI is corrected to unlocked since they just opened the app
                            Map<String, Object> updates = new HashMap<>();
                            updates.put("locked", false);

                            // Balance the arrays if it was stuck on break
                            List<Long> locks = (List<Long>) doc.get("lockTimes");
                            List<Long> unlocks = (List<Long>) doc.get("unlockTimes");
                            if (locks != null && unlocks != null && locks.size() > unlocks.size()) {
                                updates.put("unlockTimes", com.google.firebase.firestore.FieldValue.arrayUnion(System.currentTimeMillis()));
                            }
                            doc.getReference().update(updates);

                        } else {
                            // Old ghost shift from a previous day! Close it out.
                            Map<String, Object> closeUpdates = new HashMap<>();
                            closeUpdates.put("active", false);
                            closeUpdates.put("locked", false);
                            closeUpdates.put("status", "CLOSED");
                            closeUpdates.put("endTime", System.currentTimeMillis());

                            List<Long> locks = (List<Long>) doc.get("lockTimes");
                            List<Long> unlocks = (List<Long>) doc.get("unlockTimes");
                            if (locks != null && unlocks != null && locks.size() > unlocks.size()) {
                                closeUpdates.put("unlockTimes", com.google.firebase.firestore.FieldValue.arrayUnion(System.currentTimeMillis()));
                            }
                            doc.getReference().update(closeUpdates);
                        }
                    }

                    if (createNew) {
                        DocumentReference newShiftRef = FirebaseFirestore.getInstance()
                                .collection("users").document(ownerId).collection("shifts").document();

                        Shift shift = new Shift();
                        shift.setShiftId(newShiftRef.getId());
                        shift.setCashierId(uid);
                        shift.setCashierName(cashierName);
                        shift.setStartTime(System.currentTimeMillis());
                        shift.setActive(true);
                        shift.setStatus("ACTIVE");
                        shift.setLocked(false);

                        newShiftRef.set(shift.toMap());
                        activeShiftId = newShiftRef.getId();
                    }
                });
    }

    public void endAutomatedShift() {
        String uid = getCurrentUserId();
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();

        // CRITICAL FIX: Ensure uid and ownerId are not empty strings!
        if (uid == null || uid.isEmpty() || ownerId == null || ownerId.isEmpty()) return;

        FirebaseFirestore.getInstance().collection("users").document(ownerId).collection("shifts")
                .whereEqualTo("cashierId", uid)
                .whereEqualTo("active", true)
                .get().addOnSuccessListener(query -> {
                    if (!query.isEmpty()) {
                        DocumentSnapshot doc = query.getDocuments().get(0);
                        Map<String, Object> updates = new HashMap<>();
                        updates.put("active", false);
                        updates.put("status", "CLOSED");
                        updates.put("endTime", System.currentTimeMillis());

                        List<Long> locks = (List<Long>) doc.get("lockTimes");
                        List<Long> unlocks = (List<Long>) doc.get("unlockTimes");
                        if (locks != null && unlocks != null && locks.size() > unlocks.size()) {
                            unlocks.add(System.currentTimeMillis());
                            updates.put("unlockTimes", unlocks);
                            updates.put("locked", false);
                        }
                        doc.getReference().update(updates);
                    }
                });
    }

    public void logShiftLock(boolean isLocking) {
        String uid = getCurrentUserId();
        String ownerId = FirestoreManager.getInstance().getBusinessOwnerId();

        if (uid == null || uid.isEmpty() || ownerId == null || ownerId.isEmpty()) return;

        if (activeShiftId != null) {
            executeLockUpdate(ownerId, activeShiftId, isLocking);
        } else {
            fStore.collection("users").document(ownerId).collection("shifts")
                    .whereEqualTo("cashierId", uid)
                    .whereEqualTo("active", true)
                    .get().addOnSuccessListener(query -> {
                        if (!query.isEmpty()) {
                            activeShiftId = query.getDocuments().get(0).getId();
                            executeLockUpdate(ownerId, activeShiftId, isLocking);
                        }
                    });
        }
    }

    private void executeLockUpdate(String ownerId, String shiftId, boolean isLocking) {
        String arrayField = isLocking ? "lockTimes" : "unlockTimes";
        Map<String, Object> updates = new HashMap<>();
        updates.put("locked", isLocking);
        updates.put(arrayField, com.google.firebase.firestore.FieldValue.arrayUnion(System.currentTimeMillis()));

        fStore.collection("users").document(ownerId)
                .collection("shifts").document(shiftId)
                .update(updates);
    }

    public void signOutAndCleanup(final Runnable onComplete) {
        final String uid = getCurrentUserId();
        final String owner = FirestoreManager.getInstance().getBusinessOwnerId();

        // CRITICAL FIX: Explicitly set offline in Firestore BEFORE wiping the auth session
        if (uid != null && !uid.isEmpty()) {
            long now = System.currentTimeMillis();
            Map<String, Object> updates = new HashMap<>();
            updates.put("isOnline", false);
            updates.put("lastActive", now);
            fStore.collection("users").document(uid).set(updates, SetOptions.merge());

            FirebaseDatabase.getInstance().getReference("UsersStatus").child(uid).child("status").setValue("offline");
            FirebaseDatabase.getInstance().getReference("UsersStatus").child(uid).child("lastActive").setValue(now);
        }

        endAutomatedShift();

        if (application != null) {
            java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                try { AppDatabase.getInstance(application).clearAllTables(); } catch (Exception ignored) {}
            });

            application.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .remove("business_owner_routing")
                    .remove("user_id")
                    .remove("business_owner")
                    .remove("user_role")
                    .apply();

            try { ProductRepository.getInstance(application).clearLocalData(); } catch (Exception ignored) {}
            try { SalesRepository.getInstance(application).clearData(); } catch (Exception ignored) {}
            try { DashboardRepository.getInstance().clearData(); } catch (Exception ignored) {}
            try { new ProductRemoteSyncer(application).stopListening(); } catch (Exception ignored) {}
        }

        FirestoreManager.getInstance().clearCachedIds();
        FirestoreManager.resetInstance();

        try { ProductRepository.resetInstance(); } catch (Exception ignored) {}
        try { SalesRepository.resetInstance(); } catch (Exception ignored) {}
        try { FirestoreSyncListener.getInstance().reset(); } catch (Exception ignored) {}

        cachedIsAdmin = null;
        cachedIsApproved = null;
        cachedRole = null;

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            String token = null;
            if (task.isSuccessful()) token = task.getResult();

            if (uid != null && !uid.isEmpty() && token != null && !token.isEmpty()) {
                try {
                    fStore.collection("users").document(uid).collection("devices").document(token).delete();
                } catch (Exception ignored) {}
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