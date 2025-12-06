package com.pasich.mynotes.ui.presenter;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.pasich.mynotes.base.presenter.BasePresenter;
import com.pasich.mynotes.data.DataManager;
import com.pasich.mynotes.data.model.Note;
import com.pasich.mynotes.data.model.Tag;
import com.pasich.mynotes.ui.contract.MainContract;
import com.pasich.mynotes.ui.state.MainViewState;
import com.pasich.mynotes.utils.TagsSorter;
import com.pasich.mynotes.utils.constants.settings.SortParam;
import com.pasich.mynotes.utils.managers.SystemTagsManager;
import com.pasich.mynotes.utils.rx.SchedulerProvider;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import dagger.hilt.android.scopes.ActivityScoped;
import io.reactivex.Observable;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.subjects.BehaviorSubject;

@ActivityScoped
public class MainPresenter extends BasePresenter<MainContract.view> implements MainContract.presenter {

    private static final String TAG = "MainPresenter";
    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private final BehaviorSubject<Tag> selectedTag = BehaviorSubject.createDefault(new Tag());
    private final BehaviorSubject<String> sortParam = BehaviorSubject.create();
    private final BehaviorSubject<MainViewState> viewState = BehaviorSubject.create();
    private Note backupDeleteNote;
    private int mSwipe = 0;
    private Runnable swipeResetRunnable;
    private Observable<List<Tag>> tagsStream;
    private Observable<List<Note>> notesStream;
    private Observable<Set<String>> hiddenTagsStream;


    @Inject
    public MainPresenter(SchedulerProvider schedulerProvider, CompositeDisposable compositeDisposable, DataManager dataManager) {
        super(schedulerProvider, compositeDisposable, dataManager);
    }

    @Override
    public void viewIsReady() {
        sortParam.onNext(getDataManager().getSortParam());

        getView().settingsLists();
        getView().initListeners();
        initStreams();
        startStateCombiner();
        getCompositeDisposable().add(
                viewState.hide()
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(
                                state -> getView().render(state),
                                throwable -> Log.e(TAG, "observeState", throwable)
                        )
        );
    }

    private void initStreams() {
        tagsStream = Observable.combineLatest(
                getDataManager().getTags()
                        .toObservable()
                        .map(tagList -> TagsSorter.sortTags(tagList, getDataManager().getSortParamTags())),
                selectedTag,
                (tags, selected) ->
                        tags.stream()
                                .map(t -> {
                                    Tag copy = t.copy();
                                    copy.setSelected(selected != null && selected.getId() == t.getId());
                                    return copy;
                                })
                                .collect(Collectors.toList())
        );

        notesStream = getDataManager().getNotes()
                .toObservable();

        hiddenTagsStream = tagsStream
                .map(tags -> tags.stream()
                        .filter(t -> t.getVisibility() == 1)
                        .map(Tag::getNameTag)
                        .collect(Collectors.toSet())
                );


    }

    private void startStateCombiner() {
        getCompositeDisposable().add(
                Observable.combineLatest(
                                tagsStream,
                                notesStream,
                                selectedTag,
                                sortParam,
                                hiddenTagsStream,
                                this::buildState
                        )
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(
                                viewState::onNext,
                                throwable -> Log.e(TAG, "combineLatest", throwable)
                        )


        );
    }

    private MainViewState buildState(
            List<Tag> tags,
            List<Note> notes,
            Tag selectedTag,
            String sort,
            Set<String> hiddenTags
    ) {
        String tagName = selectedTag == null ? "allNotes" : selectedTag.getNameTag();

        // Фільтр по прихованих тегах
        List<Note> visible = filterHidden(notes, hiddenTags);

        // Фільтр по обраному тегу
        List<Note> filtered = filterNotes(visible, tagName);

        // Сортування
        List<Note> sorted = sortNotes(filtered, sort);


        return new MainViewState(
                tags,
                sorted,
                selectedTag
        );
    }


    private List<Note> filterHidden(List<Note> notes, Set<String> hiddenTags) {
        if (hiddenTags == null || hiddenTags.isEmpty()) return notes;

        List<Note> result = new ArrayList<>(notes.size());
        for (Note note : notes) {
            if (!hiddenTags.contains(note.getTag())) {
                result.add(note);
            }
        }
        return result;
    }


    private List<Note> filterNotes(List<Note> notes, String tagName) {
        if (tagName == null || tagName.equals("allNotes")) {
            return notes;
        }

        List<Note> result = new ArrayList<>();
        for (Note note : notes) {
            if (tagName.equals(note.getTag())) {
                result.add(note);
            }
        }
        return result;
    }


    private List<Note> sortNotes(List<Note> notes, String sort) {
        List<Note> sorted = new ArrayList<>(notes);
        Comparator<Note> comparator;
        if (sort.equals(SortParam.DataSort)) {
            comparator = (e1, e2) -> Long.compare(e2.getDate(), e1.getDate());
        } else {
            comparator = Comparator.comparingLong(Note::getDate);
        }

        sorted.sort(comparator);
        return sorted;
    }


    @Override
    public void onSortChanged(String newSort) {
        sortParam.onNext(newSort);
    }


    @Override
    public void newNotesClick() {
        if (!isViewAttached()) return;
        createNewNote(new Note().create(
                "",
                "",
                System.currentTimeMillis(),
                ""
        ), new MainContract.CreateNoteCallback() {
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
    public void deleteNotesArray(ArrayList<Note> notes) {
        List<Integer> ids = new ArrayList<>();
        for (Note note : notes) ids.add(note.getId());
        getCompositeDisposable().add(
                getDataManager()
                        .moveNotesToTrash(ids)
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

    private void performDelete(Tag tag) {
        getCompositeDisposable().add(
                getDataManager().getCountNotesTag(tag.getNameTag())
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(count -> {
                            if (count == 0) {
                                deleteTagFromDb(tag);
                            } else {
                                getView().startDeleteTagDialog(tag);
                            }
                        }, throwable -> Log.e(TAG, "count error", throwable))
        );
    }

    private void deleteTagFromDb(Tag tag) {
        getCompositeDisposable().add(
                getDataManager().deleteTag(tag)
                        .subscribeOn(getSchedulerProvider().io())
                        .observeOn(getSchedulerProvider().ui())
                        .subscribe(
                                () -> { /* ок */ },
                                throwable -> Log.e(TAG, "Error deleting tag", throwable)
                        )
        );
    }

    private Tag findAllNotesTag() {
        return viewState.getValue().tags().stream()
                .filter(t -> t.getSystemAction() == SystemTagsManager.SYSTEM_ACTION_ALL_NOTES)
                .findFirst()
                .orElse(null);
    }


    @Override
    public void requestDeleteTag(Tag tag) {

        // Якщо видаляємо той, що зараз вибраний → перемикаємось
        if (selectedTag.getValue() != null &&
                selectedTag.getValue().getId() == tag.getId()) {

            Tag all = findAllNotesTag();
            selectedTag.onNext(all);
        }

        // Затримка тільки тут
        uiHandler.postDelayed(() -> performDelete(tag), 700);
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

                if (isViewAttached()) getView().exitWhat();

                if (swipeResetRunnable != null) {
                    uiHandler.removeCallbacks(swipeResetRunnable);
                }

                swipeResetRunnable = () -> mSwipe = 0;
                uiHandler.postDelayed(swipeResetRunnable, 3000);

                return false;
            } else if (mSwipe == 2) {
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


    /**
     * NEW
     */

    @Override
    public void onTagSelected(Tag tag) {
        selectedTag.onNext(tag);
    }

}
