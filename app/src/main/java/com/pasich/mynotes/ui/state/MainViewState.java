package com.pasich.mynotes.ui.state;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;

import java.util.List;

public record MainViewState(
        List<Tag> tags,
        List<Note> notes,
        Tag selectedTag
) {
    public static MainViewState empty() {
        return new MainViewState(
                List.of(),
                List.of(),
                null
        );
    }
}

