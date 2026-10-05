package androidx.recyclerview.widget;

import androidx.annotation.NonNull;

/**
 * Reaches the part of {@link ItemTouchHelper} that is not public: after the list was scrolled under
 * a dragged card that is held still, it lets the helper check whether the card now covers another
 * one, the same check it makes after its own edge scroll.
 */
public final class ItemTouchHelperAccess {

    private ItemTouchHelperAccess() {}

    public static void moveIfNecessary(
            @NonNull ItemTouchHelper helper, @NonNull RecyclerView.ViewHolder dragged) {
        helper.moveIfNecessary(dragged);
    }
}
