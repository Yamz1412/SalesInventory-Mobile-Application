package com.app.SalesInventory;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.functions.FirebaseFunctions;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.Map;

public class AdminCreateStaffsActivity extends BaseActivity {
    private EditText etStaffName;
    private EditText etStaffUsername;
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

    public static void startForCreate(Context ctx) {
        Intent i = new Intent(ctx, AdminCreateStaffsActivity.class);
        ctx.startActivity(i);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_create_staffs);

        etStaffName = findViewById(R.id.etStaffFullName);
        etStaffUsername = findViewById(R.id.etStaffUsername);
        etStaffEmail = findViewById(R.id.etStaffEmail);
        etStaffPhone = findViewById(R.id.etStaffPhone);
        etStaffPassword = findViewById(R.id.etStaffPassword);
        etStaffConfirmPassword = findViewById(R.id.etStaffConfirmPassword);
        btnCreateStaffAccount = findViewById(R.id.btnCreateStaffAccount);
        progressBarCreateStaff = findViewById(R.id.progressBarCreateStaff);
        tvCreateStaffMessage = findViewById(R.id.tvCreateStaffMessage);

        InputFilter nameFilter = new InputFilter() {
            public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
                for (int i = start; i < end; i++) {
                    char c = source.charAt(i);
                    if (!Character.isLetter(c) && c != ' ' && c != '.') {
                        return "";
                    }
                }
                return null;
            }
        };
        etStaffName.setFilters(new InputFilter[]{nameFilter});

        etStaffPhone.setInputType(InputType.TYPE_CLASS_NUMBER);
        InputFilter phoneFilter = new InputFilter.LengthFilter(11);
        etStaffPhone.setFilters(new InputFilter[]{phoneFilter});

        functions = FirebaseFunctions.getInstance();
        fStore = FirebaseFirestore.getInstance();
        authManager = AuthManager.getInstance();

        btnCreateStaffAccount.setOnClickListener(v -> createStaffAccount());
    }

    private void createStaffAccount() {
        String name = etStaffName.getText().toString().trim();
        String email = etStaffEmail.getText().toString().trim().toLowerCase(); // Convert to lowercase for checking
        String phone = etStaffPhone.getText().toString().trim();
        String password = etStaffPassword.getText().toString();
        String confirm = etStaffConfirmPassword.getText().toString();

        if (TextUtils.isEmpty(name) || TextUtils.isEmpty(email) || TextUtils.isEmpty(password)) {
            showMessage("Name, Email and Password are required");
            return;
        }

        if (!phone.matches("^09\\d{9}$")) {
            showMessage("Phone number must be exactly 11 digits and start with 09 (e.g., 09123456789)");
            return;
        }

        String emailPattern = "^[a-zA-Z0-9._%+-]+@(gmail\\.com|yahoo\\.com|outlook\\.com|hotmail\\.com|live\\.com)$";
        if (!email.matches(emailPattern)) {
            showMessage("Please use a recognized email provider (Gmail, Yahoo, Outlook, Hotmail).");
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

        String currentAdminId = authManager.getCurrentUserId();
        if (currentAdminId == null) {
            showMessage("Error: You must be logged in as Admin to create staff.");
            return;
        }

        progressBarCreateStaff.setVisibility(View.VISIBLE);
        tvCreateStaffMessage.setVisibility(View.GONE);

        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("password", password);
        data.put("name", name);
        data.put("phone", phone);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            user.getIdToken(true).addOnCompleteListener(tokenTask -> {
                if (tokenTask.isSuccessful()) {
                    functions.getHttpsCallable("adminCreateStaffUser").call(data)
                            .addOnCompleteListener(task -> {
                                progressBarCreateStaff.setVisibility(View.GONE);
                                if (!task.isSuccessful() || task.getResult() == null) {
                                    Exception e = task.getException();
                                    Log.e("AdminCreateStaff", "createStaff failed", e);
                                    String msg = e != null ? e.getMessage() : "Unknown error";
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

                                Map<String, Object> userData = new HashMap<>();
                                userData.put("uid", newUid);
                                userData.put("name", name);

                                String username = etStaffUsername != null && etStaffUsername.getText() != null ? etStaffUsername.getText().toString().trim().toLowerCase() : "";
                                userData.put("username", username);

                                userData.put("email", email);
                                userData.put("phone", phone);
                                userData.put("role", "Staff");
                                userData.put("approved", true);
                                userData.put("ownerAdminId", currentAdminId);

                                fStore.collection("users").document(newUid).set(userData, SetOptions.merge())
                                        .addOnCompleteListener(t -> {
                                            if (t.isSuccessful()) {
                                                Toast.makeText(this, "Staff account created successfully", Toast.LENGTH_SHORT).show();
                                                clearForm();
                                                finish();
                                            } else {
                                                showMessage("Failed to save staff data: " + (t.getException() != null ? t.getException().getMessage() : "Unknown"));
                                            }
                                        });
                            });
                } else {
                    progressBarCreateStaff.setVisibility(View.GONE);
                    showMessage("Failed to refresh admin session. Please logout and login again.");
                }
            });
        } else {
            progressBarCreateStaff.setVisibility(View.GONE);
            showMessage("User not authenticated.");
        }
    }

    private void clearForm() {
        etStaffName.setText("");
        etStaffEmail.setText("");
        etStaffPhone.setText("");
        etStaffPassword.setText("");
        etStaffConfirmPassword.setText("");
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