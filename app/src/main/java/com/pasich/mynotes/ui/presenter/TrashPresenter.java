package com.pasich.mynotes.ui.presenter;


import android.util.Log;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.TrashNote;
import com.pasich.mynotes.ui.contract.TrashContract;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import java.util.ArrayList;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;


public class TrashPresenter extends BasePresenter<TrashContract.view> implements TrashContract.presenter {


    @Inject
    public TrashPresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }


    @Override
    public void viewIsReady() {
        getView().settingsActionBar();
        getView().settingsNotesList();
        loadingTrash();
        getView().initListeners();
    }


    @Override
    public void cleanTrashDialogStart() {
        if (isViewAttached()) getView().cleanTrashDialogShow();
    }

    @Override
    public void loadingTrash() {
        getCompositeDisposable().add(
                getDataManager().getTrashNotesLoad()
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(trashNoteList -> getView().loadData(trashNoteList),
                                throwable -> Log.wtf("MyNotes", "trashLoad", throwable)));
    }

    @Override
    public void restoreNotesArray(ArrayList<TrashNote> notes) {
        for (TrashNote tNote : notes) {
            getDataManager().transferNoteOutTrash(tNote, new Note().create(tNote.getTitle(), tNote.getValue(), tNote.getDate())).subscribeOn(Schedulers.newThread()).subscribe();
        }
    }

    @Override
    public void clearTrash() {
        getCompositeDisposable().add(getDataManager().deleteAll().subscribeOn(getSchedulerProvider().io()).subscribe());
    }

}
