package com.app.SalesInventory;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class AdminCreateStaffsActivity extends BaseActivity {
    private EditText etStaffName;
    private EditText etStaffEmail;
    private EditText etStaffPhone;
    private EditText etStaffPassword;
    private EditText etStaffConfirmPassword;
    private Button btnCreateStaffAccount;
    private ProgressBar progressBarCreateStaff;
    private TextView tvCreateStaffMessage;

    private FirebaseFunctions functions;
    private FirebaseFirestore fStore;
    private AuthManager authManager;

    private static final Pattern NAME_PATTERN = Pattern.compile("^[A-Za-z\\s]+$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^\\+63\\d{10}$");

    public static void startForCreate(Context ctx) {
        Intent i = new Intent(ctx, AdminCreateStaffsActivity.class);
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_create_staffs);

        etStaffName = findViewById(R.id.etStaffName);
        etStaffEmail = findViewById(R.id.etStaffEmail);
        etStaffPhone = findViewById(R.id.etStaffPhone);
        etStaffPassword = findViewById(R.id.etStaffPassword);
        etStaffConfirmPassword = findViewById(R.id.etStaffConfirmPassword);
        btnCreateStaffAccount = findViewById(R.id.btnCreateStaffAccount);
        progressBarCreateStaff = findViewById(R.id.progressBarCreateStaff);
        tvCreateStaffMessage = findViewById(R.id.tvCreateStaffMessage);

        functions = FirebaseFunctions.getInstance();
        fStore = FirebaseFirestore.getInstance();
        authManager = AuthManager.getInstance();

        btnCreateStaffAccount.setOnClickListener(v -> createStaffAccount());
    }

    private void createStaffAccount() {
        tvCreateStaffMessage.setVisibility(View.GONE);
        String name = etStaffName.getText().toString().trim();
        String email = etStaffEmail.getText().toString().trim();
        String phone = etStaffPhone.getText().toString().trim();
        String password = etStaffPassword.getText().toString();
        String confirm = etStaffConfirmPassword.getText().toString();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showMessage("Name, Email and Password are required");
            return;
        }
        if (!NAME_PATTERN.matcher(name).matches()) {
            showMessage("Name must contain only letters and spaces");
            return;
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            showMessage("Invalid email address");
            return;
        }
        if (!password.equals(confirm)) {
            showMessage("Passwords do not match");
            return;
        }
        if (password.length() < 6) {
            showMessage("Password must be at least 6 characters");
            return;
        }
        if (TextUtils.isEmpty(phone)) {
            showMessage("Phone number is required and must start with +63 followed by 10 digits");
            return;
        }
        if (!PHONE_PATTERN.matcher(phone).matches()) {
            showMessage("Phone must be in the format +63XXXXXXXXXX (10 digits after +63)");
            return;
        }

        progressBarCreateStaff.setVisibility(View.VISIBLE);
        btnCreateStaffAccount.setEnabled(false);
        tvCreateStaffMessage.setVisibility(View.GONE);

        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("password", password);
        data.put("name", name);
        data.put("phone", phone);

        functions.getHttpsCallable("adminCreateStaffUser").call(data)
                .addOnCompleteListener(task -> {
                    progressBarCreateStaff.setVisibility(View.GONE);
                    btnCreateStaffAccount.setEnabled(true);
                    if (!task.isSuccessful() || task.getResult() == null) {
                        Exception e = task.getException();
                        Log.e("AdminCreateStaff", "createStaff failed", e);
                        String msg = e != null ? e.getMessage() : "Unknown";
                        showMessage("Failed to create staff account: " + msg);
                        return;
                    }
                    Object res = task.getResult().getData();
                    String newUid = null;
                    if (res instanceof Map) {
                        Object uidObj = ((Map<?, ?>) res).get("uid");
                        if (uidObj instanceof String) newUid = (String) uidObj;
                    }
                    if (newUid == null) {
                        showMessage("Staff created but uid missing");
                        return;
                    }
                    String currentAdminId = authManager.getCurrentUserId();
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("uid", newUid);
                    userData.put("name", name);
                    userData.put("email", email);
                    userData.put("phone", phone);
                    userData.put("role", "Staff");
                    userData.put("approved", true);
                    userData.put("ownerAdminId", currentAdminId);
                    fStore.collection("users").document(newUid).set(userData, SetOptions.merge())
                            .addOnCompleteListener(t -> {
                                if (t.isSuccessful()) {
                                    setResult(RESULT_OK);
                                    Toast.makeText(this, "Staff account created", Toast.LENGTH_SHORT).show();
                                    finish();
                                } else {
                                    showMessage("Failed to save staff data");
                                }
                            });
                });
    }

    private void showMessage(String msg) {
        tvCreateStaffMessage.setText(msg);
        tvCreateStaffMessage.setVisibility(View.VISIBLE);
    }

    public interface UpdateCallback {
        void onComplete(boolean success);
    }

    public static void updateStaffAccount(Context ctx,
                                          String staffUid,
                                          String name,
                                          String email,
                                          String phone,
                                          String newPassword,
                                          UpdateCallback callback) {
        FirebaseFirestore fStore = FirebaseFirestore.getInstance();
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("email", email);
        updates.put("phone", phone);
        fStore.collection("users").document(staffUid).set(updates, SetOptions.merge())
                .addOnCompleteListener(t -> {
                    if (!t.isSuccessful()) {
                        if (callback != null) callback.onComplete(false);
                        return;
                    }
                    if (newPassword != null && !newPassword.isEmpty()) {
                        FirebaseFunctions functions = FirebaseFunctions.getInstance();
                        Map<String, Object> data = new HashMap<>();
                        data.put("uid", staffUid);
                        data.put("password", newPassword);
                        functions.getHttpsCallable("adminSetUserPassword").call(data)
                                .addOnCompleteListener(pwTask -> {
                                    if (callback != null) callback.onComplete(pwTask.isSuccessful());
                                });
                    } else {
                        if (callback != null) callback.onComplete(true);
                    }
                });
    }
}