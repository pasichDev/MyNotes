package com.pasich.mynotes.ui.contract.dialogs;

import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.data.model.Tag;

public interface DeleteTagDialogContract {

    interface view extends BaseView {
        /** Shows how many notes carry the tag, once it is known. */
        void showNotesCount(int count);
    }

    interface presenter extends BasePresenter<view> {
        void getLoadCountNotesForTag(String nameTag);

        void deleteTagUnchecked(Tag tag);

        void deleteTagAndNotes(Tag tag);

        int getCountNotesForTag();
    }
}
