package com.pasich.mynotes.ui.contract;

import android.view.View;
import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.base.view.MoreNoteMainActivityView;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.state.MainViewState;
import com.pasich.mynotes.ui.state.StatsData;
import dagger.hilt.android.scopes.ActivityScoped;
import java.util.ArrayList;
import java.util.List;

/** Contract for the main notes list screen. */
public interface MainContract {

    interface view extends BaseView, MoreNoteMainActivityView {

        /** Renders the full main screen state. */
        void render(MainViewState state);

        /** Configures the notes list settings. */
        void settingsLists();

        /** Opens a new note with the given database id. */
        void openNewNoteWithId(long id);

        /** Shows the tag options dialog anchored to the given view. */
        void choiceTagDialog(Tag tag, View mView);

        /** Shows the note options dialog for the given position. */
        void choiceNoteDialog(Note note, int position);

        /** Shows the confirmation dialog to delete a tag. */
        void startDeleteTagDialog(Tag tag);

        /** Renders search results to the UI. */
        void renderSearch(List<Note> filtered);

        /** Updates the drawer statistics panel. */
        void renderDrawerStats(StatsData stats);

        /** Shows the dialog to select a tag from the full list. */
        void allTagSelectDialog(List<Tag> tagsList);

        /** Shows the dialog to bulk-change tags on selected notes. */
        void multipleTagChangerDialog(List<Tag> tagsList);
    }

    @ActivityScoped
    interface presenter extends BasePresenter<view> {
        /** Handles the new-note FAB click. */
        void newNotesClick();

        /** Moves an array of notes to trash. */
        void deleteNotesArray(ArrayList<Note> notes);

        /** Moves a single note to trash. */
        void noteMoveToTrash(Note note);

        /** Restores the last note that was moved to trash. */
        void restoreNoteLastMoveToTrash(Note nNote);

        /** Requests deletion of the given tag. */
        void requestDeleteTag(Tag tag);

        /** Persists a visibility change on the given tag. */
        void editVisibleTag(Tag tag);

        /** Returns the note saved before deletion for undo purposes. */
        Note getBackupDeleteNote();

        /** Stores the note that will be used as the undo target. */
        void setBackupDeleteNote(Note backupDeleteNote);

        /** Called when the user selects a tag filter. */
        void onTagSelected(Tag tag);

        /** Called when the user changes the sort order. */
        void onSortChanged(String newSort);

        /** Clears the pending UI event after it has been consumed. */
        void clearUiEvent();

        /** Updates the active search query. */
        void updateSearchQuery(String query);

        /** Restricts the search to the given tag name. */
        void updateSearchTagFilter(String tagName);

        /** Opens the tag-selection dialog; multiple controls single vs. multi mode. */
        void requestTagSelection(boolean multiple);

        /** Assigns selectedTag to all notes identified by notesIds. */
        void requestTagChangeMultipleNotes(String selectedTag, List<Integer> notesIds);
    }
}
