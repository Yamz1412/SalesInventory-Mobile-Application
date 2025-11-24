package com.app.SalesInventory;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class ThemeColorPicker extends Dialog {
    private static final String TAG = "ThemeColorPicker";
    private ThemeManager themeManager;
    private OnColorSelectedListener listener;

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }

    public ThemeColorPicker(Context context, OnColorSelectedListener listener) {
        super(context);
        this.themeManager = ThemeManager.getInstance(context);
        this.listener = listener;
        setupDialog();
    }

    /**
     * Setup color picker dialog
     */
    private void setupDialog() {
        setTitle("Select Color");

        LinearLayout colorGrid = new LinearLayout(getContext());
        colorGrid.setOrientation(LinearLayout.VERTICAL);
        colorGrid.setPadding(16, 16, 16, 16);

        // Common colors
        int[] colors = {
                0xFF2196F3, 0xFF1976D2, 0xFF006994, 0xFF2E7D32,
                0xFFE65100, 0xFF6A1B9A, 0xFFD32F2F, 0xFFFFA000,
                0xFF0097A7, 0xFF388E3C, 0xFFD81B60, 0xFF4527A0
        };

        int colorCount = 0;
        LinearLayout row = null;

        for (int color : colors) {
            if (colorCount % 4 == 0) {
                row = new LinearLayout(getContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setWeightSum(4);
                colorGrid.addView(row);
            }

            View colorView = new View(getContext());
            colorView.setBackgroundColor(color);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, 80);
            params.weight = 1;
            params.setMargins(4, 4, 4, 4);
            colorView.setLayoutParams(params);

            final int selectedColor = color;
            colorView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onColorSelected(selectedColor);
                }
                dismiss();
            });

            row.addView(colorView);
            colorCount++;
        }

        setContentView(colorGrid);
    }
}