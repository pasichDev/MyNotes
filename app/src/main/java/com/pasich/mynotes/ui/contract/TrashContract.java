package com.pasich.mynotes.ui.contract;


import com.pasich.mynotes.base.view.ActionBar;
import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.data.model.TrashNote;

import java.util.ArrayList;
import java.util.List;


public interface TrashContract {

    interface view extends BaseView, ActionBar {

        void settingsNotesList();

        void cleanTrashDialogShow();

        void loadData(List<TrashNote> trashList);
    }

    interface presenter extends BasePresenter<view> {
        void cleanTrashDialogStart();

        void loadingTrash();

        void clearTrash();

        void restoreNotesArray(ArrayList<TrashNote> notes);
    }
}
