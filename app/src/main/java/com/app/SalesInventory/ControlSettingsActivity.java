package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import android.text.InputFilter;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import androidx.annotation.NonNull;

import java.util.HashMap;
import java.util.Map;

public class ControlSettingsActivity extends BaseActivity {

    private com.google.android.material.textfield.TextInputEditText etDefaultMarkup, etTaxRate;
    private Button btnSavePricing, btnSaveTax, btnViewAttendanceLogs, btnCreatePromo, btnManagePromos;
    private com.google.android.material.switchmaterial.SwitchMaterial switchEnableTax;

    private DatabaseReference systemSettingsRef;
    private String currentUserId;
    private Spinner spinnerBusinessType, spinnerTaxType;

    private boolean isPricingLocked = false;
    private boolean isTaxLocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_control_settings);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("System Controls");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        currentUserId = FirestoreManager.getInstance().getBusinessOwnerId();
        if (currentUserId == null || currentUserId.isEmpty()) {
            currentUserId = AuthManager.getInstance().getCurrentUserId();
        }
        systemSettingsRef = FirebaseDatabase.getInstance().getReference("SystemSettings").child(currentUserId);

        // Bind Views
        etDefaultMarkup = findViewById(R.id.etDefaultMarkup);
        btnSavePricing = findViewById(R.id.btnSavePricing);
        btnViewAttendanceLogs = findViewById(R.id.btnViewAttendanceLogs);
        btnCreatePromo = findViewById(R.id.btnCreatePromo);
        btnManagePromos = findViewById(R.id.btnManagePromos);

        // Tax Bindings
        etTaxRate = findViewById(R.id.etTaxRate);
        switchEnableTax = findViewById(R.id.switchEnableTax);
        spinnerTaxType = findViewById(R.id.spinnerTaxType);
        btnSaveTax = findViewById(R.id.btnSaveTax);

        etDefaultMarkup.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(3) });

        setupBusinessTierPresets();
        setupTaxSpinner();
        loadCurrentSettings();

        btnSavePricing.setOnClickListener(v -> {
            if (isPricingLocked) togglePricingLock(false);
            else savePricingSettings();
        });

        btnSaveTax.setOnClickListener(v -> {
            if (isTaxLocked) {
                toggleTaxLock(false);
            } else {
                saveTaxSettings();
            }
        });

        btnViewAttendanceLogs.setOnClickListener(v -> startActivity(new Intent(this, AttendanceLogsActivity.class)));
        btnCreatePromo.setOnClickListener(v -> startActivity(new Intent(this, CreatePromoActivity.class)));
        btnManagePromos.setOnClickListener(v -> startActivity(new Intent(this, ManagePromosActivity.class)));
    }

    private void setupBusinessTierPresets() {
        spinnerBusinessType = findViewById(R.id.spinnerBusinessType);
        String[] businessTypes = {"Custom / Manual", "Street Shop / Kiosk (100%)", "Local Cafe (200%)", "Premium / Mall (350%)"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, businessTypes);
        spinnerBusinessType.setAdapter(adapter);

        spinnerBusinessType.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, android.view.View view, int position, long id) {
                if (!isPricingLocked) {
                    if (position == 1) etDefaultMarkup.setText("100");
                    else if (position == 2) etDefaultMarkup.setText("200");
                    else if (position == 3) etDefaultMarkup.setText("350");
                }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });
    }

    private void setupTaxSpinner() {
        String[] taxTypes = {"Inclusive (Tax is inside the price)", "Exclusive (Tax added on top of price)"};
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, taxTypes);
        spinnerTaxType.setAdapter(adapter);
    }

    private void toggleTaxLock(boolean lock) {
        isTaxLocked = lock;
        switchEnableTax.setEnabled(!lock);
        etTaxRate.setEnabled(!lock);
        spinnerTaxType.setEnabled(!lock);
        btnSaveTax.setText(lock ? "Edit Tax Settings" : "Save Tax Settings");
    }

    private void loadCurrentSettings() {
        systemSettingsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Load Pricing
                    Double defaultMarkup = snapshot.child("defaultMarkupPercent").getValue(Double.class);
                    if (defaultMarkup != null) {
                        etDefaultMarkup.setText(String.valueOf(defaultMarkup));
                        isPricingLocked = true;
                    }
                    String savedTier = snapshot.child("businessTierProfile").getValue(String.class);
                    if (savedTier != null && spinnerBusinessType != null) {
                        for (int i = 0; i < spinnerBusinessType.getCount(); i++) {
                            if (spinnerBusinessType.getItemAtPosition(i).toString().equals(savedTier)) {
                                spinnerBusinessType.setSelection(i); break;
                            }
                        }
                    }
                    togglePricingLock(isPricingLocked);

                    // Load Tax Settings
                    Boolean isTaxEnabled = snapshot.child("taxEnabled").getValue(Boolean.class);
                    if (isTaxEnabled != null) switchEnableTax.setChecked(isTaxEnabled);

                    Double taxRate = snapshot.child("taxRate").getValue(Double.class);
                    if (taxRate != null) {
                        etTaxRate.setText(String.valueOf(taxRate));
                        isTaxLocked = true;
                    }

                    String taxType = snapshot.child("taxType").getValue(String.class);
                    if (taxType != null) {
                        if (taxType.equals("Exclusive")) spinnerTaxType.setSelection(1);
                        else spinnerTaxType.setSelection(0);
                    }
                    toggleTaxLock(isTaxLocked);

                } else {
                    togglePricingLock(false);
                    toggleTaxLock(false);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void savePricingSettings() {
        String markupStr = etDefaultMarkup.getText() != null ? etDefaultMarkup.getText().toString() : "0";
        double markupValue = markupStr.isEmpty() ? 0 : Double.parseDouble(markupStr);

        Map<String, Object> updates = new HashMap<>();
        updates.put("usePercentageMarkup", true);
        updates.put("defaultMarkupPercent", markupValue);
        if (spinnerBusinessType != null && spinnerBusinessType.getSelectedItem() != null) {
            updates.put("businessTierProfile", spinnerBusinessType.getSelectedItem().toString());
        }

        systemSettingsRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Pricing Strategy Saved!", Toast.LENGTH_SHORT).show();
                togglePricingLock(true);
            }
        });
    }

    private void saveTaxSettings() {
        String rateStr = etTaxRate.getText() != null ? etTaxRate.getText().toString() : "0";
        double rateValue = rateStr.isEmpty() ? 0 : Double.parseDouble(rateStr);

        Map<String, Object> updates = new HashMap<>();
        updates.put("taxEnabled", switchEnableTax.isChecked());
        updates.put("taxRate", rateValue);

        String selectedType = spinnerTaxType.getSelectedItem().toString().contains("Inclusive") ? "Inclusive" : "Exclusive";
        updates.put("taxType", selectedType);

        systemSettingsRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(this, "Tax Settings Saved!", Toast.LENGTH_SHORT).show();
                toggleTaxLock(true); // LOCK after success
            } else {
                Toast.makeText(this, "Error saving tax settings", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void togglePricingLock(boolean lock) {
        isPricingLocked = lock;
        etDefaultMarkup.setEnabled(!lock);
        spinnerBusinessType.setEnabled(!lock);
        btnSavePricing.setText(lock ? "Edit Pricing Rule" : "Save Pricing Rule");
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }
}