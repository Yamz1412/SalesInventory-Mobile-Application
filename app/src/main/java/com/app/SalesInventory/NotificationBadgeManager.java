package com.app.SalesInventory;

import android.app.Application;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;

public class NotificationBadgeManager {
    private final AppCompatActivity activity;
    private final TextView badgeView;
    private Observer<Integer> observer;

    public NotificationBadgeManager(AppCompatActivity activity) {
        this.activity = activity;
        this.badgeView = activity.findViewById(R.id.tv_notification_badge);
        configureBadgeViewAppearance();
    }

    private void configureBadgeViewAppearance() {
        if (badgeView == null) return;
        int padding = dpToPx(4);
        badgeView.setPadding(padding, padding, padding, padding);
        badgeView.setTextColor(Color.WHITE);
        badgeView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        badgeView.setMinWidth(dpToPx(20));
        badgeView.setHeight(dpToPx(20));
        badgeView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#DD2C2C"));
        badgeView.setBackground(bg);
        badgeView.setElevation(dpToPx(4));
        badgeView.setVisibility(View.GONE);
    }

    private int dpToPx(int dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, activity.getResources().getDisplayMetrics()));
    }

    public void start() {
        Application app = activity.getApplication();
        AlertRepository repo = AlertRepository.getInstance((android.app.Application) app);
        observer = new Observer<Integer>() {
            @Override
            public void onChanged(Integer count) {
                int c = count == null ? 0 : count;
                if (badgeView == null) return;
                activity.runOnUiThread(() -> {
                    if (c <= 0) {
                        badgeView.setVisibility(View.GONE);
                    } else {
                        badgeView.setVisibility(View.VISIBLE);
                        if (c > 99) badgeView.setText("99+");
                        else badgeView.setText(String.valueOf(c));
                        badgeView.bringToFront();
                    }
                });
            }
        };
        repo.getUnreadAlertCount().observe(activity, observer);
    }

    public void stop() {
        if (observer != null) {
            AlertRepository repo = AlertRepository.getInstance((android.app.Application) activity.getApplication());
            repo.getUnreadAlertCount().removeObserver(observer);
            observer = null;
        }
    }
}