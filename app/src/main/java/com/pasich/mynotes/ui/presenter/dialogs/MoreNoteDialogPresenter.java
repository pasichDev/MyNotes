package com.pasich.mynotes.ui.presenter.dialogs;


import android.util.Log;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.ui.contract.dialogs.MoreNoteDialogContract;
import com.pasich.mynotes.utils.TagsSorter;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import java.util.Date;

import javax.inject.Inject;

import io.reactivex.disposables.CompositeDisposable;

public class MoreNoteDialogPresenter extends BasePresenter<MoreNoteDialogContract.view> implements MoreNoteDialogContract.presenter {
    private static final String TAG = "MoreNoteDialogPresenter";
    private Note mNote;

    @Inject
    public MoreNoteDialogPresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }


    @Override
    public void viewIsReady() {
        getView().initInterfaces();
        getView().setSliderValue(getDataManager().getSizeTextNoteActivity());
    }


    @Override
    public Note getNote() {
        return mNote;
    }


    @Override
    public void loadNote(int noteId) {
        if (noteId <= 0) {
            if (isViewAttached()) getView().close();
            return;
        }

        getCompositeDisposable().add(
                getDataManager()
                        .getNoteForId(noteId)
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(note -> {
                            if (note != null && isViewAttached()) {
                                mNote = note;
                                getView().onNoteLoaded(note);
                            }
                        }, error -> {
                            Log.e(TAG, "loadNote failed", error);
                            if (isViewAttached()) getView().close();
                        })
        );
    }

    @Override
    public void noteMoveToTrash() {
        getCompositeDisposable().add(getDataManager().moveNoteToTrash(mNote.getId())
                .subscribeOn(getSchedulerProvider().io())
                .observeOn(getSchedulerProvider().ui())
                .subscribe(
                        () -> {
                        }, // onComplete
                        throwable -> Log.e(TAG, "Error deleting note", throwable)
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
                        throwable -> Log.e(TAG, "Error removing tag", throwable)
                ));
    }

    @Override
    public void editTagNote(String nameTag, int idNote) {
        getCompositeDisposable().add(getDataManager().setTagNote(nameTag, idNote)
                .subscribeOn(getSchedulerProvider().io())
                .subscribe(
                        () -> {
                        }, // onComplete
                        throwable -> Log.e(TAG, "Error editing tag", throwable)
                ));
    }

    @Override
    public void copyNoteMainActivity() {
        getCompositeDisposable().add(getDataManager()
                .addNote(new Note().create(mNote.getTitle() + " (copy)", mNote.getValue() + " ", new Date().getTime(), mNote.getTag()), true)
                .subscribeOn(getSchedulerProvider().io())
                .subscribe(
                        aLong -> getView().callableCopyNote(aLong),
                        throwable -> Log.e(TAG, "Error copying note", throwable)
                ));

    }

    @Override
    public void requestTagsOneShot() {
        getCompositeDisposable().add(
                getDataManager()
                        .getTagsUser()
                        .take(1)
                        .map(tags -> TagsSorter.sortTags(tags, getDataManager().getSortParamTags()))
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(
                                sorted -> getView().createChipsTag(sorted),
                                err -> Log.e(TAG, "requestTagsOneShot()", err)
                        )
        );
    }


}
