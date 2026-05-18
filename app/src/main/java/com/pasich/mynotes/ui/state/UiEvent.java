package com.pasich.mynotes.ui.state;

public enum UiEvent {
    NONE, // No UI animation required; plain list update
    SORT_CHANGED, // User changed the sorting order
    TAG_CHANGED, // User switched to another tag
    NOTE_CREATED, // A new note has been created
    NOTES_CHANGED // Notes modified: delete / restore / move / etc.
}
