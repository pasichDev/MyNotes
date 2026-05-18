package com.pasich.mynotes.ui.contract;

import android.content.Intent;
import com.pasich.mynotes.base.view.ActionBar;
import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.base.view.MoreNoteNoteActivityView;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.utils.enums.SaveState;

public interface NoteContract {

    interface view extends BaseView, ActionBar, MoreNoteNoteActivityView {

        void initParam();

        void initTypeActivity();

        void closeNoteActivity();

        void activatedActivity();

        void loadingNote(Note note);

        void updateSaveStatus(SaveState saveState);

        void runAttachmentsCleanup(Note note);

        void runCopyAnimation();

        void onNoteCopied(long newNoteId);

        void reloadExtendedEditor();
    }

    interface presenter extends BasePresenter<view> {
        void closeActivity();

        void getLoadIntentData(Intent mIntent);

        void loadingData(long idNote);

        void activateEditNote();

        long getIdKey();

        void setIdKey(long idKey);

        Note getNote();

        void setNote(Note mNote);

        boolean getNewNotesKey();

        void setNewNoteKey(boolean newNoteKey);

        int getTypeFace(String textStyle);

        void onNoteChanged();

        boolean getExtendedEditor();

        void setExtendedEditor(boolean extendedEditor);

        void extendedNoteChange(String title, String jsonData);

        void simpleNoteChange(String title, String value, boolean emergencySave);

        boolean hasNote();

        void copyNoteRequest();
    }

    interface AutoSaveCallback {
        void onSuccess();

        void onError(Throwable error);
    }
}
