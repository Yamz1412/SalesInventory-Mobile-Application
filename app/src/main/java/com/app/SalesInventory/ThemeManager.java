package com.app.SalesInventory;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.core.content.ContextCompat;

public class ThemeManager {
    private static final String TAG = "ThemeManager";
    private static final String THEME_PREFS = "theme_prefs";
    private static final String SELECTED_THEME = "selected_theme";
    private static final String PRIMARY_COLOR = "primary_color";
    private static final String SECONDARY_COLOR = "secondary_color";
    private static final String ACCENT_COLOR = "accent_color";

    private static ThemeManager instance;
    private SharedPreferences sharedPreferences;
    private Context context;

    public enum Theme {
        LIGHT("light", 0xFF2196F3, 0xFF1976D2, 0xFFFF5722),
        DARK("dark", 0xFF1E1E1E, 0xFF121212, 0xFFBB86FC),
        OCEAN("ocean", 0xFF006994, 0xFF004D73, 0xFF00BCD4),
        FOREST("forest", 0xFF2E7D32, 0xFF1B5E20, 0xFF81C784),
        SUNSET("sunset", 0xFFE65100, 0xFFBF360C, 0xFFFF6F00),
        PURPLE("purple", 0xFF6A1B9A, 0xFF4A148C, 0xFF9C27B0),
        CUSTOM("custom", 0xFF2196F3, 0xFF1976D2, 0xFFFF5722);

        public final String name;
        public final int primaryColor;
        public final int secondaryColor;
        public final int accentColor;

        Theme(String name, int primaryColor, int secondaryColor, int accentColor) {
            this.name = name;
            this.primaryColor = primaryColor;
            this.secondaryColor = secondaryColor;
            this.accentColor = accentColor;
        }
    }

    private ThemeManager(Context context) {
        this.context = context;
        this.sharedPreferences = context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context);
        }
        return instance;
    }

    /**
     * Get current theme
     */
    public Theme getCurrentTheme() {
        String themeName = sharedPreferences.getString(SELECTED_THEME, Theme.LIGHT.name);
        for (Theme theme : Theme.values()) {
            if (theme.name.equals(themeName)) {
                return theme;
            }
        }
        return Theme.LIGHT;
    }

    /**
     * Set theme
     */
    public void setTheme(Theme theme) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SELECTED_THEME, theme.name);
        editor.putInt(PRIMARY_COLOR, theme.primaryColor);
        editor.putInt(SECONDARY_COLOR, theme.secondaryColor);
        editor.putInt(ACCENT_COLOR, theme.accentColor);
        editor.apply();
        android.util.Log.d(TAG, "Theme changed to: " + theme.name);
    }

    /**
     * Get primary color
     */
    public int getPrimaryColor() {
        return sharedPreferences.getInt(PRIMARY_COLOR, Theme.LIGHT.primaryColor);
    }

    /**
     * Get secondary color
     */
    public int getSecondaryColor() {
        return sharedPreferences.getInt(SECONDARY_COLOR, Theme.LIGHT.secondaryColor);
    }

    /**
     * Get accent color
     */
    public int getAccentColor() {
        return sharedPreferences.getInt(ACCENT_COLOR, Theme.LIGHT.accentColor);
    }

    /**
     * Set custom colors
     */
    public void setCustomColors(int primaryColor, int secondaryColor, int accentColor) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SELECTED_THEME, Theme.CUSTOM.name);
        editor.putInt(PRIMARY_COLOR, primaryColor);
        editor.putInt(SECONDARY_COLOR, secondaryColor);
        editor.putInt(ACCENT_COLOR, accentColor);
        editor.apply();
        android.util.Log.d(TAG, "Custom colors set");
    }

    /**
     * Get all available themes
     */
    public Theme[] getAvailableThemes() {
        return new Theme[]{
                Theme.LIGHT,
                Theme.DARK,
                Theme.OCEAN,
                Theme.FOREST,
                Theme.SUNSET,
                Theme.PURPLE
        };
    }
}