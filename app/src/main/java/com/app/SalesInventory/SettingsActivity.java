package com.app.SalesInventory;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.Map;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import java.util.HashMap;
import java.util.Map;

public class SettingsActivity extends BaseActivity {
    private static final String TAG = "SettingsActivity";

    private Spinner themeSpinner;
    private Button customPrimaryBtn, customSecondaryBtn, customAccentBtn;
    private Button resetThemeBtn, applyBtn;
    private LinearLayout colorPreviewLayout;
    private TextView primaryColorTV, secondaryColorTV, accentColorTV;

    private ThemeManager themeManager;
    private int currentPrimary, currentSecondary, currentAccent;
    private ThemeManager.Theme[] allThemes;
    private ThemeManager.Theme selectedTheme;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        themeManager = ThemeManager.getInstance(this);
        initializeUI();
        loadCurrentTheme();
        setupListeners();
    }

    private void initializeUI() {
        themeSpinner = findViewById(R.id.themeSpinner);
        customPrimaryBtn = findViewById(R.id.customPrimaryBtn);
        customSecondaryBtn = findViewById(R.id.customSecondaryBtn);
        customAccentBtn = findViewById(R.id.customAccentBtn);
        resetThemeBtn = findViewById(R.id.resetThemeBtn);
        applyBtn = findViewById(R.id.applyBtn);
        colorPreviewLayout = findViewById(R.id.colorPreviewLayout);
        primaryColorTV = findViewById(R.id.primaryColorTV);
        secondaryColorTV = findViewById(R.id.secondaryColorTV);
        accentColorTV = findViewById(R.id.accentColorTV);
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
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, themeNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
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

    private void loadCurrentTheme() {
        currentPrimary = themeManager.getPrimaryColor();
        currentSecondary = themeManager.getSecondaryColor();
        currentAccent = themeManager.getAccentColor();
        updateColorPreview();
    }

    private void setupListeners() {
        themeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                selectedTheme = allThemes[position];
                currentPrimary = selectedTheme.primaryColor;
                currentSecondary = selectedTheme.secondaryColor;
                currentAccent = selectedTheme.accentColor;
                themeManager.setCurrentTheme(selectedTheme.name);
                updateColorPreview();
                Log.d(TAG, "Theme selected: " + selectedTheme.name);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        customPrimaryBtn.setOnClickListener(v -> openColorPicker(color -> {
            currentPrimary = color;
            updateColorPreview();
        }));

        customSecondaryBtn.setOnClickListener(v -> openColorPicker(color -> {
            currentSecondary = color;
            updateColorPreview();
        }));

        customAccentBtn.setOnClickListener(v -> openColorPicker(color -> {
            currentAccent = color;
            updateColorPreview();
        }));

        resetThemeBtn.setOnClickListener(v -> {
            ThemeManager.Theme current = themeManager.getCurrentTheme();
            currentPrimary = current.primaryColor;
            currentSecondary = current.secondaryColor;
            currentAccent = current.accentColor;
            updateColorPreview();
            Toast.makeText(this, "Theme reset", Toast.LENGTH_SHORT).show();
        });

        applyBtn.setOnClickListener(v -> applyTheme());
    }

    private void openColorPicker(ThemeColorPicker.OnColorSelectedListener listener) {
        ThemeColorPicker colorPicker = new ThemeColorPicker(this, listener);
        colorPicker.show();
    }

    private void updateColorPreview() {
        primaryColorTV.setBackgroundColor(currentPrimary);
        secondaryColorTV.setBackgroundColor(currentSecondary);
        accentColorTV.setBackgroundColor(currentAccent);
        primaryColorTV.setText(String.format("#%06X", currentPrimary & 0xFFFFFF));
        secondaryColorTV.setText(String.format("#%06X", currentSecondary & 0xFFFFFF));
        accentColorTV.setText(String.format("#%06X", currentAccent & 0xFFFFFF));
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
            themeManager.setCustomColors(currentPrimary, currentSecondary, currentAccent);
        }

        String uid = AuthManager.getInstance().getCurrentUserId();
        if (uid != null) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            Map<String, Object> data = new HashMap<>();
            data.put("themeName", themeManager.getCurrentTheme().name);
            data.put("primaryColor", currentPrimary);
            data.put("secondaryColor", currentSecondary);
            data.put("accentColor", currentAccent);
            db.collection("users").document(uid).set(data, SetOptions.merge());
        }

        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }
}