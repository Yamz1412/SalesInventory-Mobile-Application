package com.app.SalesInventory;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        ThemeManager.getInstance(this).applyTheme(this);
        super.onCreate(savedInstanceState);
        ThemeManager.getInstance(this).applySystemColorsToWindow(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        ThemeManager.getInstance(this).applySystemColorsToWindow(this);
    }
}