package com.app.SalesInventory;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.FileProvider;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

public class SettingsActivity extends BaseActivity {
    private static final String TAG = "SettingsActivity";

    private RadioGroup rgTheme;
    private RadioButton rbLight, rbDark, rbDefault;
    private SwitchMaterial switchColorblind;
    private SwitchMaterial switchAutoBackup;
    private Button btnBackup, btnRestore, btnUserManual, btnClearCache, btnSystemControls;
    private TextView tvAdminTitle;
    private View cardAdmin;

    private ActivityResultLauncher<String> backupLauncher;
    private ActivityResultLauncher<String[]> restoreLauncher;

    private AlertDialog clearCacheDialog;
    private AlertDialog restoreConfirmDialog;
    private SharedPreferences prefs;

    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        authManager = AuthManager.getInstance();

        initializeUI();
        setupLaunchers();
        loadSavedPreferences();
        setupListeners();

        checkUserRoleForPermissions();
    }

    private void initializeUI() {
        rgTheme = findViewById(R.id.rgTheme);
        rbLight = findViewById(R.id.rbLight);
        rbDark = findViewById(R.id.rbDark);
        rbDefault = findViewById(R.id.rbDefault);
        switchColorblind = findViewById(R.id.switchColorblind);
        switchAutoBackup = findViewById(R.id.switchAutoBackup);

        btnBackup = findViewById(R.id.btnBackup);
        btnRestore = findViewById(R.id.btnRestore);
        btnUserManual = findViewById(R.id.btnUserManual);
        btnClearCache = findViewById(R.id.btnClearCache);

        tvAdminTitle = findViewById(R.id.tvAdminTitle);
        cardAdmin = findViewById(R.id.cardAdmin);
        btnSystemControls = findViewById(R.id.btnSystemControls);
    }

    private void checkUserRoleForPermissions() {
        authManager.refreshCurrentUserStatus(success -> {
            runOnUiThread(() -> {
                if (authManager.isCurrentUserAdmin()) {
                    tvAdminTitle.setVisibility(View.VISIBLE);
                    cardAdmin.setVisibility(View.VISIBLE);
                } else {
                    tvAdminTitle.setVisibility(View.GONE);
                    cardAdmin.setVisibility(View.GONE);
                }
            });
        });
    }

    private void loadSavedPreferences() {
        ThemeManager.Theme currentTheme = ThemeManager.getInstance(this).getCurrentTheme();
        if ("dark".equalsIgnoreCase(currentTheme.name)) {
            rbDark.setChecked(true);
        } else if ("light".equalsIgnoreCase(currentTheme.name)) {
            rbLight.setChecked(true);
        } else {
            rbDefault.setChecked(true);
        }

        boolean isColorblind = prefs.getBoolean("ColorblindMode", false);
        switchColorblind.setChecked(isColorblind);

        boolean isAutoBackupOn = prefs.getBoolean("AutoBackupEnabled", false);
        switchAutoBackup.setChecked(isAutoBackupOn);
        btnBackup.setVisibility(isAutoBackupOn ? View.GONE : View.VISIBLE);
    }

    private void setupListeners() {
        btnSystemControls.setOnClickListener(v -> startActivity(new Intent(this, ControlSettingsActivity.class)));

        rgTheme.setOnCheckedChangeListener((group, checkedId) -> {
            String selectedTheme = "default";
            if (checkedId == R.id.rbDark) selectedTheme = "dark";
            else if (checkedId == R.id.rbLight) selectedTheme = "light";

            String currentTheme = ThemeManager.getInstance(this).getCurrentTheme().name;
            if (!selectedTheme.equals(currentTheme)) {
                ThemeManager.getInstance(this).setCurrentTheme(selectedTheme);
                safeRecreate();
            }
        });

        switchColorblind.setOnCheckedChangeListener((buttonView, isChecked) -> {
            boolean currentVal = prefs.getBoolean("ColorblindMode", false);
            if (currentVal != isChecked) {
                prefs.edit().putBoolean("ColorblindMode", isChecked).apply();
                Toast.makeText(this, "Colorblind accessibility updated.", Toast.LENGTH_SHORT).show();
                safeRecreate();
            }
        });

        // NEW: Auto-Backup Switch Logic
        switchAutoBackup.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("AutoBackupEnabled", isChecked).apply();

            if (isChecked) {
                btnBackup.setVisibility(View.GONE);
                PeriodicWorkRequest backupWorkRequest = new PeriodicWorkRequest.Builder(
                        AutoBackupWorker.class, 24, TimeUnit.HOURS).build();
                WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                        "DailyAutoBackup", ExistingPeriodicWorkPolicy.REPLACE, backupWorkRequest);
                Toast.makeText(this, "Automated backups enabled (Every 24hrs)", Toast.LENGTH_SHORT).show();
            } else {
                btnBackup.setVisibility(View.VISIBLE);
                WorkManager.getInstance(this).cancelUniqueWork("DailyAutoBackup");
                Toast.makeText(this, "Automated backups disabled", Toast.LENGTH_SHORT).show();
            }
        });

        btnBackup.setOnClickListener(v -> backupLauncher.launch("HanZai_Backup_" + System.currentTimeMillis() + ".db"));
        btnRestore.setOnClickListener(v -> restoreLauncher.launch(new String[]{"*/*"}));
        btnUserManual.setOnClickListener(v -> openUserManual());
        btnClearCache.setOnClickListener(v -> showClearCacheConfirmation());
    }

    private void safeRecreate() {
        new android.os.Handler().postDelayed(() -> {
            finish();
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            startActivity(getIntent());
        }, 150);
    }

    private void showClearCacheConfirmation() {
        if (clearCacheDialog != null && clearCacheDialog.isShowing()) clearCacheDialog.dismiss();

        clearCacheDialog = new AlertDialog.Builder(this)
                .setTitle("Clear Memory & Image Cache")
                .setMessage("This will aggressively clean temporary image files, receipts, and app cache to free up memory and prevent app lag. Are you sure?")
                .setPositiveButton("Clean Now", (dialog, which) -> clearAppCacheAggressively())
                .setNegativeButton("Cancel", null)
                .create();
        clearCacheDialog.show();
    }

    private void clearAppCacheAggressively() {
        try {
            File cacheDir = getCacheDir();
            if (cacheDir != null && cacheDir.isDirectory()) deleteDir(cacheDir);

            File extCacheDir = getExternalCacheDir();
            if (extCacheDir != null && extCacheDir.isDirectory()) deleteDir(extCacheDir);

            Toast.makeText(this, "Images and memory cache cleared successfully!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to completely clear cache", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) return false;
                }
            }
        }
        return dir != null && dir.delete();
    }

    private void setupLaunchers() {
        backupLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/octet-stream"), uri -> {
            if (uri != null && BackupManager.exportDatabase(this, uri)) {
                Toast.makeText(this, "Backup successfully saved!", Toast.LENGTH_LONG).show();
            } else if (uri != null) {
                Toast.makeText(this, "Failed to create backup.", Toast.LENGTH_LONG).show();
            }
        });

        restoreLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                restoreConfirmDialog = new AlertDialog.Builder(SettingsActivity.this)
                        .setTitle("⚠️ Confirm Restore & Migration")
                        .setMessage("This will completely overwrite your current data. If you are migrating this backup to a NEW account, the data will be synced to the new account's cloud. Are you sure?")
                        .setPositiveButton("Yes, Restore", (dialog, which) -> {
                            if (BackupManager.importDatabase(SettingsActivity.this, uri)) {

                                // NEW: Fixes the Account Migration by forcing a fresh sync!
                                BackupManager.prepareDatabaseForNewAccount(SettingsActivity.this);

                                Toast.makeText(SettingsActivity.this, "Restore Successful! Restarting app...", Toast.LENGTH_LONG).show();
                                AuthManager.getInstance().signOutAndCleanup(() -> {
                                    Intent intent = new Intent(SettingsActivity.this, SignInActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                });
                            } else {
                                Toast.makeText(SettingsActivity.this, "Restore Failed.", Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNegativeButton("Cancel", null).create();
                restoreConfirmDialog.show();
            }
        });
    }

    private void openUserManual() {
        try {
            File file = new File(getCacheDir(), "USER-MANUAL_UPDATED.pdf");

            if (!file.exists()) {
                InputStream is = getAssets().open("USER-MANUAL_UPDATED.pdf");
                OutputStream os = new FileOutputStream(file);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) os.write(buffer, 0, length);
                os.flush();
                os.close();
                is.close();
            }

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(Intent.createChooser(intent, "Open User Manual"));

        } catch (Exception e) {
            Toast.makeText(this, "Unable to open manual: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}