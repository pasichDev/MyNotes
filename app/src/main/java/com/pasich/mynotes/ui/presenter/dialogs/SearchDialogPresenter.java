package com.pasich.mynotes.ui.presenter.dialogs;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.ui.contract.dialogs.SearchDialogContract;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;


public class SearchDialogPresenter extends BasePresenter<SearchDialogContract.view> implements SearchDialogContract.presenter {

    @Inject
    public SearchDialogPresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }

    @Override
    public void viewIsReady() {
        getView().initFabButton();
        getView().settingsListResult();
        getView().createListenerSearch(getDataManager().getNotes());
        getView().initListeners();
    }

    @Override
    public void detachView() {
        super.detachView();
    }


}
