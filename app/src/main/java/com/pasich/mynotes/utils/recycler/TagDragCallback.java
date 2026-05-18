package com.pasich.mynotes.utils.recycler;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import com.pasich.mynotes.utils.adapters.TagsManagementAdapter;

public class TagDragCallback extends ItemTouchHelper.Callback {

    private final TagsManagementAdapter adapter;
    private boolean isDragging = false;

    public TagDragCallback(TagsManagementAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public int getMovementFlags(
            @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        int position = viewHolder.getAbsoluteAdapterPosition();

        // Disable drag for first element (Add button)
        if (position == 0) {
            return 0;
        }

        // Allow only vertical dragging
        int dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        int swipeFlags = 0; // Disable swipe
        return makeMovementFlags(dragFlags, swipeFlags);
    }

    @Override
    public boolean onMove(
            @NonNull RecyclerView recyclerView,
            @NonNull RecyclerView.ViewHolder viewHolder,
            @NonNull RecyclerView.ViewHolder target) {
        int fromPosition = viewHolder.getAbsoluteAdapterPosition();
        int toPosition = target.getAbsoluteAdapterPosition();

        // Cannot move to position 0 (Add button)
        if (toPosition == 0) return false;

        // Mark that we're currently dragging
        isDragging = true;

        // Call adapter method for UI movement only
        adapter.moveItemUI(fromPosition, toPosition);
        return true;
    }

    @Override
    public void clearView(
            @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        // If we were dragging, save changes to database
        if (isDragging) {
            adapter.saveDragChangesToDatabase();
            isDragging = false;
        }
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        // Not using swipe
    }

    @Override
    public boolean isLongPressDragEnabled() {
        // Allow drag to start with long press
        return true;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return false;
    }
}
