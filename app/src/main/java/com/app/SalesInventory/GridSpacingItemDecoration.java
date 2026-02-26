package com.app.SalesInventory;

import android.graphics.Rect;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;

public class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {
    private final int spacing;

    public GridSpacingItemDecoration(int spacing) {
        this.spacing = spacing;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        int position = parent.getChildAdapterPosition(view);
        int spanCount = 1;
        if (parent.getLayoutManager() instanceof androidx.recyclerview.widget.GridLayoutManager) {
            spanCount = ((androidx.recyclerview.widget.GridLayoutManager) parent.getLayoutManager()).getSpanCount();
        }
        int column = position % spanCount;
        outRect.left = spacing - column * spacing / spanCount;
        outRect.right = (column + 1) * spacing / spanCount;
        if (position < spanCount) {
            outRect.top = spacing;
        }
        outRect.bottom = spacing;
    }
}