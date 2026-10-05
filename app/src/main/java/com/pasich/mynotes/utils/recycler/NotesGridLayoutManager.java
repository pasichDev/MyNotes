package com.pasich.mynotes.utils.recycler;

import android.view.View;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

/**
 * The notes list and grid, which also keeps a dragged card on screen as it moves.
 *
 * <p>After a card moves under the finger, {@link StaggeredGridLayoutManager} lays the list out
 * again from the first card it had on screen. A card dragged onto the first one therefore ended up
 * above the screen: the list seemed to scroll away from it, and the drag was dropped because its
 * view left the list. {@code LinearLayoutManager} handles this with {@code prepareForDrop}; this
 * layout manager does the same once the move is laid out, scrolling only as far as needed to bring
 * the card back where it was dropped. (Asking for a scroll to a position instead would be resolved
 * against the layout before the move and land wrong.)
 */
public class NotesGridLayoutManager extends StaggeredGridLayoutManager {

    private static final int MAX_STEPS = 8;

    private int keepPosition = RecyclerView.NO_POSITION;
    private int keepEdge;
    private boolean keepBottom;

    public NotesGridLayoutManager(int spanCount) {
        super(spanCount, VERTICAL);
    }

    /**
     * After the next layout, if the card at {@code position} is not fully on screen, scrolls so its
     * top (or, with {@code alignBottom}, its bottom) is at {@code edge}, as close as the list
     * allows.
     */
    public void keepInView(int position, int edge, boolean alignBottom) {
        keepPosition = position;
        keepEdge = edge;
        keepBottom = alignBottom;
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        super.onLayoutChildren(recycler, state);
        if (state.isPreLayout() || keepPosition == RecyclerView.NO_POSITION) return;
        int position = keepPosition;
        keepPosition = RecyclerView.NO_POSITION;
        if (position < state.getItemCount()) bringIntoView(position, recycler, state);
    }

    private void bringIntoView(
            int position, RecyclerView.Recycler recycler, RecyclerView.State state) {
        int top = getPaddingTop();
        int bottom = getHeight() - getPaddingBottom();
        for (int step = 0; step < MAX_STEPS; step++) {
            View child = findViewByPosition(position);
            if (child == null) {
                // Not laid out at all: it went above or below the screen. Bring it closer.
                int[] first = findFirstVisibleItemPositions(null);
                int firstShown = Integer.MAX_VALUE;
                for (int p : first)
                    if (p != RecyclerView.NO_POSITION) firstShown = Math.min(firstShown, p);
                int direction = position < firstShown ? -1 : 1;
                if (scrollVerticallyBy(direction * Math.max(1, bottom - top), recycler, state)
                        == 0) {
                    return;
                }
                continue;
            }
            int childTop = getDecoratedTop(child);
            int childBottom = getDecoratedBottom(child);
            boolean fullyShown = childTop >= top && childBottom <= bottom;
            boolean tallerThanScreen = childBottom - childTop > bottom - top;
            if (fullyShown || (tallerThanScreen && childTop < bottom && childBottom > top)) return;
            int delta = keepBottom ? childBottom - keepEdge : childTop - keepEdge;
            if (delta != 0) scrollVerticallyBy(delta, recycler, state);
            return;
        }
    }
}
