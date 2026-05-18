package com.pasich.mynotes.ui.contract;

import android.content.Intent;
import com.pasich.mynotes.base.view.ActionBar;
import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.base.view.MoreNoteNoteActivityView;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.utils.enums.SaveState;

/** Contract for the note editor screen. */
public interface NoteContract {

    interface view extends BaseView, ActionBar, MoreNoteNoteActivityView {

        /** Reads and applies Activity intent parameters. */
        void initParam();

        /** Detects whether the Activity opens a new or existing note. */
        void initTypeActivity();

        /** Closes the note editor screen. */
        void closeNoteActivity();

        /** Activates the editing mode in the UI. */
        void activatedActivity();

        /** Populates the editor with the given note data. */
        void loadingNote(Note note);

        /** Updates the save-status indicator in the toolbar. */
        void updateSaveStatus(SaveState saveState);

        /** Triggers cleanup of orphaned attachments for the note. */
        void runAttachmentsCleanup(Note note);

        /** Plays the copy-success animation. */
        void runCopyAnimation();

        /** Called after the note has been duplicated with its new id. */
        void onNoteCopied(long newNoteId);

        /** Reloads the extended editor with the current note content. */
        void reloadExtendedEditor();
    }

    interface presenter extends BasePresenter<view> {
        /** Saves pending changes and closes the note screen. */
        void closeActivity();

        /** Extracts note id and flags from the launch Intent. */
        void getLoadIntentData(Intent mIntent);

        /** Loads the note with the given id from the database. */
        void loadingData(long idNote);

        /** Puts the note into edit mode. */
        void activateEditNote();

        /** Returns the current note's database id. */
        long getIdKey();

        /** Sets the current note's database id. */
        void setIdKey(long idKey);

        /** Returns the in-memory note being edited. */
        Note getNote();

        /** Replaces the in-memory note. */
        void setNote(Note mNote);

        /** Returns true if this is a newly created note. */
        boolean getNewNotesKey();

        /** Marks whether the current note is newly created. */
        void setNewNoteKey(boolean newNoteKey);

        /** Returns the Android Typeface constant for the given text-style name. */
        int getTypeFace(String textStyle);

        /** Notifies the presenter that the note content has changed. */
        void onNoteChanged();

        /** Returns true if the extended (rich) editor is active. */
        boolean getExtendedEditor();

        /** Enables or disables the extended editor mode. */
        void setExtendedEditor(boolean extendedEditor);

        /** Applies an extended-editor change to the current note. */
        void extendedNoteChange(String title, String jsonData);

        /** Applies a plain-text editor change to the current note. */
        void simpleNoteChange(String title, String value, boolean emergencySave);

        /** Returns true if a note is currently loaded. */
        boolean hasNote();

        /** Requests duplication of the current note. */
        void copyNoteRequest();
    }

    interface AutoSaveCallback {
        /** Called when the note was saved successfully. */
        void onSuccess();

        /** Called when saving the note failed. */
        void onError(Throwable error);
    }
}
