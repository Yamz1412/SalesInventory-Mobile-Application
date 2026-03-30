package com.app.SalesInventory;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.view.View;
import android.view.Window;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

public class ThemeManager {
    private static final String THEME_PREFS = "theme_prefs";
    private static final String SELECTED_THEME = "selected_theme";

    private static ThemeManager instance;
    private SharedPreferences sharedPreferences;
    private Context context;

    public interface ThemeLoadCallback {
        void onLoaded();
    }

    public enum Theme {
        DEFAULT("default", 0xFF4E342E, 0xFF3E2723, 0xFF8D6E63),
        LIGHT("light", 0xFF4E342E, 0xFF3E2723, 0xFF8D6E63),
        DARK("dark", 0xFF121212, 0xFF000000, 0xFF8D6E63);

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
        String themeName = sharedPreferences.getString(SELECTED_THEME, Theme.DEFAULT.name);
        for (Theme theme : Theme.values()) {
            if (theme.name.equals(themeName)) {
                return theme;
            }
        }
        return Theme.DEFAULT;
    }

    private void saveLocalTheme(Theme chosen) {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(SELECTED_THEME, chosen.name);
        editor.apply();
    }

    public void setCurrentTheme(String themeName) {
        Theme chosen = Theme.DEFAULT;
        for (Theme t : Theme.values()) {
            if (t.name.equals(themeName)) {
                chosen = t;
                break;
            }
        }
        saveLocalTheme(chosen);

        String uid = AuthManager.getInstance().getCurrentUserId();
        if (uid != null) {
            FirebaseFirestore.getInstance().collection("users").document(uid)
                    .update("themeName", chosen.name);
        }
    }

    public void applyTheme(AppCompatActivity activity) {
        Theme current = getCurrentTheme();
        if ("dark".equalsIgnoreCase(current.name)) {
            activity.setTheme(R.style.AppTheme_Dark);
        } else if ("light".equalsIgnoreCase(current.name)) {
            activity.setTheme(R.style.AppTheme_Light);
        } else {
            activity.setTheme(R.style.Theme_SalesInventory); // Default
        }
    }

    public void applySystemColorsToWindow(Activity activity) {
        if (activity == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) return;

        Theme current = getCurrentTheme();
        Window window = activity.getWindow();

        window.setStatusBarColor(current.primaryColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.setNavigationBarColor(current.secondaryColor);
        } else {
            window.setNavigationBarColor(current.primaryColor);
        }
    }

    public void loadUserThemeFromRemote(ThemeLoadCallback callback) {
        String uid = AuthManager.getInstance().getCurrentUserId();
        if (uid == null) {
            if (callback != null) callback.onLoaded();
            return;
        }
        FirebaseFirestore.getInstance().collection("users").document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    if (doc != null && doc.exists() && doc.contains("themeName")) {
                        String themeName = doc.getString("themeName");
                        for (Theme t : Theme.values()) {
                            if (t.name.equals(themeName)) {
                                saveLocalTheme(t);
                                break;
                            }
                        }
                    }
                    if (callback != null) callback.onLoaded();
                })
                .addOnFailureListener(e -> {
                    if (callback != null) callback.onLoaded();
                });
    }
}