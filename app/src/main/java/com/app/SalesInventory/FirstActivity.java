package com.app.SalesInventory;

import android.animation.ValueAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextPaint;
import android.text.style.CharacterStyle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class FirstActivity extends BaseActivity {

    private ImageView ivSplashLogo;
    private TextView tvSplashTitle;
    private LinearLayout layoutButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first);

        ivSplashLogo = findViewById(R.id.ivSplashLogo);
        tvSplashTitle = findViewById(R.id.tvSplashTitle);
        layoutButtons = findViewById(R.id.layoutButtons);

        // Start the Splash Animation sequence
        startSplashAnimation();
    }

    private void startSplashAnimation() {
        // 1. Animate Logo sliding down and fading in
        ivSplashLogo.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(1000)
                .setInterpolator(new DecelerateInterpolator())
                .start();

        // 2. Animate Title (Grand Opening Dancing Letters!)
        animateDancingText(tvSplashTitle, "Sales & Inventory Management", 200);

        // 3. Animate Buttons sliding up after the text is mostly done dancing
        layoutButtons.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(500)
                .setStartDelay(1000)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    // --- THE DANCING TEXT ENGINE ---
    private void animateDancingText(TextView textView, String text, long startDelay) {
        textView.setAlpha(1f);
        textView.setTranslationY(0f);

        SpannableString spannable = new SpannableString(text);
        DancingSpan[] spans = new DancingSpan[text.length()];

        // Attach a separate animator span to every single letter
        for (int i = 0; i < text.length(); i++) {
            spans[i] = new DancingSpan();
            spannable.setSpan(spans[i], i, i + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        textView.setText(spannable);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setStartDelay(startDelay);
        animator.setDuration(2000); // Total duration of the wave
        animator.addUpdateListener(anim -> {
            float progress = (float) anim.getAnimatedValue();

            for (int i = 0; i < text.length(); i++) {
                // Stagger start times: first char starts at 0.0, last char starts at 0.7
                float charStart = i * (0.7f / text.length());

                // Each character's individual animation lasts 30% of the total time
                float charProgress = (progress - charStart) / 0.3f;
                charProgress = Math.max(0f, Math.min(1f, charProgress));

                // 1. Fade the letter in
                spans[i].setAlpha((int) (Math.min(1f, charProgress * 2f) * 255));

                // 2. The Bounce Math:
                // Drops from below, overshoots (bounces) above the line, then settles at 0.
                float shift = (1f - charProgress) * 50f - (float) Math.sin(charProgress * Math.PI) * 35f;
                spans[i].setTranslationY(shift);
            }
            // Update the text view to redraw the new letter positions
            textView.setText(spannable);
        });
        animator.start();
    }

    // Custom Span that allows us to move and fade individual letters inside a TextView
    private static class DancingSpan extends CharacterStyle {
        private float translationY = 50f;
        private int alpha = 0;

        public void setTranslationY(float translationY) { this.translationY = translationY; }
        public void setAlpha(int alpha) { this.alpha = alpha; }

        @Override
        public void updateDrawState(TextPaint tp) {
            tp.baselineShift = (int) translationY;
            tp.setAlpha(alpha);
        }
    }

    public void OpenSingIn(View view) {
        Intent intent = new Intent(this, SignInActivity.class);
        startActivity(intent);
    }

    public void OpenSingUp(View view) {
        Intent intent = new Intent(this, SignUpActivity.class);
        startActivity(intent);
    }
}