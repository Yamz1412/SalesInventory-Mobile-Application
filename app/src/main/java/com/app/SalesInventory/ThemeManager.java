package com.app.SalesInventory;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.appcompat.app.AppCompatActivity;

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
        this.context = context.getApplicationContext();
        this.sharedPreferences = this.context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE);
    }

    public static synchronized ThemeManager getInstance(Context context) {
        if (instance == null) {
            instance = new ThemeManager(context);
        }
        return instance;
    }

    public Theme getCurrentTheme() {
        String themeName = sharedPreferences.getString(SELECTED_THEME, Theme.LIGHT.name);
        for (Theme theme : Theme.values()) {
            if (theme.name.equals(themeName)) {
                return theme;
            }
        }
        return Theme.LIGHT;
    }

    public void setCurrentTheme(String themeName) {
        Theme chosen = Theme.LIGHT;
        for (Theme t : Theme.values()) {
            if (t.name.equals(themeName)) {
                chosen = t;
                break;
            }
        }
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SELECTED_THEME, chosen.name);
        editor.putInt(PRIMARY_COLOR, chosen.primaryColor);
        editor.putInt(SECONDARY_COLOR, chosen.secondaryColor);
        editor.putInt(ACCENT_COLOR, chosen.accentColor);
        editor.apply();
    }

    public void applyTheme(AppCompatActivity activity) {
        Theme current = getCurrentTheme();
        if ("dark".equalsIgnoreCase(current.name)) {
            activity.setTheme(R.style.AppTheme_Dark);
        } else if ("ocean".equalsIgnoreCase(current.name)) {
            activity.setTheme(R.style.AppTheme_Ocean);
        } else if ("forest".equalsIgnoreCase(current.name)) {
            activity.setTheme(R.style.AppTheme_Forest);
        } else if ("sunset".equalsIgnoreCase(current.name)) {
            activity.setTheme(R.style.AppTheme_Sunset);
        } else if ("purple".equalsIgnoreCase(current.name)) {
            activity.setTheme(R.style.AppTheme_Purple);
        } else {
            activity.setTheme(R.style.AppTheme_Light);
        }
    }

    public int getPrimaryColor() {
        return sharedPreferences.getInt(PRIMARY_COLOR, Theme.LIGHT.primaryColor);
    }

    public int getSecondaryColor() {
        return sharedPreferences.getInt(SECONDARY_COLOR, Theme.LIGHT.secondaryColor);
    }

    public int getAccentColor() {
        return sharedPreferences.getInt(ACCENT_COLOR, Theme.LIGHT.accentColor);
    }

    public void setCustomColors(int primaryColor, int secondaryColor, int accentColor) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SELECTED_THEME, Theme.CUSTOM.name);
        editor.putInt(PRIMARY_COLOR, primaryColor);
        editor.putInt(SECONDARY_COLOR, secondaryColor);
        editor.putInt(ACCENT_COLOR, accentColor);
        editor.apply();
    }

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

    public void applySystemColorsToWindow(Activity activity) {
        if (activity == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;
        int primary = getPrimaryColor();
        int secondary = getSecondaryColor();
        Window window = activity.getWindow();
        window.setStatusBarColor(primary);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.setNavigationBarColor(secondary);
        } else {
            window.setNavigationBarColor(primary);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            boolean lightStatusIcons = isColorLight(primary);
            if (lightStatusIcons) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    private boolean isColorLight(int color) {
        double r = android.graphics.Color.red(color) / 255.0;
        double g = android.graphics.Color.green(color) / 255.0;
        double b = android.graphics.Color.blue(color) / 255.0;
        double luminance = 0.299 * r + 0.587 * g + 0.114 * b;
        return luminance > 0.5;
    }
}