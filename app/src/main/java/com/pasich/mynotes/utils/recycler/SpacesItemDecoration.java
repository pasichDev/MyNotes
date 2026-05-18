package com.pasich.mynotes.utils.recycler;

import android.graphics.Rect;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import dagger.hilt.android.scopes.ActivityScoped;
import javax.inject.Inject;

@ActivityScoped
public class SpacesItemDecoration extends RecyclerView.ItemDecoration {
    private final int vertical;
    private final int horizontal;

    @Inject
    public SpacesItemDecoration(int vertical, int horizontal) {
        this.vertical = vertical;
        this.horizontal = horizontal;
    }

    @Override
    public void getItemOffsets(
            Rect outRect,
            @NonNull View view,
            @NonNull RecyclerView parent,
            @NonNull RecyclerView.State state) {
        outRect.bottom = vertical;
        outRect.left = horizontal;
        outRect.top = vertical;
        outRect.right = horizontal;
    }
}
