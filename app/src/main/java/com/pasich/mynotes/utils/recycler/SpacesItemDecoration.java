package com.pasich.mynotes.utils.recycler;

import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;

@ActivityScoped
public class SpacesItemDecoration extends RecyclerView.ItemDecoration {
  private final int space;

  @Inject
  public SpacesItemDecoration(int space) {
    this.space = space;
  }

  @Override
  public void getItemOffsets(
          Rect outRect,
          @NonNull View view,
          @NonNull RecyclerView parent,
          @NonNull RecyclerView.State state) {
    outRect.bottom = space;
    outRect.left = space;
    outRect.top = space;
    outRect.right = space;
  }
}
