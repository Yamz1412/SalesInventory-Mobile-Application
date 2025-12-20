package com.app.SalesInventory;

import android.content.Intent;
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
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends BaseActivity {
    private static final String TAG = "SettingsActivity";
    private Spinner themeSpinner;
    private Button resetThemeBtn, applyBtn;
    private Button btnBackup, btnRestore;
    private Button btnUserManual;
    private ThemeManager themeManager;
    private ThemeManager.Theme[] allThemes;
    private ThemeManager.Theme selectedTheme;
    private ActivityResultLauncher<String> backupLauncher;
    private ActivityResultLauncher<String> restoreLauncher;
    private View previewPrimary, previewSecondary, previewAccent;
    private int currentPrimary;
    private int currentSecondary;
    private int currentAccent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        themeManager = ThemeManager.getInstance(this);
        initializeUI();
        loadCurrentTheme();
        setupThemeSpinner();
        initBackupLaunchers();
        setupListeners();
    }

    private void initializeUI() {
        themeSpinner = findViewById(R.id.themeSpinner);
        resetThemeBtn = findViewById(R.id.resetThemeBtn);
        applyBtn = findViewById(R.id.applyBtn);
        btnBackup = findViewById(R.id.btnBackup);
        btnRestore = findViewById(R.id.btnRestore);
        btnUserManual = findViewById(R.id.btnUserManual);
        previewPrimary = findViewById(R.id.previewPrimary);
        previewSecondary = findViewById(R.id.previewSecondary);
        previewAccent = findViewById(R.id.previewAccent);
    }

    private void loadCurrentTheme() {
        currentPrimary = themeManager.getPrimaryColor();
        currentSecondary = themeManager.getSecondaryColor();
        currentAccent = themeManager.getAccentColor();
        updatePreviewViews();
    }

    private void updatePreviewViews() {
        try {
            if (previewPrimary != null) previewPrimary.setBackgroundColor(currentPrimary);
            if (previewSecondary != null) previewSecondary.setBackgroundColor(currentSecondary);
            if (previewAccent != null) previewAccent.setBackgroundColor(currentAccent);
        } catch (Exception ignored) {}
    }

    private void initBackupLaunchers() {
        backupLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/octet-stream"),
                uri -> {
                    if (uri != null) {
                        boolean ok = BackupManager.exportDatabase(this, uri);
                        if (ok) {
                            Toast.makeText(this, "Backup completed", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "Backup failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        restoreLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        boolean ok = BackupManager.importDatabase(this, uri);
                        if (ok) {
                            Toast.makeText(this, "Restore completed. Restart app to apply.", Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Restore failed", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void setupThemeSpinner() {
        allThemes = themeManager.getAvailableThemes();
        String[] themeNames = new String[allThemes.length];
        for (int i = 0; i < allThemes.length; i++) {
            String raw = allThemes[i].name == null ? "" : allThemes[i].name;
            themeNames[i] = raw.length() == 0 ? "Theme " + (i + 1) : raw.substring(0, 1).toUpperCase() + raw.substring(1);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, R.layout.item_theme_spinner, themeNames) {
            @Override
            public View getView(int position, View convertView, android.view.ViewGroup parent) {
                View v = super.getView(position, convertView, parent);
                TextView tv = v.findViewById(R.id.tvThemeName);
                int bg = currentPrimary;
                int textColor = getReadableTextColor(bg);
                v.setBackgroundColor(bg);
                tv.setTextColor(textColor);
                tv.setText(getItem(position));
                return v;
            }

            @Override
            public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                View v = getLayoutInflater().inflate(R.layout.item_theme_spinner_dropdown, parent, false);
                TextView tv = v.findViewById(R.id.tvThemeName);
                tv.setText(getItem(position));
                int bg = allThemes[position].secondaryColor;
                int textColor = getReadableTextColor(bg);
                v.setBackgroundColor(bg);
                tv.setTextColor(textColor);
                return v;
            }
        };

        themeSpinner.setAdapter(adapter);

        ThemeManager.Theme currentTheme = themeManager.getCurrentTheme();
        int position = 0;
        for (int i = 0; i < allThemes.length; i++) {
            if (allThemes[i].name.equalsIgnoreCase(currentTheme.name)) {
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
                currentPrimary = selectedTheme.primaryColor;
                currentSecondary = selectedTheme.secondaryColor;
                currentAccent = selectedTheme.accentColor;
                updatePreviewViews();
                Log.d(TAG, "Theme selected (preview): " + selectedTheme.name);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        resetThemeBtn.setOnClickListener(v -> {
            themeManager.setCurrentTheme(ThemeManager.Theme.LIGHT.name);
            loadCurrentTheme();
            String uid = AuthManager.getInstance().getCurrentUserId();
            if (uid != null) {
                FirebaseFirestore db = FirebaseFirestore.getInstance();
                Map<String, Object> data = new HashMap<>();
                data.put("themeName", themeManager.getCurrentTheme().name);
                data.put("primaryColor", themeManager.getPrimaryColor());
                data.put("secondaryColor", themeManager.getSecondaryColor());
                data.put("accentColor", themeManager.getAccentColor());
                db.collection("users").document(uid).set(data, SetOptions.merge());
            }
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        });

        applyBtn.setOnClickListener(v -> applyTheme());

        btnBackup.setOnClickListener(v -> backupLauncher.launch("sales_inventory_backup.db"));

        btnRestore.setOnClickListener(v -> restoreLauncher.launch("*/*"));

        btnUserManual.setOnClickListener(v -> {
            Intent i = new Intent(SettingsActivity.this, PDFViewerActivity.class);
            i.putExtra("assetName", "manual.pdf");
            startActivity(i);
        });
    }

    private int getReadableTextColor(int bg) {
        double r = android.graphics.Color.red(bg) / 255.0;
        double g = android.graphics.Color.green(bg) / 255.0;
        double b = android.graphics.Color.blue(bg) / 255.0;
        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
        return luminance > 0.5 ? android.graphics.Color.BLACK : android.graphics.Color.WHITE;
    }

    private void applyTheme() {
        if (selectedTheme == ThemeManager.Theme.DARK ||
                selectedTheme == ThemeManager.Theme.LIGHT ||
                selectedTheme == ThemeManager.Theme.OCEAN ||
                selectedTheme == ThemeManager.Theme.FOREST ||
                selectedTheme == ThemeManager.Theme.SUNSET ||
                selectedTheme == ThemeManager.Theme.PURPLE) {
            themeManager.setCurrentTheme(selectedTheme.name);
        } else {
            themeManager.setCurrentTheme(ThemeManager.Theme.LIGHT.name);
        }

        String uid = AuthManager.getInstance().getCurrentUserId();
        if (uid != null) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Map<String, Object> data = new HashMap<>();
            data.put("themeName", themeManager.getCurrentTheme().name);
            data.put("primaryColor", themeManager.getPrimaryColor());
            data.put("secondaryColor", themeManager.getSecondaryColor());
            data.put("accentColor", themeManager.getAccentColor());
            db.collection("users").document(uid).set(data, SetOptions.merge());
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}