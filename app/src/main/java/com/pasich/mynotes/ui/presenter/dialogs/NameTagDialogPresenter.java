package com.pasich.mynotes.ui.presenter.dialogs;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.contract.dialogs.NameTagDialogContract;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;

public class NameTagDialogPresenter extends BasePresenter<NameTagDialogContract.view> implements NameTagDialogContract.presenter {

    @Inject
    public NameTagDialogPresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }

    @Override
    public void viewIsReady() {
        getView().initListeners();
    }


    @Override
    public void saveTag(String nameNewTag) {
        getCompositeDisposable().add(getDataManager().addTag(new Tag().create(nameNewTag))
                .subscribeOn(getSchedulerProvider().io())
                .observeOn(getSchedulerProvider().ui())
                .subscribe());
    }

    @Override
    public void editNameTag(String nameNewTag, Tag mTag) {
        //   String oldName = mTag.getNameTag();
        //   mTag.setNameTag(nameNewTag);
        getCompositeDisposable().add(getDataManager()
                .renameTag(mTag, nameNewTag)
                .subscribeOn(getSchedulerProvider().io())
                .observeOn(getSchedulerProvider().ui())
                .subscribe());
    }


}
