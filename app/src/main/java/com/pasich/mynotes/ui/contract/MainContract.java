package com.pasich.mynotes.ui.contract;

import android.view.View;

import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.base.view.MoreNoteMainActivityView;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.state.MainViewState;
import com.pasich.mynotes.ui.state.UiEvent;
import com.pasich.mynotes.utils.actionPanel.interfaces.ManagerViewAction;

import java.util.ArrayList;

import dagger.hilt.android.scopes.ActivityScoped;

public interface MainContract {

    interface view extends BaseView, MoreNoteMainActivityView, ManagerViewAction<Note> {

        void render(MainViewState state);

        void settingsLists();

        void openNewNoteWithId(long id);

        void choiceTagDialog(Tag tag, View mView);

        void choiceNoteDialog(Note note, int position);

        void startDeleteTagDialog(Tag tag);

     //   void exitWhat();

    //    void finishActivityOtPresenter();

    //    void hideSearchView();

    }


    @ActivityScoped
    interface presenter extends BasePresenter<view> {
        void newNotesClick();

        void deleteNotesArray(ArrayList<Note> notes);

        void noteMoveToTrash(Note note);

        void restoreNoteLastMoveToTrash(Note nNote);

        void requestDeleteTag(Tag tag);

        void editVisibleTag(Tag tag);

        Note getBackupDeleteNote();

        void setBackupDeleteNote(Note backupDeleteNote);

   //     boolean closeApp(boolean showSearchView);

        void onTagSelected(Tag tag);

        void onSortChanged(String newSort);

        void clearUiEvent();
    }

/*    interface CreateNoteCallback {
        void onCreated(long id);

        void onError(Throwable t);
    }

 */

}
