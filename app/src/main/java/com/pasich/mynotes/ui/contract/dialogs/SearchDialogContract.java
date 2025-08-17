package com.pasich.mynotes.ui.contract.dialogs;

import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.data.model.Note;

import java.util.List;

import io.reactivex.Flowable;


public interface SearchDialogContract {

    interface view extends BaseView {

        void settingsListResult();

        void createListenerSearch(Flowable<List<Note>> mNotes);

        void initFabButton();
    }

    interface presenter extends BasePresenter<view> {


    }
}
