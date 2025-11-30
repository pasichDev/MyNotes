package com.pasich.mynotes.ui.presenter;


import android.util.Log;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.ui.contract.TrashContract;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;


public class TrashPresenter extends BasePresenter<TrashContract.view> implements TrashContract.presenter {
    private static final String TAG = "TrashPresenter";

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
                getDataManager().getNotesInTrash()
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(trashNoteList -> getView().loadData(trashNoteList),
                                throwable -> Log.wtf(TAG, "trashLoad", throwable)));
    }

    @Override
    public void restoreNotesArray(ArrayList<Note> notes) {
        List<Integer> ids = new ArrayList<>();
        for (Note note : notes) ids.add(note.getId());
        getCompositeDisposable().add(
                getDataManager()
                        .transferNotesOutTrash(ids)
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(
                                () -> {
                                },
                                throwable -> Log.e(TAG, "Error restoring notes", throwable)
                        )
        );
    }


    @Override
    public void clearTrash() {
        getCompositeDisposable().add(
                getDataManager().clearTrash()
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(
                                () -> getView().loadData(new ArrayList<>()),
                                throwable -> Log.e(TAG, "clearTrash failed", throwable)
                        )
        );
    }




}
