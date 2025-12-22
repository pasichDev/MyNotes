package com.pasich.mynotes.ui.contract;

import android.view.View;

import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.base.view.MoreNoteMainActivityView;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.state.MainViewState;
import com.pasich.mynotes.ui.state.StatsData;

import java.util.ArrayList;
import java.util.List;

import dagger.hilt.android.scopes.ActivityScoped;

public interface MainContract {

    interface view extends BaseView, MoreNoteMainActivityView {

        void render(MainViewState state);

        void settingsLists();

        void openNewNoteWithId(long id);

        void choiceTagDialog(Tag tag, View mView);

        void choiceNoteDialog(Note note, int position);

        void startDeleteTagDialog(Tag tag);

        void renderSearch(List<Note> filtered);

        void renderDrawerStats(StatsData stats);

        void allTagSelectDialog(List<Tag> tagsList);

        void multipleTagChangerDialog(List<Tag> tagsList);
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

        void onTagSelected(Tag tag);

        void onSortChanged(String newSort);

        void clearUiEvent();

        void updateSearchQuery(String query);

        void requestTagSelection(boolean multiple);

        void requestTagChangeMultipleNotes(String selectedTag, List<Integer> notesIds);
    }

}
