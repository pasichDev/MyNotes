package com.pasich.mynotes.ui.presenter.dialogs;


import android.util.Log;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.ui.contract.dialogs.MoreNoteDialogContract;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import java.util.Date;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;

public class MoreNoteDialogPresenter extends BasePresenter<MoreNoteDialogContract.view> implements MoreNoteDialogContract.presenter {


    @Inject
    public MoreNoteDialogPresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }


    @Override
    public void viewIsReady() {
        getView().initInterfaces();
        getView().loadingTagsOfChips(getDataManager().getTagsUser());
        getView().initListeners();
        getView().setSliderValue(getDataManager().getSizeTextNoteActivity());
    }


    @Override
    public void noteMoveToTrash(Note note) {
        getCompositeDisposable().add(getDataManager().moveNoteToTrash(note.getId())
                .subscribeOn(getSchedulerProvider().io())
                .observeOn(getSchedulerProvider().ui())
                .subscribe(
                        () -> {
                        }, // onComplete
                        throwable -> Log.e("MoreNoteDialogPresenter", "Error deleting note", throwable)
                ));
    }

    @Override
    public void editSizeText(int value) {
        getDataManager().editSizeTextNoteActivity(value);
    }

    @Override
    public void removeTagNote(int idNote) {
        getCompositeDisposable().add(getDataManager().setTagNote("", idNote)
                .subscribeOn(getSchedulerProvider().io())
                .subscribe(
                        () -> {
                        }, // onComplete
                        throwable -> Log.e("MoreNoteDialogPresenter", "Error removing tag", throwable)
                ));
    }

    @Override
    public void editTagNote(String nameTag, int idNote) {
        getCompositeDisposable().add(getDataManager().setTagNote(nameTag, idNote)
                .subscribeOn(getSchedulerProvider().io())
                .subscribe(
                        () -> {
                        }, // onComplete
                        throwable -> Log.e("MoreNoteDialogPresenter", "Error editing tag", throwable)
                ));
    }

    @Override
    public void copyNoteMainActivity(Note note) {
        getCompositeDisposable().add(getDataManager()
                .addNote(new Note().create(note.getTitle() + " (copy)", note.getValue() + " ", new Date().getTime(), note.getTag()), true)
                .subscribeOn(getSchedulerProvider().io())
                .subscribe(
                        aLong -> getView().callableCopyNote(aLong),
                        throwable -> Log.e("MoreNoteDialogPresenter", "Error copying note", throwable)
                ));

    }
}
