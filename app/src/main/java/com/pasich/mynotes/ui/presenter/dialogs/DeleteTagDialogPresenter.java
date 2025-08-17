package com.pasich.mynotes.ui.presenter.dialogs;


import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.contract.dialogs.DeleteTagDialogContract;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;

public class DeleteTagDialogPresenter extends BasePresenter<DeleteTagDialogContract.view> implements DeleteTagDialogContract.presenter {

    private int countNotesForTag;

    @Inject
    public DeleteTagDialogPresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }

    @Override
    public void viewIsReady() {
        getView().initListeners();
    }


    @Override
    public void getLoadCountNotesForTag(String nameTag) {
        getCompositeDisposable().add(getDataManager().getCountNotesTag(nameTag).subscribeOn(getSchedulerProvider().io()).subscribe(this::setCountNotesForTag));

    }

    @Override
    public void deleteTagUnchecked(Tag tag) {
        getCompositeDisposable().add(getDataManager().deleteTagForNotes(tag).subscribeOn(getSchedulerProvider().io()).subscribe());
    }

    @Override
    public void deleteTagAndNotes(Tag tag) {
        getCompositeDisposable().add(getDataManager().deleteTagAndNotes(tag).subscribeOn(getSchedulerProvider().io()).subscribe());
    }

    @Override
    public int getCountNotesForTag() {
        return this.countNotesForTag;
    }

    public void setCountNotesForTag(int count) {
        this.countNotesForTag = count;
    }


    @Override
    public void detachView() {
        super.detachView();
        countNotesForTag = 0;
    }
}
