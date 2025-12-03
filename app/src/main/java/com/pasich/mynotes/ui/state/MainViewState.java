package com.pasich.mynotes.ui.state;

import static com.pasich.mynotes.utils.constants.settings.PreferencesConfig.ARGUMENT_DEFAULT_SORT_PREF;

import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;

import java.util.List;
import java.util.Set;

public record MainViewState(
        List<Tag> tags,
        List<Note> notes,
        Tag selectedTag,
        String sortParam,
        boolean isEmpty,
        int countNotes,
        Set<String> hiddenTags
) {
    public static MainViewState empty() {
        return new MainViewState(
                List.of(),
                List.of(),
                null,
                ARGUMENT_DEFAULT_SORT_PREF,
                true,
                0,
                Set.of()
        );
    }
}

