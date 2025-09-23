package com.pasich.mynotes.ui.presenter;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.data.model.TrashNote;
import com.pasich.mynotes.ui.contract.MainContract;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;
import io.reactivex.disposables.CompositeDisposable;

@ActivityScoped
public class MainPresenter extends BasePresenter<MainContract.view> implements MainContract.presenter {

    private Note backupDeleteNote;
    private int mSwipe = 0;
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable swipeResetRunnable;

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
        getCompositeDisposable().add(getDataManager().getTags().subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe((tagList) -> {
            List<Tag> sortedTags = sortTagsList(tagList);
            getView().loadingTags(sortedTags);
        }, throwable -> Log.e("com.pasich.myNotes", "loadTags", throwable)));

        getCompositeDisposable().add(getDataManager().getNotes().subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe((noteList) -> getView().loadingNotes(noteList, getDataManager().getSortParam()), throwable -> Log.e("com.pasich.myNotes", "loadNotes", throwable)));
    }

    private List<Tag> sortTagsList(List<Tag> tagList) {
        String sortParam = getDataManager().getSortParamTags();
        List<Tag> sortedList = new ArrayList<>(tagList);
        if ("TagsPositionSort".equals(sortParam)) {
            // Sort by position (custom sorting)
            sortedList.sort(Comparator.comparingInt(Tag::getPosition));
        } else {
            // Sort by creation date (ID - higher is newer)
            sortedList.sort((tag1, tag2) -> Long.compare(tag2.getId(), tag1.getId()));
        }

        return sortedList;
    }


    @Override
    public void newNotesClick() {
        if (isViewAttached()) getView().newNotesButton();
    }


    @Override
    public void clickTag(Tag tag, int position) {
        if (SystemTagsManager.isChangeLogTag(tag)) {
            // Відкриваємо ChangelogActivity для нового тегу "change"
            getView().openChangelogActivity();
        } else {
            // Викликаємо selectTagUser тільки для не вибраних тегів
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
            deleteNote(note);
        }
    }

    @Override
    public void deleteNote(Note note) {
        getCompositeDisposable().add(getDataManager().moveNoteToTrash(new TrashNote().create(note.getTitle(), note.getValue(), note.getDate()), note).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
                }, // onComplete
                throwable -> Log.e("MainPresenter", "Error deleting note", throwable)));
    }

    @Override
    public void restoreNote(Note nNote) {
        getCompositeDisposable().add(getDataManager().restoreNote(nNote).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
                }, // onComplete
                throwable -> Log.e("MainPresenter", "Error restoring note", throwable)));
    }

    @Override
    public void deleteTag(Tag tag) {
        getCompositeDisposable().add(getDataManager().getCountNotesTag(tag.getNameTag()).subscribeOn(getSchedulerProvider().io()).subscribe(integer -> {
            if (integer == 0) {
                getCompositeDisposable().add(getDataManager().deleteTag(tag).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
                        }, // onComplete
                        throwable -> Log.e("MainPresenter", "Error deleting tag", throwable)));
            } else {
                getView().startDeleteTagDialog(tag);
            }
        }, throwable -> Log.e("MainPresenter", "Error checking tag count", throwable)));
    }

    @Override
    public void editVisibleTag(Tag tag) {
        getCompositeDisposable().add(getDataManager().updateTag(tag).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
                }, // onComplete
                throwable -> Log.e("MainPresenter", "Error updating tag", throwable)));
    }


    @Override
    public String getSortParam() {
        return getDataManager().getSortParam();
    }

    @Override
    @Deprecated
    public void addNote(Note note) {
        getCompositeDisposable().add(getDataManager().addNote(note, false).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(aLong -> {
                }, // onSuccess
                throwable -> Log.e("MainPresenter", "Error adding note", throwable)));
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

                // Очищаємо попередній callback якщо він існує
                if (swipeResetRunnable != null) {
                    uiHandler.removeCallbacks(swipeResetRunnable);
                }

                // Оптимізовано: зменшуємо затримку та спрощуємо логіку
                swipeResetRunnable = () -> mSwipe = 0;
                uiHandler.postDelayed(swipeResetRunnable, 3000); // Зменшили з 5000 до 3000

                return false;
            } else if (mSwipe == 2) {
                // Очищаємо callback перед завершенням
                if (swipeResetRunnable != null) {
                    uiHandler.removeCallbacks(swipeResetRunnable);
                }
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
    public void cleanBackupPrefs() {
        getDataManager().cleanBackupInfo();
    }

    @Override
    public void detachView() {
        if (swipeResetRunnable != null) {
            uiHandler.removeCallbacks(swipeResetRunnable);
        }
        super.detachView();
        backupDeleteNote = null;
        swipeResetRunnable = null;
    }
}
