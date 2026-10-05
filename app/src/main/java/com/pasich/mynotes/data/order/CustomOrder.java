package com.pasich.mynotes.data.order;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.pasich.mynotes.data.model.Note;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The rules of the user's own note order ("Custom" sort).
 *
 * <p>Each note carries a {@link Note#getCustomPosition() position}; a larger one comes first, and
 * pinned notes come before all others. Positions are spread {@link #STEP} apart, so moving a note
 * between two others gives it the midpoint and writes one row. Only when two neighbours have run
 * out of room between them is the whole order renumbered.
 *
 * <p>Free of {@code android.*}, so it is testable under ordinary JVM tests.
 */
public final class CustomOrder {

    /** The gap left between neighbours when positions are handed out. */
    public static final long STEP = 1024L;

    /** Pinned first, then the larger position, then the newer row for equal positions. */
    public static final Comparator<Note> COMPARATOR =
            (a, b) -> {
                if (a.isPinned() != b.isPinned()) return a.isPinned() ? -1 : 1;
                int byPosition = Long.compare(b.getCustomPosition(), a.getCustomPosition());
                return byPosition != 0 ? byPosition : Integer.compare(b.getId(), a.getId());
            };

    private CustomOrder() {}

    /** The position for a note placed at the top of everything at {@code highest}. */
    public static long above(long highest) {
        return highest + STEP;
    }

    /**
     * The position for a note dropped between two neighbours.
     *
     * @param upper position of the note that will be just above it, or null at the top.
     * @param lower position of the note that will be just below it, or null at the bottom.
     * @return the new position, or null when there is no room between the two and the order has to
     *     be renumbered first.
     */
    @Nullable
    public static Long between(@Nullable Long upper, @Nullable Long lower) {
        if (upper == null && lower == null) return STEP;
        if (upper == null) return lower + STEP;
        if (lower == null) return upper - STEP;
        if (upper - lower < 2) return null;
        return lower + (upper - lower) / 2;
    }

    /**
     * Positions for every note in {@code ordered} (top first), {@link #STEP} apart, keeping their
     * order.
     */
    @NonNull
    public static List<Long> renumbered(int count) {
        List<Long> positions = new ArrayList<>(count);
        for (int i = 0; i < count; i++) positions.add((long) (count - i) * STEP);
        return positions;
    }
}
