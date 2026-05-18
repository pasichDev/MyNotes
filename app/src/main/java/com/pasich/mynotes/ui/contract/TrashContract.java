package com.pasich.mynotes.ui.contract;

import com.pasich.mynotes.base.view.ActionBar;
import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.data.model.Note;
import java.util.ArrayList;
import java.util.List;

/** Contract for the trash (deleted notes) screen. */
public interface TrashContract {

    interface view extends BaseView, ActionBar {

        /** Configures the recycler view for trash notes. */
        void settingsNotesList();

        /** Shows the confirmation dialog to empty the trash. */
        void cleanTrashDialogShow();

        /** Submits the trash note list to the adapter. */
        void loadData(List<Note> trashList);
    }

    interface presenter extends BasePresenter<view> {
        /** Requests the view to show the empty-trash dialog. */
        void cleanTrashDialogStart();

        /** Loads all trashed notes from the database. */
        void loadingTrash();

        /** Permanently deletes all notes from the trash. */
        void clearTrash();

        /** Restores the given notes from trash back to the main list. */
        void restoreNotesArray(ArrayList<Note> notes);
    }
}
