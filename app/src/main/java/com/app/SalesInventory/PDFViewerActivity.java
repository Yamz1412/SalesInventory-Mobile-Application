package com.app.SalesInventory;

import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.pdf.PdfRenderer;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Button;
import androidx.core.view.GestureDetectorCompat;
import androidx.appcompat.app.AppCompatActivity;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

public class PDFViewerActivity extends AppCompatActivity {
    private PdfRenderer renderer;
    private PdfRenderer.Page currentPage;
    private ParcelFileDescriptor parcelFileDescriptor;
    private ImageView imageView;
    private Button btnClose;
    private TextView tvPageIndicator;
    private ScrollView scrollView;
    private int pageIndex = 0;
    private int pageCount = 0;
    private String assetName = "manual.pdf";
    private GestureDetectorCompat gestureDetector;
    private static final int SWIPE_MIN_DISTANCE = 120;
    private static final int SWIPE_THRESHOLD_VELOCITY = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pdf_viewer);
        imageView = findViewById(R.id.pdf_image);
        btnClose = findViewById(R.id.btn_close);
        tvPageIndicator = findViewById(R.id.tv_page_indicator);
        scrollView = findViewById(R.id.pdf_scroll);
        String a = getIntent().getStringExtra("assetName");
        if (a != null && !a.isEmpty()) assetName = a;
        try {
            File file = new File(getCacheDir(), assetName);
            if (!file.exists()) {
                InputStream is = getAssets().open(assetName);
                FileOutputStream fos = new FileOutputStream(file);
                byte[] buffer = new byte[1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, read);
                }
                fos.flush();
                fos.close();
                is.close();
            }
            parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
            renderer = new PdfRenderer(parcelFileDescriptor);
            pageCount = renderer.getPageCount();
            openPage(0);
        } catch (Exception e) {
            finish();
            return;
        }
        gestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                float dx = e2.getX() - e1.getX();
                float dy = e2.getY() - e1.getY();
                if (Math.abs(dx) > Math.abs(dy) && Math.abs(dx) > SWIPE_MIN_DISTANCE && Math.abs(velocityX) > SWIPE_THRESHOLD_VELOCITY) {
                    if (dx < 0) {
                        goNext();
                    } else {
                        goPrevious();
                    }
                    return true;
                }
                return false;
            }
        });
        View.OnTouchListener touchListener = (v, event) -> {
            gestureDetector.onTouchEvent(event);
            return false;
        };
        imageView.setOnTouchListener(touchListener);
        scrollView.setOnTouchListener(touchListener);
        btnClose.setOnClickListener(v -> finish());
    }

    private void goNext() {
        if (pageIndex < pageCount - 1) openPage(pageIndex + 1);
    }

    private void goPrevious() {
        if (pageIndex > 0) openPage(pageIndex - 1);
    }

    private void openPage(int index) {
        try {
            if (renderer == null) return;
            if (currentPage != null) currentPage.close();
            currentPage = renderer.openPage(index);
            pageIndex = index;
            int pageWidth = currentPage.getWidth();
            int pageHeight = currentPage.getHeight();
            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            int maxWidth = size.x;
            int defaultMarginDp = 16;
            float density = getResources().getDisplayMetrics().density;
            int marginPx = (int) (defaultMarginDp * density + 0.5f);
            int availableWidth = Math.max(1, maxWidth - marginPx);
            float scale = availableWidth / (float) pageWidth;
            int bitmapWidth = Math.max(1, (int) (pageWidth * scale));
            int bitmapHeight = Math.max(1, (int) (pageHeight * scale));
            Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
            currentPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
            imageView.setImageBitmap(bitmap);
            tvPageIndicator.setText((pageIndex + 1) + " / " + pageCount);
        } catch (Exception e) {
            tvPageIndicator.setText((pageIndex + 1) + " / " + pageCount);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            if (currentPage != null) currentPage.close();
            if (renderer != null) renderer.close();
            if (parcelFileDescriptor != null) parcelFileDescriptor.close();
        } catch (Exception ignored) {}
        super.onDestroy();
    }
}