package com.pasich.mynotes.ui.contract.dialogs;

import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;

import java.util.List;


public interface MoreNoteDialogContract {

    interface view extends BaseView {
        void setSliderValue(int value);

        void initInterfaces();

        void callableCopyNote(long newNoteId);

        void createChipsTag(List<Tag> tags);

        void close();

        void onNoteLoaded(Note mNote);
    }

    interface presenter extends BasePresenter<view> {
        void loadNote(int noteId);

        void noteMoveToTrash();

        void editSizeText(int value);

        void removeTagNote(int idNote);

        void editTagNote(String nameTag, int idNote);

        void copyNoteMainActivity();

        void requestTagsOneShot();

        Note getNote();
    }
}
