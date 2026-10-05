package com.pasich.mynotes.ui.state;

import androidx.annotation.Nullable;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.utils.recycler.diffutil.NoteDiff;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of the main screen UI state.
 *
 * <p>Equality is by what the screen shows, not by object identity: Room hands out new {@code Note}
 * and {@code Tag} instances on every emission, so a sync that rewrites rows without changing them
 * produces an equal state and is dropped before it reaches the view. The {@code uiEvent} is not
 * part of equality.
 */
public record MainViewState(List<Tag> tags, List<Note> notes, Tag selectedTag, UiEvent uiEvent) {
    public static MainViewState empty() {
        return new MainViewState(List.of(), List.of(), null, UiEvent.NONE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MainViewState other)) return false;

        return sameTag(selectedTag, other.selectedTag)
                && sameTags(tags, other.tags)
                && sameNotes(notes, other.notes);
    }

    @Override
    public int hashCode() {
        int h = 1;
        for (Note n : notes) h = 31 * h + n.getId();
        for (Tag t : tags) h = 31 * h + Long.hashCode(t.getId());
        if (selectedTag != null) h = 31 * h + Long.hashCode(selectedTag.getId());
        return h;
    }

    private static boolean sameNotes(List<Note> a, List<Note> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!NoteDiff.sameContent(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    private static boolean sameTags(List<Tag> a, List<Tag> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Tag x = a.get(i);
            Tag y = b.get(i);
            if (!sameTag(x, y)
                    || x.getSelected() != y.getSelected()
                    || x.getVisibility() != y.getVisibility()
                    || x.getPosition() != y.getPosition()) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameTag(@Nullable Tag a, @Nullable Tag b) {
        if (a == null || b == null) return a == b;
        return a.getId() == b.getId()
                && a.getSystemAction() == b.getSystemAction()
                && Objects.equals(a.getNameTag(), b.getNameTag());
    }
}
