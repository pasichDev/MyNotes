package com.pasich.mynotes.utils.recycler;

import com.pasich.mynotes.data.model.Note;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Decides how the notes grid should move from one list to the next. */
public final class NoteListTransition {

    private NoteListTransition() {}

    /**
     * True when the change should be shown as one cross-fade of the whole list instead of per-card
     * animations.
     *
     * <p>A reorder always qualifies: an edited note jumping to the top would otherwise slide over
     * every card in between. In a grid with more than one column, adding or removing a card also
     * qualifies, because it moves the cards after it into the other column and those moves cut
     * diagonally across their neighbours. Text-only changes, and anything involving an empty list,
     * never do.
     */
    public static boolean needsCrossfade(List<Note> previous, List<Note> next, int spanCount) {
        if (previous.isEmpty() || next.isEmpty()) return false;

        Set<Integer> previousIds = idsOf(previous);
        Set<Integer> nextIds = idsOf(next);
        boolean membershipChanged = !previousIds.equals(nextIds);
        if (membershipChanged && spanCount > 1) return true;

        return !commonOrder(previous, nextIds).equals(commonOrder(next, previousIds));
    }

    private static Set<Integer> idsOf(List<Note> notes) {
        Set<Integer> ids = new HashSet<>(notes.size() * 2);
        for (Note n : notes) ids.add(n.getId());
        return ids;
    }

    /** The ids of {@code notes} that also appear in {@code keep}, in list order. */
    private static List<Integer> commonOrder(List<Note> notes, Set<Integer> keep) {
        List<Integer> order = new ArrayList<>(notes.size());
        for (Note n : notes) {
            if (keep.contains(n.getId())) order.add(n.getId());
        }
        return order;
    }
}
