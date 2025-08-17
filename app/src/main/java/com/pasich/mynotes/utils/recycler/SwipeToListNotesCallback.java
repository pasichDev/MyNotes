package com.pasich.mynotes.utils.recycler;

import android.graphics.Canvas;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;


abstract public class SwipeToListNotesCallback extends ItemTouchHelper.SimpleCallback {


    public SwipeToListNotesCallback(int dragDirs, int swipeDirs) {
        super(dragDirs, swipeDirs);
    }


    @Override
    public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
        return false;
    }

    @Override
    public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState, boolean isCurrentlyActive) {

        final float alpha = 1.0f - Math.abs(dX) / (float) viewHolder.itemView.getWidth();
        viewHolder.itemView.setAlpha(alpha);


        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
    }
}
