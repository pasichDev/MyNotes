package com.pasich.mynotes.utils.recycler;

import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/**
 * Swipes on the notes list and, in the custom order, drags of the cards in the list and the grid.
 * The screen decides what is allowed and what a move or a drop means ({@link Host}); this class
 * handles how a dragged card finds its place and stays on screen.
 */
public class NoteDragCallback extends SwipeToListNotesCallback {

    /** The screen the cards belong to. */
    public interface Host {
        /** Whether cards can be dragged now (custom order, no selection, no search). */
        boolean canDrag();

        /** Whether cards can be swiped now. */
        boolean canSwipe();

        /** Whether the cards at the two positions may trade places (same section). */
        boolean canTrade(int first, int second);

        /** Whether a drag is in progress, from pick-up until its drop is behind the list. */
        boolean isDragging();

        /** Moves the dragged card in the list on screen. */
        void move(int from, int to);

        void onDragStarted(@NonNull RecyclerView.ViewHolder holder);

        /** The card was let go, moved or not. */
        void onDragEnded(@NonNull RecyclerView.ViewHolder holder);

        void onSwiped(@NonNull RecyclerView.ViewHolder holder, int direction);
    }

    private final Host host;

    public NoteDragCallback(@NonNull Host host) {
        super(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT);
        this.host = host;
    }

    @Override
    public boolean isItemViewSwipeEnabled() {
        return host.canSwipe();
    }

    @Override
    public boolean isLongPressDragEnabled() {
        // Started from the card's own long press, which also decides whether its menu opens.
        return false;
    }

    @Override
    public int getDragDirs(
            @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        if (!host.canDrag()) return 0;
        int vertical = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        return spanCount(recyclerView) > 1
                ? vertical | ItemTouchHelper.START | ItemTouchHelper.END
                : vertical;
    }

    private static int spanCount(RecyclerView recyclerView) {
        return recyclerView.getLayoutManager() instanceof NotesGridLayoutManager grid
                ? grid.getSpanCount()
                : 1;
    }

    @Override
    public boolean canDropOver(
            @NonNull RecyclerView recyclerView,
            @NonNull RecyclerView.ViewHolder current,
            @NonNull RecyclerView.ViewHolder target) {
        return host.canTrade(
                current.getBindingAdapterPosition(), target.getBindingAdapterPosition());
    }

    @Nullable
    @Override
    public RecyclerView.ViewHolder chooseDropTarget(
            @NonNull RecyclerView.ViewHolder selected,
            @NonNull List<RecyclerView.ViewHolder> dropTargets,
            int curX,
            int curY) {
        int from = selected.getBindingAdapterPosition();
        if (from == RecyclerView.NO_POSITION) return null;
        List<DragTarget.Card> cards = new ArrayList<>(dropTargets.size());
        for (RecyclerView.ViewHolder target : dropTargets) {
            int position = target.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) cards.add(card(position, target));
        }
        DragTarget.Card chosen =
                DragTarget.choose(
                        card(from, selected),
                        curX + selected.itemView.getWidth() / 2,
                        curY + selected.itemView.getHeight() / 2,
                        cards);
        if (chosen == null) return null;
        for (RecyclerView.ViewHolder target : dropTargets) {
            if (target.getBindingAdapterPosition() == chosen.position()) return target;
        }
        return null;
    }

    private static DragTarget.Card card(int position, RecyclerView.ViewHolder holder) {
        return new DragTarget.Card(
                position,
                holder.itemView.getLeft(),
                holder.itemView.getTop(),
                holder.itemView.getRight(),
                holder.itemView.getBottom());
    }

    @Override
    public boolean onMove(
            @NonNull RecyclerView recyclerView,
            @NonNull RecyclerView.ViewHolder viewHolder,
            @NonNull RecyclerView.ViewHolder target) {
        int from = viewHolder.getBindingAdapterPosition();
        int to = target.getBindingAdapterPosition();
        if (!host.isDragging()
                || from == RecyclerView.NO_POSITION
                || to == RecyclerView.NO_POSITION
                || !host.canTrade(from, to)) {
            return false;
        }
        RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
        if (lm instanceof NotesGridLayoutManager grid) {
            // The card takes the other card's place: its top there when moving up the list,
            // its bottom when moving down.
            boolean down = to > from;
            grid.keepInView(
                    to,
                    down
                            ? lm.getDecoratedBottom(target.itemView)
                            : lm.getDecoratedTop(target.itemView),
                    down);
        }
        host.move(from, to);
        return true;
    }

    @Override
    public void onMoved(
            @NonNull RecyclerView recyclerView,
            @NonNull RecyclerView.ViewHolder viewHolder,
            int fromPos,
            @NonNull RecyclerView.ViewHolder target,
            int toPos,
            int x,
            int y) {
        // The default scrolls to the new position when the other card was at an edge; with a
        // StaggeredGridLayoutManager that scroll is resolved against the layout before the move
        // and hid the dragged card. NotesGridLayoutManager keeps it on screen instead.
    }

    @Override
    public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
        if (actionState == ItemTouchHelper.ACTION_STATE_DRAG && viewHolder != null) {
            host.onDragStarted(viewHolder);
        }
    }

    @Override
    public void clearView(
            @NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
        super.clearView(recyclerView, viewHolder);
        viewHolder.itemView.setAlpha(1f);
        if (host.isDragging()) host.onDragEnded(viewHolder);
    }

    @Override
    public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
        host.onSwiped(viewHolder, direction);
    }

    /**
     * Slides a card that was swiped off the screen but stays in the list (it was selected, not
     * removed) back into its place.
     *
     * <p>{@link ItemTouchHelper} keeps drawing a swiped card where the swipe left it until its view
     * is detached from the list. That used to happen when the card was rebound: the change was
     * shown with a second copy of the card and the swiped one went away. Without change animations
     * the same view is rebound in place, so the card stayed off the screen. Here the helper lets go
     * of it as if it was detached, and the card is animated back.
     */
    public static void returnSwipedCard(
            @NonNull ItemTouchHelper helper, @NonNull RecyclerView.ViewHolder holder) {
        View card = holder.itemView;
        float x = card.getTranslationX();
        float alpha = card.getAlpha();
        helper.onChildViewDetachedFromWindow(card);
        // Its own animator: the list's item animator cancels the view's property animations
        // when the card is rebound for its new selection state.
        ValueAnimator back = ValueAnimator.ofFloat(1f, 0f);
        back.setDuration(SWIPE_RETURN_MS);
        back.setInterpolator(new DecelerateInterpolator());
        back.addUpdateListener(
                animation -> {
                    float left = (float) animation.getAnimatedValue();
                    card.setTranslationX(x * left);
                    card.setAlpha(1f - (1f - alpha) * left);
                });
        back.start();
    }

    static final long SWIPE_RETURN_MS = 220;
}
