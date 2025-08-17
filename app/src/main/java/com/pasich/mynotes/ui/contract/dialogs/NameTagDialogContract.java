package com.pasich.mynotes.ui.contract.dialogs;

import com.pasich.mynotes.base.view.BasePresenter;
import com.pasich.mynotes.base.view.BaseView;
import com.pasich.mynotes.data.model.Tag;


public interface NameTagDialogContract {

    interface view extends BaseView {

    }

    interface presenter extends BasePresenter<view> {

        void saveTag(String nameNewTag);

        void editNameTag(String nameNewTag, Tag mTag);
    }
}
