package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

public class ControlSettingsActivity extends BaseActivity {

    private SwitchMaterial switchEnableMarkup, switchAutoShift;
    private TextInputEditText etDefaultMarkup;
    private Button btnSavePricing, btnViewAttendanceLogs, btnCreatePromo, btnManagePromos;

    private DatabaseReference systemSettingsRef;
    private String currentUserId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("System Controls");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Get Owner ID
        currentUserId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (currentUserId == null || currentUserId.isEmpty()) {
            currentUserId = AuthManager.getInstance().getCurrentUserId();
        }

        systemSettingsRef = FirebaseDatabase.getInstance().getReference("SystemSettings").child(currentUserId);

        // Initialize Views
        switchEnableMarkup = findViewById(R.id.switchEnableMarkup);
        etDefaultMarkup = findViewById(R.id.etDefaultMarkup);
        btnSavePricing = findViewById(R.id.btnSavePricing);

        switchAutoShift = findViewById(R.id.switchAutoShift);
        btnViewAttendanceLogs = findViewById(R.id.btnViewAttendanceLogs);

        btnCreatePromo = findViewById(R.id.btnCreatePromo);
        btnManagePromos = findViewById(R.id.btnManagePromos);

        loadCurrentSettings();

        // Listeners
        btnSavePricing.setOnClickListener(v -> savePricingSettings());

        switchAutoShift.setOnCheckedChangeListener((buttonView, isChecked) -> {
            systemSettingsRef.child("autoShiftEnabled").setValue(isChecked);
            Toast.makeText(this, isChecked ? "Auto Time-In Enabled" : "Auto Time-In Disabled", Toast.LENGTH_SHORT).show();
        });

        // --- NEW: LINKED NAVIGATION TO THE NEW SCREENS ---
        btnViewAttendanceLogs.setOnClickListener(v -> {
            startActivity(new Intent(this, AttendanceLogsActivity.class));
        });

        btnCreatePromo.setOnClickListener(v -> {
            startActivity(new Intent(this, CreatePromoActivity.class));
        });

        btnManagePromos.setOnClickListener(v -> {
            startActivity(new Intent(this, ManagePromosActivity.class));
        });
    }

    private void loadCurrentSettings() {
        systemSettingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Boolean isMarkupEnabled = snapshot.child("usePercentageMarkup").getValue(Boolean.class);
                    Double defaultMarkup = snapshot.child("defaultMarkupPercent").getValue(Double.class);
                    Boolean isAutoShift = snapshot.child("autoShiftEnabled").getValue(Boolean.class);

                    if (isMarkupEnabled != null) switchEnableMarkup.setChecked(isMarkupEnabled);
                    if (defaultMarkup != null) etDefaultMarkup.setText(String.valueOf(defaultMarkup));
                    if (isAutoShift != null) switchAutoShift.setChecked(isAutoShift);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ControlSettingsActivity.this, "Failed to load settings", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void savePricingSettings() {
        boolean useMarkup = switchEnableMarkup.isChecked();
        String markupStr = etDefaultMarkup.getText() != null ? etDefaultMarkup.getText().toString() : "0";
        double markupValue = markupStr.isEmpty() ? 0 : Double.parseDouble(markupStr);

        Map<String, Object> updates = new HashMap<>();
        updates.put("usePercentageMarkup", useMarkup);
        updates.put("defaultMarkupPercent", markupValue);

        systemSettingsRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Pricing Strategy Saved!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Error saving settings", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}