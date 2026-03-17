package com.app.SalesInventory;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends BaseActivity {
    private static final String TAG = "SettingsActivity";

    private Spinner themeSpinner;
    private Button resetThemeBtn, applyBtn, btnClearCache;
    private Button btnBackup, btnRestore, btnUserManual, btnInjectMockData;

    private ThemeManager themeManager;
    private ThemeManager.Theme[] allThemes;
    private ThemeManager.Theme selectedTheme;

    private ActivityResultLauncher<String> backupLauncher;
    private ActivityResultLauncher<String[]> restoreLauncher;
    private Button btnResetMockData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        themeManager = ThemeManager.getInstance(this);

        initializeUI();
        setupLaunchers();
        setupListeners();
    }

    // =========================================================================
    // BACKUP & RESTORE ENGINE (Storage Access Framework)
    // =========================================================================
    private void setupLaunchers() {
        // 1. Setup Backup Launcher (Prompts user where to save the .db file)
        backupLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/octet-stream"),
                uri -> {
                    if (uri != null) {
                        boolean success = BackupManager.exportDatabase(this, uri);
                        if (success) {
                            Toast.makeText(this, "Backup successfully saved!", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Failed to create backup.", Toast.LENGTH_LONG).show();
                        }
                    }
                });

        // 2. Setup Restore Launcher (Prompts user to pick a .db file to load)
        restoreLauncher = registerForActivityResult(
                new ActivityResultContracts.OpenDocument(),
                uri -> {
                    if (uri != null) {
                        // Crucial: Warn the user before wiping their current data
                        new AlertDialog.Builder(this)
                                .setTitle("⚠️ Confirm Restore")
                                .setMessage("This will completely overwrite your current inventory, sales, and settings with the data from the backup file. Are you absolutely sure?")
                                .setPositiveButton("Yes, Restore", (dialog, which) -> {
                                    boolean success = BackupManager.importDatabase(this, uri);
                                    if (success) {
                                        Toast.makeText(this, "Restore Successful! Restarting app...", Toast.LENGTH_LONG).show();

                                        // To prevent Room Database crashes from cached data,
                                        // we sign the user out and restart the app cleanly.
                                        AuthManager.getInstance().signOutAndCleanup(() -> {
                                            Intent intent = new Intent(SettingsActivity.this, SignInActivity.class);
                                            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                            startActivity(intent);
                                            finish();
                                        });
                                    } else {
                                        Toast.makeText(this, "Restore Failed. The file might be corrupted or invalid.", Toast.LENGTH_LONG).show();
                                    }
                                })
                                .setNegativeButton("Cancel", null)
                                .show();
                    }
                });
    }

    private void initializeUI() {
        btnInjectMockData = findViewById(R.id.btnInjectMockData);
        themeSpinner = findViewById(R.id.themeSpinner);
        resetThemeBtn = findViewById(R.id.resetThemeBtn);
        applyBtn = findViewById(R.id.applyBtn);
        btnBackup = findViewById(R.id.btnBackup);
        btnRestore = findViewById(R.id.btnRestore);
        btnUserManual = findViewById(R.id.btnUserManual);
        btnClearCache = findViewById(R.id.btnClearCache);
        btnResetMockData = findViewById(R.id.btnResetMockData);

        setupThemeSpinner();
    }

    private void setupThemeSpinner() {
        allThemes = themeManager.getAvailableThemes();
        String[] themeNames = new String[allThemes.length];
        for (int i = 0; i < allThemes.length; i++) {
            String raw = allThemes[i].name == null ? "" : allThemes[i].name;
            if (raw.length() == 0) {
                themeNames[i] = "Theme " + (i + 1);
            } else {
                themeNames[i] = raw.substring(0, 1).toUpperCase() + raw.substring(1);
            }
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.item_theme_spinner, themeNames) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView tv = v.findViewById(R.id.tvThemeName);
                if (tv != null) {
                    int bg = themeManager.getPrimaryColor();
                    int textColor = getReadableTextColor(bg);
                    v.setBackgroundColor(bg);
                    tv.setTextColor(textColor);
                }
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                View v = getLayoutInflater().inflate(R.layout.item_theme_spinner_dropdown, parent, false);
                TextView tv = v.findViewById(R.id.tvThemeName);
                if (tv != null) {
                    tv.setText(getItem(position));
                    int bg = themeManager.getSecondaryColor();
                    int textColor = getReadableTextColor(bg);
                    v.setBackgroundColor(bg);
                    tv.setTextColor(textColor);
                }
                return v;
            }
        };

        themeSpinner.setAdapter(adapter);

        ThemeManager.Theme currentTheme = themeManager.getCurrentTheme();
        int position = 0;
        for (int i = 0; i < allThemes.length; i++) {
            if (allThemes[i].name.equals(currentTheme.name)) {
                position = i;
                break;
            }
        }
        selectedTheme = allThemes[position];
        themeSpinner.setSelection(position);
    }

    private void setupListeners() {
        themeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedTheme = allThemes[position];
                updateButtonTints();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        btnInjectMockData.setOnClickListener(v -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Inject Mock Data")
                    .setMessage("This will add 60 inventory items and 54 sales products for a Philippine coffee shop. Continue?")
                    .setPositiveButton("Yes, Inject", (dialog, which) -> {
                        MockDataInjector.injectHanZaiDefenseData(this, () -> {
                            // Data is already in Room — navigate to MainActivity so all screens reload
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        btnResetMockData.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("⚠️ WIPE ALL DATA")
                    .setMessage("Are you absolutely sure? This will delete ALL Sales, Inventory, Purchase Orders, and Adjustments for your account. This cannot be undone.")
                    .setPositiveButton("YES, WIPE DATA", (dialog, which) -> {
                        Toast.makeText(this, "Wiping database...", Toast.LENGTH_LONG).show();
                        MockDataInjector.resetHanZaiDefenseData(this, () -> {
                            Toast.makeText(this, "System Reset Successful! Restarting...", Toast.LENGTH_LONG).show();
                            Intent intent = new Intent(this, MainActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                            startActivity(intent);
                            finish();
                        });
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        resetThemeBtn.setOnClickListener(v -> {
            ThemeManager.Theme currentTheme = themeManager.getCurrentTheme();
            for (int i = 0; i < allThemes.length; i++) {
                if (allThemes[i].name.equals(currentTheme.name)) {
                    themeSpinner.setSelection(i);
                    break;
                }
            }
            Toast.makeText(this, "Theme selection reset", Toast.LENGTH_SHORT).show();
        });

        applyBtn.setOnClickListener(v -> applyTheme());

        btnBackup.setOnClickListener(v -> {
            String filename = "HanZai_Backup_" + System.currentTimeMillis() + ".db";
            backupLauncher.launch(filename);
        });

        btnRestore.setOnClickListener(v -> {
            restoreLauncher.launch(new String[]{"*/*"});
        });

        btnUserManual.setOnClickListener(v -> openUserManual());
        btnClearCache.setOnClickListener(v -> showClearCacheConfirmation());
    }

    private void updateButtonTints() {
        if (selectedTheme == null) return;

        int primary = selectedTheme.primaryColor;
        int secondary = ContextCompat.getColor(this, R.color.colorPrimary);

        tintButton(applyBtn, primary);
        tintButton(btnBackup, primary);
        tintButton(btnRestore, primary);
        tintButton(btnUserManual, primary);
        tintButton(resetThemeBtn, secondary);
        // Note: btnClearCache is intentionally ignored here so it keeps its red background from XML
    }

    private void applyTheme() {
        if (selectedTheme != null) {
            themeManager.setCurrentTheme(selectedTheme.name);

            String uid = AuthManager.getInstance().getCurrentUserId();
            if (uid != null) {
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                Map<String, Object> data = new HashMap<>();
                data.put("themeName", selectedTheme.name);
                db.collection("users").document(uid).set(data, SetOptions.merge());
            }

            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
    }

    private void showClearCacheConfirmation() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Local Cache")
                .setMessage("Are you sure you want to clear the app's local cache? This frees up storage space (like temporary PDFs and images) but may briefly slow down the app the next time it loads files.")
                .setPositiveButton("Clear", (dialog, which) -> clearAppCache())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearAppCache() {
        try {
            File cacheDir = getCacheDir();
            if (cacheDir != null && cacheDir.isDirectory()) {
                deleteDir(cacheDir);
            }
            Toast.makeText(this, "Cache cleared successfully", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to clear cache", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error clearing cache", e);
        }
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) {
                        return false;
                    }
                }
            }
        }
        return dir != null && dir.delete();
    }

    private void openUserManual() {
        try {
            File file = new File(getCacheDir(), "USER-MANUAL.pdf");
            if (!file.exists()) {
                InputStream is = getAssets().open("USER-MANUAL.pdf");
                OutputStream os = new FileOutputStream(file);
                byte[] buffer = new byte[1024];
                int length;
                while ((length = is.read(buffer)) > 0) {
                    os.write(buffer, 0, length);
                }
                os.flush();
                os.close();
                is.close();
            }

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/pdf");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
            startActivity(Intent.createChooser(intent, "Open User Manual"));
        } catch (Exception e) {
            Toast.makeText(this, "Unable to open manual", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error opening PDF", e);
        }
    }

    private int getReadableTextColor(int bg) {
        double r = android.graphics.Color.red(bg) / 255.0;
        double g = android.graphics.Color.green(bg) / 255.0;
        double b = android.graphics.Color.blue(bg) / 255.0;
        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
        return luminance > 0.5 ? android.graphics.Color.BLACK : android.graphics.Color.WHITE;
    }

    private void tintButton(Button b, int bgColor) {
        if (b == null) return;
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
        b.setTextColor(getReadableTextColor(bgColor));
    }
}