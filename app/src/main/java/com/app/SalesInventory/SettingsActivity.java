package com.app.SalesInventory;

import android.graphics.Color;
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

import androidx.appcompat.app.AppCompatActivity;

import com.app.SalesInventory.R;
import com.app.SalesInventory.ThemeColorPicker;
import com.app.SalesInventory.ThemeManager;

public class SettingsActivity extends AppCompatActivity {
    private static final String TAG = "SettingsActivity";

    // UI Components
    private Spinner themeSpinner;
    private Button customPrimaryBtn, customSecondaryBtn, customAccentBtn;
    private Button resetThemeBtn, applyBtn;
    private LinearLayout colorPreviewLayout;
    private TextView primaryColorTV, secondaryColorTV, accentColorTV;

    // Theme Manager
    private ThemeManager themeManager;
    private int currentPrimary, currentSecondary, currentAccent;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Initialize theme manager
        themeManager = ThemeManager.getInstance(this);

        // Initialize UI
        initializeUI();

        // Load current theme
        loadCurrentTheme();

        // Setup listeners
        setupListeners();
    }

    /**
     * Initialize UI components
     */
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

        // Setup theme spinner
        setupThemeSpinner();
    }

    /**
     * Setup theme spinner with options
     */
    private void setupThemeSpinner() {
        ThemeManager.Theme[] themes = themeManager.getAvailableThemes();
        String[] themeNames = new String[themes.length];

        for (int i = 0; i < themes.length; i++) {
            themeNames[i] = themes[i].name.substring(0, 1).toUpperCase() +
                    themes[i].name.substring(1);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, themeNames);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        themeSpinner.setAdapter(adapter);

        // Set current theme
        ThemeManager.Theme currentTheme = themeManager.getCurrentTheme();
        int position = 0;
        for (int i = 0; i < themes.length; i++) {
            if (themes[i].name.equals(currentTheme.name)) {
                position = i;
                break;
            }
        }
        themeSpinner.setSelection(position);
    }

    /**
     * Load current theme colors
     */
    private void loadCurrentTheme() {
        currentPrimary = themeManager.getPrimaryColor();
        currentSecondary = themeManager.getSecondaryColor();
        currentAccent = themeManager.getAccentColor();

        updateColorPreview();
    }

    /**
     * Setup click listeners
     */
    private void setupListeners() {
        themeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                ThemeManager.Theme[] themes = themeManager.getAvailableThemes();
                ThemeManager.Theme selectedTheme = themes[position];

                currentPrimary = selectedTheme.primaryColor;
                currentSecondary = selectedTheme.secondaryColor;
                currentAccent = selectedTheme.accentColor;

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
            loadCurrentTheme();
            setupThemeSpinner();
            Toast.makeText(this, "Theme reset", Toast.LENGTH_SHORT).show();
        });

        applyBtn.setOnClickListener(v -> applyTheme());
    }

    /**
     * Open color picker
     */
    private void openColorPicker(ThemeColorPicker.OnColorSelectedListener listener) {
        ThemeColorPicker colorPicker = new ThemeColorPicker(this, listener);
        colorPicker.show();
    }

    /**
     * Update color preview
     */
    private void updateColorPreview() {
        primaryColorTV.setBackgroundColor(currentPrimary);
        secondaryColorTV.setBackgroundColor(currentSecondary);
        accentColorTV.setBackgroundColor(currentAccent);

        primaryColorTV.setText(String.format("#%06X", currentPrimary & 0xFFFFFF));
        secondaryColorTV.setText(String.format("#%06X", currentSecondary & 0xFFFFFF));
        accentColorTV.setText(String.format("#%06X", currentAccent & 0xFFFFFF));
    }

    /**
     * Apply theme changes
     */
    private void applyTheme() {
        themeManager.setCustomColors(currentPrimary, currentSecondary, currentAccent);
        Toast.makeText(this, "Theme applied!", Toast.LENGTH_SHORT).show();
        Log.d(TAG, "Theme applied");

        // Optionally restart activity to apply theme
        recreate();
    }
}