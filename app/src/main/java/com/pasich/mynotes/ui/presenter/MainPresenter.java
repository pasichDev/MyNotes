package com.pasich.mynotes.ui.presenter;

import static com.pasich.mynotes.utils.constants.settings.TagSettings.MAX_TAG_COUNT;

import android.os.Handler;
import android.util.Log;
import android.view.View;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.TrashNote;
import com.pasich.mynotes.ui.contract.MainContract;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import java.util.ArrayList;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;
import io.reactivex.disposables.CompositeDisposable;

@ActivityScoped
public class MainPresenter extends BasePresenter<MainContract.view> implements MainContract.presenter {

    private Note backupDeleteNote;
    private int mSwipe = 0;

    @Inject
    public MainPresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }

    @Override
    public void viewIsReady() {
        getView().settingsSearchView();
        getView().settingsLists();
        loadingData();
        getView().initListeners();
    }

    @Override
    public void loadingData() {
        getCompositeDisposable().add(getDataManager().getTags().subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe((tagList) -> getView().loadingTags(tagList), throwable -> Log.e("com.pasich.myNotes", "loadTags", throwable)));
        getCompositeDisposable().add(getDataManager().getNotes().subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe((noteList) -> getView().loadingNotes(noteList), throwable -> Log.e("com.pasich.myNotes", "loadNotes", throwable)));
    }


    @Override
    public void newNotesClick() {
        if (isViewAttached()) getView().newNotesButton();
    }



    @Override
    public void clickTag(Tag tag, int position) {
        if (tag.getSystemAction() == 1) {
            getCompositeDisposable().add(getDataManager().getCountTagAll().subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(integer -> {
                if (integer >= MAX_TAG_COUNT) {
                    getView().startToastCheckCountTags();
                } else getView().startCreateTagDialog();
            }));


        } else {
            getView().selectTagUser(position);

        }
    }


    @Override
    public void clickLongTag(Tag tag, View mView) {
        if (tag.getSystemAction() == 0) {
            getView().choiceTagDialog(tag, mView);
        }
    }


    @Override
    public void deleteNotesArray(ArrayList<Note> notes) {
        for (Note note : notes) {
            getCompositeDisposable().add(getDataManager().moveNoteToTrash(new TrashNote().create(note.getTitle(), note.getValue(), note.getDate()), note).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe());
        }
    }

    @Override
    public void deleteNote(Note note) {
        getCompositeDisposable().add(getDataManager().moveNoteToTrash(new TrashNote().create(note.getTitle(), note.getValue(), note.getDate()), note).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe());
    }

    @Override
    public void restoreNote(Note nNote) {
        getCompositeDisposable().add(getDataManager().restoreNote(nNote).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe());
    }

    @Override
    public void deleteTag(Tag tag) {
        getCompositeDisposable().add(getDataManager().getCountNotesTag(tag.getNameTag()).subscribeOn(getSchedulerProvider().io()).subscribe(integer -> {
            if (integer == 0)
                getCompositeDisposable().add(getDataManager().deleteTag(tag).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe());
            else getView().startDeleteTagDialog(tag);
        }));

    }

    @Override
    public void editVisibleTag(Tag tag) {
        getCompositeDisposable().add(getDataManager().updateTag(tag).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe());
    }


    @Override
    public String getSortParam() {
        return getDataManager().getSortParam();
    }

    @Override
    @Deprecated
    public void addNote(Note note) {
        getCompositeDisposable().add(getDataManager().addNote(note, false).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe());
    }

    public Note getBackupDeleteNote() {
        return backupDeleteNote;
    }

    public void setBackupDeleteNote(Note backupDeleteNote) {
        this.backupDeleteNote = backupDeleteNote;
    }

    /**
     * Method The method that implements the closing of the application
     */
    @Override
    public boolean closeApp(boolean showSearchView) {
        if (!showSearchView) {
            mSwipe = mSwipe + 1;
            if (mSwipe == 1) {
                getView().exitWhat();

                Handler handler = new Handler();
                handler.postDelayed(() -> {
                    if (mSwipe == 1) {
                        mSwipe = 0;
                    }
                }, 5000);

                return false;
            } else if (mSwipe == 2) {

                getView().finishActivityOtPresenter();
                mSwipe = 0;
                return true;
            }
        } else {
            getView().hideSearchView();
            return false;
        }
        return false;
    }

    @Override
    public void detachView() {
        super.detachView();
        backupDeleteNote = null;
    }
}
