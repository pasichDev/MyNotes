package com.pasich.mynotes.ui.contract.dialogs;

import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;

import java.util.List;

import io.reactivex.Flowable;


public interface MoreNoteDialogContract {

    interface view extends BaseView {
        void setSliderValue(int value);

        void loadingTagsOfChips(Flowable<List<Tag>> tagsList);

        void initInterfaces();

        void callableCopyNote(long newNoteId);

    }

    interface presenter extends BasePresenter<view> {
        void deleteNote(Note note);

        void editSizeText(int value);

        void removeTagNote(int idNote);

        void editTagNote(String nameTag, int idNote);

        void copyNote(Note note, boolean noteActivity);
    }
}
