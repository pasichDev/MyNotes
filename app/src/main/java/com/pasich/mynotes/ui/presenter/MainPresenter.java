package com.pasich.mynotes.ui.presenter;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.contract.MainContract;
import com.pasich.mynotes.utils.adapters.tagAdapter.TagsSorter;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;
import io.reactivex.disposables.CompositeDisposable;

@ActivityScoped
public class MainPresenter extends BasePresenter<MainContract.view> implements MainContract.presenter {

    private static final String TAG = "MainPresenter";
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Note backupDeleteNote;
    private int mSwipe = 0;
    private Runnable swipeResetRunnable;

    @Inject
    public MainPresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }

    @Override
    public void viewIsReady() {
        getView().settingsLists();
        loadingData();
        getView().initListeners();
    }

    @Override
    public void loadingData() {
        getCompositeDisposable().add(getDataManager().getTags().subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe((tagList) -> {
            getView().loadingTags(TagsSorter.sortTags(tagList, getDataManager().getSortParamTags()));
        }, throwable -> Log.e(TAG, "loadTags", throwable)));

        getCompositeDisposable().add(getDataManager().getNotes().subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe((noteList) -> getView().loadingNotes(noteList, getDataManager().getSortParam()), throwable -> Log.e(TAG, "loadNotes", throwable)));
    }


    @Override
    public void newNotesClick() {
        if (!isViewAttached()) return;

        Note note = new Note().create(
                "",
                "",
                System.currentTimeMillis(),
                ""
        );

        createNewNote(note, new MainContract.CreateNoteCallback() {
            @Override
            public void onCreated(long id) {
                // Кажемо View відкрити нову нотатку вже з ID
                if (isViewAttached()) {
                    getView().openNewNoteWithId(id);
                }
            }

            @Override
            public void onError(Throwable t) {
                Log.e(TAG, "Failed to create note", t);
            }
        });
    }


    @Override
    public void clickTag(Tag tag, int position) {
        getView().selectTagUser(position);
    }


    @Override
    public void clickLongTag(Tag tag, View mView) {
        if (tag.getSystemAction() == 0) {
            getView().choiceTagDialog(tag, mView);
        }
    }

    @Override
    public void deleteNotesArray(ArrayList<Note> notes) {
        List<Integer> ids = new ArrayList<>();
        for (Note note : notes) ids.add(note.getId());
        getCompositeDisposable().add(
                getDataManager()
                        .moveNotesToTrash(ids)
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(
                                () -> {},
                                throwable -> Log.e(TAG, "Error restoring notes", throwable)
                        )
        );
    }


    @Override
    public void noteMoveToTrash(Note note) {
        getCompositeDisposable().add(getDataManager().moveNoteToTrash(note.getId()).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
                }, // onComplete
                throwable -> Log.e(TAG, "Error deleting note", throwable)));
    }

    @Override
    public void restoreNoteLastMoveToTrash(Note nNote) {
        getCompositeDisposable().add(getDataManager()
                .transferNoteOutTrash(nNote.getId())
                .subscribeOn(getSchedulerProvider().io())
                .observeOn(getSchedulerProvider().ui())
                .subscribe(() -> {
                }, // onComplete
                throwable -> Log.e(TAG, "Error restoring note", throwable)));
    }

    @Override
    public void deleteTag(Tag tag) {
        getCompositeDisposable().add(getDataManager().getCountNotesTag(tag.getNameTag()).subscribeOn(getSchedulerProvider().io()).subscribe(integer -> {
            if (integer == 0) {
                getCompositeDisposable().add(getDataManager().deleteTag(tag).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
                        }, // onComplete
                        throwable -> Log.e(TAG, "Error deleting tag", throwable)));
            } else {
                getView().startDeleteTagDialog(tag);
            }
        }, throwable -> Log.e(TAG, "Error checking tag count", throwable)));
    }

    @Override
    public void editVisibleTag(Tag tag) {
        getCompositeDisposable().add(getDataManager().updateTag(tag).subscribeOn(getSchedulerProvider().io()).observeOn(getSchedulerProvider().ui()).subscribe(() -> {
                }, // onComplete
                throwable -> Log.e(TAG, "Error updating tag", throwable)));
    }


    /**
     * Створення нової нотатки з поверненням її ID
     */
    public void createNewNote(Note note, MainContract.CreateNoteCallback callback) {
        if (note == null) {
            callback.onError(new Exception("Note is null"));
            return;
        }

        getCompositeDisposable().add(
                getDataManager().addNote(note, false)
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(id -> {
                            note.setId(Math.toIntExact(id));
                            callback.onCreated(id);
                        }, callback::onError)
        );
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
    public void detachView() {
        if (swipeResetRunnable != null) {
            uiHandler.removeCallbacks(swipeResetRunnable);
        }
        super.detachView();
        backupDeleteNote = null;
        swipeResetRunnable = null;
    }
}
