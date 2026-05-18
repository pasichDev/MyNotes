package com.pasich.mynotes.ui.state;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import java.util.List;
import java.util.Objects;

/** Immutable snapshot of the main screen UI state. */
public record MainViewState(List<Tag> tags, List<Note> notes, Tag selectedTag, UiEvent uiEvent) {
    public static MainViewState empty() {
        return new MainViewState(List.of(), List.of(), null, UiEvent.NONE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MainViewState other)) return false;

        return tags.equals(other.tags)
                && notes.equals(other.notes)
                && ((selectedTag == null && other.selectedTag == null)
                        || (selectedTag != null && selectedTag.equals(other.selectedTag)));
    }

    @Override
    public int hashCode() {
        return Objects.hash(tags, notes, selectedTag);
    }
}
