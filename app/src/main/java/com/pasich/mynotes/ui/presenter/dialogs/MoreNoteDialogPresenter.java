package com.pasich.mynotes.ui.presenter.dialogs;


import static android.content.ContentValues.TAG;

import android.util.Log;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.TrashNote;
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
    public void deleteNote(Note note) {
        getCompositeDisposable().add(getDataManager().moveNoteToTrash(new TrashNote().create(note.getTitle(), note.getValue(), note.getDate()), note).subscribeOn(getSchedulerProvider().io()).subscribe());
    }

    @Override
    public void editSizeText(int value) {
        getDataManager().editSizeTextNoteActivity(value);
    }

    @Override
    public void removeTagNote(int idNote) {
        getCompositeDisposable().add(getDataManager().setTagNote("", idNote).subscribeOn(getSchedulerProvider().io()).subscribe());
    }

    @Override
    public void editTagNote(String nameTag, int idNote) {
        getCompositeDisposable().add(getDataManager().setTagNote(nameTag, idNote).subscribeOn(getSchedulerProvider().io()).subscribe());
    }

    @Override
    public void copyNote(Note note, boolean noteActivity) {

        if (noteActivity) {
            getCompositeDisposable().add(getDataManager().updateNote(note).subscribeOn(getSchedulerProvider().io()).subscribe());
        }

        getCompositeDisposable().add(getDataManager()
                .addNote(new Note().create(note.getTitle() + " (2)", note.getValue() + " ", new Date().getTime(), note.getTag()), true)
                .subscribeOn(getSchedulerProvider().io()).subscribe((aLong) -> getView().callableCopyNote(aLong), (throwable -> Log.wtf(TAG, "copyNote: " + throwable))));

    }
}
